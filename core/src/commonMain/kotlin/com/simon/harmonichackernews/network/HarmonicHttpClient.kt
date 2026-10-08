package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.utils.io.readBuffer
import io.ktor.utils.io.cancel
import kotlinx.io.readByteArray
import kotlinx.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

/** Common transport policy; takes ownership of a fresh platform engine and optional storage. */
fun createHarmonicHttpClient(
    engine: HttpClientEngine,
    userAgent: String,
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(object : HttpClientEngineFactory<HttpClientEngineConfig> {
    // HttpClient(engine) leaves engine ownership with the caller. Use the managed factory
    // overload so closing/resetting a client also releases its engine (including config copies).
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine = engine
}) {
    expectSuccess = false
    followRedirects = true
    install(HttpTimeout) {
        connectTimeoutMillis = HARMONIC_CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = HARMONIC_SOCKET_TIMEOUT_MILLIS
        requestTimeoutMillis = HARMONIC_REQUEST_TIMEOUT_MILLIS
    }
    defaultRequest {
        header(HttpHeaders.UserAgent, userAgent)
    }
    configure()
}

/** Installs cookie handling with RFC 6265's default-path behavior. */
fun HttpClientConfig<*>.installHarmonicHttpCookies() {
    install(HttpCookies) {
        storage = Rfc6265CookiesStorage()
    }
}

/**
 * Ktor 3.5.1 defaults a path-less cookie to the complete request path. RFC 6265 instead uses the
 * request path up to its final slash, so a cookie received from `/login` must also match `/submit`.
 */
private class Rfc6265CookiesStorage(
    private val delegate: CookiesStorage = AcceptAllCookiesStorage(),
) : CookiesStorage {
    override suspend fun get(requestUrl: Url): List<Cookie> = delegate.get(requestUrl)

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        val normalizedCookie = if (cookie.path?.startsWith('/') == true) {
            cookie
        } else {
            cookie.copy(path = requestUrl.defaultCookiePath())
        }
        delegate.addCookie(requestUrl, normalizedCookie)
    }

    override fun close() = delegate.close()
}

private fun Url.defaultCookiePath(): String {
    val requestPath = encodedPath
    if (!requestPath.startsWith('/') || requestPath.count { it == '/' } <= 1) return "/"
    return requestPath.substringBeforeLast('/').ifEmpty { "/" }
}

/**
 * Buffered read-only content requests. Never use this for action URLs, even if they use GET.
 * Retry transient failures twice; keep generic transport retries disabled to protect writes.
 */
internal suspend fun HttpClient.getTextOrThrow(url: String): String {
    var retries = 0
    while (true) {
        try {
            return getTextOnceOrThrow(url)
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (!error.isRetryableReadFailure() || retries >= 2) throw error
            delay(if (retries++ == 0) 500L else 1_500L)
        }
    }
}

/** Shared by request retries and foreground feed recovery; never classify writes with this. */
internal fun Throwable.isRetryableReadFailure(): Boolean = when (this) {
    is CancellationException, is HttpBodyLimitException -> false
    is HttpStatusException -> statusCode in setOf(502, 503, 504)
    is HttpRequestTimeoutException, is IOException -> true
    else -> false
}

private suspend fun HttpClient.getTextOnceOrThrow(url: String): String {
    return prepareGet(url) {
        header(HttpHeaders.CacheControl, "no-store")
    }.execute { response ->
        val channel = response.bodyAsChannel()
        try {
            val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (declaredLength != null && declaredLength > DEFAULT_MAX_BUFFERED_BODY_BYTES) {
                throw HttpBodyLimitException(DEFAULT_MAX_BUFFERED_BODY_BYTES, declaredLength)
            }
            val bytes = channel.readBuffer(DEFAULT_MAX_BUFFERED_BODY_BYTES + 1L).readByteArray()
            // Ktor's bounded read can finish without throwing an already-closed channel's error.
            channel.closedCause?.let { throw it }
            if (bytes.size > DEFAULT_MAX_BUFFERED_BODY_BYTES) {
                throw HttpBodyLimitException(DEFAULT_MAX_BUFFERED_BODY_BYTES, bytes.size.toLong())
            }
            val body = bytes.decodeToString()
            if (response.status.value !in 200..299) {
                throw HttpStatusException(response.status.value, response.status.description, url)
            }
            body
        } finally {
            channel.cancel()
        }
    }
}

internal const val DEFAULT_MAX_BUFFERED_BODY_BYTES = 8 * 1024 * 1024
private const val HARMONIC_CONNECT_TIMEOUT_MILLIS = 15_000L
private const val HARMONIC_SOCKET_TIMEOUT_MILLIS = 30_000L
private const val HARMONIC_REQUEST_TIMEOUT_MILLIS = 60_000L
