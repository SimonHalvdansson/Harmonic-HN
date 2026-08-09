package com.simon.harmonichackernews.network

import android.util.Log
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.settings.UserSettings
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AlgoliaFallbackManager(
    private val userSettings: UserSettings,
    private val filteredUsers: Set<String>,
    private val listener: FallbackListener,
    private val algoliaRepository: AlgoliaRepository = NetworkComponent.algoliaRepository,
    private val hackerNewsRepository: HackerNewsRepository = NetworkComponent.hackerNewsRepository,
) {
    interface FallbackListener {
        fun onAlgoliaSuccess(response: String?)
        fun onAlgoliaFailed(noInternet: Boolean)
        fun onUsingFallback()
        fun onHNAPIStoryLoaded(story: Story)
        fun onHNAPIFailed()
        fun onAllCommentsLoaded(comments: MutableList<Comment>)
    }

    private lateinit var treeBuilder: CommentTreeBuilder
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val allCommentIds = mutableSetOf<Int>()
    private val loadedCommentIds = mutableSetOf<Int>()
    private var totalExpectedComments = 0

    fun loadComments(storyId: Int, cachedResponse: String?) {
        if (userSettings.reading.useAlgoliaApi) {
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
        scope.launch {
            try {
                val response = algoliaRepository.getItemJson(storyId)
                listener.onAlgoliaSuccess(response)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(
                    TAG,
                    "Algolia request failed for storyId=$storyId: ${error.message}",
                    error
                )
                val shouldFallback = error is HttpRequestTimeoutException ||
                    error is HttpStatusException &&
                    (error.statusCode == 404 || error.statusCode >= 500)
                if (shouldFallback) {
                    Log.d(TAG, "Falling back to HN API for storyId=" + storyId)
                    loadWithHNAPI(storyId)
                    listener.onUsingFallback()
                } else {
                    listener.onAlgoliaFailed(error !is HttpStatusException)
                }
            }
        }
    }

    private fun loadWithHNAPI(storyId: Int) {
        scope.launch {
            try {
                val story = hackerNewsRepository.getStory(storyId)
                if (story != null) {
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
                    Log.w(TAG, "HN API story parse failed for storyId=$storyId")
                    listener.onHNAPIFailed()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "HN API story request failed for storyId=$storyId", error)
                listener.onHNAPIFailed()
            }
        }
    }

    fun dispose() {
        scope.cancel()
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
            loadComment(commentId, 0)
        }

        totalExpectedComments = allCommentIds.size
    }

    private fun loadComment(commentId: Int, depth: Int) {
        scope.launch {
            try {
                val comment = hackerNewsRepository.getComment(commentId)?.also { loadedComment ->
                    loadedComment.expanded = true
                    loadedComment.depth = depth
                }
                val author = comment?.by
                if (comment != null && author != null && author.lowercase() !in filteredUsers) {
                    onCommentLoaded(comment)
                } else {
                    onCommentFailed(commentId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "HN API comment request failed, commentId=$commentId", error)
                onCommentFailed(commentId)
            }
        }
    }

    private fun onCommentLoaded(comment: Comment) {
        treeBuilder.addComment(comment)
        loadedCommentIds.add(comment.id)


        // Load children if they exist
        comment.kidsIds?.forEach { childId ->
            if (allCommentIds.add(childId)) {
                totalExpectedComments++
                loadComment(childId, comment.depth + 1)
            }
        }

        notifyIfComplete()
    }

    private fun onCommentFailed(commentId: Int) {
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
