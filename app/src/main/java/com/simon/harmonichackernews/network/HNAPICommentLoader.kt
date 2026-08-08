package com.simon.harmonichackernews.network

import android.util.Log
import com.simon.harmonichackernews.data.Comment
import java.util.Locale
import com.simon.harmonichackernews.serialization.JsonException as JSONException

class HNAPICommentLoader(
    private val queue: RequestQueue,
    private val requestTag: Any?,
    private val filteredUsers: Set<String>,
    private val listener: CommentLoadListener
) {
    interface CommentLoadListener {
        fun onCommentLoaded(comment: Comment)
        fun onCommentFailed(commentId: Int)
    }

    fun loadComment(commentId: Int, depth: Int) {
        val url = "https://hacker-news.firebaseio.com/v0/item/" + commentId + ".json"

        val request = StringRequest(
            QueueRequest.Method.GET, url,
            QueueResponse.Listener { response: String? ->
                try {
                    val comment = JSONParser.parseOfficialHNCommentResponse(response.orEmpty())
                    val author = comment?.by
                    if (comment != null && author != null &&
                        author.lowercase(Locale.getDefault()) !in filteredUsers
                    ) {
                        comment.depth = depth
                        listener.onCommentLoaded(comment)
                    } else {
                        Log.w(
                            TAG,
                            "Skipping HN API comment, commentId=$commentId" +
                                ", parsed=${comment != null}" +
                                ", hasAuthor=${author != null}" +
                                ", responseLength=${response?.length ?: 0}"
                        )
                        listener.onCommentFailed(commentId)
                    }
                } catch (e: JSONException) {
                    Log.w(
                        TAG,
                        "Failed to parse HN API comment, commentId=$commentId" +
                            ", responseLength=${response?.length ?: 0}",
                        e
                    )
                    listener.onCommentFailed(commentId)
                }
            },
            QueueResponse.ErrorListener { error: NetworkError? ->
                Log.w(
                    TAG,
                    "HN API comment request failed, commentId=$commentId: " +
                        NetworkErrorUtils.describe(error),
                    error
                )
                listener.onCommentFailed(commentId)
            })

        request.tag = requestTag
        request.setRetryPolicy(
            DefaultRetryPolicy(
                10000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        )
        queue.add<String?>(request)
    }

    companion object {
        private const val TAG = "HNAPICommentLoader"
    }
}
