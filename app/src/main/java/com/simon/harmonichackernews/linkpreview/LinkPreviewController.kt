package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.simon.harmonichackernews.data.NitterInfo
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.LinkPreviewData
import com.simon.harmonichackernews.network.LinkPreviewPreferences
import com.simon.harmonichackernews.network.LinkPreviewUseCase
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.NitterPreview
import com.simon.harmonichackernews.settings.AndroidUserSettings
import kotlin.math.min

class LinkPreviewController(private val story: Story?, private val callbacks: Callbacks) {
    fun interface Callbacks {
        fun onPreviewChanged()
    }

    private val nitterLinkPreviewHandler = Handler(Looper.getMainLooper())
    private var nitterLinkPreviewGeneration = 0
    private var activeNitterPreviewWebView: WebView? = null

    fun loadNetworkPreviews(context: Context?) {
        val currentStory = story
        val url = currentStory?.url
        if (context == null || currentStory == null || url == null || currentStory.linkPreviewLoading || currentStory.hasLoadedLinkPreview()) {
            return
        }

        val useCase = LinkPreviewUseCase(NetworkComponent.linkPreviewRepository)
        val preferences = AndroidUserSettings.get(context).reading
        val provider = useCase.selectProvider(
            url,
            LinkPreviewPreferences(
                arxiv = preferences.previewArxiv,
                github = preferences.previewGithub,
                gitLab = preferences.previewGitlab,
                stackExchange = preferences.previewStackExchange,
                wikipedia = preferences.previewWikipedia,
            ),
        ) ?: return

        setLinkPreviewLoading(true)
        NetworkComponent.launchCallbackRequest(
            request = { useCase.load(provider, url) },
            onSuccess = { preview ->
                when (preview) {
                    is LinkPreviewData.Arxiv -> currentStory.arxivInfo = preview.value
                    is LinkPreviewData.GitHub -> currentStory.repoInfo = preview.value
                    is LinkPreviewData.GitLab -> currentStory.gitLabInfo = preview.value
                    is LinkPreviewData.StackExchange -> currentStory.stackExchangeInfo = preview.value
                    is LinkPreviewData.Wikipedia -> currentStory.wikiInfo = preview.value
                }
                setLinkPreviewLoading(false)
            },
            onFailure = { setLinkPreviewLoading(false) },
        )
    }

    fun shouldInitializeWebViewForPreview(context: Context?): Boolean {
        return context != null && story != null && NitterPreview.isConvertibleUrl(story.url)
                && AndroidUserSettings.get(context).reading.previewX
    }

    fun prepareWebViewLoad(context: Context?, webView: WebView?, url: String): String {
        var url = url
        cancelPendingNitterLinkPreviewRead()
        if (context == null || webView == null) {
            return url
        }

        if (NitterPreview.isConvertibleUrl(url) && AndroidUserSettings.get(context).reading.redirectNitter) {
            url = NitterPreview.convertUrl(url)
        }

        activeNitterPreviewWebView = webView
        val loadingNitterPreview =
            story != null && story.nitterInfo == null && shouldLoadNitterLinkPreview(context, url)
        if (loadingNitterPreview) {
            setLinkPreviewLoading(true)
            scheduleNitterLinkPreviewPageLoadTimeout(webView, url, nitterLinkPreviewGeneration)
        } else if (story != null && story.linkPreviewLoading
            && story.nitterInfo == null && !shouldLoadNitterLinkPreview(
                context,
                url
            ) && shouldLoadNitterLinkPreview(context, story.url)
        ) {
            setLinkPreviewLoading(false)
        }

        return url
    }

    fun onWebViewPageFinished(context: Context?, view: WebView, url: String?) {
        if (context != null && shouldReadNitterLinkPreview(context, url)) {
            readNitterLinkPreviewWithRetry(context, view, url)
        }
    }

    fun onWebViewOfflineFallback(context: Context?) {
        if (context != null && story != null && story.linkPreviewLoading
            && shouldLoadNitterLinkPreview(context, story.url)
        ) {
            setLinkPreviewLoading(false)
        }
    }

    fun cancelPendingNitterLinkPreviewRead() {
        nitterLinkPreviewGeneration++
        activeNitterPreviewWebView = null
        nitterLinkPreviewHandler.removeCallbacksAndMessages(null)
    }

    private fun shouldLoadNitterLinkPreview(context: Context?, url: String?): Boolean {
        return shouldReadNitterLinkPreview(context, url)
                || (NitterPreview.isConvertibleUrl(url)
                && context != null
                && AndroidUserSettings.get(context).reading.redirectNitter
                && AndroidUserSettings.get(context).reading.previewX)
    }

    private fun shouldReadNitterLinkPreview(context: Context?, url: String?): Boolean {
        return NitterPreview.isNitterUrl(url)
                && context != null
                && AndroidUserSettings.get(context).reading.previewX
    }

    private fun scheduleNitterLinkPreviewPageLoadTimeout(
        view: WebView,
        url: String?,
        generation: Int
    ) {
        nitterLinkPreviewHandler.postDelayed(readTimeout@ Runnable {
            if (isCurrentNitterLinkPreviewRead(view, url, generation)) {
                readNitterLinkPreviewAttempt(view.getContext(), view, url, generation, 0)
            }
        }, NITTER_LINK_PREVIEW_PAGE_LOAD_TIMEOUT_MS)
    }

    private fun readNitterLinkPreviewWithRetry(context: Context?, view: WebView, url: String?) {
        if (story == null || story.nitterInfo != null) {
            return
        }
        cancelPendingNitterLinkPreviewRead()
        activeNitterPreviewWebView = view
        setLinkPreviewLoading(true)
        readNitterLinkPreviewAttempt(context, view, url, nitterLinkPreviewGeneration, 0)
    }

    private fun readNitterLinkPreviewAttempt(
        context: Context?,
        view: WebView,
        url: String?,
        generation: Int,
        attempt: Int
    ) {
        if (context == null || !isCurrentNitterLinkPreviewRead(view, url, generation)) {
            return
        }
        if (!isWebViewAtNitterLinkPreviewUrl(view, url)) {
            onNitterLinkPreviewReadFailed(context, view, url, generation, attempt)
            return
        }

        val finished = booleanArrayOf(false)
        nitterLinkPreviewHandler.postDelayed(Runnable readTimeout@ {
            if (finished[0] || !isCurrentNitterLinkPreviewRead(view, url, generation)) {
                return@readTimeout
            }
            finished[0] = true
            onNitterLinkPreviewReadFailed(context, view, url, generation, attempt)
        }, NITTER_LINK_PREVIEW_HTML_READ_TIMEOUT_MS)

        NitterGetter.getInfo(view, context, object : NitterGetter.GetterCallback {
            override fun onSuccess(nitterInfo: NitterInfo?) {
                if (finished[0] || !isCurrentNitterLinkPreviewRead(view, url, generation)) {
                    return
                }
                finished[0] = true
                story!!.nitterInfo = nitterInfo
                setLinkPreviewLoading(false)
            }

            override fun onFailure(reason: String?) {
                if (finished[0] || !isCurrentNitterLinkPreviewRead(view, url, generation)) {
                    return
                }
                finished[0] = true
                onNitterLinkPreviewReadFailed(context, view, url, generation, attempt)
            }
        })
    }

    private fun onNitterLinkPreviewReadFailed(
        context: Context?,
        view: WebView,
        url: String?,
        generation: Int,
        attempt: Int
    ) {
        val nextAttempt = attempt + 1
        if (nextAttempt < NITTER_LINK_PREVIEW_MAX_ATTEMPTS) {
            val delay: Long = NITTER_LINK_PREVIEW_RETRY_DELAYS_MS[min(
                attempt,
                NITTER_LINK_PREVIEW_RETRY_DELAYS_MS.size - 1
            )]
            nitterLinkPreviewHandler.postDelayed(
                Runnable {
                    readNitterLinkPreviewAttempt(
                        context,
                        view,
                        url,
                        generation,
                        nextAttempt
                    )
                },
                delay
            )
            return
        }

        if (isCurrentNitterLinkPreviewRead(view, url, generation)) {
            setLinkPreviewLoading(false)
        }
    }

    private fun isCurrentNitterLinkPreviewRead(
        view: WebView?,
        url: String?,
        generation: Int
    ): Boolean {
        return generation == nitterLinkPreviewGeneration && view != null && view === activeNitterPreviewWebView && story != null && story.nitterInfo == null && NitterPreview.isNitterUrl(
            url
        )
    }

    private fun isWebViewAtNitterLinkPreviewUrl(view: WebView, expectedUrl: String?): Boolean {
        val currentUrl = view.url
        if (currentUrl.isNullOrEmpty() || !NitterPreview.isNitterUrl(currentUrl)) {
            return false
        }
        return NitterPreview.isSamePage(currentUrl, expectedUrl)
    }

    private fun setLinkPreviewLoading(loading: Boolean) {
        if (story == null) {
            return
        }
        story.linkPreviewLoading = loading
        callbacks.onPreviewChanged()
    }

    companion object {
        private const val NITTER_LINK_PREVIEW_MAX_ATTEMPTS = 4
        private const val NITTER_LINK_PREVIEW_PAGE_LOAD_TIMEOUT_MS: Long = 6000
        private const val NITTER_LINK_PREVIEW_HTML_READ_TIMEOUT_MS: Long = 2500
        private val NITTER_LINK_PREVIEW_RETRY_DELAYS_MS = longArrayOf(500, 1500, 3000)
    }
}
