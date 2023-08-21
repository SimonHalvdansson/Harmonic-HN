package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
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
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
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

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.PollOption;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.network.VolleyOkHttp3StackInterceptors;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.ArchiveOrgUrlGetter;
import com.simon.harmonichackernews.utils.FileDownloader;
import com.simon.harmonichackernews.utils.ShareUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;

import static android.content.Context.DOWNLOAD_SERVICE;
import static androidx.webkit.WebViewFeature.FORCE_DARK_STRATEGY;
import static androidx.webkit.WebViewFeature.isFeatureSupported;

public class CommentsFragment extends Fragment implements CommentsRecyclerViewAdapter.CommentClickListener {

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
    public final static String EXTRA_FORWARD = "com.simon.harmonichackernews.EXTRA_FORWARD";
    public final static String EXTRA_SHOW_WEBSITE = "com.simon.harmonichackernews.EXTRA_SHOW_WEBSITE";

    private final static String PDF_MIME_TYPE = "application/pdf";
    private final static String PDF_LOADER_URL = "file:///android_asset/pdf/index.html";

    private BottomSheetFragmentCallback callback;
    private List<Comment> comments;
    private RequestQueue queue;
    private CommentsRecyclerViewAdapter adapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private RecyclerView recyclerViewSwipe;
    private RecyclerView recyclerViewRegular;
    private LinearLayout scrollNavigation;
    private LinearProgressIndicator progressIndicator;
    private LinearLayout bottomSheet;
    private WebView webView;
    private FrameLayout webViewContainer;
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
    private String username;
    private Story story;

    private int bottomSheetMargin;

    public CommentsFragment() {
        super(R.layout.fragment_comments);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, true));
        setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.Y, false));

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
            story.loaded = true;

            if (Utils.isTablet(requireContext())) {
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
            //check if url intercept
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
                            } catch(Exception e) {
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        inflater.inflate(R.layout.fragment_comments, container, false);
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getActivity() instanceof BottomSheetFragmentCallback) {
            callback = (BottomSheetFragmentCallback) getActivity();
        }

        prefIntegratedWebview = Utils.shouldUseIntegratedWebView(getContext());

        integratedWebview = prefIntegratedWebview && story.isLink;
        preloadWebview = Utils.shouldPreloadWebView(getContext());
        matchWebviewTheme = Utils.shouldMatchWebViewTheme(getContext());
        blockAds = Utils.shouldBlockAds(getContext());

        webView = view.findViewById(R.id.comments_webview);
        downloadButton = view.findViewById(R.id.webview_download);
        swipeRefreshLayout = view.findViewById(R.id.comments_swipe_refresh);
        recyclerViewRegular = view.findViewById(R.id.comments_recyclerview);
        recyclerViewSwipe = view.findViewById(R.id.comments_recyclerview_swipe);
        bottomSheet = view.findViewById(R.id.comments_bottom_sheet);
        webViewContainer = view.findViewById(R.id.webview_container);

        if (story.title == null) {
            //Empty view for tablets
            view.findViewById(R.id.comments_empty).setVisibility(View.VISIBLE);
            bottomSheet.setVisibility(View.GONE);
            webViewContainer.setVisibility(View.GONE);

            swipeRefreshLayout.setEnabled(false);
            return;
        }

        swipeRefreshLayout.setOnRefreshListener(this::refreshComments);
        Utils.setUpSwipeRefreshWithStatusBarOffset(swipeRefreshLayout);

        // this is how much the bottom sheet sticks up by default and also decides height of webview
        //We want to watch for navigation bar height changes (tablets on Android 12L can cause
        // these)

        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                updateBottomSheetMargin(insets.bottom);

                return windowInsets;
            }
        });

        updateBottomSheetMargin(Utils.getNavigationBarHeight(getResources()));

        webViewContainer.setPadding(0, Utils.getStatusBarHeight(getResources()), 0, 0);

        if (!showWebsite) {
            BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        }

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
        comments.add(new Comment()); //header

        username = AccountUtils.getAccountUsername(getContext());

        scrollNavigation = view.findViewById(R.id.comments_scroll_navigation);
        FrameLayout.LayoutParams scrollParams = (FrameLayout.LayoutParams) scrollNavigation.getLayoutParams();
        scrollParams.setMargins(0,0,0, Utils.getNavigationBarHeight(getResources()) + Utils.pxFromDpInt(getResources(), 16));

        showNavButtons = Utils.shouldShowNavigationButtons(getContext());
        updateNavigationVisibility();

        ImageButton scrollPrev = view.findViewById(R.id.comments_scroll_previous);
        ImageButton scrollNext = view.findViewById(R.id.comments_scroll_next);
        ImageView scrollIcon = view.findViewById(R.id.comments_scroll_icon);

        scrollIcon.setOnClickListener(null);

        scrollPrev.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                scrollTop();
                return true;
            }
        });

        scrollPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scrollPrevious();
            }
        });

        scrollNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                scrollNext();
            }
        });

        scrollNext.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                scrollLast();
                return true;
            }
        });

        initializeRecyclerView();

        queue = Volley.newRequestQueue(requireContext(), new VolleyOkHttp3StackInterceptors());
        String cachedResponse = Utils.loadCachedStory(getContext(), story.id);

        loadStoryAndComments(story.id, cachedResponse == null, cachedResponse, true);

        if (cachedResponse != null) {
            handleJsonResponse(story.id, cachedResponse,false, false);
        }
    }

    private void updateBottomSheetMargin(int navbarHeight) {
        int standardMargin = Utils.pxFromDpInt(getResources(), Utils.isTablet(requireContext()) ? 81 : 68);

        BottomSheetBehavior.from(bottomSheet).setPeekHeight(standardMargin + navbarHeight);
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.setMargins(0,0, 0, standardMargin + navbarHeight);

        webViewContainer.setLayoutParams(params);

        if (adapter != null) {
            adapter.navbarHeight = navbarHeight;
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        //this is to make sure that action buttons in header get updated padding on rotations...
        //yes its ugly, I know
        if (getContext() != null && Utils.isTablet(getContext()) && adapter != null) {
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
                Utils.shouldCollapseParent(getContext()),
                Utils.shouldShowThumbnails(getContext()),
                username,
                Utils.getPreferredCommentTextSize(getContext()),
                Utils.shouldUseMonochromeCommentDepthIndicators(getContext()),
                Utils.shouldShowNavigationButtons(getContext()),
                Utils.getPreferredFont(getContext()),
                isFeatureSupported(WebViewFeature.FORCE_DARK) || WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING),
                Utils.shouldShowTopLevelDepthIndicator(getContext()),
                Utils.shouldShowWebviewExpandButton(getContext()),
                ThemeUtils.isDarkMode(getContext()));

        adapter.setOnHeaderClickListener(story1 -> {
            Utils.launchCustomTab(getActivity(), story1.url);
        });

        adapter.setOnCommentClickListener((comment, index, commentView) -> {
            comment.expanded = !comment.expanded;

            int lastChildIndex = adapter.getIndexOfLastChild(comment.depth, index);

            if (lastChildIndex != index || adapter.collapseParent) {
                // + 1 since if we have 1 subcomment we have changed the parent and the child
                adapter.notifyItemRangeChanged(index, lastChildIndex - index + 1);
            }
        });

        adapter.setOnCommentLongClickListener(this);
        adapter.setRetryListener(this::refreshComments);

        adapter.setOnHeaderActionClickListener(new CommentsRecyclerViewAdapter.HeaderActionClickListener() {
            @Override
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

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_BACK:
                        if (webView.canGoBack()) {
                            if (downloadButton.getVisibility() == View.VISIBLE && webView.getVisibility() == View.GONE) {
                                webView.setVisibility(View.VISIBLE);
                                downloadButton.setVisibility(View.GONE);
                            } else {
                                webView.goBack();
                            }
                        } else {
                            if (getActivity() instanceof CommentsActivity) {
                                requireActivity().finish();
                                requireActivity().overridePendingTransition(0, R.anim.activity_out_animation);
                            }
                        }

                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_REFRESH:
                        webView.reload();
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_EXPAND:
                        BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
                        break;

                    case CommentsRecyclerViewAdapter.FLAG_ACTION_CLICK_INVERT:
                        //this whole thing should only be visible for SDK_INT larger than Q (29)
                        //We first check the "new" version of dark mode, algorithmic darkening
                        // this requires the isDarkMode thing to be true for the theme which we
                        // have set
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.getSettings(), !WebSettingsCompat.isAlgorithmicDarkeningAllowed(webView.getSettings()));
                        } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                            //I don't know why but this seems to always be true whenever we
                            //are at or above android 10
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

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (integratedWebview) {
                    //Shouldn't be neccessary but once I was stuck in comments and couldn't swipe up.
                    //this just updates a flag so there's no performance impact
                    if (dy != 0 && callback != null) {
                        callback.onSwitchView(false);
                    }
                    BottomSheetBehavior.from(bottomSheet).setDraggable(recyclerView.computeVerticalScrollOffset() == 0);
                }
            }
        });

        if (!Utils.shouldUseCommentsAnimation(getContext())) {
            recyclerView.setItemAnimator(null);
        }

        recyclerView.setPadding(0,0,0, Utils.getNavigationBarHeight(getResources()) + getResources().getDimensionPixelSize(showNavButtons ? R.dimen.comments_bottom_navigation : R.dimen.comments_bottom_standard));
        recyclerView.setAdapter(adapter);

        recyclerView.getRecycledViewPool().setMaxRecycledViews(CommentsRecyclerViewAdapter.TYPE_ITEM, 100);
    }


    @SuppressLint({"RequiresFeature", "SetJavaScriptEnabled"})
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
                //onSlide gets called when if we're just scrolling the scrollview in the sheet,
                //we only want to start loading if we're actually sliding up the thing!
                if (!startedLoading && slideOffset < 0.9999) {
                    startedLoading = true;
                    loadUrl(story.url);
                }
            }
        });

        ((FrameLayout) swipeRefreshLayout.getParent()).removeView(swipeRefreshLayout);
        if (blockAds && TextUtils.isEmpty(Utils.adservers)) {
            Utils.loadAdservers(getResources());
        }
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setWebViewClient(new MyWebViewClient());
        if (preloadWebview.equals("always") || (preloadWebview.equals("onlywifi") && Utils.isOnWiFi(requireContext())) || showWebsite) {
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
            public void onProgressChanged(WebView view, int progress) {
                if (progress < 100 && progressIndicator.getVisibility() == View.GONE) {
                    progressIndicator.setVisibility(View.VISIBLE);
                }
                progressIndicator.setProgress(progress);
                if (progress == 100) {
                    progressIndicator.setVisibility(View.GONE);
                }
            }
        });

        if (matchWebviewTheme && ThemeUtils.isDarkMode(getContext())) {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.getSettings(), true);
            } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(webView.getSettings(), WebSettingsCompat.FORCE_DARK_ON);
            }
        }

    }

    private void loadUrl(String url) {
        loadUrl(url, null);
    }

    private void loadUrl(String url, @Nullable String pdfFilePath) {
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
        webView.loadUrl(url);
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
        webView.setVisibility(View.GONE);
        downloadButton.setVisibility(View.VISIBLE);
        downloadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //just download via notification as usual
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));
                    DownloadManager dm = (DownloadManager) getContext().getSystemService(DOWNLOAD_SERVICE);
                    dm.enqueue(request);
                    Toast.makeText(getContext(), "Downloading...", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Failed to download, opening in browser", Toast.LENGTH_LONG).show();
                    Utils.launchInExternalBrowser(getActivity(), url);
                }

            }
        });
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
            Context ctx = getContext();
            boolean updateHeader = false;
            boolean updateComments = false;

            if (adapter.collapseParent != Utils.shouldCollapseParent(ctx)) {
                adapter.collapseParent = !adapter.collapseParent;
                updateComments = true;
            }

            if (adapter.showThumbnail != Utils.shouldShowThumbnails(ctx)) {
                adapter.showThumbnail = !adapter.showThumbnail;
                updateHeader = true;
            }

            if (adapter.preferredTextSize != Utils.getPreferredCommentTextSize(ctx)) {
                adapter.preferredTextSize = Utils.getPreferredCommentTextSize(ctx);
                updateHeader = true;
                updateComments = true;
            }

            if (adapter.monochromeCommentDepthIndicators != Utils.shouldUseMonochromeCommentDepthIndicators(ctx)) {
                adapter.monochromeCommentDepthIndicators = Utils.shouldUseMonochromeCommentDepthIndicators(ctx);
                updateComments = true;
            }

            if (!adapter.font.equals(Utils.getPreferredFont(ctx))) {
                adapter.font = Utils.getPreferredFont(ctx);
                updateHeader = true;
                updateComments = true;
            }

            if (adapter.showTopLevelDepthIndicator != Utils.shouldShowTopLevelDepthIndicator(ctx)) {
                adapter.showTopLevelDepthIndicator = Utils.shouldShowTopLevelDepthIndicator(ctx);
                updateComments = true;
            }

            if (adapter.showExpand != Utils.shouldShowWebviewExpandButton(ctx)) {
                adapter.showExpand = Utils.shouldShowWebviewExpandButton(ctx);
                updateHeader = true;
            }

            if (adapter.darkThemeActive != ThemeUtils.isDarkMode(ctx)) {
                adapter.darkThemeActive = ThemeUtils.isDarkMode(ctx);
                updateHeader = true;
                updateComments = true;

                // darkThemeActive might change because the system changed from day to night mode.
                // In that case, we'll need to update the sheet and webview background color since
                // that will have changed too.
                if (bottomSheet != null) {
                    bottomSheet.setBackgroundColor(ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx)));
                }
                if (webViewContainer != null){
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
    public void onDestroy() {
        super.onDestroy();
        if (queue != null) {
            queue.cancelAll(request -> true);
            queue.stop();
        }
        destroyWebView();
    }

    public void destroyWebView() {
        //nuclear
        webViewContainer.removeAllViews();
        webView.clearHistory();
        webView.clearCache(true);
        webView.loadUrl("about:blank");
        webView.onPause();
        webView.removeAllViews();
        webView.destroyDrawingCache();
        webView.pauseTimers();
        webView.destroy();
        webView = null;
    }

    @Override
    public void onDestroyView() {
        if (recyclerView != null) {
            recyclerView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    // no-op
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    recyclerView.setAdapter(null);
                }
            });
        }

        super.onDestroyView();
    }

    public void refreshComments() {
        swipeRefreshLayout.setRefreshing(true);
        loadStoryAndComments(adapter.story.id, true, null, true);
    }

    private void loadStoryAndComments(final int id, final boolean forceHeaderRefresh, final String oldCachedResponse, final boolean useAlgolia) {
        String url = "https://hn.algolia.com/api/v1/items/" + id;

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (TextUtils.isEmpty(oldCachedResponse) || !oldCachedResponse.equals(response)) {
                        handleJsonResponse(id, response,true, forceHeaderRefresh);
                    }
                    swipeRefreshLayout.setRefreshing(false);
                }, error -> {
            error.printStackTrace();

            if (error instanceof com.android.volley.TimeoutError) {
                adapter.loadingFailedServerError = true;
            }

            adapter.loadingFailed = true;
            adapter.notifyItemChanged(0);
            swipeRefreshLayout.setRefreshing(false);
        });

        if (story.pollOptions != null) {
            loadPollOptions();
        }

        stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                15000,
                DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        queue.add(stringRequest);
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
                            for (int i = 0; i < story.pollOptionArrayList.size(); i++) {
                                PollOption pollOption = story.pollOptionArrayList.get(i);

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

            queue.add(stringRequest);
        }
    }

    private void handleJsonResponse(final int id, final String response, final boolean cache, final boolean forceHeaderRefresh) {
        int oldCommentCount = comments.size();

        // This is what we get if the Algolia API has not indexed the post,
        // we should attempt to show the user an option to switch API:s in this
        // server error case
        if (response.equals(JSONParser.ALGOLIA_ERROR_STRING)) {
            adapter.loadingFailed = true;
            adapter.loadingFailedServerError = true;
            adapter.notifyItemChanged(0);
            swipeRefreshLayout.setRefreshing(false);
        }

        try {
            JSONObject jsonObject = new JSONObject(response);

            JSONArray children = jsonObject.getJSONArray("children");

            for (int i = 0; i < children.length(); i++) {
                JSONParser.readChildAndParseSubchilds(children.getJSONObject(i), comments, adapter, 0, story.kids);
            }

            boolean changed = JSONParser.updateStoryInformation(story, jsonObject, forceHeaderRefresh, oldCommentCount, comments.size());
            if (changed || forceHeaderRefresh) {
                adapter.notifyItemChanged(0);
            }

            integratedWebview = prefIntegratedWebview && story.isLink;

            if (integratedWebview && !initializedWebView) {
                //it's the first time, so we need to re-initialize the recyclerview too
                initializeWebView();
                initializeRecyclerView();
            }

            adapter.loadingFailed = false;

            //Seems like loading went well, lets cache the result
            if (cache) {
                Utils.cacheStory(getContext(), id, response);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            //Show some error, remove things?
            adapter.loadingFailed = true;
            adapter.loadingFailedServerError = false;
            adapter.notifyItemChanged(0);
            swipeRefreshLayout.setRefreshing(false);
        }

        adapter.commentsLoaded = true;
        updateNavigationVisibility();
    }

    public void clickShare(View view) {
        if (adapter.story.isLink) {
            PopupMenu popup = new PopupMenu(requireActivity(), view);
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    int id = item.getItemId();

                    if (id == R.id.menu_link) {
                        startActivity(ShareUtils.getShareIntent(adapter.story.url));
                    } else if (id == R.id.menu_hacker_news_link) {
                        startActivity(ShareUtils.getShareIntent(adapter.story.id));
                    }

                    return true;
                }
            });
            MenuInflater inflater = popup.getMenuInflater();
            inflater.inflate(R.menu.share_menu, popup.getMenu());
            popup.show();
        } else {
            startActivity(ShareUtils.getShareIntent(adapter.story.url));
        }
    }

    public void clickMore(View view) {
        PopupMenu popup = new PopupMenu(requireActivity(), view);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();

                if (id == R.id.menu_user) {
                    clickUser();
                } else if (id == R.id.menu_refresh) {
                    refreshComments();
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
                }

                return true;
            }
        });
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.comments_more_menu, popup.getMenu());

        if (!story.isLink) {
            MenuItem menuItem3 = popup.getMenu().getItem(3);
            if (menuItem3.getItemId() == R.id.menu_archive) {
                menuItem3.setVisible(false);
            } else{
                Toast.makeText(getContext(), "Error: Archive menu item ID wrong", Toast.LENGTH_SHORT).show();
            }
        }

        if (!Utils.shouldBlockAds(getContext())) {
            MenuItem menuItem2 = popup.getMenu().getItem(2);
            if (menuItem2.getItemId() == R.id.menu_adblock) {
                menuItem2.setVisible(false);
            } else{
                Toast.makeText(getContext(), "Error: Adblock menu item ID wrong", Toast.LENGTH_SHORT).show();
            }
        }

        popup.show();
    }

    public void clickUser() {
        UserDialogFragment.showUserDialog(requireActivity().getSupportFragmentManager(), adapter.story.by);
    }

    public void clickComment() {
        if (AccountUtils.getAccountDetails(getContext()) == null) {
            AccountUtils.showLoginPrompt(getParentFragmentManager());
            return;
        }

        Intent intent = new Intent(getContext(), ComposeActivity.class);
        intent.putExtra(ComposeActivity.EXTRA_ID, adapter.story.id);
        intent.putExtra(ComposeActivity.EXTRA_PARENT_TEXT, adapter.story.title);
        startActivity(intent);
    }

    public void clickVote() {
        UserActions.upvote(getContext(), adapter.story.id, getParentFragmentManager());
    }

    private void scrollTop() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            recyclerView.smoothScrollToPosition(0);
        }
    }

    private void scrollPrevious() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            int firstVisible = layoutManager.findFirstVisibleItemPosition();

            int toScrollTo = 0;

            for (int i = 0; i < firstVisible; i++) {
                if (comments.get(i).depth == 0 || i == 0) {
                    toScrollTo = i;
                }
            }

            recyclerView.smoothScrollToPosition(toScrollTo);
        }
    }

    public void scrollNext() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            int firstVisible = layoutManager.findFirstVisibleItemPosition();
            int toScrollTo = firstVisible;

            for (int i = firstVisible + 1; i < comments.size(); i++) {
                if (comments.get(i).depth == 0) {
                    toScrollTo = i;
                    break;
                }
            }

            RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(requireContext()) {
                @Override protected int getVerticalSnapPreference() {
                    return LinearSmoothScroller.SNAP_TO_START;
                }
            };

            smoothScroller.setTargetPosition(toScrollTo);
            layoutManager.startSmoothScroll(smoothScroller);

        }
    }

    private void scrollLast() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        if (layoutManager != null) {
            int firstVisible = layoutManager.findFirstVisibleItemPosition();
            int toScrollTo = firstVisible;

            for (int i = firstVisible + 1; i < comments.size(); i++) {
                if (comments.get(i).depth == 0) {
                    toScrollTo = i;
                }
            }

            RecyclerView.SmoothScroller smoothScroller = new LinearSmoothScroller(requireContext()) {
                @Override protected int getVerticalSnapPreference() {
                    return LinearSmoothScroller.SNAP_TO_START;
                }
            };

            smoothScroller.setTargetPosition(toScrollTo);
            layoutManager.startSmoothScroll(smoothScroller);
        }
    }

    private void updateNavigationVisibility() {
        if (showNavButtons) {
            //If was gone and shouldn't be now, animate in
            if (comments != null && comments.size() > 1 && scrollNavigation.getVisibility() == View.GONE) {
                scrollNavigation.setVisibility(View.VISIBLE);

                AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
                anim.setDuration(300);
                anim.setRepeatMode(Animation.REVERSE);
                scrollNavigation.startAnimation(anim);
            }
        }

    }

    @Override
    public void onItemClick(Comment comment, int pos, View view) {
        final Context ctx = view.getContext();

        Pair[] items;

        if (Utils.timeInSecondsMoreThanTwoWeeksAgo(comment.time)) {
            items = new Pair[]{
                    new Pair<>("View user (" + comment.by + ")", R.drawable.ic_action_user),
                    new Pair<>("Share comment link", R.drawable.ic_action_share),
                    new Pair<>("Copy text", R.drawable.ic_action_copy),
                    new Pair<>("Select text", R.drawable.ic_action_select),
                    new Pair<>("Vote up", R.drawable.ic_action_thumbs_up),
                    new Pair<>("Unvote", R.drawable.ic_action_thumbs),
                    new Pair<>("Vote down (experimental)", R.drawable.ic_action_thumb_down),
            };
        } else {
            items = new Pair[]{
                    new Pair<>("View user (" + comment.by + ")", R.drawable.ic_action_user),
                    new Pair<>("Share comment link", R.drawable.ic_action_share),
                    new Pair<>("Copy text", R.drawable.ic_action_copy),
                    new Pair<>("Select text", R.drawable.ic_action_select),
                    new Pair<>("Vote up", R.drawable.ic_action_thumbs_up),
                    new Pair<>("Unvote", R.drawable.ic_action_thumbs),
                    new Pair<>("Vote down (experimental)", R.drawable.ic_action_thumb_down),
                    new Pair<>("Reply", R.drawable.ic_action_reply)
            };
        }


        ListAdapter adapter = new ArrayAdapter<Pair<String, Integer>>(ctx,
                R.layout.comment_dialog_item,
                R.id.comment_dialog_text,
                items){
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);

                view.setCompoundDrawablesWithIntrinsicBounds((Integer) items[position].second, 0, 0, 0);
                view.setText((CharSequence) items[position].first);

                return view;
            }
        };

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(ctx);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: //view user
                        UserDialogFragment.showUserDialog(requireActivity().getSupportFragmentManager(), comment.by);

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
                        MaterialAlertDialogBuilder selectTextDialogBuilder = new MaterialAlertDialogBuilder(ctx);
                        View rootView = LayoutInflater.from(ctx).inflate(R.layout.select_text_dialog, null);
                        selectTextDialogBuilder.setView(rootView);

                        HtmlTextView htmlTextView = rootView.findViewById(R.id.select_text_htmltextview);
                        htmlTextView.setHtml(comment.text);

                        htmlTextView.setOnClickATagListener(new OnClickATagListener() {
                            @Override
                            public boolean onClick(View widget, String spannedText, @Nullable String href) {
                                Utils.launchCustomTab(getActivity(), href);
                                return true;
                            }
                        });

                        final AlertDialog selectTextDialog = selectTextDialogBuilder.create();

                        rootView.findViewById(R.id.select_text_dialog_done).setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                selectTextDialog.dismiss();
                            }
                        });

                        selectTextDialog.show();

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
                        if (AccountUtils.getAccountDetails(ctx) == null) {
                            AccountUtils.showLoginPrompt(getParentFragmentManager());
                            return;
                        }

                        Intent replyIntent = new Intent(ctx, ComposeActivity.class);
                        replyIntent.putExtra(ComposeActivity.EXTRA_ID, comment.id);
                        replyIntent.putExtra(ComposeActivity.EXTRA_PARENT_TEXT, comment.text);
                        replyIntent.putExtra(ComposeActivity.EXTRA_USER, comment.by);
                        ctx.startActivity(replyIntent);

                }
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public class MyWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            webView.setBackgroundColor(Color.WHITE);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            view.loadUrl(url);
            return true;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            if (!blockAds) {
                return super.shouldInterceptRequest(view, request);
            }
            ByteArrayInputStream EMPTY = new ByteArrayInputStream("".getBytes());
            if (!TextUtils.isEmpty(Utils.adservers)) {
                if (Utils.adservers.contains(":::::" + request.getUrl().getHost())) {
                    Utils.log("Blocked: " + request.getUrl());
                    return new WebResourceResponse("text/plain", "utf-8", EMPTY);
                }
            }

            return super.shouldInterceptRequest(view, request);
        }
    }

    public interface BottomSheetFragmentCallback {
        void onSwitchView(boolean isAtWebView);
    }

    static class PdfAndroidJavascriptBridge {
        private File mFile;
        private @Nullable
        RandomAccessFile mRandomAccessFile;
        private @Nullable Callbacks mCallback;
        private Handler mHandler;

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
                if (mRandomAccessFile != null) {
                    final int bufferSize = (int)(end - begin);
                    byte[] data = new byte[bufferSize];
                    mRandomAccessFile.seek(begin);
                    mRandomAccessFile.read(data);
                    return Base64.encodeToString(data, Base64.DEFAULT);
                } else {
                    return "";
                }
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
                mHandler.post(() -> mCallback.onLoad());
            }
        }

        @JavascriptInterface
        public void onFailure() {
            if (mCallback != null) {
                mHandler.post(() -> mCallback.onFailure());
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
        public void finalize() throws Throwable {
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