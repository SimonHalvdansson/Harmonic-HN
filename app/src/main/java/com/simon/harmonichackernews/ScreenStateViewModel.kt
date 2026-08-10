package com.simon.harmonichackernews

import androidx.lifecycle.ViewModel
import com.simon.harmonichackernews.data.CommentsScrollProgress
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.presentation.CommentsSessionState
import com.simon.harmonichackernews.presentation.SubmissionsSessionState
import com.simon.harmonichackernews.presentation.SubmissionsStore

/** Retains content state whose Android coordinators are recreated with the Activity. */
class ScreenStateViewModel : ViewModel() {
    private var commentsKey: Int? = null
    private var commentsState = CommentsSessionState()
    private val commentsScrollProgresses = mutableMapOf<Int, CommentsScrollProgress>()

    private var submissionsKey: Int? = null
    private var submissionsUserName: String? = null
    private var submissionsState: SubmissionsSessionState? = null

    fun commentsStateFor(key: Int, storyId: Int): CommentsSessionState {
        if (commentsKey != key) {
            commentsKey = key
            val scrollProgress = commentsScrollProgresses.getOrPut(storyId) {
                CommentsScrollProgress().apply { this.storyId = storyId }
            }
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
}
