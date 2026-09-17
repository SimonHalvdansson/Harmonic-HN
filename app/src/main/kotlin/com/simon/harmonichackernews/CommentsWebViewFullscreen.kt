package com.simon.harmonichackernews

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient.CustomViewCallback
import android.widget.FrameLayout

/** Owns WebChromeClient's fullscreen view and restores the browser when it closes. */
internal class CommentsWebViewFullscreen(
    private val onFullscreenChanged: (Boolean) -> Unit,
) {
    private var webViewContainer: FrameLayout? = null
    private var fullscreenContainer: FrameLayout? = null
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    val isShowing: Boolean get() = customView != null

    fun bind(webViewContainer: FrameLayout, fullscreenContainer: FrameLayout) {
        release()
        this.webViewContainer = webViewContainer
        this.fullscreenContainer = fullscreenContainer
    }

    fun show(view: View, callback: CustomViewCallback) {
        val browser = webViewContainer
        val fullscreen = fullscreenContainer
        if (browser == null || fullscreen == null) {
            callback.onCustomViewHidden()
            return
        }
        hide(notifyCallback = true)
        customView = view
        customViewCallback = callback

        (view.parent as? ViewGroup)?.removeView(view)
        fullscreen.removeAllViews()
        fullscreen.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        fullscreen.visibility = View.VISIBLE
        browser.visibility = View.GONE
        onFullscreenChanged(true)
    }

    fun hide(notifyCallback: Boolean) {
        val view = customView ?: return
        val callback = customViewCallback
        customView = null
        customViewCallback = null

        (view.parent as? ViewGroup)?.removeView(view)
        fullscreenContainer?.apply {
            removeAllViews()
            visibility = View.GONE
        }
        webViewContainer?.visibility = View.VISIBLE
        onFullscreenChanged(false)
        if (notifyCallback) callback?.onCustomViewHidden()
    }

    fun release() {
        hide(notifyCallback = false)
        webViewContainer = null
        fullscreenContainer = null
    }
}
