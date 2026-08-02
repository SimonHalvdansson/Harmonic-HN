package com.simon.harmonichackernews.ui.editor;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.simon.harmonichackernews.MainActivity;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.ui.common.CaptchaResultCallback;
import com.simon.harmonichackernews.utils.Utils;

import okhttp3.Response;

/** Owns submission side effects while the editor itself is a MainActivity Compose destination. */
public final class ComposeEditorCoordinator {
    private final MainActivity activity;
    private final Runnable onFinished;
    private final int id;
    private final int type;
    private final int titleMaxLength;
    private final String parentText;
    private final String postTitle;
    private final String user;
    private boolean submitting;
    private ComposeEditorController controller;

    public ComposeEditorCoordinator(
            @NonNull MainActivity activity,
            @NonNull Bundle arguments,
            @NonNull Runnable onFinished) {
        this.activity = activity;
        this.onFinished = onFinished;
        id = arguments.getInt(ComposeEditorContract.EXTRA_ID, -1);
        type = arguments.getInt(
                ComposeEditorContract.EXTRA_TYPE,
                ComposeEditorContract.TYPE_POST);
        parentText = arguments.getString(ComposeEditorContract.EXTRA_PARENT_TEXT);
        postTitle = arguments.getString(ComposeEditorContract.EXTRA_POST_TITLE);
        user = arguments.getString(ComposeEditorContract.EXTRA_USER);
        titleMaxLength = activity.getResources().getInteger(R.integer.title_max_length);
    }

    public int getType() {
        return type;
    }

    public int getTitleMaxLength() {
        return titleMaxLength;
    }

    @Nullable
    public String getParentText() {
        return parentText;
    }

    @Nullable
    public String getPostTitle() {
        return postTitle;
    }

    @Nullable
    public String getUser() {
        return user;
    }

    public void attachController(@NonNull ComposeEditorController controller) {
        this.controller = controller;
        controller.setSubmitting(submitting);
    }

    public void submit(@NonNull ComposeEditorSubmission submission) {
        submitValues(
                submission.getTitle(),
                submission.getUrl(),
                submission.getText(),
                submission.getComment());
    }

    private void submitValues(
            @NonNull String submittedTitle,
            @NonNull String submittedUrl,
            @NonNull String submittedText,
            @NonNull String submittedComment) {
        if (submitting) return;

        if (type == ComposeEditorContract.TYPE_POST) {
            if (TextUtils.isEmpty(submittedTitle)
                    || submittedTitle.length() > titleMaxLength
                    || (TextUtils.isEmpty(submittedUrl) && TextUtils.isEmpty(submittedText))) {
                return;
            }
        } else if (TextUtils.isEmpty(submittedComment)) {
            return;
        }

        if (!Utils.isNetworkAvailable(activity)) {
            showSubmissionFailure(
                    null,
                    null,
                    type == ComposeEditorContract.TYPE_POST ? null : submittedComment);
            return;
        }

        setSubmitting(true);
        if (type == ComposeEditorContract.TYPE_POST) {
            submitPost(submittedTitle, submittedText, submittedUrl);
        } else {
            submitComment(submittedComment);
        }
    }

    private void submitPost(@NonNull String title, @NonNull String text, @NonNull String url) {
        UserActions.submit(title, text, url, activity, new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                Toast.makeText(
                        activity,
                        "Post submitted, it might take a minute to show up",
                        Toast.LENGTH_SHORT).show();
                onFinished.run();
            }

            @Override
            public void onFailure(String summary, String response) {
                setSubmitting(false);
                showSubmissionFailure(summary, response, null);
            }

            @Override
            public void onCaptchaRequired(UserActions.CaptchaChallenge challenge) {
                UserActions.ActionCallback callback = this;
                activity.showCaptchaDialog(
                        challenge,
                        new CaptchaResultCallback() {
                            @Override
                            public void onCaptchaResponse(
                                    UserActions.CaptchaChallenge captchaChallenge,
                                    String captchaResponse) {
                                if (captchaChallenge.isLoginChallenge()) {
                                    UserActions.submitAfterLoginCaptcha(
                                            title,
                                            text,
                                            url,
                                            activity,
                                            captchaChallenge,
                                            captchaResponse,
                                            callback);
                                } else {
                                    UserActions.continueCaptchaAction(
                                            activity,
                                            captchaChallenge,
                                            captchaResponse,
                                            callback);
                                }
                            }

                            @Override
                            public void onCaptchaCancelled() {
                                setSubmitting(false);
                                Toast.makeText(
                                        activity,
                                        "Post submission requires completing the HN captcha",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });
    }

    private void submitComment(@NonNull String commentText) {
        UserActions.comment(String.valueOf(id), commentText, activity,
                new UserActions.ActionCallback() {
                    @Override
                    public void onSuccess(Response response) {
                        Toast.makeText(
                                activity,
                                "Comment posted, it might take a minute to show up",
                                Toast.LENGTH_SHORT).show();
                        onFinished.run();
                    }

                    @Override
                    public void onFailure(String summary, String response) {
                        setSubmitting(false);
                        showSubmissionFailure(summary, response, commentText);
                    }

                    @Override
                    public void onCaptchaRequired(UserActions.CaptchaChallenge challenge) {
                        UserActions.ActionCallback callback = this;
                        activity.showCaptchaDialog(
                                challenge,
                                new CaptchaResultCallback() {
                                    @Override
                                    public void onCaptchaResponse(
                                            UserActions.CaptchaChallenge captchaChallenge,
                                            String captchaResponse) {
                                        UserActions.continueCaptchaAction(
                                                activity,
                                                captchaChallenge,
                                                captchaResponse,
                                                callback);
                                    }

                                    @Override
                                    public void onCaptchaCancelled() {
                                        setSubmitting(false);
                                        Toast.makeText(
                                                activity,
                                                "Comment posting requires completing the HN captcha",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                });
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        if (controller != null) controller.setSubmitting(value);
    }

    private void showSubmissionFailure(
            @Nullable String summary,
            @Nullable String response,
            @Nullable String commentDraft) {
        boolean isPost = type == ComposeEditorContract.TYPE_POST;
        String draftName = isPost ? "post" : "comment";
        String title;
        String message;

        if (!Utils.isNetworkAvailable(activity)) {
            title = "No internet connection";
            message = "Connect to the internet, then try again. Your " + draftName
                    + " is still here.";
        } else {
            title = isPost ? "Couldn't submit post" : "Couldn't post comment";
            StringBuilder messageBuilder = new StringBuilder();
            if (!TextUtils.isEmpty(summary)) messageBuilder.append(summary);
            if (!TextUtils.isEmpty(response)) {
                if (messageBuilder.length() > 0) messageBuilder.append("\n\n");
                messageBuilder.append(response);
            }
            if (messageBuilder.length() > 0) messageBuilder.append("\n\n");
            messageBuilder.append("Your ").append(draftName)
                    .append(" is still here, so you can edit it or try again.");
            message = messageBuilder.toString();
        }

        UserActions.showFailureDetailDialog(activity, title, message, commentDraft);
    }
}
