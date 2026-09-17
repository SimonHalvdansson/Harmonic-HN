package com.simon.harmonichackernews.linkpreview

import android.content.Context
import android.webkit.WebView
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.createStoryLinkPreviewSession
import com.simon.harmonichackernews.data.NitterInfo
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.WebPageExtractor
import com.simon.harmonichackernews.settings.ReadingPreferences
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine

/** Android WebView adapter for the shared story link-preview session. */
class LinkPreviewController(
    story: Story?,
    appComposition: HarmonicAppComposition,
    readingPreferences: ReadingPreferences,
    callbacks: Callbacks,
) {
    fun interface Callbacks {
        fun onPreviewChanged()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val session = appComposition.createStoryLinkPreviewSession(
        scope = scope,
        story = story,
        readingPreferences = readingPreferences,
        onPreviewChanged = callbacks::onPreviewChanged,
    )

    fun loadNetworkPreviews(context: Context?) {
        if (context != null) session.loadNetworkPreviews()
    }

    fun shouldInitializeWebViewForPreview(context: Context?): Boolean =
        context != null && session.shouldInitializeWebPage()

    fun prepareWebViewLoad(context: Context?, webView: WebView?, url: String): String =
        session.prepareLoad(
            url = url,
            extractor = if (context != null && webView != null) AndroidExtractor(webView) else null,
        )

    fun onWebViewPageFinished(context: Context?, view: WebView, url: String?) {
        if (context != null) session.onPageFinished(url, AndroidExtractor(view))
    }

    fun onWebViewOfflineFallback(context: Context?) {
        if (context != null) session.offlineFallback()
    }

    fun cancelPendingNitterLinkPreviewRead() = session.cancelNitterRead()

    fun dispose() {
        session.dispose()
        scope.cancel()
    }

    private class AndroidExtractor(
        private val webView: WebView,
    ) : WebPageExtractor<NitterInfo> {
        override val currentUrl: String?
            get() = webView.url

        override suspend fun extract(): NitterInfo? = suspendCancellableCoroutine { continuation ->
            NitterGetter.getInfo(webView, object : NitterGetter.GetterCallback {
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
