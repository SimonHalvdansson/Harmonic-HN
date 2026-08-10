package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

/** Common transport policy; each platform supplies only its Ktor engine and optional storage. */
fun createHarmonicHttpClient(
    engine: HttpClientEngine,
    userAgent: String,
    configure: HttpClientConfig<*>.() -> Unit = {},
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    followRedirects = true
    install(HttpTimeout)
    defaultRequest {
        header(HttpHeaders.UserAgent, userAgent)
    }
    configure()
}

/** Canonical buffered GET path for shared repositories. */
internal suspend fun HttpClient.getTextOrThrow(url: String): String {
    val response = get(url)
    val body = response.bodyAsText()
    if (response.status.value !in 200..299) {
        throw HttpStatusException(response.status.value, response.status.description, url)
    }
    return body
}
