package com.simon.harmonichackernews.network

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.utils.HackerNewsCaptchaWebProtocol
import com.simon.harmonichackernews.utils.HackerNewsLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

typealias HackerNewsCredentials = HackerNewsAccount

data class HackerNewsCaptchaFormField(
    val name: String,
    val value: String,
)

data class HackerNewsCaptchaChallenge(
    val actionUrl: String,
    val siteKey: String,
    val formFields: List<HackerNewsCaptchaFormField>,
    val useCookies: Boolean,
) {
    val captchaHtml: String
        get() = """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://www.google.com/recaptcha/api.js" async defer></script>
            <style>body{margin:0;padding:16px;background:#fff;font-family:sans-serif}.wrap{min-height:420px}</style>
            </head><body><div class="wrap"><div class="g-recaptcha" data-sitekey="${siteKey.htmlEscape()}"></div></div></body></html>
        """.trimIndent()

    val isLoginChallenge: Boolean
        get() = actionUrl.toNetworkUrlOrNull()?.let { url ->
            url.pathSegments.size == 1 && url.pathSegments.first() == LOGIN_PATH
        } == true

    private companion object {
        const val LOGIN_PATH = "login"
    }
}

enum class HackerNewsActionFailureReason {
    GENERAL,
    MISSING_CREDENTIALS,
    INVALID_CREDENTIALS,
    /** The request was dispatched, but the client could not confirm whether HN committed it. */
    INDETERMINATE,
}

/** Transport failed after a mutating HN request was dispatched. */
internal class IndeterminateHackerNewsActionException(cause: Throwable) :
    RuntimeException(cause.message, cause)

sealed interface HackerNewsActionResult {
    data class Success(
        val itemId: Int? = null,
        val itemTitle: String? = null,
    ) : HackerNewsActionResult

    data class Failure(
        val summary: String,
        val detail: String? = null,
        val reason: HackerNewsActionFailureReason = HackerNewsActionFailureReason.GENERAL,
    ) : HackerNewsActionResult

    data class Captcha(val challenge: HackerNewsCaptchaChallenge) : HackerNewsActionResult
}

object HackerNewsActionMessages {
    fun favoriteFailure(summary: String?, response: String?): String {
        val safeSummary = summary?.trim()?.takeIf(String::isNotEmpty)
            ?: "Couldn't add to favorites"
        val safeResponse = response?.trim().orEmpty()
        if (safeResponse.isEmpty() || safeResponse == safeSummary) return safeSummary

        val compactResponse = safeResponse.replace('\n', ' ').replace(whitespace, " ")
        val displayedResponse = if (compactResponse.length > MAXIMUM_DETAIL_CHARS) {
            compactResponse.take(MAXIMUM_DETAIL_CHARS - 3) + "…"
        } else {
            compactResponse
        }
        return "$safeSummary: $displayedResponse"
    }

    private val whitespace = Regex("\\s+")
    private const val MAXIMUM_DETAIL_CHARS = 160
}

interface HackerNewsActionRepository {
    suspend fun login(credentials: HackerNewsCredentials): HackerNewsActionResult

    suspend fun continueLoginWithCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
    ): HackerNewsActionResult

    suspend fun vote(
        credentials: HackerNewsCredentials,
        itemId: String,
        direction: String,
    ): HackerNewsActionResult

    suspend fun comment(
        credentials: HackerNewsCredentials,
        itemId: String,
        text: String,
    ): HackerNewsActionResult

    suspend fun submit(
        credentials: HackerNewsCredentials,
        title: String,
        text: String,
        url: String,
    ): HackerNewsActionResult

    suspend fun submitAfterLoginCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
        title: String,
        text: String,
        url: String,
    ): HackerNewsActionResult

    suspend fun continueCaptchaAction(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
    ): HackerNewsActionResult

    suspend fun setFavorite(
        credentials: HackerNewsCredentials,
        itemId: Int,
        favorite: Boolean,
    ): HackerNewsActionResult
}

class KtorHackerNewsActionRepository(
    private val client: KtorHttpClient,
    private val cookieClient: KtorHttpClient,
) : HackerNewsActionRepository {
    override suspend fun login(credentials: HackerNewsCredentials): HackerNewsActionResult =
        when (val page = loadLoginPage(loginRequest(credentials))) {
            is PageResult.Success -> HackerNewsActionResult.Success()
            is PageResult.Result -> page.value
        }

    override suspend fun continueLoginWithCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
    ): HackerNewsActionResult = when (
        val page = loadLoginPage(buildCaptchaRequest(challenge, captchaResponse))
    ) {
        is PageResult.Success -> HackerNewsActionResult.Success()
        is PageResult.Result -> page.value
    }

    override suspend fun vote(
        credentials: HackerNewsCredentials,
        itemId: String,
        direction: String,
    ): HackerNewsActionResult = executeAction(
        client = client,
        request = postForm(
            VOTE_PATH,
            LOGIN_PARAM_ACCT to credentials.username,
            LOGIN_PARAM_PW to credentials.password,
            VOTE_PARAM_ID to itemId,
            VOTE_PARAM_HOW to direction,
        ),
        useCookies = false,
    )

    override suspend fun comment(
        credentials: HackerNewsCredentials,
        itemId: String,
        text: String,
    ): HackerNewsActionResult = executeAction(
        client = client,
        request = postForm(
            COMMENT_PATH,
            LOGIN_PARAM_ACCT to credentials.username,
            LOGIN_PARAM_PW to credentials.password,
            COMMENT_PARAM_PARENT to itemId,
            COMMENT_PARAM_TEXT to text,
        ),
        useCookies = false,
    )

    override suspend fun submit(
        credentials: HackerNewsCredentials,
        title: String,
        text: String,
        url: String,
    ): HackerNewsActionResult = when (val page = loadLoginPage(loginRequest(credentials))) {
        is PageResult.Result -> page.value
        is PageResult.Success -> submitWithPage(page.body, title, text, url)
    }

    override suspend fun submitAfterLoginCaptcha(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
        title: String,
        text: String,
        url: String,
    ): HackerNewsActionResult = when (
        val page = loadLoginPage(buildCaptchaRequest(challenge, captchaResponse))
    ) {
        is PageResult.Result -> page.value
        is PageResult.Success -> submitWithPage(page.body, title, text, url)
    }

    override suspend fun continueCaptchaAction(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
    ): HackerNewsActionResult = executeAction(
        client = if (challenge.useCookies) cookieClient else client,
        request = buildCaptchaRequest(challenge, captchaResponse),
        useCookies = challenge.useCookies,
    )

    override suspend fun setFavorite(
        credentials: HackerNewsCredentials,
        itemId: Int,
        favorite: Boolean,
    ): HackerNewsActionResult {
        when (val loginPage = loadLoginPage(loginRequest(credentials))) {
            is PageResult.Result -> return loginPage.value
            is PageResult.Success -> Unit
        }

        val itemUrl = HackerNewsLinks.itemUrl(itemId)
        val initialPage = when (val page = loadPage(cookieClient, get(itemUrl), true)) {
            is PageResult.Result -> return page.value
            is PageResult.Success -> page.body
        }
        val initialDocument = Ksoup.parse(initialPage, baseUri = HackerNewsLinks.ROOT_URL)
        val itemTitle = findItemTitle(initialDocument, itemId)
        val favoriteLink = findFavoriteLink(initialDocument, itemId, favorite)
        if (favoriteLink.alreadyDesiredState) {
            return HackerNewsActionResult.Success(itemId, itemTitle)
        }
        val actionUrl = favoriteLink.actionUrl ?: return HackerNewsActionResult.Failure(
            "Favorite unavailable",
            "HN did not return a favorite action for this item.",
        )

        when (val action = executeAction(cookieClient, get(actionUrl), true)) {
            is HackerNewsActionResult.Success -> Unit
            else -> return action
        }

        val verificationPage = when (
            val page = loadPageAfterMutation(cookieClient, get(itemUrl), true)
        ) {
            is PageResult.Result -> return HackerNewsActionResult.Failure(
                summary = "Favorite update not confirmed",
                detail = (page.value as? HackerNewsActionResult.Failure)?.detail,
                reason = HackerNewsActionFailureReason.INDETERMINATE,
            )
            is PageResult.Success -> page.body
        }
        val favoriteConfirmed = try {
            findFavoriteLink(verificationPage, itemId, favorite).alreadyDesiredState
        } catch (error: Throwable) {
            throw IndeterminateHackerNewsActionException(error)
        }
        if (!favoriteConfirmed) {
            return HackerNewsActionResult.Failure(
                "Favorite update not confirmed",
                if (favorite) {
                    "HN still reports this item as not favorited."
                } else {
                    "HN still reports this item as favorited."
                },
            )
        }
        return HackerNewsActionResult.Success(itemId, itemTitle)
    }

    private suspend fun submitWithPage(
        loginPage: String,
        title: String,
        text: String,
        url: String,
    ): HackerNewsActionResult {
        val fnid = Ksoup.parse(loginPage, baseUri = HackerNewsLinks.ROOT_URL)
            .selectFirst("input[name=fnid]")
            ?.attr("value")
            ?.takeIf(String::isNotBlank)
            ?: return HackerNewsActionResult.Failure(
                "HN submit form parsing error",
                "No fnid found on /submit",
            )
        return executeAction(
            client = cookieClient,
            request = postForm(
                SUBMIT_POST_PATH,
                SUBMIT_PARAM_FNID to fnid,
                SUBMIT_PARAM_FNOP to DEFAULT_FNOP,
                SUBMIT_PARAM_TITLE to title,
                SUBMIT_PARAM_URL to url,
                SUBMIT_PARAM_TEXT to text,
            ),
            useCookies = true,
        )
    }

    private fun loginRequest(credentials: HackerNewsCredentials): HttpRequest = postForm(
        LOGIN_PATH,
        LOGIN_PARAM_ACCT to credentials.username,
        LOGIN_PARAM_PW to credentials.password,
        LOGIN_PARAM_GOTO to SUBMIT_PATH,
    )

    private suspend fun loadLoginPage(request: HttpRequest): PageResult =
        when (val page = loadLoginResponse(request)) {
            is PageResult.Result -> page
            is PageResult.Success -> {
                val document = Ksoup.parse(page.body, baseUri = HackerNewsLinks.ROOT_URL)
                if (document.selectFirst("input[name=fnid]") == null) {
                    PageResult.Result(
                        HackerNewsActionResult.Failure("Bad login", "Submit form not found"),
                    )
                } else {
                    page
                }
            }
        }

    /**
     * HN completes a successful login POST with a 302 to the requested page. Ktor deliberately
     * does not follow POST redirects, and following one as another POST would resend credentials
     * to the redirect target. Follow only HN's expected submit-page redirect as a fresh GET.
     */
    private suspend fun loadLoginResponse(request: HttpRequest): PageResult {
        val response = cookieClient.execute(request)
        if (response.code !in HN_GET_REDIRECT_CODES) {
            return classifyResponse(response, useCookies = true)
        }

        val location = response.header(LOCATION_HEADER)
        response.close()
        val redirectUrl = request.url.resolve(location.orEmpty())?.takeIf { url ->
            url.scheme == "https" &&
                url.host.equals(HackerNewsLinks.HOST, ignoreCase = true) &&
                url.encodedPath == "/$SUBMIT_PATH"
        } ?: return PageResult.Result(
            HackerNewsActionResult.Failure(
                "Unexpected login redirect",
                "HN did not redirect to its submit page.",
            ),
        )
        return loadPage(cookieClient, get(redirectUrl.toString()), useCookies = true)
    }

    private suspend fun executeAction(
        client: KtorHttpClient,
        request: HttpRequest,
        useCookies: Boolean,
    ): HackerNewsActionResult = runIndeterminateAfterDispatch {
        when (
            val page = loadActionResponse(
                client,
                request,
                useCookies,
            )
        ) {
            is PageResult.Success -> HackerNewsActionResult.Success()
            is PageResult.Result -> page.value
        }
    }

    /**
     * HN completes successful mutations with a redirect to the affected page. Ktor does not
     * follow POST redirects, so mirror the legacy OkHttp behavior with an explicit, same-origin
     * GET. Inspecting the destination page preserves HN's error and captcha classification.
     */
    private suspend fun loadActionResponse(
        client: KtorHttpClient,
        request: HttpRequest,
        useCookies: Boolean,
    ): PageResult {
        val response = client.execute(request)
        if (response.code !in HN_GET_REDIRECT_CODES) {
            return classifyResponse(
                response = response,
                useCookies = useCookies,
                indeterminateOnHttpFailure = true,
            )
        }

        val location = response.header(LOCATION_HEADER)
        response.close()
        val redirectUrl = request.url.resolve(location.orEmpty())?.takeIf { url ->
            url.scheme == "https" &&
                url.host.equals(HackerNewsLinks.HOST, ignoreCase = true)
        } ?: return PageResult.Result(
            HackerNewsActionResult.Failure(
                summary = "Unexpected action redirect",
                detail = "HN did not redirect to one of its pages.",
                reason = HackerNewsActionFailureReason.INDETERMINATE,
            ),
        )
        return loadPage(
            client = client,
            request = get(redirectUrl.toString()),
            useCookies = useCookies,
            indeterminateOnHttpFailure = true,
        )
    }

    private suspend fun loadPageAfterMutation(
        client: KtorHttpClient,
        request: HttpRequest,
        useCookies: Boolean,
    ): PageResult = runIndeterminateAfterDispatch {
        loadPage(
            client,
            request,
            useCookies,
            indeterminateOnHttpFailure = true,
        )
    }

    private suspend fun <T> runIndeterminateAfterDispatch(block: suspend () -> T): T = try {
        block()
    } catch (error: IndeterminateHackerNewsActionException) {
        throw error
    } catch (error: CancellationException) {
        if (!currentCoroutineContext().isActive) throw error
        throw IndeterminateHackerNewsActionException(error)
    } catch (error: Throwable) {
        throw IndeterminateHackerNewsActionException(error)
    }

    private suspend fun loadPage(
        client: KtorHttpClient,
        request: HttpRequest,
        useCookies: Boolean,
        indeterminateOnHttpFailure: Boolean = false,
    ): PageResult = classifyResponse(
        response = client.execute(request),
        useCookies = useCookies,
        indeterminateOnHttpFailure = indeterminateOnHttpFailure,
    )

    private suspend fun classifyResponse(
        response: HttpResponse,
        useCookies: Boolean,
        indeterminateOnHttpFailure: Boolean = false,
    ): PageResult = try {
        val body = response.body.readText()
        if (!response.isSuccessful) {
            PageResult.Result(
                HackerNewsActionResult.Failure(
                    "Unsuccessful response",
                    response.toString(),
                    reason = if (indeterminateOnHttpFailure) {
                        HackerNewsActionFailureReason.INDETERMINATE
                    } else {
                        HackerNewsActionFailureReason.GENERAL
                    },
                ),
            )
        } else {
            classifyPage(body, useCookies)
        }
    } finally {
        response.close()
    }

    private fun classifyPage(body: String, useCookies: Boolean): PageResult = when {
        body.contains(UNKNOWN_OR_EXPIRED_LINK_TEXT) -> PageResult.Result(
            HackerNewsActionResult.Failure("Unknown or expired link", body),
        )

        body.contains(BAD_LOGIN_TEXT) -> PageResult.Result(
            HackerNewsActionResult.Failure(
                summary = "Bad login",
                detail = "Your session has expired or credentials are invalid. Logged out.",
                reason = HackerNewsActionFailureReason.INVALID_CREDENTIALS,
            ),
        )

        HackerNewsActionParser.isCaptchaRequired(body) -> PageResult.Result(
            HackerNewsActionParser.parseCaptchaChallenge(body, useCookies)?.let {
                HackerNewsActionResult.Captcha(it)
            } ?: HackerNewsActionResult.Failure(
                "Captcha parsing error",
                "HN asked for a captcha, but Harmonic could not read the challenge form.",
            ),
        )

        else -> PageResult.Success(body)
    }

    private fun buildCaptchaRequest(
        challenge: HackerNewsCaptchaChallenge,
        captchaResponse: String,
    ): HttpRequest {
        val form = FormRequestBody.Builder()
        challenge.formFields.forEach { form.add(it.name, it.value) }
        form.add(HackerNewsCaptchaWebProtocol.RESPONSE_FIELD, captchaResponse)
        return HttpRequest.Builder()
            .url(challenge.actionUrl)
            .post(form.build())
            .build()
    }

    private fun postForm(path: String, vararg fields: Pair<String, String>): HttpRequest {
        val form = FormRequestBody.Builder()
        fields.forEach { (name, value) -> form.add(name, value) }
        return HttpRequest.Builder()
            .url(HackerNewsLinks.absolutePath(path))
            .post(form.build())
            .build()
    }

    private fun get(url: String): HttpRequest = HttpRequest.Builder().url(url).get().build()

    private sealed interface PageResult {
        data class Success(val body: String) : PageResult
        data class Result(val value: HackerNewsActionResult) : PageResult
    }

    private companion object {
        const val LOGIN_PATH = "login"
        const val VOTE_PATH = "vote"
        const val COMMENT_PATH = "comment"
        const val SUBMIT_PATH = "submit"
        const val SUBMIT_POST_PATH = "r"
        const val LOGIN_PARAM_ACCT = "acct"
        const val LOGIN_PARAM_PW = "pw"
        const val LOGIN_PARAM_GOTO = "goto"
        const val VOTE_PARAM_ID = "id"
        const val VOTE_PARAM_HOW = "how"
        const val COMMENT_PARAM_PARENT = "parent"
        const val COMMENT_PARAM_TEXT = "text"
        const val SUBMIT_PARAM_TITLE = "title"
        const val SUBMIT_PARAM_URL = "url"
        const val SUBMIT_PARAM_TEXT = "text"
        const val SUBMIT_PARAM_FNID = "fnid"
        const val SUBMIT_PARAM_FNOP = "fnop"
        const val DEFAULT_FNOP = "submit-page"
        const val LOCATION_HEADER = "Location"
        const val BAD_LOGIN_TEXT = "Bad login."
        const val UNKNOWN_OR_EXPIRED_LINK_TEXT = "Unknown or expired link."
        val HN_GET_REDIRECT_CODES = setOf(301, 302, 303)
    }
}

object HackerNewsActionParser {
    private const val CAPTCHA_VALIDATION_TEXT =
        "Validation required. If this doesn't work, you can email"

    fun isCaptchaRequired(body: String?): Boolean =
        body?.contains(CAPTCHA_VALIDATION_TEXT) == true && body.contains("g-recaptcha")

    fun parseCaptchaChallenge(
        body: String,
        useCookies: Boolean,
    ): HackerNewsCaptchaChallenge? {
        val document = Ksoup.parse(body, baseUri = HackerNewsLinks.ROOT_URL)
        val captcha = document.selectFirst(".g-recaptcha[data-sitekey]") ?: return null
        val form = captcha.closest("form") ?: document.selectFirst("form[action]") ?: return null
        val actionUrl = form.absUrl("action").ifBlank {
            HackerNewsLinks.ROOT_URL.toNetworkUrlOrNull()
                ?.resolve(form.attr("action"))
                ?.toString()
                .orEmpty()
        }.takeIf(String::isNotBlank) ?: return null
        val siteKey = captcha.attr("data-sitekey").takeIf(String::isNotBlank) ?: return null
        val fields = form.select("input[name], textarea[name]").mapNotNull { input ->
            val name = input.attr("name")
            val type = input.attr("type")
            if (
                name.isBlank() ||
                name == HackerNewsCaptchaWebProtocol.RESPONSE_FIELD ||
                type.equals("submit", ignoreCase = true) ||
                type.equals("button", ignoreCase = true)
            ) {
                null
            } else {
                HackerNewsCaptchaFormField(name, input.value())
            }
        }
        return HackerNewsCaptchaChallenge(actionUrl, siteKey, fields, useCookies)
    }
}

private data class FavoriteLinkResult(
    val actionUrl: String? = null,
    val alreadyDesiredState: Boolean = false,
)

private fun findFavoriteLink(body: String, itemId: Int, favorite: Boolean): FavoriteLinkResult =
    findFavoriteLink(Ksoup.parse(body, baseUri = HackerNewsLinks.ROOT_URL), itemId, favorite)

private fun findFavoriteLink(
    document: Document,
    itemId: Int,
    favorite: Boolean,
): FavoriteLinkResult {
    document.select("a[href]").forEach { link ->
        val actionUrl = link.absUrl("href").toNetworkUrlOrNull() ?: return@forEach
        if (
            actionUrl.scheme != "https" ||
            actionUrl.host != HackerNewsLinks.HOST ||
            actionUrl.encodedPath != "/fave" ||
            actionUrl.queryParameter("id") != itemId.toString() ||
            actionUrl.queryParameter("auth").isNullOrBlank()
        ) {
            return@forEach
        }
        val unfavorite = actionUrl.queryParameter("un")
        if (unfavorite != null && unfavorite != "t") return@forEach
        val currentlyFavorited = unfavorite == "t"
        return if (currentlyFavorited == favorite) {
            FavoriteLinkResult(alreadyDesiredState = true)
        } else {
            FavoriteLinkResult(actionUrl = actionUrl.toString())
        }
    }
    return FavoriteLinkResult()
}

private fun findItemTitle(document: Document, itemId: Int): String? =
    document.getElementById(itemId.toString())
        ?.selectFirst("span.titleline > a")
        ?.text()
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun String.htmlEscape(): String = buildString(length) {
    this@htmlEscape.forEach { character ->
        append(
            when (character) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> character
            },
        )
    }
}
