package com.simon.harmonichackernews.ui.common

import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge

/** Callback bridge for Compose-owned CAPTCHA dialogs requested by non-UI coordinators.  */
interface CaptchaResultCallback {
    fun onCaptchaResponse(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
    )

    fun onCaptchaCancelled()
}
