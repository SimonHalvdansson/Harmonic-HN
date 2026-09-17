package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository

/**
 * Host-neutral Hacker News login workflow. The host renders CAPTCHA content and success feedback;
 * credential lifetime and network sequencing remain identical on every platform.
 */
class LoginWorkflow(
    private val accounts: ObservableHackerNewsAccountRepository,
    private val userService: HackerNewsUserService,
) {
    suspend fun login(username: String, password: String): HackerNewsActionResult {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isEmpty() || password.isEmpty()) {
            return HackerNewsActionResult.Failure("Login failed", "Missing credentials")
        }
        if (!accounts.saveAccount(HackerNewsAccount(normalizedUsername, password))) {
            return HackerNewsActionResult.Failure("Login failed", "Couldn't save credentials")
        }
        val result = userService.login()
        clearAfterTerminalFailure(result)
        return result
    }

    suspend fun continueWithCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        response: String,
    ): HackerNewsActionResult {
        val result = userService.continueLoginWithCaptcha(challenge, response)
        clearAfterTerminalFailure(result)
        return result
    }

    suspend fun cancelCaptcha() {
        accounts.clearAccount()
    }

    private suspend fun clearAfterTerminalFailure(result: HackerNewsActionResult) {
        if (result is HackerNewsActionResult.Failure) accounts.clearAccount()
    }
}
