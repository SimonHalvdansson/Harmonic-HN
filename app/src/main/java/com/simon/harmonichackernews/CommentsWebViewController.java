package com.simon.harmonichackernews;

import static android.content.Context.DOWNLOAD_SERVICE;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.databinding.CommentsWebviewBinding;
import com.simon.harmonichackernews.databinding.FragmentCommentsBinding;
import com.simon.harmonichackernews.linkpreview.LinkPreviewController;
import com.simon.harmonichackernews.utils.FileDownloader;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import okhttp3.Call;

class CommentsWebViewController {

    private static final String PDF_MIME_TYPE = "application/pdf";
    private static final String PDF_LOADER_URL = "file:///android_asset/pdf/index.html";
    private static final String OFFLINE_PAGE_URL = "file:///android_asset/webview_error.html";
    private static final String READER_MODE_READABILITY_SCRIPT_ASSET = "vendor/mozilla/readability/0.6.0/Readability.min.js";
    private static final String READER_MODE_SCRIPT_ASSET = "reader_mode.js";
    private static final long WEBVIEW_VISIBLE_LOAD_GRACE_MS = 1500;
    private static final long READER_MODE_INITIAL_AVAILABILITY_GRACE_MS = 2000;
    private static final long READER_MODE_AVAILABILITY_RECHECK_DELAY_MS = 2500;
    private static final long WEBVIEW_LOAD_TIMEOUT_MS = 45000;
    private static final long SUMMARY_LOAD_TIMEOUT_MS = 30000;

    private enum ErrorPageType {
        DNS,
        OFFLINE,
        SSL,
        GENERIC
    }

    interface Callbacks {
        void onSwitchView(boolean isAtWebView);

        void syncOnBackPressedCallbackEnabledState();

        void onReaderModeChanged(boolean enabled);

        void onReaderModeAvailabilityChanged(boolean available);
    }

    interface PageTextCallback {
        void onResult(@Nullable String text);
    }

    private final CommentsFragment fragment;
    private final Story story;
    private final LinkPreviewController linkPreviewController;
    private final Callbacks callbacks;
    private final Handler webViewHandler = new Handler(Looper.getMainLooper());
    private final Runnable initializeWebViewRunnable = this::initialize;
    private final Runnable webViewBackdropFadeInRunnable = new Runnable() {
        @Override
        public void run() {
            if (webViewBackdrop != null) {
                webViewBackdrop.animate()
                        .alpha(1f)
                        .setDuration(300)
                        .start();
            }
        }
    };

    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout bottomSheet;
    private WebView webView;
    private ViewStub webViewStub;
    private FrameLayout webViewContainer;
    private FrameLayout fullscreenContainer;
    private View webViewBackdrop;
    private MaterialButton downloadButton;
    private LinearProgressIndicator progressIndicator;
    private ValueAnimator progressAnimator;
    private BottomSheetBehavior.BottomSheetCallback webViewBottomSheetCallback;
    private boolean showWebsite = false;
    private boolean integratedWebview = true;
    private String preloadWebview = "never";
    private int preloadWebviewMinimumBattery = SettingsUtils.DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY;
    private boolean matchWebviewTheme = true;
    private boolean readerModeFeatureEnabled = true;
    private boolean readerModeDefault = false;
    private boolean blockAds = true;
    private boolean startedLoading = false;
    private boolean initializedWebView = false;
    private boolean showingErrorPage = false;
    private boolean showingCachedArticlePage = false;
    private boolean clearWebViewHistoryOnNextFinish = false;
    private boolean webViewLoadInProgress = false;
    private boolean webViewLoadUiSettled = true;
    private boolean webViewLoadCommittedVisible = false;
    private int webViewLoadGeneration = 0;
    @Nullable
    private String lastFailedWebViewUrl;
    @Nullable
    private String lastRequestedWebViewUrl;
    @Nullable
    private Runnable pendingSummaryOnDone;
    private int pendingSummaryGeneration = 0;
    @Nullable
    private View customView;
    @Nullable
    private WebChromeClient.CustomViewCallback customViewCallback;
    @Nullable
    private PdfAndroidJavascriptBridge pdfAndroidJavascriptBridge;
    @Nullable
    private String currentPdfFilePath;
    private boolean retryingFailedWebViewUrl = false;
    private boolean readerModeAvailable = false;
    private boolean readerModeEnabled = false;
    private boolean readerModePending = false;
    private boolean readerModeDisabledForCurrentPage = false;
    private boolean readerModeInitialAvailabilityGraceUsed = false;
    private int readerModeInitialAvailabilityGraceGeneration = -1;
    private int readerModeUnavailableDelayGeneration = -1;
    private int readerModeAvailabilityRecheckGeneration = -1;
    private boolean readerModeAvailabilityRecheckUsed = false;
    private long readerModeInitialAvailabilityGraceStartedAtMs = 0L;
    @Nullable
    private String readerModeScript;

    CommentsWebViewController(CommentsFragment fragment, Story story, LinkPreviewController linkPreviewController, Callbacks callbacks) {
        this.fragment = fragment;
        this.story = story;
        this.linkPreviewController = linkPreviewController;
        this.callbacks = callbacks;
    }

    void bindViews(@NonNull FragmentCommentsBinding binding, @NonNull LinearLayout bottomSheet, @NonNull SwipeRefreshLayout swipeRefreshLayout, @NonNull LinearProgressIndicator progressIndicator) {
        this.bottomSheet = bottomSheet;
        this.swipeRefreshLayout = swipeRefreshLayout;
        this.progressIndicator = progressIndicator;
        this.progressIndicator.setVisibility(View.GONE);
        this.progressIndicator.setProgress(0);
        webViewStub = binding.commentsWebviewStub;
        webView = null;
        downloadButton = binding.webviewDownload;
        webViewContainer = binding.webviewContainer;
        fullscreenContainer = binding.commentsFullscreenContainer;
        webViewBackdrop = binding.commentsWebviewBackdrop;
    }

    void configure(boolean showWebsite, boolean integratedWebview, String preloadWebview, int preloadWebviewMinimumBattery, boolean matchWebviewTheme, boolean readerModeFeatureEnabled, boolean readerModeDefault, boolean blockAds) {
        this.showWebsite = showWebsite;
        this.integratedWebview = integratedWebview;
        this.preloadWebview = preloadWebview;
        this.preloadWebviewMinimumBattery = preloadWebviewMinimumBattery;
        this.matchWebviewTheme = matchWebviewTheme;
        this.readerModeFeatureEnabled = readerModeFeatureEnabled;
        this.readerModeDefault = readerModeFeatureEnabled && readerModeDefault;
        this.blockAds = blockAds;
    }

    void setIntegratedWebview(boolean integratedWebview) {
        this.integratedWebview = integratedWebview;
        if (!integratedWebview) {
            setReaderModeUnavailableNow();
            setReaderModeEnabled(false);
        }
    }

    Runnable getInitializeRunnable() {
        return initializeWebViewRunnable;
    }

    @Nullable
    BottomSheetBehavior.BottomSheetCallback getBottomSheetCallback() {
        return webViewBottomSheetCallback;
    }

    boolean hasWebView() {
        return webView != null;
    }

    void getLoadedPageText(@NonNull PageTextCallback callback) {
        WebView targetWebView = webView;
        if (!canReadLoadedPageText(targetWebView)) {
            callback.onResult(null);
            return;
        }

        int generation = webViewLoadGeneration;
        targetWebView.evaluateJavascript(
                "(function() { return document.body ? (document.body.innerText || '') : ''; })();",
                result -> {
                    if (generation != webViewLoadGeneration || !canReadLoadedPageText(targetWebView)) {
                        callback.onResult(null);
                        return;
                    }
                    callback.onResult(decodeJavascriptString(result));
                }
        );
    }

    boolean canGoBack() {
        return webView != null && webView.canGoBack();
    }

    boolean isShowingCustomView() {
        return customView != null;
    }

    boolean willExpandBottomSheetOnBack() {
        return !isShowingCustomView() && webView != null && !webView.canGoBack();
    }

    boolean isShowingOfflineOrCachedPage() {
        return showingErrorPage || showingCachedArticlePage;
    }

    boolean hasLastFailedUrl() {
        return !TextUtils.isEmpty(lastFailedWebViewUrl);
    }

    boolean isReaderModeAvailable() {
        return readerModeAvailable;
    }

    boolean isReaderModeEnabled() {
        return readerModeEnabled;
    }

    void setContainerVisibility(int visibility) {
        if (webViewContainer != null) {
            webViewContainer.setVisibility(visibility);
        }
    }

    void setContainerPadding(int left, int top, int right, int bottom) {
        if (webViewContainer != null) {
            webViewContainer.setPadding(left, top, right, bottom);
        }
    }

    void setContainerLayoutParams(ViewGroup.LayoutParams params) {
        if (webViewContainer != null) {
            webViewContainer.setLayoutParams(params);
        }
    }

    void setContainerBackgroundColor(int color) {
        if (webViewContainer != null) {
            webViewContainer.setBackgroundColor(color);
        }
    }

    void goBackFromVisibleWebView() {
        if (webView == null || !webView.canGoBack()) {
            return;
        }
        if (downloadButton != null && downloadButton.getVisibility() == View.VISIBLE && webView.getVisibility() == View.GONE) {
            webView.setVisibility(View.VISIBLE);
            downloadButton.setVisibility(View.GONE);
        } else if (showingErrorPage) {
            showingErrorPage = false;
            if (webView.canGoBackOrForward(-2)) {
                webView.goBackOrForward(-2);
            } else {
                webView.goBack();
            }
        } else {
            webView.goBack();
        }
    }

    void retryLastFailedUrl() {
        if (!TextUtils.isEmpty(lastFailedWebViewUrl)) {
            retryingFailedWebViewUrl = true;
            loadUrl(lastFailedWebViewUrl);
        }
    }

    void reload() {
        if (webView != null) {
            webView.reload();
        }
    }

    void openCurrentOrStoryUrlInBrowser() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        try {
            if (webView == null) {
                throw new IllegalStateException("WebView not available");
            }
            intent.setData(Uri.parse(webView.getUrl()));
            fragment.startActivity(intent);
        } catch (Exception e) {
            try {
                intent.setData(Uri.parse(story.url));
                fragment.startActivity(intent);
            } catch (Exception e2) {
                Utils.toast("Couldn't open URL", fragment.getContext());
            }
        }
    }

    void disableAdBlockAndReload() {
        blockAds = false;
        if (webView == null) {
            return;
        }
        webView.reload();

        Snackbar snackbar = Snackbar.make(webView, "Disabled AdBlock, refreshing WebView", Snackbar.LENGTH_SHORT);
        ViewCompat.setElevation(snackbar.getView(), Utils.pxFromDp(fragment.getResources(), 24));
        snackbar.show();
    }

    boolean isBlockingAds() {
        return blockAds;
    }

    void toggleReaderMode() {
        if (!readerModeFeatureEnabled) {
            return;
        }
        if (webView == null) {
            initialize();
        }
        Context context = fragment.getContext();
        if (webView == null || context == null || fragment.getView() == null) {
            return;
        }

        String currentUrl = webView.getUrl();
        if (showingErrorPage || PDF_LOADER_URL.equals(currentUrl) || isErrorPageUrl(currentUrl)) {
            Toast.makeText(context, "Reader mode unavailable for this page", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean enableReaderMode = !readerModeEnabled;
        readerModeDisabledForCurrentPage = !enableReaderMode;
        if (!startedLoading || TextUtils.isEmpty(currentUrl) || (webView.getProgress() < 100 && webViewLoadInProgress)) {
            if (TextUtils.isEmpty(story.url)) {
                Toast.makeText(context, "Reader mode unavailable for this page", Toast.LENGTH_SHORT).show();
                return;
            }
            readerModePending = true;
            if (!startedLoading) {
                startedLoading = true;
                loadUrl(story.url);
            }
            Toast.makeText(context, "Reader mode will open after the page loads", Toast.LENGTH_SHORT).show();
            return;
        }

        applyReaderMode(enableReaderMode);
    }

    void disableReaderMode() {
        if (!readerModeEnabled) {
            return;
        }
        readerModeDisabledForCurrentPage = true;
        applyReaderMode(false);
    }

    private void applyReaderMode(boolean enable) {
        applyReaderMode(enable, true);
    }

    private void applyReaderMode(boolean enable, boolean showFeedback) {
        Context context = fragment.getContext();
        if (!readerModeFeatureEnabled || webView == null || context == null || fragment.getView() == null) {
            return;
        }

        String script = getReaderModeScript(context);
        if (TextUtils.isEmpty(script)) {
            setReaderModeUnavailableNow();
            if (showFeedback) {
                Toast.makeText(context, "Reader mode unavailable", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String command = script
                + "\nHarmonicReaderMode.setTheme(" + getReaderModeThemeJson(context) + ");"
                + "\nHarmonicReaderMode." + (enable ? "enable" : "disable") + "();";
        WebView targetWebView = webView;
        int generation = webViewLoadGeneration;
        targetWebView.evaluateJavascript(command, result -> {
            Context callbackContext = fragment.getContext();
            if (callbackContext == null
                    || targetWebView != webView
                    || generation != webViewLoadGeneration
                    || fragment.getView() == null) {
                return;
            }

            String status = normalizeJavascriptResult(result);
            if ("enabled".equals(status)) {
                setReaderModeConfirmedAvailable();
                setReaderModeEnabled(true);
            } else if ("disabled".equals(status)) {
                setReaderModeConfirmedAvailable();
                setReaderModeEnabled(false);
            } else if ("no_article".equals(status)) {
                setReaderModeUnavailableRespectingInitialGrace(generation);
                setReaderModeEnabled(false);
                if (showFeedback) {
                    Toast.makeText(callbackContext, "Couldn't find readable article", Toast.LENGTH_SHORT).show();
                }
            } else if ("unavailable".equals(status)) {
                setReaderModeUnavailableRespectingInitialGrace(generation);
                setReaderModeEnabled(false);
                if (showFeedback) {
                    Toast.makeText(callbackContext, "Reader mode unavailable for this page", Toast.LENGTH_SHORT).show();
                }
            } else {
                setReaderModeUnavailableRespectingInitialGrace(generation);
                if (showFeedback) {
                    Toast.makeText(callbackContext, "Couldn't open reader mode", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void checkReaderModeAvailability(WebView view, int generation) {
        Context context = fragment.getContext();
        if (!canCheckReaderModeAvailability(view, generation, context)) {
            setReaderModeUnavailableNow();
            return;
        }

        String script = getReaderModeScript(context);
        if (TextUtils.isEmpty(script)) {
            setReaderModeUnavailableNow();
            return;
        }

        view.evaluateJavascript(script + "\nHarmonicReaderMode.isAvailable();", result -> {
            Context callbackContext = fragment.getContext();
            if (!canCheckReaderModeAvailability(view, generation, callbackContext)) {
                return;
            }

            if ("available".equals(normalizeJavascriptResult(result))) {
                setReaderModeConfirmedAvailable();
            } else {
                setReaderModeUnavailableRespectingInitialGrace(generation);
                scheduleReaderModeAvailabilityRecheck(view, generation);
            }
        });
    }

    private boolean canCheckReaderModeAvailability(WebView view, int generation, @Nullable Context context) {
        if (!readerModeFeatureEnabled
                || !integratedWebview
                || view != webView
                || generation != webViewLoadGeneration
                || context == null
                || fragment.getView() == null) {
            return false;
        }

        String currentUrl = view.getUrl();
        return !showingErrorPage
                && !PDF_LOADER_URL.equals(currentUrl)
                && !isErrorPageUrl(currentUrl);
    }

    private void setReaderModeAvailable(boolean available) {
        if (available) {
            readerModeUnavailableDelayGeneration = -1;
        }
        boolean effectiveAvailable = readerModeFeatureEnabled && available;
        if (readerModeAvailable == effectiveAvailable) {
            return;
        }

        readerModeAvailable = effectiveAvailable;
        callbacks.onReaderModeAvailabilityChanged(effectiveAvailable);
    }

    private void setReaderModeConfirmedAvailable() {
        readerModeInitialAvailabilityGraceGeneration = -1;
        readerModeUnavailableDelayGeneration = -1;
        readerModeAvailabilityRecheckGeneration = -1;
        setReaderModeAvailable(true);
    }

    private void setReaderModeUnavailableNow() {
        readerModeInitialAvailabilityGraceGeneration = -1;
        readerModeUnavailableDelayGeneration = -1;
        setReaderModeAvailable(false);
    }

    private void setReaderModeUnavailableRespectingInitialGrace(int generation) {
        if (!isReaderModeInitialAvailabilityGraceActive(generation)) {
            setReaderModeUnavailableNow();
            return;
        }

        long elapsed = SystemClock.uptimeMillis() - readerModeInitialAvailabilityGraceStartedAtMs;
        long remaining = READER_MODE_INITIAL_AVAILABILITY_GRACE_MS - elapsed;
        if (remaining <= 0) {
            setReaderModeUnavailableNow();
            return;
        }

        readerModeUnavailableDelayGeneration = generation;
        webViewHandler.postDelayed(() -> {
            if (readerModeUnavailableDelayGeneration == generation
                    && generation == webViewLoadGeneration
                    && isReaderModeInitialAvailabilityGraceActive(generation)) {
                setReaderModeUnavailableNow();
            }
        }, remaining);
    }

    private void scheduleReaderModeAvailabilityRecheck(WebView view, int generation) {
        if (readerModeAvailabilityRecheckUsed
                || view != webView
                || generation != webViewLoadGeneration) {
            return;
        }

        readerModeAvailabilityRecheckUsed = true;
        readerModeAvailabilityRecheckGeneration = generation;
        webViewHandler.postDelayed(() -> {
            if (readerModeAvailabilityRecheckGeneration == generation
                    && generation == webViewLoadGeneration
                    && view == webView) {
                checkReaderModeAvailability(view, generation);
            }
        }, READER_MODE_AVAILABILITY_RECHECK_DELAY_MS);
    }

    private void updateReaderModeAvailabilityForLoadStart(@Nullable String url, int generation) {
        readerModeAvailabilityRecheckGeneration = -1;
        readerModeAvailabilityRecheckUsed = false;
        if (!readerModeFeatureEnabled
                || !integratedWebview
                || TextUtils.isEmpty(url)
                || PDF_LOADER_URL.equals(url)
                || isErrorPageUrl(url)) {
            setReaderModeUnavailableNow();
            return;
        }

        if (!readerModeInitialAvailabilityGraceUsed || isReaderModeInitialAvailabilityGraceActive()) {
            if (!readerModeInitialAvailabilityGraceUsed) {
                readerModeInitialAvailabilityGraceUsed = true;
                readerModeInitialAvailabilityGraceStartedAtMs = SystemClock.uptimeMillis();
            }
            readerModeInitialAvailabilityGraceGeneration = generation;
            setReaderModeAvailable(true);
        } else {
            setReaderModeUnavailableNow();
        }
    }

    private boolean isReaderModeInitialAvailabilityGraceActive() {
        return readerModeInitialAvailabilityGraceGeneration >= 0
                && SystemClock.uptimeMillis() - readerModeInitialAvailabilityGraceStartedAtMs
                < READER_MODE_INITIAL_AVAILABILITY_GRACE_MS;
    }

    private boolean isReaderModeInitialAvailabilityGraceActive(int generation) {
        return readerModeInitialAvailabilityGraceGeneration == generation
                && isReaderModeInitialAvailabilityGraceActive();
    }

    private void setReaderModeEnabled(boolean enabled) {
        if (readerModeEnabled == enabled) {
            return;
        }

        readerModeEnabled = enabled;
        callbacks.onReaderModeChanged(enabled);
        callbacks.syncOnBackPressedCallbackEnabledState();
    }

    @Nullable
    private String getReaderModeScript(Context context) {
        if (readerModeScript != null) {
            return readerModeScript;
        }

        try {
            StringBuilder builder = new StringBuilder();
            appendAssetFile(context, READER_MODE_READABILITY_SCRIPT_ASSET, builder);
            appendAssetFile(context, READER_MODE_SCRIPT_ASSET, builder);
            readerModeScript = builder.toString();
            return readerModeScript;
        } catch (IOException e) {
            Log.e("MY_APP_TAG", "Failed to load reader mode script", e);
            return null;
        }
    }

    private void appendAssetFile(Context context, String assetPath, StringBuilder builder) throws IOException {
        try (InputStream inputStream = context.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
    }

    private String normalizeJavascriptResult(@Nullable String result) {
        if (result == null) {
            return "";
        }

        String normalized = result.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private String getReaderModeThemeJson(Context context) {
        int backgroundColor = Color.TRANSPARENT;
        try {
            backgroundColor = ContextCompat.getColor(context, ThemeUtils.getBackgroundColorResource(context));
        } catch (Exception ignored) {
        }
        if (backgroundColor == Color.TRANSPARENT) {
            backgroundColor = MaterialColors.getColor(context, android.R.attr.colorBackground, Color.WHITE);
        }

        boolean isLightMode = ThemeUtils.isLightMode(context);
        int linkColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSecondary, Color.rgb(26, 115, 232));
        int textColor = MaterialColors.getColor(context, R.attr.textColorDefault, isLightMode ? Color.rgb(32, 33, 36) : Color.rgb(232, 234, 237));
        int headingColor = MaterialColors.getColor(context, R.attr.storyColorNormal, textColor);
        int secondaryTextColor = MaterialColors.getColor(context, R.attr.secondaryTextColor, isLightMode ? Color.rgb(95, 99, 104) : Color.rgb(174, 180, 186));
        int dividerColor = MaterialColors.getColor(context, R.attr.commentDividerColor, isLightMode ? Color.rgb(218, 220, 224) : Color.rgb(61, 66, 72));
        int codeBackgroundColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainerHigh, backgroundColor);
        String readerModeFont = SettingsUtils.getPreferredReaderModeFont(context);
        String readerModeFontFaceCss = getReaderModeFontFaceCss(context, readerModeFont);
        String readerModeFontFamily = TextUtils.isEmpty(readerModeFontFaceCss)
                ? getReaderModeSystemFontFamily(readerModeFont)
                : "'HarmonicReaderFont', " + getReaderModeSystemFontFamily(readerModeFont);
        int readerModeFontSize = SettingsUtils.getReaderModeFontSize(context);

        return String.format(Locale.US,
                "{\"isLight\":%s,\"backgroundColor\":\"%s\",\"textColor\":\"%s\",\"headingColor\":\"%s\",\"secondaryTextColor\":\"%s\",\"linkColor\":\"%s\",\"dividerColor\":\"%s\",\"codeBackgroundColor\":\"%s\",\"fontFaceCss\":%s,\"fontFamily\":%s,\"headingFontFamily\":%s,\"fontSizePx\":%d}",
                isLightMode ? "true" : "false",
                colorToCss(backgroundColor),
                colorToCss(textColor),
                colorToCss(headingColor),
                colorToCss(secondaryTextColor),
                colorToCss(linkColor),
                colorToCss(dividerColor),
                colorToCss(codeBackgroundColor),
                jsonString(readerModeFontFaceCss),
                jsonString(readerModeFontFamily),
                jsonString(readerModeFontFamily),
                readerModeFontSize);
    }

    private String colorToCss(int color) {
        return String.format(Locale.US, "#%06X", 0xFFFFFF & color);
    }

    private String getReaderModeSystemFontFamily(String font) {
        switch (SettingsUtils.sanitizeReaderModeFont(font)) {
            case "productsans":
            case "googlesansflexrounded":
            case "googlesans":
            case "verdana":
                return "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
            case "robotoslab":
            case "georgia":
                return "Georgia, 'Times New Roman', serif";
            case "jetbrainsmono":
            case "googlesanscode":
                return "ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace";
            case "devicedefault":
            default:
                return "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif";
        }
    }

    private String getReaderModeFontFaceCss(Context context, String font) {
        int regularFontResource;
        int boldFontResource;
        switch (SettingsUtils.sanitizeReaderModeFont(font)) {
            case "productsans":
                regularFontResource = R.font.product_sans_regular;
                boldFontResource = R.font.product_sans_bold;
                break;
            case "googlesansflexrounded":
                regularFontResource = R.font.google_sans_flex_rounded_regular;
                boldFontResource = R.font.google_sans_flex_rounded_bold;
                break;
            case "googlesans":
                regularFontResource = R.font.google_sans_regular;
                boldFontResource = R.font.google_sans_bold;
                break;
            case "verdana":
                regularFontResource = R.font.verdana_regular;
                boldFontResource = R.font.verdana_bold;
                break;
            case "robotoslab":
                regularFontResource = R.font.roboto_slab_regular;
                boldFontResource = R.font.roboto_slab_bold;
                break;
            case "googlesanscode":
                regularFontResource = R.font.google_sans_code_regular;
                boldFontResource = R.font.google_sans_code_regular;
                break;
            case "jetbrainsmono":
                regularFontResource = R.font.jetbrains_mono_regular;
                boldFontResource = R.font.jetbrains_mono_bold;
                break;
            case "georgia":
                regularFontResource = R.font.georgia_regular;
                boldFontResource = R.font.georgia_bold;
                break;
            case "devicedefault":
            default:
                return "";
        }

        String regularFontData = getFontDataUrl(context, regularFontResource);
        String boldFontData = getFontDataUrl(context, boldFontResource);
        if (TextUtils.isEmpty(regularFontData) || TextUtils.isEmpty(boldFontData)) {
            return "";
        }

        return "@font-face{font-family:'HarmonicReaderFont';font-style:normal;font-weight:400;src:url(" + regularFontData + ") format('truetype');}"
                + "@font-face{font-family:'HarmonicReaderFont';font-style:normal;font-weight:700;src:url(" + boldFontData + ") format('truetype');}";
    }

    private String getFontDataUrl(Context context, int fontResource) {
        try (InputStream inputStream = context.getResources().openRawResource(fontResource)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return "data:font/ttf;base64," + Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP);
        } catch (IOException e) {
            Log.e("MY_APP_TAG", "Failed to load reader mode font", e);
            return "";
        }
    }

    private String jsonString(String value) {
        String safeValue = value != null ? value : "";
        StringBuilder builder = new StringBuilder(safeValue.length() + 2);
        builder.append('"');
        for (int i = 0; i < safeValue.length(); i++) {
            char c = safeValue.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    builder.append('\\').append(c);
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    builder.append(c);
                    break;
            }
        }
        builder.append('"');
        return builder.toString();
    }

    void toggleDarkMode() {
        if (webView == null) {
            return;
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.getSettings(), !WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.getSettings()));
        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            if (WebSettingsCompat.getForceDark(webView.getSettings()) == WebSettingsCompat.FORCE_DARK_ON) {
                WebSettingsCompat.setForceDark(webView.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
            } else {
                WebSettingsCompat.setForceDark(webView.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
            }
        }
    }

    @SuppressLint({"RequiresFeature", "SetJavaScriptEnabled"})
    @SuppressWarnings("deprecation")
    void initialize() {
        if (initializedWebView) {
            return;
        }

        Context context = fragment.getContext();
        if (context == null || fragment.getView() == null) {
            return;
        }

        webView = getOrInflateWebView();
        if (webView == null) {
            return;
        }
        initializedWebView = true;
        BottomSheetBehavior.from(bottomSheet).setDraggable(true);
        webViewBottomSheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                callbacks.onSwitchView(newState == BottomSheetBehavior.STATE_COLLAPSED);
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                if (!startedLoading && slideOffset < 0.9999) {
                    startedLoading = true;
                    loadUrl(story.url);
                }
            }
        };
        BottomSheetBehavior.from(bottomSheet).addBottomSheetCallback(webViewBottomSheetCallback);

        try {
            ((FrameLayout) swipeRefreshLayout.getParent()).removeView(swipeRefreshLayout);
        } catch (Exception e) {
            // This will crash if we have already done this, which is fine.
        }

        if (blockAds && Utils.adservers.isEmpty()) {
            Utils.loadAdservers(context.getResources());
        }

        webView.setWebViewClient(new MyWebViewClient());

        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setGeolocationEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimetype,
                                        long contentLength) {

                if (!TextUtils.isEmpty(mimetype) && mimetype.equals(PDF_MIME_TYPE) && (url.startsWith("http://") || url.startsWith("https://"))) {
                    downloadPdf(url, contentDisposition, mimetype, webView.getContext());
                } else {
                    showDownloadButton(url, contentDisposition, mimetype);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fragment.getContext() == null || fragment.getView() == null || fullscreenContainer == null || webViewContainer == null || bottomSheet == null) {
                    callback.onCustomViewHidden();
                    return;
                }
                showCustomView(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideCustomView(false);
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                updateWebViewProgress(view, newProgress);
            }
        });

        if (matchWebviewTheme && ThemeUtils.isDarkMode(context)) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.getSettings(), true);
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(webView.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
            }
        }

        webView.setBackgroundColor(Color.TRANSPARENT);

        if (shouldPreloadStoryUrl(context) || showWebsite || linkPreviewController.shouldInitializeWebViewForPreview(context)) {
            loadUrl(story.url);
            startedLoading = true;
        }
    }

    private boolean shouldPreloadStoryUrl(Context context) {
        boolean enabledForConnection = SettingsUtils.PRELOAD_WEBVIEW_ALWAYS.equals(preloadWebview)
                || (SettingsUtils.PRELOAD_WEBVIEW_ONLY_WIFI.equals(preloadWebview) && Utils.isOnWiFi(context));
        return enabledForConnection
                && SettingsUtils.hasEnoughBatteryForWebViewPreload(context, preloadWebviewMinimumBattery);
    }

    private boolean isCurrentWebViewCallback(WebView view) {
        return view != null
                && view == webView
                && fragment.getContext() != null
                && fragment.getView() != null
                && bottomSheet != null
                && webViewBackdrop != null;
    }

    private void beginWebViewLoad(@NonNull WebView view, @Nullable String url) {
        if (view != webView || fragment.getContext() == null || fragment.getView() == null) {
            return;
        }

        webViewLoadGeneration++;
        webViewLoadInProgress = true;
        webViewLoadUiSettled = false;
        webViewLoadCommittedVisible = false;

        if (webViewBackdrop != null) {
            webViewBackdrop.removeCallbacks(webViewBackdropFadeInRunnable);
            webViewBackdrop.animate().cancel();
            webViewBackdrop.setAlpha(0f);
            webViewBackdrop.setVisibility(View.VISIBLE);
            webViewBackdrop.postDelayed(webViewBackdropFadeInRunnable, 2000);
        }

        showWebViewProgress(0);

        int generation = webViewLoadGeneration;
        webViewHandler.postDelayed(() -> handleWebViewLoadTimeout(view, url, generation), WEBVIEW_LOAD_TIMEOUT_MS);
    }

    private void handleWebViewLoadTimeout(@NonNull WebView view, @Nullable String url, int generation) {
        if (view != webView || generation != webViewLoadGeneration || !webViewLoadInProgress) {
            return;
        }

        if (webViewLoadCommittedVisible) {
            finishWebViewLoadUi(view, generation, false);
            return;
        }

        showCustomErrorPage(view, !TextUtils.isEmpty(url) ? url : view.getUrl(), ErrorPageType.GENERIC);
    }

    private void scheduleVisibleCommitSettle(@NonNull WebView view) {
        int generation = webViewLoadGeneration;
        webViewHandler.postDelayed(() -> {
            if (webViewLoadCommittedVisible && webViewLoadInProgress) {
                finishWebViewLoadUi(view, generation, false);
            }
        }, WEBVIEW_VISIBLE_LOAD_GRACE_MS);
    }

    private void showWebViewProgress(int progress) {
        LinearProgressIndicator currentProgressIndicator = progressIndicator;
        if (currentProgressIndicator == null) {
            return;
        }

        cancelProgressAnimator();
        currentProgressIndicator.setProgress(progress);
        currentProgressIndicator.setVisibility(View.VISIBLE);
    }

    private void updateWebViewProgress(@NonNull WebView view, int newProgress) {
        LinearProgressIndicator currentProgressIndicator = progressIndicator;
        if (view != webView || fragment.getContext() == null || fragment.getView() == null || currentProgressIndicator == null) {
            return;
        }
        cancelProgressAnimator();

        int current = currentProgressIndicator.getProgress();
        if (newProgress > current) {
            progressAnimator = ValueAnimator.ofInt(current, newProgress);
            progressAnimator.setDuration(400);
            progressAnimator.addUpdateListener(anim -> {
                if (progressAnimator != anim || progressIndicator != currentProgressIndicator) {
                    return;
                }
                int animatedValue = (int) anim.getAnimatedValue();
                currentProgressIndicator.setProgress(animatedValue);
            });
            progressAnimator.start();
        } else {
            currentProgressIndicator.setProgress(newProgress);
        }

        if (newProgress >= 100) {
            finishWebViewLoadUi(view, webViewLoadGeneration, true);
        } else if (!webViewLoadUiSettled) {
            currentProgressIndicator.setVisibility(View.VISIBLE);
        }
    }

    private void finishWebViewLoadUi(@NonNull WebView view, int generation, boolean completeProgress) {
        if (view != webView || generation != webViewLoadGeneration) {
            return;
        }

        webViewLoadInProgress = false;
        webViewLoadUiSettled = true;

        view.setBackgroundColor(Color.WHITE);
        hideWebViewLoadingBackdrop();

        LinearProgressIndicator currentProgressIndicator = progressIndicator;
        if (currentProgressIndicator != null) {
            cancelProgressAnimator();
            if (completeProgress) {
                currentProgressIndicator.setProgress(100);
            }
            currentProgressIndicator.setVisibility(View.GONE);
        }

        completePendingSummaryIfReady(view);
    }

    private void hideWebViewLoadingBackdrop() {
        if (webViewBackdrop == null) {
            return;
        }

        webViewBackdrop.removeCallbacks(webViewBackdropFadeInRunnable);
        webViewBackdrop.animate().cancel();
        webViewBackdrop.setVisibility(View.GONE);
        webViewBackdrop.setAlpha(0f);
    }

    private void showCustomErrorPage(WebView view, @Nullable String failingUrl, @NonNull ErrorPageType errorPageType) {
        if (!isCurrentWebViewCallback(view) || showingErrorPage || showingCachedArticlePage) {
            return;
        }
        linkPreviewController.onWebViewOfflineFallback(fragment.getContext());
        if ((errorPageType == ErrorPageType.DNS || errorPageType == ErrorPageType.OFFLINE)
                && loadCachedArticleSnapshot(view, failingUrl)) {
            return;
        }
        if (!TextUtils.isEmpty(failingUrl)) {
            lastFailedWebViewUrl = failingUrl;
        } else if (lastRequestedWebViewUrl != null) {
            lastFailedWebViewUrl = lastRequestedWebViewUrl;
        } else if (view.getUrl() != null && !TextUtils.isEmpty(view.getUrl()) && !isErrorPageUrl(view.getUrl())) {
            lastFailedWebViewUrl = view.getUrl();
        }
        retryingFailedWebViewUrl = false;
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }
        view.stopLoading();
        finishWebViewLoadUi(view, webViewLoadGeneration, false);
        clearWebViewHistoryOnNextFinish = !view.canGoBack();
        showingErrorPage = true;
        showingCachedArticlePage = false;
        loadUrl(getErrorPageUrl(errorPageType));
    }

    void hideCustomView(boolean notifyCallback) {
        if (!isShowingCustomView()) {
            return;
        }

        View currentCustomView = customView;
        WebChromeClient.CustomViewCallback currentCustomViewCallback = customViewCallback;
        customView = null;
        customViewCallback = null;

        if (currentCustomView != null && currentCustomView.getParent() instanceof ViewGroup) {
            ((ViewGroup) currentCustomView.getParent()).removeView(currentCustomView);
        }

        fullscreenContainer.removeAllViews();
        fullscreenContainer.setVisibility(View.GONE);
        webViewContainer.setVisibility(View.VISIBLE);
        bottomSheet.setVisibility(View.VISIBLE);

        setFullscreenSystemBarsHidden(false);
        callbacks.syncOnBackPressedCallbackEnabledState();

        callbacks.onSwitchView(BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED);

        if (notifyCallback && currentCustomViewCallback != null) {
            currentCustomViewCallback.onCustomViewHidden();
        }
    }

    void loadStoryUrl() {
        loadUrl(story.url);
    }

    private void showCustomView(@NonNull View view, @NonNull WebChromeClient.CustomViewCallback callback) {
        if (isShowingCustomView()) {
            hideCustomView(true);
        }

        customView = view;
        customViewCallback = callback;

        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }

        fullscreenContainer.removeAllViews();
        fullscreenContainer.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        fullscreenContainer.setVisibility(View.VISIBLE);
        webViewContainer.setVisibility(View.GONE);
        bottomSheet.setVisibility(View.GONE);

        setFullscreenSystemBarsHidden(true);
        callbacks.syncOnBackPressedCallbackEnabledState();

        callbacks.onSwitchView(true);
    }

    private void setFullscreenSystemBarsHidden(boolean hidden) {
        if (fragment.getActivity() == null) {
            return;
        }

        WindowInsetsControllerCompat windowInsetsController =
                ViewCompat.getWindowInsetsController(fragment.requireActivity().getWindow().getDecorView());
        if (windowInsetsController == null) {
            return;
        }

        if (hidden) {
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
        }
    }

    private void loadUrl(String url) {
        loadUrl(url, null);
    }

    private boolean isErrorPageUrl(@Nullable String url) {
        return url != null && url.startsWith(OFFLINE_PAGE_URL);
    }

    private String getErrorPageUrl(@NonNull ErrorPageType type) {
        switch (type) {
            case DNS:
                return OFFLINE_PAGE_URL + "#dns";
            case SSL:
                return OFFLINE_PAGE_URL + "#ssl";
            case GENERIC:
                return OFFLINE_PAGE_URL + "#generic";
            case OFFLINE:
            default:
                return OFFLINE_PAGE_URL + "#offline";
        }
    }

    private void loadUrl(String url, @Nullable String pdfFilePath) {
        Context context = fragment.getContext();
        if (webView == null && integratedWebview) {
            initialize();
            context = fragment.getContext();
        }
        if (webView == null || context == null || fragment.getView() == null) {
            return;
        }
        if (TextUtils.isEmpty(url)) {
            return;
        }

        String archiveRedirectUrl = SettingsUtils.getArchiveRedirectUrl(context, url);
        if (archiveRedirectUrl != null) {
            url = archiveRedirectUrl;
        }

        if (PDF_LOADER_URL.equals(url)) {
            pdfFilePath = !TextUtils.isEmpty(pdfFilePath) ? pdfFilePath : currentPdfFilePath;
            if (TextUtils.isEmpty(pdfFilePath)) {
                retryingFailedWebViewUrl = false;
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                return;
            }
        }

        if (!isErrorPageUrl(url)) {
            showingErrorPage = false;
            showingCachedArticlePage = false;
            setReaderModeEnabled(false);
            readerModeDisabledForCurrentPage = false;
            lastRequestedWebViewUrl = url;
            if (!PDF_LOADER_URL.equals(url)) {
                currentPdfFilePath = null;
            }
        }
        if (PDF_LOADER_URL.equals(url)) {
            currentPdfFilePath = pdfFilePath;
            setReaderModeUnavailableNow();
            clearPdfAndroidJavascriptBridge();
            pdfAndroidJavascriptBridge = new PdfAndroidJavascriptBridge(pdfFilePath, new PdfAndroidJavascriptBridge.Callbacks() {
                @Override
                public void onFailure() {

                }

                @Override
                public void onLoad() {

                }
            });

            webView.addJavascriptInterface(pdfAndroidJavascriptBridge, "PdfAndroidJavascriptBridge");
            webView.setInitialScale(100);
            webView.getSettings().setLoadWithOverviewMode(true);
            webView.getSettings().setUseWideViewPort(true);
        } else if (pdfAndroidJavascriptBridge != null) {
            webView.removeJavascriptInterface("PdfAndroidJavascriptBridge");
            clearPdfAndroidJavascriptBridge();
        }

        url = linkPreviewController.prepareWebViewLoad(context, webView, url);
        if (TextUtils.isEmpty(url)) {
            return;
        }
        beginWebViewLoad(webView, url);
        updateReaderModeAvailabilityForLoadStart(url, webViewLoadGeneration);
        webView.loadUrl(url);
        if (isErrorPageUrl(url)) {
            showingErrorPage = true;
            showingCachedArticlePage = false;
        }
    }

    @Nullable
    private WebView getOrInflateWebView() {
        if (webView != null) {
            return webView;
        }
        if (webViewStub == null) {
            return null;
        }

        View inflated = webViewStub.inflate();
        webView = CommentsWebviewBinding.bind(inflated).getRoot();
        webViewStub = null;
        return webView;
    }

    private void downloadPdf(String url, String contentDisposition, String mimetype, Context ctx) {
        if (ctx == null) {
            return;
        }
        FileDownloader fileDownloader = new FileDownloader(ctx);
        Toast.makeText(ctx, "Loading PDF...", Toast.LENGTH_LONG).show();
        fileDownloader.downloadFile(url, PDF_MIME_TYPE, new FileDownloader.FileDownloaderCallback() {
            @Override
            public void onFailure(Call call, IOException e) {
                showDownloadButton(url, contentDisposition, mimetype);
            }

            @Override
            public void onSuccess(String filePath) {
                loadUrl(PDF_LOADER_URL, filePath);
            }
        });

    }

    private void showDownloadButton(String url, String contentDisposition, String mimetype) {
        if (webView != null && downloadButton != null) {
            webView.setVisibility(View.GONE);
            downloadButton.setVisibility(View.VISIBLE);
            downloadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                        request.allowScanningByMediaScanner();
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));
                        DownloadManager dm = (DownloadManager) view.getContext().getSystemService(DOWNLOAD_SERVICE);
                        dm.enqueue(request);
                        Toast.makeText(fragment.getContext(), "Downloading...", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(fragment.getContext(), "Failed to download, opening in browser", Toast.LENGTH_LONG).show();
                        Utils.launchInExternalBrowser(fragment.getActivity(), url);
                    }

                }
            });
        }
    }

    void requestSummary(Runnable onDone) {
        if (webView == null || !startedLoading) {
            startedLoading = true;
            loadUrl(story.url);
        }

        if (webView == null) {
            webViewHandler.post(onDone);
            return;
        }

        pendingSummaryOnDone = onDone;
        int generation = ++pendingSummaryGeneration;
        webViewHandler.postDelayed(() -> finishPendingSummary(generation, "", null), SUMMARY_LOAD_TIMEOUT_MS);

        if (!webViewLoadInProgress || webView.getProgress() >= 100) {
            completePendingSummaryIfReady(webView);
        }
    }

    private void completePendingSummaryIfReady(@Nullable WebView targetWebView) {
        Runnable onDone = pendingSummaryOnDone;
        if (onDone == null || targetWebView == null || targetWebView != webView) {
            return;
        }

        int generation = pendingSummaryGeneration;
        pendingSummaryOnDone = null;
        targetWebView.evaluateJavascript(
                "(function() { return document.body ? (document.body.innerText || '') : ''; })();",
                result -> finishPendingSummary(generation, decodeJavascriptString(result), onDone)
        );
    }

    private boolean canReadLoadedPageText(@Nullable WebView targetWebView) {
        return targetWebView != null
                && targetWebView == webView
                && startedLoading
                && !webViewLoadInProgress
                && targetWebView.getProgress() >= 100
                && !showingErrorPage
                && !TextUtils.isEmpty(targetWebView.getUrl())
                && !isErrorPageUrl(targetWebView.getUrl());
    }

    private static String decodeJavascriptString(@Nullable String result) {
        if (result == null || "null".equals(result)) {
            return "";
        }
        try {
            return new JSONArray("[" + result + "]").optString(0, "");
        } catch (JSONException e) {
            return result.replaceAll("^\"|\"$", "");
        }
    }

    private void finishPendingSummary(int generation, String summary, @Nullable Runnable completedCallback) {
        if (generation != pendingSummaryGeneration) {
            return;
        }

        Runnable onDone = completedCallback != null ? completedCallback : pendingSummaryOnDone;
        if (onDone == null) {
            return;
        }
        pendingSummaryOnDone = null;
        story.summary = summary;
        webViewHandler.post(onDone);
    }

    @Nullable
    private ErrorPageType getCustomErrorPageType(int errorCode) {
        switch (errorCode) {
            case WebViewClient.ERROR_HOST_LOOKUP:
                return ErrorPageType.DNS;
            case WebViewClient.ERROR_CONNECT:
            case WebViewClient.ERROR_TIMEOUT:
                return ErrorPageType.OFFLINE;
            case WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:
                return ErrorPageType.SSL;
            case WebViewClient.ERROR_AUTHENTICATION:
            case WebViewClient.ERROR_BAD_URL:
            case WebViewClient.ERROR_FILE:
            case WebViewClient.ERROR_FILE_NOT_FOUND:
            case WebViewClient.ERROR_IO:
            case WebViewClient.ERROR_PROXY_AUTHENTICATION:
            case WebViewClient.ERROR_REDIRECT_LOOP:
            case WebViewClient.ERROR_UNKNOWN:
            case WebViewClient.ERROR_TOO_MANY_REQUESTS:
            case WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME:
            case WebViewClient.ERROR_UNSUPPORTED_SCHEME:
                return ErrorPageType.GENERIC;
            default:
                return null;
        }
    }

    private boolean loadCachedArticleSnapshot(WebView view, @Nullable String failingUrl) {
        Context context = fragment.getContext();
        if (view == null || context == null || fragment.getView() == null || story == null || !story.isLink || story.id <= 0) {
            return false;
        }

        String html = Utils.loadCachedArticleSnapshot(context, story.id);
        if (TextUtils.isEmpty(html)) {
            return false;
        }

        String baseUrl = Utils.loadCachedArticleUrl(context, story.id);
        if (TextUtils.isEmpty(baseUrl)) {
            baseUrl = !TextUtils.isEmpty(failingUrl) ? failingUrl : story.url;
        }

        lastFailedWebViewUrl = !TextUtils.isEmpty(failingUrl) ? failingUrl : baseUrl;
        retryingFailedWebViewUrl = false;
        showingErrorPage = false;
        showingCachedArticlePage = true;
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(false);
        }

        view.stopLoading();
        clearWebViewHistoryOnNextFinish = true;
        Toast.makeText(context, "Showing cached webview content", Toast.LENGTH_SHORT).show();
        view.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null);
        return true;
    }

    void destroy() {
        destroy(false);
    }

    private void destroy(boolean rendererProcessGone) {
        cancelProgressAnimator();
        currentPdfFilePath = null;
        webViewLoadGeneration++;
        webViewLoadInProgress = false;
        webViewLoadUiSettled = true;
        webViewLoadCommittedVisible = false;
        pendingSummaryOnDone = null;
        webViewHandler.removeCallbacksAndMessages(null);
        linkPreviewController.cancelPendingNitterLinkPreviewRead();
        if (webView != null) {
            WebView webViewToDestroy = webView;
            webView = null;
            initializedWebView = false;

            if (!rendererProcessGone) {
                webViewToDestroy.setWebViewClient(null);
                webViewToDestroy.setWebChromeClient(null);
                webViewToDestroy.setDownloadListener(null);
                webViewToDestroy.removeJavascriptInterface("PdfAndroidJavascriptBridge");
            }
            clearPdfAndroidJavascriptBridge();

            if (webViewToDestroy.getParent() instanceof ViewGroup) {
                ((ViewGroup) webViewToDestroy.getParent()).removeView(webViewToDestroy);
            }

            try {
                if (!rendererProcessGone) {
                    webViewToDestroy.stopLoading();
                    webViewToDestroy.clearHistory();
                    webViewToDestroy.clearCache(true);
                    webViewToDestroy.onPause();
                    webViewToDestroy.removeAllViews();
                    webViewToDestroy.destroyDrawingCache();
                    webViewToDestroy.pauseTimers();
                }
                webViewToDestroy.destroy();
            } catch (RuntimeException e) {
                Log.e("MY_APP_TAG", "Failed to destroy WebView cleanly", e);
            }
        }
    }

    private void clearPdfAndroidJavascriptBridge() {
        if (pdfAndroidJavascriptBridge != null) {
            pdfAndroidJavascriptBridge.cleanUp();
            pdfAndroidJavascriptBridge = null;
        }
    }

    private void restartWebView() {
        Context context = fragment.getContext();
        if (context == null || fragment.getView() == null || webViewContainer == null) {
            destroy(true);
            return;
        }

        destroy();

        try {
            webView = new WebView(context);
            webView.setId(R.id.comments_webview);
            webViewContainer.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            initialize();
        } catch (RuntimeException e) {
            webView = null;
            initializedWebView = false;
            Log.e("MY_APP_TAG", "Failed to recreate WebView", e);
        }
    }

    void onDestroyView(@Nullable View rootView) {
        hideCustomView(false);

        if (rootView != null) {
            rootView.removeCallbacks(initializeWebViewRunnable);
        }
        if (downloadButton != null) {
            downloadButton.setOnClickListener(null);
        }
        if (webViewBackdrop != null) {
            webViewBackdrop.removeCallbacks(webViewBackdropFadeInRunnable);
            webViewBackdrop.animate().cancel();
        }
        destroy();
    }

    private void cancelProgressAnimator() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
            progressAnimator = null;
        }
    }

    void clearViewReferences() {
        swipeRefreshLayout = null;
        bottomSheet = null;
        webView = null;
        webViewStub = null;
        webViewContainer = null;
        fullscreenContainer = null;
        webViewBackdrop = null;
        downloadButton = null;
        progressIndicator = null;
        webViewBottomSheetCallback = null;
        customView = null;
        customViewCallback = null;
        pdfAndroidJavascriptBridge = null;
        currentPdfFilePath = null;
    }

    private class MyWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (!isCurrentWebViewCallback(view)) {
                return;
            }
            beginWebViewLoad(view, url);
            if (!isErrorPageUrl(url)) {
                setReaderModeEnabled(false);
                readerModeDisabledForCurrentPage = false;
                lastRequestedWebViewUrl = url;
            }
            updateReaderModeAvailabilityForLoadStart(url, webViewLoadGeneration);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            super.onPageCommitVisible(view, url);
            if (!isCurrentWebViewCallback(view)) {
                return;
            }
            webViewLoadCommittedVisible = true;
            scheduleVisibleCommitSettle(view);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            if (!isCurrentWebViewCallback(view)) {
                return;
            }
            finishWebViewLoadUi(view, webViewLoadGeneration, true);

            if (retryingFailedWebViewUrl) {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                retryingFailedWebViewUrl = false;
            }

            if (BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                callbacks.syncOnBackPressedCallbackEnabledState();
            }

            if (clearWebViewHistoryOnNextFinish) {
                clearWebViewHistoryOnNextFinish = false;
                view.post(() -> {
                    if (isCurrentWebViewCallback(view)) {
                        view.clearHistory();
                        callbacks.syncOnBackPressedCallbackEnabledState();
                    }
                });
            }

            linkPreviewController.onWebViewPageFinished(fragment.getContext(), view, url);

            int finishedGeneration = webViewLoadGeneration;
            if (readerModePending && !showingErrorPage && !PDF_LOADER_URL.equals(url)) {
                readerModePending = false;
                view.post(() -> {
                    if (isCurrentWebViewCallback(view)) {
                        applyReaderMode(true);
                    }
                });
            } else if (integratedWebview
                    && readerModeDefault
                    && !readerModeDisabledForCurrentPage
                    && !readerModeEnabled
                    && !showingErrorPage
                    && !PDF_LOADER_URL.equals(url)
                    && !isErrorPageUrl(url)) {
                view.post(() -> {
                    if (isCurrentWebViewCallback(view)) {
                        applyReaderMode(true, false);
                    }
                });
            } else {
                view.post(() -> checkReaderModeAvailability(view, finishedGeneration));
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith("intent://")) {
                try {
                    Context context = view.getContext();
                    Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);

                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    if (fallbackUrl != null) {
                        String archiveRedirectUrl = SettingsUtils.getArchiveRedirectUrl(context, fallbackUrl);
                        loadUrl(archiveRedirectUrl != null ? archiveRedirectUrl : fallbackUrl);
                        return true;
                    } else {
                        if (intent.resolveActivity(context.getPackageManager()) != null) {
                            context.startActivity(intent);
                            return true;
                        }
                    }
                } catch (Exception e) {
                    return false;
                }
            }

            String archiveRedirectUrl = SettingsUtils.getArchiveRedirectUrl(view.getContext(), url);
            if (archiveRedirectUrl != null) {
                loadUrl(archiveRedirectUrl);
                return true;
            }

            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return request != null
                    && request.getUrl() != null
                    && shouldOverrideUrlLoading(view, request.getUrl().toString());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (!blockAds) {
                return super.shouldInterceptRequest(view, request);
            }
            ByteArrayInputStream EMPTY = new ByteArrayInputStream("".getBytes());
            if (!Utils.adservers.isEmpty()) {
                String host = request.getUrl().getHost();
                if (host != null && Utils.adservers.contains(host)) {
                    Utils.log("Blocked: " + request.getUrl());
                    return new WebResourceResponse("text/plain", "utf-8", EMPTY);
                }
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            boolean wasCurrentWebView = view == webView;
            if (wasCurrentWebView) {
                destroy(true);
            }

            if (fragment.getContext() == null || fragment.getView() == null || webViewContainer == null) {
                return true;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!detail.didCrash()) {
                    Log.e("MY_APP_TAG", "System killed the WebView rendering process " +
                            "to reclaim memory. Recreating...");

                    if (wasCurrentWebView) {
                        restartWebView();
                    }

                    return true;
                }
            }
            Context context = fragment.getContext();
            if (context != null && wasCurrentWebView) {
                Utils.toast("WebView crashed, reinitializing", context);
                restartWebView();
            }

            Log.e("MY_APP_TAG", "The WebView rendering process crashed!");
            return true;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            ErrorPageType errorPageType = getCustomErrorPageType(errorCode);
            if (errorPageType != null) {
                showCustomErrorPage(view, failingUrl, errorPageType);
            } else {
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            ErrorPageType errorPageType = getCustomErrorPageType(error.getErrorCode());
            if (request.isForMainFrame() && errorPageType != null) {
                showCustomErrorPage(view, request.getUrl() != null ? request.getUrl().toString() : null, errorPageType);
            } else {
                super.onReceivedError(view, request, error);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            String failingUrl = error != null ? error.getUrl() : null;
            if (!TextUtils.isEmpty(failingUrl)
                    && !TextUtils.equals(failingUrl, lastRequestedWebViewUrl)
                    && !TextUtils.equals(failingUrl, view.getUrl())) {
                return;
            }
            showCustomErrorPage(view, failingUrl, ErrorPageType.SSL);
        }
    }

    public static class PdfAndroidJavascriptBridge {
        private final File mFile;
        private @Nullable
        RandomAccessFile mRandomAccessFile;
        private final @Nullable Callbacks mCallback;
        private final Handler mHandler;

        PdfAndroidJavascriptBridge(@NonNull String filePath, @Nullable Callbacks callback) {
            mFile = new File(filePath);
            mCallback = callback;
            mHandler = new Handler(Looper.getMainLooper());
        }

        @JavascriptInterface
        public String getChunk(long begin, long end) {
            try {
                if (mRandomAccessFile == null) {
                    mRandomAccessFile = new RandomAccessFile(mFile, "r");
                }
                final int bufferSize = (int) (end - begin);
                byte[] data = new byte[bufferSize];
                mRandomAccessFile.seek(begin);
                mRandomAccessFile.read(data);
                return Base64.encodeToString(data, Base64.DEFAULT);
            } catch (IOException e) {
                Log.e("Exception", e.toString());
                return "";
            }
        }

        @JavascriptInterface
        public long getSize() {
            return mFile.length();
        }

        @JavascriptInterface
        public void onLoad() {
            if (mCallback != null) {
                mHandler.post(mCallback::onLoad);
            }
        }

        @JavascriptInterface
        public void onFailure() {
            if (mCallback != null) {
                mHandler.post(mCallback::onFailure);
            }
        }

        public void cleanUp() {
            try {
                if (mRandomAccessFile != null) {
                    mRandomAccessFile.close();
                    mRandomAccessFile = null;
                }
            } catch (IOException e) {
                Log.e("Exception", e.toString());
            }
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                cleanUp();
            } finally {
                super.finalize();
            }
        }

        interface Callbacks {
            void onFailure();

            void onLoad();
        }
    }
}
