package com.simon.harmonichackernews.ui.editor

import androidx.lifecycle.lifecycleScope
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.app.createEditorFeatureSession
import com.simon.harmonichackernews.app.EditorFeatureSessionEvent
import com.simon.harmonichackernews.ui.common.CaptchaResultCallback
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.presentation.EditorSubmission
import com.simon.harmonichackernews.presentation.EditorWorkflowResult
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
    val titleMaxLength = activity.resources.getInteger(R.integer.title_max_length)
    val parentText: String? = destination.parentText
    val postTitle: String? = destination.postTitle
    val user: String? = destination.userName
    private var controller: ComposeEditorController? = null
    private val appComposition = activity.harmonicAppComposition
    private val session = appComposition.createEditorFeatureSession(
        scope = activity.lifecycleScope,
        type = type,
        itemId = id,
        titleMaxLength = titleMaxLength,
    )

    init {
        activity.lifecycleScope.launch {
            session.events.collect { event ->
                when (event) {
                    is EditorFeatureSessionEvent.Submitting ->
                        controller?.setSubmitting(event.value)
                    is EditorFeatureSessionEvent.Result -> handleResult(event.value)
                }
            }
        }
    }

    fun attachController(controller: ComposeEditorController) {
        this.controller = controller
        controller.setSubmitting(session.isSubmitting)
    }

    fun submit(submission: EditorSubmission) {
        session.submit(submission)
    }

    private fun handleResult(result: EditorWorkflowResult) {
        when (result) {
            EditorWorkflowResult.Success -> {
                navigation.showMessage(
                    if (type == EditorType.POST) {
                        "Post submitted, it might take a minute to show up"
                    } else {
                        "Comment posted, it might take a minute to show up"
                    },
                )
                onFinished()
            }
            is EditorWorkflowResult.Failure -> navigation.showFailureDetailDialog(
                result.title,
                result.message,
                result.commentDraft,
            )
            is EditorWorkflowResult.Captcha -> navigation.showCaptchaDialog(
                result.challenge,
                object : CaptchaResultCallback {
                    override fun onCaptchaResponse(
                        challenge: com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge,
                        captchaResponse: String,
                    ) {
                        session.respondToCaptcha(challenge, captchaResponse)
                    }

                    override fun onCaptchaCancelled() {
                        session.cancelCaptcha()
                    }
                },
            )
            is EditorWorkflowResult.CaptchaCancelled -> navigation.showMessage(result.message)
            EditorWorkflowResult.Ignored -> Unit
        }
    }
}
