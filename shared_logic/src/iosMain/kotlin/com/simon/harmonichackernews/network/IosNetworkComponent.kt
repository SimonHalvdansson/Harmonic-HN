package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

/** iOS composition root for shared networking. The iOS app owns this instance's lifecycle. */
class IosNetworkComponent(
    userAgent: String,
) {
    val httpClient: HttpClient = createHarmonicHttpClient(Darwin.create(), userAgent)
    val hackerNewsRepository: HackerNewsRepository = DefaultHackerNewsRepository(
        KtorHackerNewsApi(httpClient),
    )
    val algoliaRepository: AlgoliaRepository = KtorAlgoliaRepository(httpClient)

    fun close() {
        httpClient.close()
    }
}
