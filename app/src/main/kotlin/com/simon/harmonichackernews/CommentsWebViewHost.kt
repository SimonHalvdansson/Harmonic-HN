package com.simon.harmonichackernews

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.insets.ProtectionLayout
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.ui.comments.WebContentOverlay
import com.simon.harmonichackernews.ui.comments.WebContentOverlayState
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.utils.AndroidDisplay

/**
 * The small View island needed by Android WebView. All visible comments UI, including the
 * integrated-browser sheet and its controls, is rendered by Compose above this host.
 */
internal class CommentsWebViewHost(context: Context) {
    val root: ProtectionLayout
    val webViewContainer: FrameLayout
    val fullscreenContainer: FrameLayout
    val webViewBackdrop: View
    val overlayState = WebContentOverlayState()

    init {
        root = ProtectionLayout(context).apply {
            id = R.id.list_protection
            layoutParams = matchParentParams()
        }

        val content = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }
        root.addView(content, matchParentParams())

        webViewContainer = FrameLayout(context).apply {
            id = R.id.webview_container
        }
        val webViewContainerParams = matchParentFrameParams().apply {
            bottomMargin = AndroidDisplay.dpToPxInt(context.resources, 68f)
        }
        content.addView(webViewContainer, webViewContainerParams)

        webViewBackdrop = View(context).apply {
            id = R.id.comments_webview_backdrop
            alpha = 0f
            setBackgroundColor(Color.WHITE)
        }
        webViewContainer.addView(webViewBackdrop, matchParentFrameParams())

        val overlay = ComposeView(context).apply {
            // Preloaded browsers can be measured offscreen before a window supplies Compose's
            // lifecycle/recomposer. Only measure the overlay while attached to that window.
            visibility = View.GONE
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) { view.visibility = View.VISIBLE }
                override fun onViewDetachedFromWindow(view: View) { view.visibility = View.GONE }
            })
            setContent {
                val appearance = context.harmonicAppComposition.appearance
                val selection by appearance.selections.collectAsStateWithLifecycle(
                    initialValue = appearance.selection(),
                )
                HarmonicTheme(selection) { WebContentOverlay(overlayState) }
            }
        }
        webViewContainer.addView(overlay, matchParentFrameParams())

        fullscreenContainer = FrameLayout(context).apply {
            id = R.id.comments_fullscreen_container
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        content.addView(fullscreenContainer, matchParentFrameParams())
    }

    companion object {
        private fun matchParentParams(): ViewGroup.LayoutParams {
            return ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        private fun matchParentFrameParams(): FrameLayout.LayoutParams {
            return FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
}
