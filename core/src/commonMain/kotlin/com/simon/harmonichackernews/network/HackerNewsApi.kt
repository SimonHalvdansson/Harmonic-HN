package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import com.simon.harmonichackernews.network.dto.toComment
import com.simon.harmonichackernews.network.dto.toStory
import io.ktor.client.HttpClient
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Suspend-first wire API. No Android lifecycle, callbacks, or Context cross this boundary. */
interface HackerNewsApi {
    suspend fun getItem(id: Int): HackerNewsItemDto?
    suspend fun getUser(username: String): HackerNewsUserDto?
    suspend fun getMaxItemId(): Int
    suspend fun getStoryIds(type: StoryType): List<Int>
}

class KtorHackerNewsApi(
    private val client: suspend () -> HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : HackerNewsApi {
    constructor(client: HttpClient, json: Json = Json { ignoreUnknownKeys = true }) :
        this({ client }, json)
    override suspend fun getItem(id: Int): HackerNewsItemDto? {
        require(id > 0) { "A positive Hacker News item ID is required" }
        val body = client().getTextOrThrow("$API_BASE/item/$id.json")
        if (body.isBlank() || body.trim() == "null") return null
        return decode(body)
    }

    override suspend fun getUser(username: String): HackerNewsUserDto? {
        val normalized = username.trim()
        require(normalized.isNotEmpty()) { "A Hacker News username is required" }
        val url = URLBuilder(API_BASE)
            .appendPathSegments("user", "$normalized.json")
            .buildString()
        val body = client().getTextOrThrow(url)
        if (body.isBlank() || body.trim() == "null") return null
        return decode(body)
    }

    override suspend fun getMaxItemId(): Int =
        client().getTextOrThrow("$API_BASE/maxitem.json").trim().toIntOrNull() ?: 0

    override suspend fun getStoryIds(type: StoryType): List<Int> {
        val path = when (type) {
            StoryType.TOP_STORIES -> "top"
            StoryType.NEW_STORIES -> "new"
            StoryType.BEST_STORIES -> "best"
            StoryType.ASK_HN -> "ask"
            StoryType.SHOW_HN -> "show"
            StoryType.HN_JOBS -> "job"
            else -> throw IllegalArgumentException("$type has no official HN story-list endpoint")
        }
        return decode(client().getTextOrThrow("$API_BASE/${path}stories.json"))
    }

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString(body)
    } catch (error: SerializationException) {
        throw ApiDecodingException("Invalid Hacker News API response", error)
    } catch (error: IllegalArgumentException) {
        throw ApiDecodingException("Invalid Hacker News API response", error)
    }

    private companion object {
        const val API_BASE = "https://hacker-news.firebaseio.com/v0"
    }
}

class HttpStatusException(
    val statusCode: Int,
    statusMessage: String,
    url: String,
) : Exception("HTTP $statusCode $statusMessage for $url")

class ApiDecodingException(message: String, cause: Throwable) : Exception(message, cause)

/** Domain-facing API kept separate from wire DTOs. */
interface HackerNewsRepository {
    suspend fun getStory(id: Int): Story?
    suspend fun getComment(id: Int): Comment?
    suspend fun getStoryIds(type: StoryType): List<Int>
}

class DefaultHackerNewsRepository(private val api: HackerNewsApi) : HackerNewsRepository {
    override suspend fun getStory(id: Int): Story? = api.getItem(id)?.toStory()

    override suspend fun getComment(id: Int): Comment? = api.getItem(id)?.toComment()

    override suspend fun getStoryIds(type: StoryType): List<Int> = api.getStoryIds(type)
}
