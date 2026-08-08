package com.simon.harmonichackernews.network

import android.content.Context
import android.util.Log
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.HNAPICommentLoader.CommentLoadListener
import com.simon.harmonichackernews.utils.SettingsUtils

class AlgoliaFallbackManager(
    private val context: Context, private val queue: RequestQueue, private val requestTag: Any?,
    private val filteredUsers: Set<String>, private val listener: FallbackListener
) : CommentLoadListener {
    interface FallbackListener {
        fun onAlgoliaSuccess(response: String?)
        fun onAlgoliaFailed(noInternet: Boolean)
        fun onUsingFallback()
        fun onHNAPIStoryLoaded(story: Story)
        fun onHNAPIFailed()
        fun onAllCommentsLoaded(comments: MutableList<Comment>)
    }

    private lateinit var treeBuilder: CommentTreeBuilder
    private val commentLoader = HNAPICommentLoader(queue, requestTag, filteredUsers, this)
    private val allCommentIds = mutableSetOf<Int>()
    private val loadedCommentIds = mutableSetOf<Int>()
    private var totalExpectedComments = 0

    fun loadComments(storyId: Int, cachedResponse: String?) {
        if (SettingsUtils.shouldUseAlgoliaAPI(context)) {
            Log.d(
                TAG,
                "Loading storyId=" + storyId + " with Algolia, hasCachedResponse=" + (cachedResponse != null)
            )
            loadWithAlgolia(storyId)
        } else {
            Log.d(TAG, "Loading storyId=" + storyId + " with HN API because Algolia is disabled")
            loadWithHNAPI(storyId)
        }
    }

    private fun loadWithAlgolia(storyId: Int) {
        val url = "https://hn.algolia.com/api/v1/items/" + storyId

        val request = StringRequest(
            QueueRequest.Method.GET, url,
            QueueResponse.Listener { response: String? ->
                listener.onAlgoliaSuccess(response)
            },
            QueueResponse.ErrorListener { error: NetworkError? ->
                Log.w(
                    TAG,
                    "Algolia request failed for storyId=" + storyId + ": " + NetworkErrorUtils.describe(
                        error
                    ),
                    error
                )
                // If Algolia fails, try HN API
                val networkResponse = error?.networkResponse
                if ((networkResponse != null &&
                        (networkResponse.statusCode == 404 || networkResponse.statusCode >= 500)
                    ) || error is NetworkTimeoutError
                ) {
                    Log.d(TAG, "Falling back to HN API for storyId=" + storyId)
                    loadWithHNAPI(storyId)
                    listener.onUsingFallback()
                } else {
                    listener.onAlgoliaFailed(networkResponse == null)
                }
            })

        request.tag = requestTag
        request.setRetryPolicy(
            DefaultRetryPolicy(
                15000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        )
        queue.add<String?>(request)
    }

    private fun loadWithHNAPI(storyId: Int) {
        val url = "https://hacker-news.firebaseio.com/v0/item/" + storyId + ".json"

        val request = StringRequest(
            QueueRequest.Method.GET, url,
            QueueResponse.Listener { response: String? ->
                val story = Story()
                if (JSONParser.updateStoryWithOfficialHNResponse(story, response)) {
                    Log.d(
                        TAG, ("HN API story loaded for storyId=" + storyId
                                + ", topLevelComments=" + (story.kids?.size ?: 0))
                    )
                    listener.onHNAPIStoryLoaded(story)


                    // Start loading all comments
                    val topLevelIds = story.kids
                    if (topLevelIds != null && topLevelIds.isNotEmpty()) {
                        loadAllComments(topLevelIds)
                    } else {
                        // No comments
                        listener.onAllCommentsLoaded(mutableListOf())
                    }
                } else {
                    Log.w(
                        TAG, ("HN API story parse failed for storyId=" + storyId
                                + ", responseLength=" + (response?.length ?: 0))
                    )
                    listener.onHNAPIFailed()
                }
            },
            QueueResponse.ErrorListener { error: NetworkError? ->
                Log.w(
                    TAG,
                    "HN API story request failed for storyId=" + storyId + ": " + NetworkErrorUtils.describe(
                        error
                    ),
                    error
                )
                listener.onHNAPIFailed()
            })

        request.tag = requestTag
        request.setRetryPolicy(
            DefaultRetryPolicy(
                15000,
                2,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
            )
        )
        queue.add<String?>(request)
    }

    private fun loadAllComments(topLevelIds: IntArray) {
        treeBuilder = CommentTreeBuilder(topLevelIds)
        allCommentIds.clear()
        loadedCommentIds.clear()
        totalExpectedComments = topLevelIds.size
        Log.d(TAG, "Loading HN API comments, initialTopLevelCount=" + topLevelIds.size)


        // Discover all comment IDs by loading each comment and checking for children
        for (commentId in topLevelIds) {
            allCommentIds.add(commentId)
            commentLoader.loadComment(commentId, 0)
        }

        totalExpectedComments = allCommentIds.size
    }

    override fun onCommentLoaded(comment: Comment) {
        treeBuilder.addComment(comment)
        loadedCommentIds.add(comment.id)


        // Load children if they exist
        comment.kidsIds?.forEach { childId ->
            if (allCommentIds.add(childId)) {
                totalExpectedComments++
                commentLoader.loadComment(childId, comment.depth + 1)
            }
        }

        notifyIfComplete()
    }

    override fun onCommentFailed(commentId: Int) {
        loadedCommentIds.add(commentId) // Mark as processed even if failed
        Log.w(
            TAG, ("HN API comment failed, commentId=" + commentId
                    + ", loadedOrFailed=" + loadedCommentIds.size
                    + ", totalExpected=" + totalExpectedComments)
        )


        notifyIfComplete()
    }

    private fun notifyIfComplete() {
        // Failed comments count as processed, so completion includes both outcomes.
        if (loadedCommentIds.size >= totalExpectedComments) {
            listener.onAllCommentsLoaded(treeBuilder.buildOrderedTree())
        }
    }

    companion object {
        private const val TAG = "AlgoliaFallbackManager"
    }
}
