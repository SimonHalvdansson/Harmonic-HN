package com.simon.harmonichackernews;

import static androidx.webkit.WebViewFeature.isFeatureSupported;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.appcompat.widget.PopupMenu;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.transition.Transition;
import androidx.transition.TransitionListenerAdapter;
import androidx.transition.TransitionManager;
import androidx.webkit.WebViewFeature;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.transition.MaterialContainerTransform;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;
import com.simon.harmonichackernews.adapters.CommentsRecyclerViewAdapter;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.CommentsScrollProgress;
import com.simon.harmonichackernews.data.PollOption;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.linkpreview.LinkPreviewController;
import com.simon.harmonichackernews.network.AlgoliaFallbackManager;
import com.simon.harmonichackernews.network.ArchiveOrgUrlGetter;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.CommentSorter;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ShareUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.sufficientlysecure.htmltextview.HtmlTextView;
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

public class CommentsFragment extends Fragment implements CommentsRecyclerViewAdapter.CommentClickListener, CommentsRecyclerViewAdapter.RequestSummaryCallback, CommentsRecyclerViewAdapter.RetryListener {

    public final static String EXTRA_TITLE = "com.simon.harmonichackernews.EXTRA_TITLE";
    public final static String EXTRA_PDF_TITLE = "com.simon.harmonichackernews.EXTRA_PDF_TITLE";
    public final static String EXTRA_BY = "com.simon.harmonichackernews.EXTRA_BY";
    public final static String EXTRA_URL = "com.simon.harmonichackernews.EXTRA_URL";
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
    private final static Pattern POLL_TITLE_PATTERN = Pattern.compile("\\bpoll\\b", Pattern.CASE_INSENSITIVE);

    private final static int PREDICTIVE_BACK_MAX_PEEK_DP = 70;
    private final static int COMMENT_NAVIGATION_SPEED_STEP = 35;
    private final static int SEARCH_SCROLL_TOP_MIN_VISIBLE_COMMENT = 10;
    private final static int COMMENT_ACTION_VIEW_USER = 0;
    private final static int COMMENT_ACTION_SHARE = 1;
    private final static int COMMENT_ACTION_COPY = 2;
    private final static int COMMENT_ACTION_BOOKMARK = 4;
    private final static int COMMENT_ACTION_FAVORITE = 5;
    private final static int COMMENT_ACTION_UPVOTE = 6;
    private final static int COMMENT_ACTION_UNVOTE = 7;
    private final static int COMMENT_ACTION_DOWNVOTE = 8;
    private final static int COMMENT_ACTION_REPLY = 9;
    private final static int NO_COMMENT_ACTION_COMMENT_ID = -1;
    private final static int COMMENT_ACTION_TEXT_MAX_HEIGHT_DP = 300;
    private final static int COMMENT_ACTION_TRANSFORM_DURATION_MS = 280;
    private final static float COMMENT_ACTION_TRANSFORM_START_PROGRESS = 0f;
    private final static float COMMENT_ACTION_TRANSFORM_END_PROGRESS = 1f;
    private final static int COMMENT_ACTION_STANDARD_SOURCE_CORNER_RADIUS_DP = 0;
    private final static int COMMENT_ACTION_CARD_SOURCE_CORNER_RADIUS_DP = 8;
    private final static int COMMENT_ACTION_CARD_CORNER_RADIUS_DP = 28;
    private final static int COMMENT_ACTION_PREDICTIVE_BACK_TRANSLATION_X_DP = 56;
    private final static int COMMENT_ACTION_PREDICTIVE_BACK_TRANSLATION_Y_DP = 18;
    private final static float COMMENT_ACTION_PREDICTIVE_BACK_MIN_SCALE = 0.9f;
    private final static float COMMENT_ACTION_PREDICTIVE_BACK_MIN_SCRIM_ALPHA = 0.45f;

    private BottomSheetFragmentCallback callback;
    private List<Comment> comments;
    private List<Comment> allComments;
    private RequestQueue queue;
    private final Object requestTag = new Object();
    private CommentsRecyclerViewAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private RecyclerView recyclerViewSwipe;
    private RecyclerView recyclerViewRegular;
    private LinearLayoutManager layoutManager;
    private RecyclerView.OnScrollListener recyclerViewScrollListener;
    private BottomSheetBehavior.BottomSheetCallback recyclerBottomSheetCallback;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private RecyclerView.SmoothScroller smoothScroller;
    private int smoothScrollSpeedMultiplier = 1;
    private LinearLayout scrollNavigation;
    private ExtendedFloatingActionButton searchScrollTopFab;
    private LinearProgressIndicator progressIndicator;
    private LinearLayout bottomSheet;
    private Space headerSpacer;
    private LinkPreviewController linkPreviewController;
    private CommentsWebViewController webViewController;
    private boolean showNavButtons = false;
    private boolean showWebsite = false;
    private boolean integratedWebview = true;
    private boolean prefIntegratedWebview = true;
    private String preloadWebview = "never";
    private boolean matchWebviewTheme = true;
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
    private int searchedCommentScrollTopTargetId = -1;
    private boolean searchedCommentScrollTopPending = false;
    private boolean commentsByOpFilterActive = false;
    private FrameLayout commentActionOverlay;
    private MaterialCardView commentActionCard;
    private View commentActionSourceView;
    private int commentActionCommentId = NO_COMMENT_ACTION_COMMENT_ID;
    private int pendingCommentActionCommentId = NO_COMMENT_ACTION_COMMENT_ID;
    private boolean commentActionOverlayDismissing = false;
    private boolean commentActionPredictiveBackActive = false;

    // Clean fallback management
    private AlgoliaFallbackManager fallbackManager;

    public CommentsFragment() {
        super(R.layout.fragment_comments);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        filteredUsers = Utils.getFilteredUsers(getContext());

        postponeEnterTransition();

        story = new Story();

        Bundle bundle = getArguments();
        if (bundle != null && bundle.getString(EXTRA_TITLE) != null && bundle.getString(EXTRA_BY) != null) {
            story.title = bundle.getString(EXTRA_TITLE);
            story.pdfTitle = bundle.getString(EXTRA_PDF_TITLE, null);
            story.by = bundle.getString(EXTRA_BY);
            story.url = bundle.getString(EXTRA_URL);
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
            story.loaded = true;

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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            pendingCommentActionCommentId = savedInstanceState.getInt(STATE_COMMENT_ACTION_COMMENT_ID, NO_COMMENT_ACTION_COMMENT_ID);
        }

        if (getActivity() instanceof BottomSheetFragmentCallback) {
            callback = (BottomSheetFragmentCallback) getActivity();
        }

        prefIntegratedWebview = SettingsUtils.shouldUseIntegratedWebView(getContext());

        integratedWebview = prefIntegratedWebview && story.isLink;
        preloadWebview = SettingsUtils.shouldPreloadWebView(getContext());
        matchWebviewTheme = SettingsUtils.shouldMatchWebViewTheme(getContext());
        boolean blockAds = SettingsUtils.shouldBlockAds(getContext());
        closeWebViewOnBack = SettingsUtils.shouldCloseWebViewOnBack(getContext());

        swipeRefreshLayout = view.findViewById(R.id.comments_swipe_refresh);
        recyclerViewRegular = view.findViewById(R.id.comments_recyclerview);
        recyclerViewSwipe = view.findViewById(R.id.comments_recyclerview_swipe);
        bottomSheet = view.findViewById(R.id.comments_bottom_sheet);
        progressIndicator = view.findViewById(R.id.webview_progress);
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
        });
        webViewController.bindViews(view, bottomSheet, swipeRefreshLayout, progressIndicator);
        webViewController.configure(showWebsite, integratedWebview, preloadWebview, matchWebviewTheme, blockAds);

        if (story.title == null) {
            // Empty view for tablets
            view.findViewById(R.id.comments_empty).setVisibility(View.VISIBLE);
            bottomSheet.setVisibility(View.GONE);
            webViewController.setContainerVisibility(View.GONE);

            swipeRefreshLayout.setEnabled(false);
            return;
        }

        backPressedCallback = new OnBackPressedCallback(true) {

            @Override
            public void handleOnBackCancelled() {
                if (commentActionOverlay != null) {
                    cancelCommentActionPredictiveBack();
                    return;
                }

                if (willExpandBottomSheetOnBack()) {
                    bottomSheet.setTranslationY(0f);
                    try {
                        adapter.bottomSheet.findViewById(R.id.comment_sheet_buttons_container).setAlpha(1f);
                        adapter.bottomSheet.findViewById(R.id.comments_header).setAlpha(0f);
                    } catch (Exception ignored) {

                    }
                }
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                if (commentActionOverlay != null) {
                    updateCommentActionPredictiveBack(backEvent);
                    return;
                }

                if (willExpandBottomSheetOnBack()) {
                    bottomSheet.setTranslationY(backEvent.getProgress() * -Utils.pxFromDpInt(getResources(), PREDICTIVE_BACK_MAX_PEEK_DP));
                    try {
                        adapter.bottomSheet.findViewById(R.id.comment_sheet_buttons_container).setAlpha(1f-backEvent.getProgress()*0.7f);
                        adapter.bottomSheet.findViewById(R.id.comments_header).setAlpha(backEvent.getProgress()*0.7f);
                    } catch (Exception ignored) {

                    }
                }
            }

            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                if (commentActionOverlay != null) {
                    startCommentActionPredictiveBack(backEvent);
                    return;
                }

                if (willExpandBottomSheetOnBack()) {
                    bottomSheet.setTranslationY(backEvent.getProgress() * -Utils.pxFromDpInt(getResources(), PREDICTIVE_BACK_MAX_PEEK_DP));
                    try {
                        adapter.bottomSheet.findViewById(R.id.comment_sheet_buttons_container).setAlpha(1f-backEvent.getProgress()*0.7f);
                        adapter.bottomSheet.findViewById(R.id.comments_header).setAlpha(backEvent.getProgress()*0.7f);
                    } catch (Exception ignored) {

                    }
                }
            }

            @Override
            public void handleOnBackPressed() {
                if (commentActionOverlay != null) {
                    if (commentActionPredictiveBackActive) {
                        commitCommentActionPredictiveBack();
                        return;
                    }
                    dismissCommentActionOverlay(true);
                    return;
                }

                if (webViewController.isShowingCustomView()) {
                    webViewController.hideCustomView(true);
                    return;
                }

                boolean webViewVisible = BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED;
                if (willExpandBottomSheetOnBack()) {
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
        ViewUtils.setUpSwipeRefreshWithStatusBarOffset(swipeRefreshLayout);

        // This is how much the bottom sheet sticks up by default and also decides height of WebView
        // We want to watch for navigation bar height changes (tablets on Android 12L can cause
        // these)

        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets systemInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
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
                bottomSheet.setPadding(leftPadding, 0, rightPadding, 0);

                View emptyView = view.findViewById(R.id.comments_empty);
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

        progressIndicator = view.findViewById(R.id.webview_progress);

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

        scrollNavigation = view.findViewById(R.id.comments_scroll_navigation);
        ViewCompat.setOnApplyWindowInsetsListener(scrollNavigation, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());

                FrameLayout.LayoutParams scrollParams = (FrameLayout.LayoutParams) scrollNavigation.getLayoutParams();
                scrollParams.setMargins(0, 0, 0, insets.bottom + Utils.pxFromDpInt(getResources(), 16));

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(scrollNavigation);

        searchScrollTopFab = view.findViewById(R.id.comments_search_scroll_top_fab);
        FrameLayout.LayoutParams searchScrollTopFabParams = (FrameLayout.LayoutParams) searchScrollTopFab.getLayoutParams();
        int searchScrollTopFabBottomMargin = searchScrollTopFabParams.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(searchScrollTopFab, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());

                FrameLayout.LayoutParams fabParams = (FrameLayout.LayoutParams) searchScrollTopFab.getLayoutParams();
                fabParams.setMargins(fabParams.leftMargin, fabParams.topMargin, fabParams.rightMargin, insets.bottom + searchScrollTopFabBottomMargin);

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(searchScrollTopFab);
        searchScrollTopFab.setOnClickListener(v -> {
            if (SettingsUtils.shouldUseCommentsAnimationNavigation(requireContext())) {
                smoothScrollTop();
            } else {
                scrollTop();
            }
            clearSearchedCommentScrollTopTarget();
        });

        showNavButtons = SettingsUtils.shouldShowNavigationButtons(getContext());
        updateNavigationVisibility();

        ImageButton scrollPrev = view.findViewById(R.id.comments_scroll_previous);
        ImageButton scrollNext = view.findViewById(R.id.comments_scroll_next);
        ImageView scrollIcon = view.findViewById(R.id.comments_scroll_icon);

        scrollIcon.setOnClickListener(null);

        scrollNext.setOnClickListener(v -> navigateToNextComment());
        scrollNext.setOnLongClickListener(v -> {
            if (SettingsUtils.shouldUseCommentsAnimationNavigation(getContext())) {
                smoothScrollLast();
            } else {
                scrollLast();
            }
            return true;
        });

        scrollPrev.setOnClickListener(v -> navigateToPreviousComment());
        scrollPrev.setOnLongClickListener(v -> {
            if (SettingsUtils.shouldUseCommentsAnimationNavigation(getContext())) {
                smoothScrollTop();
            } else {
                scrollTop();
            }
            return true;
        });

        initializeRecyclerView();

        boolean restoreScrollFromCache = !showWebsite;

        preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                preDrawListener = null;
                startPostponedEnterTransition();
                view.post(() -> loadInitialStoryAndComments(restoreScrollFromCache));
                if (shouldInitializeWebViewAfterFirstDraw) {
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
        if (commentActionOverlay != null) {
            backPressedCallback.setEnabled(true);
            return;
        }
        if (webViewController != null && webViewController.isShowingCustomView()) {
            backPressedCallback.setEnabled(true);
            return;
        }
        if (closeWebViewOnBack) {
            backPressedCallback.setEnabled(webViewController != null && webViewController.hasWebView() &&
                    BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            backPressedCallback.setEnabled(webViewController != null && webViewController.canGoBack());
        }
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
                this);
        adapter.setCommentsByOpFilterActive(commentsByOpFilterActive);
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
                    startCommentSmoothScrollWithScaledSpeed(lastChildIndex + 1);

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
                        clickVote();
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_FAVORITE:
                        clickFavorite();
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
                if (integratedWebview) {
                    // Shouldn't be neccessary but once I was stuck in comments and couldn't swipe up.
                    // This just updates a flag so there's no performance impact
                    if (dy != 0 && callback != null) {
                        callback.onSwitchView(false);
                    }
                    BottomSheetBehavior.from(bottomSheet).setDraggable(recyclerView.computeVerticalScrollOffset() == 0);
                }

                // Note: Infinite scroll removed - all comments now load at once via AlgoliaFallbackManager
                updateSearchedCommentScrollTopVisibility(!searchedCommentScrollTopPending);
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateSearchedCommentScrollTopVisibility(true);
                }
            }
        };
        recyclerView.addOnScrollListener(recyclerViewScrollListener);
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
                return Math.max(1, super.calculateTimeForScrolling(dx) / smoothScrollSpeedMultiplier);
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

        if (!SettingsUtils.shouldUseCommentsScrollbar(getContext())) {
            // For some reason, I could only get the scrollbars to show up when they are enabled via
            // xml but disabling them in java worked so this is an okay solution...
            recyclerView.setVerticalScrollBarEnabled(false);
        }

        recyclerBottomSheetCallback = new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View view, int newState) {
                syncOnBackPressedCallbackEnabledState();
            }

            @Override
            public void onSlide(@NonNull View view, float slideOffset) {
                // Updating padding (of recyclerview) doesn't work because it causes incorrect scroll position for recycler.
                // Updating scroll together with padding causes severe lags and other problems.
                // So don't update padding at all on slide and instead just change whole view position (by translationY on recyclerView)
                // ... is something you could do but this means that the touch target of the recyclerview is not aligned with the view
                // so we go back to the padding but instead just put a view above the recyclerview (a spacer) and change its height!
                // ... is what you could do if you were stupid! This would mean that the recyclerView starts BELOW the status bar
                // breaking transparent status bar. Instead, the spacing needs to be _within_ the recyclerview header!
                // NOTE: this also needs to be set in onBindViewHolder of the adapter to stay up to date if the header item
                // should be refreshed
                loadHeaderSpacer();
                if (headerSpacer != null) {
                    headerSpacer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(topInset * slideOffset)));
                    adapter.spacerHeight = Math.round(topInset * slideOffset);
                }
            }
        };
        BottomSheetBehavior.from(bottomSheet).addBottomSheetCallback(recyclerBottomSheetCallback);

        ViewCompat.setOnApplyWindowInsetsListener(recyclerView, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                topInset = insets.top;

                float offset = BottomSheetBehavior.from(bottomSheet).calculateSlideOffset();

                loadHeaderSpacer();
                if (headerSpacer != null) {
                    headerSpacer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.round(topInset * offset)));
                    adapter.spacerHeight = Math.round(topInset * offset);
                }

                int paddingBottom = insets.bottom + getResources().getDimensionPixelSize(showNavButtons ? R.dimen.comments_bottom_navigation : R.dimen.comments_bottom_standard);
                recyclerView.setPadding(recyclerView.getPaddingLeft(), recyclerView.getPaddingTop(), recyclerView.getPaddingRight(), paddingBottom);

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(recyclerView);

        recyclerView.setAdapter(adapter);

        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_COMMENT, 300);
        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_COMMENT_CARD, 300);
        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_COLLAPSED, 600);
        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_HEADER, 1);
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

            if (adapter.preferredTextSize != SettingsUtils.getPreferredCommentTextSize(ctx)) {
                adapter.preferredTextSize = SettingsUtils.getPreferredCommentTextSize(ctx);
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

        if (lastLoaded != 0 && (System.currentTimeMillis() - lastLoaded) > 1000 * 60 * 60 && !Utils.timeInSecondsMoreThanTwoHoursAgo(story.time)) {
            if (adapter != null && !adapter.showUpdate) {
                adapter.showUpdate = true;
                clearSearchedCommentScrollTopTarget();
                adapter.notifyItemChanged(0);
            }
        }
        if (adapter != null) {
            adapter.notifyItemChanged(0);
        }
        saveScreenHeight();
    }

    private void refreshCommentActionOverlayForConfiguration() {
        if (commentActionOverlay == null || commentActionCard == null) {
            return;
        }

        commentActionOverlay.post(() -> {
            if (commentActionOverlay == null || commentActionCard == null) {
                return;
            }

            configureCommentActionCardWidth(commentActionCard);
            ScrollView textScroll = commentActionOverlay.findViewById(R.id.comment_action_text_scroll);
            HtmlTextView commentText = commentActionOverlay.findViewById(R.id.comment_action_text);
            if (textScroll != null && commentText != null) {
                ViewGroup.LayoutParams params = textScroll.getLayoutParams();
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                textScroll.setLayoutParams(params);
                resizeCommentActionTextBox(textScroll, commentText);
            }
        });
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

        int visibleCommentActionId = commentActionOverlay != null
                ? commentActionCommentId
                : pendingCommentActionCommentId;
        if (visibleCommentActionId != NO_COMMENT_ACTION_COMMENT_ID) {
            outState.putInt(STATE_COMMENT_ACTION_COMMENT_ID, visibleCommentActionId);
        }
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
                // +1 to account for header at adapter position 0
                recyclerView.post(() -> layoutManager.scrollToPositionWithOffset(targetIndex + 1, topInset));
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
        removeCommentActionOverlayNow();

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
        swipeRefreshLayout = null;
        recyclerView = null;
        recyclerViewSwipe = null;
        recyclerViewRegular = null;
        layoutManager = null;
        recyclerViewScrollListener = null;
        recyclerBottomSheetCallback = null;
        smoothScroller = null;
        scrollNavigation = null;
        searchScrollTopFab = null;
        progressIndicator = null;
        bottomSheet = null;
        headerSpacer = null;
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
        resetCommentsByOpFilter();
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
        if (context == null || recyclerView == null || story == null) {
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
        lastLoaded = System.currentTimeMillis();

        // Initialize fallback manager
        fallbackManager = new AlgoliaFallbackManager(getContext(), queue, requestTag, filteredUsers, new AlgoliaFallbackManager.FallbackListener() {
            @Override
            public void onAlgoliaSuccess(String response) {
                if (TextUtils.isEmpty(oldCachedResponse) || !oldCachedResponse.equals(response)) {
                    handleJsonResponse(id, response, true, oldCachedResponse == null, false);
                }
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onAlgoliaFailed(boolean noInternet) {
                adapter.loadingFailed = true;
                adapter.loadingFailedServerError = !noInternet;
                adapter.commentsLoaded = true;
                adapter.notifyItemChanged(0);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onUsingFallback() {
                Toast.makeText(getContext(), "Algolia API failed, using official HN API", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onHNAPIStoryLoaded(Story loadedStory) {
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
                setCommentsByOpFilterActive(false);
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
                adapter.loadingFailed = true;
                adapter.loadingFailedServerError = false;
                adapter.commentsLoaded = true;
                adapter.notifyItemChanged(0);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public void onAllCommentsLoaded(List<Comment> loadedComments) {
                // Add all comments at once in proper tree order
                allComments.addAll(loadedComments);
                if (commentsByOpFilterActive) {
                    applyDisplayedComments(getDisplayedCommentsForCurrentFilter(allComments));
                } else {
                    comments.addAll(loadedComments);
                    adapter.invalidateCommentLookup();
                    adapter.notifyItemRangeInserted(1, loadedComments.size());
                }
                adapter.commentsLoaded = true;
                updateNavigationVisibility();
                adapter.notifyItemChanged(0);
                swipeRefreshLayout.setRefreshing(false);
                recyclerView.post(() -> {
                    scrollToTargetComment();
                    restorePendingCommentActionOverlay();
                });
            }
        });

        fallbackManager.loadComments(id, oldCachedResponse);

        maybeLoadPollOptions();

        linkPreviewController.loadNetworkPreviews(getContext());
    }

    private void onLinkPreviewChanged() {
        if (adapter != null) {
            adapter.notifyItemChanged(0);
        }
    }

    private void maybeLoadPollOptions() {
        if (pollOptionsLoadStarted || story == null || story.isComment) {
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
                    Story hnStory = new Story();
                    hnStory.id = story.id;
                    if (JSONParser.updateStoryWithOfficialHNResponse(hnStory, response) && hnStory.pollOptions != null) {
                        story.pollOptions = hnStory.pollOptions;
                        maybeLoadPollOptions();
                    }
                }, error -> pollOptionsLookupStarted = false);

        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private void loadPollOptions() {
        if (story.pollOptions == null) {
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
        int oldCommentCount = getAllCommentsSource().size();

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
                adapter.notifyItemChanged(0);
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
        updateNavigationVisibility();
        recyclerView.post(() -> {
            scrollToTargetComment();
            restorePendingCommentActionOverlay();
        });
    }

    private void applyParsedComments(List<Comment> parsedComments) {
        List<Comment> oldComments = copyCommentsForDiff(comments);
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
                updateComment(existingComment, parsedComment);
                nextComments.add(existingComment);
            } else {
                nextComments.add(parsedComment);
            }
        }

        CommentSorter.sort(getContext(), nextComments);

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

    private void showCommentsByOp() {
        List<Comment> sourceComments = getAllCommentsSource();
        if (!hasCommentsByOp(sourceComments)) {
            return;
        }

        setCommentsByOpFilterActive(true);
        applyDisplayedComments(buildCommentsByOpThreadList(sourceComments));
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
            if (hasCommentsByOp(sourceComments)) {
                return buildCommentsByOpThreadList(sourceComments);
            }
            setCommentsByOpFilterActive(false);
        }
        return new ArrayList<>(sourceComments);
    }

    private boolean hasCommentsByOp() {
        return hasCommentsByOp(getAllCommentsSource());
    }

    private boolean hasCommentsByOp(List<Comment> sourceComments) {
        if (story == null || TextUtils.isEmpty(story.by)) {
            return false;
        }

        for (int i = 1; i < sourceComments.size(); i++) {
            if (TextUtils.equals(story.by, sourceComments.get(i).by)) {
                return true;
            }
        }
        return false;
    }

    private List<Comment> buildCommentsByOpThreadList(List<Comment> sourceComments) {
        List<Comment> filteredComments = new ArrayList<>();
        if (sourceComments.isEmpty()) {
            return filteredComments;
        }

        filteredComments.add(sourceComments.get(0));

        Map<Integer, Comment> commentsById = new HashMap<>();
        for (int i = 1; i < sourceComments.size(); i++) {
            Comment comment = sourceComments.get(i);
            commentsById.put(comment.id, comment);
        }

        Set<Integer> includedCommentIds = new HashSet<>();
        for (int i = 1; i < sourceComments.size(); i++) {
            Comment comment = sourceComments.get(i);
            if (!TextUtils.equals(story.by, comment.by)) {
                continue;
            }

            includeCommentAndAncestors(comment, commentsById, includedCommentIds, sourceComments.size());
            int opCommentDepth = comment.depth;
            for (int j = i + 1; j < sourceComments.size(); j++) {
                Comment candidate = sourceComments.get(j);
                if (candidate.depth <= opCommentDepth) {
                    break;
                }
                includedCommentIds.add(candidate.id);
            }
        }

        for (int i = 1; i < sourceComments.size(); i++) {
            Comment comment = sourceComments.get(i);
            if (includedCommentIds.contains(comment.id)) {
                filteredComments.add(comment);
            }
        }
        return filteredComments;
    }

    private void includeCommentAndAncestors(Comment comment, Map<Integer, Comment> commentsById, Set<Integer> includedCommentIds, int maxDepth) {
        Comment current = comment;
        int guard = 0;
        while (current != null && guard++ < maxDepth) {
            includedCommentIds.add(current.id);
            current = commentsById.get(current.parent);
        }
    }

    private void applyDisplayedComments(List<Comment> nextComments) {
        applyDisplayedComments(nextComments, copyCommentsForDiff(comments));
    }

    private void applyDisplayedComments(List<Comment> nextComments, List<Comment> oldComments) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldComments.size();
            }

            @Override
            public int getNewListSize() {
                return nextComments.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                if (oldItemPosition == 0 || newItemPosition == 0) {
                    return oldItemPosition == 0 && newItemPosition == 0;
                }
                return oldComments.get(oldItemPosition).id == nextComments.get(newItemPosition).id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                if (oldItemPosition == 0 && newItemPosition == 0) {
                    return true;
                }
                return commentsAreSame(oldComments.get(oldItemPosition), nextComments.get(newItemPosition));
            }
        }, false);

        comments.clear();
        comments.addAll(nextComments);
        if (adapter != null) {
            adapter.invalidateCommentLookup();
            diffResult.dispatchUpdatesTo(adapter);
            adapter.notifyItemChanged(0);
        }
        updateNavigationVisibility();
    }

    private List<Comment> copyCommentsForDiff(List<Comment> source) {
        List<Comment> copy = new ArrayList<>(source.size());
        for (Comment comment : source) {
            Comment commentCopy = new Comment();
            commentCopy.id = comment.id;
            commentCopy.parent = comment.parent;
            commentCopy.time = comment.time;
            commentCopy.expanded = comment.expanded;
            commentCopy.depth = comment.depth;
            commentCopy.children = comment.children;
            commentCopy.by = comment.by;
            commentCopy.text = comment.text;
            copy.add(commentCopy);
        }
        return copy;
    }

    private void updateComment(Comment existingComment, Comment parsedComment) {
        existingComment.parent = parsedComment.parent;
        existingComment.by = parsedComment.by;
        existingComment.text = parsedComment.text;
        existingComment.time = parsedComment.time;
        existingComment.depth = parsedComment.depth;
        existingComment.children = parsedComment.children;
        existingComment.childComments = parsedComment.childComments;
    }

    private boolean commentsAreSame(Comment oldComment, Comment newComment) {
        return oldComment.id == newComment.id
                && oldComment.parent == newComment.parent
                && oldComment.time == newComment.time
                && oldComment.expanded == newComment.expanded
                && oldComment.depth == newComment.depth
                && oldComment.children == newComment.children
                && TextUtils.equals(oldComment.by, newComment.by)
                && TextUtils.equals(oldComment.text, newComment.text);
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
        }

        popup.show();
    }

    public void clickMore(View view) {
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
                    webViewController.disableAdBlockAndReload();
                } else if (id == R.id.menu_archive) {
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
                } else if (id == R.id.menu_search_comments) {
                    resetCommentsByOpFilter();
                    CommentsSearchDialogFragment.showCommentSearchDialog(getParentFragmentManager(), comments, new CommentsSearchDialogFragment.CommentSelectedListener() {
                        @Override
                        public void onCommentSelected(Comment comment) {
                            for (Comment c : comments) {
                                if (c.id == comment.id) {
                                    int targetIndex = comments.indexOf(c);
                                    expandParentsForComment(c);
                                    setSearchedCommentScrollTopTarget(targetIndex);
                                    recyclerView.post(() -> {
                                        startCommentSmoothScrollWithScaledSpeed(targetIndex);
                                        updateSearchedCommentScrollTopVisibility(false);
                                    });
                                    break;
                                }
                            }
                        }
                    });
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

        for (int i = 0; i < popup.getMenu().size(); i++) {
            MenuItem item = popup.getMenu().getItem(i);

            if (!story.isLink && item.getItemId() == R.id.menu_archive) {
                item.setVisible(false);
            }

            if (!SettingsUtils.shouldBlockAds(getContext()) && item.getItemId() == R.id.menu_adblock) {
                item.setVisible(false);
            }

            if (item.getItemId() == R.id.menu_search_comments && getAllCommentsSource().size() < 2) {
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
        intent.putExtra(ComposeActivity.EXTRA_TYPE, ComposeActivity.TYPE_TOP_COMMENT);
        startActivity(intent);
    }

    public void clickVote() {
        UserActions.upvote(getContext(), adapter.story.id, getParentFragmentManager());
    }

    public void clickFavorite() {
        Context ctx = getContext();
        if (ctx == null || adapter == null) {
            return;
        }

        boolean wasFavorited = Utils.isFavorited(ctx, adapter.story.id);
        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(getParentFragmentManager());
            return;
        }

        Utils.setFavorite(ctx, adapter.story.id, !wasFavorited);
        adapter.notifyItemChanged(0);
        UserActions.setFavorite(ctx, adapter.story.id, !wasFavorited, getParentFragmentManager(), new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
            }

            @Override
            public void onFailure(String summary, String response) {
                Utils.setFavorite(ctx, adapter.story.id, wasFavorited);
                adapter.notifyItemChanged(0);
                if (!wasFavorited) {
                    Toast.makeText(ctx, "Couldn't add favorite", Toast.LENGTH_SHORT).show();
                } else {
                    UserActions.showFailureDetailDialog(ctx, summary, response);
                    Toast.makeText(ctx, "Couldn't update favorite", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void smoothScrollTop() {
        if (layoutManager != null) {
            startCommentSmoothScrollWithScaledSpeed(0);
        } else {
            recyclerView.smoothScrollToPosition(0);
        }
    }

    private void scrollTop() {
        recyclerView.scrollToPosition(0);
    }

    private void setSearchedCommentScrollTopTarget(int targetPosition) {
        if (comments == null || adapter == null || adapter.showUpdate || targetPosition <= 0 || targetPosition >= comments.size()) {
            clearSearchedCommentScrollTopTarget();
            return;
        }

        if (getVisibleCommentNumber(targetPosition) <= SEARCH_SCROLL_TOP_MIN_VISIBLE_COMMENT) {
            clearSearchedCommentScrollTopTarget();
            return;
        }

        searchedCommentScrollTopTargetId = comments.get(targetPosition).id;
        searchedCommentScrollTopPending = true;
        hideSearchScrollTopFab();
    }

    private void clearSearchedCommentScrollTopTarget() {
        searchedCommentScrollTopTargetId = -1;
        searchedCommentScrollTopPending = false;
        hideSearchScrollTopFab();
    }

    private void updateSearchedCommentScrollTopVisibility(boolean clearWhenAwayFromTarget) {
        if (searchScrollTopFab == null || layoutManager == null || searchedCommentScrollTopTargetId == -1) {
            return;
        }

        if (adapter != null && adapter.showUpdate) {
            clearSearchedCommentScrollTopTarget();
            return;
        }

        int targetPosition = findCommentPositionById(searchedCommentScrollTopTargetId);
        if (targetPosition == RecyclerView.NO_POSITION
                || getVisibleCommentNumber(targetPosition) <= SEARCH_SCROLL_TOP_MIN_VISIBLE_COMMENT) {
            clearSearchedCommentScrollTopTarget();
            return;
        }

        boolean targetVisible = targetPosition >= layoutManager.findFirstVisibleItemPosition()
                && targetPosition <= layoutManager.findLastVisibleItemPosition()
                && layoutManager.findViewByPosition(targetPosition) != null;

        if (targetVisible) {
            searchedCommentScrollTopPending = false;
            showSearchScrollTopFab();
        } else if (clearWhenAwayFromTarget) {
            clearSearchedCommentScrollTopTarget();
        }
    }

    private int findCommentPositionById(int commentId) {
        if (comments == null) {
            return RecyclerView.NO_POSITION;
        }

        for (int i = 1; i < comments.size(); i++) {
            if (comments.get(i).id == commentId) {
                return i;
            }
        }

        return RecyclerView.NO_POSITION;
    }

    private int getVisibleCommentNumber(int targetPosition) {
        if (adapter == null) {
            return targetPosition;
        }

        int visibleComments = 0;
        for (int i = 1; i <= targetPosition && i < adapter.getItemCount(); i++) {
            if (CommentsRecyclerViewAdapter.isCommentViewType(adapter.getItemViewType(i))) {
                visibleComments++;
            }
        }

        return visibleComments;
    }

    private void showSearchScrollTopFab() {
        if (searchScrollTopFab != null && searchScrollTopFab.getVisibility() != View.VISIBLE) {
            searchScrollTopFab.show();
        }
    }

    private void hideSearchScrollTopFab() {
        if (searchScrollTopFab != null && searchScrollTopFab.getVisibility() == View.VISIBLE) {
            searchScrollTopFab.hide();
        }
    }

    private int findFirstVisiblePosition() {
        int firstVisible = layoutManager.findFirstVisibleItemPosition();

        View firstVisibleView = layoutManager.findViewByPosition(firstVisible);
        if (firstVisibleView != null) {
            int top = firstVisibleView.getTop();
            int height = firstVisibleView.getHeight();
            int scrolled = height - Math.abs(top);

            // There is a topInset-sized padding at the top of the recyclerview (the
            // recyclerview extends behind the status bar) and as such
            // findFirstVisiblePosition() may return the view that is hidden behind the
            // status bar. If we have scrolled so short, then firstVisible should get a ++
            if (scrolled <= topInset) {
                firstVisible++;
            }
        }
        return firstVisible;
    }

    public void navigateToNextComment() {
        navigateToNextComment(true);
    }

    public void navigateToNextComment(boolean topLevelOnly) {
        navigateToNextComment(topLevelOnly, false);
    }

    public void navigateToNextComment(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        if (!isAdded()) {
            return;
        }

        if (SettingsUtils.shouldUseCommentsAnimationNavigation(requireContext())) {
            smoothScrollNext(topLevelOnly, scaleLongScrollSpeed);
        } else {
            scrollNext(topLevelOnly);
        }
    }

    public void navigateToPreviousComment() {
        navigateToPreviousComment(true);
    }

    public void navigateToPreviousComment(boolean topLevelOnly) {
        navigateToPreviousComment(topLevelOnly, false);
    }

    public void navigateToPreviousComment(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        if (!isAdded()) {
            return;
        }

        if (SettingsUtils.shouldUseCommentsAnimationNavigation(requireContext())) {
            smoothScrollPrevious(topLevelOnly, scaleLongScrollSpeed);
        } else {
            scrollPrevious(topLevelOnly);
        }
    }

    private void smoothScrollPrevious(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        if (layoutManager != null) {
            int firstVisible = findFirstVisiblePosition();

            int toScrollTo = findPreviousCommentPosition(firstVisible, topLevelOnly);

            startCommentSmoothScroll(toScrollTo, getCommentNavigationSpeedMultiplier(firstVisible, toScrollTo, scaleLongScrollSpeed));
        }
    }

    private void scrollPrevious(boolean topLevelOnly) {
        View toScrollToCommentView;
        if (layoutManager != null) {
            int firstVisible = findFirstVisiblePosition();

            int toScrollTo = findPreviousCommentPosition(firstVisible, topLevelOnly);

            toScrollToCommentView = layoutManager.findViewByPosition(toScrollTo);
            if (toScrollToCommentView != null) { // top comment visible, scroll to item with topInset
                recyclerView.scrollBy(0, toScrollToCommentView.getTop() - topInset);
            } else { // top comment not visible
                toScrollToCommentView = layoutManager.findViewByPosition(toScrollTo);
                while (toScrollToCommentView == null) { // scroll until find top comment
                    recyclerView.scrollBy(0, -SCREEN_HEIGHT_IN_PIXELS);
                    toScrollToCommentView = layoutManager.findViewByPosition(toScrollTo);
                }
                recyclerView.scrollBy(0, toScrollToCommentView.getTop() - topInset);
            }
        }
    }

    private void smoothScrollNext(boolean topLevelOnly, boolean scaleLongScrollSpeed) {
        if (layoutManager != null) {
            int firstVisible = findFirstVisiblePosition();

            int toScrollTo = findNextCommentPosition(firstVisible, topLevelOnly);

            startCommentSmoothScroll(toScrollTo, getCommentNavigationSpeedMultiplier(firstVisible, toScrollTo, scaleLongScrollSpeed));
        }
    }

    private void scrollNext(boolean topLevelOnly) {
        View toScrollToCommentView;
        if (layoutManager != null) {
            int firstVisible = findFirstVisiblePosition();

            int toScrollTo = findNextCommentPosition(firstVisible, topLevelOnly);

            toScrollToCommentView = layoutManager.findViewByPosition(toScrollTo);
            if (toScrollToCommentView != null) { // top comment visible, scroll to item with topInset
                recyclerView.scrollBy(0, toScrollToCommentView.getTop() - topInset);
            } else { // top comment not visible
                toScrollToCommentView = layoutManager.findViewByPosition(toScrollTo);
                while (toScrollToCommentView == null) { // scroll until find top comment
                    recyclerView.scrollBy(0, SCREEN_HEIGHT_IN_PIXELS);
                    toScrollToCommentView = layoutManager.findViewByPosition(toScrollTo);
                }
                recyclerView.scrollBy(0, toScrollToCommentView.getTop() - topInset);
            }
        }
    }

    private void startCommentSmoothScroll(int targetPosition, int speedMultiplier) {
        smoothScrollSpeedMultiplier = Math.max(1, speedMultiplier);
        smoothScroller.setTargetPosition(targetPosition);
        layoutManager.startSmoothScroll(smoothScroller);
    }

    private void startCommentSmoothScrollWithScaledSpeed(int targetPosition) {
        int firstVisible = findFirstVisiblePosition();
        startCommentSmoothScroll(targetPosition, getCommentNavigationSpeedMultiplier(firstVisible, targetPosition, true));
    }

    private int getCommentNavigationSpeedMultiplier(int fromPosition, int toPosition, boolean scaleLongScrollSpeed) {
        if (!scaleLongScrollSpeed) {
            return 1;
        }

        int commentDistance = Math.abs(toPosition - fromPosition);
        return ((commentDistance - 1) / COMMENT_NAVIGATION_SPEED_STEP) + 1;
    }

    private int findPreviousCommentPosition(int firstVisible, boolean topLevelOnly) {
        if (comments == null || comments.isEmpty()) {
            return 0;
        }

        int safeFirstVisible = Math.max(0, Math.min(firstVisible, comments.size() - 1));

        if (safeFirstVisible <= 0) {
            return 0;
        }

        if (!topLevelOnly) {
            return Math.max(safeFirstVisible - 1, 0);
        }

        for (int i = safeFirstVisible - 1; i >= 0; i--) {
            if (comments.get(i).depth == 0 || i == 0) {
                return i;
            }
        }

        return 0;
    }

    private int findNextCommentPosition(int firstVisible, boolean topLevelOnly) {
        if (comments == null || comments.isEmpty()) {
            return 0;
        }

        int safeFirstVisible = Math.max(0, Math.min(firstVisible, comments.size() - 1));

        if (!topLevelOnly) {
            return Math.min(safeFirstVisible + 1, comments.size() - 1);
        }

        for (int i = safeFirstVisible + 1; i < comments.size(); i++) {
            if (comments.get(i).depth == 0) {
                return i;
            }
        }

        return safeFirstVisible;
    }

    private void smoothScrollLast() {
        if (layoutManager != null) {
            int firstVisible = layoutManager.findFirstVisibleItemPosition();
            int toScrollTo = firstVisible;

            for (int i = firstVisible + 1; i < comments.size(); i++) {
                if (comments.get(i).depth == 0) {
                    toScrollTo = i;
                }
            }

            startCommentSmoothScroll(toScrollTo, getCommentNavigationSpeedMultiplier(firstVisible, toScrollTo, true));
        }
    }

    private void scrollLast() {
        View toScrollToCommentView;
        if (layoutManager != null) {
            int firstVisible = layoutManager.findFirstVisibleItemPosition();
            int toScrollTo = firstVisible;

            for (int i = firstVisible + 1; i < comments.size(); i++) {
                if (comments.get(i).depth == 0) {
                    toScrollTo = i;
                }
            }

            toScrollToCommentView = layoutManager.findViewByPosition(toScrollTo);
            if (toScrollToCommentView != null) { // top comment visible, scroll to item with topInset
                recyclerView.scrollBy(0, toScrollToCommentView.getTop() - topInset);
            } else { // top comment not visible
                toScrollToCommentView = layoutManager.findViewByPosition(toScrollTo);
                while (toScrollToCommentView == null) { // scroll until find top comment
                    recyclerView.scrollBy(0, SCREEN_HEIGHT_IN_PIXELS);
                    toScrollToCommentView = layoutManager.findViewByPosition(toScrollTo);
                }
                recyclerView.scrollBy(0, toScrollToCommentView.getTop() - topInset);
            }
        }
    }

    private void updateNavigationVisibility() {
        if (showNavButtons) {
            // If was gone and shouldn't be now, animate in
            if (comments != null && comments.size() > 1 && scrollNavigation.getVisibility() == View.GONE) {
                scrollNavigation.setVisibility(View.VISIBLE);

                AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
                anim.setDuration(400);
                anim.setRepeatMode(Animation.REVERSE);
                scrollNavigation.startAnimation(anim);
            }
        }

    }

    @Override
    public void onItemClick(Comment comment, int pos, View view) {
        showCommentActionOverlay(comment, view, true);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showCommentActionOverlay(Comment comment, @Nullable View sourceView, boolean animate) {
        Context ctx = getContext();
        ViewGroup overlayHost = getCommentActionOverlayHost();
        if (ctx == null || overlayHost == null || comment == null) {
            return;
        }

        removeCommentActionOverlayNow();

        commentActionCommentId = comment.id;
        commentActionSourceView = sourceView;
        commentActionOverlayDismissing = false;

        if (adapter != null) {
            adapter.disableCommentATagClick = true;
        }

        commentActionOverlay = (FrameLayout) LayoutInflater.from(ctx)
                .inflate(R.layout.comment_action_overlay, overlayHost, false);
        overlayHost.addView(commentActionOverlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        View scrim = commentActionOverlay.findViewById(R.id.comment_action_scrim);
        View content = commentActionOverlay.findViewById(R.id.comment_action_content);
        commentActionCard = commentActionOverlay.findViewById(R.id.comment_action_card);

        configureCommentActionOverlayInsets(content);
        configureCommentActionCardWidth(commentActionCard);
        bindCommentActionOverlay(comment);

        scrim.setOnClickListener(v -> dismissCommentActionOverlay(true));
        commentActionCard.setOnTouchListener((v, event) -> true);

        syncOnBackPressedCallbackEnabledState();

        commentActionOverlay.post(() -> {
            if (commentActionOverlay == null || commentActionCard == null) {
                return;
            }

            if (animate && isUsableTransitionView(sourceView)) {
                MaterialContainerTransform transform = createCommentActionTransform(
                        sourceView,
                        commentActionCard,
                        MaterialContainerTransform.TRANSITION_DIRECTION_ENTER);
                transform.addTarget(commentActionCard);
                TransitionManager.beginDelayedTransition(overlayHost, transform);
                scrim.animate().alpha(1f).setDuration(COMMENT_ACTION_TRANSFORM_DURATION_MS).start();
                setCommentActionSourceVisible(sourceView, false);
                commentActionCard.setVisibility(View.VISIBLE);
            } else {
                scrim.setAlpha(1f);
                setCommentActionSourceVisible(sourceView, false);
                commentActionCard.setVisibility(View.VISIBLE);
            }
        });
    }

    private void bindCommentActionOverlay(Comment comment) {
        Context ctx = requireContext();

        boolean bookmarksEnabled = SettingsUtils.shouldUseBookmarks(ctx);
        boolean oldBookmarked = bookmarksEnabled && Utils.isBookmarked(ctx, comment.id);
        boolean oldFavorited = Utils.isFavorited(ctx, comment.id);

        MaterialButton userButton = commentActionOverlay.findViewById(R.id.comment_action_user);
        String userLabel = TextUtils.isEmpty(comment.by) ? "Unknown user" : comment.by;
        if (story != null && TextUtils.equals(story.by, comment.by)) {
            userLabel += " (OP)";
        }
        userButton.setText(userLabel);
        userButton.setAllCaps(false);
        userButton.setContentDescription("View user " + (TextUtils.isEmpty(comment.by) ? "profile" : comment.by));
        TooltipCompat.setTooltipText(userButton, "View user");
        userButton.setOnClickListener(v ->
                performCommentAction(COMMENT_ACTION_VIEW_USER, comment, oldBookmarked, oldFavorited));

        HtmlTextView commentText = commentActionOverlay.findViewById(R.id.comment_action_text);
        String text = Utils.expandShortenedAnchorText(comment.text == null ? "" : comment.text);
        commentText.setHtml(text);
        commentText.setTextIsSelectable(true);
        commentText.setOnClickATagListener((widget, spannedText, href) -> {
            Utils.openLinkMaybeHN(widget.getContext(), href);
            return true;
        });
        FontUtils.setTypeface(commentText, false, SettingsUtils.getPreferredCommentTextSize(ctx));

        ScrollView textScroll = commentActionOverlay.findViewById(R.id.comment_action_text_scroll);
        resizeCommentActionTextBox(textScroll, commentText);

        LinearLayout actionsContainer = commentActionOverlay.findViewById(R.id.comment_action_actions);
        bindCommentActionButtons(actionsContainer, ctx, comment, bookmarksEnabled, oldBookmarked, oldFavorited);
    }

    private void bindCommentActionButtons(LinearLayout actionsContainer,
                                          Context ctx,
                                          Comment comment,
                                          boolean bookmarksEnabled,
                                          boolean oldBookmarked,
                                          boolean oldFavorited) {
        actionsContainer.removeAllViews();

        boolean hasAccount = AccountUtils.hasAccountDetails(ctx);

        ArrayList<CommentActionItem> iconActions = new ArrayList<>();
        if (hasAccount) {
            iconActions.add(new CommentActionItem(COMMENT_ACTION_UPVOTE, "Vote up", R.drawable.ic_action_thumbs_up));
            iconActions.add(new CommentActionItem(COMMENT_ACTION_DOWNVOTE, "Vote down", R.drawable.ic_action_thumb_down));
            iconActions.add(new CommentActionItem(COMMENT_ACTION_UNVOTE, "Unvote", R.drawable.ic_action_thumbs_unvote));
        }

        if (bookmarksEnabled) {
            iconActions.add(new CommentActionItem(
                    COMMENT_ACTION_BOOKMARK,
                    oldBookmarked ? "Remove bookmark" : "Bookmark",
                    oldBookmarked ? R.drawable.ic_action_bookmark_filled : R.drawable.ic_action_bookmark_border));
        }
        if (hasAccount) {
            iconActions.add(new CommentActionItem(
                    COMMENT_ACTION_FAVORITE,
                    oldFavorited ? "Remove favorite" : "Favorite",
                    oldFavorited ? R.drawable.ic_action_star_filled : R.drawable.ic_action_star));
        }

        iconActions.add(new CommentActionItem(COMMENT_ACTION_COPY, "Copy text", R.drawable.ic_action_copy));
        iconActions.add(new CommentActionItem(COMMENT_ACTION_SHARE, "Share link", R.drawable.ic_action_share));
        addCommentActionIconRow(actionsContainer, iconActions, comment, oldBookmarked, oldFavorited);

        if (hasAccount && !Utils.timeInSecondsMoreThanTwoWeeksAgo(comment.time)) {
            addCommentActionReplyButton(actionsContainer, comment, oldBookmarked, oldFavorited);
        }
    }

    private void addCommentActionIconRow(LinearLayout actionsContainer,
                                         List<CommentActionItem> actionItems,
                                         Comment comment,
                                         boolean oldBookmarked,
                                         boolean oldFavorited) {
        if (actionItems.isEmpty()) {
            return;
        }

        LinearLayout row = new LinearLayout(actionsContainer.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        row.setBaselineAligned(false);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (actionsContainer.getChildCount() > 0) {
            rowParams.topMargin = Utils.pxFromDpInt(getResources(), 4);
        }
        actionsContainer.addView(row, rowParams);

        for (CommentActionItem actionItem : actionItems) {
            ImageButton button = createCommentActionIconButton(row.getContext(), actionItem);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    Utils.pxFromDpInt(getResources(), 48),
                    1f);
            params.leftMargin = Utils.pxFromDpInt(getResources(), 1);
            params.rightMargin = Utils.pxFromDpInt(getResources(), 1);
            row.addView(button, params);
            button.setOnClickListener(v -> performCommentAction(actionItem.action, comment, oldBookmarked, oldFavorited));
        }
    }

    private ImageButton createCommentActionIconButton(Context ctx, CommentActionItem actionItem) {
        ImageButton button = new ImageButton(ctx);
        button.setImageResource(actionItem.iconRes);
        button.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(button, R.attr.storyColorNormal)));
        button.setBackgroundResource(resolveSelectableItemBackgroundBorderless(ctx));
        button.setContentDescription(actionItem.label);
        button.setPadding(
                Utils.pxFromDpInt(getResources(), 8),
                Utils.pxFromDpInt(getResources(), 10),
                Utils.pxFromDpInt(getResources(), 8),
                Utils.pxFromDpInt(getResources(), 10));
        button.setScaleType(ImageView.ScaleType.CENTER);
        TooltipCompat.setTooltipText(button, actionItem.label);
        return button;
    }

    private void addCommentActionReplyButton(LinearLayout actionsContainer,
                                             Comment comment,
                                             boolean oldBookmarked,
                                             boolean oldFavorited) {
        Context buttonContext = new ContextThemeWrapper(
                actionsContainer.getContext(),
                com.google.android.material.R.style.Widget_Material3Expressive_Button_ElevatedButton);
        MaterialButton button = new MaterialButton(buttonContext);
        button.setText("Reply");
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setIconResource(R.drawable.ic_action_reply);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(Utils.pxFromDpInt(getResources(), 8));
        int replyBackgroundColor = MaterialColors.getColor(
                button,
                R.attr.overlayButtonColor);
        button.setTextColor(Color.WHITE);
        button.setIconTint(ColorStateList.valueOf(Color.WHITE));
        button.setBackgroundTintList(ColorStateList.valueOf(replyBackgroundColor));
        button.setContentDescription("Reply");
        button.setOnClickListener(v -> performCommentAction(COMMENT_ACTION_REPLY, comment, oldBookmarked, oldFavorited));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Utils.pxFromDpInt(getResources(), 56));
        params.topMargin = Utils.pxFromDpInt(getResources(), 10);
        actionsContainer.addView(button, params);
    }

    private int resolveSelectableItemBackgroundBorderless(Context ctx) {
        TypedValue typedValue = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true);
        return typedValue.resourceId;
    }

    private void performCommentAction(int action, Comment comment, boolean oldBookmarked, boolean oldFavorited) {
        if (!isAdded()) {
            return;
        }

        Context ctx = requireContext();
        switch (action) {
            case COMMENT_ACTION_VIEW_USER:
                UserDialogFragment.showUserDialog(requireActivity().getSupportFragmentManager(), comment.by, new UserDialogFragment.UserDialogCallback() {
                    @Override
                    public void onResult(boolean accepted) {
                        if (accepted) {
                            updateUserTags(comment.by);
                        }
                    }
                });
                break;

            case COMMENT_ACTION_SHARE:
                ctx.startActivity(ShareUtils.getShareIntent(comment.id));
                break;

            case COMMENT_ACTION_COPY:
                ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Hacker News comment", Html.fromHtml(comment.text == null ? "" : comment.text));
                clipboard.setPrimaryClip(clip);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(ctx, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
                }
                break;

            case COMMENT_ACTION_BOOKMARK:
                if (oldBookmarked) {
                    Utils.removeBookmark(ctx, comment.id);
                } else {
                    Utils.addBookmark(ctx, comment.id);
                }
                if (commentActionOverlay != null) {
                    bindCommentActionOverlay(comment);
                }
                break;

            case COMMENT_ACTION_FAVORITE:
                if (!AccountUtils.hasAccountDetails(ctx)) {
                    AccountUtils.showLoginPrompt(getParentFragmentManager());
                    break;
                }

                Utils.setFavorite(ctx, comment.id, !oldFavorited);
                if (commentActionOverlay != null) {
                    bindCommentActionOverlay(comment);
                }
                UserActions.setFavorite(ctx, comment.id, !oldFavorited, getParentFragmentManager(), new UserActions.ActionCallback() {
                    @Override
                    public void onSuccess(Response response) {
                    }

                    @Override
                    public void onFailure(String summary, String response) {
                        Utils.setFavorite(ctx, comment.id, oldFavorited);
                        if (commentActionOverlay != null) {
                            bindCommentActionOverlay(comment);
                        }
                        UserActions.showFailureDetailDialog(ctx, summary, response);
                        Toast.makeText(ctx, "Couldn't update favorite", Toast.LENGTH_SHORT).show();
                    }
                });
                break;

            case COMMENT_ACTION_UPVOTE:
                UserActions.upvote(ctx, comment.id, getParentFragmentManager());
                break;

            case COMMENT_ACTION_UNVOTE:
                UserActions.unvote(ctx, comment.id, getParentFragmentManager());
                break;

            case COMMENT_ACTION_DOWNVOTE:
                UserActions.downvote(ctx, comment.id, getParentFragmentManager());
                break;

            case COMMENT_ACTION_REPLY:
                if (!AccountUtils.hasAccountDetails(ctx)) {
                    AccountUtils.showLoginPrompt(getParentFragmentManager());
                    return;
                }
                if (Utils.timeInSecondsMoreThanTwoWeeksAgo(comment.time)) {
                    Toast.makeText(ctx, "This comment is too old to reply to", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent replyIntent = new Intent(ctx, ComposeActivity.class);
                replyIntent.putExtra(ComposeActivity.EXTRA_ID, comment.id);
                replyIntent.putExtra(ComposeActivity.EXTRA_PARENT_TEXT, comment.text);
                replyIntent.putExtra(ComposeActivity.EXTRA_USER, comment.by);
                replyIntent.putExtra(ComposeActivity.EXTRA_TYPE, ComposeActivity.TYPE_COMMENT_REPLY);
                ctx.startActivity(replyIntent);
                break;
        }
    }

    private void configureCommentActionOverlayInsets(View content) {
        int baseLeft = content.getPaddingLeft();
        int baseTop = content.getPaddingTop();
        int baseRight = content.getPaddingRight();
        int baseBottom = content.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(content, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(
                        baseLeft + insets.left,
                        baseTop + insets.top,
                        baseRight + insets.right,
                        baseBottom + insets.bottom);
                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(content);
    }

    private void configureCommentActionCardWidth(MaterialCardView card) {
        int maxCardWidth = Utils.pxFromDpInt(getResources(), Utils.isTablet(getResources()) ? 640 : 520);
        int horizontalPadding = Utils.pxFromDpInt(getResources(), 40);
        int hostWidth = getResources().getDisplayMetrics().widthPixels;
        if (card.getParent() instanceof View) {
            int parentWidth = ((View) card.getParent()).getWidth();
            if (parentWidth > 0) {
                hostWidth = parentWidth;
            }
        }
        int availableWidth = Math.max(Utils.pxFromDpInt(getResources(), 280),
                hostWidth - horizontalPadding);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
        params.width = Math.min(maxCardWidth, availableWidth);
        card.setLayoutParams(params);
    }

    private void resizeCommentActionTextBox(ScrollView textScroll, HtmlTextView commentText) {
        textScroll.post(() -> {
            if (commentActionOverlay == null) {
                return;
            }

            int maxHeight = Utils.pxFromDpInt(getResources(), COMMENT_ACTION_TEXT_MAX_HEIGHT_DP);
            int contentHeight = commentText.getHeight();
            if (contentHeight <= 0) {
                return;
            }

            int paddedContentHeight = contentHeight + textScroll.getPaddingTop() + textScroll.getPaddingBottom();
            boolean needsScrolling = paddedContentHeight > maxHeight;

            ViewGroup.LayoutParams params = textScroll.getLayoutParams();
            params.height = needsScrolling ? maxHeight : paddedContentHeight;
            textScroll.setLayoutParams(params);
            textScroll.setVerticalFadingEdgeEnabled(needsScrolling);
            textScroll.setOverScrollMode(needsScrolling ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
        });
    }

    private MaterialContainerTransform createCommentActionTransform(View startView, View endView, int direction) {
        MaterialContainerTransform transform = new MaterialContainerTransform();
        transform.setStartView(startView);
        transform.setEndView(endView);
        transform.setDuration(COMMENT_ACTION_TRANSFORM_DURATION_MS);
        transform.setScrimColor(Color.TRANSPARENT);
        transform.setDrawingViewId(android.R.id.content);
        transform.setTransitionDirection(direction);
        transform.setFadeMode(MaterialContainerTransform.FADE_MODE_THROUGH);
        transform.setFitMode(MaterialContainerTransform.FIT_MODE_AUTO);
        transform.setStartShapeAppearanceModel(createCommentActionShape(startView));
        transform.setEndShapeAppearanceModel(createCommentActionShape(endView));
        transform.setScaleMaskProgressThresholds(createCommentActionProgressThresholds());
        transform.setShapeMaskProgressThresholds(createCommentActionProgressThresholds());
        transform.setElevationShadowEnabled(true);
        transform.setAllContainerColors(getCommentActionContainerColor(endView));
        transform.setStartElevation(getCommentActionContainerElevation(startView));
        transform.setEndElevation(getCommentActionContainerElevation(endView));

        return transform;
    }

    private MaterialContainerTransform.ProgressThresholds createCommentActionProgressThresholds() {
        return new MaterialContainerTransform.ProgressThresholds(
                COMMENT_ACTION_TRANSFORM_START_PROGRESS,
                COMMENT_ACTION_TRANSFORM_END_PROGRESS);
    }

    private ShapeAppearanceModel createCommentActionShape(View view) {
        int cornerRadiusDp;
        if (view == commentActionCard) {
            cornerRadiusDp = COMMENT_ACTION_CARD_CORNER_RADIUS_DP;
        } else if (view instanceof MaterialCardView) {
            cornerRadiusDp = COMMENT_ACTION_CARD_SOURCE_CORNER_RADIUS_DP;
        } else {
            cornerRadiusDp = COMMENT_ACTION_STANDARD_SOURCE_CORNER_RADIUS_DP;
        }
        return ShapeAppearanceModel.builder()
                .setAllCornerSizes(Utils.pxFromDpInt(getResources(), cornerRadiusDp))
                .build();
    }

    private int getCommentActionContainerColor(View view) {
        if (view instanceof MaterialCardView) {
            return ((MaterialCardView) view).getCardBackgroundColor().getDefaultColor();
        }
        return MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainerHigh);
    }

    private float getCommentActionContainerElevation(View view) {
        if (view instanceof MaterialCardView) {
            return ((MaterialCardView) view).getCardElevation();
        }
        return view.getElevation();
    }

    private void dismissCommentActionOverlay(boolean animate) {
        dismissCommentActionOverlay(animate, null);
    }

    private void dismissCommentActionOverlay(boolean animate, @Nullable Runnable afterDismiss) {
        if (commentActionOverlay == null) {
            if (afterDismiss != null) {
                afterDismiss.run();
            }
            return;
        }
        if (commentActionOverlayDismissing) {
            return;
        }

        commentActionOverlayDismissing = true;
        commentActionPredictiveBackActive = false;
        ViewGroup overlayHost = getCommentActionOverlayHost();
        View scrim = commentActionOverlay.findViewById(R.id.comment_action_scrim);
        View endView = resolveCommentActionSourceView(commentActionCommentId);

        pendingCommentActionCommentId = NO_COMMENT_ACTION_COMMENT_ID;
        commentActionCommentId = NO_COMMENT_ACTION_COMMENT_ID;
        if (adapter != null) {
            adapter.disableCommentATagClick = false;
        }
        syncOnBackPressedCallbackEnabledState();

        if (animate && overlayHost != null && commentActionCard != null && isUsableTransitionView(endView)) {
            MaterialContainerTransform transform = createCommentActionTransform(
                    commentActionCard,
                    endView,
                    MaterialContainerTransform.TRANSITION_DIRECTION_RETURN);
            transform.addTarget(endView);
            transform.addListener(new TransitionListenerAdapter() {
                private boolean finished;

                @Override
                public void onTransitionEnd(@NonNull Transition transition) {
                    finish();
                }

                @Override
                public void onTransitionCancel(@NonNull Transition transition) {
                    finish();
                }

                private void finish() {
                    if (finished) {
                        return;
                    }
                    finished = true;
                    finishCommentActionOverlayDismiss(afterDismiss);
                }
            });
            TransitionManager.beginDelayedTransition(overlayHost, transform);
            scrim.animate().alpha(0f).setDuration(COMMENT_ACTION_TRANSFORM_DURATION_MS).start();
            setCommentActionSourceVisible(endView, true);
            commentActionCard.setVisibility(View.INVISIBLE);
        } else {
            commentActionCard.animate()
                    .alpha(0f)
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(COMMENT_ACTION_TRANSFORM_DURATION_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            finishCommentActionOverlayDismiss(afterDismiss);
                        }
                    });
            scrim.animate().alpha(0f).setDuration(COMMENT_ACTION_TRANSFORM_DURATION_MS).start();
        }
    }

    private void finishCommentActionOverlayDismiss(@Nullable Runnable afterDismiss) {
        removeCommentActionOverlayNow();
        if (afterDismiss != null) {
            afterDismiss.run();
        }
    }

    private void removeCommentActionOverlayNow() {
        if (commentActionOverlay == null) {
            return;
        }

        View content = commentActionOverlay.findViewById(R.id.comment_action_content);
        if (content != null) {
            ViewCompat.setOnApplyWindowInsetsListener(content, null);
        }
        setCommentActionSourceVisible(commentActionSourceView, true);
        if (commentActionOverlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) commentActionOverlay.getParent()).removeView(commentActionOverlay);
        }

        commentActionOverlay = null;
        commentActionCard = null;
        commentActionSourceView = null;
        commentActionCommentId = NO_COMMENT_ACTION_COMMENT_ID;
        commentActionOverlayDismissing = false;
        if (adapter != null) {
            adapter.disableCommentATagClick = false;
        }
        syncOnBackPressedCallbackEnabledState();
    }

    private void startCommentActionPredictiveBack(@NonNull BackEventCompat backEvent) {
        if (commentActionCard == null || commentActionOverlayDismissing) {
            return;
        }

        commentActionPredictiveBackActive = true;
        commentActionCard.animate().cancel();
        View scrim = commentActionOverlay.findViewById(R.id.comment_action_scrim);
        if (scrim != null) {
            scrim.animate().cancel();
        }
        updateCommentActionPredictiveBack(backEvent);
    }

    private void updateCommentActionPredictiveBack(@NonNull BackEventCompat backEvent) {
        if (commentActionCard == null || commentActionOverlayDismissing) {
            return;
        }

        commentActionPredictiveBackActive = true;
        float progress = Math.max(0f, Math.min(1f, backEvent.getProgress()));
        float easedProgress = 1f - ((1f - progress) * (1f - progress));
        float scale = 1f - ((1f - COMMENT_ACTION_PREDICTIVE_BACK_MIN_SCALE) * easedProgress);
        float edgeDirection = backEvent.getSwipeEdge() == BackEventCompat.EDGE_RIGHT ? -1f : 1f;

        commentActionCard.setPivotX(edgeDirection > 0f ? 0f : commentActionCard.getWidth());
        commentActionCard.setPivotY(backEvent.getTouchY() > 0f
                ? Math.max(0f, Math.min(commentActionCard.getHeight(), backEvent.getTouchY() - commentActionCard.getTop()))
                : commentActionCard.getHeight() / 2f);
        commentActionCard.setScaleX(scale);
        commentActionCard.setScaleY(scale);
        commentActionCard.setTranslationX(edgeDirection
                * Utils.pxFromDpInt(getResources(), COMMENT_ACTION_PREDICTIVE_BACK_TRANSLATION_X_DP)
                * easedProgress);
        commentActionCard.setTranslationY(Utils.pxFromDpInt(getResources(), COMMENT_ACTION_PREDICTIVE_BACK_TRANSLATION_Y_DP)
                * easedProgress);

        View scrim = commentActionOverlay.findViewById(R.id.comment_action_scrim);
        if (scrim != null) {
            scrim.setAlpha(1f - ((1f - COMMENT_ACTION_PREDICTIVE_BACK_MIN_SCRIM_ALPHA) * easedProgress));
        }
    }

    private void cancelCommentActionPredictiveBack() {
        if (commentActionCard == null || !commentActionPredictiveBackActive) {
            return;
        }

        commentActionPredictiveBackActive = false;
        commentActionCard.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(COMMENT_ACTION_TRANSFORM_DURATION_MS)
                .setListener(null)
                .start();

        View scrim = commentActionOverlay.findViewById(R.id.comment_action_scrim);
        if (scrim != null) {
            scrim.animate()
                    .alpha(1f)
                    .setDuration(COMMENT_ACTION_TRANSFORM_DURATION_MS)
                    .start();
        }
    }

    private void commitCommentActionPredictiveBack() {
        if (commentActionOverlay == null || commentActionCard == null || commentActionOverlayDismissing) {
            return;
        }

        commentActionPredictiveBackActive = false;
        commentActionCard.animate().cancel();
        dismissCommentActionOverlay(true);
    }

    private void setCommentActionSourceVisible(@Nullable View sourceView, boolean visible) {
        if (isUsableTransitionView(sourceView)) {
            sourceView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void restorePendingCommentActionOverlay() {
        if (pendingCommentActionCommentId == NO_COMMENT_ACTION_COMMENT_ID || commentActionOverlay != null) {
            return;
        }

        Comment comment = findCommentById(pendingCommentActionCommentId);
        if (comment == null) {
            return;
        }

        int restoredCommentId = pendingCommentActionCommentId;
        pendingCommentActionCommentId = NO_COMMENT_ACTION_COMMENT_ID;
        showCommentActionOverlay(comment, findCommentView(restoredCommentId), false);
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
    private View resolveCommentActionSourceView(int commentId) {
        if (isUsableTransitionView(commentActionSourceView)) {
            return commentActionSourceView;
        }
        return findCommentView(commentId);
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
                View commentCard = holder.itemView.findViewById(R.id.comment_card);
                return commentCard != null ? commentCard : holder.itemView;
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

    private boolean isUsableTransitionView(@Nullable View view) {
        return view != null && ViewCompat.isAttachedToWindow(view) && view.getWidth() > 0 && view.getHeight() > 0;
    }

    private static class CommentActionItem {
        final int action;
        final String label;
        final int iconRes;

        CommentActionItem(int action, String label, int iconRes) {
            this.action = action;
            this.label = label;
            this.iconRes = iconRes;
        }
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
        webViewController.requestSummary(onDone);
    }


    public interface BottomSheetFragmentCallback {
        void onSwitchView(boolean isAtWebView);
    }

}
