package com.simon.harmonichackernews.network

import android.content.Context
import com.simon.harmonichackernews.utils.AccountUtils

internal fun HackerNewsActionResult.failureDetails(): Pair<String, String?> = when (this) {
    is HackerNewsActionResult.Failure -> summary to detail
    is HackerNewsActionResult.Captcha -> "Captcha required" to
        "HN requires a captcha for this action. Please try again in a browser."
    is HackerNewsActionResult.Success -> "Action failed" to null
}

internal fun HackerNewsActionResult.showLoginPromptIfCredentialsMissing(context: Context) {
    if (this is HackerNewsActionResult.Failure && summary == "Couldn't read credentials") {
        AccountUtils.showLoginPrompt(context)
    }
}
