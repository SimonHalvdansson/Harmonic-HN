package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.presentation.UserProfileRepository
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.withContext

/**
 * Supplies the cookie-enabled transport used for authenticated Hacker News requests.
 *
 * Platform composition roots own engine creation and any synchronization required around resets.
 * The shared graph owns the repositories built on top of the transport.
 */
interface AuthenticatedHttpClientProvider {
    fun get(): HttpClient
    fun reset()
    fun close() = reset()
}

fun interface NetworkCacheMaintenance {
    fun removeCachedStoryResponses(storyId: Int)

    data object None : NetworkCacheMaintenance {
        override fun removeCachedStoryResponses(storyId: Int) = Unit
    }
}

/**
 * Resettable provider whose lazy construction and disposal are safe across worker threads.
 */
class ResettableAuthenticatedHttpClientProvider(
    private val factory: () -> HttpClient,
) : AuthenticatedHttpClientProvider {
    private fun newTransport() = NetworkTransport(Dispatchers.Default, factory)
    private val transport = MutableStateFlow(newTransport())

    override fun get(): HttpClient = transport.value.get()

    override fun reset() {
        transport.getAndUpdate { newTransport() }.close()
    }
}

/**
 * Platform-neutral networking composition root.
 *
 * Platforms supply configured Ktor transports and a lifecycle scope; all repository and use-case
 * wiring stays shared so Android and iOS expose the same networking surface.
 */
class NetworkGraph internal constructor(
    private val transport: NetworkTransport,
    scope: CoroutineScope,
    private val authenticatedClientProvider: AuthenticatedHttpClientProvider,
    val userAgent: String = "Harmonic-HN",
    private val cacheMaintenance: NetworkCacheMaintenance = NetworkCacheMaintenance.None,
    private val readOnlyTransport: NetworkTransport? = null,
) {
    /** Retains ownership of caller-supplied transports for native and test hosts. */
    constructor(
        transportClient: HttpClient,
        scope: CoroutineScope,
        authenticatedClientProvider: AuthenticatedHttpClientProvider,
        userAgent: String = "Harmonic-HN",
        cacheMaintenance: NetworkCacheMaintenance = NetworkCacheMaintenance.None,
    ) : this(NetworkTransport(transportClient), scope, authenticatedClientProvider, userAgent, cacheMaintenance)

    /** Native compatibility access. Portable repositories await initialization off the UI thread. */
    val transportClient: HttpClient get() = transport.get()
    private val client: suspend () -> HttpClient = (readOnlyTransport ?: transport)::await
    // This surface can issue writes and GET-based actions; never give it read-only retries.
    val httpClient: KtorHttpClient = KtorHttpClient(transport::await)

    val hackerNewsApi: HackerNewsApi = KtorHackerNewsApi(client)
    val userProfiles = UserProfileRepository(
        hackerNewsApi, scope,
    )
    val hackerNewsRepository: HackerNewsRepository = DefaultHackerNewsRepository(hackerNewsApi)
    val unslopRepository: UnslopRepository = UnslopRepository(client)
    val pollOptionsRepository: PollOptionsRepository = PollOptionsRepository(hackerNewsApi)
    val replyScanner: ReplyScanner = DefaultReplyScanner(hackerNewsApi)
    val algoliaRepository: AlgoliaRepository = KtorAlgoliaRepository(client)
    val linkPreviewRepository: LinkPreviewRepository = KtorLinkPreviewRepository(client)
    val linkSummaryRepository: LinkSummaryRepository =
        KtorLinkSummaryRepository(client, linkPreviewRepository)
    val previewContentCoordinator: PreviewContentCoordinator = PreviewContentCoordinator(scope)
    val cloudSummaryRepository: CloudSummaryRepository =
        KtorCloudSummaryRepository(httpClient, userAgent)
    val summaryUseCase: SummaryUseCase = SummaryUseCase(cloudSummaryRepository)
    val aiModelCatalogRepository: AiModelCatalogRepository =
        KtorAiModelCatalogRepository(httpClient)
    val openRouterProviderIconRepository: OpenRouterProviderIconRepository =
        KtorOpenRouterProviderIconRepository(httpClient, scope)
    val hackerNewsWebRepository: HackerNewsWebRepository =
        KtorHackerNewsWebRepository(client)

    val httpClientWithCookies: KtorHttpClient
        get() = KtorHttpClient(client = { withContext(Dispatchers.Default) { authenticatedClientProvider.get() } })

    val authenticatedHackerNewsWebRepository: HackerNewsWebRepository
        get() = KtorHackerNewsWebRepository(client = { withContext(Dispatchers.Default) { authenticatedClientProvider.get() } })

    val hackerNewsActionRepository: HackerNewsActionRepository
        get() = KtorHackerNewsActionRepository(httpClient, httpClientWithCookies)

    val hackerNewsSession: HackerNewsAuthenticatedSession =
        object : HackerNewsAuthenticatedSession {
            override val actions: HackerNewsActionRepository
                get() = hackerNewsActionRepository
            override val authenticatedWeb: HackerNewsWebRepository
                get() = authenticatedHackerNewsWebRepository
            override val publicWeb: HackerNewsWebRepository
                get() = hackerNewsWebRepository

            override fun reset() = authenticatedClientProvider.reset()
        }

    fun removeCachedStoryResponses(storyId: Int) {
        if (storyId > 0) cacheMaintenance.removeCachedStoryResponses(storyId)
    }

    fun close() {
        authenticatedClientProvider.close()
        transport.close()
        readOnlyTransport?.close()
    }
}
