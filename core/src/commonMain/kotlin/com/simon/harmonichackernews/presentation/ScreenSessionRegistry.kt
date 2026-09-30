package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.CommentsScrollProgress
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.navigation.MainNavigationEntry
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot

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
    private var commentsState: CommentsSessionState? = null
    private val commentsScrollProgresses = mutableMapOf<Int, CommentsScrollProgress>()

    private var submissionsKey: Int? = null
    private var submissionsUserName: String? = null
    private var submissionsState: SubmissionsSessionState? = null

    fun commentsStateFor(key: Int, storyId: Int): CommentsSessionState {
        if (commentsKey != key || commentsState == null) {
            commentsKey = key
            val scrollProgress = commentScrollProgressFor(storyId)
            commentsState = CommentsSessionState(scrollProgress)
        }
        return checkNotNull(commentsState)
    }

    /**
     * Drop only the registry's ownership when an entry is permanently removed. Its outgoing UI
     * still owns the session until the exit finishes; entries covered by another screen and
     * activity recreation keep their state. Small per-story scroll/collapse records stay intact.
     */
    fun navigationChanged(navigation: MainNavigationSnapshot) {
        if (navigation.destinationStack.none {
                it is MainNavigationEntry.Story && it.request.serial == commentsKey
            }
        ) {
            commentsKey = null
            commentsState = null
        }
        if (navigation.destinationStack.none {
                it is MainNavigationEntry.Submissions && it.request.serial == submissionsKey
            }
        ) {
            submissionsKey = null
            submissionsUserName = null
            submissionsState = null
        }
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
                SubmissionsListStore(userName, repository),
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
