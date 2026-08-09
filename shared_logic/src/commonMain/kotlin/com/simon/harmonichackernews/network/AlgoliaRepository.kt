package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.dto.AlgoliaSearchResponseDto
import com.simon.harmonichackernews.network.dto.toStory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

interface AlgoliaRepository {
    suspend fun getSubmissions(userName: String, limit: Int): List<Story>
    suspend fun search(url: String): List<Story>
    suspend fun getItemJson(id: Int): String
}

class KtorAlgoliaRepository(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : AlgoliaRepository {
    override suspend fun getSubmissions(userName: String, limit: Int): List<Story> {
        require(userName.isNotBlank()) { "A username is required" }
        require(limit > 0) { "A positive result limit is required" }
        val url = URLBuilder("$ALGOLIA_API/search_by_date").apply {
            parameters.append("tags", "author_$userName")
            parameters.append("hitsPerPage", limit.toString())
        }.buildString()
        return search(url)
    }

    override suspend fun search(url: String): List<Story> {
        val body = get(url)
        return try {
            json.decodeFromString<AlgoliaSearchResponseDto>(body)
                .hits
                .mapNotNull { it.toStory() }
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
                return get(url)
            } catch (error: HttpRequestTimeoutException) {
                if (++attempt >= ITEM_REQUEST_ATTEMPTS) throw error
            } catch (error: HttpStatusException) {
                if (error.statusCode < 500 || ++attempt >= ITEM_REQUEST_ATTEMPTS) throw error
            }
        }
    }

    private suspend fun get(url: String): String {
        val response = client.get(url)
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw HttpStatusException(response.status.value, response.status.description, url)
        }
        return body
    }

    private companion object {
        const val ALGOLIA_API = "https://hn.algolia.com/api/v1"
        const val ITEM_REQUEST_ATTEMPTS = 3
    }
}
