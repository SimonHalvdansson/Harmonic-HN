package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.CommentsScrollProgress
import com.simon.harmonichackernews.network.AlgoliaRepository

/**
 * Retains platform-neutral screen sessions independently of any platform lifecycle holder.
 *
 * Platform shells may keep one registry in a ViewModel, observable object, or application-owned
 * navigation scope. Replacing a navigation key creates a fresh screen session while comment
 * scroll progress remains associated with its story.
 */
class ScreenSessionRegistry(
    private val maxRetainedCommentScrollProgresses: Int = DEFAULT_MAX_RETAINED_COMMENT_SCROLLS,
) {
    init {
        require(maxRetainedCommentScrollProgresses > 0)
    }
    /** The process-retained stories session; platform lifecycle holders only reference this state. */
    val stories = StoriesSessionState()

    private var commentsKey: Int? = null
    private var commentsState = CommentsSessionState()
    private val commentsScrollProgresses = mutableMapOf<Int, CommentsScrollProgress>()

    private var submissionsKey: Int? = null
    private var submissionsUserName: String? = null
    private var submissionsState: SubmissionsSessionState? = null

    fun commentsStateFor(key: Int, storyId: Int): CommentsSessionState {
        if (commentsKey != key) {
            commentsKey = key
            val scrollProgress = commentScrollProgressFor(storyId)
            commentsState = CommentsSessionState(scrollProgress)
        }
        return commentsState
    }

    fun submissionsStateFor(
        key: Int,
        userName: String,
        repository: AlgoliaRepository,
    ): SubmissionsSessionState {
        if (submissionsKey != key || submissionsUserName != userName || submissionsState == null) {
            submissionsKey = key
            submissionsUserName = userName
            submissionsState = SubmissionsSessionState(
                SubmissionsStore(userName, repository),
            )
        }
        return checkNotNull(submissionsState)
    }

    private fun commentScrollProgressFor(storyId: Int): CommentsScrollProgress {
        commentsScrollProgresses.remove(storyId)?.let { existing ->
            commentsScrollProgresses[storyId] = existing
            return existing
        }
        while (commentsScrollProgresses.size >= maxRetainedCommentScrollProgresses) {
            commentsScrollProgresses.remove(commentsScrollProgresses.keys.first())
        }
        return CommentsScrollProgress().apply { this.storyId = storyId }.also { created ->
            commentsScrollProgresses[storyId] = created
        }
    }

    private companion object {
        const val DEFAULT_MAX_RETAINED_COMMENT_SCROLLS = 64
    }
}
