package com.simon.harmonichackernews.network

import android.os.Handler
import android.os.Looper
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.JSONParser.AlgoliaCommentsResponse
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import com.simon.harmonichackernews.serialization.JsonException as JSONException

object BackgroundJSONParser {
    private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Parse Algolia JSON response on a background thread
     * @param jsonResponse The JSON string to parse
     * @param callback Callback to receive results on main thread
     */
    fun parseAlgoliaJson(jsonResponse: String?, callback: AlgoliaParseCallback): Future<*>? {
        return executorService.submit(Runnable parseTask@ {
            try {
                val stories = JSONParser.algoliaJsonToStories(jsonResponse.orEmpty())
                if (Thread.interrupted()) {
                    return@parseTask
                }
                mainHandler.post { callback.onParseSuccess(stories) }
            } catch (e: JSONException) {
                if (Thread.interrupted()) {
                    return@parseTask
                }
                mainHandler.post { callback.onParseError(e) }
            }
        })
    }

    /**
     * Parse an Algolia story-with-comments response on a background thread.
     * 
     * @param jsonResponse The JSON string to parse
     * @param topLevelCommentIds Preferred top-level comment ordering
     * @param filteredUsers Usernames whose comments should be omitted
     * @param callback Callback to receive results on the main thread
     */
    fun parseAlgoliaCommentsJson(
        jsonResponse: String?,
        topLevelCommentIds: IntArray?,
        filteredUsers: Set<String>?,
        callback: AlgoliaCommentsParseCallback
    ): Future<*>? {
        return executorService.submit(Runnable parseTask@ {
            try {
                val response =
                    JSONParser.parseAlgoliaCommentsResponse(
                        jsonResponse,
                        topLevelCommentIds,
                        filteredUsers
                    )
                if (Thread.interrupted()) {
                    return@parseTask
                }
                mainHandler.post { callback.onParseSuccess(response) }
            } catch (e: IOException) {
                if (Thread.interrupted()) {
                    return@parseTask
                }
                mainHandler.post { callback.onParseError(e) }
            }
        })
    }

    interface AlgoliaParseCallback {
        /**
         * Called on the main thread when parsing succeeds
         * @param stories List of parsed stories
         */
        fun onParseSuccess(stories: MutableList<Story>)

        /**
         * Called on the main thread when parsing fails
         * @param error The exception that occurred
         */
        fun onParseError(error: JSONException)
    }

    interface AlgoliaCommentsParseCallback {
        /**
         * Called on the main thread when parsing succeeds.
         * 
         * @param response Parsed story and comments
         */
        fun onParseSuccess(response: AlgoliaCommentsResponse)

        /**
         * Called on the main thread when parsing fails.
         * 
         * @param error The exception that occurred
         */
        fun onParseError(error: IOException)
    }
}
