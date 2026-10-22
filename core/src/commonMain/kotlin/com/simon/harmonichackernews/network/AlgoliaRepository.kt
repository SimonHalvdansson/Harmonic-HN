package com.simon.harmonichackernews.network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.dto.AlgoliaSearchResponseDto
import com.simon.harmonichackernews.network.dto.toStory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.URLBuilder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

enum class AlgoliaSubmissionType { BOTH, STORIES, COMMENTS }

data class AlgoliaSubmissionCount(val value: Int, val exact: Boolean)

/** A page within a date window. The inclusive boundary preserves timestamp ties. */
data class AlgoliaSubmissionsCursor(
    val page: Int = 0,
    val throughEpochSeconds: Int? = null,
)

data class AlgoliaSubmissionsPage(
    val items: List<Story>,
    val nextCursor: AlgoliaSubmissionsCursor? = null,
    val totalCount: AlgoliaSubmissionCount? = null,
) {
    val canLoadMore: Boolean get() = nextCursor != null
}

/** Search ranking is page based; nbHits can exceed the number of accessible results. */
data class AlgoliaSearchPage(
    val stories: List<Story>,
    val page: Int,
    val pageCount: Int,
) {
    val nextPage: Int? get() = (page + 1).takeIf { it < pageCount }
}

/** A user's comment reduced to what is needed to mark the story it belongs to. */
data class AlgoliaUserComment(val storyId: Int, val createdAtEpochSeconds: Int)

interface AlgoliaRepository {
    /** Set [pageSize] to zero for totals without content. Reuse the returned cursor for older pages. */
    suspend fun getSubmissions(
        userName: String,
        pageSize: Int,
        type: AlgoliaSubmissionType = AlgoliaSubmissionType.BOTH,
        cursor: AlgoliaSubmissionsCursor = AlgoliaSubmissionsCursor(),
    ): AlgoliaSubmissionsPage
    suspend fun search(url: String): AlgoliaSearchPage
    suspend fun getItemJson(id: Int): String

    /** Up to the newest 1,000 comments, limited to those created at or after [sinceEpochSeconds]. */
    suspend fun getUserComments(
        userName: String,
        sinceEpochSeconds: Int? = null,
    ): List<AlgoliaUserComment> =
        getSubmissions(userName, USER_COMMENTS_LIMIT, AlgoliaSubmissionType.COMMENTS).items
            .filter { sinceEpochSeconds == null || it.createdAtEpochSeconds >= sinceEpochSeconds }
            .mapNotNull { comment ->
                comment.rootStoryId.takeIf { it > 0 }?.let {
                    AlgoliaUserComment(it, comment.createdAtEpochSeconds)
                }
            }

    companion object {
        const val USER_COMMENTS_LIMIT = 1000
    }
}

class KtorAlgoliaRepository(
    private val client: suspend () -> HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AlgoliaRepository {
    constructor(
        client: HttpClient,
        json: Json = Json { ignoreUnknownKeys = true },
        requestDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ) : this({ client }, json, requestDispatcher)
    override suspend fun getSubmissions(
        userName: String,
        pageSize: Int,
        type: AlgoliaSubmissionType,
        cursor: AlgoliaSubmissionsCursor,
    ): AlgoliaSubmissionsPage = withContext(requestDispatcher) {
        require(userName.isNotBlank()) { "A username is required" }
        require(pageSize in 0..1000) { "Page size must be between 0 and 1000" }
        require(cursor.page >= 0) { "Page must not be negative" }
        val url = URLBuilder("$ALGOLIA_API/search_by_date").apply {
            val tags = when (type) {
                AlgoliaSubmissionType.BOTH -> "author_$userName,(story,poll,comment)"
                AlgoliaSubmissionType.STORIES -> "author_$userName,(story,poll)"
                AlgoliaSubmissionType.COMMENTS -> "author_$userName,comment"
            }
            parameters.append("tags", tags)
            parameters.append("hitsPerPage", pageSize.toString())
            parameters.append("page", cursor.page.toString())
            cursor.throughEpochSeconds?.let {
                parameters.append("numericFilters", "created_at_i<=$it")
            }
        }.buildString()
        val response = searchResponse(url)
        return@withContext AlgoliaSubmissionsPage(
            items = response.hits.mapNotNull { it.toStory() },
            nextCursor = nextSubmissionsCursor(response, pageSize, cursor),
            // Date-window counts describe the remaining history, not the user's total.
            totalCount = response.nbHits?.takeIf { cursor.throughEpochSeconds == null }?.let {
                AlgoliaSubmissionCount(it, response.exhaustiveNbHits)
            },
        )
    }

    private fun nextSubmissionsCursor(
        response: AlgoliaSearchResponseDto,
        pageSize: Int,
        cursor: AlgoliaSubmissionsCursor,
    ): AlgoliaSubmissionsCursor? {
        if (pageSize == 0) return null
        if (response.page + 1 < response.nbPages) {
            return AlgoliaSubmissionsCursor(
                page = response.page + 1,
                // Keep new submissions from shifting subsequent pages until refresh.
                throughEpochSeconds = cursor.throughEpochSeconds
                    ?: response.hits.mapNotNull { it.createdAt }.maxOrNull(),
            )
        }
        val fetched = response.page * pageSize + response.hits.size
        val mayBeCapped = fetched >= 1000 && !response.exhaustiveNbHits
        if ((response.nbHits ?: 0) <= fetched && !mayBeCapped) return null
        // Algolia caps a query at 1,000 retrievable hits. Restart at the oldest
        // second, inclusively; the store deduplicates the overlapping records.
        val oldest = response.hits.mapNotNull { it.createdAt }.minOrNull()
            ?: error("Missing timestamp for older submissions")
        if (cursor.throughEpochSeconds != null && oldest >= cursor.throughEpochSeconds) {
            error("Submissions date window did not advance")
        }
        return AlgoliaSubmissionsCursor(throughEpochSeconds = oldest)
    }

    override suspend fun getUserComments(
        userName: String,
        sinceEpochSeconds: Int?,
    ): List<AlgoliaUserComment> = withContext(requestDispatcher) {
        require(userName.isNotBlank()) { "A username is required" }
        val url = URLBuilder("$ALGOLIA_API/search_by_date").apply {
            parameters.append("tags", "author_$userName,comment")
            parameters.append("hitsPerPage", AlgoliaRepository.USER_COMMENTS_LIMIT.toString())
            // Only story IDs are needed; this keeps a full page around 70 KB instead of 1.6 MB.
            parameters.append("attributesToRetrieve", "story_id,created_at_i")
            parameters.append("attributesToHighlight", "[]")
            sinceEpochSeconds?.let { parameters.append("numericFilters", "created_at_i>=$it") }
        }.buildString()
        searchResponse(url).hits.mapNotNull { hit ->
            hit.storyId?.takeIf { it > 0 }?.let { AlgoliaUserComment(it, hit.createdAt ?: 0) }
        }
    }

    override suspend fun search(url: String): AlgoliaSearchPage = withContext(requestDispatcher) {
        val response = searchResponse(url)
        AlgoliaSearchPage(
            stories = response.hits.mapNotNull { it.toStory() },
            page = response.page,
            pageCount = response.nbPages,
        )
    }

    private suspend fun searchResponse(url: String): AlgoliaSearchResponseDto {
        val body = client().getTextOrThrow(url)
        return try {
            json.decodeFromString<AlgoliaSearchResponseDto>(body)
        } catch (error: SerializationException) {
            throw ApiDecodingException("Invalid Algolia search response", error)
        } catch (error: IllegalArgumentException) {
            throw ApiDecodingException("Invalid Algolia search response", error)
        }
    }

    override suspend fun getItemJson(id: Int): String = withContext(requestDispatcher) {
        requestItemJson(id)
    }

    private suspend fun requestItemJson(id: Int): String {
        require(id > 0) { "A positive Hacker News item ID is required" }
        val url = "$ALGOLIA_API/items/$id"
        var attempt = 0
        while (true) {
            try {
                return client().getTextOrThrow(url)
            } catch (error: HttpRequestTimeoutException) {
                if (++attempt >= ITEM_REQUEST_ATTEMPTS) throw error
            } catch (error: HttpStatusException) {
                if (error.statusCode < 500 || ++attempt >= ITEM_REQUEST_ATTEMPTS) throw error
            }
        }
    }

    private companion object {
        const val ALGOLIA_API = "https://hn.algolia.com/api/v1"
        const val ITEM_REQUEST_ATTEMPTS = 3
    }
}
