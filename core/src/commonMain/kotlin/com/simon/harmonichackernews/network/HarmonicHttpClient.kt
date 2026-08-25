package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.cancel
import kotlinx.io.readByteArray

/** Common transport policy; each platform supplies only its Ktor engine and optional storage. */
fun createHarmonicHttpClient(
    engine: HttpClientEngine,
    userAgent: String,
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(engine) {
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

/** Canonical buffered GET path for application repositories. */
internal suspend fun HttpClient.getTextOrThrow(url: String): String {
    return prepareGet(url).execute { response ->
        val channel = response.bodyAsChannel()
        try {
            val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (declaredLength != null && declaredLength > DEFAULT_MAX_BUFFERED_BODY_BYTES) {
                throw HttpBodyLimitException(DEFAULT_MAX_BUFFERED_BODY_BYTES, declaredLength)
            }
            val bytes = channel.readRemaining(DEFAULT_MAX_BUFFERED_BODY_BYTES + 1L).readByteArray()
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
