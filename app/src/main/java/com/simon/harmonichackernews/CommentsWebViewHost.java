package com.simon.harmonichackernews;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.FrameLayout;

import androidx.core.view.insets.ProtectionLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.simon.harmonichackernews.utils.Utils;

/**
 * The small View island needed by Android WebView. All visible comments UI, including the
 * integrated-browser sheet and its controls, is rendered by Compose above this host.
 */
final class CommentsWebViewHost {

    final ProtectionLayout root;
    final FrameLayout webViewContainer;
    final FrameLayout fullscreenContainer;
    final View webViewBackdrop;
    final MaterialButton downloadButton;
    final ViewStub webViewStub;
    final LinearProgressIndicator progressIndicator;

    CommentsWebViewHost(Context context) {
        root = new ProtectionLayout(context);
        root.setId(R.id.list_protection);
        root.setLayoutParams(matchParentParams());

        FrameLayout content = new FrameLayout(context);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, matchParentParams());

        webViewContainer = new FrameLayout(context);
        webViewContainer.setId(R.id.webview_container);
        FrameLayout.LayoutParams webViewContainerParams = matchParentFrameParams();
        webViewContainerParams.bottomMargin = Utils.pxFromDpInt(context.getResources(), 68);
        content.addView(webViewContainer, webViewContainerParams);

        webViewBackdrop = new View(context);
        webViewBackdrop.setId(R.id.comments_webview_backdrop);
        webViewBackdrop.setAlpha(0f);
        webViewBackdrop.setBackgroundColor(android.graphics.Color.WHITE);
        webViewContainer.addView(webViewBackdrop, matchParentFrameParams());

        downloadButton = new MaterialButton(
                context,
                null,
                com.google.android.material.R.attr.materialIconButtonOutlinedStyle);
        downloadButton.setId(R.id.webview_download);
        downloadButton.setText("Download file");
        downloadButton.setIconResource(R.drawable.ic_file_download);
        downloadButton.setTextColor(MaterialColors.getColor(
                downloadButton,
                R.attr.storyColorNormal));
        downloadButton.setVisibility(View.GONE);
        FrameLayout.LayoutParams downloadParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        webViewContainer.addView(downloadButton, downloadParams);

        webViewStub = new ViewStub(context);
        webViewStub.setId(R.id.comments_webview_stub);
        webViewStub.setInflatedId(R.id.comments_webview_stub_content);
        webViewStub.setLayoutResource(R.layout.comments_webview);
        webViewContainer.addView(webViewStub, matchParentFrameParams());

        progressIndicator = new LinearProgressIndicator(context);
        progressIndicator.setId(R.id.webview_progress);
        progressIndicator.setVisibility(View.GONE);
        webViewContainer.addView(progressIndicator, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        fullscreenContainer = new FrameLayout(context);
        fullscreenContainer.setId(R.id.comments_fullscreen_container);
        fullscreenContainer.setBackgroundColor(android.graphics.Color.BLACK);
        fullscreenContainer.setVisibility(View.GONE);
        content.addView(fullscreenContainer, matchParentFrameParams());
    }

    private static ViewGroup.LayoutParams matchParentParams() {
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static FrameLayout.LayoutParams matchParentFrameParams() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }
}
