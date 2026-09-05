package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

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
 * Simple resettable provider for platform shells that do not need extra synchronization.
 */
class ResettableAuthenticatedHttpClientProvider(
    private val factory: () -> HttpClient,
) : AuthenticatedHttpClientProvider {
    private var activeClient: HttpClient? = null

    override fun get(): HttpClient = activeClient ?: factory().also { activeClient = it }

    override fun reset() {
        activeClient?.close()
        activeClient = null
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
    private val scope: CoroutineScope,
    private val authenticatedClientProvider: AuthenticatedHttpClientProvider,
    val userAgent: String = "Harmonic-HN",
    private val cacheMaintenance: NetworkCacheMaintenance = NetworkCacheMaintenance.None,
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
    private val client: suspend () -> HttpClient = transport::await
    val httpClient: KtorHttpClient = KtorHttpClient(client)

    val hackerNewsApi: HackerNewsApi = KtorHackerNewsApi(client)
    val hackerNewsRepository: HackerNewsRepository = DefaultHackerNewsRepository(hackerNewsApi)
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
        get() = KtorHttpClient(authenticatedClientProvider.get())

    val authenticatedHackerNewsWebRepository: HackerNewsWebRepository
        get() = KtorHackerNewsWebRepository(authenticatedClientProvider.get())

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
    }
}
