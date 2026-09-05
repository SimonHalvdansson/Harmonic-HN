package com.simon.harmonichackernews.network

import android.content.Context
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.platform.StorageKeyPolicy
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.concurrent.Volatile

internal class AndroidNetworkEnvironment(context: Context) : NetworkCacheMaintenance {
    private val appContext = context.applicationContext
    val userAgent: String =
        "Harmonic-HN-Android/" + BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_TYPE

    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val responseCache: CacheStorage by lazy {
        val cacheDirectory = File(
            appContext.cacheDir,
            StorageKeyPolicy.HTTP_CACHE_DIRECTORY,
        ).apply { mkdirs() }
        FileStorage(cacheDirectory)
    }
    private val authenticatedClientProvider = object : AuthenticatedHttpClientProvider {
        @Volatile
        private var activeClient: HttpClient? = null

        override fun get(): HttpClient {
            activeClient?.let { return it }
            return synchronized(this@AndroidNetworkEnvironment) {
                activeClient ?: createClient { installHarmonicHttpCookies() }
                    .also { activeClient = it }
            }
        }

        override fun reset() {
            synchronized(this@AndroidNetworkEnvironment) {
                activeClient?.close()
                activeClient = null
            }
        }
    }

    /** Shared network graph owned by the Android application composition. */
    val graph: NetworkGraph by lazy {
        NetworkGraphFactory.create(NetworkGraphEnvironment(
            scope = networkScope,
            engine = { CIO.create() },
            authenticatedClientProvider = authenticatedClientProvider,
            userAgent = userAgent,
            cacheMaintenance = this,
            transportDispatcher = Dispatchers.IO,
            configureTransport = {
                install(HttpCache) {
                    publicStorage(responseCache)
                    privateStorage(responseCache)
                }
            },
        ))
    }

    override fun removeCachedStoryResponses(storyId: Int) {
        if (storyId <= 0) return
        networkScope.launch {
            responseCache.removeAll(Url("https://hn.algolia.com/api/v1/items/$storyId"))
            responseCache.removeAll(
                Url("https://hacker-news.firebaseio.com/v0/item/$storyId.json")
            )
        }
    }

    private fun createClient(
        configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {},
    ): HttpClient = createHarmonicHttpClient(CIO.create(), userAgent, configure)
}
