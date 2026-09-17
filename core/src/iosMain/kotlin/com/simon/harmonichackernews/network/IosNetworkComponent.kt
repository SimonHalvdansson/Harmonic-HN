package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** iOS composition root for shared networking. The iOS app owns this instance's lifecycle. */
class IosNetworkComponent(
    userAgent: String,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val graph = NetworkGraphFactory.create(NetworkGraphEnvironment(
        scope = scope,
        userAgent = userAgent,
        engine = { Darwin.create() },
    ))

    val httpClient: HttpClient get() = graph.transportClient
    val hackerNewsApi: HackerNewsApi get() = graph.hackerNewsApi
    val hackerNewsRepository: HackerNewsRepository get() = graph.hackerNewsRepository
    val pollOptionsRepository: PollOptionsRepository get() = graph.pollOptionsRepository
    val replyScanner: ReplyScanner get() = graph.replyScanner
    val algoliaRepository: AlgoliaRepository get() = graph.algoliaRepository
    val linkPreviewRepository: LinkPreviewRepository get() = graph.linkPreviewRepository
    val linkSummaryRepository: LinkSummaryRepository get() = graph.linkSummaryRepository
    val previewContentCoordinator: PreviewContentCoordinator get() = graph.previewContentCoordinator
    val cloudSummaryRepository: CloudSummaryRepository get() = graph.cloudSummaryRepository
    val summaryUseCase: SummaryUseCase get() = graph.summaryUseCase
    val aiModelCatalogRepository: AiModelCatalogRepository get() = graph.aiModelCatalogRepository
    val openRouterProviderIconRepository: OpenRouterProviderIconRepository
        get() = graph.openRouterProviderIconRepository
    val hackerNewsWebRepository: HackerNewsWebRepository get() = graph.hackerNewsWebRepository
    val httpClientWithCookies: KtorHttpClient get() = graph.httpClientWithCookies
    val authenticatedHackerNewsWebRepository: HackerNewsWebRepository
        get() = graph.authenticatedHackerNewsWebRepository
    val hackerNewsActionRepository: HackerNewsActionRepository
        get() = graph.hackerNewsActionRepository
    val hackerNewsSession: HackerNewsAuthenticatedSession get() = graph.hackerNewsSession

    fun close() {
        graph.close()
        scope.cancel()
    }
}
