package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
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
