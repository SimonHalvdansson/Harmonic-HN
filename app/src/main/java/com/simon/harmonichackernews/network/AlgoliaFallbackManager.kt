package com.simon.harmonichackernews.network

import android.content.Context
import android.util.Log
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.TimeoutError
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
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

    private var treeBuilder: CommentTreeBuilder? = null
    private val commentLoader: HNAPICommentLoader
    private val allCommentIds: MutableSet<Int> = HashSet()
    private val loadedCommentIds: MutableSet<Int> = HashSet()
    private var totalExpectedComments = 0

    init {
        this.commentLoader = HNAPICommentLoader(queue, requestTag, filteredUsers, this)
    }

    fun loadComments(storyId: Int, cachedResponse: String?) {
        if (SettingsUtils.shouldUseAlgoliaAPI(context)) {
            Log.d(
                TAG,
                "Loading storyId=" + storyId + " with Algolia, hasCachedResponse=" + (cachedResponse != null)
            )
            loadWithAlgolia(storyId, cachedResponse)
        } else {
            Log.d(TAG, "Loading storyId=" + storyId + " with HN API because Algolia is disabled")
            loadWithHNAPI(storyId)
        }
    }

    private fun loadWithAlgolia(storyId: Int, cachedResponse: String?) {
        val url = "https://hn.algolia.com/api/v1/items/" + storyId

        val request = StringRequest(
            Request.Method.GET, url,
            Response.Listener { response: String? ->
                listener.onAlgoliaSuccess(response)
            },
            Response.ErrorListener { error: VolleyError? ->
                Log.w(
                    TAG,
                    "Algolia request failed for storyId=" + storyId + ": " + VolleyErrorUtils.describe(
                        error
                    ),
                    error
                )
                // If Algolia fails, try HN API
                if (error!!.networkResponse != null &&
                    (error.networkResponse.statusCode == 404 || error.networkResponse.statusCode >= 500) ||
                    error is TimeoutError
                ) {
                    Log.d(TAG, "Falling back to HN API for storyId=" + storyId)
                    loadWithHNAPI(storyId)
                    listener.onUsingFallback()
                } else {
                    listener.onAlgoliaFailed(error.networkResponse == null)
                }
            })

        request.setTag(requestTag)
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
            Request.Method.GET, url,
            Response.Listener { response: String? ->
                val story = Story()
                if (JSONParser.updateStoryWithOfficialHNResponse(story, response)) {
                    Log.d(
                        TAG, ("HN API story loaded for storyId=" + storyId
                                + ", topLevelComments=" + (if (story.kids == null) 0 else story.kids!!.size))
                    )
                    listener.onHNAPIStoryLoaded(story)


                    // Start loading all comments
                    if (story.kids != null && story.kids!!.size > 0) {
                        loadAllComments(story.kids!!)
                    } else {
                        // No comments
                        listener.onAllCommentsLoaded(ArrayList())
                    }
                } else {
                    Log.w(
                        TAG, ("HN API story parse failed for storyId=" + storyId
                                + ", responseLength=" + (if (response == null) 0 else response.length))
                    )
                    listener.onHNAPIFailed()
                }
            },
            Response.ErrorListener { error: VolleyError? ->
                Log.w(
                    TAG,
                    "HN API story request failed for storyId=" + storyId + ": " + VolleyErrorUtils.describe(
                        error
                    ),
                    error
                )
                listener.onHNAPIFailed()
            })

        request.setTag(requestTag)
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
        treeBuilder!!.addComment(comment)
        loadedCommentIds.add(comment.id)


        // Load children if they exist
        if (comment.kidsIds != null && comment.kidsIds!!.size > 0) {
            for (childId in comment.kidsIds) {
                if (!allCommentIds.contains(childId)) {
                    allCommentIds.add(childId)
                    totalExpectedComments++
                    commentLoader.loadComment(childId, comment.depth + 1)
                }
            }
        }


        // Check if all comments are loaded
        if (loadedCommentIds.size >= totalExpectedComments) {
            val orderedComments = treeBuilder!!.buildOrderedTree()
            listener.onAllCommentsLoaded(orderedComments)
        }
    }

    override fun onCommentFailed(commentId: Int) {
        loadedCommentIds.add(commentId) // Mark as processed even if failed
        Log.w(
            TAG, ("HN API comment failed, commentId=" + commentId
                    + ", loadedOrFailed=" + loadedCommentIds.size
                    + ", totalExpected=" + totalExpectedComments)
        )


        // Check if we're done (including failed ones)
        if (loadedCommentIds.size >= totalExpectedComments) {
            val orderedComments = treeBuilder!!.buildOrderedTree()
            listener.onAllCommentsLoaded(orderedComments)
        }
    }

    companion object {
        private const val TAG = "AlgoliaFallbackManager"
    }
}
