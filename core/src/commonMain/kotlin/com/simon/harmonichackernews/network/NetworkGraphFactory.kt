package com.simon.harmonichackernews.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

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
    val transportDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // Optional transport with connection recovery enabled, used only by read-only repositories.
    // Generic HTTP and HN actions continue to use engine, even when the action is a GET.
    val readOnlyEngine: (() -> HttpClientEngine)? = null,
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
            transport = NetworkTransport(environment.transportDispatcher) {
                createHarmonicHttpClient(
                    environment.engine(),
                    environment.userAgent,
                    environment.configureTransport,
                )
            },
            scope = environment.scope,
            authenticatedClientProvider = authenticated,
            userAgent = environment.userAgent,
            cacheMaintenance = environment.cacheMaintenance,
            readOnlyTransport = environment.readOnlyEngine?.let { engine ->
                NetworkTransport(environment.transportDispatcher) {
                    createHarmonicHttpClient(engine(), environment.userAgent, environment.configureTransport)
                }
            },
        )
    }
}
