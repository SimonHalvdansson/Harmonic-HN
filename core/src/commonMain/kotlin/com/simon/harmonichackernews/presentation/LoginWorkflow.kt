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
    private data class CaptchaStage(
        val account: HackerNewsAccount?,
        val challenge: HackerNewsCaptchaChallenge,
        var awaitingResponse: Boolean = true,
    )
    private var captchaStage: CaptchaStage? = null
    private var attempt = 0

    suspend fun login(username: String, password: String): HackerNewsActionResult {
        val normalizedUsername = username.trim()
        if (normalizedUsername.isEmpty() || password.isEmpty()) {
            return HackerNewsActionResult.Failure("Login failed", "Missing credentials")
        }
        val account = HackerNewsAccount(normalizedUsername, password)
        val currentAttempt = ++attempt
        captchaStage = null
        return verifyAccount(account) {
            if (!accounts.saveAccount(account)) {
                HackerNewsActionResult.Failure("Login failed", "Couldn't save credentials")
            } else {
                confirmAccount(account, currentAttempt, userService.login())
            }
        }.also { result ->
            if (attempt == currentAttempt && result is HackerNewsActionResult.Captcha) {
                captchaStage = CaptchaStage(account, result.challenge)
            }
        }
    }

    suspend fun continueWithCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        response: String,
    ): HackerNewsActionResult {
        val account = accounts.awaitAccount()
        if (captchaStage == null) {
            val capturedUsername = challenge.formFields.firstOrNull { it.name == "acct" }?.value
            val capturedPassword = challenge.formFields.firstOrNull { it.name == "pw" }?.value
            if ((capturedUsername != null && capturedUsername != account?.username) ||
                (capturedPassword != null && capturedPassword != account?.password)
            ) {
                return HackerNewsActionResult.Failure(
                    "Hacker News account changed", "Start login again with the current credentials.",
                )
            }
        }
        val stage = captchaStage ?: CaptchaStage(account, challenge).also { captchaStage = it }
        if (!stage.awaitingResponse || stage.challenge != challenge || stage.account != account || response.isBlank()) {
            return HackerNewsActionResult.Failure("CAPTCHA expired", "Use the current login challenge and try again.")
        }
        stage.awaitingResponse = false
        val currentAttempt = attempt
        return verifyAccount(account) {
            confirmAccount(account, currentAttempt, userService.continueLoginWithCaptcha(challenge, response))
        }.also { result ->
            if (attempt == currentAttempt && result is HackerNewsActionResult.Captcha) {
                captchaStage = CaptchaStage(account, result.challenge)
            }
        }
    }

    suspend fun cancelCaptcha(challenge: HackerNewsCaptchaChallenge? = null) {
        val stage = captchaStage
        if (stage != null) {
            if (!stage.awaitingResponse || (challenge != null && stage.challenge != challenge)) return
            stage.awaitingResponse = false
            stage.account?.let { accounts.clearAccountIfMatches(it) }
        } else if (challenge != null) {
            // A host can restore the challenge after its workflow was recreated. Only the
            // captured login form identifies the provisional account; never guess a new login.
            val username = challenge.formFields.firstOrNull { it.name == "acct" }?.value
            val password = challenge.formFields.firstOrNull { it.name == "pw" }?.value
            if (username != null && password != null) {
                accounts.clearAccountIfMatches(HackerNewsAccount(username, password))
            }
        }
    }

    private suspend fun confirmAccount(
        provisional: HackerNewsAccount?,
        currentAttempt: Int,
        result: HackerNewsActionResult,
    ): HackerNewsActionResult {
        if (result !is HackerNewsActionResult.Success) return result
        val exact = result.authenticatedUsername?.takeIf(String::isNotBlank)
            ?: return HackerNewsActionResult.Failure("Login not confirmed", "HN did not identify the signed-in user.")
        // The server's authenticated ID is authoritative. Ignore-case is only a sanity check
        // that a stale cookie did not authenticate an entirely different login name.
        if (provisional == null || !exact.equals(provisional.username, ignoreCase = true)) {
            return HackerNewsActionResult.Failure("Login not confirmed", "HN authenticated a different account.")
        }
        if (attempt != currentAttempt || !accounts.replaceAccountIfMatches(provisional, provisional.copy(username = exact))) {
            return HackerNewsActionResult.Failure("Login changed", "A newer login replaced this attempt. Try again with the current account.")
        }
        return result
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
