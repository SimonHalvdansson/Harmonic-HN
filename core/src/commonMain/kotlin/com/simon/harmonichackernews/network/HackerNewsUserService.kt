package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import kotlinx.coroutines.CancellationException

interface HackerNewsAuthenticatedSession {
    val actions: HackerNewsActionRepository
    val authenticatedWeb: HackerNewsWebRepository
    val publicWeb: HackerNewsWebRepository
    fun reset()
}

sealed interface HackerNewsUserItemsResult {
    data class Success(val items: HackerNewsUserItems) : HackerNewsUserItemsResult
    data class Failure(val summary: String, val detail: String? = null) : HackerNewsUserItemsResult
    data class Captcha(val challenge: HackerNewsCaptchaChallenge) : HackerNewsUserItemsResult
}

interface HackerNewsUserItemsLoader {
    suspend fun getUserItems(path: String, loginRequired: Boolean): HackerNewsUserItemsResult
}

fun interface HackerNewsVotingService {
    suspend fun vote(itemId: String, direction: String): HackerNewsActionResult
}

fun interface HackerNewsFavoriteService {
    suspend fun setFavorite(itemId: Int, favorite: Boolean): HackerNewsActionResult
}

/** Owns credential/session policy while repositories own the HN wire protocol. */
class HackerNewsUserService(
    private val session: HackerNewsAuthenticatedSession,
    private val accounts: ObservableHackerNewsAccountRepository,
) : HackerNewsUserItemsLoader, HackerNewsVotingService, HackerNewsFavoriteService {
    suspend fun login(): HackerNewsActionResult {
        val account = readCredentials() ?: return missingCredentials()
        session.reset()
        return safeAction("Login failed") { session.actions.login(account) }
    }

    override suspend fun vote(itemId: String, direction: String): HackerNewsActionResult =
        withCredentials("Couldn't connect to HN") { session.actions.vote(it, itemId, direction) }

    suspend fun comment(itemId: String, text: String): HackerNewsActionResult =
        withCredentials("Couldn't connect to HN") { session.actions.comment(it, itemId, text) }

    suspend fun submit(title: String, text: String, url: String): HackerNewsActionResult {
        val account = readCredentials() ?: return missingCredentials()
        session.reset()
        return safeAction("Couldn't connect to HN") {
            session.actions.submit(account, title, text, url)
        }
    }

    override suspend fun setFavorite(itemId: Int, favorite: Boolean): HackerNewsActionResult {
        val account = readCredentials() ?: return missingCredentials()
        session.reset()
        return safeAction("Couldn't update favorite") {
            session.actions.setFavorite(account, itemId, favorite)
        }
    }

    suspend fun continueLoginWithCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
    ): HackerNewsActionResult = safeAction("Login failed") {
        session.actions.continueLoginWithCaptcha(challenge, captchaResponse)
    }

    suspend fun submitAfterLoginCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
        title: String,
        text: String,
        url: String,
    ): HackerNewsActionResult = safeAction("Couldn't connect to HN") {
        session.actions.submitAfterLoginCaptcha(
            challenge,
            captchaResponse,
            title,
            text,
            url,
        )
    }

    suspend fun continueCaptchaAction(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
    ): HackerNewsActionResult = safeAction("Couldn't connect to HN") {
        session.actions.continueCaptchaAction(challenge, captchaResponse)
    }

    override suspend fun getUserItems(
        path: String,
        loginRequired: Boolean,
    ): HackerNewsUserItemsResult {
        val account = readCredentials() ?: return HackerNewsUserItemsResult.Failure(
            "Login required",
            "Save your Hacker News login before syncing $path.",
        )
        if (loginRequired) {
            session.reset()
            when (val login = sanitize(session.actions.login(account))) {
                is HackerNewsActionResult.Success -> Unit
                is HackerNewsActionResult.Failure ->
                    return HackerNewsUserItemsResult.Failure(login.summary, login.detail)
                is HackerNewsActionResult.Captcha ->
                    return HackerNewsUserItemsResult.Captcha(login.challenge)
            }
        }
        val repository = if (loginRequired) session.authenticatedWeb else session.publicWeb
        return HackerNewsUserItemsResult.Success(repository.getUserItems(path, account.username))
    }

    private suspend fun withCredentials(
        failureSummary: String,
        block: suspend (HackerNewsCredentials) -> HackerNewsActionResult,
    ): HackerNewsActionResult {
        val account = readCredentials() ?: return missingCredentials()
        return safeAction(failureSummary) { block(account) }
    }

    private suspend fun safeAction(
        failureSummary: String,
        block: suspend () -> HackerNewsActionResult,
    ): HackerNewsActionResult = try {
        sanitize(block())
    } catch (error: IndeterminateHackerNewsActionException) {
        HackerNewsActionResult.Failure(
            failureSummary,
            error.cause?.message ?: error.message,
            HackerNewsActionFailureReason.INDETERMINATE,
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        HackerNewsActionResult.Failure(failureSummary, error.message)
    }

    private fun readCredentials(): HackerNewsCredentials? {
        return accounts.load()
    }

    private suspend fun sanitize(result: HackerNewsActionResult): HackerNewsActionResult {
        if (
            result is HackerNewsActionResult.Failure &&
            result.reason == HackerNewsActionFailureReason.INVALID_CREDENTIALS
        ) {
            accounts.clearAccount()
        }
        return result
    }

    private fun missingCredentials(): HackerNewsActionResult.Failure =
        HackerNewsActionResult.Failure(
            summary = "Couldn't read credentials",
            detail = "Check your saved login.",
            reason = HackerNewsActionFailureReason.MISSING_CREDENTIALS,
        )
}
