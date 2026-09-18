package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.HackerNewsAccountState
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import com.simon.harmonichackernews.platform.ConnectivityService
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.presentation.EditorSubmission
import com.simon.harmonichackernews.presentation.EditorSubmissionWorkflow
import com.simon.harmonichackernews.presentation.EditorWorkflowResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class HackerNewsActionDispatchClassificationTest {
    @Test
    fun accountBoundRequestsRejectTheReplacementLoginBeforeNetworkDispatch() = runTest {
        var requests = 0
        val transport = HttpClient(MockEngine { requests++; respond("ok") })
        try {
            val service = userService(transport)
            assertIs<HackerNewsActionResult.Failure>(service.voteForAccount("old-user", "42", "up"))
            assertIs<HackerNewsActionResult.Failure>(service.setFavoriteForAccount("old-user", 42, true))
            assertEquals(0, requests)
        } finally { transport.close() }
    }

    @Test
    fun lateInvalidCredentialsDoNotLogOutTheNewAccount() = runTest {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val accounts = MemoryAccounts()
        val transport = HttpClient(MockEngine {
            started.complete(Unit)
            response.await()
            respond("Bad login.")
        })
        try {
            val action = async { userService(transport, accounts).vote("42", "up") }
            started.await()
            val replacement = HackerNewsAccount("bob", "new password")
            accounts.saveAccount(replacement)
            response.complete(Unit)
            assertEquals(HackerNewsActionFailureReason.INVALID_CREDENTIALS,
                assertIs<HackerNewsActionResult.Failure>(action.await()).reason)
            assertEquals(replacement, accounts.currentAccount)
        } finally { transport.close() }
    }

    @Test
    fun unrecognizedPostingResponsesKeepTheEditorDraftAndDoNotReportSuccess() = runTest {
        for (body in listOf(
            "<html>You're posting too fast. Please slow down.</html>",
            "<html>An unfamiliar HN rejection message</html>",
            "",
        )) {
            val transport = HttpClient(MockEngine { respond(body) })
            try {
                val workflow = EditorSubmissionWorkflow(
                    type = EditorType.COMMENT_REPLY,
                    itemId = 42,
                    service = userService(transport),
                    connectivity = object : ConnectivityService {
                        override fun isOnline() = true
                        override fun isUnmetered() = true
                    },
                )
                val result = assertIs<EditorWorkflowResult.Failure>(workflow.submit(EditorSubmission(comment = "Keep this draft")))
                assertEquals("Keep this draft", result.commentDraft)
                assertFalse(workflow.isSubmitting)
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun favoritePreflightTransportFailureIsDefinite() = runTest {
        val transport = HttpClient(MockEngine { error("preflight connection failed") })
        try {
            val result = assertIs<HackerNewsActionResult.Failure>(
                userService(transport).setFavorite(42, favorite = true),
            )

            assertEquals(HackerNewsActionFailureReason.GENERAL, result.reason)
        } finally {
            transport.close()
        }
    }

    @Test
    fun favoriteVerificationFailureAfterMutationIsIndeterminate() = runTest {
        var requestCount = 0
        val transport = HttpClient(MockEngine {
            when (++requestCount) {
                1 -> respond("<html><input name=\"fnid\" value=\"token\"></html>")
                2 -> respond(
                    "<html><a href=\"https://news.ycombinator.com/fave?" +
                        "id=42&amp;auth=token\">favorite</a></html>",
                )
                3 -> respond("<html>favorite accepted</html>")
                else -> respond(
                    content = "verification unavailable",
                    status = HttpStatusCode.InternalServerError,
                )
            }
        })
        try {
            val result = assertIs<HackerNewsActionResult.Failure>(
                userService(transport).setFavorite(42, favorite = true),
            )

            assertEquals(4, requestCount)
            assertEquals(HackerNewsActionFailureReason.INDETERMINATE, result.reason)
        } finally {
            transport.close()
        }
    }

    @Test
    fun lifecycleCancellationOfMutationIsNotConvertedToAResult() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val completedResult = CompletableDeferred<HackerNewsActionResult>()
        val transport = HttpClient(MockEngine {
            requestStarted.complete(Unit)
            awaitCancellation()
        })
        try {
            val job = launch {
                completedResult.complete(userService(transport).vote("42", "up"))
            }
            requestStarted.await()

            job.cancelAndJoin()

            assertFalse(completedResult.isCompleted)
        } finally {
            transport.close()
        }
    }

    private fun userService(
        transport: HttpClient,
        accounts: ObservableHackerNewsAccountRepository = MemoryAccounts(),
    ): HackerNewsUserService {
        val actions = KtorHackerNewsActionRepository(
            client = KtorHttpClient(transport),
            cookieClient = KtorHttpClient(transport),
        )
        return HackerNewsUserService(
            session = object : HackerNewsAuthenticatedSession {
                override val actions: HackerNewsActionRepository = actions
                override val authenticatedWeb: HackerNewsWebRepository = UnusedWebRepository
                override val publicWeb: HackerNewsWebRepository = UnusedWebRepository
                override fun reset() = Unit
            },
            accounts = accounts,
        )
    }

    private class MemoryAccounts : ObservableHackerNewsAccountRepository {
        private val mutableAccount = MutableStateFlow<HackerNewsAccountState>(
            HackerNewsAccountState.LoggedIn(HackerNewsAccount("tester", "secret")),
        )
        override val accountState: StateFlow<HackerNewsAccountState> = mutableAccount
        override suspend fun saveAccount(account: HackerNewsAccount): Boolean {
            mutableAccount.value = HackerNewsAccountState.LoggedIn(account)
            return true
        }
        override suspend fun clearAccount(): Boolean {
            mutableAccount.value = HackerNewsAccountState.LoggedOut
            return true
        }
    }

    private data object UnusedWebRepository : HackerNewsWebRepository {
        override suspend fun getStoryList(
            path: String,
            commentsPage: Boolean,
            day: String?,
        ): HackerNewsListPage = error("unused")

        override suspend fun getStoryListPage(
            url: String,
            commentsPage: Boolean,
        ): HackerNewsListPage = error("unused")

        override suspend fun getListDirectory(): List<Story> = error("unused")
        override suspend fun getUserItems(path: String, username: String): HackerNewsUserItems =
            error("unused")
    }
}
