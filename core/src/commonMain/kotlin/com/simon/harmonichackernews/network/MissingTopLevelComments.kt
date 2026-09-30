package com.simon.harmonichackernews.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Repairs only known missing roots; replies never expand this bounded per-opening request budget. */
internal class MissingTopLevelComments(
    private val official: HackerNewsRepository,
    private val parser: AlgoliaCommentsParser,
    private val dispatcher: CoroutineDispatcher,
) {
    private val requests = Semaphore(MAX_CONCURRENT_REQUESTS)

    suspend fun reconcile(
        result: CommentThreadLoadResult.Algolia,
        topLevelIds: List<Int>,
        filteredUsers: Set<String>,
        onPending: suspend () -> Unit,
    ): CommentThreadLoadResult.Algolia {
        if (topLevelIds.isEmpty()) return result
        val payload = withContext(dispatcher) {
            // Prepared content is unfiltered. Most discussions need neither another JSON parse
            // nor an official request. Check raw roots too before treating deleted/blank nodes
            // omitted by the display parser as missing from Algolia.
            val present = result.parsed.cacheSummary?.preparedThread?.comments
                ?.asSequence()?.filter { it.depth == 0 }?.map { it.id }?.toSet()
            if (present != null && topLevelIds.all { it in present }) null
            else Json.parseToJsonElement(result.response) as? JsonObject
        } ?: return result
        val children = payload["children"] as? JsonArray ?: JsonArray(emptyList())
        val present = children.mapNotNullTo(mutableSetOf()) {
            ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.intOrNull
        }
        val missing = topLevelIds.asSequence().filter { it > 0 && it !in present }
            .distinct().take(MAX_MISSING_ROOTS).toList()
        if (missing.isEmpty()) return result
        onPending()
        val additions = withContext(dispatcher) {
            coroutineScope {
                missing.map { id -> async {
                    // Include permit waiting in the deadline; a slow root must not hold up the
                    // rest of the discussion. Parent cancellation still propagates normally.
                    withTimeoutOrNull(RECOVERY_TIMEOUT_MILLIS) {
                        val comment = try {
                            requests.withPermit { official.getComment(id) }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        } ?: return@withTimeoutOrNull null
                        val text = comment.text
                        if (comment.id != id || comment.parent != result.parsed.id ||
                            comment.by.isNullOrBlank() || text.isNullOrBlank() ||
                            text.trim().equals("null", ignoreCase = true)
                        ) return@withTimeoutOrNull null
                        buildJsonObject {
                            put("id", id)
                            put("parent_id", comment.parent)
                            put("author", comment.by)
                            put("text", text)
                            put("created_at_i", comment.time)
                            put("children", JsonArray(emptyList()))
                        }
                    }
                } }.awaitAll().filterNotNull()
            }
        }
        if (additions.isEmpty()) return result
        return withContext(dispatcher) {
            // Persist the supplemented payload too: raw JSON, prepared cache, content digest,
            // ranking and filtered presentation must all describe the same recovered thread.
            val response = JsonObject(payload + ("children" to JsonArray(children + additions))).toString()
            CommentThreadLoadResult.Algolia(response, parser.parseForDisplay(response, topLevelIds, filteredUsers))
        }
    }

    private companion object {
        const val MAX_MISSING_ROOTS = 8
        const val MAX_CONCURRENT_REQUESTS = 4
        const val RECOVERY_TIMEOUT_MILLIS = 3_000L
    }
}
