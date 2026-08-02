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
import com.simon.harmonichackernews.utils.Utils

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
        root = ProtectionLayout(context)
        root.setId(R.id.list_protection)
        root.setLayoutParams(matchParentParams())

        val content = FrameLayout(context)
        content.setClipChildren(false)
        content.setClipToPadding(false)
        root.addView(content, matchParentParams())

        webViewContainer = FrameLayout(context)
        webViewContainer.setId(R.id.webview_container)
        val webViewContainerParams: FrameLayout.LayoutParams = matchParentFrameParams()
        webViewContainerParams.bottomMargin = Utils.pxFromDpInt(context.getResources(), 68f)
        content.addView(webViewContainer, webViewContainerParams)

        webViewBackdrop = View(context)
        webViewBackdrop.setId(R.id.comments_webview_backdrop)
        webViewBackdrop.setAlpha(0f)
        webViewBackdrop.setBackgroundColor(Color.WHITE)
        webViewContainer.addView(webViewBackdrop, matchParentFrameParams())

        downloadButton = MaterialButton(
            context,
            null,
            com.google.android.material.R.attr.materialIconButtonOutlinedStyle
        )
        downloadButton.setId(R.id.webview_download)
        downloadButton.setText("Download file")
        downloadButton.setIconResource(R.drawable.ic_file_download)
        downloadButton.setTextColor(
            MaterialColors.getColor(
                downloadButton,
                R.attr.storyColorNormal
            )
        )
        downloadButton.setVisibility(View.GONE)
        val downloadParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        webViewContainer.addView(downloadButton, downloadParams)

        progressIndicator = LinearProgressIndicator(context)
        progressIndicator.setId(R.id.webview_progress)
        progressIndicator.setVisibility(View.GONE)
        webViewContainer.addView(
            progressIndicator, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        fullscreenContainer = FrameLayout(context)
        fullscreenContainer.setId(R.id.comments_fullscreen_container)
        fullscreenContainer.setBackgroundColor(Color.BLACK)
        fullscreenContainer.setVisibility(View.GONE)
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
