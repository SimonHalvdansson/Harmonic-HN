package com.simon.harmonichackernews.network

import android.util.Log
import com.simon.harmonichackernews.data.Comment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Callback adapter for the existing comments UI over the suspend-first repository. */
class HNAPICommentLoader(
    private val repository: HackerNewsRepository,
    private val scope: CoroutineScope,
    private val filteredUsers: Set<String>,
    private val listener: CommentLoadListener,
) {
    interface CommentLoadListener {
        fun onCommentLoaded(comment: Comment)
        fun onCommentFailed(commentId: Int)
    }

    fun loadComment(commentId: Int, depth: Int) {
        scope.launch {
            try {
                val comment = repository.getComment(commentId)
                val author = comment?.by
                if (comment != null && author != null && author.lowercase() !in filteredUsers) {
                    comment.depth = depth
                    listener.onCommentLoaded(comment)
                } else {
                    Log.w(
                        TAG,
                        "Skipping HN API comment, commentId=$commentId" +
                            ", parsed=${comment != null}, hasAuthor=${author != null}",
                    )
                    listener.onCommentFailed(commentId)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "HN API comment request failed, commentId=$commentId", error)
                listener.onCommentFailed(commentId)
            }
        }
    }

    private companion object {
        const val TAG = "HNAPICommentLoader"
    }
}
