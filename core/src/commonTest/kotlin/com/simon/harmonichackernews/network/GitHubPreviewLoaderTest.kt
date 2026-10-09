package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import com.simon.harmonichackernews.data.RepoInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubPreviewLoaderTest {
    @Test
    fun repositoryFallbackHasPageMetadataAndNoInventedStatistics() = runTest {
        val requests = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            requests += request.url.host
            if (request.url.host == "api.github.com") {
                respond("""{"message":"API rate limit exceeded"}""", HttpStatusCode.Forbidden)
            } else respond(PAGE)
        })
        try {
            val info = KtorLinkPreviewRepository(client).getGitHubInfo(URL)
            assertEquals(listOf("api.github.com", "github.com"), requests)
            assertEquals("Repository title", info.pageTitle)
            assertEquals("Repository description", info.about)
            assertEquals("https://example.com/repository.png", info.imageUrl)
            assertNull(info.formatStars())
            assertNull(info.formatWatching())
            assertNull(info.formatForks())
            assertEquals(info, Json.decodeFromString<RepoInfo>(Json.encodeToString(info)))
            val apiInfo = LinkPreviewParsers.parseGitHub("""{"name":"repo","stargazers_count":0,"forks_count":12}""")
            assertEquals("0 stars", apiInfo.formatStars())
            assertEquals("12 forks", apiInfo.formatForks())
            assertNull(apiInfo.formatWatching())
        } finally { client.close() }
    }

    @Test
    fun cachedApiPreviewWinsOverHtmlAndIsSharedBetweenRequests() = runTest {
        var apiCalls = 0
        var pageCalls = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "api.github.com") {
                if (++apiCalls == 1) respond("""{"name":"repo","stargazers_count":42}""")
                else respond("limited", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "120"))
            } else {
                pageCalls++
                respond(PAGE)
            }
        })
        try {
            val repository = KtorLinkPreviewRepository(client)
            val original = repository.getGitHubInfo(URL)
            assertEquals(original, repository.getGitHubInfo(URL))
            assertEquals(original, repository.getGitHubInfo(URL))
            assertEquals(2, apiCalls)
            assertEquals(0, pageCalls)
            // A different GitHub provider shares the same cooldown.
            val issue = assertIs<LinkPreviewData.Rich>(repository.load(LinkPreviewType.GITHUB_ISSUE, "$URL/issues/1"))
            assertEquals("Repository title", issue.value.title)
            assertEquals(2, apiCalls)
            assertEquals(1, pageCalls)
            assertEquals(original, repository.getGitHubInfo(URL))
        } finally { client.close() }
    }

    @Test
    fun cooldownUsesLatestDeadlineAndAllowsApiAgainAfterExpiry() = runTest {
        var now = 1_000_000L
        var apiCalls = 0
        var pageCalls = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "api.github.com") {
                if (++apiCalls == 1) respond(
                    "limited", HttpStatusCode.Forbidden,
                    headersOf("x-ratelimit-remaining" to listOf("0"), "x-ratelimit-reset" to listOf("1030"), "Retry-After" to listOf("10")),
                ) else respond("""{"name":"recovered"}""")
            } else { pageCalls++; respond(PAGE) }
        })
        try {
            val repository = KtorLinkPreviewRepository(client, nowMillis = { now })
            repository.getGitHubInfo(URL)
            now += 10_000
            repository.getGitHubInfo("$URL-other")
            now = 1_029_999
            repository.getGitHubInfo(URL)
            assertEquals(1, apiCalls)
            assertEquals(2, pageCalls)
            now = 1_030_000
            assertEquals("recovered", repository.getGitHubInfo(URL).name)
            assertEquals(2, apiCalls)
        } finally { client.close() }
    }

    @Test
    fun supportsRetryAfterSecondsAndHttpDateAndConservativeMissingHeaderCooldown() = runTest {
        for (retryAfter in listOf("120", "Thu, 01 Jan 1970 00:18:40 GMT", "invalid", "0")) {
            var now = 1_000_000L
            var apiCalls = 0
            val client = HttpClient(MockEngine { request ->
                if (request.url.host == "api.github.com") {
                    apiCalls++
                    respond("limited", HttpStatusCode.TooManyRequests, headersOf("Retry-After", retryAfter))
                } else respond(PAGE)
            })
            try {
                val repository = KtorLinkPreviewRepository(client, nowMillis = { now })
                repository.getGitHubInfo(URL)
                val duration = if (retryAfter == "invalid" || retryAfter == "0") 60_000L else 120_000L
                now += duration - 1
                repository.getGitHubInfo("$URL-other")
                assertEquals(1, apiCalls, retryAfter)
                now++
                repository.getGitHubInfo(URL)
                assertEquals(2, apiCalls, retryAfter)
            } finally { client.close() }
        }
    }

    @Test
    fun ordinaryForbiddenDoesNotFallbackOrSuppressAnotherRepository() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            assertEquals("api.github.com", request.url.host)
            calls++
            respond(
                """{"message":"Resource not accessible by integration"}""", HttpStatusCode.Forbidden,
                headersOf("x-ratelimit-remaining" to listOf("59"), "x-ratelimit-reset" to listOf("9999999999")),
            )
        })
        try {
            val repository = KtorLinkPreviewRepository(client)
            assertFailsWith<HttpStatusException> { repository.getGitHubInfo(URL) }
            assertFailsWith<HttpStatusException> { repository.getGitHubInfo("$URL-other") }
            assertEquals(2, calls)
        } finally { client.close() }
    }

    @Test
    fun queuedRequestsObserveSecondaryRateLimitEvenWhenFallbackFails() = runTest {
        var apiCalls = 0
        val client = HttpClient(MockEngine) {
            engine {
                dispatcher = StandardTestDispatcher(testScheduler)
                addHandler { request ->
                    if (request.url.host == "api.github.com") {
                        apiCalls++
                        delay(10)
                        respond("""{"message":"You have exceeded a secondary rate limit."}""", HttpStatusCode.Forbidden)
                    } else respond("gone", HttpStatusCode.NotFound)
                }
            }
        }
        try {
            val repository = KtorLinkPreviewRepository(client, StandardTestDispatcher(testScheduler))
            val first = async { assertFailsWith<HttpStatusException> { repository.getGitHubInfo(URL) } }
            val second = async { assertFailsWith<HttpStatusException> { repository.getGitHubInfo("$URL-other") } }
            first.await()
            second.await()
            assertEquals(1, apiCalls)
        } finally { client.close() }
    }

    @Test
    fun loadingStaysContinuousThroughApiRetriesAndHtmlFallbackRetries() = runTest {
        for (succeed in listOf(true, false)) {
            var apiCalls = 0
            var pageCalls = 0
            val states = mutableListOf<LinkPreviewRuntimeState>()
            val client = HttpClient(MockEngine) {
                engine {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler { request ->
                        if (request.url.host == "api.github.com") {
                            if (++apiCalls == 1) respond("unavailable", HttpStatusCode.ServiceUnavailable)
                            else respond("limited", HttpStatusCode.TooManyRequests)
                        } else {
                            if (++pageCalls < 3 || !succeed) respond("unavailable", HttpStatusCode.ServiceUnavailable)
                            else respond(PAGE)
                        }
                    }
                }
            }
            try {
                val repository = KtorLinkPreviewRepository(client, StandardTestDispatcher(testScheduler))
                val runtime = LinkPreviewRuntime(this, LinkPreviewUseCase(repository))
                val preferences = LinkPreviewPreferences(setOf(LinkPreviewType.GITHUB_REPOSITORY))
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    runtime.state.collect { states += it }
                }
                assertTrue(runtime.load(URL, preferences, alreadyLoaded = false))
                advanceUntilIdle()
                assertEquals(listOf(false, true, false), states.map { it.loading })
                assertEquals(2, apiCalls)
                assertEquals(3, pageCalls)
                if (succeed) {
                    assertIs<LinkPreviewData.GitHub>(states.last().preview)
                    assertNull(states.last().failure)
                    // Visibility never turns false when loading hands over to the preview.
                    assertTrue(states.drop(1).all { it.loading || it.preview != null })
                } else {
                    assertTrue(states.last().failure != null)
                    repeat(3) { assertFalse(runtime.load(URL, preferences, alreadyLoaded = false)) }
                    advanceUntilIdle()
                    assertEquals(3, states.size)
                    assertEquals(2, apiCalls)
                    assertEquals(3, pageCalls)
                }
            } finally { client.close() }
        }
    }

    private companion object {
        const val URL = "https://github.com/example/repo"
        const val PAGE = """<html><head>
            <meta property="og:title" content="Repository title">
            <meta property="og:description" content="Repository description">
            <meta property="og:image" content="https://example.com/repository.png">
            </head></html>"""
    }
}
