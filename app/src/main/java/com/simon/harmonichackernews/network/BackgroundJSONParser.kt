package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.JSONParser.AlgoliaCommentsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

/** CPU-bound JSON parsing kept off the main thread and cancelled with its caller's coroutine. */
object BackgroundJSONParser {
    suspend fun parseAlgoliaStories(jsonResponse: String?): MutableList<Story> =
        runInterruptible(Dispatchers.Default) {
            JSONParser.algoliaJsonToStories(jsonResponse.orEmpty())
        }

    suspend fun parseAlgoliaComments(
        jsonResponse: String?,
        topLevelCommentIds: IntArray?,
        filteredUsers: Set<String>?,
    ): AlgoliaCommentsResponse = runInterruptible(Dispatchers.Default) {
        JSONParser.parseAlgoliaCommentsResponse(
            jsonResponse,
            topLevelCommentIds,
            filteredUsers,
        )
    }
}
