package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.*
import com.simon.harmonichackernews.platform.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginWorkflowTest {
    @Test
    fun cancelledLoginDoesNotRetainUnverifiedAccount() = runBlocking {
        verifyCancellation(continuingCaptcha = false, replaceAccount = false)
    }

    @Test
    fun cancelledCaptchaContinuationDoesNotRetainUnverifiedAccount() = runBlocking {
        verifyCancellation(continuingCaptcha = true, replaceAccount = false)
    }

    @Test
    fun cancellationDoesNotClearAnAccountFromANewerLogin() = runBlocking {
        verifyCancellation(continuingCaptcha = false, replaceAccount = true)
    }

    private suspend fun verifyCancellation(continuingCaptcha: Boolean, replaceAccount: Boolean) = coroutineScope {
        val started = CompletableDeferred<Unit>()
        val transport = HttpClient(MockEngine {
            started.complete(Unit)
            awaitCancellation()
        })
        val accounts = MemoryAccounts()
        val actions = KtorHackerNewsActionRepository(KtorHttpClient(transport), KtorHttpClient(transport))
        val service = HackerNewsUserService(object : HackerNewsAuthenticatedSession {
            override val actions: HackerNewsActionRepository = actions
            override val authenticatedWeb: HackerNewsWebRepository = UnusedWeb
            override val publicWeb: HackerNewsWebRepository = UnusedWeb
            override fun reset() = Unit
        }, accounts)
        val pending = HackerNewsAccount("qa-unverified", "fake-password")
        val replacement = HackerNewsAccount("other-account", "other-password")
        try {
            val workflow = LoginWorkflow(accounts, service)
            if (continuingCaptcha) accounts.saveAccount(pending)
            val job = launch {
                if (continuingCaptcha) {
                    workflow.continueWithCaptcha(
                        HackerNewsCaptchaChallenge("https://news.ycombinator.com/login", "test", emptyList(), false),
                        "test-response",
                    )
                } else {
                    workflow.login(pending.username, pending.password)
                }
            }
            withTimeout(5_000) { started.await() }
            if (replaceAccount) accounts.saveAccount(replacement)
            job.cancelAndJoin()
            assertEquals(
                if (replaceAccount) HackerNewsAccountState.LoggedIn(replacement) else HackerNewsAccountState.LoggedOut,
                accounts.accountState.value,
            )
        } finally {
            transport.close()
        }
    }

    private class MemoryAccounts : ObservableHackerNewsAccountRepository {
        private val state = MutableStateFlow<HackerNewsAccountState>(HackerNewsAccountState.LoggedOut)
        override val accountState: StateFlow<HackerNewsAccountState> = state
        override suspend fun saveAccount(account: HackerNewsAccount): Boolean {
            state.value = HackerNewsAccountState.LoggedIn(account)
            return true
        }
        override suspend fun clearAccount(): Boolean {
            // Real storage switches dispatchers; cleanup must survive a cancelled caller.
            yield()
            currentCoroutineContext().ensureActive()
            state.value = HackerNewsAccountState.LoggedOut
            return true
        }
    }

    private object UnusedWeb : HackerNewsWebRepository {
        override suspend fun getStoryList(path: String, commentsPage: Boolean, day: String?): HackerNewsListPage = error("unused")
        override suspend fun getStoryListPage(url: String, commentsPage: Boolean): HackerNewsListPage = error("unused")
        override suspend fun getListDirectory(): List<Story> = error("unused")
        override suspend fun getUserItems(path: String, username: String): HackerNewsUserItems = error("unused")
    }
}
