package com.simon.harmonichackernews.ui.common;

import androidx.annotation.NonNull;

import com.simon.harmonichackernews.network.UserActions;

/** Callback bridge for Compose-owned CAPTCHA dialogs requested by non-UI coordinators. */
public interface CaptchaResultCallback {
    void onCaptchaResponse(@NonNull UserActions.CaptchaChallenge challenge,
                           @NonNull String captchaResponse);

    void onCaptchaCancelled();
}
