package com.simon.harmonichackernews.ui.common

import com.simon.harmonichackernews.network.UserActions.CaptchaChallenge

/** Callback bridge for Compose-owned CAPTCHA dialogs requested by non-UI coordinators.  */
interface CaptchaResultCallback {
    fun onCaptchaResponse(
        challenge: CaptchaChallenge,
        captchaResponse: String,
    )

    fun onCaptchaCancelled()
}
