package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.dto.AlgoliaSearchResponseDto
import com.simon.harmonichackernews.network.dto.toStory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.URLBuilder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

enum class AlgoliaSubmissionType { BOTH, STORIES, COMMENTS }

data class AlgoliaSubmissionsPage(
    val items: List<Story>,
    val canLoadMore: Boolean,
)

interface AlgoliaRepository {
    suspend fun getSubmissions(
        userName: String,
        limit: Int,
        type: AlgoliaSubmissionType = AlgoliaSubmissionType.BOTH,
    ): AlgoliaSubmissionsPage
    suspend fun search(url: String): List<Story>
    suspend fun getItemJson(id: Int): String
}

class KtorAlgoliaRepository(
    private val client: suspend () -> HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AlgoliaRepository {
    constructor(client: HttpClient, json: Json = Json { ignoreUnknownKeys = true }) :
        this({ client }, json)
    override suspend fun getSubmissions(
        userName: String,
        limit: Int,
        type: AlgoliaSubmissionType,
    ): AlgoliaSubmissionsPage {
        require(userName.isNotBlank()) { "A username is required" }
        require(limit > 0) { "A positive result limit is required" }
        val url = URLBuilder("$ALGOLIA_API/search_by_date").apply {
            val tags = when (type) {
                AlgoliaSubmissionType.BOTH -> "author_$userName"
                AlgoliaSubmissionType.STORIES -> "author_$userName,(story,poll)"
                AlgoliaSubmissionType.COMMENTS -> "author_$userName,comment"
            }
            parameters.append("tags", tags)
            parameters.append("hitsPerPage", limit.toString())
        }.buildString()
        val response = searchResponse(url)
        return AlgoliaSubmissionsPage(
            items = response.hits.mapNotNull { it.toStory() },
            canLoadMore = response.page + 1 < response.nbPages,
        )
    }

    override suspend fun search(url: String): List<Story> =
        searchResponse(url).hits.mapNotNull { it.toStory() }

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

    override suspend fun getItemJson(id: Int): String {
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
