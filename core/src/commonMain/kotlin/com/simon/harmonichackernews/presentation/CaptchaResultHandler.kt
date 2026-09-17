package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge

/** Shared editor/navigation bridge for a host-rendered CAPTCHA challenge. */
interface CaptchaResultHandler {
    fun onCaptchaResponse(challenge: HackerNewsCaptchaChallenge, captchaResponse: String)
    fun onCaptchaCancelled()
}
