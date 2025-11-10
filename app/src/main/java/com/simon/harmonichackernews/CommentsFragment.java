package com.simon.harmonichackernews;

import static android.content.Context.DOWNLOAD_SERVICE;
import static androidx.webkit.WebViewFeature.isFeatureSupported;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.util.Pair;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;
import com.simon.harmonichackernews.adapters.CommentsRecyclerViewAdapter;
import com.simon.harmonichackernews.data.ArxivInfo;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.CommentsScrollProgress;
import com.simon.harmonichackernews.data.NitterInfo;
import com.simon.harmonichackernews.data.PollOption;
import com.simon.harmonichackernews.data.RepoInfo;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.data.WikipediaInfo;
import com.simon.harmonichackernews.linkpreview.ArxivAbstractGetter;
import com.simon.harmonichackernews.linkpreview.GitHubInfoGetter;
import com.simon.harmonichackernews.linkpreview.NitterGetter;
import com.simon.harmonichackernews.linkpreview.WikipediaGetter;
import com.simon.harmonichackernews.network.AlgoliaFallbackManager;
import com.simon.harmonichackernews.network.ArchiveOrgUrlGetter;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.CommentSorter;
import com.simon.harmonichackernews.utils.DialogUtils;
import com.simon.harmonichackernews.utils.FileDownloader;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ShareUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;

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
    public final static String EXTRA_FORWARD = "com.simon.harmonichackernews.EXTRA_FORWARD";
    public final static String EXTRA_SHOW_WEBSITE = "com.simon.harmonichackernews.EXTRA_SHOW_WEBSITE";
    private final static String PDF_MIME_TYPE = "application/pdf";
    private final static String PDF_LOADER_URL = "file:///android_asset/pdf/index.html";
    private final static String OFFLINE_PAGE_URL = "file:///android_asset/webview_error.html";

    private final static int PREDICTIVE_BACK_MAX_PEEK_DP = 70;

    private BottomSheetFragmentCallback callback;
    private List<Comment> comments;
    private RequestQueue queue;
    private final Object requestTag = new Object();
    private CommentsRecyclerViewAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private RecyclerView recyclerViewSwipe;
    private RecyclerView recyclerViewRegular;
    private LinearLayoutManager layoutManager;
    private RecyclerView.SmoothScroller smoothScroller;
    private LinearLayout scrollNavigation;
    private LinearProgressIndicator progressIndicator;
    private LinearLayout bottomSheet;
    private WebView webView;
    private FrameLayout webViewContainer;
    private View webViewBackdrop;
    private Space headerSpacer;
    private MaterialButton downloadButton;
    private boolean showNavButtons = false;
    private boolean showWebsite = false;
    private boolean integratedWebview = true;
    private boolean prefIntegratedWebview = true;
    private String preloadWebview = "never";
    private boolean matchWebviewTheme = true;
    private boolean blockAds = true;
    private boolean startedLoading = false;
    private boolean initializedWebView = false;
    private boolean closeWebViewOnBack = false;
    private int topInset = 0;
    private long lastLoaded = 0;
    private OnBackPressedCallback backPressedCallback;
    private String username;
    private Story story;
    private Set<String> filteredUsers;
    private int SCREEN_HEIGHT_IN_PIXELS = 100;
    private boolean showingErrorPage = false;
    @Nullable
    private String lastFailedWebViewUrl;
    @Nullable
    private String lastRequestedWebViewUrl;
    private boolean retryingFailedWebViewUrl = false;

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
                    if (intent.getData() != null) {
                        String sId = intent.getData().getQueryParameter("id");
                        if (sId != null && !sId.equals("") && TextUtils.isDigitsOnly(sId)) {
                            try {
                                int id = Integer.parseInt(sId);
                                if (id > 0) {
                                    story.id = id;
                                    story.title = "Loading...";
                                    story.by = "";
                                    story.url = "";
                                    story.score = 0;
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(getContext(), "Unable to parse story", Toast.LENGTH_SHORT).show();
                                requireActivity().finish();
                            }
                        }
                    }
                    if (story.id == -1) {
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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getActivity() instanceof BottomSheetFragmentCallback) {
            callback = (BottomSheetFragmentCallback) getActivity();
        }

        prefIntegratedWebview = SettingsUtils.shouldUseIntegratedWebView(getContext());

        integratedWebview = prefIntegratedWebview && story.isLink;
        preloadWebview = SettingsUtils.shouldPreloadWebView(getContext());
        matchWebviewTheme = SettingsUtils.shouldMatchWebViewTheme(getContext());
        blockAds = SettingsUtils.shouldBlockAds(getContext());
        closeWebViewOnBack = SettingsUtils.shouldCloseWebViewOnBack(getContext());

        webView = view.findViewById(R.id.comments_webview);
        downloadButton = view.findViewById(R.id.webview_download);
        swipeRefreshLayout = view.findViewById(R.id.comments_swipe_refresh);
        recyclerViewRegular = view.findViewById(R.id.comments_recyclerview);
        recyclerViewSwipe = view.findViewById(R.id.comments_recyclerview_swipe);
        bottomSheet = view.findViewById(R.id.comments_bottom_sheet);
        webViewContainer = view.findViewById(R.id.webview_container);
        webViewBackdrop = view.findViewById(R.id.comments_webview_backdrop);

        if (story.title == null) {
            // Empty view for tablets
            view.findViewById(R.id.comments_empty).setVisibility(View.VISIBLE);
            bottomSheet.setVisibility(View.GONE);
            webViewContainer.setVisibility(View.GONE);

            swipeRefreshLayout.setEnabled(false);
            return;
        }

        backPressedCallback = new OnBackPressedCallback(true) {

            @Override
            public void handleOnBackCancelled() {
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
                boolean webViewVisible = BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED;
                if (willExpandBottomSheetOnBack()) {
                    // If the webView can't go back but the back handler is enabled,
                    // it means that the closeWebViewOnBack == true
                    BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
                    bottomSheet.setTranslationY(0f);
                    return;
                } else if (webViewVisible) {
                    if (webView.canGoBack()) {
                        if (downloadButton.getVisibility() == View.VISIBLE && webView.getVisibility() == View.GONE) {
                            webView.setVisibility(View.VISIBLE);
                            downloadButton.setVisibility(View.GONE);
                        } else {
                            if (showingErrorPage && webView.canGoBackOrForward(-2)) {
                                showingErrorPage = false;
                                webView.goBackOrForward(-2);
                            } else {
                                webView.goBack();
                            }
                        }
                    }

                    return;
                }

                requireActivity().finish();
                if (!SettingsUtils.shouldDisableCommentsSwipeBack(getContext()) && !Utils.isTablet(getResources())) {
                    requireActivity().overridePendingTransition(0, R.anim.activity_out_animation);
                }
            }

            private boolean willExpandBottomSheetOnBack() {
                boolean webViewVisible = BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED;
                return webViewVisible && !webView.canGoBack();
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
                int extraPadding = getResources().getDimensionPixelSize(R.dimen.extra_pane_padding);
                bottomSheet.setPadding(Math.max(cutoutInsets.left, systemInsets.left), 0, Math.max(Math.max(cutoutInsets.right, extraPadding), systemInsets.right), 0);

                webViewContainer.setPadding(0, systemInsets.top, 0, 0);

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

        if (integratedWebview) {
            initializeWebView();
        } else {
            BottomSheetBehavior.from(bottomSheet).setDraggable(false);
        }

        bottomSheet.setBackgroundColor(ContextCompat.getColor(requireContext(), ThemeUtils.getBackgroundColorResource(requireContext())));
        webViewContainer.setBackgroundColor(ContextCompat.getColor(requireContext(), ThemeUtils.getBackgroundColorResource(requireContext())));

        comments = new ArrayList<>();
        comments.add(new Comment()); // header

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

        queue = NetworkComponent.getRequestQueueInstance(requireContext());
        String cachedResponse = Utils.loadCachedStory(getContext(), story.id);

        loadStoryAndComments(story.id, cachedResponse);

        // if this isn't here, the addition of the text appears to scroll the recyclerview down a little
        recyclerView.scrollToPosition(0);

        if (cachedResponse != null) {
            handleJsonResponse(story.id, cachedResponse, false, false, !showWebsite);
        }

        view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                startPostponedEnterTransition();
                return true;
            }
        });
    }

    private void syncOnBackPressedCallbackEnabledState() {
        if (closeWebViewOnBack) {
            toggleBackPressedCallback(webView != null &&
                    BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            toggleBackPressedCallback(webView != null && webView.canGoBack());
        }
    }

    private void toggleBackPressedCallback(boolean newStatus) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            backPressedCallback.setEnabled(newStatus);
        } else {
            backPressedCallback.setEnabled(true);
        }
    }

    private void updateBottomSheetMargin(int navbarHeight) {
        int standardMargin = Utils.pxFromDpInt(getResources(), Utils.isTablet(getResources()) ? 81 : 68);

        BottomSheetBehavior.from(bottomSheet).setPeekHeight(standardMargin + navbarHeight);
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(0, 0, 0, standardMargin + navbarHeight);

        webViewContainer.setLayoutParams(params);

        if (adapter != null) {
            adapter.setNavbarHeight(navbarHeight);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // this is to make sure that action buttons in header get updated padding on rotations...
        // yes its ugly, I know
        if (getContext() != null && Utils.isTablet(getResources()) && adapter != null) {
            adapter.notifyItemChanged(0);
        }
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
                SettingsUtils.shouldUseMonochromeCommentDepthIndicators(getContext()),
                SettingsUtils.shouldShowNavigationButtons(getContext()),
                SettingsUtils.getPreferredFont(getContext()),
                isFeatureSupported(WebViewFeature.FORCE_DARK) || WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING),
                SettingsUtils.shouldShowTopLevelDepthIndicator(getContext()),
                ThemeUtils.getPreferredTheme(getContext()),
                Utils.isTablet(getResources()),
                SettingsUtils.getPreferredFaviconProvider(getContext()),
                SettingsUtils.shouldSwapCommentLongPressTap(getContext()),
                this);

        adapter.setOnHeaderClickListener(story1 -> Utils.launchCustomTab(getActivity(), story1.url));

        adapter.setOnCommentClickListener((comment, index, commentView) -> {
            comment.expanded = !comment.expanded;

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
                    smoothScroller.setTargetPosition(lastChildIndex + 1);
                    layoutManager.startSmoothScroll(smoothScroller);

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

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_SHARE:
                        clickShare(clickedView);
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_MORE:
                        clickMore(clickedView);
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_REFRESH:
                        if (showingErrorPage && !TextUtils.isEmpty(lastFailedWebViewUrl)) {
                            retryingFailedWebViewUrl = true;
                            loadUrl(lastFailedWebViewUrl);
                        } else {
                            webView.reload();
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
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.getSettings(), !WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.getSettings()));
                        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                            // I don't know why but this seems to always be true whenever we
                            // are at or above android 10
                            if (WebSettingsCompat.getForceDark(webView.getSettings()) == WebSettingsCompat.FORCE_DARK_ON) {
                                WebSettingsCompat.setForceDark(webView.getSettings(), WebSettingsCompat.FORCE_DARK_OFF);
                            } else {
                                WebSettingsCompat.setForceDark(webView.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
                            }
                        }

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

        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
            }
        });
        smoothScroller = new LinearSmoothScroller(requireContext()) {
            public PointF computeScrollVectorForPosition(int targetPosition) {
                return layoutManager.computeScrollVectorForPosition(targetPosition);
            }

            @Override
            protected int getVerticalSnapPreference() {
                return LinearSmoothScroller.SNAP_TO_START;
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

        BottomSheetBehavior.from(bottomSheet).addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
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
        });

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
        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_COLLAPSED, 600);
        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_HEADER, 1);
    }

    @SuppressLint({"RequiresFeature", "SetJavaScriptEnabled"})
    @SuppressWarnings("deprecation")
    private void initializeWebView() {
        initializedWebView = true;
        BottomSheetBehavior.from(bottomSheet).setDraggable(true);
        BottomSheetBehavior.from(bottomSheet).addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (callback != null) {
                    callback.onSwitchView(newState == BottomSheetBehavior.STATE_COLLAPSED);
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // onSlide gets called when if we're just scrolling the scrollview in the sheet,
                // we only want to start loading if we're actually sliding up the thing!
                if (!startedLoading && slideOffset < 0.9999) {
                    startedLoading = true;
                    loadUrl(story.url);
                }
            }
        });

        // This is because we are now for sure not using swipeRefresh
        try {
            ((FrameLayout) swipeRefreshLayout.getParent()).removeView(swipeRefreshLayout);
        } catch (Exception e) {
            // This will crash if we have already done this, which is fine
        }

        if (blockAds && Utils.adservers.isEmpty()) {
            Utils.loadAdservers(getResources());
        }

        webView.setWebViewClient(new MyWebViewClient());
        if (preloadWebview.equals("always") || (preloadWebview.equals("onlywifi") && Utils.isOnWiFi(requireContext())) || showWebsite || (NitterGetter.isConvertibleToNitter(story.url) && SettingsUtils.shouldUseLinkPreviewX(getContext()))) {
            loadUrl(story.url);
            startedLoading = true;
        }

        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setGeolocationEnabled(true);
        webView.getSettings().setDatabaseEnabled(true);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);

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

            private ValueAnimator progressAnimator;

            public void onProgressChanged(WebView view, int newProgress) {
                if (progressAnimator != null && progressAnimator.isRunning()) {
                    progressAnimator.cancel();
                }

                int current = progressIndicator.getProgress();
                if (newProgress > current) {
                    progressAnimator = ValueAnimator.ofInt(current, newProgress);
                    progressAnimator.setDuration(400);
                    progressAnimator.addUpdateListener(anim -> {
                        int animatedValue = (int) anim.getAnimatedValue();
                        progressIndicator.setProgress(animatedValue);
                    });
                    progressAnimator.start();
                } else {
                    progressIndicator.setProgress(newProgress);
                }

                progressIndicator.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
            }
        });

        if (matchWebviewTheme && ThemeUtils.isDarkMode(getContext())) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.getSettings(), true);
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(webView.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
            }
        }

        webView.setBackgroundColor(Color.TRANSPARENT);

        webViewBackdrop.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (webViewBackdrop != null) {
                    webViewBackdrop.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .start();
                }

            }
        }, 2000); // Start the animation after 2 seconds
    }

    private void loadUrl(String url) {
        loadUrl(url, null);
    }

    private void loadUrl(String url, @Nullable String pdfFilePath) {
        if (webView == null) {
            return;
        }
        if (!OFFLINE_PAGE_URL.equals(url)) {
            showingErrorPage = false;
            lastRequestedWebViewUrl = url;
        }
        if (url.equals(PDF_LOADER_URL)) {
            PdfAndroidJavascriptBridge pdfAndroidJavascriptBridge = new PdfAndroidJavascriptBridge(pdfFilePath, new PdfAndroidJavascriptBridge.Callbacks() {
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
        }

        if (NitterGetter.isConvertibleToNitter(url) && SettingsUtils.shouldRedirectNitter(getContext())) {
            url = NitterGetter.convertToNitterUrl(url);
        }

        webView.loadUrl(url);
        if (OFFLINE_PAGE_URL.equals(url)) {
            showingErrorPage = true;
        }
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
                    // Just download via notification as usual
                    try {
                        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                        request.allowScanningByMediaScanner();
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));
                        DownloadManager dm = (DownloadManager) view.getContext().getSystemService(DOWNLOAD_SERVICE);
                        dm.enqueue(request);
                        Toast.makeText(getContext(), "Downloading...", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Failed to download, opening in browser", Toast.LENGTH_LONG).show();
                        Utils.launchInExternalBrowser(getActivity(), url);
                    }

                }
            });
        }
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

            if (adapter.monochromeCommentDepthIndicators != SettingsUtils.shouldUseMonochromeCommentDepthIndicators(ctx)) {
                adapter.monochromeCommentDepthIndicators = SettingsUtils.shouldUseMonochromeCommentDepthIndicators(ctx);
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
                if (webViewContainer != null) {
                    webViewContainer.setBackgroundColor(ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx)));
                }
            }
            if (updateHeader) {
                adapter.notifyItemChanged(0);
            }
            if (updateComments) {
                adapter.notifyItemRangeChanged(1, comments.size());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (lastLoaded != 0 && (System.currentTimeMillis() - lastLoaded) > 1000 * 60 * 60 && !Utils.timeInSecondsMoreThanTwoHoursAgo(story.time)) {
            if (adapter != null && !adapter.showUpdate) {
                adapter.showUpdate = true;
                adapter.notifyItemChanged(0);
            }
        }
        saveScreenHeight();
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

    public void destroyWebView() {
        // Nuclear
        if (webView != null) {
            webViewContainer.removeAllViews();
            webView.clearHistory();
            webView.clearCache(true);
            webView.onPause();
            webView.removeAllViews();
            webView.destroyDrawingCache();
            webView.pauseTimers();
            webView.destroy();
            webView = null;
        }
    }

    public void restartWebView() {
        destroyWebView();

        webView = new WebView(getContext());
        webViewContainer.addView(webView);
        initializeWebView();
    }

    @Override
    public void onDestroyView() {
        if (recyclerView != null) {
            recyclerView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    // no-op
                }

                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    recyclerView.setAdapter(null);
                }
            });
        }

        super.onDestroyView();

        if (queue != null) {
            queue.cancelAll(requestTag);
        }
        destroyWebView();
    }

    @Override
    public void onRetry() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setRefreshing(true);
        }
        loadStoryAndComments(adapter.story.id, null);
    }

    @Override
    public void onOpenInBrowser() {
        Utils.launchInExternalBrowser(getActivity(), "https://news.ycombinator.com/item?id=" + story.id);
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
                story.text = loadedStory.text;
                story.kids = loadedStory.kids;
                story.descendants = loadedStory.descendants;

                // Reset comments
                int oldSize = comments.size();
                if (oldSize > 1) {
                    comments.subList(1, oldSize).clear();
                    adapter.notifyItemRangeRemoved(1, oldSize - 1);
                }

                adapter.loadingFailed = false;
                adapter.loadingFailedServerError = false;
                adapter.notifyItemChanged(0);
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
                comments.addAll(loadedComments);
                adapter.notifyItemRangeInserted(1, loadedComments.size());
                adapter.commentsLoaded = true;
                updateNavigationVisibility();
                adapter.notifyItemChanged(0);
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        fallbackManager.loadComments(id, oldCachedResponse);

        if (story.pollOptions != null) {
            loadPollOptions();
        }

        loadLinkPreviews();
    }

    private void loadLinkPreviews() {
        if (ArxivAbstractGetter.isValidArxivUrl(story.url) && SettingsUtils.shouldUseLinkPreviewArxiv(getContext())) {
            ArxivAbstractGetter.getAbstract(story.url, getContext(), new ArxivAbstractGetter.GetterCallback() {
                @Override
                public void onSuccess(ArxivInfo arxivInfo) {
                    story.arxivInfo = arxivInfo;
                    if (adapter != null) {
                        adapter.notifyItemChanged(0);
                    }
                }

                @Override
                public void onFailure(String reason) {
                    // no-op
                }
            });
        } else if (GitHubInfoGetter.isValidGitHubUrl(story.url) && SettingsUtils.shouldUseLinkPreviewGithub(getContext())) {
            GitHubInfoGetter.getInfo(story.url, getContext(), new GitHubInfoGetter.GetterCallback() {
                @Override
                public void onSuccess(RepoInfo repoInfo) {
                    story.repoInfo = repoInfo;
                    if (adapter != null) {
                        adapter.notifyItemChanged(0);
                    }
                }

                @Override
                public void onFailure(String reason) {
                    // no op
                }
            });
        } else if (WikipediaGetter.isValidWikipediaUrl(story.url) && SettingsUtils.shouldUseLinkPreviewWikipedia(getContext())) {
            WikipediaGetter.getInfo(story.url, getContext(), new WikipediaGetter.GetterCallback() {
                @Override
                public void onSuccess(WikipediaInfo wikipediaInfo) {
                    story.wikiInfo = wikipediaInfo;
                    if (adapter != null) {
                        adapter.notifyItemChanged(0);
                    }
                }

                @Override
                public void onFailure(String reason) {
                    // no op
                }
            });
        }
    }

    private void loadPollOptions() {
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
        int oldCommentCount = comments.size();

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
            JSONObject jsonObject = new JSONObject(response);

            JSONArray children = jsonObject.getJSONArray("children");

            // We run the default sorting
            boolean addedNewComment = false;
            for (int i = 0; i < children.length(); i++) {
                boolean added = JSONParser.readChildAndParseSubchilds(children.getJSONObject(i), comments, adapter, 0, story.kids, filteredUsers);
                if (added) {
                    addedNewComment = true;
                }
            }

            // If non default, do full refresh after the sorting below!
            if (addedNewComment && !SettingsUtils.getPreferredCommentSorting(getContext()).equals("Default")) {
                adapter.notifyItemRangeChanged(1, comments.size());
            }

            // And then perhaps apply an updated sorting
            CommentSorter.sort(getContext(), comments);

            boolean storyChanged = JSONParser.updateStoryInformation(story, jsonObject, forceHeaderRefresh, oldCommentCount, comments.size());
            if (storyChanged || forceHeaderRefresh) {
                adapter.notifyItemChanged(0);
            }

            integratedWebview = prefIntegratedWebview && story.isLink;

            if (integratedWebview && !initializedWebView) {
                // It's the first time, so we need to re-initialize the recyclerview too
                initializeWebView();
                initializeRecyclerView();
            }

            if (SettingsUtils.shouldCollapseTopLevel(getContext())) {
                for (Comment c : comments) {
                    if (c.depth == 0) {
                        c.expanded = false;
                    }
                }
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

        } catch (JSONException e) {
            e.printStackTrace();
            // Show some error, remove things?
            adapter.loadingFailed = true;
            adapter.loadingFailedServerError = false;
            adapter.notifyItemChanged(0);
            swipeRefreshLayout.setRefreshing(false);
        }

        adapter.commentsLoaded = true;
        updateNavigationVisibility();
    }

    public void clickBrowser() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        try {
            intent.setData(Uri.parse(webView.getUrl()));
            startActivity(intent);
        } catch (Exception e) {
            // If we're at a PDF or something like that, just do the original URL
            try {
                intent.setData(Uri.parse(story.url));
                startActivity(intent);
            } catch (Exception e2) {
                Utils.toast("Couldn't open URL", getContext());
            }
        }
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
                } else if (id == R.id.menu_adblock) {
                    blockAds = false;
                    webView.reload();

                    Snackbar snackbar = Snackbar.make(webView, "Disabled AdBlock, refreshing WebView", Snackbar.LENGTH_SHORT);
                    ViewCompat.setElevation(snackbar.getView(), Utils.pxFromDp(getResources(), 24));
                    snackbar.show();
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
                    CommentsSearchDialogFragment.showCommentSearchDialog(getParentFragmentManager(), comments, new CommentsSearchDialogFragment.CommentSelectedListener() {
                        @Override
                        public void onCommentSelected(Comment comment) {
                            for (Comment c : comments) {
                                if (c.id == comment.id) {
                                    smoothScroller.setTargetPosition(comments.indexOf(c));
                                    layoutManager.startSmoothScroll(smoothScroller);
                                    break;
                                }
                            }
                        }
                    });
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

            if (item.getItemId() == R.id.menu_search_comments && comments.size() < 2) {
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

    private void smoothScrollTop() {
        recyclerView.smoothScrollToPosition(0);
    }

    private void scrollTop() {
        recyclerView.scrollToPosition(0);
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
        if (!isAdded()) {
            return;
        }

        if (SettingsUtils.shouldUseCommentsAnimationNavigation(requireContext())) {
            smoothScrollNext(topLevelOnly);
        } else {
            scrollNext(topLevelOnly);
        }
    }

    public void navigateToPreviousComment() {
        navigateToPreviousComment(true);
    }

    public void navigateToPreviousComment(boolean topLevelOnly) {
        if (!isAdded()) {
            return;
        }

        if (SettingsUtils.shouldUseCommentsAnimationNavigation(requireContext())) {
            smoothScrollPrevious(topLevelOnly);
        } else {
            scrollPrevious(topLevelOnly);
        }
    }

    private void smoothScrollPrevious(boolean topLevelOnly) {
        if (layoutManager != null) {
            int firstVisible = findFirstVisiblePosition();

            int toScrollTo = findPreviousCommentPosition(firstVisible, topLevelOnly);

            smoothScroller.setTargetPosition(toScrollTo);
            layoutManager.startSmoothScroll(smoothScroller);
        }
    }

    private void scrollPrevious(boolean topLevelOnly) {
        View toScrollToCommentView = null;
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

    private void smoothScrollNext(boolean topLevelOnly) {
        if (layoutManager != null) {
            int firstVisible = findFirstVisiblePosition();

            int toScrollTo = findNextCommentPosition(firstVisible, topLevelOnly);

            smoothScroller.setTargetPosition(toScrollTo);
            layoutManager.startSmoothScroll(smoothScroller);
        }
    }

    private void scrollNext(boolean topLevelOnly) {
        View toScrollToCommentView = null;
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

            smoothScroller.setTargetPosition(toScrollTo);
            layoutManager.startSmoothScroll(smoothScroller);
        }
    }

    private void scrollLast() {
        View toScrollToCommentView = null;
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
        final Context ctx = getContext();

        Pair[] items;

        if (Utils.timeInSecondsMoreThanTwoWeeksAgo(comment.time)) {
            items = new Pair[]{
                    new Pair<>("View user (" + comment.by + ")", R.drawable.ic_action_user),
                    new Pair<>("Share comment link", R.drawable.ic_action_share),
                    new Pair<>("Copy text", R.drawable.ic_action_copy),
                    new Pair<>("Select text", R.drawable.ic_action_select),
                    new Pair<>("Vote up", R.drawable.ic_action_thumbs_up),
                    new Pair<>("Unvote", R.drawable.ic_action_thumbs),
                    new Pair<>("Vote down", R.drawable.ic_action_thumb_down),
            };
        } else {
            items = new Pair[]{
                    new Pair<>("View user (" + comment.by + ")", R.drawable.ic_action_user),
                    new Pair<>("Share comment link", R.drawable.ic_action_share),
                    new Pair<>("Copy text", R.drawable.ic_action_copy),
                    new Pair<>("Select text", R.drawable.ic_action_select),
                    new Pair<>("Vote up", R.drawable.ic_action_thumbs_up),
                    new Pair<>("Unvote", R.drawable.ic_action_thumbs),
                    new Pair<>("Vote down", R.drawable.ic_action_thumb_down),
                    new Pair<>("Reply", R.drawable.ic_action_reply)
            };
        }

        ListAdapter adapter = new ArrayAdapter<Pair<String, Integer>>(ctx,
                R.layout.comment_dialog_item,
                R.id.comment_dialog_text,
                items) {
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);

                view.setCompoundDrawablesWithIntrinsicBounds((Integer) items[position].second, 0, 0, 0);
                view.setText((CharSequence) items[position].first);

                return view;
            }
        };

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: //view user

                        UserDialogFragment.showUserDialog(requireActivity().getSupportFragmentManager(), comment.by, new UserDialogFragment.UserDialogCallback() {
                            @Override
                            public void onResult(boolean accepted) {
                                if (accepted) {
                                    updateUserTags(comment.by);
                                }
                            }
                        });

                        break;
                    case 1: //share comment
                        ctx.startActivity(ShareUtils.getShareIntent(comment.id));

                        break;
                    case 2: // copy text
                        ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Hacker News comment", Html.fromHtml(comment.text));
                        clipboard.setPrimaryClip(clip);

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(ctx, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
                        }

                        break;
                    case 3: //select text
                        DialogUtils.showTextSelectionDialog(ctx, comment.text);

                        break;
                    case 4: //upvote
                        UserActions.upvote(ctx, comment.id, getParentFragmentManager());
                        break;

                    case 5: //unvote
                        UserActions.unvote(ctx, comment.id, getParentFragmentManager());
                        break;

                    case 6: //downvote
                        UserActions.downvote(ctx, comment.id, getParentFragmentManager());
                        break;

                    case 7: //reply
                        if (!AccountUtils.hasAccountDetails(ctx)) {
                            AccountUtils.showLoginPrompt(getParentFragmentManager());
                            return;
                        }

                        Intent replyIntent = new Intent(ctx, ComposeActivity.class);
                        replyIntent.putExtra(ComposeActivity.EXTRA_ID, comment.id);
                        replyIntent.putExtra(ComposeActivity.EXTRA_PARENT_TEXT, comment.text);
                        replyIntent.putExtra(ComposeActivity.EXTRA_USER, comment.by);
                        replyIntent.putExtra(ComposeActivity.EXTRA_TYPE, ComposeActivity.TYPE_COMMENT_REPLY);
                        ctx.startActivity(replyIntent);

                }
            }
        });

        CommentsRecyclerViewAdapter commentAdapter = this.adapter;
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                commentAdapter.disableCommentATagClick = false;
            }
        });

        AlertDialog dialog = builder.create();
        commentAdapter.disableCommentATagClick = true;
        dialog.show();
    }

    private void updateUserTags(String changedUser) {
        if (story.by.equals(changedUser)) {
            CommentsFragment.this.adapter.notifyItemChanged(0);
        }
        for (int i = 1; i < comments.size(); i++) {
            String by = comments.get(i).by;
            if (by != null) {
                if (by.equals(changedUser)) {
                    if (CommentsFragment.this.adapter != null) {
                        CommentsFragment.this.adapter.notifyItemChanged(i);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onRequest(Runnable onDone) {
        // Ensure we're on the main thread
        Handler handler = new Handler(Looper.getMainLooper());

        // If the WebView hasn't been initialized or hasn't started loading yet, start it now
        if (webView == null || !startedLoading) {
            startedLoading = true;
            loadUrl(story.url);
        }

        // Inject a one-time listener to wait for the page to finish loading
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Run a simple JS snippet to grab the page's body text
                view.evaluateJavascript(
                        "(function() { return document.body.innerText || ''; })();",
                        result -> {
                            // result is a JSON-encoded string
                            if (result != null) {
                                // Strip the surrounding quotes
                                story.summary = result.replaceAll("^\"|\"$", "");
                            } else {
                                story.summary = "";
                            }
                            // Notify caller that we're done
                            handler.post(onDone);
                        }
                );
            }
        });
    }

    private boolean shouldShowOfflineFallback(int errorCode) {
        switch (errorCode) {
            case WebViewClient.ERROR_HOST_LOOKUP:
            case WebViewClient.ERROR_CONNECT:
            case WebViewClient.ERROR_TIMEOUT:
            case WebViewClient.ERROR_UNKNOWN:
                return true;
            default:
                return false;
        }
    }

    public class MyWebViewClient extends WebViewClient {

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (!OFFLINE_PAGE_URL.equals(url)) {
                lastRequestedWebViewUrl = url;
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            webView.setBackgroundColor(Color.WHITE);
            webViewBackdrop.setVisibility(View.GONE);

            if (retryingFailedWebViewUrl) {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                retryingFailedWebViewUrl = false;
            }

            if (BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_COLLAPSED) {
                // If we are at the webview and we just loaded, recheck the canGoBack status
                syncOnBackPressedCallbackEnabledState();
            }

            if (NitterGetter.isValidNitterUrl(url) && SettingsUtils.shouldUseLinkPreviewX(getContext())) {
                NitterGetter.getInfo(view, getContext(), new NitterGetter.GetterCallback() {
                    @Override
                    public void onSuccess(NitterInfo nitterInfo) {
                        story.nitterInfo = nitterInfo;
                        if (adapter != null) {
                            adapter.notifyItemChanged(0);
                        }
                    }

                    @Override
                    public void onFailure(String reason) {

                    }
                });
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (url.startsWith("intent://")) {
                try {
                    Context context = view.getContext();
                    Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);

                    // First, try to use the fallback URL (browser version of Play Store)
                    String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                    if (fallbackUrl != null) {
                        webView.loadUrl(fallbackUrl);
                        return true; // Indicate that we're handling this URL
                    } else {
                        // If no valid fallback URL, then check if the intent can be resolved (Play Store app is installed)
                        if (intent.resolveActivity(context.getPackageManager()) != null) {
                            context.startActivity(intent);
                            return true; // Indicate that we're handling this URL
                        }
                    }
                } catch (Exception e) {
                    // Handle the error
                    return false; // Indicate that we're not handling this URL
                }
            }

            return false;
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!detail.didCrash()) {
                    // Renderer is killed because the system ran out of memory. The app
                    // can recover gracefully by creating a new WebView instance in the
                    // foreground.
                    Log.e("MY_APP_TAG", "System killed the WebView rendering process " +
                            "to reclaim memory. Recreating...");

                    Utils.toast("System ran out of memory and killed WebView, reinitializing", getContext());
                    restartWebView();

                    // By this point, the instance variable "mWebView" is guaranteed to
                    // be null, so it's safe to reinitialize it.

                    return true; // The app continues executing.
                }
            }
            Utils.toast("WebView crashed, reinitializing", getContext());
            restartWebView();

            // Renderer crashes because of an internal error, such as a memory
            // access violation.
            Log.e("MY_APP_TAG", "The WebView rendering process crashed!");

            // In this example, the app itself crashes after detecting that the
            // renderer crashed. If you handle the crash more gracefully and let
            // your app continue executing, you must destroy the current WebView
            // instance, specify logic for how the app continues executing, and
            // return "true" instead.
            return true;
        }

        private void showOfflineFallback(WebView view, @Nullable String failingUrl) {
            if (view == null || showingErrorPage) {
                return;
            }
            if (!TextUtils.isEmpty(failingUrl)) {
                lastFailedWebViewUrl = failingUrl;
            } else if (lastRequestedWebViewUrl != null) {
                lastFailedWebViewUrl = lastRequestedWebViewUrl;
            } else if (view.getUrl() != null && !TextUtils.isEmpty(view.getUrl()) && !OFFLINE_PAGE_URL.equals(view.getUrl())) {
                lastFailedWebViewUrl = view.getUrl();
            }
            retryingFailedWebViewUrl = false;
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            view.stopLoading();
            CommentsFragment.this.loadUrl(OFFLINE_PAGE_URL);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            if (shouldShowOfflineFallback(errorCode)) {
                showOfflineFallback(view, failingUrl);
            } else {
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && request.isForMainFrame() && shouldShowOfflineFallback(error.getErrorCode())) {
                showOfflineFallback(view, request.getUrl() != null ? request.getUrl().toString() : null);
            } else {
                super.onReceivedError(view, request, error);
            }
        }
    }

    public interface BottomSheetFragmentCallback {
        void onSwitchView(boolean isAtWebView);
    }

    public static class PdfAndroidJavascriptBridge {
        private final File mFile;
        private @Nullable
        RandomAccessFile mRandomAccessFile;
        private final @Nullable Callbacks mCallback;
        private final Handler mHandler;

        PdfAndroidJavascriptBridge(String filePath, @Nullable Callbacks callback) {
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
