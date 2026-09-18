package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val cookieSessionMutex = Mutex()

    suspend fun login(): HackerNewsActionResult = cookieSessionMutex.withLock {
        val account = readCredentials() ?: return@withLock missingCredentials()
        session.reset()
        safeAction("Login failed", account) { session.actions.login(account) }
    }

    override suspend fun vote(itemId: String, direction: String): HackerNewsActionResult =
        withCredentials("Couldn't connect to HN") { session.actions.vote(it, itemId, direction) }

    suspend fun voteForAccount(
        username: String?,
        itemId: String,
        direction: String,
        isAccountCurrent: () -> Boolean = { true },
    ): HackerNewsActionResult = withCredentials(
        "Couldn't connect to HN", username, requireAccountMatch = true,
    ) {
        if (isAccountCurrent()) session.actions.vote(it, itemId, direction) else accountChanged()
    }

    suspend fun comment(itemId: String, text: String): HackerNewsActionResult =
        withCredentials("Couldn't connect to HN") { session.actions.comment(it, itemId, text) }

    suspend fun submit(title: String, text: String, url: String): HackerNewsActionResult =
        cookieSessionMutex.withLock {
            val account = readCredentials() ?: return@withLock missingCredentials()
            session.reset()
            safeAction("Couldn't connect to HN", account) {
                session.actions.submit(account, title, text, url)
            }
        }

    override suspend fun setFavorite(itemId: Int, favorite: Boolean): HackerNewsActionResult =
        setFavoriteForAccount(accounts.awaitAccount()?.username, itemId, favorite)

    suspend fun setFavoriteForAccount(
        username: String?,
        itemId: Int,
        favorite: Boolean,
        isAccountCurrent: () -> Boolean = { true },
    ): HackerNewsActionResult = cookieSessionMutex.withLock {
        val account = readCredentials() ?: return@withLock missingCredentials()
        if (account.username != username || !isAccountCurrent()) return@withLock accountChanged()
        session.reset()
        safeAction("Couldn't update favorite", account) {
            session.actions.setFavorite(account, itemId, favorite)
        }
    }

    suspend fun continueLoginWithCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
    ): HackerNewsActionResult = cookieSessionMutex.withLock {
        safeAction("Login failed") {
            session.actions.continueLoginWithCaptcha(challenge, captchaResponse)
        }
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
    ): HackerNewsUserItemsResult = cookieSessionMutex.withLock {
        val account = readCredentials() ?: return@withLock HackerNewsUserItemsResult.Failure(
            "Login required",
            "Save your Hacker News login before syncing $path.",
        )
        if (loginRequired) {
            session.reset()
            when (val login = sanitize(session.actions.login(account), account)) {
                is HackerNewsActionResult.Success -> Unit
                is HackerNewsActionResult.Failure ->
                    return@withLock HackerNewsUserItemsResult.Failure(login.summary, login.detail)
                is HackerNewsActionResult.Captcha ->
                    return@withLock HackerNewsUserItemsResult.Captcha(login.challenge)
            }
        }
        val repository = if (loginRequired) session.authenticatedWeb else session.publicWeb
        HackerNewsUserItemsResult.Success(repository.getUserItems(path, account.username))
    }

    private suspend fun withCredentials(
        failureSummary: String,
        expectedUsername: String? = null,
        requireAccountMatch: Boolean = false,
        block: suspend (HackerNewsCredentials) -> HackerNewsActionResult,
    ): HackerNewsActionResult {
        val account = readCredentials() ?: return missingCredentials()
        if (requireAccountMatch && account.username != expectedUsername) return accountChanged()
        return safeAction(failureSummary, account) { block(account) }
    }

    private suspend fun safeAction(
        failureSummary: String,
        account: HackerNewsCredentials? = accounts.currentAccount,
        block: suspend () -> HackerNewsActionResult,
    ): HackerNewsActionResult = try {
        sanitize(block(), account)
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

    private suspend fun readCredentials(): HackerNewsCredentials? = accounts.awaitAccount()

    private suspend fun sanitize(
        result: HackerNewsActionResult,
        account: HackerNewsCredentials?,
    ): HackerNewsActionResult {
        if (
            result is HackerNewsActionResult.Failure &&
            result.reason == HackerNewsActionFailureReason.INVALID_CREDENTIALS && account != null
        ) {
            accounts.clearAccountIfMatches(account)
        }
        return result
    }

    private fun accountChanged() = HackerNewsActionResult.Failure(
        "Hacker News account changed",
        "Try the action again with the current login.",
    )

    private fun missingCredentials(): HackerNewsActionResult.Failure =
        HackerNewsActionResult.Failure(
            summary = "Couldn't read credentials",
            detail = "Check your saved login.",
            reason = HackerNewsActionFailureReason.MISSING_CREDENTIALS,
        )
}
