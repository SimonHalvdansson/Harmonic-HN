package com.simon.harmonichackernews;

import static androidx.webkit.WebViewFeature.isFeatureSupported;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.webkit.WebViewFeature;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.simon.harmonichackernews.adapters.CommentDisplaySettings;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.CommentsScrollProgress;
import com.simon.harmonichackernews.data.PollOption;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.linkpreview.LinkPreviewController;
import com.simon.harmonichackernews.network.AlgoliaFallbackManager;
import com.simon.harmonichackernews.network.ArchiveOrgUrlGetter;
import com.simon.harmonichackernews.network.BackgroundJSONParser;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.SummaryManager;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.CommentSorter;
import com.simon.harmonichackernews.utils.ReferenceLinkRowUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ShareUtils;
import com.simon.harmonichackernews.utils.StatusBarProtectionUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;
import com.simon.harmonichackernews.ui.comments.CommentsComposeController;
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import okhttp3.Response;

public class CommentsFragment extends Fragment {
    private static final String TAG = "CommentsFragment";

    public final static String EXTRA_TITLE = "com.simon.harmonichackernews.EXTRA_TITLE";
    public final static String EXTRA_PDF_TITLE = "com.simon.harmonichackernews.EXTRA_PDF_TITLE";
    public final static String EXTRA_VIDEO_TITLE = "com.simon.harmonichackernews.EXTRA_VIDEO_TITLE";
    public final static String EXTRA_BY = "com.simon.harmonichackernews.EXTRA_BY";
    public final static String EXTRA_URL = "com.simon.harmonichackernews.EXTRA_URL";
    public final static String EXTRA_PREVIEW_IMAGE_URL = "com.simon.harmonichackernews.EXTRA_PREVIEW_IMAGE_URL";
    public final static String EXTRA_PREVIEW_IMAGE_URL_LOADED = "com.simon.harmonichackernews.EXTRA_PREVIEW_IMAGE_URL_LOADED";
    public final static String EXTRA_PREVIEW_IMAGE_LOAD_FAILED = "com.simon.harmonichackernews.EXTRA_PREVIEW_IMAGE_LOAD_FAILED";
    public final static String EXTRA_PREVIEW_IMAGE_TINT_COLOR_LOADED = "com.simon.harmonichackernews.EXTRA_PREVIEW_IMAGE_TINT_COLOR_LOADED";
    public final static String EXTRA_PREVIEW_IMAGE_TINT_COLOR = "com.simon.harmonichackernews.EXTRA_PREVIEW_IMAGE_TINT_COLOR";
    public final static String EXTRA_PREVIEW_IMAGE_TINT_SOURCE_URL = "com.simon.harmonichackernews.EXTRA_PREVIEW_IMAGE_TINT_SOURCE_URL";
    public final static String EXTRA_PREVIEW_IMAGE_TINT_BASE_COLOR = "com.simon.harmonichackernews.EXTRA_PREVIEW_IMAGE_TINT_BASE_COLOR";
    public final static String EXTRA_PREVIEW_IMAGE_TINT_MODE = "com.simon.harmonichackernews.EXTRA_PREVIEW_IMAGE_TINT_MODE";
    public final static String EXTRA_FAVICON_TINT_COLOR_LOADED = "com.simon.harmonichackernews.EXTRA_FAVICON_TINT_COLOR_LOADED";
    public final static String EXTRA_FAVICON_TINT_COLOR = "com.simon.harmonichackernews.EXTRA_FAVICON_TINT_COLOR";
    public final static String EXTRA_FAVICON_TINT_SOURCE_URL = "com.simon.harmonichackernews.EXTRA_FAVICON_TINT_SOURCE_URL";
    public final static String EXTRA_FAVICON_TINT_BASE_COLOR = "com.simon.harmonichackernews.EXTRA_FAVICON_TINT_BASE_COLOR";
    public final static String EXTRA_FAVICON_TINT_MODE = "com.simon.harmonichackernews.EXTRA_FAVICON_TINT_MODE";
    public final static String EXTRA_TIME = "com.simon.harmonichackernews.EXTRA_TIME";
    public final static String EXTRA_KIDS = "com.simon.harmonichackernews.EXTRA_KIDS";
    public final static String EXTRA_POLL_OPTIONS = "com.simon.harmonichackernews.EXTRA_POLL_OPTIONS";
    public final static String EXTRA_DESCENDANTS = "com.simon.harmonichackernews.EXTRA_DESCENDANTS";
    public final static String EXTRA_ID = "com.simon.harmonichackernews.EXTRA_ID";
    public final static String EXTRA_SCORE = "com.simon.harmonichackernews.EXTRA_SCORE";
    public final static String EXTRA_TEXT = "com.simon.harmonichackernews.EXTRA_TEXT";
    public final static String EXTRA_IS_LINK = "com.simon.harmonichackernews.EXTRA_IS_LINK";
    public final static String EXTRA_IS_COMMENT = "com.simon.harmonichackernews.EXTRA_IS_COMMENT";
    public final static String EXTRA_PARENT_ID = "com.simon.harmonichackernews.EXTRA_PARENT_ID";
    public final static String EXTRA_COMMENT_MASTER_ID = "com.simon.harmonichackernews.EXTRA_COMMENT_MASTER_ID";
    public final static String EXTRA_COMMENT_MASTER_TITLE = "com.simon.harmonichackernews.EXTRA_COMMENT_MASTER_TITLE";
    public final static String EXTRA_COMMENT_MASTER_URL = "com.simon.harmonichackernews.EXTRA_COMMENT_MASTER_URL";
    public final static String EXTRA_FORWARD = "com.simon.harmonichackernews.EXTRA_FORWARD";
    public final static String EXTRA_SHOW_WEBSITE = "com.simon.harmonichackernews.EXTRA_SHOW_WEBSITE";
    public final static String EXTRA_SCROLL_TO_COMMENT = "com.simon.harmonichackernews.EXTRA_SCROLL_TO_COMMENT";
    private final static String STATE_COMMENT_ACTION_COMMENT_ID = "com.simon.harmonichackernews.STATE_COMMENT_ACTION_COMMENT_ID";
    private final static String STATE_ADBLOCK_DISABLED_FOR_SESSION = "com.simon.harmonichackernews.STATE_ADBLOCK_DISABLED_FOR_SESSION";
    private final static String STATE_COMMENT_SORTING = "com.simon.harmonichackernews.STATE_COMMENT_SORTING";
    private final static String STATE_REFERENCE_LINK_SUMMARY_URL =
            "com.simon.harmonichackernews.STATE_REFERENCE_LINK_SUMMARY_URL";
    private final static String STATE_REFERENCE_LINK_SUMMARY_TITLE =
            "com.simon.harmonichackernews.STATE_REFERENCE_LINK_SUMMARY_TITLE";
    private final static String STATE_PREVIEW_IMAGE_DIALOG_URL =
            "com.simon.harmonichackernews.STATE_PREVIEW_IMAGE_DIALOG_URL";
    private final static Pattern POLL_TITLE_PATTERN = Pattern.compile("\\bpoll\\b", Pattern.CASE_INSENSITIVE);
    // Keep WebView startup clear of the comments entrance transition. WebView process and
    // renderer initialization can otherwise land on the same frames as the shared transition
    // on physical devices, which makes opening a story feel much heavier than it is.
    private static final long WEBVIEW_BACKGROUND_INITIALIZATION_DELAY_MS = 900L;
    private BottomSheetFragmentCallback callback;
    private List<Comment> comments;
    private List<Comment> allComments;
    private RequestQueue queue;
    private final Object requestTag = new Object();
    private int commentsLoadGeneration = 0;
    @Nullable private PendingCommentsParse pendingCommentsParse;
    @Nullable private CommentsWebViewHost webViewHost;
    private int commentsContentInsetLeft;
    private int commentsContentInsetRight;
    private int pendingCommentActionId = -1;
    @Nullable private String pendingReferenceLinkSummaryUrl;
    @Nullable private String pendingReferenceLinkSummaryTitle;
    @Nullable private String pendingPreviewImageDialogUrl;
    private boolean uncachedStoryHeaderLoading;
    private LinearProgressIndicator progressIndicator;
    private LinkPreviewController linkPreviewController;
    private CommentsWebViewController webViewController;
    private boolean showWebsite = false;
    private boolean integratedWebview = true;
    private boolean prefIntegratedWebview = true;
    private String preloadWebview = "never";
    private int preloadWebviewMinimumBattery = SettingsUtils.DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY;
    private boolean matchWebviewTheme = true;
    private boolean readerModeEnabled = true;
    private boolean readerModeDefault = false;
    private boolean adBlockDisabledForSession = false;
    private boolean pollOptionsLoadStarted = false;
    private boolean pollOptionsLookupStarted = false;
    private boolean closeWebViewOnBack = false;
    private int topInset = 0;
    private long lastLoaded = 0;
    private boolean commentsLoaded;
    private boolean commentsRefreshInProgress;
    private boolean loadingFailed;
    private boolean loadingFailedServerError;
    private boolean showUpdate;
    private boolean storyVoteLoading;
    private boolean storyFavoriteLoading;
    private boolean hasAccountDetails;
    @Nullable private CommentDisplaySettings displaySettings;
    private OnBackPressedCallback backPressedCallback;
    private String username;
    private Story story;
    private Set<String> filteredUsers;
    private int scrollToCommentId = -1;
    private boolean commentsByOpFilterActive = false;
    private int originalStatusBarColor = Color.TRANSPARENT;
    private boolean originalStatusBarColorCaptured = false;
    private int commentsPaneStatusBarColor = Color.TRANSPARENT;
    private float composeHeaderStatusBarCoverage = 0f;
    private int commentsHeaderStatusBarColor = Color.TRANSPARENT;
    private boolean appliedStatusBarProtectionKnown = false;
    private boolean appliedStatusBarProtectionEnabled = false;
    private int appliedStatusBarProtectionColor = Color.TRANSPARENT;
    private String currentCommentSorting;
    @Nullable private CommentsComposeController composeController;

    // Clean fallback management
    private AlgoliaFallbackManager fallbackManager;

    private static final class PendingCommentsParse {
        final int loadGeneration;
        final int storyId;
        @Nullable Runnable completion;
        @Nullable Runnable followUp;
        @Nullable Future<?> future;

        PendingCommentsParse(
                int loadGeneration,
                int storyId,
                @Nullable Runnable completion) {
            this.loadGeneration = loadGeneration;
            this.storyId = storyId;
            this.completion = completion;
        }
    }

    public CommentsFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        webViewHost = new CommentsWebViewHost(inflater.getContext());
        return webViewHost.root;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        filteredUsers = Utils.getFilteredUsers(getContext());

        story = new Story();

        Bundle bundle = getArguments();
        if (hasStoryHeaderArguments(bundle)) {
            story.title = bundle.getString(EXTRA_TITLE);
            story.pdfTitle = bundle.getString(EXTRA_PDF_TITLE, null);
            story.videoTitle = bundle.getString(EXTRA_VIDEO_TITLE, null);
            story.by = bundle.getString(EXTRA_BY);
            story.url = bundle.getString(EXTRA_URL);
            story.previewImageUrl = bundle.getString(EXTRA_PREVIEW_IMAGE_URL);
            story.previewImageUrlLoaded = bundle.getBoolean(EXTRA_PREVIEW_IMAGE_URL_LOADED, !TextUtils.isEmpty(story.previewImageUrl));
            story.previewImageLoadFailed = bundle.getBoolean(EXTRA_PREVIEW_IMAGE_LOAD_FAILED, false);
            story.previewImageTintColorLoaded = bundle.getBoolean(EXTRA_PREVIEW_IMAGE_TINT_COLOR_LOADED, false);
            story.previewImageTintColor = bundle.getInt(EXTRA_PREVIEW_IMAGE_TINT_COLOR, 0);
            story.previewImageTintSourceUrl = bundle.getString(EXTRA_PREVIEW_IMAGE_TINT_SOURCE_URL);
            story.previewImageTintBaseColor = bundle.getInt(EXTRA_PREVIEW_IMAGE_TINT_BASE_COLOR, Color.TRANSPARENT);
            story.previewImageTintMode = bundle.getString(EXTRA_PREVIEW_IMAGE_TINT_MODE);
            story.faviconTintColorLoaded = bundle.getBoolean(EXTRA_FAVICON_TINT_COLOR_LOADED, false);
            story.faviconTintColor = bundle.getInt(EXTRA_FAVICON_TINT_COLOR, 0);
            story.faviconTintSourceUrl = bundle.getString(EXTRA_FAVICON_TINT_SOURCE_URL);
            story.faviconTintBaseColor = bundle.getInt(EXTRA_FAVICON_TINT_BASE_COLOR, Color.TRANSPARENT);
            story.faviconTintMode = bundle.getString(EXTRA_FAVICON_TINT_MODE);
            story.time = bundle.getInt(EXTRA_TIME, 0);
            story.kids = bundle.getIntArray(EXTRA_KIDS);
            story.pollOptions = bundle.getIntArray(EXTRA_POLL_OPTIONS);
            story.descendants = bundle.getInt(EXTRA_DESCENDANTS, 0);
            story.id = bundle.getInt(EXTRA_ID, 0);
            story.score = bundle.getInt(EXTRA_SCORE, 0);
            story.text = bundle.getString(EXTRA_TEXT);
            story.isLink = bundle.getBoolean(EXTRA_IS_LINK, true);
            story.isComment = bundle.getBoolean(EXTRA_IS_COMMENT, false);
            story.parentId = bundle.getInt(EXTRA_PARENT_ID, 0);
            story.commentMasterId = bundle.getInt(EXTRA_COMMENT_MASTER_ID, 0);
            story.commentMasterTitle = bundle.getString(EXTRA_COMMENT_MASTER_TITLE);
            story.commentMasterUrl = bundle.getString(EXTRA_COMMENT_MASTER_URL);
            story.loaded = story.by != null;

            showWebsite = bundle.getBoolean(EXTRA_SHOW_WEBSITE, false);
            scrollToCommentId = bundle.getInt(EXTRA_SCROLL_TO_COMMENT, -1);

        } else {
            story.loaded = false;
            story.id = -1;
        }
    }

    private boolean hasStoryHeaderArguments(@Nullable Bundle bundle) {
        return bundle != null
                && bundle.getInt(EXTRA_ID, -1) > 0
                && bundle.getString(EXTRA_TITLE) != null;
    }

    private void loadInitialStorySummaryFromCache() {
        if (story == null || story.loaded || story.id <= 0) {
            return;
        }

        Utils.loadCachedStorySummary(getContext(), story);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            pendingReferenceLinkSummaryUrl = savedInstanceState.getString(STATE_REFERENCE_LINK_SUMMARY_URL);
            pendingReferenceLinkSummaryTitle = savedInstanceState.getString(STATE_REFERENCE_LINK_SUMMARY_TITLE);
            pendingPreviewImageDialogUrl = savedInstanceState.getString(STATE_PREVIEW_IMAGE_DIALOG_URL);
        }
        CommentsWebViewHost host = webViewHost;
        if (host == null) {
            throw new IllegalStateException("Comments WebView host was not created");
        }
        topInset = 0;

        if (savedInstanceState != null) {
            pendingCommentActionId = savedInstanceState.getInt(
                    STATE_COMMENT_ACTION_COMMENT_ID,
                    -1);
            adBlockDisabledForSession = savedInstanceState.getBoolean(STATE_ADBLOCK_DISABLED_FOR_SESSION, false);
            currentCommentSorting = savedInstanceState.getString(STATE_COMMENT_SORTING);
        }

        if (TextUtils.isEmpty(currentCommentSorting)) {
            currentCommentSorting = SettingsUtils.getPreferredCommentSorting(getContext());
        }

        if (getActivity() instanceof BottomSheetFragmentCallback) {
            callback = (BottomSheetFragmentCallback) getActivity();
        }
        originalStatusBarColor = requireActivity().getWindow().getStatusBarColor();
        originalStatusBarColorCaptured = true;

        prefIntegratedWebview = SettingsUtils.shouldUseIntegratedWebView(getContext());
        loadInitialStorySummaryFromCache();
        uncachedStoryHeaderLoading = story.id > 0 && !story.loaded;

        commentsPaneStatusBarColor = StatusBarProtectionUtils.getPaneBackgroundColor(requireContext());
        commentsHeaderStatusBarColor = commentsPaneStatusBarColor;
        appliedStatusBarProtectionKnown = false;
        updateCommentsStatusBarAppearance();

        integratedWebview = prefIntegratedWebview && story.isLink;
        preloadWebview = SettingsUtils.shouldPreloadWebView(getContext());
        preloadWebviewMinimumBattery = SettingsUtils.getPreloadWebViewMinimumBattery(getContext());
        matchWebviewTheme = SettingsUtils.shouldMatchWebViewTheme(getContext());
        readerModeEnabled = SettingsUtils.shouldUseReaderMode(getContext());
        readerModeDefault = SettingsUtils.shouldUseReaderModeByDefault(getContext());
        boolean blockAds = SettingsUtils.shouldBlockAds(getContext()) && !adBlockDisabledForSession;
        closeWebViewOnBack = SettingsUtils.shouldCloseWebViewOnBack(getContext());

        progressIndicator = host.progressIndicator;
        linkPreviewController = new LinkPreviewController(story, CommentsFragment.this::onLinkPreviewChanged);
        webViewController = new CommentsWebViewController(this, story, linkPreviewController, new CommentsWebViewController.Callbacks() {
            @Override
            public void onSwitchView(boolean isAtWebView) {
                if (callback != null) {
                    callback.onSwitchView(isAtWebView);
                }
            }

            @Override
            public void syncOnBackPressedCallbackEnabledState() {
                CommentsFragment.this.syncOnBackPressedCallbackEnabledState();
            }

            @Override
            public void onReaderModeChanged(boolean enabled) {
                if (composeController != null && webViewController != null) {
                    composeController.updateReaderMode(
                            webViewController.isReaderModeAvailable(), enabled);
                }
            }

            @Override
            public void onReaderModeAvailabilityChanged(boolean available) {
                if (composeController != null && webViewController != null) {
                    composeController.updateReaderMode(
                            available, webViewController.isReaderModeEnabled());
                }
            }

            @Override
            public void onFullscreenChanged(boolean fullscreen) {
                if (composeController != null) {
                    composeController.updateWebViewFullscreen(fullscreen);
                }
            }
        });
        webViewController.bindViews(host, null, progressIndicator);
        webViewController.configure(showWebsite, integratedWebview, preloadWebview, preloadWebviewMinimumBattery, matchWebviewTheme, readerModeEnabled, readerModeDefault, blockAds);

        if (story.id <= 0 && story.title == null) {
            // Empty view for tablets
            webViewController.setContainerVisibility(View.GONE);

            return;
        }

        backPressedCallback = new OnBackPressedCallback(true) {

            @Override
            public void handleOnBackCancelled() {
                if (composeController != null
                        && composeController.isLinkPreviewOverlayShowing()) {
                    composeController.cancelLinkPreviewPredictiveBack();
                    return;
                }
                if (composeController != null
                        && composeController.isCommentActionOverlayShowing()) {
                    composeController.cancelCommentActionPredictiveBack();
                    return;
                }

                if (willExpandBottomSheetOnBack()) {
                    endCommentsPredictiveBackVisuals();
                }
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                if (composeController != null
                        && composeController.isLinkPreviewOverlayShowing()) {
                    composeController.updateLinkPreviewPredictiveBack(
                            backEvent.getProgress(),
                            backEvent.getSwipeEdge(),
                            backEvent.getTouchY());
                    return;
                }
                if (composeController != null
                        && composeController.isCommentActionOverlayShowing()) {
                    composeController.updateCommentActionPredictiveBack(
                            backEvent.getProgress(),
                            backEvent.getSwipeEdge(),
                            backEvent.getTouchY());
                    return;
                }

                if (willExpandBottomSheetOnBack()) {
                    updateCommentsPredictiveBackVisuals(backEvent.getProgress(), false);
                }
            }

            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                if (composeController != null
                        && composeController.isLinkPreviewOverlayShowing()) {
                    composeController.startLinkPreviewPredictiveBack(
                            backEvent.getProgress(),
                            backEvent.getSwipeEdge(),
                            backEvent.getTouchY());
                    return;
                }
                if (composeController != null
                        && composeController.isCommentActionOverlayShowing()) {
                    composeController.updateCommentActionPredictiveBack(
                            backEvent.getProgress(),
                            backEvent.getSwipeEdge(),
                            backEvent.getTouchY());
                    return;
                }

                if (willExpandBottomSheetOnBack()) {
                    updateCommentsPredictiveBackVisuals(backEvent.getProgress(), true);
                }
            }

            @Override
            public void handleOnBackPressed() {
                if (composeController != null
                        && composeController.isLinkPreviewOverlayShowing()) {
                    if (composeController.isLinkPreviewPredictiveBackActive()) {
                        composeController.commitLinkPreviewPredictiveBack();
                    } else {
                        composeController.requestDismissLinkPreview();
                    }
                    return;
                }
                if (composeController != null
                        && composeController.isCommentActionOverlayShowing()) {
                    if (composeController.isCommentActionPredictiveBackActive()) {
                        composeController.commitCommentActionPredictiveBack();
                        return;
                    }
                    composeController.requestDismissCommentActions();
                    return;
                }

                if (webViewController.isShowingCustomView()) {
                    webViewController.hideCustomView(true);
                    return;
                }

                boolean webViewVisible = composeController != null
                        && composeController.isWebsiteVisible();
                if (webViewVisible && webViewController.isReaderModeEnabled()) {
                    webViewController.disableReaderMode();
                    return;
                } else if (willExpandBottomSheetOnBack()) {
                    // If the webView can't go back but the back handler is enabled,
                    // it means that the closeWebViewOnBack == true
                    composeController.requestExpandSheet();
                    endCommentsPredictiveBackVisuals();
                    return;
                } else if (webViewVisible) {
                    webViewController.goBackFromVisibleWebView();
                    return;
                }

                if (requireActivity() instanceof MainActivity) {
                    ((MainActivity) requireActivity()).closeStory();
                } else {
                    requireActivity().finish();
                }
            }

            private boolean willExpandBottomSheetOnBack() {
                boolean webViewVisible = composeController != null
                        && composeController.isWebsiteVisible();
                return webViewVisible && webViewController.willExpandBottomSheetOnBack();
            }
        };

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);

        // This is how much the bottom sheet sticks up by default and also decides height of WebView
        // We want to watch for navigation bar height changes (tablets on Android 12L can cause
        // these)

        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets systemInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                topInset = systemInsets.top;
                updateBottomSheetMargin(systemInsets.bottom);

                Insets cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
                int contentPaddingLeft = 0;
                int contentPaddingRight = 0;
                if (Utils.isTablet(getResources())) {
                    if (requireActivity() instanceof MainActivity) {
                        contentPaddingRight = getResources().getDimensionPixelSize(R.dimen.extra_pane_padding);
                    } else {
                        int singleViewSideMargin = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);
                        contentPaddingLeft = singleViewSideMargin;
                        contentPaddingRight = singleViewSideMargin;
                    }
                }
                int leftPadding = Math.max(Math.max(cutoutInsets.left, systemInsets.left), contentPaddingLeft);
                int rightPadding = Math.max(Math.max(cutoutInsets.right, systemInsets.right), contentPaddingRight);
                setCommentsContentSideInsets(leftPadding, rightPadding);

                webViewController.setContainerPadding(0, systemInsets.top, 0, 0);

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(view);

        syncOnBackPressedCallbackEnabledState();

        if (callback != null) {
            callback.onSwitchView(showWebsite);
        }

        progressIndicator = host.progressIndicator;

        boolean shouldInitializeWebViewBeforeFirstDraw = integratedWebview && showWebsite;
        boolean shouldInitializeWebViewInBackground = integratedWebview
                && !showWebsite
                && webViewController.shouldInitializeInBackground(requireContext());

        if (shouldInitializeWebViewBeforeFirstDraw) {
            webViewController.initialize();
        }

        // The pane color was already resolved from the active theme above. Reusing it avoids two
        // cold theme/preference/resource lookups on the comments-open frame.
        webViewController.setContainerBackgroundColor(commentsPaneStatusBarColor);

        comments = new ArrayList<>();
        Comment headerComment = new Comment();
        comments.add(headerComment); // header
        allComments = new ArrayList<>();
        allComments.add(headerComment);

        username = AccountUtils.getAccountUsername(getContext());
        hasAccountDetails = AccountUtils.hasAccountDetails(requireContext());
        displaySettings = createCommentDisplaySettings();

        initializeComposeUi();

        boolean restoreScrollFromCache = !showWebsite;

        // Navigation Compose owns the screen transition. Do not hold the fragment's first frame
        // behind the old inset-gated postponed transition; render the header skeleton immediately
        // and start loading on the next main-loop turn.
        view.post(() -> {
            if (getView() != view || !isAdded()) {
                return;
            }
            loadInitialStoryAndComments(restoreScrollFromCache);
        });
        if (shouldInitializeWebViewInBackground && webViewController != null) {
            view.postDelayed(
                    webViewController.getInitializeRunnable(),
                    WEBVIEW_BACKGROUND_INITIALIZATION_DELAY_MS);
        }
    }

    private void updateCommentsPredictiveBackVisuals(float progress, boolean started) {
        if (composeController != null) {
            if (started) {
                composeController.beginPredictiveBack(progress);
            } else {
                composeController.updatePredictiveBack(progress);
            }
            return;
        }
    }

    private void endCommentsPredictiveBackVisuals() {
        if (composeController != null) {
            composeController.endPredictiveBack();
            return;
        }
    }

    private void initializeComposeUi() {
        if (webViewHost == null || story == null) {
            return;
        }
        composeController = CommentsComposeController.create(
                (androidx.appcompat.app.AppCompatActivity) requireActivity(),
                story,
                showWebsite,
                username,
                new CommentsComposeController.Listener() {
                    @Override
                    public void onToggleComment(@NonNull Comment comment, int position) {
                        toggleCommentExpanded(comment, position);
                    }

                    @Override
                    public void onCommentAction(@NonNull Comment comment, int action) {
                        handleComposeCommentAction(comment, action);
                    }

                    @Override
                    public void onCommentActionOverlayVisibilityChanged(boolean showing) {
                        syncOnBackPressedCallbackEnabledState();
                        updateCommentsStatusBarAppearance();
                    }

                    @Override
                    public void onLinkPreviewOverlayVisibilityChanged(boolean showing) {
                        syncOnBackPressedCallbackEnabledState();
                    }

                    @Override
                    public void onHeaderClick() {
                        if (story != null && story.isLink) {
                            Utils.launchCustomTab(getActivity(), story.url);
                        }
                    }

                    @Override
                    public void onHeaderAction(int action) {
                        if (action == CommentsComposeController.HEADER_ACTION_USER) {
                            clickUser();
                        } else if (action == CommentsComposeController.HEADER_ACTION_REPLY) {
                            clickComment();
                        } else if (action == CommentsComposeController.HEADER_ACTION_VOTE) {
                            clickVote();
                        } else if (action == CommentsComposeController.HEADER_ACTION_FAVORITE) {
                            clickFavorite();
                        } else if (action == CommentsComposeController.HEADER_ACTION_BOOKMARK) {
                            toggleStoryBookmark();
                            syncComposeState();
                        } else if (action == CommentsComposeController.HEADER_ACTION_SUMMARIZE) {
                            requestComposeSummary();
                        } else if (action == CommentsComposeController.HEADER_ACTION_REFRESH) {
                            onRetry();
                        }
                    }

                    @Override
                    public void onShareAction(int action) {
                        shareFromCompose(action);
                    }

                    @Override
                    public void onMoreAction(int action) {
                        handleComposeMoreAction(action);
                    }

                    @Override
                    public void onSearchResultSelected(@NonNull Comment comment) {
                        selectComposeSearchResult(comment);
                    }

                    @Override
                    public void onSortComments(@NonNull String sortType) {
                        changeCommentSorting(sortType);
                    }

                    @Override
                    public void onSheetAction(int action) {
                        handleComposeSheetAction(action);
                    }

                    @Override
                    public void onCollapseSheetForWebsite() {
                        collapseBottomSheetForWebsite();
                    }

                    @Override
                    public void onSheetProgressChanged(float expandedFraction) {
                        if (expandedFraction < 0.999f
                                && integratedWebview
                                && webViewController != null
                                && !webViewController.hasWebView()) {
                            webViewController.initializeForVisibleWebsite();
                        }
                        updateCommentsStatusBarAppearance();
                    }

                    @Override
                    public void onSheetSettled(boolean expanded) {
                        if (!expanded
                                && integratedWebview
                                && webViewController != null
                                && !webViewController.hasWebView()) {
                            webViewController.initializeForVisibleWebsite();
                        }
                        if (callback != null) {
                            callback.onSwitchView(!expanded);
                        }
                        syncOnBackPressedCallbackEnabledState();
                        updateCommentsStatusBarAppearance();
                    }

                    @Override
                    public void onHeaderColorChanged(int color) {
                        updateHeaderStatusBarColor(color);
                    }

                    @Override
                    public void onHeaderCoverageChanged(float coverage) {
                        composeHeaderStatusBarCoverage = Math.max(0f, Math.min(1f, coverage));
                        updateCommentsStatusBarAppearance();
                    }

                    @Override
                    public void onPollOption(int optionId) {
                        UserActions.votePollOption(requireContext(), optionId);
                    }
                });
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).attachCommentsComposeController(composeController);
        }
        restoreLinkSummaryAfterRecreation();
        syncComposeState();
    }

    private void syncComposeState() {
        CommentsComposeController controller = composeController;
        if (controller == null || story == null || comments == null) {
            return;
        }
        boolean readerAvailable = webViewController != null && webViewController.isReaderModeAvailable();
        boolean readerEnabled = webViewController != null && webViewController.isReaderModeEnabled();
        if (displaySettings == null) {
            displaySettings = createCommentDisplaySettings();
        }
        controller.updateContent(
                story,
                comments,
                displaySettings,
                commentsLoaded,
                commentsRefreshInProgress,
                loadingFailed,
                loadingFailedServerError,
                showUpdate,
                commentsByOpFilterActive,
                hasCommentsByOp(),
                webViewController != null && webViewController.isBlockingAds(),
                integratedWebview,
                readerAvailable,
                readerEnabled,
                getCurrentCommentSorting(),
                topInset,
                commentsContentInsetLeft,
                commentsContentInsetRight,
                storyVoteLoading,
                storyFavoriteLoading);
    }

    private void requestComposeSummary() {
        CommentsComposeController controller = composeController;
        if (controller == null) {
            return;
        }
        controller.updateStorySummaryLoading(true);
        onRequest(controller::refreshContent, () -> {
            controller.updateStorySummaryLoading(false);
            controller.refreshContent();
        });
    }

    private void shareFromCompose(int action) {
        if (story == null) {
            return;
        }
        Intent shareIntent;
        if (action == CommentsComposeController.SHARE_ARTICLE) {
            shareIntent = ShareUtils.getShareIntent(story.url);
        } else if (action == CommentsComposeController.SHARE_ARTICLE_TITLE) {
            shareIntent = ShareUtils.getShareIntentWithTitle(story.title, story.url);
        } else if (action == CommentsComposeController.SHARE_HN) {
            shareIntent = ShareUtils.getShareIntent(story.id);
        } else if (action == CommentsComposeController.SHARE_ALL) {
            shareIntent = ShareUtils.getShareIntentWithTitle(story.title, story.id, story.url);
        } else {
            shareIntent = ShareUtils.getShareIntentWithTitle(story.title, story.id);
        }
        startActivity(shareIntent);
    }

    private void handleComposeMoreAction(int action) {
        if (story == null) {
            return;
        }
        if (action == CommentsComposeController.MORE_REFRESH) {
            onRetry();
        } else if (action == CommentsComposeController.MORE_OPEN_PARENT && story.parentId > 0) {
            Utils.openCommentsActivity(story.parentId, -1, requireContext());
        } else if (action == CommentsComposeController.MORE_OPEN_TOP_LEVEL && story.commentMasterId > 0) {
            Utils.openCommentsActivity(story.commentMasterId, -1, requireContext());
        } else if (action == CommentsComposeController.MORE_TOGGLE_BOOKMARK) {
            toggleStoryBookmark();
            syncComposeState();
        } else if (action == CommentsComposeController.MORE_SEARCH) {
            showComposeCommentSearch();
        } else if (action == CommentsComposeController.MORE_COMMENTS_BY_OP) {
            if (commentsByOpFilterActive) {
                resetCommentsByOpFilter();
            } else {
                showCommentsByOp();
            }
        } else if (action == CommentsComposeController.MORE_OPEN_BROWSER) {
            onOpenInBrowser();
        } else if (action == CommentsComposeController.MORE_DISABLE_ADBLOCK) {
            adBlockDisabledForSession = true;
            webViewController.disableAdBlockAndReload();
        } else if (action == CommentsComposeController.MORE_ARCHIVE_ORG) {
            openArchiveOrg();
        } else if (action == CommentsComposeController.MORE_ARCHIVE_IS) {
            openArchiveIs();
        } else if (action == CommentsComposeController.MORE_ARCHIVE_TODAY) {
            openArchiveToday();
        }
    }

    private void showComposeCommentSearch() {
        resetCommentsByOpFilter();
        if (composeController != null) {
            composeController.showCommentSearch();
        }
    }

    private void selectComposeSearchResult(@NonNull Comment comment) {
        expandParentsForComment(comment);
        if (composeController != null) {
            syncComposeState();
            composeController.scrollToSearchResult(comment.id);
        }
    }

    private void handleComposeSheetAction(int action) {
        if (webViewController == null) {
            return;
        }
        if (action == CommentsComposeController.SHEET_REFRESH) {
            if (webViewController.isShowingOfflineOrCachedPage() && webViewController.hasLastFailedUrl()) {
                webViewController.retryLastFailedUrl();
            } else {
                webViewController.reload();
            }
        } else if (action == CommentsComposeController.SHEET_EXPAND) {
            if (composeController != null) {
                composeController.requestExpandSheet();
            }
        } else if (action == CommentsComposeController.SHEET_BROWSER) {
            clickBrowser();
        } else if (action == CommentsComposeController.SHEET_READER) {
            webViewController.toggleReaderMode();
        } else if (action == CommentsComposeController.SHEET_INVERT) {
            webViewController.toggleDarkMode();
        }
    }

    private void handleComposeCommentAction(@NonNull Comment comment, int action) {
        if (!isAdded() || composeController == null) {
            return;
        }
        Context ctx = requireContext();
        if (action == CommentsComposeController.COMMENT_ACTION_USER) {
            if (!TextUtils.isEmpty(comment.by)) {
                ((MainActivity) requireActivity()).showUserDialog(
                        comment.by,
                        () -> updateUserTags(comment.by));
            }
            return;
        }
        if (action == CommentsComposeController.COMMENT_ACTION_SHARE) {
            startActivity(ShareUtils.getShareIntent(comment.id));
            return;
        }
        if (action == CommentsComposeController.COMMENT_ACTION_COPY) {
            ClipboardManager clipboard = (ClipboardManager)
                    ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        "Hacker News comment",
                        Html.fromHtml(comment.text == null ? "" : comment.text,
                                Html.FROM_HTML_MODE_LEGACY)));
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(ctx, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
        if (action == CommentsComposeController.COMMENT_ACTION_BOOKMARK) {
            if (Utils.isBookmarked(ctx, comment.id)) {
                Utils.removeBookmark(ctx, comment.id);
            } else {
                Utils.addBookmark(ctx, comment.id);
            }
            composeController.refreshCommentActionState();
            return;
        }
        if (action == CommentsComposeController.COMMENT_ACTION_REPLY) {
            if (!AccountUtils.hasAccountDetails(ctx)) {
                AccountUtils.showLoginPrompt(ctx);
                return;
            }
            if (Utils.timeInSecondsMoreThanTwoWeeksAgo(comment.time)) {
                Toast.makeText(ctx, "This comment is too old to reply to", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent replyIntent = ComposeEditorContract.createIntent(ctx);
            replyIntent.putExtra(ComposeEditorContract.EXTRA_ID, comment.id);
            replyIntent.putExtra(ComposeEditorContract.EXTRA_PARENT_TEXT, comment.text);
            replyIntent.putExtra(
                    ComposeEditorContract.EXTRA_POST_TITLE,
                    story == null ? null : story.title);
            replyIntent.putExtra(ComposeEditorContract.EXTRA_USER, comment.by);
            replyIntent.putExtra(
                    ComposeEditorContract.EXTRA_TYPE,
                    ComposeEditorContract.TYPE_COMMENT_REPLY);
            startActivity(replyIntent);
            return;
        }

        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(ctx);
            return;
        }
        if (action == CommentsComposeController.COMMENT_ACTION_FAVORITE) {
            boolean oldFavorited = Utils.isFavorited(ctx, comment.id);
            boolean newFavorited = !oldFavorited;
            composeController.setCommentActionFavoriteLoading(comment.id, true);
            UserActions.setFavorite(ctx, comment.id, newFavorited,
                    new UserActions.ActionCallback() {
                        @Override
                        public void onSuccess(Response response) {
                            if (composeController != null) {
                                composeController.setCommentActionFavoriteLoading(
                                        comment.id, false);
                            }
                        }

                        @Override
                        public void onFailure(String summary, String response) {
                            Utils.setFavorite(ctx, comment.id, oldFavorited);
                            if (composeController != null) {
                                composeController.setCommentActionFavoriteLoading(
                                        comment.id, false);
                            }
                            UserActions.showFailureDetailDialog(ctx, summary, response);
                            Toast.makeText(
                                    ctx,
                                    "Couldn't update favorite",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
            return;
        }

        if (action != CommentsComposeController.COMMENT_ACTION_UPVOTE
                && action != CommentsComposeController.COMMENT_ACTION_DOWNVOTE
                && action != CommentsComposeController.COMMENT_ACTION_UNVOTE) {
            return;
        }
        if (composeController.isCommentActionVoteLoading(comment.id)) {
            return;
        }
        boolean wasUpvoted = Utils.isUpvoted(ctx, comment.id, true);
        boolean wasDownvoted = !wasUpvoted
                && composeController.isCommentActionDownvoted(comment.id);
        composeController.setCommentActionVoteLoading(comment.id, action);
        UserActions.ActionCallback callback = new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                boolean upvoted = action == CommentsComposeController.COMMENT_ACTION_UPVOTE;
                boolean downvoted = action == CommentsComposeController.COMMENT_ACTION_DOWNVOTE;
                Utils.setUpvoted(ctx, comment.id, true, upvoted);
                if (composeController != null) {
                    composeController.finishCommentActionVote(comment.id, downvoted);
                }
            }

            @Override
            public void onFailure(String summary, String response) {
                Utils.setUpvoted(ctx, comment.id, true, wasUpvoted);
                if (composeController != null) {
                    composeController.finishCommentActionVote(comment.id, wasDownvoted);
                }
            }
        };
        if (action == CommentsComposeController.COMMENT_ACTION_UPVOTE) {
            UserActions.upvote(ctx, comment.id, callback);
        } else if (action == CommentsComposeController.COMMENT_ACTION_DOWNVOTE) {
            UserActions.downvote(ctx, comment.id, callback);
        } else {
            UserActions.unvote(ctx, comment.id, callback);
        }
    }

    private void syncOnBackPressedCallbackEnabledState() {
        if (backPressedCallback == null) {
            return;
        }
        if (composeController != null
                && (composeController.isLinkPreviewOverlayShowing()
                || composeController.isCommentActionOverlayShowing())) {
            backPressedCallback.setEnabled(true);
            return;
        }
        if (webViewController != null && webViewController.isShowingCustomView()) {
            backPressedCallback.setEnabled(true);
            return;
        }
        boolean webViewVisible = webViewController != null
                && webViewController.hasWebView()
                && composeController != null
                && composeController.isWebsiteVisible();
        if (webViewVisible && webViewController.isReaderModeEnabled()) {
            backPressedCallback.setEnabled(true);
            return;
        }
        if (closeWebViewOnBack) {
            backPressedCallback.setEnabled(webViewVisible);
        } else {
            backPressedCallback.setEnabled(webViewController != null && webViewController.canGoBack());
        }
    }

    private void updateBottomSheetMargin(int navbarHeight) {
        int standardMargin = Utils.pxFromDpInt(getResources(), Utils.isTablet(getResources()) ? 81 : 68);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(0, 0, 0, standardMargin + navbarHeight);

        webViewController.setContainerLayoutParams(params);

    }

    private void updateHeaderStatusBarColor(int color) {
        commentsHeaderStatusBarColor = color;
        updateCommentsStatusBarAppearance();
    }

    private void syncCommentsStatusBarProtection() {
        if (!isAdded()) {
            return;
        }
        commentsPaneStatusBarColor = StatusBarProtectionUtils.getPaneBackgroundColor(requireContext());
        updateCommentsStatusBarAppearance();
    }

    private void updateCommentsStatusBarAppearance() {
        updateCommentsStatusBarAppearance(getCurrentCommentsStatusBarColor());
    }

    private void updateCommentsStatusBarAppearance(int commentsStatusBarColor) {
        CommentsWebViewHost host = webViewHost;
        if (host == null || getContext() == null) {
            return;
        }

        boolean showStatusBarProtection = shouldShowCommentsStatusBarProtection();
        boolean statusBarProtectionEnabled = showStatusBarProtection;
        int statusBarColor = showStatusBarProtection ? commentsStatusBarColor : commentsPaneStatusBarColor;
        if (!appliedStatusBarProtectionKnown
                || appliedStatusBarProtectionEnabled != statusBarProtectionEnabled
                || (statusBarProtectionEnabled && appliedStatusBarProtectionColor != statusBarColor)) {
            StatusBarProtectionUtils.setTopProtection(
                    host.root,
                    statusBarProtectionEnabled,
                    statusBarColor);
            appliedStatusBarProtectionKnown = true;
            appliedStatusBarProtectionEnabled = statusBarProtectionEnabled;
            appliedStatusBarProtectionColor = statusBarProtectionEnabled ? statusBarColor : Color.TRANSPARENT;
        }
        if (getActivity() == null) {
            return;
        }
        int windowStatusBarColor = SettingsUtils.shouldUseTransparentStatusBar(requireContext())
                ? Color.TRANSPARENT
                : statusBarColor;
        if (requireActivity().getWindow().getStatusBarColor() != windowStatusBarColor) {
            requireActivity().getWindow().setStatusBarColor(windowStatusBarColor);
        }
    }

    private boolean shouldShowCommentsStatusBarProtection() {
        return isBottomSheetFullyExpanded();
    }

    public boolean isBottomSheetFullyExpanded() {
        return composeController != null && composeController.isSheetExpanded();
    }

    public boolean switchStoryViewIfMatching(int storyId, boolean showWebsite) {
        if (!isAdded()
                || story == null
                || story.id != storyId
                || !integratedWebview
                || webViewController == null) {
            return false;
        }
        if (showWebsite) {
            webViewController.initialize();
            if (composeController != null) {
                composeController.requestWebsite();
                return true;
            }
            scrollCommentsToTopThenCollapseBottomSheet();
        } else {
            if (composeController != null) {
                composeController.requestStopScroll();
                composeController.requestExpandSheet();
            }
        }
        return true;
    }

    private void scrollCommentsToTopThenCollapseBottomSheet() {
        if (composeController != null) {
            composeController.requestWebsite();
        } else {
            collapseBottomSheetForWebsite();
        }
    }

    private void collapseBottomSheetForWebsite() {
        if (webViewController != null) {
            webViewController.initialize();
        }
        if (composeController != null) {
            composeController.requestCollapseSheet();
        }
    }

    private int getCurrentCommentsStatusBarColor() {
        float headerCoverage = getHeaderStatusBarCoverage();
        return ColorUtils.blendARGB(commentsPaneStatusBarColor, commentsHeaderStatusBarColor, headerCoverage);
    }

    private float getHeaderStatusBarCoverage() {
        if (composeController != null) {
            return composeHeaderStatusBarCoverage;
        }
        return 0f;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // this is to make sure that action buttons in header get updated padding on rotations...
        // yes it's ugly, I know
        if (getContext() != null && Utils.isTablet(getResources())) {
            displaySettings = createCommentDisplaySettings();
            notifyHeaderChanged();
        }
    }

    private void toggleCommentExpanded(@NonNull Comment comment, int index) {
        if (comments == null) {
            return;
        }
        comment.expanded = !comment.expanded;
        syncComposeState();
    }

    private void setCommentsContentSideInsets(int leftInset, int rightInset) {
        int safeLeftInset = Math.max(0, leftInset);
        int safeRightInset = Math.max(0, rightInset);
        if (commentsContentInsetLeft == safeLeftInset
                && commentsContentInsetRight == safeRightInset) {
            return;
        }

        commentsContentInsetLeft = safeLeftInset;
        commentsContentInsetRight = safeRightInset;

        syncComposeState();
    }

    private CommentDisplaySettings createCommentDisplaySettings() {
        Context context = requireContext();
        return CommentDisplaySettings.from(
                context,
                shouldShowInvertAction(),
                Utils.isTablet(getResources()),
                hasAccountDetails,
                story != null && story.isLink && Utils.canProvideSummary(context));
    }

    private boolean shouldShowInvertAction() {
        return isFeatureSupported(WebViewFeature.FORCE_DARK)
                || WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING);
    }


    @Override
    public void onStart() {
        super.onStart();

        if (callback == null && getActivity() instanceof BottomSheetFragmentCallback) {
            callback = (BottomSheetFragmentCallback) getActivity();
        }

        if (callback != null) {
            callback.onSwitchView(composeController != null
                    && composeController.isWebsiteVisible());
        }

        Context ctx = requireContext();
        hasAccountDetails = AccountUtils.hasAccountDetails(ctx);
        CommentDisplaySettings latestSettings = createCommentDisplaySettings();
        boolean themeChanged = displaySettings != null
                && !TextUtils.equals(displaySettings.theme, latestSettings.theme);
        displaySettings = latestSettings;
        if (themeChanged) {
            int backgroundColor = ContextCompat.getColor(
                    ctx, ThemeUtils.getBackgroundColorResource(ctx));
            if (webViewController != null) {
                webViewController.setContainerBackgroundColor(backgroundColor);
            }
        }
        syncComposeState();
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean shouldShowUpdate = SettingsUtils.shouldAlwaysShowTapToRefresh(getContext())
                || (lastLoaded != 0 && (System.currentTimeMillis() - lastLoaded) > 1000 * 60 * 60 && !Utils.timeInSecondsMoreThanTwoHoursAgo(story.time));
        if (showUpdate != shouldShowUpdate) {
            showUpdate = shouldShowUpdate;
            if (showUpdate && composeController != null) {
                composeController.clearSearchScrollTopTarget();
            }
        }
        syncCommentsStatusBarProtection();
        syncComposeState();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (composeController == null || story == null) {
            return;
        }
        if (MainActivity.commentsScrollProgresses == null) {
            MainActivity.commentsScrollProgresses = new ArrayList<>();
        }
        CommentsScrollProgress recordedProgress = recordScrollProgress();
        for (int i = 0; i < MainActivity.commentsScrollProgresses.size(); i++) {
            CommentsScrollProgress scrollProgress = MainActivity.commentsScrollProgresses.get(i);
            if (scrollProgress.storyId == story.id) {
                MainActivity.commentsScrollProgresses.set(i, recordedProgress);
                return;
            }
        }
        MainActivity.commentsScrollProgresses.add(recordedProgress);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (composeController != null && composeController.isLinkPreviewReferenceShowing()) {
            outState.putString(STATE_REFERENCE_LINK_SUMMARY_URL,
                    composeController.getLinkPreviewVisibleUrl());
            outState.putString(STATE_REFERENCE_LINK_SUMMARY_TITLE,
                    composeController.getLinkPreviewFallbackTitle());
        } else if (composeController != null && composeController.isLinkPreviewImageShowing()) {
            outState.putString(STATE_PREVIEW_IMAGE_DIALOG_URL,
                    composeController.getLinkPreviewVisibleUrl());
        }

        int visibleCommentActionId = composeController == null
                ? pendingCommentActionId
                : composeController.getVisibleCommentActionId();
        if (visibleCommentActionId != -1) {
            outState.putInt(STATE_COMMENT_ACTION_COMMENT_ID, visibleCommentActionId);
        }
        if (adBlockDisabledForSession) {
            outState.putBoolean(STATE_ADBLOCK_DISABLED_FOR_SESSION, true);
        }
        outState.putString(STATE_COMMENT_SORTING, getCurrentCommentSorting());
    }

    private CommentsScrollProgress recordScrollProgress() {
        CommentsScrollProgress scrollProgress = new CommentsScrollProgress();

        scrollProgress.storyId = story.id;
        if (composeController != null) {
            scrollProgress.topCommentId = composeController.getFirstVisibleCommentId();
            // LazyColumn exposes how far the first visible item has scrolled past the top.
            scrollProgress.topCommentOffset = -composeController.getFirstVisibleCommentOffset();
        }

        scrollProgress.collapsedIDs = new HashSet<>();

        for (Comment c : comments) {
            if (!c.expanded) {
                scrollProgress.collapsedIDs.add(c.id);
            }
        }

        return scrollProgress;
    }

    private void restoreScrollProgress(CommentsScrollProgress scrollProgress) {
        for (int i = 0; i < comments.size(); i++) {
            Comment c = comments.get(i);
            c.expanded = !scrollProgress.collapsedIDs.contains(c.id);
        }
        if (composeController != null) {
            syncComposeState();
            composeController.scrollToComment(
                    scrollProgress.topCommentId,
                    scrollProgress.topCommentOffset,
                    false);
        }
    }

    private void scrollToTargetComment() {
        if (scrollToCommentId == -1) return;
        for (int i = 0; i < comments.size(); i++) {
            if (comments.get(i).id == scrollToCommentId) {
                expandParentsForComment(comments.get(i));
                if (composeController != null) {
                    syncComposeState();
                    composeController.scrollToComment(scrollToCommentId, topInset, false);
                    scrollToCommentId = -1;
                    return;
                }
                scrollToCommentId = -1;
                return;
            }
        }
        Toast.makeText(getContext(), "Comment not found", Toast.LENGTH_SHORT).show();
        scrollToCommentId = -1;
    }

    private void expandParentsForComment(Comment comment) {
        boolean expandedAny = false;
        int parentId = comment.parent;

        while (parentId > 0) {
            Comment parent = null;
            for (int i = 1; i < comments.size(); i++) {
                Comment c = comments.get(i);
                if (c.id == parentId) {
                    parent = c;
                    break;
                }
            }

            if (parent == null) {
                break;
            }

            if (!parent.expanded) {
                parent.expanded = true;
                expandedAny = true;
            }

            parentId = parent.parent;
        }

        if (expandedAny) {
            syncComposeState();
        }
    }

    @Override
    public void onDestroyView() {
        CommentsComposeController controllerToDetach = composeController;
        boolean preserveReferenceSummary = getActivity() != null
                && requireActivity().isChangingConfigurations()
                && composeController != null
                && composeController.isLinkPreviewReferenceShowing();
        boolean preservePreviewImage = getActivity() != null
                && requireActivity().isChangingConfigurations()
                && composeController != null
                && composeController.isLinkPreviewImageShowing();
        pendingReferenceLinkSummaryUrl = preserveReferenceSummary
                ? composeController.getLinkPreviewVisibleUrl() : null;
        pendingReferenceLinkSummaryTitle = preserveReferenceSummary
                ? composeController.getLinkPreviewFallbackTitle() : null;
        pendingPreviewImageDialogUrl = preservePreviewImage
                ? composeController.getLinkPreviewVisibleUrl() : null;
        if (composeController != null) {
            if (composeController.isLinkPreviewOverlayShowing()) {
                composeController.completeLinkPreviewDismiss();
            }
            composeController.completeCommentActionDismiss();
        }
        if (originalStatusBarColorCaptured && getActivity() != null) {
            requireActivity().getWindow().setStatusBarColor(originalStatusBarColor);
            originalStatusBarColorCaptured = false;
        }

        View rootView = getView();
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, null);
        }

        if (backPressedCallback != null) {
            backPressedCallback.remove();
            backPressedCallback = null;
        }

        if (queue != null) {
            queue.cancelAll(requestTag);
        }
        commentsLoadGeneration++;
        cancelPendingCommentsParse();
        fallbackManager = null;
        if (webViewController != null) {
            webViewController.onDestroyView(rootView);
        }
        if (controllerToDetach != null && getActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).detachCommentsComposeController(controllerToDetach);
        }

        clearViewReferences();

        super.onDestroyView();
    }

    private void restoreLinkSummaryAfterRecreation() {
        if (!TextUtils.isEmpty(pendingPreviewImageDialogUrl) && getView() != null) {
            String imageUrl = pendingPreviewImageDialogUrl;
            pendingPreviewImageDialogUrl = null;
            getView().post(() -> {
                if (composeController != null) {
                    int backgroundColor = commentsHeaderStatusBarColor != Color.TRANSPARENT
                            ? commentsHeaderStatusBarColor
                            : ContextCompat.getColor(
                                    requireContext(),
                                    ThemeUtils.getBackgroundColorResource(requireContext()));
                    composeController.showImagePreview(
                            imageUrl,
                            TextUtils.isEmpty(story.title)
                                    ? "Story preview image" : "Preview image for " + story.title,
                            null,
                            backgroundColor);
                }
            });
            return;
        }
        if (TextUtils.isEmpty(pendingReferenceLinkSummaryUrl) || getView() == null) {
            return;
        }
        String url = pendingReferenceLinkSummaryUrl;
        String title = pendingReferenceLinkSummaryTitle;
        pendingReferenceLinkSummaryUrl = null;
        pendingReferenceLinkSummaryTitle = null;
        getView().post(() -> {
            if (composeController != null) {
                composeController.showReferencePreview(url, title);
            }
        });
    }

    @Override
    public void onDetach() {
        callback = null;
        super.onDetach();
    }

    private void clearViewReferences() {
        webViewHost = null;
        progressIndicator = null;
        composeController = null;
        appliedStatusBarProtectionKnown = false;
        if (webViewController != null) {
            webViewController.clearViewReferences();
            webViewController = null;
        }
        if (linkPreviewController != null) {
            linkPreviewController.cancelPendingNitterLinkPreviewRead();
            linkPreviewController = null;
        }
    }

    public void onRetry() {
        retryComments();
    }

    private void retryComments() {
        if (!isCommentsViewActive() || story == null) {
            Log.w(TAG, "Retry ignored: commentsViewActive=" + isCommentsViewActive()
                    + ", storyPresent=" + (story != null));
            return;
        }
        if (pendingCommentsParse != null) {
            Log.d(TAG, "Retry ignored while comments are still being parsed for storyId="
                    + story.id);
            return;
        }
        Log.d(TAG, "Retry requested for storyId=" + story.id);
        setCommentsRefreshInProgress(true);
        loadStoryAndComments(story.id, null);
    }

    public void onOpenInBrowser() {
        Utils.launchInExternalBrowser(getActivity(), "https://news.ycombinator.com/item?id=" + story.id);
    }

    private void loadInitialStoryAndComments(boolean restoreScrollFromCache) {
        Context context = getContext();
        if (context == null || !isCommentsViewActive() || story == null) {
            return;
        }

        queue = NetworkComponent.getRequestQueueInstance(context);
        String cachedResponse = Utils.loadCachedStory(context, story.id);

        int loadGeneration = loadStoryAndComments(story.id, cachedResponse);

        if (cachedResponse != null && loadGeneration >= 0) {
            handleJsonResponse(
                    story.id,
                    cachedResponse,
                    false,
                    false,
                    restoreScrollFromCache,
                    loadGeneration,
                    null);
        }
    }

    private int loadStoryAndComments(final int id, final String oldCachedResponse) {
        Context context = getContext();
        if (context == null || queue == null || !isCommentsViewActive()) {
            Log.w(TAG, "Skipping comments load for storyId=" + id
                    + ": contextPresent=" + (context != null)
                    + ", queuePresent=" + (queue != null)
                    + ", commentsViewActive=" + isCommentsViewActive());
            return -1;
        }

        final int loadGeneration = ++commentsLoadGeneration;
        cancelPendingCommentsParse();
        Log.d(TAG, "Loading comments for storyId=" + id + ", hasCachedResponse=" + (oldCachedResponse != null));
        lastLoaded = System.currentTimeMillis();
        if (showUpdate) {
            showUpdate = false;
            notifyHeaderChanged();
        }

        // Initialize fallback manager
        fallbackManager = new AlgoliaFallbackManager(context, queue, requestTag, filteredUsers, new AlgoliaFallbackManager.FallbackListener() {
            @Override
            public void onAlgoliaSuccess(String response) {
                if (!isCurrentCommentsLoad(loadGeneration, id)) {
                    Log.w(TAG, "Ignoring stale Algolia success for storyId=" + id);
                    return;
                }
                Log.d(TAG, "Algolia comments load succeeded for storyId=" + id
                        + ", responseLength=" + (response == null ? 0 : response.length()));
                if (TextUtils.isEmpty(oldCachedResponse) || !oldCachedResponse.equals(response)) {
                    Runnable parseLiveResponse = () -> handleJsonResponse(
                            id,
                            response,
                            true,
                            oldCachedResponse == null,
                            false,
                            loadGeneration,
                            () -> finishCommentsRefresh(loadGeneration, id));
                    if (!deferUntilPendingParseFinishes(
                            loadGeneration,
                            id,
                            parseLiveResponse)) {
                        parseLiveResponse.run();
                    }
                } else if (!attachCompletionToPendingParse(
                        loadGeneration,
                        id,
                        () -> finishCommentsRefresh(loadGeneration, id))) {
                    finishCommentsRefresh(loadGeneration, id);
                }
            }

            @Override
            public void onAlgoliaFailed(boolean noInternet) {
                if (!isCurrentCommentsLoad(loadGeneration, id)) {
                    Log.w(TAG, "Ignoring stale Algolia failure for storyId=" + id);
                    return;
                }
                Log.w(TAG, "Algolia comments load failed for storyId=" + id + ", noInternet=" + noInternet);
                loadingFailed = true;
                loadingFailedServerError = !noInternet;
                commentsLoaded = true;
                setCommentsRefreshInProgress(false);
                notifyHeaderChanged();
            }

            @Override
            public void onUsingFallback() {
                Context context = getContext();
                if (context != null && isCurrentCommentsLoad(loadGeneration, id)) {
                    Toast.makeText(context, "Algolia API failed, using official HN API", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onHNAPIStoryLoaded(Story loadedStory) {
                if (!isCurrentCommentsLoad(loadGeneration, id)) {
                    return;
                }
                // Update story data
                story.title = loadedStory.title;
                story.by = loadedStory.by;
                story.score = loadedStory.score;
                story.time = loadedStory.time;
                story.url = loadedStory.url;
                story.isLink = loadedStory.isLink;
                story.isComment = loadedStory.isComment;
                story.text = loadedStory.text;
                story.kids = loadedStory.kids;
                story.pollOptions = loadedStory.pollOptions;
                story.descendants = loadedStory.descendants;
                story.parentId = loadedStory.parentId;
                story.loaded = true;

                // Reset comments
                if (allComments != null && allComments.size() > 1) {
                    allComments.subList(1, allComments.size()).clear();
                }
                int oldSize = comments.size();
                if (oldSize > 1) {
                    comments.subList(1, oldSize).clear();
                }

                loadingFailed = false;
                loadingFailedServerError = false;
                if (linkPreviewController != null) {
                    linkPreviewController.loadNetworkPreviews(context);
                }
                refreshHeaderAfterStoryLoad();
                maybeLoadPollOptions();
            }

            @Override
            public void onHNAPIFailed() {
                if (!isCurrentCommentsLoad(loadGeneration, id)) {
                    Log.w(TAG, "Ignoring stale HN API failure for storyId=" + id);
                    return;
                }
                Log.w(TAG, "HN API comments load failed for storyId=" + id);
                loadingFailed = true;
                loadingFailedServerError = false;
                commentsLoaded = true;
                setCommentsRefreshInProgress(false);
                notifyHeaderChanged();
            }

            @Override
            public void onAllCommentsLoaded(List<Comment> loadedComments) {
                if (!isCurrentCommentsLoad(loadGeneration, id)) {
                    Log.w(TAG, "Ignoring stale loaded comments for storyId=" + id
                            + ", loadedCount=" + (loadedComments == null ? 0 : loadedComments.size()));
                    return;
                }
                Log.d(TAG, "Loaded comments from fallback path for storyId=" + id
                        + ", loadedCount=" + loadedComments.size());
                Runnable revealComments = () -> {
                    if (!isCurrentCommentsLoad(loadGeneration, id)) {
                        return;
                    }
                    // Add all comments at once in proper tree order. Compose animates the loading
                    // row removal and item insertion from the immutable list snapshot.
                    allComments.addAll(loadedComments);
                    updateDefaultCommentSortOrder(allComments);
                    CommentSorter.sort(allComments, getCurrentCommentSorting());
                    applyDisplayedComments(getDisplayedCommentsForCurrentFilter(allComments));
                    completeCommentsLoad(false);
                    setCommentsRefreshInProgress(false);
                };

                revealComments.run();
            }
        });

        fallbackManager.loadComments(id, oldCachedResponse);

        maybeLoadPollOptions();

        if (linkPreviewController != null) {
            linkPreviewController.loadNetworkPreviews(context);
        }
        return loadGeneration;
    }

    private void onLinkPreviewChanged() {
        notifyHeaderChanged();
    }

    private void notifyHeaderChanged() {
        syncComposeState();
    }

    private void setCommentsRefreshInProgress(boolean refreshInProgress) {
        if (commentsRefreshInProgress == refreshInProgress) {
            return;
        }
        commentsRefreshInProgress = refreshInProgress;
        syncComposeState();
    }

    private void maybeLoadPollOptions() {
        if (!isCommentsViewActive() || pollOptionsLoadStarted || story == null || story.isComment || queue == null) {
            return;
        }

        if (story.pollOptions != null) {
            loadPollOptions();
            return;
        }

        if (pollOptionsLookupStarted || story.id <= 0 || TextUtils.isEmpty(story.title) || !POLL_TITLE_PATTERN.matcher(story.title).find()) {
            return;
        }

        pollOptionsLookupStarted = true;
        String url = "https://hacker-news.firebaseio.com/v0/item/" + story.id + ".json";

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isCommentsViewActive()) {
                        return;
                    }
                    Story hnStory = new Story();
                    hnStory.id = story.id;
                    if (JSONParser.updateStoryWithOfficialHNResponse(hnStory, response) && hnStory.pollOptions != null) {
                        story.pollOptions = hnStory.pollOptions;
                        maybeLoadPollOptions();
                    }
                }, error -> {
                    if (isCommentsViewActive()) {
                        pollOptionsLookupStarted = false;
                    }
                });

        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private void loadPollOptions() {
        if (!isCommentsViewActive() || story.pollOptions == null || queue == null) {
            return;
        }

        pollOptionsLoadStarted = true;
        story.pollOptionArrayList = new ArrayList<>();
        for (int optionId : story.pollOptions) {
            PollOption pollOption = new PollOption();
            pollOption.loaded = false;
            pollOption.id = optionId;
            story.pollOptionArrayList.add(pollOption);
        }

        for (int optionId : story.pollOptions) {
            String url = "https://hacker-news.firebaseio.com/v0/item/" + optionId + ".json";

            StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                    response -> {
                        if (!isCommentsViewActive()) {
                            return;
                        }
                        try {
                            for (PollOption pollOption : story.pollOptionArrayList) {
                                if (pollOption.id == optionId) {
                                    pollOption.loaded = true;

                                    JSONObject jsonObject = new JSONObject(response);
                                    pollOption.points = jsonObject.getInt("score");
                                    pollOption.text = JSONParser.preprocessHtml(jsonObject.getString("text"));

                                    notifyHeaderChanged();
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }, error -> {

            });

            stringRequest.setTag(requestTag);
            queue.add(stringRequest);
        }
    }

    private void handleJsonResponse(
            final int id,
            final String response,
            final boolean cache,
            final boolean forceHeaderRefresh,
            final boolean restoreScroll,
            final int loadGeneration,
            @Nullable final Runnable completion) {
        if (!isCurrentCommentsLoad(loadGeneration, id)) {
            return;
        }

        final int oldCommentCount = getAllCommentsSource().size();
        // This is what we get if the Algolia API has not indexed the post,
        // we should attempt to show the user an option to switch API:s in this
        // server error case
        // Actually, the response being a 404 should be captured by another part
        // so this should never be called. Not that I dare remove it...
        if (response.equals(JSONParser.ALGOLIA_ERROR_STRING)) {
            loadingFailed = true;
            loadingFailedServerError = true;
            notifyHeaderChanged();
        }

        final int[] topLevelCommentIds = story.kids == null ? null : story.kids.clone();
        final Set<String> filteredUsersSnapshot =
                filteredUsers == null ? null : new HashSet<>(filteredUsers);
        cancelPendingCommentsParse();
        PendingCommentsParse pendingParse =
                new PendingCommentsParse(loadGeneration, id, completion);
        pendingCommentsParse = pendingParse;
        pendingParse.future = BackgroundJSONParser.parseAlgoliaCommentsJson(
                response,
                topLevelCommentIds,
                filteredUsersSnapshot,
                new BackgroundJSONParser.AlgoliaCommentsParseCallback() {
                    @Override
                    public void onParseSuccess(JSONParser.AlgoliaCommentsResponse parsedResponse) {
                        if (!isCurrentPendingCommentsParse(pendingParse)) {
                            return;
                        }
                        pendingCommentsParse = null;
                        applyParsedJsonResponse(
                                id,
                                response,
                                cache,
                                forceHeaderRefresh,
                                restoreScroll,
                                loadGeneration,
                                oldCommentCount,
                                parsedResponse);
                        runPendingParseCompletion(pendingParse);
                        runPendingParseFollowUp(pendingParse);
                    }

                    @Override
                    public void onParseError(IOException error) {
                        if (!isCurrentPendingCommentsParse(pendingParse)) {
                            return;
                        }
                        pendingCommentsParse = null;
                        error.printStackTrace();
                        loadingFailed = true;
                        loadingFailedServerError = false;
                        notifyHeaderChanged();
                        completeCommentsLoad(false);
                        runPendingParseCompletion(pendingParse);
                        runPendingParseFollowUp(pendingParse);
                    }
                });
    }

    private void applyParsedJsonResponse(
            int id,
            String response,
            boolean cache,
            boolean forceHeaderRefresh,
            boolean restoreScroll,
            int loadGeneration,
            int oldCommentCount,
            JSONParser.AlgoliaCommentsResponse parsedResponse) {
        if (!isCurrentCommentsLoad(loadGeneration, id)) {
            return;
        }

        boolean storyChanged =
                parsedResponse.updateStoryInformation(story, forceHeaderRefresh, oldCommentCount);
        boolean updateHeaderAfterLoad = storyChanged || forceHeaderRefresh;
        if (linkPreviewController != null) {
            linkPreviewController.loadNetworkPreviews(getContext());
        }
        maybeLoadPollOptions();

        boolean wasIntegratedWebview = integratedWebview;
        integratedWebview = prefIntegratedWebview && story.isLink;

        if (integratedWebview && !wasIntegratedWebview) {
            webViewController.setIntegratedWebview(true);
            webViewController.initialize();
        }

        loadingFailed = false;
        loadingFailedServerError = false;

        // Seems like loading went well, lets cache the result
        if (cache) {
            Utils.cacheStory(getContext(), id, response);
        }

        Runnable revealComments = () -> {
            if (!isCurrentCommentsLoad(loadGeneration, id)) {
                return;
            }
            applyParsedComments(
                    parsedResponse.comments,
                    updateHeaderAfterLoad);

            if (!cache && restoreScroll) {
                // If we're not caching the result, this means we just loaded an old cache.
                // Let's see if we can recover the scroll position.
                if (MainActivity.commentsScrollProgresses != null && !MainActivity.commentsScrollProgresses.isEmpty()) {
                    // We check all of the caches to see if one has the same story ID
                    for (CommentsScrollProgress scrollProgress : MainActivity.commentsScrollProgresses) {
                        if (scrollProgress.storyId == story.id) {
                            // Jackpot! Let's restore the state
                            restoreScrollProgress(scrollProgress);
                        }
                    }
                }
            }

            completeCommentsLoad(updateHeaderAfterLoad);
        };

        revealComments.run();
    }

    private boolean attachCompletionToPendingParse(
            int loadGeneration,
            int storyId,
            Runnable completion) {
        PendingCommentsParse pendingParse = pendingCommentsParse;
        if (pendingParse == null
                || pendingParse.loadGeneration != loadGeneration
                || pendingParse.storyId != storyId) {
            return false;
        }
        pendingParse.completion = completion;
        return true;
    }

    private void runPendingParseCompletion(PendingCommentsParse pendingParse) {
        Runnable completion = pendingParse.completion;
        pendingParse.completion = null;
        if (completion != null) {
            completion.run();
        }
    }

    private boolean deferUntilPendingParseFinishes(
            int loadGeneration,
            int storyId,
            Runnable followUp) {
        PendingCommentsParse pendingParse = pendingCommentsParse;
        if (pendingParse == null
                || pendingParse.loadGeneration != loadGeneration
                || pendingParse.storyId != storyId) {
            return false;
        }
        Runnable previousFollowUp = pendingParse.followUp;
        pendingParse.followUp = previousFollowUp == null
                ? followUp
                : () -> {
                    previousFollowUp.run();
                    followUp.run();
                };
        return true;
    }

    private void runPendingParseFollowUp(PendingCommentsParse pendingParse) {
        Runnable followUp = pendingParse.followUp;
        pendingParse.followUp = null;
        if (followUp != null
                && isCurrentCommentsLoad(pendingParse.loadGeneration, pendingParse.storyId)) {
            followUp.run();
        }
    }

    private void finishCommentsRefresh(int loadGeneration, int storyId) {
        if (!isCurrentCommentsLoad(loadGeneration, storyId)) {
            return;
        }
        setCommentsRefreshInProgress(false);
    }

    private boolean isCurrentPendingCommentsParse(PendingCommentsParse pendingParse) {
        return pendingCommentsParse == pendingParse
                && isCurrentCommentsLoad(pendingParse.loadGeneration, pendingParse.storyId);
    }

    private void cancelPendingCommentsParse() {
        PendingCommentsParse pendingParse = pendingCommentsParse;
        pendingCommentsParse = null;
        if (pendingParse != null && pendingParse.future != null) {
            pendingParse.future.cancel(true);
            pendingParse.future = null;
        }
    }

    private void completeCommentsLoad(boolean updateHeaderAfterLoad) {
        if (!isCommentsViewActive()) {
            return;
        }
        boolean commentsWereLoaded = commentsLoaded;
        commentsLoaded = true;
        if (!commentsWereLoaded) {
            notifyHeaderChanged();
        }
        if (updateHeaderAfterLoad) {
            refreshHeaderAfterStoryLoad();
        }
        updateNavigationVisibility();
        View commentsView = getView();
        if (commentsView == null) {
            return;
        }
        commentsView.post(() -> {
            if (!isCommentsViewActive()) {
                return;
            }
            scrollToTargetComment();
            restorePendingCommentAction();
        });
    }

    private void restorePendingCommentAction() {
        if (pendingCommentActionId == -1 || composeController == null) {
            return;
        }
        Comment comment = findCommentById(pendingCommentActionId);
        if (comment == null) {
            return;
        }
        pendingCommentActionId = -1;
        composeController.restoreCommentActions(comment);
        syncOnBackPressedCallbackEnabledState();
    }

    private void refreshHeaderAfterStoryLoad() {
        if (!isCommentsViewActive()) {
            return;
        }

        if (uncachedStoryHeaderLoading && story.loaded) {
            uncachedStoryHeaderLoading = false;
        }
        notifyHeaderChanged();
    }

    private boolean isCommentsViewActive() {
        return getView() != null
                && comments != null
                && allComments != null;
    }

    private boolean isCurrentCommentsLoad(int loadGeneration, int storyId) {
        return loadGeneration == commentsLoadGeneration
                && story != null
                && story.id == storyId
                && isCommentsViewActive();
    }

    private void applyParsedComments(
            List<Comment> parsedComments,
            boolean headerRefreshWillFollow) {
        List<Comment> oldComments = CommentListDiff.copyForDiff(comments);
        Map<Integer, Comment> existingCommentsById = new HashMap<>();
        List<Comment> sourceComments = getAllCommentsSource();
        for (int i = 1; i < sourceComments.size(); i++) {
            Comment comment = sourceComments.get(i);
            existingCommentsById.put(comment.id, comment);
        }

        List<Comment> nextComments = new ArrayList<>(parsedComments.size() + 1);
        nextComments.add(sourceComments.get(0));
        for (Comment parsedComment : parsedComments) {
            Comment existingComment = existingCommentsById.get(parsedComment.id);
            if (existingComment != null) {
                CommentListDiff.updateExistingComment(existingComment, parsedComment);
                nextComments.add(existingComment);
            } else {
                nextComments.add(parsedComment);
            }
        }

        updateDefaultCommentSortOrder(nextComments);
        CommentSorter.sort(nextComments, getCurrentCommentSorting());

        if (SettingsUtils.shouldCollapseTopLevel(getContext())) {
            for (Comment comment : nextComments) {
                if (comment.depth == 0) {
                    comment.expanded = false;
                }
            }
        }

        allComments.clear();
        allComments.addAll(nextComments);
        applyDisplayedComments(
                getDisplayedCommentsForCurrentFilter(allComments),
                oldComments,
                !headerRefreshWillFollow);
    }

    private List<Comment> getAllCommentsSource() {
        if (allComments == null || allComments.isEmpty()) {
            return comments;
        }
        return allComments;
    }

    private String getCurrentCommentSorting() {
        if (TextUtils.isEmpty(currentCommentSorting)) {
            currentCommentSorting = SettingsUtils.getPreferredCommentSorting(getContext());
        }
        return currentCommentSorting;
    }

    private void updateDefaultCommentSortOrder(List<Comment> commentsWithHeader) {
        for (int i = 1; i < commentsWithHeader.size(); i++) {
            commentsWithHeader.get(i).sortOrder = i;
        }
    }

    private void changeCommentSorting(String sortType) {
        if (!isCommentsViewActive()) {
            return;
        }

        List<Comment> oldComments = CommentListDiff.copyForDiff(comments);
        currentCommentSorting = sortType;
        List<Comment> sourceComments = getAllCommentsSource();
        CommentSorter.sort(sourceComments, sortType);
        applyDisplayedComments(getDisplayedCommentsForCurrentFilter(sourceComments), oldComments);
    }

    private void showCommentsByOp() {
        List<Comment> sourceComments = getAllCommentsSource();
        if (!CommentThreadFilter.hasCommentsByOp(story, sourceComments)) {
            return;
        }

        setCommentsByOpFilterActive(true);
        applyDisplayedComments(CommentThreadFilter.buildCommentsByOpThreadList(story, sourceComments));
    }

    private void resetCommentsByOpFilter() {
        if (!commentsByOpFilterActive) {
            return;
        }

        setCommentsByOpFilterActive(false);
        applyDisplayedComments(new ArrayList<>(getAllCommentsSource()));
    }

    private void setCommentsByOpFilterActive(boolean active) {
        commentsByOpFilterActive = active;
    }

    private List<Comment> getDisplayedCommentsForCurrentFilter(List<Comment> sourceComments) {
        if (commentsByOpFilterActive) {
            if (CommentThreadFilter.hasCommentsByOp(story, sourceComments)) {
                return CommentThreadFilter.buildCommentsByOpThreadList(story, sourceComments);
            }
            setCommentsByOpFilterActive(false);
        }
        return new ArrayList<>(sourceComments);
    }

    private boolean hasCommentsByOp() {
        return CommentThreadFilter.hasCommentsByOp(story, getAllCommentsSource());
    }

    private void applyDisplayedComments(List<Comment> nextComments) {
        applyDisplayedComments(nextComments, CommentListDiff.copyForDiff(comments));
    }

    private void applyDisplayedComments(List<Comment> nextComments, List<Comment> oldComments) {
        applyDisplayedComments(nextComments, oldComments, true);
    }

    private void applyDisplayedComments(
            List<Comment> nextComments,
            List<Comment> oldComments,
            boolean updateBoundHeader) {
        comments.clear();
        comments.addAll(nextComments);
        updateNavigationVisibility();
        syncComposeState();
    }

    public void clickBrowser() {
        webViewController.openCurrentOrStoryUrlInBrowser();
    }

    private void toggleStoryBookmark() {
        Context ctx = getContext();
        if (ctx == null || story == null) {
            return;
        }

        boolean bookmarked = !Utils.isBookmarked(ctx, story.id);
        if (bookmarked) {
            Utils.addBookmark(ctx, story.id);
        } else {
            Utils.removeBookmark(ctx, story.id);
        }
    }

    private void openArchiveOrg() {
        Toast.makeText(getContext(), "Contacting archive.org API...", Toast.LENGTH_SHORT).show();
        ArchiveOrgUrlGetter.getArchiveUrl(story.url, getContext(), new ArchiveOrgUrlGetter.GetterCallback() {
            @Override
            public void onSuccess(String url) {
                Utils.launchCustomTab(getActivity(), url);
            }

            @Override
            public void onFailure(String reason) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Error: " + reason, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void openArchiveIs() {
        Utils.launchCustomTab(getActivity(), "https://archive.is/newest/" + Uri.encode(story.url));
    }

    private void openArchiveToday() {
        Utils.launchCustomTab(getActivity(), "https://archive.today/newest/" + Uri.encode(story.url));
    }

    public void clickUser() {
        ((MainActivity) requireActivity()).showUserDialog(
                story.by,
                () -> updateUserTags(story.by));
    }

    public void clickComment() {
        if (!AccountUtils.hasAccountDetails(getContext())) {
            AccountUtils.showLoginPrompt(requireContext());
            return;
        }

        Intent intent = ComposeEditorContract.createIntent(requireContext());
        intent.putExtra(ComposeEditorContract.EXTRA_ID, story.id);
        intent.putExtra(ComposeEditorContract.EXTRA_PARENT_TEXT, story.title);
        intent.putExtra(ComposeEditorContract.EXTRA_POST_TITLE, story.title);
        intent.putExtra(
                ComposeEditorContract.EXTRA_TYPE,
                ComposeEditorContract.TYPE_TOP_COMMENT);
        startActivity(intent);
    }

    public void clickVote() {
        Context ctx = getContext();
        if (ctx == null || story == null) {
            return;
        }

        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(requireContext());
            return;
        }

        int storyId = story.id;
        boolean storyIsComment = story.isComment;
        boolean wasUpvoted = Utils.isUpvoted(ctx, storyId, storyIsComment);
        boolean newUpvoted = !wasUpvoted;
        storyVoteLoading = true;
        syncComposeState();

        UserActions.ActionCallback cb = new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                Utils.setUpvoted(ctx, storyId, storyIsComment, newUpvoted);
                storyVoteLoading = false;
                syncComposeState();
            }

            @Override
            public void onFailure(String summary, String response) {
                Utils.setUpvoted(ctx, storyId, storyIsComment, wasUpvoted);
                storyVoteLoading = false;
                syncComposeState();
            }
        };

        if (newUpvoted) {
            UserActions.upvote(ctx, storyId, cb);
        } else {
            UserActions.unvote(ctx, storyId, cb);
        }
    }

    public void clickFavorite() {
        Context ctx = getContext();
        if (ctx == null || story == null) {
            return;
        }

        int storyId = story.id;
        boolean wasFavorited = Utils.isFavorited(ctx, storyId);
        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(requireContext());
            return;
        }

        boolean newFavorited = !wasFavorited;
        storyFavoriteLoading = true;
        syncComposeState();
        UserActions.setFavorite(ctx, storyId, newFavorited, new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                Utils.setFavorite(ctx, storyId, newFavorited);
                storyFavoriteLoading = false;
                syncComposeState();
            }

            @Override
            public void onFailure(String summary, String response) {
                Utils.setFavorite(ctx, storyId, wasFavorited);
                storyFavoriteLoading = false;
                syncComposeState();
                if (!wasFavorited) {
                    Toast.makeText(ctx, "Couldn't add favorite", Toast.LENGTH_SHORT).show();
                } else {
                    UserActions.showFailureDetailDialog(ctx, summary, response);
                    Toast.makeText(ctx, "Couldn't update favorite", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void navigateToNextComment() {
        navigateToNextComment(true);
    }

    public void navigateToNextComment(boolean topLevelOnly) {
        navigateToNextComment(topLevelOnly, false);
    }

    public void navigateToNextComment(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        if (composeController != null) {
            composeController.navigateNext(topLevelOnly, scaleLongScrollSpeed);
        }
    }

    public void navigateToPreviousComment() {
        navigateToPreviousComment(true);
    }

    public void navigateToPreviousComment(boolean topLevelOnly) {
        navigateToPreviousComment(topLevelOnly, false);
    }

    public void navigateToPreviousComment(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        if (composeController != null) {
            composeController.navigatePrevious(topLevelOnly, scaleLongScrollSpeed);
        }
    }

    private void updateNavigationVisibility() {
        // Compose derives navigation visibility directly from display settings and list content.
    }

    @Nullable
    private Comment findCommentById(int commentId) {
        if (comments != null) {
            for (Comment comment : comments) {
                if (comment.id == commentId) {
                    return comment;
                }
            }
        }
        if (allComments != null) {
            for (Comment comment : allComments) {
                if (comment.id == commentId) {
                    return comment;
                }
            }
        }
        return null;
    }

    private void updateUserTags(String changedUser) {
        syncComposeState();
    }

    public void onRequest(Runnable onUpdate, Runnable onDone) {
        if (story == null || TextUtils.isEmpty(story.url)) {
            onDone.run();
            return;
        }
        if (!Utils.isAiSummaryEnabled(requireContext())) {
            onDone.run();
            return;
        }

        Context context = requireContext();
        String mode = PreferenceManager.getDefaultSharedPreferences(context)
                .getString("pref_ai_summary_mode", "cloud");

        if (webViewController != null) {
            webViewController.getLoadedPageText(
                    text -> summarizeStory(context, mode, text, onUpdate, onDone));
            return;
        }

        summarizeStory(context, mode, null, onUpdate, onDone);
    }

    private void summarizeStory(Context context,
                                String mode,
                                @Nullable String articleText,
                                Runnable onUpdate,
                                Runnable onDone) {
        boolean hasArticleText = !TextUtils.isEmpty(articleText);
        if ("local".equals(mode)) {
            SummaryManager.SummaryCallback callback = new SummaryManager.SummaryCallback() {
                @Override
                public void onDebugInfo(String debugInfo) {
                    story.summaryDebugInfo = debugInfo;
                    onUpdate.run();
                }

                @Override
                public void onProgress(String summary) {
                    story.summary = summary;
                    onUpdate.run();
                }

                @Override
                public void onSuccess(String summary) {
                    story.summary = summary;
                    story.summaryGeneratedSuccessfully = true;
                    onDone.run();
                }

                @Override
                public void onFailure(String error) {
                    story.summary = "Failed to generate local summary: " + error;
                    story.summaryGeneratedSuccessfully = false;
                    onDone.run();
                }
            };
            if (hasArticleText) {
                SummaryManager.summarizeTextWithGeminiNano(context, articleText, callback);
            } else {
                SummaryManager.summarizeArticleWithGeminiNano(context, story.url, callback);
            }
        } else {
            SummaryManager.SummaryCallback callback = new SummaryManager.SummaryCallback() {
                @Override
                public void onDebugInfo(String debugInfo) {
                    story.summaryDebugInfo = debugInfo;
                    onUpdate.run();
                }

                @Override
                public void onProgress(String summary) {
                    story.summary = summary;
                    onUpdate.run();
                }

                @Override
                public void onSuccess(String summary) {
                    story.summary = summary;
                    story.summaryGeneratedSuccessfully = true;
                    onDone.run();
                }

                @Override
                public void onFailure(String error) {
                    story.summary = "Failed to generate summary: " + error;
                    story.summaryGeneratedSuccessfully = false;
                    onDone.run();
                }
            };
            if (hasArticleText) {
                SummaryManager.summarizeText(context, queue, articleText, callback);
            } else {
                SummaryManager.summarizeArticle(context, queue, story.url, callback);
            }
        }
    }


    public interface BottomSheetFragmentCallback {
        void onSwitchView(boolean isAtWebView);
    }

}
