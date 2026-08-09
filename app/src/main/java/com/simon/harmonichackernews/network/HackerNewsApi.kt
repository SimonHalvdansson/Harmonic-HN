package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.applyTo
import com.simon.harmonichackernews.network.dto.toComment
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Suspend-first wire API. No Android lifecycle, callbacks, or Context cross this boundary. */
interface HackerNewsApi {
    suspend fun getItem(id: Int): HackerNewsItemDto?
    suspend fun getTopStoryIds(): List<Int>
}

class KtorHackerNewsApi(
    private val client: KtorHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : HackerNewsApi {
    override suspend fun getItem(id: Int): HackerNewsItemDto? {
        require(id > 0) { "A positive Hacker News item ID is required" }
        val body = get("$API_BASE/item/$id.json")
        if (body.isBlank() || body.trim() == "null") return null
        return decode(body)
    }

    override suspend fun getTopStoryIds(): List<Int> = decode(get("$API_BASE/topstories.json"))

    private suspend fun get(url: String): String {
        val request = HttpRequest.Builder().url(url).get().build()
        return client.execute(request).use { response ->
            if (!response.isSuccessful) {
                throw HttpStatusException(response.code, response.message, url)
            }
            response.body.readText()
        }
    }

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString(body)
    } catch (error: SerializationException) {
        throw IOException("Invalid Hacker News API response", error)
    } catch (error: IllegalArgumentException) {
        throw IOException("Invalid Hacker News API response", error)
    }

    private companion object {
        const val API_BASE = "https://hacker-news.firebaseio.com/v0"
    }
}

class HttpStatusException(
    val statusCode: Int,
    statusMessage: String,
    url: String,
) : IOException("HTTP $statusCode $statusMessage for $url")

/** Domain-facing API kept separate from wire DTOs. */
interface HackerNewsRepository {
    suspend fun getStory(id: Int): Story?
    suspend fun getComment(id: Int): Comment?
    suspend fun getTopStoryIds(): List<Int>
}

class DefaultHackerNewsRepository(private val api: HackerNewsApi) : HackerNewsRepository {
    override suspend fun getStory(id: Int): Story? {
        val item = api.getItem(id) ?: return null
        return Story().takeIf { item.applyTo(it) }
    }

    override suspend fun getComment(id: Int): Comment? = api.getItem(id)?.toComment()

    override suspend fun getTopStoryIds(): List<Int> = api.getTopStoryIds()
}
