package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.HackerNewsActionRepository
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsAuthenticatedSession
import com.simon.harmonichackernews.network.HackerNewsListPage
import com.simon.harmonichackernews.network.HackerNewsUserItems
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.HackerNewsWebRepository
import com.simon.harmonichackernews.network.KtorHackerNewsActionRepository
import com.simon.harmonichackernews.network.KtorHttpClient
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.HackerNewsAccountState
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** End-to-end protocol fixtures; no test logs in to the live Hacker News website. */
class LoginCanonicalIdentityTest {
    @Test
    fun loginPersistsTheExactAuthenticatedHeaderIdentityAndOriginalPassword() = runTest {
        val accounts = MemoryAccounts()
        val transport = HttpClient(MockEngine { respond(authenticatedPage("Canonical")) })
        try {
            val result = assertIs<HackerNewsActionResult.Success>(
                workflow(transport, accounts).login(" canonical ", " secret "),
            )
            assertEquals("Canonical", result.authenticatedUsername)
            assertEquals(HackerNewsAccount("Canonical", " secret "), accounts.currentAccount)
        } finally { transport.close() }
    }

    @Test
    fun captchaContinuationPersistsTheExactAuthenticatedHeaderIdentity() = runTest {
        val accounts = MemoryAccounts()
        var requests = 0
        val transport = HttpClient(MockEngine {
            requests++
            respond(if (requests == 1) loginChallenge() else authenticatedPage("Canonical"))
        })
        try {
            val workflow = workflow(transport, accounts)
            val challenge = assertIs<HackerNewsActionResult.Captcha>(workflow.login("canonical", "secret"))
            assertEquals(HackerNewsAccount("canonical", "secret"), accounts.currentAccount)
            val result = assertIs<HackerNewsActionResult.Success>(
                workflow.continueWithCaptcha(challenge.challenge, "fixture-response"),
            )
            assertEquals("Canonical", result.authenticatedUsername)
            assertEquals(HackerNewsAccount("Canonical", "secret"), accounts.currentAccount)
            assertEquals(2, requests)
        } finally { transport.close() }
    }

    @Test
    fun aDifferentAuthenticatedAccountFailsAndRemovesTheProvisionalLogin() = runTest {
        val accounts = MemoryAccounts()
        val transport = HttpClient(MockEngine { respond(authenticatedPage("SomeoneElse")) })
        try {
            val result = assertIs<HackerNewsActionResult.Failure>(
                workflow(transport, accounts).login("canonical", "secret"),
            )
            assertEquals("Login not confirmed", result.summary)
            assertNull(accounts.currentAccount)
        } finally { transport.close() }
    }

    @Test
    fun aSubmitFormWithoutTheAuthenticatedHeaderCannotConfirmLogin() = runTest {
        val accounts = MemoryAccounts()
        val transport = HttpClient(MockEngine { respond("<input name='fnid' value='fixture'>") })
        try {
            val result = assertIs<HackerNewsActionResult.Failure>(
                workflow(transport, accounts).login("canonical", "secret"),
            )
            assertEquals("Login not confirmed", result.summary)
            assertNull(accounts.currentAccount)
        } finally { transport.close() }
    }

    @Test
    fun aLateAuthenticatedResponseCannotReplaceNewerCredentials() = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<String>()
        val accounts = MemoryAccounts()
        val transport = HttpClient(MockEngine {
            started.complete(Unit)
            respond(response.await())
        })
        try {
            val workflow = workflow(transport, accounts)
            val pending = async { workflow.login("canonical", "old-secret") }
            started.await()
            val newer = HackerNewsAccount("Canonical", "new-secret")
            accounts.saveAccount(newer)
            response.complete(authenticatedPage("Canonical"))
            assertEquals("Login changed", assertIs<HackerNewsActionResult.Failure>(pending.await()).summary)
            assertEquals(newer, accounts.currentAccount)
        } finally { transport.close() }
    }

    private fun workflow(transport: HttpClient, accounts: MemoryAccounts): LoginWorkflow {
        val actions = KtorHackerNewsActionRepository(KtorHttpClient(transport), KtorHttpClient(transport))
        val service = HackerNewsUserService(object : HackerNewsAuthenticatedSession {
            override val actions: HackerNewsActionRepository = actions
            override val authenticatedWeb: HackerNewsWebRepository = UnusedWeb
            override val publicWeb: HackerNewsWebRepository = UnusedWeb
            override fun reset() = Unit
        }, accounts)
        return LoginWorkflow(accounts, service)
    }

    private fun authenticatedPage(username: String) = """
        <html><body><span class="pagetop">
        <a id="me" href="user?id=$username">$username</a> (1) | <a id="logout" href="logout">logout</a>
        </span><form><input name="fnid" value="fixture"></form></body></html>
    """.trimIndent()

    private fun loginChallenge() = """
        <form action="/login"><input name="acct" value="canonical"><input name="pw" value="secret">
        <input name="goto" value="submit">Validation required. If this doesn't work, you can email
        <div class="g-recaptcha" data-sitekey="fixture"></div></form>
    """.trimIndent()

    private class MemoryAccounts : ObservableHackerNewsAccountRepository {
        private val state = MutableStateFlow<HackerNewsAccountState>(HackerNewsAccountState.LoggedOut)
        override val accountState: StateFlow<HackerNewsAccountState> = state
        override suspend fun saveAccount(account: HackerNewsAccount): Boolean {
            state.value = HackerNewsAccountState.LoggedIn(account)
            return true
        }
        override suspend fun clearAccount(): Boolean {
            state.value = HackerNewsAccountState.LoggedOut
            return true
        }
    }

    private object UnusedWeb : HackerNewsWebRepository {
        override suspend fun getStoryList(path: String, commentsPage: Boolean, day: String?): HackerNewsListPage = error("Unused")
        override suspend fun getStoryListPage(url: String, commentsPage: Boolean): HackerNewsListPage = error("Unused")
        override suspend fun getListDirectory(): List<Story> = error("Unused")
        override suspend fun getUserItems(path: String, username: String): HackerNewsUserItems = error("Unused")
    }
}
