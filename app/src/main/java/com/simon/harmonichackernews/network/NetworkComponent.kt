package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Looper
import com.simon.harmonichackernews.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.concurrent.Volatile

object NetworkComponent {
    val USER_AGENT: String =
        "Harmonic-HN-Android/" + BuildConfig.VERSION_NAME + "/" + BuildConfig.BUILD_TYPE

    private val networkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transportClient: HttpClient by lazy { createClient() }

    @Volatile
    private var responseCache: CacheStorage? = null

    val httpClientInstance: KtorHttpClient by lazy {
        KtorHttpClient(transportClient, networkScope)
    }

    val hackerNewsApi: HackerNewsApi by lazy { KtorHackerNewsApi(transportClient) }

    val hackerNewsRepository: HackerNewsRepository by lazy {
        DefaultHackerNewsRepository(hackerNewsApi)
    }

    @Volatile
    private var cookieTransportClient: HttpClient? = null

    @Volatile
    private var cookieClient: KtorHttpClient? = null

    private var requestQueueInstance: RequestQueue? = null

    val httpClientInstanceWithCookies: KtorHttpClient
        get() {
            cookieClient?.let { return it }
            return synchronized(NetworkComponent::class.java) {
                cookieClient ?: createCookieClient().also { cookieClient = it }
            }
        }

    private fun createCookieClient(): KtorHttpClient {
        val transport = createClient { install(HttpCookies) }
        cookieTransportClient = transport
        return KtorHttpClient(transport, networkScope)
    }

    fun resetHttpClientCookieInstance() {
        synchronized(NetworkComponent::class.java) {
            cookieTransportClient?.close()
            cookieTransportClient = null
            cookieClient = null
        }
    }

    fun getRequestQueueInstance(context: Context): RequestQueue {
        check(
            !(BuildConfig.DEBUG && !Looper.getMainLooper().isCurrentThread())
        ) { "getRequestQueueInstance currently doesn't support multithreaded access" }

        return requestQueueInstance ?: run {
            val cacheDirectory = File(
                context.applicationContext.cacheDir,
                HTTP_CACHE_DIRECTORY,
            ).apply { mkdirs() }
            val cacheStorage = FileStorage(cacheDirectory)
            responseCache = cacheStorage
            val queueClient = createClient {
                install(HttpCache) {
                    publicStorage(cacheStorage)
                    privateStorage(cacheStorage)
                }
            }
            RequestQueue(
                client = queueClient,
                workerScope = networkScope,
                callbackDispatcher = Dispatchers.Main.immediate,
            ).also { requestQueueInstance = it }
        }
    }

    fun removeCachedStoryResponses(context: Context?, storyId: Int) {
        if (context == null || storyId <= 0) return

        getRequestQueueInstance(context)
        val cacheStorage = responseCache ?: return
        networkScope.launch {
            cacheStorage.removeAll(Url("https://hn.algolia.com/api/v1/items/$storyId"))
            cacheStorage.removeAll(
                Url("https://hacker-news.firebaseio.com/v0/item/$storyId.json")
            )
        }
    }

    private fun createClient(
        configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {},
    ): HttpClient = HttpClient(CIO) {
        expectSuccess = false
        followRedirects = true
        install(HttpTimeout)
        defaultRequest {
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
        configure()
    }

    private const val HTTP_CACHE_DIRECTORY = "ktor_http_cache"
}
