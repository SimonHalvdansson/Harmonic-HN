package com.simon.harmonichackernews.ui.editor

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.platform.AndroidCredentialStore
import com.simon.harmonichackernews.ui.common.CaptchaResultCallback
import com.simon.harmonichackernews.utils.Utils
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.presentation.EditorSubmission
import kotlinx.coroutines.launch

/** Owns submission side effects while the editor itself is a MainActivity Compose destination.  */
class ComposeEditorCoordinator(
    private val activity: MainActivity,
    destination: EditorDestination,
    private val onFinished: () -> Unit,
) {
    private val id = destination.itemId
    val type = destination.type
    val titleMaxLength = activity.resources.getInteger(R.integer.title_max_length)
    val parentText: String? = destination.parentText
    val postTitle: String? = destination.postTitle
    val user: String? = destination.userName
    private var submitting = false
    private var controller: ComposeEditorController? = null
    private val hackerNewsUserService = HackerNewsUserService(
        NetworkComponent.hackerNewsSession,
        AndroidCredentialStore(activity),
    )

    fun attachController(controller: ComposeEditorController) {
        this.controller = controller
        controller.setSubmitting(submitting)
    }

    fun submit(submission: EditorSubmission) {
        submitValues(
            submission.title,
            submission.url,
            submission.text,
            submission.comment,
        )
    }

    private fun submitValues(
        submittedTitle: String,
        submittedUrl: String,
        submittedText: String,
        submittedComment: String,
    ) {
        if (submitting) return

        if (type == EditorType.POST) {
            if (submittedTitle.isEmpty() ||
                submittedTitle.length > titleMaxLength ||
                (submittedUrl.isEmpty() && submittedText.isEmpty())
            ) {
                return
            }
        } else if (submittedComment.isEmpty()) {
            return
        }

        if (!Utils.isNetworkAvailable(activity)) {
            showSubmissionFailure(
                null,
                null,
                if (type == EditorType.POST) null else submittedComment,
            )
            return
        }

        setSubmitting(true)
        if (type == EditorType.POST) {
            submitPost(submittedTitle, submittedText, submittedUrl)
        } else {
            submitComment(submittedComment)
        }
    }

    private fun submitPost(title: String, text: String, url: String) {
        activity.lifecycleScope.launch {
            handlePostResult(hackerNewsUserService.submit(title, text, url), title, text, url)
        }
    }

    private fun submitComment(commentText: String) {
        activity.lifecycleScope.launch {
            handleCommentResult(hackerNewsUserService.comment(id.toString(), commentText), commentText)
        }
    }

    private fun handlePostResult(
        result: HackerNewsActionResult,
        title: String,
        text: String,
        url: String,
    ) {
        when (result) {
            is HackerNewsActionResult.Success -> {
                Toast.makeText(
                    activity,
                    "Post submitted, it might take a minute to show up",
                    Toast.LENGTH_SHORT,
                ).show()
                onFinished()
            }
            is HackerNewsActionResult.Failure -> {
                setSubmitting(false)
                showSubmissionFailure(result.summary, result.detail, null)
            }
            is HackerNewsActionResult.Captcha -> activity.showCaptchaDialog(
                result.challenge,
                object : CaptchaResultCallback {
                    override fun onCaptchaResponse(
                        captchaChallenge: HackerNewsCaptchaChallenge,
                        captchaResponse: String,
                    ) {
                        activity.lifecycleScope.launch {
                            val continued = if (captchaChallenge.isLoginChallenge) {
                                hackerNewsUserService.submitAfterLoginCaptcha(
                                    captchaChallenge,
                                    captchaResponse,
                                    title,
                                    text,
                                    url,
                                )
                            } else {
                                hackerNewsUserService.continueCaptchaAction(
                                    captchaChallenge,
                                    captchaResponse,
                                )
                            }
                            handlePostResult(continued, title, text, url)
                        }
                    }

                    override fun onCaptchaCancelled() {
                        setSubmitting(false)
                        Toast.makeText(
                            activity,
                            "Post submission requires completing the HN captcha",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }
    }

    private fun handleCommentResult(result: HackerNewsActionResult, commentText: String) {
        when (result) {
            is HackerNewsActionResult.Success -> {
                Toast.makeText(
                    activity,
                    "Comment posted, it might take a minute to show up",
                    Toast.LENGTH_SHORT,
                ).show()
                onFinished()
            }
            is HackerNewsActionResult.Failure -> {
                setSubmitting(false)
                showSubmissionFailure(result.summary, result.detail, commentText)
            }
            is HackerNewsActionResult.Captcha -> activity.showCaptchaDialog(
                result.challenge,
                object : CaptchaResultCallback {
                    override fun onCaptchaResponse(
                        captchaChallenge: HackerNewsCaptchaChallenge,
                        captchaResponse: String,
                    ) {
                        activity.lifecycleScope.launch {
                            handleCommentResult(
                                hackerNewsUserService.continueCaptchaAction(
                                    captchaChallenge,
                                    captchaResponse,
                                ),
                                commentText,
                            )
                        }
                    }

                    override fun onCaptchaCancelled() {
                        setSubmitting(false)
                        Toast.makeText(
                            activity,
                            "Comment posting requires completing the HN captcha",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }
    }

    private fun setSubmitting(value: Boolean) {
        submitting = value
        controller?.setSubmitting(value)
    }

    private fun showSubmissionFailure(
        summary: String?,
        response: String?,
        commentDraft: String?,
    ) {
        val isPost = type == EditorType.POST
        val draftName = if (isPost) "post" else "comment"
        val (title, message) = if (!Utils.isNetworkAvailable(activity)) {
            "No internet connection" to
                "Connect to the internet, then try again. Your $draftName is still here."
        } else {
            val failureTitle = if (isPost) "Couldn't submit post" else "Couldn't post comment"
            val failureMessage = listOfNotNull(
                summary?.takeIf { it.isNotEmpty() },
                response?.takeIf { it.isNotEmpty() },
                "Your $draftName is still here, so you can edit it or try again.",
            ).joinToString("\n\n")
            failureTitle to failureMessage
        }

        activity.showFailureDetailDialog(title, message, commentDraft)
    }
}
