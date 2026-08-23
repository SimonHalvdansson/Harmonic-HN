package com.simon.harmonichackernews.ui.editor

import androidx.lifecycle.lifecycleScope
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.app.createEditorFeatureSession
import com.simon.harmonichackernews.presentation.CaptchaResultHandler
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.presentation.EditorSubmission
import com.simon.harmonichackernews.presentation.EditorPresentationCopy
import com.simon.harmonichackernews.presentation.EditorWorkflowResult
import com.simon.harmonichackernews.ui.session.EditorScreenSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns submission side effects while the editor itself is a MainActivity Compose destination.  */
class ComposeEditorCoordinator(
    private val activity: MainActivity,
    destination: EditorDestination,
    private val navigation: MainNavigationController,
    private val onFinished: () -> Unit,
) {
    private val id = destination.itemId
    val type = destination.type
    val parentText: String? = destination.parentText
    val postTitle: String? = destination.postTitle
    val user: String? = destination.userName
    private var controller: EditorComposeController? = null
    private val appComposition = activity.harmonicAppComposition
    private val featureSession = appComposition.createEditorFeatureSession(
        scope = activity.lifecycleScope,
        type = type,
        itemId = id,
    )
    private val screenSession = EditorScreenSession(activity.lifecycleScope, featureSession)
    private val submittingCollectionJob: Job = activity.lifecycleScope.launch {
        screenSession.submitting.collect { controller?.updateSubmitting(it) }
    }
    private val resultCollectionJob: Job = activity.lifecycleScope.launch {
        screenSession.results.collect(::handleResult)
    }
    private var closeRequested = false
    private var resultHandlingDisposed = false
    private var awaitingWorkflowResult = false

    fun attachController(controller: EditorComposeController) {
        this.controller = controller
        controller.updateSubmitting(screenSession.submitting.value)
    }

    fun submit(submission: EditorSubmission) {
        awaitingWorkflowResult = true
        screenSession.submit(submission)
    }

    fun close() {
        if (closeRequested) return
        closeRequested = true
        controller = null
        submittingCollectionJob.cancel()
        if (!awaitingWorkflowResult) disposeResultHandling()
    }

    private fun disposeResultHandling() {
        if (resultHandlingDisposed) return
        resultHandlingDisposed = true
        resultCollectionJob.cancel()
        screenSession.dispose()
    }

    private fun handleResult(result: EditorWorkflowResult) {
        val completesPendingWorkflow = when (result) {
            is EditorWorkflowResult.Captcha -> false
            EditorWorkflowResult.Ignored -> !featureSession.isSubmitting
            EditorWorkflowResult.Success,
            is EditorWorkflowResult.Failure,
            is EditorWorkflowResult.CaptchaCancelled -> true
        }
        if (completesPendingWorkflow) awaitingWorkflowResult = false

        when (result) {
            EditorWorkflowResult.Success -> {
                navigation.showMessage(EditorPresentationCopy.successMessage(type))
                onFinished()
            }
            is EditorWorkflowResult.Failure -> navigation.showFailureDetailDialog(
                result.title,
                result.message,
                result.commentDraft,
            )
            is EditorWorkflowResult.Captcha -> navigation.showCaptchaDialog(
                result.challenge,
                object : CaptchaResultHandler {
                    override fun onCaptchaResponse(
                        challenge: com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge,
                        captchaResponse: String,
                    ) {
                        screenSession.respondToCaptcha(challenge, captchaResponse)
                    }

                    override fun onCaptchaCancelled() {
                        screenSession.cancelCaptcha()
                    }
                },
            )
            is EditorWorkflowResult.CaptchaCancelled -> navigation.showMessage(result.message)
            EditorWorkflowResult.Ignored -> Unit
        }

        // Closing an idle editor releases it immediately. If a submission is already running,
        // preserve the previous behavior by handling its result (including a captcha round trip)
        // before detaching the result bridge. The feature request itself remains Activity-scoped.
        if (closeRequested && completesPendingWorkflow) disposeResultHandling()
    }
}
