package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.network.NetworkGraph
import com.simon.harmonichackernews.network.ResettableAuthenticatedHttpClientProvider
import com.simon.harmonichackernews.network.createHarmonicHttpClient
import com.simon.harmonichackernews.platform.AppPlatformDependencies
import com.simon.harmonichackernews.platform.CredentialBackedHackerNewsAccountRepository
import com.simon.harmonichackernews.platform.CredentialStore
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow

/**
 * Desktop lifecycle owner for the real shared application and CIO networking graphs.
 *
 * Constructing the graph does not make a request. A production desktop host can provide durable
 * [KeyValueStore] implementations and supported [AppPlatformDependencies]; [inMemory] is intended
 * for previews and smoke hosts that must not write credentials, settings, or application data.
 */
class DesktopHarmonicAppBootstrap(
    userAgent: String,
    platform: AppPlatformDependencies,
    settingsStore: KeyValueStore,
    appDataStore: KeyValueStore,
    previewCacheStore: KeyValueStore,
    settingsChanges: Flow<Unit>,
    currentTheme: () -> String? = { null },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val authenticatedClients = ResettableAuthenticatedHttpClientProvider {
        createHarmonicHttpClient(CIO.create(), userAgent) {
            install(HttpCookies)
        }
    }
    private var closed = false

    val network = NetworkGraph(
        transportClient = createHarmonicHttpClient(CIO.create(), userAgent),
        scope = scope,
        authenticatedClientProvider = authenticatedClients,
    )
    val app = HarmonicAppComposition(
        network = network,
        platform = platform,
        settingsStore = settingsStore,
        appDataStore = appDataStore,
        previewCacheStore = previewCacheStore,
        settingsChanges = settingsChanges,
        currentTheme = currentTheme,
    )

    fun close() {
        if (closed) return
        closed = true
        network.close()
        scope.cancel()
    }

    companion object {
        /** Creates an operational but side-effect-free host with unsupported native facilities. */
        fun inMemory(userAgent: String): DesktopHarmonicAppBootstrap {
            val settings = InMemoryKeyValueStore()
            val credentials = InMemoryCredentialStore()
            return DesktopHarmonicAppBootstrap(
                userAgent = userAgent,
                platform = AppPlatformDependencies(
                    credentials = credentials,
                    accounts = CredentialBackedHackerNewsAccountRepository(
                        credentials,
                    ),
                ),
                settingsStore = settings,
                appDataStore = InMemoryKeyValueStore(),
                previewCacheStore = InMemoryKeyValueStore(),
                settingsChanges = settings.changes,
            )
        }
    }
}

private class InMemoryCredentialStore : CredentialStore {
    private val values = mutableMapOf<String, String>()

    override fun read(id: String): String? = values[id]

    override fun write(id: String, value: String): Boolean {
        values[id] = value
        return true
    }

    override fun remove(id: String): Boolean {
        values.remove(id)
        return true
    }
}
