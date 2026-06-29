package com.simon.harmonichackernews;

import static androidx.webkit.WebViewFeature.isFeatureSupported;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebViewFeature;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;
import com.simon.harmonichackernews.adapters.CommentsRecyclerViewAdapter;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.CommentsScrollProgress;
import com.simon.harmonichackernews.data.PollOption;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.databinding.FragmentCommentsBinding;
import com.simon.harmonichackernews.linkpreview.LinkPreviewController;
import com.simon.harmonichackernews.network.AlgoliaFallbackManager;
import com.simon.harmonichackernews.network.ArchiveOrgUrlGetter;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.SummaryManager;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.CommentSorter;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ShareUtils;
import com.simon.harmonichackernews.utils.StatusBarProtectionUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import okhttp3.Response;

public class CommentsFragment extends Fragment implements CommentsRecyclerViewAdapter.CommentClickListener, CommentsRecyclerViewAdapter.RequestSummaryCallback, CommentsRecyclerViewAdapter.RetryListener, CommentNavigationController.Host {
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
    private final static String STATE_COMMENT_ACTION_COMMENT_ID = "com.simon.harmonichackernews.STATE_COMMENT_ACTION_COMMENT_ID";
    private final static String STATE_ADBLOCK_DISABLED_FOR_SESSION = "com.simon.harmonichackernews.STATE_ADBLOCK_DISABLED_FOR_SESSION";
    private final static String STATE_COMMENT_SORTING = "com.simon.harmonichackernews.STATE_COMMENT_SORTING";
    private final static Pattern POLL_TITLE_PATTERN = Pattern.compile("\\bpoll\\b", Pattern.CASE_INSENSITIVE);

    private final static int PREDICTIVE_BACK_MAX_PEEK_DP = 70;
    private final static int MENU_COMMENT_SORT_GROUP_ID = 100;
    private final static int MENU_COMMENT_SORT_ITEM_ID_BASE = 200;
    private final static int MENU_ARCHIVE_SERVICE_GROUP_ID = 300;
    private final static int MENU_ARCHIVE_ORG_ITEM_ID = 301;
    private final static int MENU_ARCHIVE_IS_ITEM_ID = 302;
    private final static int MENU_ARCHIVE_TODAY_ITEM_ID = 303;
    private static final int SWIPE_REFRESH_PROGRESS_START_OFFSET_DP = -32;
    private static final int SWIPE_REFRESH_PROGRESS_END_OFFSET_DP = -32;

    private BottomSheetFragmentCallback callback;
    private List<Comment> comments;
    private List<Comment> allComments;
    private RequestQueue queue;
    private final Object requestTag = new Object();
    private CommentsRecyclerViewAdapter adapter;
    private FragmentCommentsBinding binding;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private RecyclerView recyclerViewSwipe;
    private RecyclerView recyclerViewRegular;
    private LinearLayoutManager layoutManager;
    private RecyclerView.OnScrollListener recyclerViewScrollListener;
    private View.OnLayoutChangeListener recyclerViewLayoutChangeListener;
    private BottomSheetBehavior.BottomSheetCallback recyclerBottomSheetCallback;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private RecyclerView.SmoothScroller smoothScroller;
    private CommentNavigationController commentNavigationController;
    private final CommentActionOverlayController commentActionOverlayController = new CommentActionOverlayController(new CommentActionOverlayController.Host() {
        @Nullable
        @Override
        public Context getCommentActionContext() {
            return CommentsFragment.this.getContext();
        }

        @NonNull
        @Override
        public Context requireCommentActionContext() {
            return CommentsFragment.this.requireContext();
        }

        @NonNull
        @Override
        public androidx.fragment.app.FragmentManager getCommentActionActivityFragmentManager() {
            return CommentsFragment.this.requireActivity().getSupportFragmentManager();
        }

        @NonNull
        @Override
        public androidx.fragment.app.FragmentManager getCommentActionParentFragmentManager() {
            return CommentsFragment.this.getParentFragmentManager();
        }

        @Nullable
        @Override
        public Story getCommentActionStory() {
            return story;
        }

        @Nullable
        @Override
        public String getCommentActionReplyPostTitle() {
            return adapter != null && adapter.story != null ? adapter.story.title : null;
        }

        @Override
        public boolean isCommentActionHostAdded() {
            return CommentsFragment.this.isAdded();
        }

        @Override
        public boolean shouldUseCommentActionCardStyle(Context ctx) {
            return adapter != null
                    ? adapter.cardStyle
                    : SettingsUtils.shouldUseCardCommentDisplayStyle(ctx);
        }

        @Nullable
        @Override
        public ViewGroup getCommentActionOverlayHost() {
            return CommentsFragment.this.getCommentActionOverlayHost();
        }

        @Nullable
        @Override
        public View findCommentActionSourceView(int commentId) {
            return CommentsFragment.this.findCommentView(commentId);
        }

        @Nullable
        @Override
        public Comment findCommentActionComment(int commentId) {
            return CommentsFragment.this.findCommentById(commentId);
        }

        @Override
        public void stopCommentActionListScroll() {
            if (recyclerView != null) {
                recyclerView.stopScroll();
            }
        }

        @Override
        public void setCommentActionLinksDisabled(boolean disabled) {
            if (adapter != null) {
                adapter.disableCommentATagClick = disabled;
            }
        }

        @Override
        public void syncCommentActionBackState() {
            syncOnBackPressedCallbackEnabledState();
        }

        @Override
        public void onCommentActionOverlayRemoved() {
            updateCommentsStatusBarAppearance();
        }

        @Override
        public void updateCommentActionUserTags(String changedUser) {
            updateUserTags(changedUser);
        }
    });
    private View scrollNavigation;
    private ExtendedFloatingActionButton searchScrollTopFab;
    private int commentsBottomInset = 0;
    private int scrollNavigationBaseBottomMargin = 0;
    private int searchScrollTopFabBaseBottomMargin = 0;
    private boolean rootInsetsApplied = false;
    private boolean recyclerInsetsApplied = false;
    private LinearProgressIndicator progressIndicator;
    private LinearLayout bottomSheet;
    private View headerSpacer;
    private LinkPreviewController linkPreviewController;
    private CommentsWebViewController webViewController;
    private boolean showNavButtons = false;
    private boolean showCommentsScrollbar = false;
    private boolean showWebsite = false;
    private boolean integratedWebview = true;
    private boolean prefIntegratedWebview = true;
    private boolean translucentStatusBarEnabled = false;
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
    private OnBackPressedCallback backPressedCallback;
    private String username;
    private Story story;
    private Set<String> filteredUsers;
    private int SCREEN_HEIGHT_IN_PIXELS = 100;
    private int scrollToCommentId = -1;
    private boolean commentsByOpFilterActive = false;
    private int originalStatusBarColor = Color.TRANSPARENT;
    private boolean originalStatusBarColorCaptured = false;
    private int commentsPaneStatusBarColor = Color.TRANSPARENT;
    private int commentsHeaderStatusBarColor = Color.TRANSPARENT;
    private boolean appliedStatusBarProtectionKnown = false;
    private boolean appliedStatusBarProtectionEnabled = false;
    private int appliedStatusBarProtectionColor = Color.TRANSPARENT;
    private String currentCommentSorting;

    // Clean fallback management
    private AlgoliaFallbackManager fallbackManager;

    public CommentsFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return FragmentCommentsBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        filteredUsers = Utils.getFilteredUsers(getContext());

        postponeEnterTransition();

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

            if (Utils.isTablet(getResources())) {
                int forward = bundle.getInt(EXTRA_FORWARD, 0);
                if (forward == 0) {
                    setExitTransition(new MaterialFadeThrough());
                    setEnterTransition(new MaterialFadeThrough());
                } else if (forward > 0) {
                    setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, true));
                    setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, false));
                } else {
                    setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, false));
                    setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, true));
                }
            }

            showWebsite = bundle.getBoolean(EXTRA_SHOW_WEBSITE, false);

        } else {
            story.loaded = false;
            story.id = -1;
            // check if url intercept
            Intent intent = requireActivity().getIntent();
            if (intent != null) {
                if (Intent.ACTION_VIEW.equalsIgnoreCase(intent.getAction())) {
                    if (!loadStoryFromHackerNewsUri(intent.getData())) {
                        Toast.makeText(getContext(), "Unable to parse story", Toast.LENGTH_SHORT).show();
                        requireActivity().finish();
                    }
                } else if (Intent.ACTION_SEND.equalsIgnoreCase(intent.getAction())) {
                    CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                    Uri uri = Utils.getHackerNewsItemUriFromText(sharedText != null ? sharedText.toString() : null);
                    if (!loadStoryFromHackerNewsUri(uri)) {
                        Toast.makeText(getContext(), "Unable to parse story", Toast.LENGTH_SHORT).show();
                        requireActivity().finish();
                    }
                } else {
                    if (intent.getIntExtra(EXTRA_ID, -1) != -1) {
                        story.id = intent.getIntExtra(EXTRA_ID, -1);
                        story.title = intent.getStringExtra(EXTRA_TITLE);
                        story.by = "";
                        story.url = "";
                        story.score = 0;
                    }
                }
            }
        }
    }

    private boolean hasStoryHeaderArguments(@Nullable Bundle bundle) {
        return bundle != null
                && bundle.getInt(EXTRA_ID, -1) > 0
                && bundle.getString(EXTRA_TITLE) != null;
    }

    private boolean loadStoryFromHackerNewsUri(Uri uri) {
        if (!Utils.isHackerNewsItemUri(uri)) return false;

        try {
            int id = Integer.parseInt(uri.getQueryParameter("id"));
            if (id <= 0) return false;

            story.id = id;
            story.title = "Loading...";
            story.by = "";
            story.url = "";
            story.score = 0;

            String fragment = uri.getFragment();
            if (fragment != null && !fragment.isEmpty() && TextUtils.isDigitsOnly(fragment)) {
                scrollToCommentId = Integer.parseInt(fragment);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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
        binding = FragmentCommentsBinding.bind(view);
        rootInsetsApplied = false;
        recyclerInsetsApplied = false;
        topInset = 0;

        if (savedInstanceState != null) {
            commentActionOverlayController.setPendingCommentId(savedInstanceState.getInt(
                    STATE_COMMENT_ACTION_COMMENT_ID,
                    CommentActionOverlayController.NO_COMMENT_ID));
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

        commentsPaneStatusBarColor = StatusBarProtectionUtils.getPaneBackgroundColor(requireContext());
        commentsHeaderStatusBarColor = commentsPaneStatusBarColor;
        translucentStatusBarEnabled = SettingsUtils.shouldUseTranslucentStatusBar(requireContext());
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

        swipeRefreshLayout = binding.commentsSwipeRefresh;
        recyclerViewRegular = binding.commentsRecyclerview;
        recyclerViewSwipe = binding.commentsRecyclerviewSwipe;
        bottomSheet = binding.commentsBottomSheet;
        progressIndicator = binding.webviewProgress;
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
                if (adapter != null) {
                    adapter.setReaderModeEnabled(enabled);
                }
            }

            @Override
            public void onReaderModeAvailabilityChanged(boolean available) {
                if (adapter != null) {
                    adapter.setReaderModeAvailable(available);
                }
            }
        });
        webViewController.bindViews(binding, bottomSheet, swipeRefreshLayout, progressIndicator);
        webViewController.configure(showWebsite, integratedWebview, preloadWebview, preloadWebviewMinimumBattery, matchWebviewTheme, readerModeEnabled, readerModeDefault, blockAds);

        if (story.title == null) {
            // Empty view for tablets
            binding.commentsEmpty.setVisibility(View.VISIBLE);
            bottomSheet.setVisibility(View.GONE);
            webViewController.setContainerVisibility(View.GONE);

            swipeRefreshLayout.setEnabled(false);
            return;
        }

        backPressedCallback = new OnBackPressedCallback(true) {

            @Override
            public void handleOnBackCancelled() {
                if (commentActionOverlayController.isShowing()) {
                    commentActionOverlayController.cancelPredictiveBack();
                    return;
                }

                if (willExpandBottomSheetOnBack()) {
                    bottomSheet.setTranslationY(0f);
                    try {
                        setSheetButtonsContentAlpha(1f);
                        adapter.setBoundHeaderAlpha(0f);
                    } catch (Exception ignored) {

                    }
                }
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                if (commentActionOverlayController.isShowing()) {
                    commentActionOverlayController.updatePredictiveBack(backEvent);
                    return;
                }

                if (willExpandBottomSheetOnBack()) {
                    bottomSheet.setTranslationY(backEvent.getProgress() * -Utils.pxFromDpInt(getResources(), PREDICTIVE_BACK_MAX_PEEK_DP));
                    try {
                        setSheetButtonsContentAlpha(1f - backEvent.getProgress() * 0.7f);
                        adapter.setBoundHeaderAlpha(backEvent.getProgress() * 0.7f);
                    } catch (Exception ignored) {

                    }
                }
            }

            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                if (commentActionOverlayController.isShowing()) {
                    commentActionOverlayController.startPredictiveBack(backEvent);
                    return;
                }

                if (willExpandBottomSheetOnBack()) {
                    bottomSheet.setTranslationY(backEvent.getProgress() * -Utils.pxFromDpInt(getResources(), PREDICTIVE_BACK_MAX_PEEK_DP));
                    try {
                        setSheetButtonsContentAlpha(1f - backEvent.getProgress() * 0.7f);
                        adapter.setBoundHeaderAlpha(backEvent.getProgress() * 0.7f);
                    } catch (Exception ignored) {

                    }
                }
            }

            @Override
            public void handleOnBackPressed() {
                if (commentActionOverlayController.isShowing()) {
                    if (commentActionOverlayController.isPredictiveBackActive()) {
                        commentActionOverlayController.commitPredictiveBack();
                        return;
                    }
                    commentActionOverlayController.dismiss(true);
                    return;
                }

                if (webViewController.isShowingCustomView()) {
                    webViewController.hideCustomView(true);
                    return;
                }

                boolean webViewVisible = BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED;
                if (webViewVisible && webViewController.isReaderModeEnabled()) {
                    webViewController.disableReaderMode();
                    return;
                } else if (willExpandBottomSheetOnBack()) {
                    // If the webView can't go back but the back handler is enabled,
                    // it means that the closeWebViewOnBack == true
                    BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
                    bottomSheet.setTranslationY(0f);
                    return;
                } else if (webViewVisible) {
                    webViewController.goBackFromVisibleWebView();
                    return;
                }

                requireActivity().finish();
                if (!SettingsUtils.shouldDisableCommentsSwipeBack(getContext()) && !Utils.isTablet(getResources())) {
                    requireActivity().overridePendingTransition(0, R.anim.activity_out_animation);
                }
            }

            private boolean willExpandBottomSheetOnBack() {
                boolean webViewVisible = BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED;
                return webViewVisible && webViewController.willExpandBottomSheetOnBack();
            }
        };

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);

        swipeRefreshLayout.setOnRefreshListener(this::onRetry);
        ViewUtils.setUpSwipeRefreshWithStatusBarOffset(swipeRefreshLayout,
                Utils.pxFromDpInt(getResources(), SWIPE_REFRESH_PROGRESS_START_OFFSET_DP),
                Utils.pxFromDpInt(getResources(), SWIPE_REFRESH_PROGRESS_END_OFFSET_DP));

        // This is how much the bottom sheet sticks up by default and also decides height of WebView
        // We want to watch for navigation bar height changes (tablets on Android 12L can cause
        // these)

        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets systemInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                rootInsetsApplied = true;
                topInset = systemInsets.top;
                updateBottomSheetMargin(systemInsets.bottom);
                updateHeaderSpacerForCurrentSheetOffset();

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
                bottomSheet.setPadding(0, 0, 0, 0);
                setCommentsRecyclerSidePadding(leftPadding, rightPadding);

                View emptyView = binding.commentsEmpty;
                emptyView.setPadding(leftPadding, emptyView.getPaddingTop(), rightPadding, emptyView.getPaddingBottom());

                webViewController.setContainerPadding(0, systemInsets.top, 0, 0);

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(view);

        if (!showWebsite) {
            BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        syncOnBackPressedCallbackEnabledState();

        if (callback != null) {
            callback.onSwitchView(showWebsite);
        }

        if (integratedWebview) {
            swipeRefreshLayout.setEnabled(false);
            swipeRefreshLayout.setNestedScrollingEnabled(true);
        }

        progressIndicator = binding.webviewProgress;

        boolean shouldInitializeWebViewBeforeFirstDraw = integratedWebview && showWebsite;
        boolean shouldInitializeWebViewAfterFirstDraw = integratedWebview && !showWebsite;

        if (shouldInitializeWebViewBeforeFirstDraw) {
            webViewController.initialize();
        } else if (!integratedWebview) {
            BottomSheetBehavior.from(bottomSheet).setDraggable(false);
        }

        bottomSheet.setBackgroundColor(ContextCompat.getColor(requireContext(), ThemeUtils.getBackgroundColorResource(requireContext())));
        webViewController.setContainerBackgroundColor(ContextCompat.getColor(requireContext(), ThemeUtils.getBackgroundColorResource(requireContext())));

        comments = new ArrayList<>();
        Comment headerComment = new Comment();
        comments.add(headerComment); // header
        allComments = new ArrayList<>();
        allComments.add(headerComment);

        username = AccountUtils.getAccountUsername(getContext());

        scrollNavigation = binding.commentsScrollNavigation;
        scrollNavigationBaseBottomMargin = ((FrameLayout.LayoutParams) scrollNavigation.getLayoutParams()).bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(scrollNavigation, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());

                FrameLayout.LayoutParams scrollParams = (FrameLayout.LayoutParams) scrollNavigation.getLayoutParams();
                commentsBottomInset = insets.bottom;
                scrollParams.setMargins(scrollParams.leftMargin, scrollParams.topMargin, scrollParams.rightMargin,
                        commentsBottomInset + scrollNavigationBaseBottomMargin);
                updateSearchScrollTopFabPosition();

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(scrollNavigation);

        searchScrollTopFab = binding.commentsSearchScrollTopFab;
        FrameLayout.LayoutParams searchScrollTopFabParams = (FrameLayout.LayoutParams) searchScrollTopFab.getLayoutParams();
        searchScrollTopFabBaseBottomMargin = searchScrollTopFabParams.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(searchScrollTopFab, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());

                commentsBottomInset = insets.bottom;
                updateSearchScrollTopFabPosition();

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(searchScrollTopFab);
        commentNavigationController = new CommentNavigationController(this);
        searchScrollTopFab.setOnClickListener(v -> {
            if (commentNavigationController == null) {
                return;
            }
            if (SettingsUtils.shouldSmoothScrollComments(requireContext())) {
                commentNavigationController.smoothScrollTop();
            } else {
                commentNavigationController.scrollTop();
            }
            commentNavigationController.clearSearchedCommentScrollTopTarget();
        });

        showNavButtons = SettingsUtils.shouldShowNavigationButtons(getContext());
        showCommentsScrollbar = SettingsUtils.shouldUseCommentsScrollbar(getContext());
        updateNavigationVisibility();

        ImageButton scrollPrev = binding.commentsScrollPrevious;
        ImageButton scrollNext = binding.commentsScrollNext;
        ImageView scrollIcon = binding.commentsScrollIcon;

        scrollIcon.setOnClickListener(null);

        scrollNext.setOnClickListener(v -> navigateToNextComment());
        scrollNext.setOnLongClickListener(v -> {
            if (commentNavigationController == null) {
                return true;
            }
            if (SettingsUtils.shouldSmoothScrollComments(getContext())) {
                commentNavigationController.smoothScrollLast();
            } else {
                commentNavigationController.scrollLast();
            }
            return true;
        });

        scrollPrev.setOnClickListener(v -> navigateToPreviousComment());
        scrollPrev.setOnLongClickListener(v -> {
            if (commentNavigationController == null) {
                return true;
            }
            if (SettingsUtils.shouldSmoothScrollComments(getContext())) {
                commentNavigationController.smoothScrollTop();
            } else {
                commentNavigationController.scrollTop();
            }
            return true;
        });

        initializeRecyclerView();

        boolean restoreScrollFromCache = !showWebsite;

        preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (!rootInsetsApplied || !recyclerInsetsApplied) {
                    ViewCompat.requestApplyInsets(view);
                    if (recyclerView != null) {
                        ViewCompat.requestApplyInsets(recyclerView);
                    }
                    return false;
                }

                view.getViewTreeObserver().removeOnPreDrawListener(this);
                preDrawListener = null;
                startPostponedEnterTransition();
                view.post(() -> loadInitialStoryAndComments(restoreScrollFromCache));
                if (shouldInitializeWebViewAfterFirstDraw && webViewController != null) {
                    view.post(webViewController.getInitializeRunnable());
                }
                return true;
            }
        };
        view.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
    }

    private void syncOnBackPressedCallbackEnabledState() {
        if (backPressedCallback == null) {
            return;
        }
        if (commentActionOverlayController.isShowing()) {
            backPressedCallback.setEnabled(true);
            return;
        }
        if (webViewController != null && webViewController.isShowingCustomView()) {
            backPressedCallback.setEnabled(true);
            return;
        }
        boolean webViewVisible = webViewController != null && webViewController.hasWebView() &&
                BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED;
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

    private void updateCommentsScrollbarVisibility(boolean bottomSheetFullyExpanded) {
        if (recyclerView == null) {
            return;
        }
        recyclerView.setVerticalScrollBarEnabled(showCommentsScrollbar && bottomSheetFullyExpanded);
    }

    private void updateBottomSheetMargin(int navbarHeight) {
        int standardMargin = Utils.pxFromDpInt(getResources(), Utils.isTablet(getResources()) ? 81 : 68);

        BottomSheetBehavior.from(bottomSheet).setPeekHeight(standardMargin + navbarHeight);
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(0, 0, 0, standardMargin + navbarHeight);

        webViewController.setContainerLayoutParams(params);

        if (adapter != null) {
            adapter.setNavbarHeight(navbarHeight);
        }
    }

    private void updateHeaderStatusBarColor(int color) {
        commentsHeaderStatusBarColor = color;
        updateCommentsStatusBarAppearance();
    }

    private void syncCommentsStatusBarProtectionPreference() {
        if (!isAdded()) {
            return;
        }
        commentsPaneStatusBarColor = StatusBarProtectionUtils.getPaneBackgroundColor(requireContext());
        translucentStatusBarEnabled = SettingsUtils.shouldUseTranslucentStatusBar(requireContext());
        updateCommentsStatusBarAppearance();
    }

    private void updateCommentsStatusBarAppearance() {
        if (binding == null || getContext() == null) {
            return;
        }

        boolean showStatusBarProtection = shouldShowCommentsStatusBarProtection();
        boolean statusBarProtectionEnabled = translucentStatusBarEnabled && showStatusBarProtection;
        int statusBarColor = showStatusBarProtection ? getCurrentCommentsStatusBarColor() : commentsPaneStatusBarColor;
        if (!appliedStatusBarProtectionKnown
                || appliedStatusBarProtectionEnabled != statusBarProtectionEnabled
                || (statusBarProtectionEnabled && appliedStatusBarProtectionColor != statusBarColor)) {
            StatusBarProtectionUtils.setTopProtection(
                    binding.listProtection,
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
        if (bottomSheet == null) {
            return false;
        }

        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        return behavior.getState() == BottomSheetBehavior.STATE_EXPANDED
                && behavior.calculateSlideOffset() >= 0.9999f;
    }

    private int getCurrentCommentsStatusBarColor() {
        float headerCoverage = getHeaderStatusBarCoverage();
        return ColorUtils.blendARGB(commentsPaneStatusBarColor, commentsHeaderStatusBarColor, headerCoverage);
    }

    private float getHeaderStatusBarCoverage() {
        if (recyclerView == null || topInset <= 0) {
            return 0f;
        }

        RecyclerView.ViewHolder headerHolder = recyclerView.findViewHolderForAdapterPosition(0);
        if (!(headerHolder instanceof CommentsRecyclerViewAdapter.HeaderViewHolder)) {
            return 0f;
        }

        View headerView = headerHolder.itemView;
        int[] rootLocation = new int[2];
        int[] headerLocation = new int[2];
        binding.listProtection.getLocationOnScreen(rootLocation);
        headerView.getLocationOnScreen(headerLocation);

        int headerTop = headerLocation[1] - rootLocation[1];
        int headerBottom = headerTop + headerView.getHeight();
        int overlap = Math.min(headerBottom, topInset) - Math.max(headerTop, 0);
        if (overlap <= 0) {
            return 0f;
        }
        return Math.min(1f, overlap / (float) topInset);
    }

    private void setSheetButtonsContentAlpha(float alpha) {
        if (adapter == null) {
            return;
        }
        adapter.setBoundSheetButtonsContentAlpha(alpha);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // this is to make sure that action buttons in header get updated padding on rotations...
        // yes it's ugly, I know
        if (getContext() != null && Utils.isTablet(getResources()) && adapter != null) {
            adapter.notifyItemChanged(0);
        }
        refreshCommentActionOverlayForConfiguration();
    }

    private void initializeRecyclerView() {
        adapter = new CommentsRecyclerViewAdapter(
                integratedWebview,
                bottomSheet,
                requireActivity().getSupportFragmentManager(),
                comments,
                story,
                SettingsUtils.shouldCollapseParent(getContext()),
                SettingsUtils.shouldShowThumbnails(getContext()),
                SettingsUtils.shouldShowCommentsHeaderPreviewImage(getContext()),
                SettingsUtils.shouldTintCommentsHeader(getContext()),
                SettingsUtils.getPreferredPaletteTintConfigKey(getContext()),
                username,
                SettingsUtils.getPreferredCommentTextSize(getContext()),
                SettingsUtils.getPreferredCommentDepthIndicatorMode(getContext()),
                SettingsUtils.shouldShowNavigationButtons(getContext()),
                SettingsUtils.getPreferredFont(getContext()),
                isFeatureSupported(WebViewFeature.FORCE_DARK) || WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING),
                SettingsUtils.shouldShowTopLevelDepthIndicator(getContext()),
                ThemeUtils.getPreferredTheme(getContext()),
                Utils.isTablet(getResources()),
                SettingsUtils.getPreferredFaviconProvider(getContext()),
                SettingsUtils.shouldSwapCommentLongPressTap(getContext()),
                SettingsUtils.shouldUseCardCommentDisplayStyle(getContext()),
                SettingsUtils.shouldCollectLinksInComments(getContext()),
                AccountUtils.hasAccountDetails(getContext()),
                this);
        adapter.lastRefreshed = lastLoaded;
        adapter.setCommentsByOpFilterActive(commentsByOpFilterActive);
        if (webViewController != null) {
            adapter.setReaderModeEnabled(webViewController.isReaderModeEnabled());
            adapter.setReaderModeAvailable(webViewController.isReaderModeAvailable());
        }
        adapter.setHeaderBackgroundColorListener(this::updateHeaderStatusBarColor);
        adapter.loadUserTags(requireContext());

        adapter.setOnHeaderClickListener(story1 -> Utils.launchCustomTab(getActivity(), story1.url));

        adapter.setOnCommentClickListener((comment, index, commentView) -> {
            comment.expanded = !comment.expanded;
            adapter.invalidateCommentVisibility();

            int offset = 0;
            int lastChildIndex = adapter.getIndexOfLastChild(comment.depth, index);
            if (index == lastChildIndex && !adapter.collapseParent) {
                return;
            }

            final RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(index);
            if (holder != null && !adapter.collapseParent && holder instanceof CommentsRecyclerViewAdapter.ItemViewHolder) {
                // if we can reach the ViewHolder (which we should), we can animate the
                // hiddenIndicator ourselves to get around a FULL item refresh (which flashes
                // all the text which we don't want)
                offset = 1;
                final TextView hiddenIndicator = ((CommentsRecyclerViewAdapter.ItemViewHolder) holder).commentHiddenCount;
                int shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

                hiddenIndicator.setText("+" + (lastChildIndex - index));

                if (comment.expanded) {
                    // fade out
                    hiddenIndicator.setVisibility(View.VISIBLE);
                    hiddenIndicator.setAlpha(1f);
                    hiddenIndicator.animate()
                            .alpha(0f)
                            .setDuration(shortAnimationDuration)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    hiddenIndicator.setVisibility(View.INVISIBLE);
                                }
                            });
                } else {
                    // fade in
                    hiddenIndicator.setVisibility(View.VISIBLE);
                    hiddenIndicator.setAlpha(0f);
                    hiddenIndicator.animate()
                            .alpha(1f)
                            .setDuration(shortAnimationDuration)
                            .setListener(null);
                }
            } else {
                adapter.notifyItemChanged(index);
            }

            if (lastChildIndex != index || adapter.collapseParent) {
                // + 1 since if we have 1 subcomment we have changed the parent and the child
                adapter.notifyItemRangeChanged(index + 1, lastChildIndex - index + 1 - offset);
            }

            // next couple of lines makes it so that if we hide parents and click the comment at
            // the top of the screen, we scroll down to the next comment automatically
            // this is only applicable if we're hiding a comment
            if (layoutManager != null && !comment.expanded && adapter.collapseParent) {
                int firstVisible = layoutManager.findFirstVisibleItemPosition();
                int clickedIndex = comments.indexOf(comment);

                // if we clicked the top one and the new top level comment exists
                if (clickedIndex == firstVisible && comments.size() > lastChildIndex + 1) {
                    if (commentNavigationController != null) {
                        commentNavigationController.startCommentSmoothScrollWithScaledSpeed(lastChildIndex + 1);
                    }

                }
            }
        });

        adapter.setOnCommentLongClickListener(this);
        adapter.setRetryListener(this);

        adapter.setOnHeaderActionClickListener(new CommentsRecyclerViewAdapter.HeaderActionClickListener() {
            @Override
            @SuppressWarnings("deprecation")
            public void onActionClicked(int flag, View clickedView) {
                switch (flag) {
                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_USER:
                        clickUser();
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_COMMENT:
                        clickComment();
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_VOTE:
                        clickVote(clickedView);
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_FAVORITE:
                        clickFavorite(clickedView);
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_SHARE:
                        clickShare(clickedView);
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_MORE:
                        clickMore(clickedView);
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_REFRESH:
                        if (webViewController.isShowingOfflineOrCachedPage() && webViewController.hasLastFailedUrl()) {
                            webViewController.retryLastFailedUrl();
                        } else {
                            webViewController.reload();
                        }
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_EXPAND:
                        BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
                        break;
                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_BROWSER:
                        clickBrowser();
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_READER:
                        webViewController.toggleReaderMode();
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_INVERT:
                        // This whole thing should only be visible for SDK_INT larger than Q (29)
                        // We first check the "new" version of dark mode, algorithmic darkening
                        // this requires the isDarkMode thing to be true for the theme which we
                        // have set
                        webViewController.toggleDarkMode();

                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_RESET_OP_FILTER:
                        resetCommentsByOpFilter();
                        break;
                }
            }
        });

        if (integratedWebview) {
            recyclerView = recyclerViewRegular;
        } else {
            recyclerView = recyclerViewSwipe;
        }

        layoutManager = new LinearLayoutManager(getContext());

        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerViewScrollListener = new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateCommentsStatusBarAppearance();

                if (integratedWebview) {
                    // Shouldn't be neccessary but once I was stuck in comments and couldn't swipe up.
                    // This just updates a flag so there's no performance impact
                    if (dy != 0 && callback != null) {
                        callback.onSwitchView(false);
                    }
                    BottomSheetBehavior.from(bottomSheet).setDraggable(recyclerView.computeVerticalScrollOffset() == 0);
                }

                // Note: Infinite scroll removed - all comments now load at once via AlgoliaFallbackManager
                if (commentNavigationController != null) {
                    commentNavigationController.updateSearchedCommentScrollTopVisibility(
                            !commentNavigationController.isSearchedCommentScrollTopPending());
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (commentNavigationController != null) {
                        commentNavigationController.updateSearchedCommentScrollTopVisibility(true);
                        commentNavigationController.highlightPendingSearchedCommentIfReady();
                    }
                }
            }
        };
        recyclerView.addOnScrollListener(recyclerViewScrollListener);
        recyclerViewLayoutChangeListener = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateCommentsStatusBarAppearance();
        recyclerView.addOnLayoutChangeListener(recyclerViewLayoutChangeListener);
        smoothScroller = new LinearSmoothScroller(requireContext()) {
            public PointF computeScrollVectorForPosition(int targetPosition) {
                return layoutManager.computeScrollVectorForPosition(targetPosition);
            }

            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
            }

            @Override
            protected int calculateTimeForScrolling(int dx) {
                int speedMultiplier = commentNavigationController == null ? 1 : commentNavigationController.getSmoothScrollSpeedMultiplier();
                return Math.max(1, super.calculateTimeForScrolling(dx) / speedMultiplier);
            }

            @Override
            public int calculateDyToMakeVisible(View view, int snapPreference) {
                // This is to make sure that scrollTo calls work properly
                return super.calculateDyToMakeVisible(view, snapPreference) + topInset;
            }

        };

        if (!SettingsUtils.shouldUseCommentsAnimation(getContext())) {
            recyclerView.setItemAnimator(null);
        }

        // For some reason, I could only get the scrollbars to show up when they are enabled via
        // xml but disabling them in java worked so this is an okay solution...
        updateCommentsScrollbarVisibility(BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_EXPANDED);

        recyclerBottomSheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View view, int newState) {
                syncOnBackPressedCallbackEnabledState();
                updateCommentsScrollbarVisibility(newState == BottomSheetBehavior.STATE_EXPANDED);
                updateCommentsStatusBarAppearance();
            }

            @Override
            public void onSlide(@NonNull View view, float slideOffset) {
                updateCommentsScrollbarVisibility(BottomSheetBehavior.from(view).getState() == BottomSheetBehavior.STATE_EXPANDED
                        && slideOffset >= 0.9999f);
                // Updating padding (of recyclerview) doesn't work because it causes incorrect scroll position for recycler.
                // Updating scroll together with padding causes severe lags and other problems.
                // So don't update padding at all on slide and instead just change whole view position (by translationY on recyclerView)
                // ... is something you could do but this means that the touch target of the recyclerview is not aligned with the view
                // so we go back to the padding but instead just put a view above the recyclerview (a spacer) and change its height!
                // ... is what you could do if you were stupid! This would mean that the recyclerView starts BELOW the status bar
                // breaking transparent status bar. Instead, the spacing needs to be _within_ the recyclerview header!
                // NOTE: this also needs to be set in onBindViewHolder of the adapter to stay up to date if the header item
                // should be refreshed
                updateHeaderSpacer(slideOffset);
            }
        };
        BottomSheetBehavior.from(bottomSheet).addBottomSheetCallback(recyclerBottomSheetCallback);

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                recyclerInsetsApplied = true;
                topInset = insets.top;

                updateHeaderSpacerForCurrentSheetOffset();
                updateCommentsStatusBarAppearance();

                int paddingBottom = insets.bottom + getResources().getDimensionPixelSize(showNavButtons ? R.dimen.comments_bottom_navigation : R.dimen.comments_bottom_standard);
                recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(), recyclerView.getPaddingRight(), paddingBottom);

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(recyclerView);

        updateHeaderSpacerForCurrentSheetOffset();
        recyclerView.setAdapter(adapter);
        recyclerView.post(this::updateHeaderSpacerForCurrentSheetOffset);
        recyclerView.post(this::updateCommentsStatusBarAppearance);

        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_COMMENT, 300);
        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_COMMENT_CARD, 300);
        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_COLLAPSED, 600);
        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_HEADER, 1);
    }

    private void setCommentsRecyclerSidePadding(int leftPadding, int rightPadding) {
        boolean regularChanged = setRecyclerSidePadding(recyclerViewRegular, leftPadding, rightPadding);
        boolean swipeChanged = setRecyclerSidePadding(recyclerViewSwipe, leftPadding, rightPadding);
        if ((regularChanged || swipeChanged) && adapter != null) {
            adapter.notifyItemChanged(0);
        }
    }

    private boolean setRecyclerSidePadding(@Nullable RecyclerView targetRecyclerView,
                                        int leftPadding,
                                        int rightPadding) {
        if (targetRecyclerView == null) {
            return false;
        }

        if (targetRecyclerView.getPaddingLeft() == leftPadding
                && targetRecyclerView.getPaddingRight() == rightPadding) {
            return false;
        }

        targetRecyclerView.setPadding(
                leftPadding,
                targetRecyclerView.getPaddingTop(),
                rightPadding,
                targetRecyclerView.getPaddingBottom());
        return true;
    }


    @Override
    public void onStart() {
        super.onStart();

        if (callback == null && getActivity() instanceof BottomSheetFragmentCallback) {
            callback = (BottomSheetFragmentCallback) getActivity();
        }

        if (callback != null) {
            callback.onSwitchView(BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED);
        }

        if (adapter != null) {
            Context ctx = requireContext();
            boolean updateHeader = false;
            boolean updateComments = false;

            if (adapter.reloadUserTagsIfChanged(ctx)) {
                updateHeader = true;
                updateComments = true;
            }

            if (adapter.collapseParent != SettingsUtils.shouldCollapseParent(ctx)) {
                adapter.collapseParent = !adapter.collapseParent;
                updateComments = true;
            }

            if (adapter.showThumbnail != SettingsUtils.shouldShowThumbnails(ctx)) {
                adapter.showThumbnail = !adapter.showThumbnail;
                updateHeader = true;
            }

            boolean showHeaderPreviewImage = SettingsUtils.shouldShowCommentsHeaderPreviewImage(ctx);
            if (adapter.showHeaderPreviewImage != showHeaderPreviewImage) {
                adapter.showHeaderPreviewImage = showHeaderPreviewImage;
                updateHeader = true;
            }

            boolean tintHeader = SettingsUtils.shouldTintCommentsHeader(ctx);
            if (adapter.tintHeader != tintHeader) {
                adapter.tintHeader = tintHeader;
                updateHeader = true;
            }

            String paletteTintMode = SettingsUtils.getPreferredPaletteTintConfigKey(ctx);
            if (!paletteTintMode.equals(adapter.paletteTintMode)) {
                adapter.paletteTintMode = paletteTintMode;
                updateHeader = true;
            }

            float preferredCommentTextSize = SettingsUtils.getPreferredCommentTextSize(ctx);
            if (Float.compare(adapter.preferredTextSize, preferredCommentTextSize) != 0) {
                adapter.preferredTextSize = preferredCommentTextSize;
                updateHeader = true;
                updateComments = true;
            }

            if (!adapter.commentDepthIndicatorMode.equals(SettingsUtils.getPreferredCommentDepthIndicatorMode(ctx))) {
                adapter.commentDepthIndicatorMode = SettingsUtils.getPreferredCommentDepthIndicatorMode(ctx);
                updateComments = true;
            }

            if (!adapter.font.equals(SettingsUtils.getPreferredFont(ctx))) {
                adapter.font = SettingsUtils.getPreferredFont(ctx);
                updateHeader = true;
                updateComments = true;
            }

            if (adapter.showTopLevelDepthIndicator != SettingsUtils.shouldShowTopLevelDepthIndicator(ctx)) {
                adapter.showTopLevelDepthIndicator = SettingsUtils.shouldShowTopLevelDepthIndicator(ctx);
                updateComments = true;
            }

            if (adapter.swapLongPressTap != SettingsUtils.shouldSwapCommentLongPressTap(ctx)) {
                adapter.swapLongPressTap = SettingsUtils.shouldSwapCommentLongPressTap(ctx);
            }

            if (adapter.cardStyle != SettingsUtils.shouldUseCardCommentDisplayStyle(ctx)) {
                adapter.cardStyle = SettingsUtils.shouldUseCardCommentDisplayStyle(ctx);
                updateComments = true;
            }

            if (adapter.collectReferenceLinks != SettingsUtils.shouldCollectLinksInComments(ctx)) {
                adapter.collectReferenceLinks = SettingsUtils.shouldCollectLinksInComments(ctx);
                updateHeader = true;
                updateComments = true;
            }

            if (!adapter.theme.equals(ThemeUtils.getPreferredTheme(ctx))) {
                adapter.theme = ThemeUtils.getPreferredTheme(ctx);
                updateHeader = true;
                updateComments = true;

                // darkThemeActive might change because the system changed from day to night mode.
                // In that case, we'll need to update the sheet and webview background color since
                // that will have changed too.
                if (bottomSheet != null) {
                    bottomSheet.setBackgroundColor(ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx)));
                }
                if (webViewController != null) {
                    webViewController.setContainerBackgroundColor(ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx)));
                }
            }
            if (updateHeader) {
                adapter.notifyItemChanged(0);
            }
            if (updateComments) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean shouldShowUpdate = SettingsUtils.shouldAlwaysShowTapToRefresh(getContext())
                || (lastLoaded != 0 && (System.currentTimeMillis() - lastLoaded) > 1000 * 60 * 60 && !Utils.timeInSecondsMoreThanTwoHoursAgo(story.time));
        if (adapter != null) {
            adapter.lastRefreshed = lastLoaded;
            boolean hasAccountDetails = AccountUtils.hasAccountDetails(getContext());
            if (adapter.hasAccountDetails != hasAccountDetails) {
                adapter.hasAccountDetails = hasAccountDetails;
                adapter.notifyItemChanged(0);
            }
            if (adapter.showUpdate != shouldShowUpdate) {
                adapter.showUpdate = shouldShowUpdate;
                if (shouldShowUpdate) {
                    if (commentNavigationController != null) {
                        commentNavigationController.clearSearchedCommentScrollTopTarget();
                    }
                }
                adapter.notifyItemChanged(0);
            }
        }
        if (adapter != null) {
            adapter.notifyItemChanged(0);
        }
        saveScreenHeight();
        refreshCommentActionOverlayForConfiguration();
        syncCommentsStatusBarProtectionPreference();
    }

    private void refreshCommentActionOverlayForConfiguration() {
        commentActionOverlayController.refreshForConfiguration();
    }

    @Override
    public void onStop() {
        super.onStop();

        if (layoutManager != null) {
            if (layoutManager.findFirstVisibleItemPosition() != RecyclerView.NO_POSITION) {
                if (MainActivity.commentsScrollProgresses == null) {
                    MainActivity.commentsScrollProgresses = new ArrayList<>();
                }
                // Let's check all scrollProgresses in memory to see if we should change an active
                // object
                for (int i = 0; i < MainActivity.commentsScrollProgresses.size(); i++) {
                    CommentsScrollProgress scrollProgress = MainActivity.commentsScrollProgresses.get(i);
                    if (scrollProgress.storyId == story.id) {
                        // If we find, overwrite the old thing and stop completely
                        MainActivity.commentsScrollProgresses.set(i, recordScrollProgress());
                        return;
                    }
                }

                // If we didn't find anything, let's add it ourselves
                MainActivity.commentsScrollProgresses.add(recordScrollProgress());
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        int visibleCommentActionId = commentActionOverlayController.getRestorableCommentId();
        if (visibleCommentActionId != CommentActionOverlayController.NO_COMMENT_ID) {
            outState.putInt(STATE_COMMENT_ACTION_COMMENT_ID, visibleCommentActionId);
        }
        if (adBlockDisabledForSession) {
            outState.putBoolean(STATE_ADBLOCK_DISABLED_FOR_SESSION, true);
        }
        outState.putString(STATE_COMMENT_SORTING, getCurrentCommentSorting());
    }

    private void saveScreenHeight() {
        SCREEN_HEIGHT_IN_PIXELS = Resources.getSystem().getDisplayMetrics().heightPixels;
    }

    private void loadHeaderSpacer() {
        if (recyclerView == null) {
            return;
        }

        RecyclerView.ViewHolder viewHolder = recyclerView.findViewHolderForAdapterPosition(0);
        if (viewHolder instanceof CommentsRecyclerViewAdapter.HeaderViewHolder) {
            headerSpacer = ((CommentsRecyclerViewAdapter.HeaderViewHolder) viewHolder).spacer;
        }
    }

    private void updateHeaderSpacerForCurrentSheetOffset() {
        if (bottomSheet == null) {
            return;
        }

        updateHeaderSpacer(getCurrentBottomSheetSlideOffsetForHeader());
    }

    private float getCurrentBottomSheetSlideOffsetForHeader() {
        if (bottomSheet == null) {
            return 1f;
        }

        BottomSheetBehavior<LinearLayout> behavior = BottomSheetBehavior.from(bottomSheet);
        int state = behavior.getState();
        if (state == BottomSheetBehavior.STATE_COLLAPSED) {
            return 0f;
        }
        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            return 1f;
        }
        return behavior.calculateSlideOffset();
    }

    private void updateHeaderSpacer(float slideOffset) {
        if (adapter == null) {
            return;
        }

        float sanitizedSlideOffset = Float.isNaN(slideOffset) ? 1f : Math.max(0f, Math.min(1f, slideOffset));
        int spacerHeight = Math.round(topInset * sanitizedSlideOffset);
        adapter.spacerHeight = spacerHeight;
        updateCommentsStatusBarAppearance();

        loadHeaderSpacer();
        if (headerSpacer == null) {
            return;
        }

        ViewGroup.LayoutParams params = headerSpacer.getLayoutParams();
        if (params != null && params.height == spacerHeight) {
            return;
        }

        headerSpacer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, spacerHeight));
    }

    private CommentsScrollProgress recordScrollProgress() {
        CommentsScrollProgress scrollProgress = new CommentsScrollProgress();

        int lastScrollIndex = layoutManager.findFirstVisibleItemPosition();
        scrollProgress.storyId = story.id;
        scrollProgress.topCommentId = comments.get(lastScrollIndex).id;

        scrollProgress.collapsedIDs = new HashSet<>();

        for (Comment c : comments) {
            if (!c.expanded) {
                scrollProgress.collapsedIDs.add(c.id);
            }
        }

        View firstVisibleItem = recyclerView.getChildAt(0);
        scrollProgress.topCommentOffset = (firstVisibleItem == null) ? 0 : (firstVisibleItem.getTop() - recyclerView.getPaddingTop());

        return scrollProgress;
    }

    private void restoreScrollProgress(CommentsScrollProgress scrollProgress) {
        for (Comment c : comments) {
            if (c.id == scrollProgress.topCommentId) {
                layoutManager.scrollToPositionWithOffset(comments.indexOf(c), scrollProgress.topCommentOffset);
            }
            c.expanded = !scrollProgress.collapsedIDs.contains(c.id);
        }
    }

    private void scrollToTargetComment() {
        if (scrollToCommentId == -1) return;
        for (int i = 0; i < comments.size(); i++) {
            if (comments.get(i).id == scrollToCommentId) {
                int targetIndex = i;
                expandParentsForComment(comments.get(i));
                RecyclerView currentRecyclerView = recyclerView;
                LinearLayoutManager currentLayoutManager = layoutManager;
                if (currentRecyclerView == null || currentLayoutManager == null) {
                    scrollToCommentId = -1;
                    return;
                }
                // +1 to account for header at adapter position 0
                currentRecyclerView.post(() -> {
                    if (recyclerView == currentRecyclerView && layoutManager == currentLayoutManager) {
                        currentLayoutManager.scrollToPositionWithOffset(targetIndex + 1, topInset);
                    }
                });
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

        if (expandedAny && adapter != null) {
            adapter.invalidateCommentVisibility();
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onDestroyView() {
        commentActionOverlayController.removeNow();
        if (commentNavigationController != null) {
            commentNavigationController.clear();
        }
        if (originalStatusBarColorCaptured && getActivity() != null) {
            requireActivity().getWindow().setStatusBarColor(originalStatusBarColor);
            originalStatusBarColorCaptured = false;
        }

        View rootView = getView();
        if (rootView != null) {
            if (preDrawListener != null && rootView.getViewTreeObserver().isAlive()) {
                rootView.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
            }
            ViewCompat.setOnApplyWindowInsetsListener(rootView, null);
        }
        preDrawListener = null;

        if (backPressedCallback != null) {
            backPressedCallback.remove();
            backPressedCallback = null;
        }

        if (bottomSheet != null) {
            BottomSheetBehavior<View> bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            if (recyclerBottomSheetCallback != null) {
                bottomSheetBehavior.removeBottomSheetCallback(recyclerBottomSheetCallback);
            }
            if (webViewController != null && webViewController.getBottomSheetCallback() != null) {
                bottomSheetBehavior.removeBottomSheetCallback(webViewController.getBottomSheetCallback());
            }
        }

        if (recyclerView != null) {
            if (recyclerViewScrollListener != null) {
                recyclerView.removeOnScrollListener(recyclerViewScrollListener);
            }
            if (recyclerViewLayoutChangeListener != null) {
                recyclerView.removeOnLayoutChangeListener(recyclerViewLayoutChangeListener);
            }
            ViewCompat.setOnApplyWindowInsetsListener(recyclerView, null);
            recyclerView.stopScroll();
            recyclerView.setAdapter(null);
            recyclerView.setLayoutManager(null);
        }
        if (recyclerViewRegular != null && recyclerViewRegular != recyclerView) {
            recyclerViewRegular.setAdapter(null);
            recyclerViewRegular.setLayoutManager(null);
        }
        if (recyclerViewSwipe != null && recyclerViewSwipe != recyclerView) {
            recyclerViewSwipe.setAdapter(null);
            recyclerViewSwipe.setLayoutManager(null);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(null);
        }
        if (scrollNavigation != null) {
            ViewCompat.setOnApplyWindowInsetsListener(scrollNavigation, null);
        }
        if (searchScrollTopFab != null) {
            ViewCompat.setOnApplyWindowInsetsListener(searchScrollTopFab, null);
            searchScrollTopFab.setOnClickListener(null);
        }
        if (queue != null) {
            queue.cancelAll(requestTag);
        }
        fallbackManager = null;
        if (webViewController != null) {
            webViewController.onDestroyView(rootView);
        }

        clearViewReferences();

        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        callback = null;
        super.onDetach();
    }

    private void clearViewReferences() {
        adapter = null;
        binding = null;
        swipeRefreshLayout = null;
        recyclerView = null;
        recyclerViewSwipe = null;
        recyclerViewRegular = null;
        layoutManager = null;
        recyclerViewScrollListener = null;
        recyclerViewLayoutChangeListener = null;
        recyclerBottomSheetCallback = null;
        smoothScroller = null;
        commentNavigationController = null;
        scrollNavigation = null;
        searchScrollTopFab = null;
        progressIndicator = null;
        bottomSheet = null;
        headerSpacer = null;
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

    @Override
    public void onRetry() {
        if (!isCommentsViewActive() || adapter.story == null) {
            Log.w(TAG, "Retry ignored: commentsViewActive=" + isCommentsViewActive()
                    + ", adapterPresent=" + (adapter != null)
                    + ", storyPresent=" + (adapter != null && adapter.story != null));
            return;
        }
        Log.d(TAG, "Retry requested for storyId=" + adapter.story.id);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }
        loadStoryAndComments(adapter.story.id, null);
    }

    @Override
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

        loadStoryAndComments(story.id, cachedResponse);

        // if this isn't here, the addition of the text appears to scroll the recyclerview down a little
        recyclerView.scrollToPosition(0);

        if (cachedResponse != null) {
            handleJsonResponse(story.id, cachedResponse, false, false, restoreScrollFromCache);
        }
    }

    private void loadStoryAndComments(final int id, final String oldCachedResponse) {
        Context context = getContext();
        if (context == null || queue == null || !isCommentsViewActive()) {
            Log.w(TAG, "Skipping comments load for storyId=" + id
                    + ": contextPresent=" + (context != null)
                    + ", queuePresent=" + (queue != null)
                    + ", commentsViewActive=" + isCommentsViewActive());
            return;
        }

        Log.d(TAG, "Loading comments for storyId=" + id + ", hasCachedResponse=" + (oldCachedResponse != null));
        lastLoaded = System.currentTimeMillis();
        if (adapter != null) {
            adapter.lastRefreshed = lastLoaded;
            if (adapter.showUpdate) {
                adapter.showUpdate = false;
                adapter.notifyItemChanged(0);
            }
        }

        // Initialize fallback manager
        fallbackManager = new AlgoliaFallbackManager(context, queue, requestTag, filteredUsers, new AlgoliaFallbackManager.FallbackListener() {
            @Override
            public void onAlgoliaSuccess(String response) {
                if (!isCommentsViewActive()) {
                    Log.w(TAG, "Ignoring Algolia success because comments view is inactive for storyId=" + id);
                    return;
                }
                Log.d(TAG, "Algolia comments load succeeded for storyId=" + id
                        + ", responseLength=" + (response == null ? 0 : response.length()));
                if (TextUtils.isEmpty(oldCachedResponse) || !oldCachedResponse.equals(response)) {
                    handleJsonResponse(id, response, true, oldCachedResponse == null, false);
                }
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onAlgoliaFailed(boolean noInternet) {
                if (!isCommentsViewActive()) {
                    Log.w(TAG, "Ignoring Algolia failure because comments view is inactive for storyId=" + id);
                    return;
                }
                Log.w(TAG, "Algolia comments load failed for storyId=" + id + ", noInternet=" + noInternet);
                adapter.loadingFailed = true;
                adapter.loadingFailedServerError = !noInternet;
                adapter.commentsLoaded = true;
                adapter.notifyItemChanged(0);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onUsingFallback() {
                Context context = getContext();
                if (context != null && isCommentsViewActive()) {
                    Toast.makeText(context, "Algolia API failed, using official HN API", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onHNAPIStoryLoaded(Story loadedStory) {
                if (!isCommentsViewActive()) {
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

                // Reset comments
                if (allComments != null && allComments.size() > 1) {
                    allComments.subList(1, allComments.size()).clear();
                }
                int oldSize = comments.size();
                if (oldSize > 1) {
                    comments.subList(1, oldSize).clear();
                    adapter.invalidateCommentLookup();
                    adapter.notifyItemRangeRemoved(1, oldSize - 1);
                }

                adapter.loadingFailed = false;
                adapter.loadingFailedServerError = false;
                adapter.notifyItemChanged(0);
                maybeLoadPollOptions();
            }

            @Override
            public void onHNAPIFailed() {
                if (!isCommentsViewActive()) {
                    Log.w(TAG, "Ignoring HN API failure because comments view is inactive for storyId=" + id);
                    return;
                }
                Log.w(TAG, "HN API comments load failed for storyId=" + id);
                adapter.loadingFailed = true;
                adapter.loadingFailedServerError = false;
                adapter.commentsLoaded = true;
                adapter.notifyItemChanged(0);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onAllCommentsLoaded(List<Comment> loadedComments) {
                if (!isCommentsViewActive()) {
                    Log.w(TAG, "Ignoring loaded comments because comments view is inactive for storyId=" + id
                            + ", loadedCount=" + (loadedComments == null ? 0 : loadedComments.size()));
                    return;
                }
                Log.d(TAG, "Loaded comments from fallback path for storyId=" + id
                        + ", loadedCount=" + loadedComments.size());
                // Add all comments at once in proper tree order
                allComments.addAll(loadedComments);
                updateDefaultCommentSortOrder(allComments);
                CommentSorter.sort(allComments, getCurrentCommentSorting());
                applyDisplayedComments(getDisplayedCommentsForCurrentFilter(allComments));
                adapter.commentsLoaded = true;
                updateNavigationVisibility();
                adapter.notifyItemChanged(0);
                swipeRefreshLayout.setRefreshing(false);
                recyclerView.post(() -> {
                    if (!isCommentsViewActive()) {
                        return;
                    }
                    scrollToTargetComment();
                    commentActionOverlayController.restorePending();
                });
            }
        });

        fallbackManager.loadComments(id, oldCachedResponse);

        maybeLoadPollOptions();

        if (linkPreviewController != null) {
            linkPreviewController.loadNetworkPreviews(context);
        }
    }

    private void onLinkPreviewChanged() {
        if (adapter != null) {
            adapter.notifyItemChanged(0);
        }
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

                                    adapter.notifyItemChanged(0);
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

    private void handleJsonResponse(final int id, final String response, final boolean cache, final boolean forceHeaderRefresh, boolean restoreScroll) {
        if (!isCommentsViewActive()) {
            return;
        }

        int oldCommentCount = getAllCommentsSource().size();
        boolean updateHeaderAfterLoad = false;

        // This is what we get if the Algolia API has not indexed the post,
        // we should attempt to show the user an option to switch API:s in this
        // server error case
        // Actually, the response being a 404 should be captured by another part
        // so this should never be called. Not that I dare remove it...
        if (response.equals(JSONParser.ALGOLIA_ERROR_STRING)) {
            adapter.loadingFailed = true;
            adapter.loadingFailedServerError = true;
            adapter.notifyItemChanged(0);
            swipeRefreshLayout.setRefreshing(false);
        }

        try {
            JSONParser.AlgoliaCommentsResponse parsedResponse = JSONParser.parseAlgoliaCommentsResponse(response, story.kids, filteredUsers);
            applyParsedComments(parsedResponse.comments);

            boolean storyChanged = parsedResponse.updateStoryInformation(story, forceHeaderRefresh, oldCommentCount);
            if (storyChanged || forceHeaderRefresh) {
                updateHeaderAfterLoad = true;
            }
            maybeLoadPollOptions();

            integratedWebview = prefIntegratedWebview && story.isLink;

            if (integratedWebview && !adapter.integratedWebview) {
                // It's the first time, so we need to re-initialize the recyclerview too
                webViewController.setIntegratedWebview(true);
                webViewController.initialize();
                initializeRecyclerView();
            }

            adapter.loadingFailed = false;
            adapter.loadingFailedServerError = false;

            // Seems like loading went well, lets cache the result
            if (cache) {
                Utils.cacheStory(getContext(), id, response);
            } else if (restoreScroll) {
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

        } catch (IOException e) {
            e.printStackTrace();
            // Show some error, remove things?
            adapter.loadingFailed = true;
            adapter.loadingFailedServerError = false;
            adapter.notifyItemChanged(0);
            swipeRefreshLayout.setRefreshing(false);
        }

        adapter.commentsLoaded = true;
        if (updateHeaderAfterLoad) {
            refreshHeaderAfterStoryLoad();
        }
        updateNavigationVisibility();
        recyclerView.post(() -> {
            if (!isCommentsViewActive()) {
                return;
            }
            scrollToTargetComment();
            commentActionOverlayController.restorePending();
        });
    }

    private void refreshHeaderAfterStoryLoad() {
        if (!isCommentsViewActive()) {
            return;
        }

        adapter.updateBoundHeaderStoryViews();
    }

    private boolean isCommentsViewActive() {
        return getView() != null
                && adapter != null
                && swipeRefreshLayout != null
                && recyclerView != null
                && comments != null
                && allComments != null;
    }

    private void applyParsedComments(List<Comment> parsedComments) {
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
        applyDisplayedComments(getDisplayedCommentsForCurrentFilter(allComments), oldComments);
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
        if (adapter != null) {
            adapter.setCommentsByOpFilterActive(active);
        }
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
        androidx.recyclerview.widget.DiffUtil.DiffResult diffResult = CommentListDiff.calculateDiff(oldComments, nextComments);

        comments.clear();
        comments.addAll(nextComments);
        if (adapter != null) {
            adapter.invalidateCommentLookup();
            diffResult.dispatchUpdatesTo(adapter);
            adapter.updateBoundHeaderStoryViews();
        }
        updateNavigationVisibility();
    }

    public void clickBrowser() {
        webViewController.openCurrentOrStoryUrlInBrowser();
    }

    public void clickShare(View view) {
        PopupMenu popup = new PopupMenu(requireActivity(), view);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int itemId = item.getItemId();

                if (itemId == R.id.menu_link) {
                    startActivity(ShareUtils.getShareIntent(adapter.story.url));
                } else if (itemId == R.id.menu_link_title) {
                    startActivity(ShareUtils.getShareIntentWithTitle(adapter.story.title, adapter.story.url));
                } else if (itemId == R.id.menu_hacker_news_link) {
                    startActivity(ShareUtils.getShareIntent(adapter.story.id));
                } else if (itemId == R.id.menu_hacker_news_link_title) {
                    startActivity(ShareUtils.getShareIntentWithTitle(adapter.story.title, adapter.story.id));
                } else if (itemId == R.id.menu_link_title_and_hacker_news_link_title) {
                    startActivity(ShareUtils.getShareIntentWithTitle(adapter.story.title, adapter.story.id, adapter.story.url));
                }


                return true;
            }
        });
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.share_menu, popup.getMenu());

        if (!adapter.story.isLink) {
            popup.getMenu().findItem(R.id.menu_link).setVisible(false);
            popup.getMenu().findItem(R.id.menu_link_title).setVisible(false);
            popup.getMenu().findItem(R.id.menu_link_title_and_hacker_news_link_title).setVisible(false);
        }

        popup.show();
    }

    public void clickMore(View view) {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }

        PopupMenu popup = new PopupMenu(requireActivity(), view);

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.menu_refresh) {
                    onRetry();
                } else if (id == R.id.menu_open_parent) {
                    if (story.parentId > 0) {
                        Utils.openCommentsActivity(story.parentId, -1, requireContext());
                    }
                } else if (id == R.id.menu_open_top_level) {
                    if (story.commentMasterId > 0) {
                        Utils.openCommentsActivity(story.commentMasterId, -1, requireContext());
                    }
                } else if (id == R.id.menu_adblock) {
                    adBlockDisabledForSession = true;
                    webViewController.disableAdBlockAndReload();
                } else if (id == R.id.menu_bookmark) {
                    toggleStoryBookmark();
                } else if (id == R.id.menu_archive) {
                    view.post(() -> showArchiveServiceMenu(view));
                } else if (id == R.id.menu_search_comments) {
                    resetCommentsByOpFilter();
                    CommentsSearchDialogFragment.showCommentSearchDialog(getParentFragmentManager(), comments, new CommentsSearchDialogFragment.CommentSelectedListener() {
                        @Override
                        public void onCommentSelected(Comment comment) {
                            for (Comment c : comments) {
                                if (c.id == comment.id) {
                                    int targetIndex = comments.indexOf(c);
                                    expandParentsForComment(c);
                                    if (commentNavigationController != null) {
                                        commentNavigationController.setSearchedCommentScrollTopTarget(targetIndex);
                                    }
                                    RecyclerView currentRecyclerView = recyclerView;
                                    if (currentRecyclerView != null) {
                                        currentRecyclerView.post(() -> {
                                            if (!isCommentsViewActive() || recyclerView != currentRecyclerView) {
                                                return;
                                            }
                                            if (commentNavigationController != null) {
                                                commentNavigationController.scrollToSearchedComment(targetIndex);
                                                commentNavigationController.setPendingSearchedCommentHighlight(targetIndex);
                                                commentNavigationController.updateSearchedCommentScrollTopVisibility(false);
                                            }
                                        });
                                    }
                                    break;
                                }
                            }
                        }
                    });
                } else if (id == R.id.menu_change_comment_sorting) {
                    view.post(() -> showCommentSortingMenu(view));
                } else if (id == R.id.menu_comments_by_op) {
                    showCommentsByOp();
                } else if (id == R.id.menu_comments_browser) {
                    onOpenInBrowser();
                }

                return true;
            }
        });
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.comments_more_menu, popup.getMenu());

        boolean hasAccountDetails = AccountUtils.hasAccountDetails(ctx);
        boolean bookmarksEnabled = SettingsUtils.shouldUseBookmarks(ctx);
        MenuItem bookmarkItem = popup.getMenu().findItem(R.id.menu_bookmark);
        bookmarkItem.setVisible(hasAccountDetails && bookmarksEnabled);
        if (bookmarksEnabled && story != null) {
            boolean bookmarked = Utils.isBookmarked(ctx, story.id);
            bookmarkItem.setTitle(bookmarked ? "Remove bookmark" : "Bookmark");
        }

        for (int i = 0; i < popup.getMenu().size(); i++) {
            MenuItem item = popup.getMenu().getItem(i);

            if (!story.isLink && item.getItemId() == R.id.menu_archive) {
                item.setVisible(false);
            }

            if (!webViewController.isBlockingAds() && item.getItemId() == R.id.menu_adblock) {
                item.setVisible(false);
            }

            if (item.getItemId() == R.id.menu_search_comments && getAllCommentsSource().size() < 2) {
                item.setVisible(false);
            }

            if (item.getItemId() == R.id.menu_change_comment_sorting && getAllCommentsSource().size() < 3) {
                item.setVisible(false);
            }

            if (item.getItemId() == R.id.menu_comments_by_op && !hasCommentsByOp()) {
                item.setVisible(false);
            }

            if (item.getItemId() == R.id.menu_open_parent && (!story.isComment || story.parentId <= 0)) {
                item.setVisible(false);
            }

            if (item.getItemId() == R.id.menu_open_top_level && (!story.isComment || story.commentMasterId <= 0)) {
                item.setVisible(false);
            }
        }

        popup.show();
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

        if (adapter != null) {
            adapter.notifyItemChanged(0);
        }
    }

    private void showArchiveServiceMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireActivity(), anchor);

        popup.getMenu().add(MENU_ARCHIVE_SERVICE_GROUP_ID, MENU_ARCHIVE_ORG_ITEM_ID, 0, "archive.org");
        popup.getMenu().add(MENU_ARCHIVE_SERVICE_GROUP_ID, MENU_ARCHIVE_IS_ITEM_ID, 1, "archive.is");
        popup.getMenu().add(MENU_ARCHIVE_SERVICE_GROUP_ID, MENU_ARCHIVE_TODAY_ITEM_ID, 2, "archive.today");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_ARCHIVE_ORG_ITEM_ID) {
                openArchiveOrg();
                return true;
            } else if (item.getItemId() == MENU_ARCHIVE_IS_ITEM_ID) {
                openArchiveIs();
                return true;
            } else if (item.getItemId() == MENU_ARCHIVE_TODAY_ITEM_ID) {
                openArchiveToday();
                return true;
            }

            return false;
        });

        popup.show();
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

    private void showCommentSortingMenu(View anchor) {
        if (!isCommentsViewActive()) {
            return;
        }

        PopupMenu popup = new PopupMenu(requireActivity(), anchor);
        String[] sortingOptions = getResources().getStringArray(R.array.comment_sorting);
        String currentSorting = getCurrentCommentSorting();

        for (int i = 0; i < sortingOptions.length; i++) {
            MenuItem item = popup.getMenu().add(MENU_COMMENT_SORT_GROUP_ID,
                    MENU_COMMENT_SORT_ITEM_ID_BASE + i,
                    i,
                    sortingOptions[i]);
            item.setCheckable(true);
            item.setChecked(TextUtils.equals(sortingOptions[i], currentSorting));
        }
        popup.getMenu().setGroupCheckable(MENU_COMMENT_SORT_GROUP_ID, true, true);

        popup.setOnMenuItemClickListener(item -> {
            int index = item.getItemId() - MENU_COMMENT_SORT_ITEM_ID_BASE;
            if (index < 0 || index >= sortingOptions.length) {
                return false;
            }
            item.setChecked(true);
            changeCommentSorting(sortingOptions[index]);
            return true;
        });

        popup.show();
    }

    public void clickUser() {
        UserDialogFragment.showUserDialog(requireActivity().getSupportFragmentManager(), adapter.story.by, new UserDialogFragment.UserDialogCallback() {
            @Override
            public void onResult(boolean accepted) {
                if (accepted) {
                    updateUserTags(story.by);
                }
            }
        });
    }

    public void clickComment() {
        if (!AccountUtils.hasAccountDetails(getContext())) {
            AccountUtils.showLoginPrompt(getParentFragmentManager());
            return;
        }

        Intent intent = new Intent(getContext(), ComposeActivity.class);
        intent.putExtra(ComposeActivity.EXTRA_ID, adapter.story.id);
        intent.putExtra(ComposeActivity.EXTRA_PARENT_TEXT, adapter.story.title);
        intent.putExtra(ComposeActivity.EXTRA_POST_TITLE, adapter.story.title);
        intent.putExtra(ComposeActivity.EXTRA_TYPE, ComposeActivity.TYPE_TOP_COMMENT);
        startActivity(intent);
    }

    public void clickVote() {
        clickVote(null);
    }

    public void clickVote(@Nullable View actionView) {
        Context ctx = getContext();
        if (ctx == null || adapter == null || adapter.story == null) {
            return;
        }

        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(getParentFragmentManager());
            return;
        }

        int storyId = adapter.story.id;
        boolean storyIsComment = adapter.story.isComment;
        boolean wasUpvoted = Utils.isUpvoted(ctx, storyId, storyIsComment);
        boolean newUpvoted = !wasUpvoted;
        adapter.showStoryVoteLoading(actionView, newUpvoted);

        UserActions.ActionCallback cb = new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                Utils.setUpvoted(ctx, storyId, storyIsComment, newUpvoted);
                if (adapter != null) {
                    adapter.showStoryVoteResult(actionView, newUpvoted);
                }
            }

            @Override
            public void onFailure(String summary, String response) {
                Utils.setUpvoted(ctx, storyId, storyIsComment, wasUpvoted);
                if (adapter != null) {
                    adapter.showStoryVoteResult(actionView, wasUpvoted);
                }
            }
        };

        if (newUpvoted) {
            UserActions.upvote(ctx, storyId, getParentFragmentManager(), cb);
        } else {
            UserActions.unvote(ctx, storyId, getParentFragmentManager(), cb);
        }
    }

    public void clickFavorite() {
        clickFavorite(null);
    }

    public void clickFavorite(@Nullable View actionView) {
        Context ctx = getContext();
        if (ctx == null || adapter == null) {
            return;
        }

        int storyId = adapter.story.id;
        boolean wasFavorited = Utils.isFavorited(ctx, storyId);
        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(getParentFragmentManager());
            return;
        }

        boolean newFavorited = !wasFavorited;
        adapter.showStoryFavoriteLoading(actionView, newFavorited);
        UserActions.setFavorite(ctx, storyId, newFavorited, getParentFragmentManager(), new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                Utils.setFavorite(ctx, storyId, newFavorited);
                if (adapter != null) {
                    adapter.showStoryFavoriteResult(actionView, newFavorited);
                }
            }

            @Override
            public void onFailure(String summary, String response) {
                Utils.setFavorite(ctx, storyId, wasFavorited);
                if (adapter != null) {
                    adapter.showStoryFavoriteResult(actionView, wasFavorited);
                }
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
        if (commentNavigationController != null) {
            commentNavigationController.navigateToNextComment(topLevelOnly, scaleLongScrollSpeed);
        }
    }

    public void navigateToPreviousComment() {
        navigateToPreviousComment(true);
    }

    public void navigateToPreviousComment(boolean topLevelOnly) {
        navigateToPreviousComment(topLevelOnly, false);
    }

    public void navigateToPreviousComment(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        if (commentNavigationController != null) {
            commentNavigationController.navigateToPreviousComment(topLevelOnly, scaleLongScrollSpeed);
        }
    }

    private void updateNavigationVisibility() {
        if (commentNavigationController != null) {
            commentNavigationController.updateNavigationVisibility();
        }
    }

    private void updateSearchScrollTopFabPosition() {
        if (commentNavigationController != null) {
            commentNavigationController.updateSearchScrollTopFabPosition();
        }
    }

    @Override
    public boolean isNavigationHostAdded() {
        return isAdded();
    }

    @Override
    public Context requireNavigationContext() {
        return requireContext();
    }

    @Override
    public Resources getNavigationResources() {
        return getResources();
    }

    @Override
    public @Nullable List<Comment> getNavigationComments() {
        return comments;
    }

    @Override
    public @Nullable CommentsRecyclerViewAdapter getNavigationAdapter() {
        return adapter;
    }

    @Override
    public @Nullable RecyclerView getNavigationRecyclerView() {
        return recyclerView;
    }

    @Override
    public @Nullable LinearLayoutManager getNavigationLayoutManager() {
        return layoutManager;
    }

    @Override
    public @Nullable RecyclerView.SmoothScroller getNavigationSmoothScroller() {
        return smoothScroller;
    }

    @Override
    public int getTopInset() {
        return topInset;
    }

    @Override
    public int getScreenHeightInPixels() {
        return SCREEN_HEIGHT_IN_PIXELS;
    }

    @Override
    public boolean shouldShowNavButtons() {
        return showNavButtons;
    }

    @Override
    public @Nullable View getScrollNavigationView() {
        return scrollNavigation;
    }

    @Override
    public @Nullable ExtendedFloatingActionButton getSearchScrollTopFab() {
        return searchScrollTopFab;
    }

    @Override
    public int getCommentsBottomInset() {
        return commentsBottomInset;
    }

    @Override
    public int getScrollNavigationBaseBottomMargin() {
        return scrollNavigationBaseBottomMargin;
    }

    @Override
    public int getSearchScrollTopFabBaseBottomMargin() {
        return searchScrollTopFabBaseBottomMargin;
    }

    @Override
    public void onItemClick(Comment comment, int pos, View view) {
        commentActionOverlayController.show(comment, view, true);
    }

    @Nullable
    private ViewGroup getCommentActionOverlayHost() {
        if (!isAdded()) {
            return null;
        }

        View content = requireActivity().findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            return (ViewGroup) content;
        }

        View fragmentView = getView();
        return fragmentView instanceof ViewGroup ? (ViewGroup) fragmentView : null;
    }

    @Nullable
    private View findCommentView(int commentId) {
        if (recyclerView == null || comments == null) {
            return null;
        }

        for (int i = 1; i < comments.size(); i++) {
            if (comments.get(i).id == commentId) {
                RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(i);
                if (holder == null) {
                    return null;
                }
                if (holder instanceof CommentsRecyclerViewAdapter.ItemViewHolder) {
                    return ((CommentsRecyclerViewAdapter.ItemViewHolder) holder).getCommentActionSourceView();
                }
                return holder.itemView;
            }
        }
        return null;
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
        CommentsRecyclerViewAdapter commentAdapter = CommentsFragment.this.adapter;
        if (commentAdapter == null) {
            return;
        }

        commentAdapter.loadUserTags(requireContext());

        if (story.by.equals(changedUser)) {
            commentAdapter.notifyItemChanged(0);
        }
        for (int i = 1; i < comments.size(); i++) {
            String by = comments.get(i).by;
            if (by != null) {
                if (by.equals(changedUser)) {
                    commentAdapter.notifyItemChanged(i);
                }
            }
        }
    }

    @Override
    public void onRequest(Runnable onDone) {
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
            webViewController.getLoadedPageText(text -> summarizeStory(context, mode, text, onDone));
            return;
        }

        summarizeStory(context, mode, null, onDone);
    }

    private void summarizeStory(Context context, String mode, @Nullable String articleText, Runnable onDone) {
        boolean hasArticleText = !TextUtils.isEmpty(articleText);
        if ("local".equals(mode)) {
            SummaryManager.SummaryCallback callback = new SummaryManager.SummaryCallback() {
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
