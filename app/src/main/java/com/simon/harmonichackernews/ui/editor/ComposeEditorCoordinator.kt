package com.simon.harmonichackernews.ui.editor

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.platform.AndroidConnectivityService
import com.simon.harmonichackernews.ui.common.CaptchaResultCallback
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.presentation.EditorSubmission
import com.simon.harmonichackernews.presentation.EditorSubmissionWorkflow
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
    private val appComposition = AndroidAppComposition.get(activity)
    private val hackerNewsUserService = appComposition.hackerNewsUser
    private val workflow = EditorSubmissionWorkflow(
        type = type,
        itemId = id,
        titleMaxLength = titleMaxLength,
        service = hackerNewsUserService,
        connectivity = AndroidConnectivityService(activity),
        onSubmittingChanged = { value -> controller?.setSubmitting(value) },
    )

    fun attachController(controller: ComposeEditorController) {
        this.controller = controller
        controller.setSubmitting(workflow.isSubmitting)
    }

    fun submit(submission: EditorSubmission) {
        activity.lifecycleScope.launch {
            handleResult(workflow.submit(submission))
        }
    }

    private fun handleResult(result: EditorWorkflowResult) {
        when (result) {
            EditorWorkflowResult.Success -> {
                Toast.makeText(
                    activity,
                    if (type == EditorType.POST) {
                        "Post submitted, it might take a minute to show up"
                    } else {
                        "Comment posted, it might take a minute to show up"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
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
                        activity.lifecycleScope.launch {
                            handleResult(workflow.respondToCaptcha(challenge, captchaResponse))
                        }
                    }

                    override fun onCaptchaCancelled() {
                        handleResult(workflow.cancelCaptcha())
                    }
                },
            )
            is EditorWorkflowResult.CaptchaCancelled -> Toast.makeText(
                activity,
                result.message,
                Toast.LENGTH_SHORT,
            ).show()
            EditorWorkflowResult.Ignored -> Unit
        }
    }
}
