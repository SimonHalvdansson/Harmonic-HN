package com.simon.harmonichackernews

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.presentation.CommentsSessionState
import com.simon.harmonichackernews.presentation.SubmissionsSessionState

/** Retains content state whose Android coordinators are recreated with the Activity. */
class ScreenStateViewModel(application: Application) : AndroidViewModel(application) {
    private val sessions = AndroidAppComposition.get(application).sessions

    fun commentsStateFor(key: Int, storyId: Int): CommentsSessionState =
        sessions.commentsStateFor(key, storyId)

    fun submissionsStateFor(
        key: Int,
        userName: String,
        repository: AlgoliaRepository,
    ): SubmissionsSessionState = sessions.submissionsStateFor(key, userName, repository)
}
