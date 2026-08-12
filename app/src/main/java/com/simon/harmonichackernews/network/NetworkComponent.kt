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

    private val authenticatedClientProvider = object : AuthenticatedHttpClientProvider {
        @Volatile
        private var activeClient: HttpClient? = null

        override fun get(): HttpClient {
            activeClient?.let { return it }
            return synchronized(NetworkComponent::class.java) {
                activeClient ?: createClient { install(HttpCookies) }
                    .also { activeClient = it }
            }
        }

        override fun reset() {
            synchronized(NetworkComponent::class.java) {
                activeClient?.close()
                activeClient = null
            }
        }
    }

    /** Shared network graph used by the application composition and legacy Android bridges. */
    val graph: NetworkGraph by lazy {
        NetworkGraph(
            transportClient = transportClient,
            scope = networkScope,
            authenticatedClientProvider = authenticatedClientProvider,
        )
    }

    @Volatile
    private var responseCache: CacheStorage? = null

    val httpClientInstance: KtorHttpClient by lazy {
        graph.httpClient
    }

    val hackerNewsApi: HackerNewsApi by lazy { graph.hackerNewsApi }

    val hackerNewsRepository: HackerNewsRepository by lazy {
        graph.hackerNewsRepository
    }

    val pollOptionsRepository: PollOptionsRepository by lazy {
        graph.pollOptionsRepository
    }

    val replyScanner: ReplyScanner by lazy {
        graph.replyScanner
    }

    val algoliaRepository: AlgoliaRepository by lazy {
        graph.algoliaRepository
    }

    val linkPreviewRepository: LinkPreviewRepository by lazy {
        graph.linkPreviewRepository
    }

    val linkSummaryRepository: LinkSummaryRepository by lazy {
        graph.linkSummaryRepository
    }

    val previewContentCoordinator: PreviewContentCoordinator by lazy {
        graph.previewContentCoordinator
    }

    val cloudSummaryRepository: CloudSummaryRepository by lazy {
        graph.cloudSummaryRepository
    }

    val summaryUseCase: SummaryUseCase by lazy {
        graph.summaryUseCase
    }

    val aiModelCatalogRepository: AiModelCatalogRepository by lazy {
        graph.aiModelCatalogRepository
    }

    val openRouterProviderIconRepository: OpenRouterProviderIconRepository by lazy {
        graph.openRouterProviderIconRepository
    }

    val hackerNewsWebRepository: HackerNewsWebRepository by lazy {
        graph.hackerNewsWebRepository
    }

    val hackerNewsSession: HackerNewsAuthenticatedSession by lazy { graph.hackerNewsSession }

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

    private var requestQueueInstance: RequestQueue? = null

    val httpClientInstanceWithCookies: KtorHttpClient
        get() = graph.httpClientWithCookies

    val authenticatedHackerNewsWebRepository: HackerNewsWebRepository
        get() = graph.authenticatedHackerNewsWebRepository

    val hackerNewsActionRepository: HackerNewsActionRepository
        get() = graph.hackerNewsActionRepository

    fun resetHttpClientCookieInstance() {
        authenticatedClientProvider.reset()
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
                client = KtorHttpClient(queueClient),
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
