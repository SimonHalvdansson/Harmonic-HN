package com.simon.harmonichackernews.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope

/** Inputs that genuinely differ between Ktor hosts. */
data class NetworkGraphEnvironment(
    val scope: CoroutineScope,
    val userAgent: String,
    val engine: () -> HttpClientEngine,
    val cacheMaintenance: NetworkCacheMaintenance = NetworkCacheMaintenance.None,
    val authenticatedClientProvider: AuthenticatedHttpClientProvider? = null,
    val configureTransport: HttpClientConfig<*>.() -> Unit = {},
    val configureAuthenticated: HttpClientConfig<*>.() -> Unit = {
        installHarmonicHttpCookies()
    },
)

/** Canonical network bootstrap used by Android, iOS and desktop. */
object NetworkGraphFactory {
    fun create(environment: NetworkGraphEnvironment): NetworkGraph {
        val authenticated = environment.authenticatedClientProvider
            ?: ResettableAuthenticatedHttpClientProvider {
                createHarmonicHttpClient(
                    environment.engine(),
                    environment.userAgent,
                    environment.configureAuthenticated,
                )
            }
        return NetworkGraph(
            transportClient = createHarmonicHttpClient(
                environment.engine(),
                environment.userAgent,
                environment.configureTransport,
            ),
            scope = environment.scope,
            authenticatedClientProvider = authenticated,
            userAgent = environment.userAgent,
            cacheMaintenance = environment.cacheMaintenance,
        )
    }
}
