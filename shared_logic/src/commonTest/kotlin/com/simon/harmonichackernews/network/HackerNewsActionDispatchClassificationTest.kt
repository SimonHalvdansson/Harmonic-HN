package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
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

    private fun userService(transport: HttpClient): HackerNewsUserService {
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
            accounts = MemoryAccounts(),
        )
    }

    private class MemoryAccounts : ObservableHackerNewsAccountRepository {
        private val mutableAccount = MutableStateFlow<HackerNewsAccount?>(
            HackerNewsAccount("tester", "secret"),
        )
        override val accountState: StateFlow<HackerNewsAccount?> = mutableAccount
        override fun load(): HackerNewsAccount? = mutableAccount.value
        override fun save(account: HackerNewsAccount): Boolean {
            mutableAccount.value = account
            return true
        }
        override fun clear(): Boolean {
            mutableAccount.value = null
            return true
        }
        override suspend fun saveAccount(account: HackerNewsAccount): Boolean = save(account)
        override suspend fun clearAccount(): Boolean = clear()
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
