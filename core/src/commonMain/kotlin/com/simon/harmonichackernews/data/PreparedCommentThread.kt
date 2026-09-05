package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.network.AlgoliaCommentsResponse
import com.simon.harmonichackernews.network.AlgoliaStorySummary
import com.simon.harmonichackernews.network.StableHash
import com.simon.harmonichackernews.serialization.JsonObject
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlin.coroutines.coroutineContext

/** Eager, user-independent content. Field order is part of the versioned ProtoBuf schema. */
@Serializable
data class PreparedCommentThread(
    val schemaVersion: Int,
    val textPreparationVersion: Int,
    val sourceDigest: String,
    val story: PreparedCommentStory,
    val comments: List<PreparedCommentRecord>,
    val summaryJson: String,
    val rankedIds: List<Int>,
) {
    /** Applies today's filters/ranking and creates fresh mutable presentation state for each open. */
    suspend fun restore(
        topLevelCommentIds: List<Int> = rankedIds,
        filteredUsers: Set<String> = emptySet(),
    ): AlgoliaCommentsResponse {
        require(isCompatible()) { "Incompatible prepared comment cache" }
        val blocked = filteredUsers.mapNotNullTo(mutableSetOf()) {
            it.trim().lowercase().takeIf(String::isNotEmpty)
        }
        val roots = ArrayList<Int>()
        var index = 0
        while (index < comments.size) {
            roots += index
            index = comments[index].subtreeEndExclusive
        }
        if (topLevelCommentIds.isNotEmpty()) {
            val priorities = HashMap<Int, Int>(topLevelCommentIds.size)
            topLevelCommentIds.forEachIndexed { priority, id ->
                if (id !in priorities) priorities[id] = priority
            }
            roots.sortBy { priorities[comments[it].id] ?: topLevelCommentIds.size }
        }
        val restored = ArrayList<Comment>(comments.size)
        for (root in roots) {
            index = root
            val end = comments[root].subtreeEndExclusive
            while (index < end) {
                coroutineContext.ensureActive()
                val record = comments[index]
                if (blocked.isNotEmpty() && record.author.lowercase() in blocked) {
                    index = record.subtreeEndExclusive
                    continue
                }
                restored += Comment().apply {
                    id = record.id
                    parent = record.parentId
                    by = record.author
                    time = record.createdAtEpochSeconds
                    depth = record.depth
                    children = record.childCount
                    expanded = true
                    restorePreparedText(record.html, record.expandedHtml ?: record.html)
                }
                index++
            }
        }
        return AlgoliaCommentsResponse(
            comments = restored, title = story.title, points = story.points,
            createdAtEpochSeconds = story.createdAtEpochSeconds, type = story.type,
            author = story.author, storyId = story.storyId, parentId = story.parentId,
            storyTitle = story.storyTitle, url = story.url, text = story.text, id = story.id,
            cacheSummary = cacheSummary(topLevelCommentIds),
        )
    }

    internal fun cacheSummary(topLevelCommentIds: List<Int> = rankedIds): AlgoliaStorySummary {
        val metadata = JsonObject(summaryJson)
        return AlgoliaStorySummary(
            metadata, metadata.optInt("descendants", comments.size), topLevelCommentIds.toList(), this,
        )
    }

    internal fun isCompatible(): Boolean {
        if (schemaVersion != SCHEMA_VERSION || textPreparationVersion != TEXT_PREPARATION_VERSION) return false
        if (sourceDigest.length != 64) return false
        // Validate subtree boundaries before any indexed traversal. IDs may be absent/duplicated in
        // permissive API payloads; structural depth, rather than ID uniqueness, defines subtrees.
        val ancestors = IntArray(comments.size)
        var count = 0
        for (index in comments.indices) {
            val record = comments[index]
            while (count > 0 && comments[ancestors[count - 1]].depth >= record.depth) {
                if (comments[ancestors[--count]].subtreeEndExclusive != index) return false
            }
            if (record.depth != count || record.childCount < 0) return false
            ancestors[count++] = index
        }
        while (count > 0) {
            if (comments[ancestors[--count]].subtreeEndExclusive != comments.size) return false
        }
        return true
    }

    companion object {
        const val SCHEMA_VERSION = 1
        // Bump whenever normalization, deleted-parent handling, or expanded-link HTML changes.
        const val TEXT_PREPARATION_VERSION = 1

        internal suspend fun fromParsed(
            response: String,
            parsed: AlgoliaCommentsResponse,
            rankedIds: List<Int>,
        ): PreparedCommentThread {
            val source = parsed.comments
            val ends = IntArray(source.size) { source.size }
            val ancestors = IntArray(source.size)
            var count = 0
            for (index in source.indices) {
                while (count > 0 && source[ancestors[count - 1]].depth >= source[index].depth) {
                    ends[ancestors[--count]] = index
                }
                ancestors[count++] = index
            }
            val records = source.mapIndexed { index, comment ->
                coroutineContext.ensureActive()
                val html = comment.text.orEmpty()
                PreparedCommentRecord(
                    comment.id, comment.parent, comment.by.orEmpty(), comment.time,
                    html, comment.expandedAnchorText?.takeUnless { it == html },
                    comment.depth, comment.children, ends[index],
                )
            }
            return PreparedCommentThread(
                SCHEMA_VERSION, TEXT_PREPARATION_VERSION, StableHash.sha256Hex(response),
                PreparedCommentStory(
                    parsed.id, parsed.title, parsed.points, parsed.createdAtEpochSeconds,
                    parsed.type, parsed.author, parsed.storyId, parsed.parentId,
                    parsed.storyTitle, parsed.url, parsed.text,
                ),
                records, requireNotNull(parsed.cacheSummary).encode(parsed.id), rankedIds.toList(),
            )
        }
    }
}

@Serializable
data class PreparedCommentStory(
    val id: Int, val title: String, val points: Int, val createdAtEpochSeconds: Int,
    val type: String, val author: String, val storyId: Int, val parentId: Int,
    val storyTitle: String, val url: String, val text: String,
)

@Serializable
data class PreparedCommentRecord(
    val id: Int, val parentId: Int, val author: String, val createdAtEpochSeconds: Int,
    val html: String,
    /** Omitted when expansion leaves HTML unchanged, avoiding duplicate stored comment text. */
    val expandedHtml: String? = null,
    val depth: Int, val childCount: Int, val subtreeEndExclusive: Int,
)
