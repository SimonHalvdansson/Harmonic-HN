package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Looper
import com.simon.harmonichackernews.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cache.storage.CacheStorage
import io.ktor.client.plugins.cache.storage.FileStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
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

    val replyScanner: ReplyScanner by lazy {
        DefaultReplyScanner(hackerNewsApi)
    }

    val algoliaRepository: AlgoliaRepository by lazy {
        KtorAlgoliaRepository(transportClient)
    }

    val linkPreviewRepository: LinkPreviewRepository by lazy {
        KtorLinkPreviewRepository(transportClient)
    }

    val linkSummaryRepository: LinkSummaryRepository by lazy {
        KtorLinkSummaryRepository(transportClient, linkPreviewRepository)
    }

    val cloudSummaryRepository: CloudSummaryRepository by lazy {
        KtorCloudSummaryRepository(httpClientInstance)
    }

    val aiModelCatalogRepository: AiModelCatalogRepository by lazy {
        KtorAiModelCatalogRepository(httpClientInstance)
    }

    val openRouterProviderIconRepository: OpenRouterProviderIconRepository by lazy {
        KtorOpenRouterProviderIconRepository(httpClientInstance)
    }

    val hackerNewsWebRepository: HackerNewsWebRepository by lazy {
        KtorHackerNewsWebRepository(transportClient)
    }

    /** Callback bridge for Android callers while shared repositories remain suspend-first. */
    fun <T> launchCallbackRequest(
        request: suspend () -> T,
        onSuccess: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): Job = networkScope.launch {
        try {
            val result = request()
            withContext(Dispatchers.Main.immediate) { onSuccess(result) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            withContext(Dispatchers.Main.immediate) { onFailure(error) }
        }
    }

    /** Collects a shared flow on the network scope and delivers every event on Android's UI. */
    fun <T> collectCallbackFlow(
        flow: Flow<T>,
        onEach: (T) -> Unit,
        onFailure: (Throwable) -> Unit,
    ): Job = networkScope.launch {
        try {
            flow.collect { event ->
                withContext(Dispatchers.Main.immediate) { onEach(event) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            withContext(Dispatchers.Main.immediate) { onFailure(error) }
        }
    }

    @Volatile
    private var cookieTransportClient: HttpClient? = null

    @Volatile
    private var cookieClient: KtorHttpClient? = null

    @Volatile
    private var authenticatedWebRepository: HackerNewsWebRepository? = null

    @Volatile
    private var authenticatedActionRepository: HackerNewsActionRepository? = null

    private var requestQueueInstance: RequestQueue? = null

    val httpClientInstanceWithCookies: KtorHttpClient
        get() {
            cookieClient?.let { return it }
            return synchronized(NetworkComponent::class.java) {
                cookieClient ?: createCookieClient().also { cookieClient = it }
            }
        }

    val authenticatedHackerNewsWebRepository: HackerNewsWebRepository
        get() {
            authenticatedWebRepository?.let { return it }
            return synchronized(NetworkComponent::class.java) {
                authenticatedWebRepository ?: run {
                    createCookieClient()
                    checkNotNull(authenticatedWebRepository)
                }
            }
        }

    val hackerNewsActionRepository: HackerNewsActionRepository
        get() {
            authenticatedActionRepository?.let { return it }
            return synchronized(NetworkComponent::class.java) {
                authenticatedActionRepository ?: run {
                    httpClientInstanceWithCookies
                    checkNotNull(authenticatedActionRepository)
                }
            }
        }

    private fun createCookieClient(): KtorHttpClient {
        val transport = createClient { install(HttpCookies) }
        val client = KtorHttpClient(transport, networkScope)
        cookieTransportClient = transport
        authenticatedWebRepository = KtorHackerNewsWebRepository(transport)
        authenticatedActionRepository = KtorHackerNewsActionRepository(httpClientInstance, client)
        return client
    }

    fun resetHttpClientCookieInstance() {
        synchronized(NetworkComponent::class.java) {
            cookieTransportClient?.close()
            cookieTransportClient = null
            cookieClient = null
            authenticatedWebRepository = null
            authenticatedActionRepository = null
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
                client = KtorHttpClient(queueClient, networkScope),
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
    ): HttpClient = createHarmonicHttpClient(CIO.create(), USER_AGENT, configure)

    private const val HTTP_CACHE_DIRECTORY = "ktor_http_cache"
}
