package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.webkit.WebView
import androidx.annotation.Nullable
import com.simon.harmonichackernews.data.ArxivInfo
import com.simon.harmonichackernews.data.GitLabInfo
import com.simon.harmonichackernews.data.NitterInfo
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.data.StackExchangeInfo
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.WikipediaInfo
import com.simon.harmonichackernews.linkpreview.GitLabInfoGetter.getInfo
import com.simon.harmonichackernews.linkpreview.GitLabInfoGetter.isValidGitLabUrl
import com.simon.harmonichackernews.utils.SettingsUtils
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

        if (ArxivAbstractGetter.isValidArxivUrl(url) && SettingsUtils.shouldUseLinkPreviewArxiv(
                context
            )
        ) {
            setLinkPreviewLoading(true)
            ArxivAbstractGetter.getAbstract(
                url,
                context,
                object : ArxivAbstractGetter.GetterCallback {
                    override fun onSuccess(arxivInfo: ArxivInfo) {
                        currentStory.arxivInfo = arxivInfo
                        setLinkPreviewLoading(false)
                    }

                    override fun onFailure(reason: String) {
                        setLinkPreviewLoading(false)
                    }
                })
        } else if (GitHubInfoGetter.isValidGitHubUrl(url) && SettingsUtils.shouldUseLinkPreviewGithub(
                context
            )
        ) {
            setLinkPreviewLoading(true)
            GitHubInfoGetter.getInfo(
                url,
                context,
                object : GitHubInfoGetter.GetterCallback {
                    override fun onSuccess(repoInfo: RepoInfo) {
                        currentStory.repoInfo = repoInfo
                        setLinkPreviewLoading(false)
                    }

                    override fun onFailure(reason: String) {
                        setLinkPreviewLoading(false)
                    }
                })
        } else if (isValidGitLabUrl(url) && SettingsUtils.shouldUseLinkPreviewGitLab(context)) {
            setLinkPreviewLoading(true)
            getInfo(url, context, object : GitLabInfoGetter.GetterCallback {
                override fun onSuccess(gitLabInfo: GitLabInfo) {
                    currentStory.gitLabInfo = gitLabInfo
                    setLinkPreviewLoading(false)
                }

                override fun onFailure(reason: String) {
                    setLinkPreviewLoading(false)
                }
            })
        } else if (StackExchangeGetter.isValidStackExchangeUrl(url) && SettingsUtils.shouldUseLinkPreviewStackExchange(
                context
            )
        ) {
            setLinkPreviewLoading(true)
            StackExchangeGetter.getInfo(
                url,
                context,
                object : StackExchangeGetter.GetterCallback {
                    override fun onSuccess(stackExchangeInfo: StackExchangeInfo) {
                        currentStory.stackExchangeInfo = stackExchangeInfo
                        setLinkPreviewLoading(false)
                    }

                    override fun onFailure(reason: String) {
                        setLinkPreviewLoading(false)
                    }
                })
        } else if (WikipediaGetter.isValidWikipediaUrl(url) && SettingsUtils.shouldUseLinkPreviewWikipedia(
                context
            )
        ) {
            setLinkPreviewLoading(true)
            WikipediaGetter.getInfo(url, context, object : WikipediaGetter.GetterCallback {
                override fun onSuccess(wikipediaInfo: WikipediaInfo) {
                    currentStory.wikiInfo = wikipediaInfo
                    setLinkPreviewLoading(false)
                }

                override fun onFailure(reason: String) {
                    setLinkPreviewLoading(false)
                }
            })
        }
    }

    fun shouldInitializeWebViewForPreview(context: Context?): Boolean {
        return context != null && story != null && NitterGetter.isConvertibleToNitter(story.url)
                && SettingsUtils.shouldUseLinkPreviewX(context)
    }

    fun prepareWebViewLoad(context: Context?, webView: WebView?, url: String): String {
        var url = url
        cancelPendingNitterLinkPreviewRead()
        if (context == null || webView == null) {
            return url
        }

        if (NitterGetter.isConvertibleToNitter(url) && SettingsUtils.shouldRedirectNitter(context)) {
            url = NitterGetter.convertToNitterUrl(url)
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
                || (NitterGetter.isConvertibleToNitter(url)
                && context != null
                && SettingsUtils.shouldRedirectNitter(context)
                && SettingsUtils.shouldUseLinkPreviewX(context))
    }

    private fun shouldReadNitterLinkPreview(context: Context?, url: String?): Boolean {
        return NitterGetter.isValidNitterUrl(url)
                && context != null
                && SettingsUtils.shouldUseLinkPreviewX(context)
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
        return generation == nitterLinkPreviewGeneration && view != null && view === activeNitterPreviewWebView && story != null && story.nitterInfo == null && NitterGetter.isValidNitterUrl(
            url
        )
    }

    private fun isWebViewAtNitterLinkPreviewUrl(view: WebView, expectedUrl: String?): Boolean {
        val currentUrl = view.getUrl()
        if (TextUtils.isEmpty(currentUrl) || !NitterGetter.isValidNitterUrl(currentUrl)) {
            return false
        }

        val expectedStatusId = getNitterStatusId(expectedUrl)
        val currentStatusId = getNitterStatusId(currentUrl)
        if (!TextUtils.isEmpty(expectedStatusId) && !TextUtils.isEmpty(currentStatusId)) {
            return expectedStatusId == currentStatusId
        }

        return areSameNitterPage(currentUrl, expectedUrl)
    }

    private fun getNitterStatusId(url: String?): String? {
        try {
            val segments = Uri.parse(url).getPathSegments()
            for (i in 0..<segments.size - 1) {
                if ("status" == segments.get(i)) {
                    return segments.get(i + 1)
                }
            }
        } catch (ignored: Exception) {
        }
        return null
    }

    private fun areSameNitterPage(firstUrl: String?, secondUrl: String?): Boolean {
        try {
            val first = Uri.parse(firstUrl)
            val second = Uri.parse(secondUrl)
            return TextUtils.equals(normalizeHost(first.getHost()), normalizeHost(second.getHost()))
                    && TextUtils.equals(
                trimTrailingSlash(first.getPath()),
                trimTrailingSlash(second.getPath())
            )
        } catch (ignored: Exception) {
            return TextUtils.equals(firstUrl, secondUrl)
        }
    }

    private fun normalizeHost(host: String?): String {
        var host = host
        if (host == null) {
            return ""
        }
        host = host.lowercase()
        return if (host.startsWith("www.")) host.substring(4) else host
    }

    private fun trimTrailingSlash(path: String?): String {
        var path = path
        if (TextUtils.isEmpty(path) || "/" == path) {
            return ""
        }
        while (path!!.endsWith("/")) {
            path = path.substring(0, path.length - 1)
        }
        return path
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
