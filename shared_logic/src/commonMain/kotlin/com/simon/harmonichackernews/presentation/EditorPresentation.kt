package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.platform.ConnectivityService
import kotlinx.coroutines.CancellationException

data class EditorSubmission(
    val title: String = "",
    val url: String = "",
    val text: String = "",
    val comment: String = "",
)

data class EditorValidation(
    val canSubmit: Boolean,
    val titleTooLong: Boolean,
)

fun EditorSubmission.validate(type: EditorType, titleMaxLength: Int): EditorValidation {
    val titleTooLong = title.length > titleMaxLength
    val canSubmit = if (type == EditorType.POST) {
        title.isNotEmpty() && !titleTooLong && (url.isNotEmpty() || text.isNotEmpty())
    } else {
        comment.isNotEmpty()
    }
    return EditorValidation(canSubmit = canSubmit, titleTooLong = titleTooLong)
}

sealed interface EditorWorkflowResult {
    data object Ignored : EditorWorkflowResult
    data object Success : EditorWorkflowResult
    data class Captcha(val challenge: HackerNewsCaptchaChallenge) : EditorWorkflowResult
    data class Failure(
        val title: String,
        val message: String,
        val commentDraft: String? = null,
    ) : EditorWorkflowResult
    data class CaptchaCancelled(val message: String) : EditorWorkflowResult
}

/**
 * Platform-neutral post/comment submission state machine. The app shell owns the lifecycle and
 * renders dialogs, captcha UI, and success feedback; this class owns validation, connectivity
 * policy, request routing, captcha continuation, and the in-flight state.
 */
class EditorSubmissionWorkflow(
    private val type: EditorType,
    private val itemId: Int,
    private val titleMaxLength: Int,
    private val service: HackerNewsUserService,
    private val connectivity: ConnectivityService,
    private val onSubmittingChanged: (Boolean) -> Unit = {},
) {
    var isSubmitting: Boolean = false
        private set

    private var pendingSubmission: EditorSubmission? = null

    suspend fun submit(submission: EditorSubmission): EditorWorkflowResult {
        if (isSubmitting || !submission.validate(type, titleMaxLength).canSubmit) {
            return EditorWorkflowResult.Ignored
        }
        if (!connectivity.isOnline()) return offlineFailure(submission)

        pendingSubmission = submission
        setSubmitting(true)
        return runRequest { initialRequest(submission) }
    }

    suspend fun respondToCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        response: String,
    ): EditorWorkflowResult {
        val submission = pendingSubmission ?: return EditorWorkflowResult.Ignored
        if (!isSubmitting) return EditorWorkflowResult.Ignored
        if (!connectivity.isOnline()) {
            setSubmitting(false)
            return offlineFailure(submission)
        }
        return runRequest {
            if (type == EditorType.POST && challenge.isLoginChallenge) {
                service.submitAfterLoginCaptcha(
                    challenge,
                    response,
                    submission.title,
                    submission.text,
                    submission.url,
                )
            } else {
                service.continueCaptchaAction(challenge, response)
            }
        }
    }

    fun cancelCaptcha(): EditorWorkflowResult.CaptchaCancelled {
        setSubmitting(false)
        return EditorWorkflowResult.CaptchaCancelled(
            if (type == EditorType.POST) {
                "Post submission requires completing the HN captcha"
            } else {
                "Comment posting requires completing the HN captcha"
            },
        )
    }

    private suspend fun initialRequest(submission: EditorSubmission): HackerNewsActionResult =
        if (type == EditorType.POST) {
            service.submit(submission.title, submission.text, submission.url)
        } else {
            service.comment(itemId.toString(), submission.comment)
        }

    private suspend fun runRequest(
        request: suspend () -> HackerNewsActionResult,
    ): EditorWorkflowResult = try {
        when (val result = request()) {
            is HackerNewsActionResult.Success -> EditorWorkflowResult.Success
            is HackerNewsActionResult.Captcha -> EditorWorkflowResult.Captcha(result.challenge)
            is HackerNewsActionResult.Failure -> {
                setSubmitting(false)
                failure(result.summary, result.detail, pendingSubmission)
            }
        }
    } catch (error: CancellationException) {
        setSubmitting(false)
        throw error
    }

    private fun offlineFailure(submission: EditorSubmission): EditorWorkflowResult.Failure {
        val draftName = if (type == EditorType.POST) "post" else "comment"
        return EditorWorkflowResult.Failure(
            title = "No internet connection",
            message = "Connect to the internet, then try again. Your $draftName is still here.",
            commentDraft = submission.comment.takeIf { type != EditorType.POST },
        )
    }

    private fun failure(
        summary: String?,
        detail: String?,
        submission: EditorSubmission?,
    ): EditorWorkflowResult.Failure {
        val isPost = type == EditorType.POST
        val draftName = if (isPost) "post" else "comment"
        return EditorWorkflowResult.Failure(
            title = if (isPost) "Couldn't submit post" else "Couldn't post comment",
            message = listOfNotNull(
                summary?.takeIf(String::isNotEmpty),
                detail?.takeIf(String::isNotEmpty),
                "Your $draftName is still here, so you can edit it or try again.",
            ).joinToString("\n\n"),
            commentDraft = submission?.comment?.takeIf { !isPost },
        )
    }

    private fun setSubmitting(value: Boolean) {
        if (isSubmitting == value) return
        isSubmitting = value
        onSubmittingChanged(value)
    }
}
