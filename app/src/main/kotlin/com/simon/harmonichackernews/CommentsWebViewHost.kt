package com.simon.harmonichackernews

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.insets.ProtectionLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
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
    val downloadButton: MaterialButton
    val progressIndicator: LinearProgressIndicator

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

        downloadButton = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialIconButtonOutlinedStyle
        ).apply {
            id = R.id.webview_download
            text = context.getString(R.string.download_file)
            setIconResource(R.drawable.ic_file_download)
            setTextColor(
                MaterialColors.getColor(
                    this,
                    R.attr.storyColorNormal
                )
            )
            visibility = View.GONE
        }
        val downloadParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        webViewContainer.addView(downloadButton, downloadParams)

        progressIndicator = LinearProgressIndicator(context).apply {
            id = R.id.webview_progress
            visibility = View.GONE
        }
        webViewContainer.addView(
            progressIndicator, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

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
