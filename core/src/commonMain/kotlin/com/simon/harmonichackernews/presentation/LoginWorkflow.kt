package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

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
        val account = HackerNewsAccount(normalizedUsername, password)
        return verifyAccount(account) {
            if (!accounts.saveAccount(account)) {
                HackerNewsActionResult.Failure("Login failed", "Couldn't save credentials")
            } else {
                userService.login()
            }
        }
    }

    suspend fun continueWithCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        response: String,
    ): HackerNewsActionResult {
        val account = accounts.awaitAccount()
        return verifyAccount(account) {
            userService.continueLoginWithCaptcha(challenge, response)
        }
    }

    suspend fun cancelCaptcha() {
        accounts.clearAccount()
    }

    private suspend fun verifyAccount(
        account: HackerNewsAccount?,
        action: suspend () -> HackerNewsActionResult,
    ): HackerNewsActionResult {
        var keepAccount = false
        try {
            return action().also { result ->
                keepAccount = result is HackerNewsActionResult.Success || result is HackerNewsActionResult.Captcha
            }
        } finally {
            if (!keepAccount && account != null) {
                // Dialog disposal cancels its coroutine. Storage cleanup must still finish, and
                // must not erase a different login that replaced this attempt in the meantime.
                withContext(NonCancellable) { accounts.clearAccountIfMatches(account) }
            }
        }
    }
}
