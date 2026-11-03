package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.platform.*
import com.simon.harmonichackernews.presentation.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** All responses are local fixtures; these checks never log in, vote or post to live HN. */
class CaptchaActionContinuationTest {
    @Test
    fun voteResumesCapturedFormWithoutReplayingInitialRequest() = runTest {
        val paths = mutableListOf<String>()
        val transport = HttpClient(MockEngine { request ->
            paths += request.url.encodedPath
            if (paths.size == 1) respond(challenge("/vote-confirm")) else respond("ok")
        })
        try {
            val actions = CaptchaAwareHackerNewsActions(service(transport)) { "fixture-response" }
            val result = actions.vote("42", "up")
            assertIs<HackerNewsActionResult.Success>(result, "Unexpected vote result: $result")
            assertEquals(listOf("/vote", "/vote-confirm"), paths)
        } finally { transport.close() }
    }

    @Test
    fun cancellationDoesNotSubmitAChallengeOrRetryTheAction() = runTest {
        var requests = 0
        val transport = HttpClient(MockEngine { requests++; respond(challenge("/vote-confirm")) })
        try {
            val actions = CaptchaAwareHackerNewsActions(service(transport)) { null }
            assertEquals("CAPTCHA cancelled", assertIs<HackerNewsActionResult.Failure>(actions.vote("42", "up")).summary)
            assertEquals(1, requests)
        } finally { transport.close() }
    }

    @Test
    fun solvingLoginChallengeContinuesFavoriteAndVerifiesItWithoutLoggingInAgain() = runTest {
        val paths = mutableListOf<String>()
        var favoriteSent = false
        val transport = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            when (path) {
                "/login" -> if (paths.size == 1) respond(challenge("/login")) else respond("<input name='fnid' value='fixture'>")
                "/fave" -> { favoriteSent = true; respond("ok") }
                "/item" -> respond(if (favoriteSent) itemPage("unfavorite", "&amp;un=t") else itemPage("favorite"))
                else -> error("Unexpected request $path")
            }
        })
        try {
            val actions = CaptchaAwareHackerNewsActions(service(transport)) { "fixture-response" }
            assertIs<HackerNewsActionResult.Success>(actions.setFavorite(42, true))
            assertEquals(listOf("/login", "/login", "/item", "/fave", "/item"), paths)
            assertTrue(favoriteSent)
        } finally { transport.close() }
    }

    @Test
    fun favoriteMutationChallengeStillRequiresDesiredStateVerification() = runTest {
        for (confirmed in listOf(true, false)) {
            val paths = mutableListOf<String>()
            val transport = HttpClient(MockEngine { request ->
                val path = request.url.encodedPath
                paths += path
                when (path) {
                    "/login" -> respond("<input name='fnid' value='fixture'>")
                    "/item" -> respond(if (paths.size > 3 && confirmed) itemPage("unfavorite", "&amp;un=t") else itemPage("favorite"))
                    "/fave" -> respond("<form action='/fave-confirm'>Validation required. If this doesn't work, you can email<div class='g-recaptcha' data-sitekey='fixture'></div></form>")
                    "/fave-confirm" -> respond("ok")
                    else -> error("Unexpected request $path")
                }
            })
            try {
                val actions = CaptchaAwareHackerNewsActions(service(transport)) { "fixture-response" }
                val result = actions.setFavorite(42, true)
                if (confirmed) assertIs<HackerNewsActionResult.Success>(result)
                else assertEquals("Favorite update not confirmed", assertIs<HackerNewsActionResult.Failure>(result).summary)
                assertEquals(listOf("/login", "/item", "/fave", "/fave-confirm", "/item"), paths)
            } finally { transport.close() }
        }
    }

    @Test
    fun changingAccountDuringChallengePreventsDispatchUnderTheNewIdentity() = runTest {
        var requests = 0
        val accounts = Accounts()
        val transport = HttpClient(MockEngine { requests++; respond(challenge("/vote-confirm")) })
        try {
            val actions = CaptchaAwareHackerNewsActions(service(transport, accounts)) {
                accounts.saveAccount(HackerNewsAccount("other-user", "other-password"))
                "fixture-response"
            }
            assertEquals("Hacker News account changed", assertIs<HackerNewsActionResult.Failure>(actions.vote("42", "up")).summary)
            assertEquals(1, requests)
        } finally { transport.close() }
    }

    @Test
    fun editorCannotResumeCapturedFormAfterAnAccountSwitch() = runTest {
        for (type in listOf(EditorType.POST, EditorType.COMMENT_REPLY)) {
            var requests = 0
            val accounts = Accounts()
            val transport = HttpClient(MockEngine {
                requests++
                respond(challenge(if (type == EditorType.POST) "/login" else "/comment"))
            })
            try {
                val workflow = EditorSubmissionWorkflow(type, 42, service = service(transport, accounts), connectivity = object : ConnectivityService {
                    override fun isOnline() = true
                    override fun isUnmetered() = true
                })
                val draft = EditorSubmission(title = "Fixture", text = "Post draft", comment = "Comment draft")
                val pending = assertIs<EditorWorkflowResult.Captcha>(workflow.submit(draft))
                accounts.saveAccount(HackerNewsAccount("replacement-user", "replacement-password"))
                val result = assertIs<EditorWorkflowResult.Failure>(workflow.respondToCaptcha(pending.challenge, "fixture-response"))
                assertTrue(result.message.contains("account changed"))
                if (type != EditorType.POST) assertEquals(draft.comment, result.commentDraft)
                assertEquals(1, requests)
            } finally { transport.close() }
        }
    }

    @Test
    fun savedItemsSyncResumesAfterLoginChallengeWithoutRestartingLogin() = runTest {
        var requests = 0
        val web = Web()
        val transport = HttpClient(MockEngine {
            if (++requests == 1) respond(challenge("/login")) else respond("<span class='pagetop'><a id='me' href='user?id=fixture-user'>fixture-user</a></span><input name='fnid' value='fixture'>")
        })
        try {
            val actions = CaptchaAwareHackerNewsActions(service(transport, web = web)) { "fixture-response" }
            val result = assertIs<HackerNewsUserItemsResult.Success>(actions.getUserItems("upvoted", true))
            assertEquals(listOf(42), result.items.itemIds)
            assertEquals(2, requests)
            assertEquals(listOf("upvoted:fixture-user"), web.loads)
        } finally { transport.close() }
    }

    @Test
    fun paginationChallengeRestartsReadOnlySyncAndNeverReturnsAPartialSnapshot() = runTest {
        var verified = false
        var challengeResponses = 0
        val transport = HttpClient(MockEngine { request ->
            when (request.url.encodedPath) {
                "/verify-list" -> { verified = true; respond("ok") }
                "/favorites" -> when {
                    request.url.parameters["comments"] == "t" -> respond("<table><span class='comhead'><span class='age'><a href='item?id=43'>age</a></span></span></table>")
                    request.url.parameters["p"] == "2" && !verified -> {
                        challengeResponses++
                        respond("Validation required. If this doesn't work, you can email<form action='/verify-list'><div class='g-recaptcha' data-sitekey='fixture'></div></form>")
                    }
                    request.url.parameters["p"] == "2" -> respond("<table><tr class='athing' id='44'><td>Final story</td></tr></table>")
                    else -> respond("<table><tr class='athing' id='42'><td>First story</td></tr></table><a class='morelink' href='/favorites?id=fixture-user&amp;p=2'>More</a>")
                }
                else -> error("Unexpected request ${request.url.encodedPath}")
            }
        })
        try {
            val actions = CaptchaAwareHackerNewsActions(service(transport, web = KtorHackerNewsWebRepository(transport))) { "fixture-response" }
            val result = assertIs<HackerNewsUserItemsResult.Success>(actions.getUserItems("favorites", false))
            assertEquals(listOf(42, 44, 43), result.items.itemIds)
            assertEquals(listOf(43), result.items.commentIds)
            assertTrue(verified)
            assertEquals(1, challengeResponses)
        } finally { transport.close() }
    }

    @Test
    fun unknownOrLoginSavedListPagesFailInsteadOfReplacingDataWithEmptySuccess() = runTest {
        for (body in listOf("Unexpected server error", "<form action='/login'><input name='pw'></form>")) {
            val transport = HttpClient(MockEngine { respond(body) })
            try {
                val actions = CaptchaAwareHackerNewsActions(service(transport, web = KtorHackerNewsWebRepository(transport))) { null }
                val result = assertIs<HackerNewsUserItemsResult.Failure>(actions.getUserItems("favorites", false))
                assertTrue(result.detail.orEmpty().contains("Existing saved items are preserved"))
            } finally { transport.close() }
        }
    }

    @Test
    fun editorConsumesChallengeBeforeNetworkDispatchAndIgnoresLateCancelOrResponse() = runTest {
        var requests = 0
        val continuing = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val transport = HttpClient(MockEngine { request ->
            when (++requests) {
                1 -> respond(challenge("/comment"))
                2 -> {
                    continuing.complete(Unit)
                    release.await()
                    respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/item?id=42"))
                }
                else -> {
                    assertEquals("/item", request.url.encodedPath)
                    respond("<html>Comment confirmed</html>")
                }
            }
        })
        try {
            val workflow = editor(service(transport))
            val pending = assertIs<EditorWorkflowResult.Captcha>(workflow.submit(EditorSubmission(comment = "Keep this draft")))
            val response = async { workflow.respondToCaptcha(pending.challenge, "fixture-response") }
            continuing.await()
            assertEquals(EditorWorkflowResult.Ignored, workflow.respondToCaptcha(pending.challenge, "duplicate"))
            assertEquals(EditorWorkflowResult.Ignored, workflow.cancelCaptcha())
            assertTrue(workflow.isSubmitting)
            release.complete(Unit)
            assertEquals(EditorWorkflowResult.Success, response.await())
            assertEquals(3, requests)
        } finally { transport.close() }
    }

    @Test
    fun editorRetainsDraftThroughChallengeCancellationAndFailureAndAllowsExplicitRetry() = runTest {
        var requests = 0
        val transport = HttpClient(MockEngine {
            when (++requests) {
                1, 2 -> respond(challenge("/comment"))
                else -> respond("An unfamiliar rejection from HN")
            }
        })
        try {
            val workflow = editor(service(transport))
            val draft = EditorSubmission(comment = "Keep this draft")
            assertIs<EditorWorkflowResult.Captcha>(workflow.submit(draft))
            assertIs<EditorWorkflowResult.CaptchaCancelled>(workflow.cancelCaptcha())
            assertFalse(workflow.isSubmitting)
            val next = assertIs<EditorWorkflowResult.Captcha>(workflow.submit(draft))
            val failure = assertIs<EditorWorkflowResult.Failure>(workflow.respondToCaptcha(next.challenge, "fixture-response"))
            assertEquals(draft.comment, failure.commentDraft)
            assertFalse(workflow.isSubmitting)
            assertEquals(3, requests)
        } finally { transport.close() }
    }

    @Test
    fun postPreservesPendingDraftAcrossLoginAndSubmissionChallengeStages() = runTest {
        val paths = mutableListOf<String>()
        var postRequests = 0
        val transport = HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            paths += path
            when (path) {
                "/login" -> if (paths.size == 1) respond(challenge("/login")) else respond("<input name='fnid' value='fixture'>")
                "/r" -> {
                    if (++postRequests == 1) respond("<form action='/r'><input name='title' value='Fixture post'><textarea name='text'>Pending post draft</textarea>Validation required. If this doesn't work, you can email<div class='g-recaptcha' data-sitekey='fixture-post'></div></form>")
                    else respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/item?id=42"))
                }
                "/item" -> respond("<html>Post confirmed</html>")
                else -> error("Unexpected request $path")
            }
        })
        try {
            val workflow = EditorSubmissionWorkflow(EditorType.POST, -1, service = service(transport), connectivity = object : ConnectivityService {
                override fun isOnline() = true
                override fun isUnmetered() = true
            })
            val login = assertIs<EditorWorkflowResult.Captcha>(workflow.submit(EditorSubmission(title = "Fixture post", text = "Pending post draft")))
            val submission = assertIs<EditorWorkflowResult.Captcha>(workflow.respondToCaptcha(login.challenge, "login-response"))
            assertEquals(EditorWorkflowResult.Ignored, workflow.respondToCaptcha(login.challenge, "stale-login-response"))
            assertEquals(EditorWorkflowResult.Success, workflow.respondToCaptcha(submission.challenge, "post-response"))
            assertEquals(listOf("/login", "/login", "/r", "/r", "/item"), paths)
        } finally { transport.close() }
    }

    @Test
    fun uncertainSubmissionKeepsDraftAndExplainsHowToAvoidADuplicate() = runTest {
        var requests = 0
        val transport = HttpClient(MockEngine {
            if (++requests == 1) respond(challenge("/comment")) else error("Connection lost after dispatch")
        })
        try {
            val workflow = editor(service(transport))
            val pending = assertIs<EditorWorkflowResult.Captcha>(workflow.submit(EditorSubmission(comment = "Keep this draft")))
            val failure = assertIs<EditorWorkflowResult.Failure>(workflow.respondToCaptcha(pending.challenge, "fixture-response"))
            assertEquals("Keep this draft", failure.commentDraft)
            assertTrue(failure.message.contains("Check Hacker News before submitting again"))
            assertEquals(2, requests)
        } finally { transport.close() }
    }

    private fun editor(service: HackerNewsUserService) = EditorSubmissionWorkflow(
        type = EditorType.COMMENT_REPLY, itemId = 42, service = service,
        connectivity = object : ConnectivityService {
            override fun isOnline() = true
            override fun isUnmetered() = true
        },
    )

    private fun service(transport: HttpClient, accounts: Accounts = Accounts(), web: HackerNewsWebRepository = Web()): HackerNewsUserService {
        val repository = KtorHackerNewsActionRepository(KtorHttpClient(transport), KtorHttpClient(transport))
        return HackerNewsUserService(object : HackerNewsAuthenticatedSession {
            override val actions: HackerNewsActionRepository = repository
            override val authenticatedWeb: HackerNewsWebRepository = web
            override val publicWeb: HackerNewsWebRepository = web
            override fun reset() = Unit
        }, accounts)
    }

    private class Accounts : ObservableHackerNewsAccountRepository {
        private val state = MutableStateFlow<HackerNewsAccountState>(HackerNewsAccountState.LoggedIn(HackerNewsAccount("fixture-user", "fixture-password")))
        override val accountState: StateFlow<HackerNewsAccountState> = state
        override suspend fun saveAccount(account: HackerNewsAccount): Boolean { state.value = HackerNewsAccountState.LoggedIn(account); return true }
        override suspend fun clearAccount(): Boolean { state.value = HackerNewsAccountState.LoggedOut; return true }
    }

    private class Web : HackerNewsWebRepository {
        val loads = mutableListOf<String>()
        override suspend fun getUserItems(path: String, username: String): HackerNewsUserItems {
            loads += "$path:$username"
            return HackerNewsUserItems(listOf(42), emptyList())
        }
        override suspend fun getStoryList(path: String, commentsPage: Boolean, day: String?): HackerNewsListPage = error("unused")
        override suspend fun getStoryListPage(url: String, commentsPage: Boolean): HackerNewsListPage = error("unused")
        override suspend fun getListDirectory(): List<Story> = error("unused")
    }

    private fun challenge(action: String): String {
        val fields = when (action) {
            "/comment" -> "<input name='parent' value='42'><textarea name='text'>Keep this draft</textarea>"
            "/vote-confirm" -> "<input name='id' value='42'><input name='how' value='up'>"
            else -> "<input name='goto' value='submit'>"
        }
        return """<html><form action='$action'><input name='acct' value='fixture-user'><input name='pw' value='fixture-password'>$fields Validation required. If this doesn't work, you can email<div class='g-recaptcha' data-sitekey='fixture'></div></form></html>"""
    }
    private fun itemPage(label: String, suffix: String = "") = """<html><span class='titleline'><a>Fixture story</a></span><a href='/fave?id=42&amp;auth=fixture$suffix'>$label</a></html>"""
}
