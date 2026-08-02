package com.simon.harmonichackernews.network

import android.util.Log
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.data.Comment
import java.util.Locale
import org.json.JSONException

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
            Request.Method.GET, url,
            Response.Listener { response: String? ->
                try {
                    val comment = JSONParser.parseOfficialHNCommentResponse(response.orEmpty())
                    if (comment != null && comment.by != null && !filteredUsers.contains(
                            comment.by!!.lowercase(
                                Locale.getDefault()
                            )
                        )
                    ) {
                        comment.depth = depth
                        listener.onCommentLoaded(comment)
                    } else {
                        Log.w(
                            TAG, ("Skipping HN API comment, commentId=" + commentId
                                    + ", parsed=" + (comment != null)
                                    + ", hasAuthor=" + (comment != null && comment.by != null)
                                    + ", responseLength=" + (if (response == null) 0 else response.length))
                        )
                        listener.onCommentFailed(commentId)
                    }
                } catch (e: JSONException) {
                    Log.w(
                        TAG,
                        ("Failed to parse HN API comment, commentId=" + commentId
                                + ", responseLength=" + (if (response == null) 0 else response.length)),
                        e
                    )
                    listener.onCommentFailed(commentId)
                }
            },
            Response.ErrorListener { error: VolleyError? ->
                Log.w(
                    TAG,
                    "HN API comment request failed, commentId=" + commentId + ": " + VolleyErrorUtils.describe(
                        error
                    ),
                    error
                )
                listener.onCommentFailed(commentId)
            })

        request.setTag(requestTag)
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
