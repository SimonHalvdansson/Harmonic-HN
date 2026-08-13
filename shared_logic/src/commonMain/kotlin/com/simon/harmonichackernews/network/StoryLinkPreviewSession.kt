package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.settings.ReadingPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Story-scoped preview orchestration shared by every host. A native web surface is needed only for
 * the optional Nitter DOM extraction and is supplied through [WebPageExtractor].
 */
class StoryLinkPreviewSession(
    scope: CoroutineScope,
    private val story: Story?,
    useCase: LinkPreviewUseCase,
    private val readingPreferences: ReadingPreferences,
    private val onPreviewChanged: () -> Unit,
) {
    private val network = LinkPreviewRuntime(scope, useCase)
    private val nitter = NitterLinkPreviewRuntime(scope)
    private var networkState = LinkPreviewRuntimeState()
    private var nitterState = NitterLinkPreviewState()

    init {
        scope.launch { network.state.collect(::applyNetworkState) }
        scope.launch { nitter.state.collect(::applyNitterState) }
    }

    fun loadNetworkPreviews(): Boolean {
        val current = story ?: return false
        return network.load(
            url = current.url,
            preferences = LinkPreviewPreferences(
                arxiv = readingPreferences.previewArxiv,
                github = readingPreferences.previewGithub,
                gitLab = readingPreferences.previewGitlab,
                stackExchange = readingPreferences.previewStackExchange,
                wikipedia = readingPreferences.previewWikipedia,
            ),
            alreadyLoaded = current.hasLoadedLinkPreview(),
        )
    }

    fun shouldInitializeWebPage(): Boolean =
        nitter.shouldInitializeWebPage(story?.url, nitterPreferences())

    fun prepareLoad(url: String, extractor: WebPageExtractor<com.simon.harmonichackernews.data.NitterInfo>?): String {
        if (extractor == null) {
            nitter.cancel()
            return url
        }
        return nitter.prepareLoad(
            requestedUrl = url,
            preferences = nitterPreferences(),
            alreadyLoaded = story?.nitterInfo != null,
            extractor = extractor,
        )
    }

    fun onPageFinished(
        url: String?,
        extractor: WebPageExtractor<com.simon.harmonichackernews.data.NitterInfo>,
    ) {
        nitter.onPageFinished(
            loadedUrl = url,
            preferences = nitterPreferences(),
            alreadyLoaded = story?.nitterInfo != null,
            extractor = extractor,
        )
    }

    fun offlineFallback() = nitter.offlineFallback()

    fun cancelNitterRead() = nitter.cancel()

    fun dispose() {
        network.dispose()
        nitter.dispose()
    }

    private fun applyNetworkState(state: LinkPreviewRuntimeState) {
        val current = story ?: return
        networkState = state
        var changed = false
        when (val preview = state.preview) {
            is LinkPreviewData.Arxiv -> { current.arxivInfo = preview.value; changed = true }
            is LinkPreviewData.GitHub -> { current.repoInfo = preview.value; changed = true }
            is LinkPreviewData.GitLab -> { current.gitLabInfo = preview.value; changed = true }
            is LinkPreviewData.StackExchange -> { current.stackExchangeInfo = preview.value; changed = true }
            is LinkPreviewData.Wikipedia -> { current.wikiInfo = preview.value; changed = true }
            null -> Unit
        }
        syncLoading(changed)
    }

    private fun applyNitterState(state: NitterLinkPreviewState) {
        val current = story ?: return
        nitterState = state
        val changed = state.preview != null && current.nitterInfo !== state.preview
        state.preview?.let { current.nitterInfo = it }
        syncLoading(changed)
    }

    private fun syncLoading(changed: Boolean) {
        val current = story ?: return
        val loading = networkState.loading || nitterState.loading
        if (current.linkPreviewLoading != loading || changed) {
            current.linkPreviewLoading = loading
            onPreviewChanged()
        }
    }

    private fun nitterPreferences() = NitterLinkPreviewPreferences(
        previewEnabled = readingPreferences.previewX,
        redirectEnabled = readingPreferences.redirectNitter,
    )
}
