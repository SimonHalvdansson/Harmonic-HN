package com.simon.harmonichackernews

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import androidx.webkit.WebViewFeature
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.simon.harmonichackernews.CommentsWebViewController.PageTextCallback
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.CommentsScrollProgress
import com.simon.harmonichackernews.data.PollOption
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.linkpreview.LinkPreviewController
import com.simon.harmonichackernews.network.AlgoliaFallbackManager
import com.simon.harmonichackernews.network.AlgoliaFallbackManager.FallbackListener
import com.simon.harmonichackernews.network.ArchiveOrgUrlGetter
import com.simon.harmonichackernews.network.BackgroundJSONParser
import com.simon.harmonichackernews.network.BackgroundJSONParser.AlgoliaCommentsParseCallback
import com.simon.harmonichackernews.network.JSONParser
import com.simon.harmonichackernews.network.JSONParser.AlgoliaCommentsResponse
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.SummaryManager
import com.simon.harmonichackernews.network.SummaryManager.SummaryCallback
import com.simon.harmonichackernews.network.UserActions
import com.simon.harmonichackernews.network.UserActions.ActionCallback
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.CommentSorter
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.ShareUtils
import com.simon.harmonichackernews.utils.StatusBarProtectionUtils
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.Utils
import com.simon.harmonichackernews.utils.ViewUtils
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Future
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

class CommentsCoordinator(
    private val activity: MainActivity,
    arguments: Bundle,
    savedInstanceState: Bundle?
) {
    private val arguments: Bundle
    private var callback: CommentsPaneCallback?
    private var started = false
    private var destroyed = false
    private var comments: MutableList<Comment>? = null
    private var allComments: MutableList<Comment>? = null
    private var queue: RequestQueue? = null
    private val requestTag = Any()
    private var commentsLoadGeneration = 0
    private var pendingCommentsParse: PendingCommentsParse? = null
    private var webViewHost: CommentsWebViewHost?
    private var commentsContentInsetLeft = 0
    private var commentsContentInsetRight = 0
    private var pendingCommentActionId = -1
    private var pendingReferenceLinkSummaryUrl: String? = null
    private var pendingReferenceLinkSummaryTitle: String? = null
    private var pendingPreviewImageDialogUrl: String? = null
    private var uncachedStoryHeaderLoading = false
    private var progressIndicator: LinearProgressIndicator? = null
    private var linkPreviewController: LinkPreviewController? = null
    private var webViewController: CommentsWebViewController? = null
    private var showWebsite = false
    private var integratedWebview = true
    private var prefIntegratedWebview = true
    private var preloadWebview: String? = "never"
    private var preloadWebviewMinimumBattery = SettingsUtils.DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY
    private var matchWebviewTheme = true
    private var readerModeEnabled = true
    private var readerModeDefault = false
    private var adBlockDisabledForSession = false
    private var pollOptionsLoadStarted = false
    private var pollOptionsLookupStarted = false
    private var closeWebViewOnBack = false
    private var topInset = 0
    private var lastLoaded: Long = 0
    private var commentsLoaded = false
    private var commentsRefreshInProgress = false
    private var loadingFailed = false
    private var loadingFailedServerError = false
    private var showUpdate = false
    private var storyVoteLoading = false
    private var storyFavoriteLoading = false
    private var hasAccountDetails = false
    private var displaySettings: CommentDisplaySettings? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private var username: String? = null
    private var story: Story? = null
    private var filteredUsers: MutableSet<String>? = null
    private var scrollToCommentId = -1
    private var commentsByOpFilterActive = false
    private var originalStatusBarColor = Color.TRANSPARENT
    private var originalStatusBarColorCaptured = false
    private var commentsPaneStatusBarColor = Color.TRANSPARENT
    private var composeHeaderStatusBarCoverage = 0f
    private var commentsHeaderStatusBarColor = Color.TRANSPARENT
    private var appliedStatusBarProtectionKnown = false
    private var appliedStatusBarProtectionEnabled = false
    private var appliedStatusBarProtectionColor = Color.TRANSPARENT
    private var currentCommentSorting: String? = null
    private var composeController: CommentsComposeController? = null

    // Clean fallback management
    private var fallbackManager: AlgoliaFallbackManager? = null

    private class PendingCommentsParse(
        val loadGeneration: Int,
        val storyId: Int,
        var completion: Runnable?
    ) {
        var followUp: Runnable? = null
        var future: Future<*>? = null
    }

    init {
        this.arguments = Bundle(arguments)
        callback = activity
        initializeStory()
        webViewHost = CommentsWebViewHost(activity)
        initializeView(savedInstanceState)
    }

    val webViewRoot: View
        get() {
            val host = webViewHost
            checkNotNull(host) { "Comments coordinator is destroyed" }
            return host.root
        }

    val context: Context?
        get() = if (destroyed) null else activity

    fun getActivity(): MainActivity? {
        return if (destroyed) null else activity
    }

    fun requireActivity(): MainActivity {
        return activity
    }

    fun requireContext(): Context {
        return activity
    }

    val resources: Resources
        get() = activity.getResources()

    val view: View?
        get() = if (webViewHost == null || destroyed) null else webViewHost!!.root

    val isAdded: Boolean
        get() = !destroyed

    fun startActivity(intent: Intent) {
        activity.startActivity(intent)
    }

    private fun initializeStory() {
        filteredUsers = Utils.getFilteredUsers(requireContext())

        story = Story()

        val bundle = arguments
        if (hasStoryHeaderArguments(bundle)) {
            story!!.title = bundle.getString(CommentsContract.EXTRA_TITLE)
            story!!.pdfTitle = bundle.getString(CommentsContract.EXTRA_PDF_TITLE, null)
            story!!.videoTitle = bundle.getString(CommentsContract.EXTRA_VIDEO_TITLE, null)
            story!!.by = bundle.getString(CommentsContract.EXTRA_BY)
            story!!.url = bundle.getString(CommentsContract.EXTRA_URL)
            story!!.previewImageUrl = bundle.getString(CommentsContract.EXTRA_PREVIEW_IMAGE_URL)
            story!!.previewImageUrlLoaded = bundle.getBoolean(
                CommentsContract.EXTRA_PREVIEW_IMAGE_URL_LOADED,
                !TextUtils.isEmpty(story!!.previewImageUrl)
            )
            story!!.previewImageLoadFailed =
                bundle.getBoolean(CommentsContract.EXTRA_PREVIEW_IMAGE_LOAD_FAILED, false)
            story!!.previewImageTintColorLoaded =
                bundle.getBoolean(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_COLOR_LOADED, false)
            story!!.previewImageTintColor =
                bundle.getInt(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_COLOR, 0)
            story!!.previewImageTintSourceUrl =
                bundle.getString(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_SOURCE_URL)
            story!!.previewImageTintBaseColor = bundle.getInt(
                CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_BASE_COLOR,
                Color.TRANSPARENT
            )
            story!!.previewImageTintMode =
                bundle.getString(CommentsContract.EXTRA_PREVIEW_IMAGE_TINT_MODE)
            story!!.faviconTintColorLoaded =
                bundle.getBoolean(CommentsContract.EXTRA_FAVICON_TINT_COLOR_LOADED, false)
            story!!.faviconTintColor = bundle.getInt(CommentsContract.EXTRA_FAVICON_TINT_COLOR, 0)
            story!!.faviconTintSourceUrl =
                bundle.getString(CommentsContract.EXTRA_FAVICON_TINT_SOURCE_URL)
            story!!.faviconTintBaseColor =
                bundle.getInt(CommentsContract.EXTRA_FAVICON_TINT_BASE_COLOR, Color.TRANSPARENT)
            story!!.faviconTintMode = bundle.getString(CommentsContract.EXTRA_FAVICON_TINT_MODE)
            story!!.time = bundle.getInt(CommentsContract.EXTRA_TIME, 0)
            story!!.kids = bundle.getIntArray(CommentsContract.EXTRA_KIDS)
            story!!.pollOptions = bundle.getIntArray(CommentsContract.EXTRA_POLL_OPTIONS)
            story!!.descendants = bundle.getInt(CommentsContract.EXTRA_DESCENDANTS, 0)
            story!!.id = bundle.getInt(CommentsContract.EXTRA_ID, 0)
            story!!.score = bundle.getInt(CommentsContract.EXTRA_SCORE, 0)
            story!!.text = bundle.getString(CommentsContract.EXTRA_TEXT)
            story!!.isLink = bundle.getBoolean(CommentsContract.EXTRA_IS_LINK, true)
            story!!.isComment = bundle.getBoolean(CommentsContract.EXTRA_IS_COMMENT, false)
            story!!.parentId = bundle.getInt(CommentsContract.EXTRA_PARENT_ID, 0)
            story!!.commentMasterId = bundle.getInt(CommentsContract.EXTRA_COMMENT_MASTER_ID, 0)
            story!!.commentMasterTitle =
                bundle.getString(CommentsContract.EXTRA_COMMENT_MASTER_TITLE)
            story!!.commentMasterUrl = bundle.getString(CommentsContract.EXTRA_COMMENT_MASTER_URL)
            story!!.loaded = story!!.by != null

            showWebsite = bundle.getBoolean(CommentsContract.EXTRA_SHOW_WEBSITE, false)
            scrollToCommentId = bundle.getInt(CommentsContract.EXTRA_SCROLL_TO_COMMENT, -1)
        } else {
            story!!.loaded = false
            story!!.id = -1
        }
    }

    private fun hasStoryHeaderArguments(bundle: Bundle?): Boolean {
        return bundle != null && bundle.getInt(
            CommentsContract.EXTRA_ID,
            -1
        ) > 0 && bundle.getString(CommentsContract.EXTRA_TITLE) != null
    }

    private fun loadInitialStorySummaryFromCache() {
        if (story == null || story!!.loaded || story!!.id <= 0) {
            return
        }

        Utils.loadCachedStorySummary(this.context, story)
    }

    private fun initializeView(savedInstanceState: Bundle?) {
        val view = this.webViewRoot
        if (savedInstanceState != null) {
            pendingReferenceLinkSummaryUrl = savedInstanceState.getString(
                STATE_REFERENCE_LINK_SUMMARY_URL
            )
            pendingReferenceLinkSummaryTitle = savedInstanceState.getString(
                STATE_REFERENCE_LINK_SUMMARY_TITLE
            )
            pendingPreviewImageDialogUrl = savedInstanceState.getString(
                STATE_PREVIEW_IMAGE_DIALOG_URL
            )
        }
        val host = webViewHost
        checkNotNull(host) { "Comments WebView host was not created" }
        topInset = 0

        if (savedInstanceState != null) {
            pendingCommentActionId = savedInstanceState.getInt(
                STATE_COMMENT_ACTION_COMMENT_ID,
                -1
            )
            adBlockDisabledForSession = savedInstanceState.getBoolean(
                STATE_ADBLOCK_DISABLED_FOR_SESSION, false
            )
            currentCommentSorting = savedInstanceState.getString(STATE_COMMENT_SORTING)
        }

        if (TextUtils.isEmpty(currentCommentSorting)) {
            currentCommentSorting = SettingsUtils.getPreferredCommentSorting(requireContext())
        }

        originalStatusBarColor = requireActivity().getWindow().getStatusBarColor()
        originalStatusBarColorCaptured = true

        prefIntegratedWebview = SettingsUtils.shouldUseIntegratedWebView(requireContext())
        loadInitialStorySummaryFromCache()
        uncachedStoryHeaderLoading = story!!.id > 0 && !story!!.loaded

        commentsPaneStatusBarColor =
            StatusBarProtectionUtils.getPaneBackgroundColor(requireContext())
        commentsHeaderStatusBarColor = commentsPaneStatusBarColor
        appliedStatusBarProtectionKnown = false
        updateCommentsStatusBarAppearance()

        integratedWebview = prefIntegratedWebview && story!!.isLink
        preloadWebview = SettingsUtils.shouldPreloadWebView(requireContext())
        preloadWebviewMinimumBattery = SettingsUtils.getPreloadWebViewMinimumBattery(requireContext())
        matchWebviewTheme = SettingsUtils.shouldMatchWebViewTheme(requireContext())
        readerModeEnabled = SettingsUtils.shouldUseReaderMode(requireContext())
        readerModeDefault = SettingsUtils.shouldUseReaderModeByDefault(requireContext())
        val blockAds = SettingsUtils.shouldBlockAds(requireContext()) && !adBlockDisabledForSession
        closeWebViewOnBack = SettingsUtils.shouldCloseWebViewOnBack(requireContext())

        progressIndicator = host.progressIndicator
        linkPreviewController = LinkPreviewController(
            story,
            LinkPreviewController.Callbacks { this@CommentsCoordinator.onLinkPreviewChanged() })
        webViewController = CommentsWebViewController(
            this,
            story,
            linkPreviewController!!,
            object : CommentsWebViewController.Callbacks {
                override fun onSwitchView(isAtWebView: Boolean) {
                    if (callback != null) {
                        callback!!.onSwitchView(isAtWebView)
                    }
                }

                override fun syncOnBackPressedCallbackEnabledState() {
                    this@CommentsCoordinator.syncOnBackPressedCallbackEnabledState()
                }

                override fun onReaderModeChanged(enabled: Boolean) {
                    if (composeController != null && webViewController != null) {
                        composeController!!.updateReaderMode(
                            webViewController!!.isReaderModeAvailable, enabled
                        )
                    }
                }

                override fun onReaderModeAvailabilityChanged(available: Boolean) {
                    if (composeController != null && webViewController != null) {
                        composeController!!.updateReaderMode(
                            available, webViewController!!.isReaderModeEnabled()
                        )
                    }
                }

                override fun onFullscreenChanged(fullscreen: Boolean) {
                    if (composeController != null) {
                        composeController!!.updateWebViewFullscreen(fullscreen)
                    }
                }
            })
        webViewController!!.bindViews(host, progressIndicator!!)
        webViewController!!.configure(
            showWebsite,
            integratedWebview,
            preloadWebview,
            preloadWebviewMinimumBattery,
            matchWebviewTheme,
            readerModeEnabled,
            readerModeDefault,
            blockAds
        )

        if (story!!.id <= 0 && story!!.title == null) {
            // Empty view for tablets
            webViewController!!.setContainerVisibility(View.GONE)

            return
        }

        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackCancelled() {
                if (composeController != null
                    && composeController!!.isLinkPreviewOverlayShowing()
                ) {
                    composeController!!.cancelLinkPreviewPredictiveBack()
                    return
                }
                if (composeController != null
                    && composeController!!.isCommentActionOverlayShowing()
                ) {
                    composeController!!.cancelCommentActionPredictiveBack()
                    return
                }

                if (willExpandBottomSheetOnBack()) {
                    endCommentsPredictiveBackVisuals()
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (composeController != null
                    && composeController!!.isLinkPreviewOverlayShowing()
                ) {
                    composeController!!.updateLinkPreviewPredictiveBack(
                        backEvent.progress,
                        backEvent.swipeEdge,
                        backEvent.touchY
                    )
                    return
                }
                if (composeController != null
                    && composeController!!.isCommentActionOverlayShowing()
                ) {
                    composeController!!.updateCommentActionPredictiveBack(
                        backEvent.progress,
                        backEvent.swipeEdge,
                        backEvent.touchY
                    )
                    return
                }

                if (willExpandBottomSheetOnBack()) {
                    updateCommentsPredictiveBackVisuals(backEvent.progress, false)
                }
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                if (composeController != null
                    && composeController!!.isLinkPreviewOverlayShowing()
                ) {
                    composeController!!.startLinkPreviewPredictiveBack(
                        backEvent.progress,
                        backEvent.swipeEdge,
                        backEvent.touchY
                    )
                    return
                }
                if (composeController != null
                    && composeController!!.isCommentActionOverlayShowing()
                ) {
                    composeController!!.updateCommentActionPredictiveBack(
                        backEvent.progress,
                        backEvent.swipeEdge,
                        backEvent.touchY
                    )
                    return
                }

                if (willExpandBottomSheetOnBack()) {
                    updateCommentsPredictiveBackVisuals(backEvent.progress, true)
                }
            }

            override fun handleOnBackPressed() {
                if (composeController != null
                    && composeController!!.isLinkPreviewOverlayShowing()
                ) {
                    if (composeController!!.isLinkPreviewPredictiveBackActive()) {
                        composeController!!.commitLinkPreviewPredictiveBack()
                    } else {
                        composeController!!.requestDismissLinkPreview()
                    }
                    return
                }
                if (composeController != null
                    && composeController!!.isCommentActionOverlayShowing()
                ) {
                    if (composeController!!.isCommentActionPredictiveBackActive()) {
                        composeController!!.commitCommentActionPredictiveBack()
                        return
                    }
                    composeController!!.requestDismissCommentActions()
                    return
                }

                if (webViewController!!.isShowingCustomView) {
                    webViewController!!.hideCustomView(true)
                    return
                }

                val webViewVisible = composeController != null
                        && composeController!!.isWebsiteVisible()
                if (webViewVisible && webViewController!!.isReaderModeEnabled()) {
                    webViewController!!.disableReaderMode()
                    return
                } else if (willExpandBottomSheetOnBack()) {
                    // If the webView can't go back but the back handler is enabled,
                    // it means that the closeWebViewOnBack == true
                    composeController!!.requestExpandSheet()
                    endCommentsPredictiveBackVisuals()
                    return
                } else if (webViewVisible) {
                    webViewController!!.goBackFromVisibleWebView()
                    return
                }

                requireActivity().closeStory()
            }

            fun willExpandBottomSheetOnBack(): Boolean {
                val webViewVisible = composeController != null
                        && composeController!!.isWebsiteVisible()
                return webViewVisible && webViewController!!.willExpandBottomSheetOnBack()
            }
        }

        requireActivity().onBackPressedDispatcher.addCallback(backPressedCallback!!)

        // This is how much the bottom sheet sticks up by default and also decides height of WebView
        // We want to watch for navigation bar height changes (tablets on Android 12L can cause
        // these)
        ViewCompat.setOnApplyWindowInsetsListener(view, object : OnApplyWindowInsetsListener {
            override fun onApplyWindowInsets(
                v: View,
                windowInsets: WindowInsetsCompat
            ): WindowInsetsCompat {
                val systemInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                topInset = systemInsets.top
                updateBottomSheetMargin(systemInsets.bottom)

                val cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
                var contentPaddingLeft = 0
                var contentPaddingRight = 0
                if (Utils.isTablet(this@CommentsCoordinator.resources)) {
                    contentPaddingRight =
                        this@CommentsCoordinator.resources.getDimensionPixelSize(R.dimen.extra_pane_padding)
                }
                val leftPadding = max(max(cutoutInsets.left, systemInsets.left), contentPaddingLeft)
                val rightPadding =
                    max(max(cutoutInsets.right, systemInsets.right), contentPaddingRight)
                setCommentsContentSideInsets(leftPadding, rightPadding)

                webViewController!!.setContainerPadding(
                    leftPadding,
                    systemInsets.top,
                    rightPadding,
                    0,
                )

                return windowInsets
            }
        })
        ViewUtils.requestApplyInsetsWhenAttached(view)

        syncOnBackPressedCallbackEnabledState()

        if (callback != null) {
            callback!!.onSwitchView(showWebsite)
        }

        progressIndicator = host.progressIndicator

        val shouldInitializeWebViewBeforeFirstDraw = integratedWebview && showWebsite
        val shouldInitializeWebViewInBackground = integratedWebview
                && !showWebsite && webViewController!!.shouldInitializeInBackground(requireContext())

        if (shouldInitializeWebViewBeforeFirstDraw) {
            webViewController!!.initialize()
        }

        // The pane color was already resolved from the active theme above. Reusing it avoids two
        // cold theme/preference/resource lookups on the comments-open frame.
        webViewController!!.setContainerBackgroundColor(commentsPaneStatusBarColor)

        comments = ArrayList<Comment>()
        val headerComment = Comment()
        comments!!.add(headerComment) // header
        allComments = ArrayList<Comment>()
        allComments!!.add(headerComment)

        username = AccountUtils.getAccountUsername(requireContext())
        hasAccountDetails = AccountUtils.hasAccountDetails(requireContext())
        displaySettings = createCommentDisplaySettings()

        initializeComposeUi()

        val restoreScrollFromCache = !showWebsite

        // Navigation Compose owns the screen transition. Do not hold the first frame
        // behind the old inset-gated postponed transition; render the header skeleton immediately
        // and start loading on the next main-loop turn.
        view.post(object : Runnable {
            override fun run() {
                if (this@CommentsCoordinator.view !== view || !this@CommentsCoordinator.isAdded) {
                    return
                }
                loadInitialStoryAndComments(restoreScrollFromCache)
            }
        })
        if (shouldInitializeWebViewInBackground && webViewController != null) {
            view.postDelayed(
                webViewController!!.initializeRunnable,
                WEBVIEW_BACKGROUND_INITIALIZATION_DELAY_MS
            )
        }
    }

    private fun updateCommentsPredictiveBackVisuals(progress: Float, started: Boolean) {
        if (webViewController != null) {
            if (started) {
                webViewController!!.beginPredictiveBackScrollFreeze()
            } else {
                webViewController!!.maintainPredictiveBackScrollFreeze()
            }
        }
        if (composeController != null) {
            if (started) {
                composeController!!.beginPredictiveBack(progress)
            } else {
                composeController!!.updatePredictiveBack(progress)
            }
            return
        }
    }

    fun beginVisibleWebViewPredictiveBackScrollFreeze(): Boolean {
        if (webViewController == null || composeController == null || !composeController!!.isWebsiteVisible()) {
            return false
        }
        webViewController!!.beginPredictiveBackScrollFreeze()
        return true
    }

    fun maintainVisibleWebViewPredictiveBackScrollFreeze() {
        if (webViewController != null) {
            webViewController!!.maintainPredictiveBackScrollFreeze()
        }
    }

    fun endVisibleWebViewPredictiveBackScrollFreeze() {
        if (webViewController != null) {
            webViewController!!.endPredictiveBackScrollFreeze()
        }
    }

    fun handlesBackInternally(): Boolean {
        syncOnBackPressedCallbackEnabledState()
        return backPressedCallback?.isEnabled == true
    }

    fun startInternalPredictiveBack(backEvent: BackEventCompat) {
        if (composeController?.isWebsiteVisible() == true) {
            webViewController?.beginPredictiveBackScrollFreeze()
        }
        backPressedCallback?.handleOnBackStarted(backEvent)
    }

    fun updateInternalPredictiveBack(backEvent: BackEventCompat) {
        webViewController?.maintainPredictiveBackScrollFreeze()
        backPressedCallback?.handleOnBackProgressed(backEvent)
    }

    fun cancelInternalPredictiveBack() {
        try {
            backPressedCallback?.handleOnBackCancelled()
        } finally {
            webViewController?.endPredictiveBackScrollFreeze()
        }
    }

    fun commitInternalBack() {
        try {
            backPressedCallback?.takeIf { it.isEnabled }?.handleOnBackPressed()
        } finally {
            webViewController?.endPredictiveBackScrollFreeze()
        }
    }

    private fun endCommentsPredictiveBackVisuals() {
        if (webViewController != null) {
            webViewController!!.endPredictiveBackScrollFreeze()
        }
        if (composeController != null) {
            composeController!!.endPredictiveBack()
            return
        }
    }

    private fun initializeComposeUi() {
        if (webViewHost == null || story == null) {
            return
        }
        composeController = CommentsComposeController.create(
            requireActivity() as ComponentActivity,
            story!!,
            showWebsite,
            username,
            object : CommentsComposeController.Listener {
                override fun onToggleComment(comment: Comment, position: Int) {
                    toggleCommentExpanded(comment, position)
                }

                override fun onCommentAction(comment: Comment, action: Int) {
                    handleComposeCommentAction(comment, action)
                }

                override fun onCommentActionOverlayVisibilityChanged(showing: Boolean) {
                    syncOnBackPressedCallbackEnabledState()
                    updateCommentsStatusBarAppearance()
                }

                override fun onLinkPreviewOverlayVisibilityChanged(showing: Boolean) {
                    syncOnBackPressedCallbackEnabledState()
                }

                override fun onHeaderClick() {
                    if (story != null && story!!.isLink) {
                        Utils.launchCustomTab(requireActivity(), story!!.url)
                    }
                }

                override fun onHeaderPreviewLoaded() {
                    story?.id?.takeIf { it > 0 }?.let(activity::onStoryPreviewImageLoaded)
                }

                override fun onHeaderPreviewLoadFailed() {
                    story?.id?.takeIf { it > 0 }?.let(activity::onStoryPreviewImageLoadFailed)
                }

                override fun onHeaderAction(action: Int) {
                    if (action == CommentsComposeController.HEADER_ACTION_USER) {
                        clickUser()
                    } else if (action == CommentsComposeController.HEADER_ACTION_REPLY) {
                        clickComment()
                    } else if (action == CommentsComposeController.HEADER_ACTION_VOTE) {
                        clickVote()
                    } else if (action == CommentsComposeController.HEADER_ACTION_FAVORITE) {
                        clickFavorite()
                    } else if (action == CommentsComposeController.HEADER_ACTION_BOOKMARK) {
                        toggleStoryBookmark()
                        syncComposeState()
                    } else if (action == CommentsComposeController.HEADER_ACTION_SUMMARIZE) {
                        requestComposeSummary()
                    } else if (action == CommentsComposeController.HEADER_ACTION_REFRESH) {
                        onRetry()
                    }
                }

                override fun onShareAction(action: Int) {
                    shareFromCompose(action)
                }

                override fun onMoreAction(action: Int) {
                    handleComposeMoreAction(action)
                }

                override fun onSearchResultSelected(comment: Comment) {
                    selectComposeSearchResult(comment)
                }

                override fun onSortComments(sortType: String) {
                    changeCommentSorting(sortType)
                }

                override fun onSheetAction(action: Int) {
                    handleComposeSheetAction(action)
                }

                override fun onCollapseSheetForWebsite() {
                    collapseBottomSheetForWebsite()
                }

                override fun onSheetProgressChanged(expandedFraction: Float) {
                    if (expandedFraction < 0.999f && integratedWebview
                        && webViewController != null && !webViewController!!.hasWebView()
                    ) {
                        webViewController!!.initializeForVisibleWebsite()
                    }
                    updateCommentsStatusBarAppearance()
                }

                override fun onSheetSettled(expanded: Boolean) {
                    if (!expanded && integratedWebview
                        && webViewController != null && !webViewController!!.hasWebView()
                    ) {
                        webViewController!!.initializeForVisibleWebsite()
                    }
                    if (callback != null) {
                        callback!!.onSwitchView(!expanded)
                    }
                    syncOnBackPressedCallbackEnabledState()
                    updateCommentsStatusBarAppearance()
                }

                override fun onHeaderColorChanged(color: Int) {
                    updateHeaderStatusBarColor(color)
                }

                override fun onHeaderCoverageChanged(coverage: Float) {
                    composeHeaderStatusBarCoverage = max(0f, min(1f, coverage))
                    updateCommentsStatusBarAppearance()
                }

                override fun onPollOption(optionId: Int) {
                    UserActions.votePollOption(requireContext(), optionId)
                }
            })
        requireActivity().attachCommentsComposeController(composeController!!)
        restoreLinkSummaryAfterRecreation()
        syncComposeState()
        syncOnBackPressedCallbackEnabledState()
    }

    private fun syncComposeState() {
        val controller = composeController
        if (controller == null || story == null || comments == null) {
            return
        }
        val readerAvailable =
            webViewController != null && webViewController!!.isReaderModeAvailable
        val readerEnabled = webViewController != null && webViewController!!.isReaderModeEnabled()
        if (displaySettings == null) {
            displaySettings = createCommentDisplaySettings()
        }
        controller.updateContent(
            story!!,
            comments!!,
            displaySettings!!,
            commentsLoaded,
            commentsRefreshInProgress,
            loadingFailed,
            loadingFailedServerError,
            showUpdate,
            commentsByOpFilterActive,
            hasCommentsByOp(),
            webViewController != null && webViewController!!.isBlockingAds,
            integratedWebview,
            readerAvailable,
            readerEnabled,
            getCurrentCommentSorting()!!,
            topInset,
            commentsContentInsetLeft,
            commentsContentInsetRight,
            storyVoteLoading,
            storyFavoriteLoading
        )
    }

    private fun requestComposeSummary() {
        val controller = composeController
        if (controller == null) {
            return
        }
        controller.updateStorySummaryLoading(true)
        onRequest(Runnable { controller.refreshContent() }, Runnable {
            controller.updateStorySummaryLoading(false)
            controller.refreshContent()
        })
    }

    private fun shareFromCompose(action: Int) {
        if (story == null) {
            return
        }
        val shareIntent: Intent?
        if (action == CommentsComposeController.SHARE_ARTICLE) {
            shareIntent = ShareUtils.getShareIntent(story!!.url)
        } else if (action == CommentsComposeController.SHARE_ARTICLE_TITLE) {
            shareIntent = ShareUtils.getShareIntentWithTitle(story!!.title, story!!.url)
        } else if (action == CommentsComposeController.SHARE_HN) {
            shareIntent = ShareUtils.getShareIntent(story!!.id)
        } else if (action == CommentsComposeController.SHARE_ALL) {
            shareIntent = ShareUtils.getShareIntentWithTitle(story!!.title.orEmpty(), story!!.id, story!!.url)
        } else {
            shareIntent = ShareUtils.getShareIntentWithTitle(story!!.title, story!!.id)
        }
        shareIntent?.let(::startActivity)
    }

    private fun handleComposeMoreAction(action: Int) {
        if (story == null) {
            return
        }
        if (action == CommentsComposeController.MORE_REFRESH) {
            onRetry()
        } else if (action == CommentsComposeController.MORE_OPEN_PARENT && story!!.parentId > 0) {
            Utils.openCommentsActivity(story!!.parentId, -1, requireContext())
        } else if (action == CommentsComposeController.MORE_OPEN_TOP_LEVEL && story!!.commentMasterId > 0) {
            Utils.openCommentsActivity(story!!.commentMasterId, -1, requireContext())
        } else if (action == CommentsComposeController.MORE_TOGGLE_BOOKMARK) {
            toggleStoryBookmark()
            syncComposeState()
        } else if (action == CommentsComposeController.MORE_SEARCH) {
            showComposeCommentSearch()
        } else if (action == CommentsComposeController.MORE_COMMENTS_BY_OP) {
            if (commentsByOpFilterActive) {
                resetCommentsByOpFilter()
            } else {
                showCommentsByOp()
            }
        } else if (action == CommentsComposeController.MORE_OPEN_BROWSER) {
            onOpenInBrowser()
        } else if (action == CommentsComposeController.MORE_DISABLE_ADBLOCK) {
            adBlockDisabledForSession = true
            webViewController!!.disableAdBlockAndReload()
        } else if (action == CommentsComposeController.MORE_ARCHIVE_ORG) {
            openArchiveOrg()
        } else if (action == CommentsComposeController.MORE_ARCHIVE_IS) {
            openArchiveIs()
        } else if (action == CommentsComposeController.MORE_ARCHIVE_TODAY) {
            openArchiveToday()
        }
    }

    private fun showComposeCommentSearch() {
        resetCommentsByOpFilter()
        if (composeController != null) {
            composeController!!.showCommentSearch()
        }
    }

    private fun selectComposeSearchResult(comment: Comment) {
        expandParentsForComment(comment)
        if (composeController != null) {
            syncComposeState()
            composeController!!.scrollToSearchResult(comment.id)
        }
    }

    private fun handleComposeSheetAction(action: Int) {
        if (webViewController == null) {
            return
        }
        if (action == CommentsComposeController.SHEET_REFRESH) {
            if (webViewController!!.isShowingOfflineOrCachedPage && webViewController!!.hasLastFailedUrl()) {
                webViewController!!.retryLastFailedUrl()
            } else {
                webViewController!!.reload()
            }
        } else if (action == CommentsComposeController.SHEET_EXPAND) {
            if (composeController != null) {
                composeController!!.requestExpandSheet()
            }
        } else if (action == CommentsComposeController.SHEET_BROWSER) {
            clickBrowser()
        } else if (action == CommentsComposeController.SHEET_READER) {
            webViewController!!.toggleReaderMode()
        } else if (action == CommentsComposeController.SHEET_INVERT) {
            webViewController!!.toggleDarkMode()
        }
    }

    private fun handleComposeCommentAction(comment: Comment, action: Int) {
        if (!this.isAdded || composeController == null) {
            return
        }
        val ctx = requireContext()
        if (action == CommentsComposeController.COMMENT_ACTION_USER) {
            if (!TextUtils.isEmpty(comment.by)) {
                requireActivity().showUserDialog(
                    comment.by.orEmpty(),
                    Runnable { updateUserTags() })
            }
            return
        }
        if (action == CommentsComposeController.COMMENT_ACTION_SHARE) {
            ShareUtils.getShareIntent(comment.id)?.let(::startActivity)
            return
        }
        if (action == CommentsComposeController.COMMENT_ACTION_COPY) {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
            if (clipboard != null) {
                clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                        "Hacker News comment",
                        Html.fromHtml(
                            if (comment.text == null) "" else comment.text,
                            Html.FROM_HTML_MODE_LEGACY
                        )
                    )
                )
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(ctx, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }
        if (action == CommentsComposeController.COMMENT_ACTION_BOOKMARK) {
            if (Utils.isBookmarked(ctx, comment.id)) {
                Utils.removeBookmark(ctx, comment.id)
            } else {
                Utils.addBookmark(ctx, comment.id)
            }
            composeController!!.refreshCommentActionState()
            return
        }
        if (action == CommentsComposeController.COMMENT_ACTION_REPLY) {
            if (!AccountUtils.hasAccountDetails(ctx)) {
                AccountUtils.showLoginPrompt(ctx)
                return
            }
            if (Utils.timeInSecondsMoreThanTwoWeeksAgo(comment.time)) {
                Toast.makeText(ctx, "This comment is too old to reply to", Toast.LENGTH_SHORT)
                    .show()
                return
            }
            val replyIntent = ComposeEditorContract.createIntent(ctx)
            replyIntent.putExtra(ComposeEditorContract.EXTRA_ID, comment.id)
            replyIntent.putExtra(ComposeEditorContract.EXTRA_PARENT_TEXT, comment.text)
            replyIntent.putExtra(
                ComposeEditorContract.EXTRA_POST_TITLE,
                if (story == null) null else story!!.title
            )
            replyIntent.putExtra(ComposeEditorContract.EXTRA_USER, comment.by)
            replyIntent.putExtra(
                ComposeEditorContract.EXTRA_TYPE,
                ComposeEditorContract.TYPE_COMMENT_REPLY
            )
            startActivity(replyIntent)
            return
        }

        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(ctx)
            return
        }
        if (action == CommentsComposeController.COMMENT_ACTION_FAVORITE) {
            val oldFavorited = Utils.isFavorited(ctx, comment.id)
            val newFavorited = !oldFavorited
            composeController!!.setCommentActionFavoriteLoading(comment.id, true)
            UserActions.setFavorite(
                ctx, comment.id, newFavorited,
                object : ActionCallback {
                    override fun onSuccess(response: Response) {
                        if (composeController != null) {
                            composeController!!.setCommentActionFavoriteLoading(
                                comment.id, false
                            )
                        }
                    }

                    override fun onFailure(summary: String?, response: String?) {
                        Utils.setFavorite(ctx, comment.id, oldFavorited)
                        if (composeController != null) {
                            composeController!!.setCommentActionFavoriteLoading(
                                comment.id, false
                            )
                        }
                        UserActions.showFailureDetailDialog(ctx, summary, response)
                        Toast.makeText(
                            ctx,
                            "Couldn't update favorite",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            return
        }

        if (action != CommentsComposeController.COMMENT_ACTION_UPVOTE && action != CommentsComposeController.COMMENT_ACTION_DOWNVOTE && action != CommentsComposeController.COMMENT_ACTION_UNVOTE) {
            return
        }
        if (composeController!!.isCommentActionVoteLoading(comment.id)) {
            return
        }
        val wasUpvoted = Utils.isUpvoted(ctx, comment.id, true)
        val wasDownvoted = !wasUpvoted
                && composeController!!.isCommentActionDownvoted(comment.id)
        composeController!!.setCommentActionVoteLoading(comment.id, action)
        val callback: ActionCallback = object : ActionCallback {
            override fun onSuccess(response: Response) {
                val upvoted = action == CommentsComposeController.COMMENT_ACTION_UPVOTE
                val downvoted = action == CommentsComposeController.COMMENT_ACTION_DOWNVOTE
                Utils.setUpvoted(ctx, comment.id, true, upvoted)
                if (composeController != null) {
                    composeController!!.finishCommentActionVote(comment.id, downvoted)
                }
            }

            override fun onFailure(summary: String?, response: String?) {
                Utils.setUpvoted(ctx, comment.id, true, wasUpvoted)
                if (composeController != null) {
                    composeController!!.finishCommentActionVote(comment.id, wasDownvoted)
                }
            }
        }
        if (action == CommentsComposeController.COMMENT_ACTION_UPVOTE) {
            UserActions.upvote(ctx, comment.id, callback)
        } else if (action == CommentsComposeController.COMMENT_ACTION_DOWNVOTE) {
            UserActions.downvote(ctx, comment.id, callback)
        } else {
            UserActions.unvote(ctx, comment.id, callback)
        }
    }

    private fun syncOnBackPressedCallbackEnabledState() {
        val commentsController = composeController
        val websiteController = webViewController
        val webViewVisible = websiteController?.hasWebView() == true &&
            commentsController?.isWebsiteVisible() == true
        val enabled = when {
            commentsController?.isLinkPreviewOverlayShowing() == true -> true
            commentsController?.isCommentActionOverlayShowing() == true -> true
            websiteController?.isShowingCustomView == true -> true
            webViewVisible && websiteController?.isReaderModeEnabled() == true -> true
            closeWebViewOnBack -> webViewVisible
            else -> webViewVisible && websiteController?.canGoBack() == true
        }
        backPressedCallback?.isEnabled = enabled
    }

    private fun updateBottomSheetMargin(navbarHeight: Int) {
        val standardMargin = Utils.pxFromDpInt(
            this.resources, (if (Utils.isTablet(
                    this.resources
                )
            ) 81 else 68).toFloat()
        )

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        params.setMargins(0, 0, 0, standardMargin + navbarHeight)

        webViewController!!.setContainerLayoutParams(params)
    }

    private fun updateHeaderStatusBarColor(color: Int) {
        commentsHeaderStatusBarColor = color
        updateCommentsStatusBarAppearance()
    }

    private fun syncCommentsStatusBarProtection() {
        if (!this.isAdded) {
            return
        }
        commentsPaneStatusBarColor =
            StatusBarProtectionUtils.getPaneBackgroundColor(requireContext())
        updateCommentsStatusBarAppearance()
    }

    private fun updateCommentsStatusBarAppearance(commentsStatusBarColor: Int = this.currentCommentsStatusBarColor) {
        val host = webViewHost
        if (host == null || this.context == null) {
            return
        }

        val showStatusBarProtection = shouldShowCommentsStatusBarProtection()
        val statusBarProtectionEnabled = showStatusBarProtection
        val statusBarColor =
            if (showStatusBarProtection) commentsStatusBarColor else commentsPaneStatusBarColor
        if (!appliedStatusBarProtectionKnown || appliedStatusBarProtectionEnabled != statusBarProtectionEnabled || (statusBarProtectionEnabled && appliedStatusBarProtectionColor != statusBarColor)) {
            StatusBarProtectionUtils.setTopProtection(
                host.root,
                statusBarProtectionEnabled,
                statusBarColor
            )
            appliedStatusBarProtectionKnown = true
            appliedStatusBarProtectionEnabled = statusBarProtectionEnabled
            appliedStatusBarProtectionColor =
                if (statusBarProtectionEnabled) statusBarColor else Color.TRANSPARENT
        }
        if (getActivity() == null) {
            return
        }
        val windowStatusBarColor =
            if (activity.isAdaptiveTwoPaneNavigation ||
                SettingsUtils.shouldUseTransparentStatusBar(requireContext())
            )
                Color.TRANSPARENT
            else
                statusBarColor
        if (requireActivity().getWindow().getStatusBarColor() != windowStatusBarColor) {
            requireActivity().getWindow().setStatusBarColor(windowStatusBarColor)
        }
    }

    fun onAdaptiveLayoutChanged() {
        updateCommentsStatusBarAppearance()
    }

    private fun shouldShowCommentsStatusBarProtection(): Boolean {
        return this.isBottomSheetFullyExpanded
    }

    val isBottomSheetFullyExpanded: Boolean
        get() = composeController != null && composeController!!.isSheetExpanded()

    fun switchStoryViewIfMatching(storyId: Int, showWebsite: Boolean): Boolean {
        if (!this.isAdded || story == null || story!!.id != storyId || !integratedWebview || webViewController == null) {
            return false
        }
        if (showWebsite) {
            webViewController!!.initialize()
            if (composeController != null) {
                composeController!!.requestWebsite()
                return true
            }
            scrollCommentsToTopThenCollapseBottomSheet()
        } else {
            if (composeController != null) {
                composeController!!.requestStopScroll()
                composeController!!.requestExpandSheet()
            }
        }
        return true
    }

    private fun scrollCommentsToTopThenCollapseBottomSheet() {
        if (composeController != null) {
            composeController!!.requestWebsite()
        } else {
            collapseBottomSheetForWebsite()
        }
    }

    private fun collapseBottomSheetForWebsite() {
        if (webViewController != null) {
            webViewController!!.initialize()
        }
        if (composeController != null) {
            composeController!!.requestCollapseSheet()
        }
    }

    private val currentCommentsStatusBarColor: Int
        get() {
            val headerCoverage = this.headerStatusBarCoverage
            return ColorUtils.blendARGB(
                commentsPaneStatusBarColor,
                commentsHeaderStatusBarColor,
                headerCoverage
            )
        }

    private val headerStatusBarCoverage: Float
        get() {
            if (composeController != null) {
                return composeHeaderStatusBarCoverage
            }
            return 0f
        }

    fun onConfigurationChanged(newConfig: Configuration) {
        // this is to make sure that action buttons in header get updated padding on rotations...
        // yes it's ugly, I know
        if (this.context != null && Utils.isTablet(this.resources)) {
            displaySettings = createCommentDisplaySettings()
            notifyHeaderChanged()
        }
    }

    private fun toggleCommentExpanded(comment: Comment, index: Int) {
        if (comments == null) {
            return
        }
        comment.expanded = !comment.expanded
        syncComposeState()
    }

    private fun setCommentsContentSideInsets(leftInset: Int, rightInset: Int) {
        val safeLeftInset = max(0, leftInset)
        val safeRightInset = max(0, rightInset)
        if (commentsContentInsetLeft == safeLeftInset
            && commentsContentInsetRight == safeRightInset
        ) {
            return
        }

        commentsContentInsetLeft = safeLeftInset
        commentsContentInsetRight = safeRightInset

        syncComposeState()
    }

    private fun createCommentDisplaySettings(): CommentDisplaySettings {
        val context = requireContext()
        return CommentDisplaySettings.from(
            context,
            shouldShowInvertAction(),
            Utils.isTablet(this.resources),
            hasAccountDetails,
            story != null && story!!.isLink && Utils.canProvideSummary(context)
        )
    }

    private fun shouldShowInvertAction(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
                || WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
    }


    fun onStart() {
        if (destroyed || started) return
        started = true

        if (callback != null) {
            callback!!.onSwitchView(
                composeController != null
                        && composeController!!.isWebsiteVisible()
            )
        }

        val ctx = requireContext()
        hasAccountDetails = AccountUtils.hasAccountDetails(ctx)
        val latestSettings = createCommentDisplaySettings()
        val themeChanged = displaySettings != null
                && !TextUtils.equals(displaySettings!!.theme, latestSettings.theme)
        displaySettings = latestSettings
        if (themeChanged) {
            val backgroundColor = ContextCompat.getColor(
                ctx, ThemeUtils.getBackgroundColorResource(ctx)
            )
            if (webViewController != null) {
                webViewController!!.setContainerBackgroundColor(backgroundColor)
            }
        }
        syncComposeState()
    }

    fun onResume() {
        if (destroyed) return

        val shouldShowUpdate = SettingsUtils.shouldAlwaysShowTapToRefresh(requireContext())
                || (lastLoaded != 0L && (System.currentTimeMillis() - lastLoaded) > 1000 * 60 * 60 && !Utils.timeInSecondsMoreThanTwoHoursAgo(
            story!!.time
        ))
        if (showUpdate != shouldShowUpdate) {
            showUpdate = shouldShowUpdate
            if (showUpdate && composeController != null) {
                composeController!!.clearSearchScrollTopTarget()
            }
        }
        syncCommentsStatusBarProtection()
        syncComposeState()
    }

    fun onStop() {
        if (!started) return
        started = false

        if (composeController == null || story == null) {
            return
        }
        if (MainActivity.commentsScrollProgresses == null) {
            MainActivity.commentsScrollProgresses = ArrayList()
        }
        val recordedProgress = recordScrollProgress()
        for (i in MainActivity.commentsScrollProgresses.indices) {
            val scrollProgress = MainActivity.commentsScrollProgresses.get(i)
            if (scrollProgress.storyId == story!!.id) {
                MainActivity.commentsScrollProgresses.set(i, recordedProgress)
                return
            }
        }
        MainActivity.commentsScrollProgresses.add(recordedProgress)
    }

    fun onSaveInstanceState(outState: Bundle) {
        if (composeController != null && composeController!!.isLinkPreviewReferenceShowing()) {
            outState.putString(
                STATE_REFERENCE_LINK_SUMMARY_URL,
                composeController!!.getLinkPreviewVisibleUrl()
            )
            outState.putString(
                STATE_REFERENCE_LINK_SUMMARY_TITLE,
                composeController!!.getLinkPreviewFallbackTitle()
            )
        } else if (composeController != null && composeController!!.isLinkPreviewImageShowing()) {
            outState.putString(
                STATE_PREVIEW_IMAGE_DIALOG_URL,
                composeController!!.getLinkPreviewVisibleUrl()
            )
        }

        val visibleCommentActionId = if (composeController == null)
            pendingCommentActionId
        else
            composeController!!.getVisibleCommentActionId()
        if (visibleCommentActionId != -1) {
            outState.putInt(STATE_COMMENT_ACTION_COMMENT_ID, visibleCommentActionId)
        }
        if (adBlockDisabledForSession) {
            outState.putBoolean(STATE_ADBLOCK_DISABLED_FOR_SESSION, true)
        }
        outState.putString(STATE_COMMENT_SORTING, getCurrentCommentSorting())
    }

    private fun recordScrollProgress(): CommentsScrollProgress {
        val scrollProgress = CommentsScrollProgress()

        scrollProgress.storyId = story!!.id
        if (composeController != null) {
            scrollProgress.topCommentId = composeController!!.firstVisibleCommentId
            // LazyColumn exposes how far the first visible item has scrolled past the top.
            scrollProgress.topCommentOffset = -composeController!!.firstVisibleCommentOffset
        }

        scrollProgress.collapsedIDs = HashSet()

        for (c in comments!!) {
            if (!c.expanded) {
                scrollProgress.collapsedIDs.add(c.id)
            }
        }

        return scrollProgress
    }

    private fun restoreScrollProgress(scrollProgress: CommentsScrollProgress) {
        for (i in comments!!.indices) {
            val c = comments!!.get(i)
            c.expanded = !scrollProgress.collapsedIDs.contains(c.id)
        }
        if (composeController != null) {
            syncComposeState()
            composeController!!.scrollToComment(
                scrollProgress.topCommentId,
                scrollProgress.topCommentOffset,
                false
            )
        }
    }

    private fun scrollToTargetComment() {
        if (scrollToCommentId == -1) return
        for (i in comments!!.indices) {
            if (comments!!.get(i).id == scrollToCommentId) {
                expandParentsForComment(comments!!.get(i))
                if (composeController != null) {
                    syncComposeState()
                    composeController!!.scrollToComment(scrollToCommentId, topInset, false)
                    scrollToCommentId = -1
                    return
                }
                scrollToCommentId = -1
                return
            }
        }
        Toast.makeText(this.context, "Comment not found", Toast.LENGTH_SHORT).show()
        scrollToCommentId = -1
    }

    private fun expandParentsForComment(comment: Comment) {
        var expandedAny = false
        var parentId = comment.parent

        while (parentId > 0) {
            var parent: Comment? = null
            for (i in 1..<comments!!.size) {
                val c = comments!!.get(i)
                if (c.id == parentId) {
                    parent = c
                    break
                }
            }

            if (parent == null) {
                break
            }

            if (!parent.expanded) {
                parent.expanded = true
                expandedAny = true
            }

            parentId = parent.parent
        }

        if (expandedAny) {
            syncComposeState()
        }
    }

    fun onDestroy() {
        if (destroyed) return
        if (started) onStop()
        val controllerToDetach = composeController
        val preserveReferenceSummary =
            getActivity() != null && requireActivity().isChangingConfigurations()
                    && composeController != null && composeController!!.isLinkPreviewReferenceShowing()
        val preservePreviewImage =
            getActivity() != null && requireActivity().isChangingConfigurations()
                    && composeController != null && composeController!!.isLinkPreviewImageShowing()
        pendingReferenceLinkSummaryUrl = if (preserveReferenceSummary)
            composeController!!.getLinkPreviewVisibleUrl()
        else
            null
        pendingReferenceLinkSummaryTitle = if (preserveReferenceSummary)
            composeController!!.getLinkPreviewFallbackTitle()
        else
            null
        pendingPreviewImageDialogUrl = if (preservePreviewImage)
            composeController!!.getLinkPreviewVisibleUrl()
        else
            null
        if (composeController != null) {
            if (composeController!!.isLinkPreviewOverlayShowing()) {
                composeController!!.completeLinkPreviewDismiss()
            }
            composeController!!.completeCommentActionDismiss()
        }
        if (originalStatusBarColorCaptured && getActivity() != null) {
            requireActivity().getWindow().setStatusBarColor(originalStatusBarColor)
            originalStatusBarColorCaptured = false
        }

        val rootView = this.view
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, null)
        }

        if (backPressedCallback != null) {
            backPressedCallback!!.remove()
            backPressedCallback = null
        }

        if (queue != null) {
            queue!!.cancelAll(requestTag)
        }
        commentsLoadGeneration++
        cancelPendingCommentsParse()
        fallbackManager = null
        if (webViewController != null) {
            webViewController!!.onDestroyView(rootView)
        }
        if (controllerToDetach != null) {
            activity.detachCommentsComposeController(controllerToDetach)
        }

        clearViewReferences()
        callback = null
        destroyed = true
    }

    private fun restoreLinkSummaryAfterRecreation() {
        val rootView = this.view
        if (!TextUtils.isEmpty(pendingPreviewImageDialogUrl) && rootView != null) {
            val imageUrl = pendingPreviewImageDialogUrl
            pendingPreviewImageDialogUrl = null
            rootView.post(Runnable {
                if (composeController != null) {
                    val backgroundColor = if (commentsHeaderStatusBarColor != Color.TRANSPARENT)
                        commentsHeaderStatusBarColor
                    else
                        ContextCompat.getColor(
                            requireContext(),
                            ThemeUtils.getBackgroundColorResource(requireContext())
                        )
                    composeController!!.showImagePreview(
                        imageUrl!!,
                        if (TextUtils.isEmpty(story!!.title))
                            "Story preview image"
                        else
                            "Preview image for " + story!!.title,
                        null,
                        backgroundColor
                    )
                }
            })
            return
        }
        val referenceRootView = this.view
        if (TextUtils.isEmpty(pendingReferenceLinkSummaryUrl) || referenceRootView == null) {
            return
        }
        val url = pendingReferenceLinkSummaryUrl
        val title = pendingReferenceLinkSummaryTitle
        pendingReferenceLinkSummaryUrl = null
        pendingReferenceLinkSummaryTitle = null
        referenceRootView.post(Runnable {
            if (composeController != null) {
                composeController!!.showReferencePreview(url!!, title)
            }
        })
    }

    private fun clearViewReferences() {
        webViewHost = null
        progressIndicator = null
        composeController = null
        appliedStatusBarProtectionKnown = false
        if (webViewController != null) {
            webViewController!!.clearViewReferences()
            webViewController = null
        }
        if (linkPreviewController != null) {
            linkPreviewController!!.cancelPendingNitterLinkPreviewRead()
            linkPreviewController = null
        }
    }

    fun onRetry() {
        retryComments()
    }

    private fun retryComments() {
        if (!this.isCommentsViewActive || story == null) {
            Log.w(
                TAG, ("Retry ignored: commentsViewActive=" + this.isCommentsViewActive
                        + ", storyPresent=" + (story != null))
            )
            return
        }
        if (pendingCommentsParse != null) {
            Log.d(
                TAG, "Retry ignored while comments are still being parsed for storyId="
                        + story!!.id
            )
            return
        }
        Log.d(TAG, "Retry requested for storyId=" + story!!.id)
        setCommentsRefreshInProgress(true)
        loadStoryAndComments(story!!.id, null)
    }

    fun onOpenInBrowser() {
        Utils.launchInExternalBrowser(
            requireActivity(),
            "https://news.ycombinator.com/item?id=" + story!!.id
        )
    }

    private fun loadInitialStoryAndComments(restoreScrollFromCache: Boolean) {
        val context = this.context
        if (context == null || !this.isCommentsViewActive || story == null) {
            return
        }

        queue = NetworkComponent.getRequestQueueInstance(context)
        val cachedResponse = Utils.loadCachedStory(context, story!!.id)

        val loadGeneration = loadStoryAndComments(story!!.id, cachedResponse)

        if (cachedResponse != null && loadGeneration >= 0) {
            handleJsonResponse(
                story!!.id,
                cachedResponse,
                false,
                false,
                restoreScrollFromCache,
                loadGeneration,
                null
            )
        }
    }

    private fun loadStoryAndComments(id: Int, oldCachedResponse: String?): Int {
        val context = this.context
        if (context == null || queue == null || !this.isCommentsViewActive) {
            Log.w(
                TAG, ("Skipping comments load for storyId=" + id
                        + ": contextPresent=" + (context != null)
                        + ", queuePresent=" + (queue != null)
                        + ", commentsViewActive=" + this.isCommentsViewActive)
            )
            return -1
        }

        val loadGeneration = ++commentsLoadGeneration
        cancelPendingCommentsParse()
        Log.d(
            TAG,
            "Loading comments for storyId=" + id + ", hasCachedResponse=" + (oldCachedResponse != null)
        )
        lastLoaded = System.currentTimeMillis()
        if (showUpdate) {
            showUpdate = false
            notifyHeaderChanged()
        }

        // Initialize fallback manager
        val requestQueue = queue ?: return -1
        fallbackManager = AlgoliaFallbackManager(
            requireContext(),
            requestQueue,
            requestTag,
            filteredUsers ?: HashSet(),
            object : FallbackListener {
                override fun onAlgoliaSuccess(response: String?) {
                    if (!isCurrentCommentsLoad(loadGeneration, id)) {
                        Log.w(TAG, "Ignoring stale Algolia success for storyId=" + id)
                        return
                    }
                    Log.d(
                        TAG, ("Algolia comments load succeeded for storyId=" + id
                                + ", responseLength=" + (if (response == null) 0 else response.length))
                    )
                    if (TextUtils.isEmpty(oldCachedResponse) || oldCachedResponse != response) {
                        val parseLiveResponse = Runnable {
                            handleJsonResponse(
                                id,
                                response!!,
                                true,
                                oldCachedResponse == null,
                                false,
                                loadGeneration,
                                Runnable { finishCommentsRefresh(loadGeneration, id) })
                        }
                        if (!deferUntilPendingParseFinishes(
                                loadGeneration,
                                id,
                                parseLiveResponse
                            )
                        ) {
                            parseLiveResponse.run()
                        }
                    } else if (!attachCompletionToPendingParse(
                            loadGeneration,
                            id,
                            Runnable { finishCommentsRefresh(loadGeneration, id) })
                    ) {
                        finishCommentsRefresh(loadGeneration, id)
                    }
                }

                override fun onAlgoliaFailed(noInternet: Boolean) {
                    if (!isCurrentCommentsLoad(loadGeneration, id)) {
                        Log.w(TAG, "Ignoring stale Algolia failure for storyId=" + id)
                        return
                    }
                    Log.w(
                        TAG,
                        "Algolia comments load failed for storyId=" + id + ", noInternet=" + noInternet
                    )
                    loadingFailed = true
                    loadingFailedServerError = !noInternet
                    commentsLoaded = true
                    setCommentsRefreshInProgress(false)
                    notifyHeaderChanged()
                }

                override fun onUsingFallback() {
                    val context: Context? = this@CommentsCoordinator.context
                    if (context != null && isCurrentCommentsLoad(loadGeneration, id)) {
                        Toast.makeText(
                            context,
                            "Algolia API failed, using official HN API",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onHNAPIStoryLoaded(loadedStory: Story) {
                    if (!isCurrentCommentsLoad(loadGeneration, id)) {
                        return
                    }
                    // Update story data
                    story!!.title = loadedStory.title
                    story!!.by = loadedStory.by
                    story!!.score = loadedStory.score
                    story!!.time = loadedStory.time
                    story!!.url = loadedStory.url
                    story!!.isLink = loadedStory.isLink
                    story!!.isComment = loadedStory.isComment
                    story!!.text = loadedStory.text
                    story!!.kids = loadedStory.kids
                    story!!.pollOptions = loadedStory.pollOptions
                    story!!.descendants = loadedStory.descendants
                    story!!.parentId = loadedStory.parentId
                    story!!.loaded = true

                    // Reset comments
                    if (allComments != null && allComments!!.size > 1) {
                        allComments!!.subList(1, allComments!!.size).clear()
                    }
                    val oldSize = comments!!.size
                    if (oldSize > 1) {
                        comments!!.subList(1, oldSize).clear()
                    }

                    loadingFailed = false
                    loadingFailedServerError = false
                    if (linkPreviewController != null) {
                        linkPreviewController!!.loadNetworkPreviews(context)
                    }
                    refreshHeaderAfterStoryLoad()
                    maybeLoadPollOptions()
                }

                override fun onHNAPIFailed() {
                    if (!isCurrentCommentsLoad(loadGeneration, id)) {
                        Log.w(TAG, "Ignoring stale HN API failure for storyId=" + id)
                        return
                    }
                    Log.w(TAG, "HN API comments load failed for storyId=" + id)
                    loadingFailed = true
                    loadingFailedServerError = false
                    commentsLoaded = true
                    setCommentsRefreshInProgress(false)
                    notifyHeaderChanged()
                }

                override fun onAllCommentsLoaded(loadedComments: MutableList<Comment>) {
                    if (!isCurrentCommentsLoad(loadGeneration, id)) {
                        Log.w(
                            TAG, ("Ignoring stale loaded comments for storyId=" + id
                                    + ", loadedCount=" + loadedComments.size)
                        )
                        return
                    }
                    Log.d(
                        TAG, ("Loaded comments from fallback path for storyId=" + id
                                + ", loadedCount=" + loadedComments.size)
                    )
                    val revealComments = Runnable {
                        if (!isCurrentCommentsLoad(loadGeneration, id)) {
                            return@Runnable
                        }
                        // Add all comments at once in proper tree order. Compose animates the loading
                        // row removal and item insertion from the immutable list snapshot.
                        allComments!!.addAll(loadedComments)
                        updateDefaultCommentSortOrder(allComments!!)
                        CommentSorter.sort(allComments!!, getCurrentCommentSorting())
                        applyDisplayedComments(getDisplayedCommentsForCurrentFilter(allComments!!))
                        completeCommentsLoad(false)
                        setCommentsRefreshInProgress(false)
                    }

                    revealComments.run()
                }
            })

        fallbackManager!!.loadComments(id, oldCachedResponse)

        maybeLoadPollOptions()

        if (linkPreviewController != null) {
            linkPreviewController!!.loadNetworkPreviews(context)
        }
        return loadGeneration
    }

    private fun onLinkPreviewChanged() {
        notifyHeaderChanged()
    }

    private fun notifyHeaderChanged() {
        syncComposeState()
    }

    private fun setCommentsRefreshInProgress(refreshInProgress: Boolean) {
        if (commentsRefreshInProgress == refreshInProgress) {
            return
        }
        commentsRefreshInProgress = refreshInProgress
        syncComposeState()
    }

    private fun maybeLoadPollOptions() {
        if (!this.isCommentsViewActive || pollOptionsLoadStarted || story == null || story!!.isComment || queue == null) {
            return
        }

        if (story!!.pollOptions != null) {
            loadPollOptions()
            return
        }

        if (pollOptionsLookupStarted || story!!.id <= 0 || TextUtils.isEmpty(story!!.title) || !POLL_TITLE_PATTERN.matcher(
                story!!.title
            ).find()
        ) {
            return
        }

        pollOptionsLookupStarted = true
        val url = "https://hacker-news.firebaseio.com/v0/item/" + story!!.id + ".json"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            com.android.volley.Response.Listener { response: String? ->
                if (!this.isCommentsViewActive) {
                    return@Listener
                }
                val hnStory = Story()
                hnStory.id = story!!.id
                if (JSONParser.updateStoryWithOfficialHNResponse(
                        hnStory,
                        response
                    ) && hnStory.pollOptions != null
                ) {
                    story!!.pollOptions = hnStory.pollOptions
                    maybeLoadPollOptions()
                }
            }, com.android.volley.Response.ErrorListener { error: VolleyError? ->
                if (this.isCommentsViewActive) {
                    pollOptionsLookupStarted = false
                }
            })

        stringRequest.setTag(requestTag)
        queue!!.add<String?>(stringRequest)
    }

    private fun loadPollOptions() {
        if (!this.isCommentsViewActive || story!!.pollOptions == null || queue == null) {
            return
        }

        pollOptionsLoadStarted = true
        story!!.pollOptionArrayList = ArrayList()
        val pollOptionIds = story!!.pollOptions ?: intArrayOf()
        for (optionId in pollOptionIds) {
            val pollOption = PollOption()
            pollOption.loaded = false
            pollOption.loadFailed = false
            pollOption.id = optionId
            story!!.pollOptionArrayList!!.add(pollOption)
        }

        loadNextPollOption(pollOptionIds, 0)
    }

    private fun loadNextPollOption(pollOptionIds: IntArray, index: Int) {
        if (!this.isCommentsViewActive || queue == null || index >= pollOptionIds.size) {
            return
        }

        val optionId = pollOptionIds[index]
        val url = "https://hacker-news.firebaseio.com/v0/item/" + optionId + ".json"
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            com.android.volley.Response.Listener { response: String? ->
                if (!this.isCommentsViewActive) {
                    return@Listener
                }

                val pollOption = story!!.pollOptionArrayList
                    ?.firstOrNull { it.id == optionId }
                if (pollOption != null) {
                    try {
                        val jsonObject = JSONObject(response ?: "")
                        val text = JSONParser.preprocessHtml(jsonObject.getString("text"))
                        if (text.isNullOrBlank()) {
                            throw JSONException("Poll option text is empty")
                        }
                        pollOption.points = jsonObject.getInt("score")
                        pollOption.text = text
                        pollOption.loaded = true
                    } catch (e: JSONException) {
                        pollOption.loadFailed = true
                        Log.w(TAG, "Poll option response was invalid for id=$optionId", e)
                    }
                    notifyHeaderChanged()
                }
                loadNextPollOption(pollOptionIds, index + 1)
            }, com.android.volley.Response.ErrorListener { error: VolleyError? ->
                if (!this.isCommentsViewActive) {
                    return@ErrorListener
                }

                story!!.pollOptionArrayList
                    ?.firstOrNull { it.id == optionId }
                    ?.loadFailed = true
                Log.w(
                    TAG,
                    "Poll option request failed for id=$optionId: ${error?.message}",
                )
                notifyHeaderChanged()
                loadNextPollOption(pollOptionIds, index + 1)
            },
        )
        stringRequest.retryPolicy = DefaultRetryPolicy(
            10000,
            2,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT,
        )
        stringRequest.setTag(requestTag)
        queue!!.add<String?>(stringRequest)
    }

    private fun handleJsonResponse(
        id: Int,
        response: String,
        cache: Boolean,
        forceHeaderRefresh: Boolean,
        restoreScroll: Boolean,
        loadGeneration: Int,
        completion: Runnable?
    ) {
        if (!isCurrentCommentsLoad(loadGeneration, id)) {
            return
        }

        val oldCommentCount = this.allCommentsSource.size
        // This is what we get if the Algolia API has not indexed the post,
        // we should attempt to show the user an option to switch API:s in this
        // server error case
        // Actually, the response being a 404 should be captured by another part
        // so this should never be called. Not that I dare remove it...
        if (response == JSONParser.ALGOLIA_ERROR_STRING) {
            loadingFailed = true
            loadingFailedServerError = true
            notifyHeaderChanged()
        }

        val topLevelCommentIds = story!!.kids?.clone()
        val filteredUsersSnapshot: MutableSet<String>? =
            filteredUsers?.let { HashSet(it) }
        cancelPendingCommentsParse()
        val pendingParse =
            PendingCommentsParse(loadGeneration, id, completion)
        pendingCommentsParse = pendingParse
        pendingParse.future = BackgroundJSONParser.parseAlgoliaCommentsJson(
            response,
            topLevelCommentIds,
            filteredUsersSnapshot,
            object : AlgoliaCommentsParseCallback {
                override fun onParseSuccess(parsedResponse: AlgoliaCommentsResponse) {
                    if (!isCurrentPendingCommentsParse(pendingParse)) {
                        return
                    }
                    pendingCommentsParse = null
                    applyParsedJsonResponse(
                        id,
                        response,
                        cache,
                        forceHeaderRefresh,
                        restoreScroll,
                        loadGeneration,
                        oldCommentCount,
                        parsedResponse
                    )
                    runPendingParseCompletion(pendingParse)
                    runPendingParseFollowUp(pendingParse)
                }

                override fun onParseError(error: IOException) {
                    if (!isCurrentPendingCommentsParse(pendingParse)) {
                        return
                    }
                    pendingCommentsParse = null
                    error.printStackTrace()
                    loadingFailed = true
                    loadingFailedServerError = false
                    notifyHeaderChanged()
                    completeCommentsLoad(false)
                    runPendingParseCompletion(pendingParse)
                    runPendingParseFollowUp(pendingParse)
                }
            })
    }

    private fun applyParsedJsonResponse(
        id: Int,
        response: String?,
        cache: Boolean,
        forceHeaderRefresh: Boolean,
        restoreScroll: Boolean,
        loadGeneration: Int,
        oldCommentCount: Int,
        parsedResponse: AlgoliaCommentsResponse
    ) {
        if (!isCurrentCommentsLoad(loadGeneration, id)) {
            return
        }

        val storyChanged =
            parsedResponse.updateStoryInformation(story!!, forceHeaderRefresh, oldCommentCount)
        val updateHeaderAfterLoad = storyChanged || forceHeaderRefresh
        if (linkPreviewController != null) {
            linkPreviewController!!.loadNetworkPreviews(requireContext())
        }
        maybeLoadPollOptions()

        val wasIntegratedWebview = integratedWebview
        integratedWebview = prefIntegratedWebview && story!!.isLink

        if (integratedWebview && !wasIntegratedWebview) {
            webViewController!!.setIntegratedWebview(true)
            webViewController!!.initialize()
        }

        loadingFailed = false
        loadingFailedServerError = false

        // Seems like loading went well, lets cache the result
        if (cache) {
            Utils.cacheStory(requireContext(), id, response)
        }

        val revealComments = Runnable {
            if (!isCurrentCommentsLoad(loadGeneration, id)) {
                return@Runnable
            }
            applyParsedComments(parsedResponse.comments)

            if (!cache && restoreScroll) {
                // If we're not caching the result, this means we just loaded an old cache.
                // Let's see if we can recover the scroll position.
                if (MainActivity.commentsScrollProgresses != null && !MainActivity.commentsScrollProgresses.isEmpty()) {
                    // We check all of the caches to see if one has the same story ID
                    for (scrollProgress in MainActivity.commentsScrollProgresses) {
                        if (scrollProgress.storyId == story!!.id) {
                            // Jackpot! Let's restore the state
                            restoreScrollProgress(scrollProgress)
                        }
                    }
                }
            }
            completeCommentsLoad(updateHeaderAfterLoad)
        }

        revealComments.run()
    }

    private fun attachCompletionToPendingParse(
        loadGeneration: Int,
        storyId: Int,
        completion: Runnable?
    ): Boolean {
        val pendingParse = pendingCommentsParse
        if (pendingParse == null || pendingParse.loadGeneration != loadGeneration || pendingParse.storyId != storyId) {
            return false
        }
        pendingParse.completion = completion
        return true
    }

    private fun runPendingParseCompletion(pendingParse: PendingCommentsParse) {
        val completion = pendingParse.completion
        pendingParse.completion = null
        if (completion != null) {
            completion.run()
        }
    }

    private fun deferUntilPendingParseFinishes(
        loadGeneration: Int,
        storyId: Int,
        followUp: Runnable
    ): Boolean {
        val pendingParse = pendingCommentsParse
        if (pendingParse == null || pendingParse.loadGeneration != loadGeneration || pendingParse.storyId != storyId) {
            return false
        }
        val previousFollowUp = pendingParse.followUp
        pendingParse.followUp = if (previousFollowUp == null)
            followUp
        else
            Runnable {
                previousFollowUp.run()
                followUp.run()
            }
        return true
    }

    private fun runPendingParseFollowUp(pendingParse: PendingCommentsParse) {
        val followUp = pendingParse.followUp
        pendingParse.followUp = null
        if (followUp != null
            && isCurrentCommentsLoad(pendingParse.loadGeneration, pendingParse.storyId)
        ) {
            followUp.run()
        }
    }

    private fun finishCommentsRefresh(loadGeneration: Int, storyId: Int) {
        if (!isCurrentCommentsLoad(loadGeneration, storyId)) {
            return
        }
        setCommentsRefreshInProgress(false)
    }

    private fun isCurrentPendingCommentsParse(pendingParse: PendingCommentsParse): Boolean {
        return pendingCommentsParse == pendingParse
                && isCurrentCommentsLoad(pendingParse.loadGeneration, pendingParse.storyId)
    }

    private fun cancelPendingCommentsParse() {
        val pendingParse = pendingCommentsParse
        pendingCommentsParse = null
        pendingParse?.future?.cancel(true)
        pendingParse?.future = null
    }

    private fun completeCommentsLoad(updateHeaderAfterLoad: Boolean) {
        if (!this.isCommentsViewActive) {
            return
        }
        val commentsWereLoaded = commentsLoaded
        commentsLoaded = true
        if (!commentsWereLoaded) {
            notifyHeaderChanged()
        }
        if (updateHeaderAfterLoad) {
            refreshHeaderAfterStoryLoad()
        }
        updateNavigationVisibility()
        val commentsView = view ?: return
        commentsView.post {
            if (!isCommentsViewActive) {
                return@post
            }
            scrollToTargetComment()
            restorePendingCommentAction()
        }
    }

    private fun restorePendingCommentAction() {
        if (pendingCommentActionId == -1) return
        val controller = composeController ?: return
        val comment = findCommentById(pendingCommentActionId) ?: return
        pendingCommentActionId = -1
        controller.restoreCommentActions(comment)
        syncOnBackPressedCallbackEnabledState()
    }

    private fun refreshHeaderAfterStoryLoad() {
        if (!this.isCommentsViewActive) {
            return
        }

        if (uncachedStoryHeaderLoading && story!!.loaded) {
            uncachedStoryHeaderLoading = false
        }
        notifyHeaderChanged()
    }

    private val isCommentsViewActive: Boolean
        get() = this.view != null && comments != null && allComments != null

    private fun isCurrentCommentsLoad(loadGeneration: Int, storyId: Int): Boolean {
        return loadGeneration == commentsLoadGeneration &&
            story?.id == storyId &&
            isCommentsViewActive
    }

    private fun applyParsedComments(parsedComments: MutableList<Comment>) {
        val existingCommentsById = HashMap<Int, Comment>()
        val sourceComments = allCommentsSource
        for (i in 1..<sourceComments.size) {
            val comment = sourceComments[i]
            existingCommentsById[comment.id] = comment
        }

        val nextComments = ArrayList<Comment>(parsedComments.size + 1)
        nextComments.add(sourceComments[0])
        for (parsedComment in parsedComments) {
            val existingComment = existingCommentsById[parsedComment.id]
            if (existingComment != null) {
                CommentListDiff.updateExistingComment(existingComment, parsedComment)
                nextComments.add(existingComment)
            } else {
                nextComments.add(parsedComment)
            }
        }

        updateDefaultCommentSortOrder(nextComments)
        CommentSorter.sort(nextComments, getCurrentCommentSorting())

        if (SettingsUtils.shouldCollapseTopLevel(requireContext())) {
            for (comment in nextComments) {
                if (comment.depth == 0) {
                    comment.expanded = false
                }
            }
        }

        val currentAllComments = checkNotNull(allComments)
        currentAllComments.clear()
        currentAllComments.addAll(nextComments)
        applyDisplayedComments(getDisplayedCommentsForCurrentFilter(currentAllComments))
    }

    private val allCommentsSource: MutableList<Comment>
        get() {
            val source = allComments
            return if (source.isNullOrEmpty()) comments ?: mutableListOf() else source
        }

    private fun getCurrentCommentSorting(): String {
        if (TextUtils.isEmpty(currentCommentSorting)) {
            currentCommentSorting = SettingsUtils.getPreferredCommentSorting(requireContext())
        }
        return currentCommentSorting.orEmpty()
    }

    private fun updateDefaultCommentSortOrder(commentsWithHeader: MutableList<Comment>) {
        for (i in 1..<commentsWithHeader.size) {
            commentsWithHeader[i].sortOrder = i
        }
    }

    private fun changeCommentSorting(sortType: String) {
        if (!this.isCommentsViewActive) {
            return
        }

        currentCommentSorting = sortType
        val sourceComments = allCommentsSource
        CommentSorter.sort(sourceComments, sortType)
        applyDisplayedComments(getDisplayedCommentsForCurrentFilter(sourceComments))
    }

    private fun showCommentsByOp() {
        val sourceComments = allCommentsSource
        if (!CommentThreadFilter.hasCommentsByOp(story, sourceComments)) {
            return
        }

        setCommentsByOpFilterActive(true)
        applyDisplayedComments(
            CommentThreadFilter.buildCommentsByOpThreadList(
                story,
                sourceComments
            )
        )
    }

    private fun resetCommentsByOpFilter() {
        if (!commentsByOpFilterActive) {
            return
        }

        setCommentsByOpFilterActive(false)
        applyDisplayedComments(ArrayList(allCommentsSource))
    }

    private fun setCommentsByOpFilterActive(active: Boolean) {
        commentsByOpFilterActive = active
    }

    private fun getDisplayedCommentsForCurrentFilter(
        sourceComments: List<Comment>
    ): MutableList<Comment> {
        if (commentsByOpFilterActive) {
            if (CommentThreadFilter.hasCommentsByOp(story, sourceComments)) {
                return CommentThreadFilter.buildCommentsByOpThreadList(story, sourceComments)
            }
            setCommentsByOpFilterActive(false)
        }
        return ArrayList(sourceComments)
    }

    private fun hasCommentsByOp(): Boolean {
        return CommentThreadFilter.hasCommentsByOp(story, allCommentsSource)
    }

    private fun applyDisplayedComments(
        nextComments: List<Comment>
    ) {
        val displayedComments = checkNotNull(comments)
        displayedComments.clear()
        displayedComments.addAll(nextComments)
        updateNavigationVisibility()
        syncComposeState()
    }

    fun clickBrowser() {
        webViewController!!.openCurrentOrStoryUrlInBrowser()
    }

    private fun toggleStoryBookmark() {
        val ctx = this.context
        val currentStory = story
        if (ctx == null || currentStory == null) return

        val bookmarked = !Utils.isBookmarked(ctx, currentStory.id)
        if (bookmarked) {
            Utils.addBookmark(ctx, currentStory.id)
        } else {
            Utils.removeBookmark(ctx, currentStory.id)
        }
    }

    private fun openArchiveOrg() {
        Toast.makeText(this.context, "Contacting archive.org API...", Toast.LENGTH_SHORT).show()
        ArchiveOrgUrlGetter.getArchiveUrl(
            story!!.url,
            requireContext(),
            object : ArchiveOrgUrlGetter.GetterCallback {
                override fun onSuccess(url: String?) {
                    Utils.launchCustomTab(requireActivity(), url)
                }

                override fun onFailure(reason: String?) {
                    val context = this@CommentsCoordinator.context
                    if (context != null) {
                        Toast.makeText(context, "Error: " + reason, Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    private fun openArchiveIs() {
        Utils.launchCustomTab(requireActivity(), "https://archive.is/newest/" + Uri.encode(story!!.url))
    }

    private fun openArchiveToday() {
        Utils.launchCustomTab(
            requireActivity(),
            "https://archive.today/newest/" + Uri.encode(story!!.url)
        )
    }

    fun clickUser() {
        requireActivity().showUserDialog(
            story!!.by.orEmpty(),
            Runnable { updateUserTags() })
    }

    fun clickComment() {
        if (!AccountUtils.hasAccountDetails(requireContext())) {
            AccountUtils.showLoginPrompt(requireContext())
            return
        }

        val currentStory = story ?: return
        val intent = ComposeEditorContract.createIntent(requireContext())
        intent.putExtra(ComposeEditorContract.EXTRA_ID, currentStory.id)
        intent.putExtra(ComposeEditorContract.EXTRA_PARENT_TEXT, currentStory.title)
        intent.putExtra(ComposeEditorContract.EXTRA_POST_TITLE, currentStory.title)
        intent.putExtra(
            ComposeEditorContract.EXTRA_TYPE,
            ComposeEditorContract.TYPE_TOP_COMMENT
        )
        startActivity(intent)
    }

    fun clickVote() {
        val ctx = this.context
        val currentStory = story
        if (ctx == null || currentStory == null) return

        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(requireContext())
            return
        }

        val storyId = currentStory.id
        val storyIsComment = currentStory.isComment
        val wasUpvoted = Utils.isUpvoted(ctx, storyId, storyIsComment)
        val newUpvoted = !wasUpvoted
        storyVoteLoading = true
        syncComposeState()

        val cb: ActionCallback = object : ActionCallback {
            override fun onSuccess(response: Response) {
                Utils.setUpvoted(ctx, storyId, storyIsComment, newUpvoted)
                storyVoteLoading = false
                syncComposeState()
            }

            override fun onFailure(summary: String?, response: String?) {
                Utils.setUpvoted(ctx, storyId, storyIsComment, wasUpvoted)
                storyVoteLoading = false
                syncComposeState()
            }
        }

        if (newUpvoted) {
            UserActions.upvote(ctx, storyId, cb)
        } else {
            UserActions.unvote(ctx, storyId, cb)
        }
    }

    fun clickFavorite() {
        val ctx = this.context
        val currentStory = story
        if (ctx == null || currentStory == null) return

        val storyId = currentStory.id
        val wasFavorited = Utils.isFavorited(ctx, storyId)
        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(requireContext())
            return
        }

        val newFavorited = !wasFavorited
        storyFavoriteLoading = true
        syncComposeState()
        UserActions.setFavorite(ctx, storyId, newFavorited, object : ActionCallback {
            override fun onSuccess(response: Response) {
                Utils.setFavorite(ctx, storyId, newFavorited)
                storyFavoriteLoading = false
                syncComposeState()
            }

            override fun onFailure(summary: String?, response: String?) {
                Utils.setFavorite(ctx, storyId, wasFavorited)
                storyFavoriteLoading = false
                syncComposeState()
                if (!wasFavorited) {
                    Toast.makeText(ctx, "Couldn't add favorite", Toast.LENGTH_SHORT).show()
                } else {
                    UserActions.showFailureDetailDialog(ctx, summary, response)
                    Toast.makeText(ctx, "Couldn't update favorite", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    @JvmOverloads
    fun navigateToNextComment(topLevelOnly: Boolean = true, scaleLongScrollSpeed: Boolean = false) {
        if (composeController != null) {
            composeController!!.navigateNext(topLevelOnly, scaleLongScrollSpeed)
        }
    }

    @JvmOverloads
    fun navigateToPreviousComment(
        topLevelOnly: Boolean = true,
        scaleLongScrollSpeed: Boolean = false
    ) {
        if (composeController != null) {
            composeController!!.navigatePrevious(topLevelOnly, scaleLongScrollSpeed)
        }
    }

    private fun updateNavigationVisibility() {
        // Compose derives navigation visibility directly from display settings and list content.
    }

    private fun findCommentById(commentId: Int): Comment? {
        if (comments != null) {
            for (comment in comments) {
                if (comment.id == commentId) {
                    return comment
                }
            }
        }
        if (allComments != null) {
            for (comment in allComments) {
                if (comment.id == commentId) {
                    return comment
                }
            }
        }
        return null
    }

    private fun updateUserTags() {
        composeController?.refreshContent()
    }

    fun onRequest(onUpdate: Runnable, onDone: Runnable) {
        if (story == null || TextUtils.isEmpty(story!!.url)) {
            onDone.run()
            return
        }
        if (!Utils.isAiSummaryEnabled(requireContext())) {
            onDone.run()
            return
        }

        val context = requireContext()
        val mode: String = PreferenceManager.getDefaultSharedPreferences(context)
            .getString("pref_ai_summary_mode", "cloud")!!

        if (webViewController != null) {
            webViewController!!.getLoadedPageText(
                PageTextCallback { text: String? ->
                    summarizeStory(
                        context,
                        mode,
                        text,
                        onUpdate,
                        onDone
                    )
                })
            return
        }

        summarizeStory(context, mode, null, onUpdate, onDone)
    }

    private fun summarizeStory(
        context: Context?,
        mode: String?,
        articleText: String?,
        onUpdate: Runnable,
        onDone: Runnable
    ) {
        val hasArticleText = !TextUtils.isEmpty(articleText)
        if ("local" == mode) {
            val callback: SummaryCallback = object : SummaryCallback {
                override fun onDebugInfo(debugInfo: String?) {
                    story!!.summaryDebugInfo = debugInfo
                    onUpdate.run()
                }

                override fun onProgress(summary: String?) {
                    story!!.summary = summary
                    onUpdate.run()
                }

                override fun onSuccess(summary: String?) {
                    story!!.summary = summary
                    story!!.summaryGeneratedSuccessfully = true
                    onDone.run()
                }

                override fun onFailure(error: String?) {
                    story!!.summary = "Failed to generate local summary: " + error
                    story!!.summaryGeneratedSuccessfully = false
                    onDone.run()
                }
            }
            if (hasArticleText) {
                SummaryManager.summarizeTextWithGeminiNano(context, articleText, callback)
            } else {
                SummaryManager.summarizeArticleWithGeminiNano(context, story!!.url, callback)
            }
        } else {
            val callback: SummaryCallback = object : SummaryCallback {
                override fun onDebugInfo(debugInfo: String?) {
                    story!!.summaryDebugInfo = debugInfo
                    onUpdate.run()
                }

                override fun onProgress(summary: String?) {
                    story!!.summary = summary
                    onUpdate.run()
                }

                override fun onSuccess(summary: String?) {
                    story!!.summary = summary
                    story!!.summaryGeneratedSuccessfully = true
                    onDone.run()
                }

                override fun onFailure(error: String?) {
                    story!!.summary = "Failed to generate summary: " + error
                    story!!.summaryGeneratedSuccessfully = false
                    onDone.run()
                }
            }
            if (hasArticleText) {
                SummaryManager.summarizeText(requireContext(), queue, articleText, callback)
            } else {
                SummaryManager.summarizeArticle(requireContext(), queue, story!!.url.orEmpty(), callback)
            }
        }
    }


    interface CommentsPaneCallback {
        fun onSwitchView(isAtWebView: Boolean)
    }

    companion object {
        private const val TAG = "CommentsCoordinator"
        private const val STATE_COMMENT_ACTION_COMMENT_ID =
            "com.simon.harmonichackernews.STATE_COMMENT_ACTION_COMMENT_ID"
        private const val STATE_ADBLOCK_DISABLED_FOR_SESSION =
            "com.simon.harmonichackernews.STATE_ADBLOCK_DISABLED_FOR_SESSION"
        private const val STATE_COMMENT_SORTING =
            "com.simon.harmonichackernews.STATE_COMMENT_SORTING"
        private const val STATE_REFERENCE_LINK_SUMMARY_URL =
            "com.simon.harmonichackernews.STATE_REFERENCE_LINK_SUMMARY_URL"
        private const val STATE_REFERENCE_LINK_SUMMARY_TITLE =
            "com.simon.harmonichackernews.STATE_REFERENCE_LINK_SUMMARY_TITLE"
        private const val STATE_PREVIEW_IMAGE_DIALOG_URL =
            "com.simon.harmonichackernews.STATE_PREVIEW_IMAGE_DIALOG_URL"
        private val POLL_TITLE_PATTERN: Pattern =
            Pattern.compile("\\bpoll\\b", Pattern.CASE_INSENSITIVE)

        // Keep WebView startup clear of the comments entrance transition. WebView process and
        // renderer initialization can otherwise land on the same frames as the shared transition
        // on physical devices, which makes opening a story feel much heavier than it is.
        private const val WEBVIEW_BACKGROUND_INITIALIZATION_DELAY_MS = 900L
    }
}
