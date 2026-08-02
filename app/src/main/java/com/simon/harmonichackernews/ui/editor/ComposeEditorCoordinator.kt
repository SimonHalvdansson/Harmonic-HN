package com.simon.harmonichackernews.ui.editor

import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.UserActions
import com.simon.harmonichackernews.network.UserActions.ActionCallback
import com.simon.harmonichackernews.network.UserActions.CaptchaChallenge
import com.simon.harmonichackernews.ui.common.CaptchaResultCallback
import com.simon.harmonichackernews.utils.Utils
import okhttp3.Response

/** Owns submission side effects while the editor itself is a MainActivity Compose destination.  */
class ComposeEditorCoordinator(
    activity: MainActivity,
    arguments: Bundle,
    onFinished: Runnable
) {
    private val activity: MainActivity
    private val onFinished: Runnable
    private val id: Int
    val type: Int
    val titleMaxLength: Int
    val parentText: String?
    val postTitle: String?
    val user: String?
    private var submitting = false
    private var controller: ComposeEditorController? = null

    init {
        this.activity = activity
        this.onFinished = onFinished
        id = arguments.getInt(ComposeEditorContract.EXTRA_ID, -1)
        type = arguments.getInt(
            ComposeEditorContract.EXTRA_TYPE,
            ComposeEditorContract.TYPE_POST
        )
        parentText = arguments.getString(ComposeEditorContract.EXTRA_PARENT_TEXT)
        postTitle = arguments.getString(ComposeEditorContract.EXTRA_POST_TITLE)
        user = arguments.getString(ComposeEditorContract.EXTRA_USER)
        titleMaxLength = activity.getResources().getInteger(R.integer.title_max_length)
    }

    fun attachController(controller: ComposeEditorController) {
        this.controller = controller
        controller.setSubmitting(submitting)
    }

    fun submit(submission: ComposeEditorSubmission) {
        submitValues(
            submission.title,
            submission.url,
            submission.text,
            submission.comment
        )
    }

    private fun submitValues(
        submittedTitle: String,
        submittedUrl: String,
        submittedText: String,
        submittedComment: String
    ) {
        if (submitting) return

        if (type == ComposeEditorContract.TYPE_POST) {
            if (TextUtils.isEmpty(submittedTitle)
                || submittedTitle.length > titleMaxLength || (TextUtils.isEmpty(submittedUrl) && TextUtils.isEmpty(
                    submittedText
                ))
            ) {
                return
            }
        } else if (TextUtils.isEmpty(submittedComment)) {
            return
        }

        if (!Utils.isNetworkAvailable(activity)) {
            showSubmissionFailure(
                null,
                null,
                if (type == ComposeEditorContract.TYPE_POST) null else submittedComment
            )
            return
        }

        setSubmitting(true)
        if (type == ComposeEditorContract.TYPE_POST) {
            submitPost(submittedTitle, submittedText, submittedUrl)
        } else {
            submitComment(submittedComment)
        }
    }

    private fun submitPost(title: String, text: String, url: String) {
        UserActions.submit(title, text, url, activity, object : ActionCallback {
            override fun onSuccess(response: Response) {
                Toast.makeText(
                    activity,
                    "Post submitted, it might take a minute to show up",
                    Toast.LENGTH_SHORT
                ).show()
                onFinished.run()
            }

            override fun onFailure(summary: String?, response: String?) {
                setSubmitting(false)
                showSubmissionFailure(summary, response, null)
            }

            override fun onCaptchaRequired(challenge: CaptchaChallenge) {
                val callback: ActionCallback = this
                activity.showCaptchaDialog(
                    challenge,
                    object : CaptchaResultCallback {
                        override fun onCaptchaResponse(
                            captchaChallenge: CaptchaChallenge,
                            captchaResponse: String
                        ) {
                            if (captchaChallenge.isLoginChallenge) {
                                UserActions.submitAfterLoginCaptcha(
                                    title,
                                    text,
                                    url,
                                    activity,
                                    captchaChallenge,
                                    captchaResponse,
                                    callback
                                )
                            } else {
                                UserActions.continueCaptchaAction(
                                    activity,
                                    captchaChallenge,
                                    captchaResponse,
                                    callback
                                )
                            }
                        }

                        override fun onCaptchaCancelled() {
                            setSubmitting(false)
                            Toast.makeText(
                                activity,
                                "Post submission requires completing the HN captcha",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    })
            }
        })
    }

    private fun submitComment(commentText: String) {
        UserActions.comment(
            id.toString(), commentText, activity,
            object : ActionCallback {
                override fun onSuccess(response: Response) {
                    Toast.makeText(
                        activity,
                        "Comment posted, it might take a minute to show up",
                        Toast.LENGTH_SHORT
                    ).show()
                    onFinished.run()
                }

                override fun onFailure(summary: String?, response: String?) {
                    setSubmitting(false)
                    showSubmissionFailure(summary, response, commentText)
                }

                override fun onCaptchaRequired(challenge: CaptchaChallenge) {
                    val callback: ActionCallback = this
                    activity.showCaptchaDialog(
                        challenge,
                        object : CaptchaResultCallback {
                            override fun onCaptchaResponse(
                                captchaChallenge: CaptchaChallenge,
                                captchaResponse: String
                            ) {
                                UserActions.continueCaptchaAction(
                                    activity,
                                    captchaChallenge,
                                    captchaResponse,
                                    callback
                                )
                            }

                            override fun onCaptchaCancelled() {
                                setSubmitting(false)
                                Toast.makeText(
                                    activity,
                                    "Comment posting requires completing the HN captcha",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                }
            })
    }

    private fun setSubmitting(value: Boolean) {
        submitting = value
        if (controller != null) controller!!.setSubmitting(value)
    }

    private fun showSubmissionFailure(
        summary: String?,
        response: String?,
        commentDraft: String?
    ) {
        val isPost = type == ComposeEditorContract.TYPE_POST
        val draftName = if (isPost) "post" else "comment"
        val title: String?
        val message: String?

        if (!Utils.isNetworkAvailable(activity)) {
            title = "No internet connection"
            message = ("Connect to the internet, then try again. Your " + draftName
                    + " is still here.")
        } else {
            title = if (isPost) "Couldn't submit post" else "Couldn't post comment"
            val messageBuilder = StringBuilder()
            if (!TextUtils.isEmpty(summary)) messageBuilder.append(summary)
            if (!TextUtils.isEmpty(response)) {
                if (messageBuilder.length > 0) messageBuilder.append("\n\n")
                messageBuilder.append(response)
            }
            if (messageBuilder.length > 0) messageBuilder.append("\n\n")
            messageBuilder.append("Your ").append(draftName)
                .append(" is still here, so you can edit it or try again.")
            message = messageBuilder.toString()
        }

        UserActions.showFailureDetailDialog(activity, title, message, commentDraft)
    }
}
