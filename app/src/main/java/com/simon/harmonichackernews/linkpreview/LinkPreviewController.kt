package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.webkit.WebView
import com.simon.harmonichackernews.data.NitterInfo
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.LinkPreviewData
import com.simon.harmonichackernews.network.LinkPreviewPreferences
import com.simon.harmonichackernews.network.LinkPreviewRuntime
import com.simon.harmonichackernews.network.LinkPreviewRuntimeState
import com.simon.harmonichackernews.network.LinkPreviewUseCase
import com.simon.harmonichackernews.network.NitterLinkPreviewPreferences
import com.simon.harmonichackernews.network.NitterLinkPreviewRuntime
import com.simon.harmonichackernews.network.NitterLinkPreviewState
import com.simon.harmonichackernews.network.WebPageExtractor
import com.simon.harmonichackernews.settings.ReadingPreferences
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class LinkPreviewController(
    private val story: Story?,
    useCase: LinkPreviewUseCase,
    private val readingPreferences: ReadingPreferences,
    private val callbacks: Callbacks,
) {
    fun interface Callbacks {
        fun onPreviewChanged()
    }

    private val previewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val previewRuntime = LinkPreviewRuntime(
        previewScope,
        useCase,
    )
    private val nitterPreviewRuntime = NitterLinkPreviewRuntime(previewScope)
    private var networkPreviewState = LinkPreviewRuntimeState()
    private var nitterPreviewState = NitterLinkPreviewState()

    init {
        previewScope.launch { previewRuntime.state.collect(::applyNetworkPreviewState) }
        previewScope.launch { nitterPreviewRuntime.state.collect(::applyNitterPreviewState) }
    }

    fun loadNetworkPreviews(context: Context?) {
        val currentStory = story
        val url = currentStory?.url
        if (context == null || currentStory == null || url == null || currentStory.linkPreviewLoading || currentStory.hasLoadedLinkPreview()) {
            return
        }

        previewRuntime.load(
            url,
            LinkPreviewPreferences(
                arxiv = readingPreferences.previewArxiv,
                github = readingPreferences.previewGithub,
                gitLab = readingPreferences.previewGitlab,
                stackExchange = readingPreferences.previewStackExchange,
                wikipedia = readingPreferences.previewWikipedia,
            ),
            alreadyLoaded = currentStory.hasLoadedLinkPreview(),
        )
    }

    private fun applyNetworkPreviewState(state: LinkPreviewRuntimeState) {
        val currentStory = story ?: return
        networkPreviewState = state
        var previewChanged = false
        when (val preview = state.preview) {
            is LinkPreviewData.Arxiv -> {
                currentStory.arxivInfo = preview.value
                previewChanged = true
            }
            is LinkPreviewData.GitHub -> {
                currentStory.repoInfo = preview.value
                previewChanged = true
            }
            is LinkPreviewData.GitLab -> {
                currentStory.gitLabInfo = preview.value
                previewChanged = true
            }
            is LinkPreviewData.StackExchange -> {
                currentStory.stackExchangeInfo = preview.value
                previewChanged = true
            }
            is LinkPreviewData.Wikipedia -> {
                currentStory.wikiInfo = preview.value
                previewChanged = true
            }
            null -> Unit
        }
        syncPreviewLoading(previewChanged)
    }

    private fun applyNitterPreviewState(state: NitterLinkPreviewState) {
        val currentStory = story ?: return
        nitterPreviewState = state
        val previewChanged = state.preview != null && currentStory.nitterInfo !== state.preview
        state.preview?.let { currentStory.nitterInfo = it }
        syncPreviewLoading(previewChanged)
    }

    private fun syncPreviewLoading(previewChanged: Boolean) {
        val currentStory = story ?: return
        val loading = networkPreviewState.loading || nitterPreviewState.loading
        if (currentStory.linkPreviewLoading != loading || previewChanged) {
            currentStory.linkPreviewLoading = loading
            callbacks.onPreviewChanged()
        }
    }

    fun dispose() {
        cancelPendingNitterLinkPreviewRead()
        previewRuntime.dispose()
        nitterPreviewRuntime.dispose()
        previewScope.cancel()
    }

    fun shouldInitializeWebViewForPreview(context: Context?): Boolean {
        if (context == null) return false
        return nitterPreviewRuntime.shouldInitializeWebPage(
            story?.url,
            nitterPreferences(),
        )
    }

    fun prepareWebViewLoad(context: Context?, webView: WebView?, url: String): String {
        if (context == null || webView == null) {
            cancelPendingNitterLinkPreviewRead()
            return url
        }
        return nitterPreviewRuntime.prepareLoad(
            requestedUrl = url,
            preferences = nitterPreferences(),
            alreadyLoaded = story?.nitterInfo != null,
            extractor = AndroidNitterWebPageExtractor(webView, context),
        )
    }

    fun onWebViewPageFinished(context: Context?, view: WebView, url: String?) {
        if (context == null) return
        nitterPreviewRuntime.onPageFinished(
            loadedUrl = url,
            preferences = nitterPreferences(),
            alreadyLoaded = story?.nitterInfo != null,
            extractor = AndroidNitterWebPageExtractor(view, context),
        )
    }

    fun onWebViewOfflineFallback(context: Context?) {
        if (context != null) nitterPreviewRuntime.offlineFallback()
    }

    fun cancelPendingNitterLinkPreviewRead() {
        nitterPreviewRuntime.cancel()
    }

    private fun nitterPreferences(): NitterLinkPreviewPreferences {
        return NitterLinkPreviewPreferences(
            previewEnabled = readingPreferences.previewX,
            redirectEnabled = readingPreferences.redirectNitter,
        )
    }

    private class AndroidNitterWebPageExtractor(
        private val webView: WebView,
        private val context: Context,
    ) : WebPageExtractor<NitterInfo> {
        override val currentUrl: String?
            get() = webView.url

        override suspend fun extract(): NitterInfo? = suspendCancellableCoroutine { continuation ->
            NitterGetter.getInfo(webView, context, object : NitterGetter.GetterCallback {
                override fun onSuccess(nitterInfo: NitterInfo?) {
                    if (continuation.isActive) continuation.resume(nitterInfo)
                }

                override fun onFailure(reason: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IllegalStateException(reason ?: "Nitter extraction failed"),
                        )
                    }
                }
            })
        }
    }
}
