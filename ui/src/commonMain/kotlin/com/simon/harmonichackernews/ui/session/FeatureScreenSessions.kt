package com.simon.harmonichackernews.ui.session

import com.simon.harmonichackernews.app.EditorFeatureSession
import com.simon.harmonichackernews.app.EditorFeatureSessionEvent
import com.simon.harmonichackernews.presentation.EditorSubmission
import com.simon.harmonichackernews.presentation.EditorWorkflowResult
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shared editor state/effect bridge; platform hosts only present native dialogs/navigation. */
class EditorScreenSession(
    private val scope: CoroutineScope,
    private val feature: EditorFeatureSession,
) {
    private val mutableSubmitting = MutableStateFlow(feature.isSubmitting)
    private val mutableResults = MutableSharedFlow<EditorWorkflowResult>(extraBufferCapacity = 8)
    private val job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        feature.events.collect { event ->
            when (event) {
                is EditorFeatureSessionEvent.Submitting -> mutableSubmitting.value = event.value
                is EditorFeatureSessionEvent.Result -> mutableResults.emit(event.value)
            }
        }
    }
    val submitting: StateFlow<Boolean> = mutableSubmitting.asStateFlow()
    val results: SharedFlow<EditorWorkflowResult> = mutableResults.asSharedFlow()

    fun submit(submission: EditorSubmission) = feature.submit(submission)
    fun respondToCaptcha(challenge: HackerNewsCaptchaChallenge, response: String) =
        feature.respondToCaptcha(challenge, response)
    fun cancelCaptcha() = feature.cancelCaptcha()
    fun dispose() = job.cancel()
}
