package com.simon.harmonichackernews

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import androidx.webkit.WebViewFeature
import com.simon.harmonichackernews.network.RequestQueue
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.simon.harmonichackernews.CommentsWebViewController.PageTextCallback
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.toBundle
import com.simon.harmonichackernews.linkpreview.LinkPreviewController
import com.simon.harmonichackernews.network.CommentThreadRepository
import com.simon.harmonichackernews.network.CloudSummaryEvent
import com.simon.harmonichackernews.network.HackerNewsActionResult
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.LocalSummaryCallback
import com.simon.harmonichackernews.network.LocalSummaryManager
import com.simon.harmonichackernews.network.failureDetails
import com.simon.harmonichackernews.network.showLoginPromptIfCredentialsMissing
import com.simon.harmonichackernews.settings.AndroidAiSummarySettings
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.WebViewPreferences
import com.simon.harmonichackernews.network.ArchiveOrgUrlGetter
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toStory
import com.simon.harmonichackernews.platform.AndroidPlatformServices
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.PlatformServices
import com.simon.harmonichackernews.presentation.CommentsAction
import com.simon.harmonichackernews.presentation.CommentActionContext
import com.simon.harmonichackernews.presentation.CommentMenuAction
import com.simon.harmonichackernews.presentation.CommentsEffect
import com.simon.harmonichackernews.presentation.CommentsHeaderAction
import com.simon.harmonichackernews.presentation.CommentsHeaderContext
import com.simon.harmonichackernews.presentation.CommentsMoreAction
import com.simon.harmonichackernews.presentation.CommentsPresenter
import com.simon.harmonichackernews.presentation.CommentsSavedItemRequest
import com.simon.harmonichackernews.presentation.CommentsShareAction
import com.simon.harmonichackernews.presentation.CommentsSheetAction
import com.simon.harmonichackernews.presentation.CommentsPlatformEffect
import com.simon.harmonichackernews.presentation.CommentsUiOrchestrator
import com.simon.harmonichackernews.presentation.FeatureDecision
import com.simon.harmonichackernews.presentation.CommentThreadStore
import com.simon.harmonichackernews.presentation.SavedItemActionOutcome
import com.simon.harmonichackernews.presentation.SavedItemActionUseCase
import com.simon.harmonichackernews.presentation.StoryLoadFailure
import com.simon.harmonichackernews.presentation.VoteDirection
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.comments.CommentsScreenState
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.StoryUpdate
import com.simon.harmonichackernews.utils.StatusBarProtectionUtils
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.Utils
import com.simon.harmonichackernews.utils.ViewUtils
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CommentsCoordinator(
    private val activity: MainActivity,
    private val destination: StoryDestination,
    sessionKey: Int,
    savedInstanceState: Bundle?,
    private val platformServices: PlatformServices = AndroidPlatformServices.create(activity),
    private val clock: Clock = Clock.System,
) {
    private val userSettings = AndroidUserSettings(activity)
    private val externalLinks = platformServices.externalLinks
    private val contentFilters = ContentFilterRepository(AndroidKeyValueStore.defaults(activity))
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hackerNewsUserService = HackerNewsUserService(
        NetworkComponent.hackerNewsSession,
        platformServices.credentials,
    )
    private val savedItemActions = SavedItemActionUseCase(
        repository = SavedItemsRepository(AndroidKeyValueStore.global(activity)),
        nowMillis = { clock.now().toEpochMilliseconds() },
        voteRequest = { id, direction ->
            hackerNewsUserService.vote(id.toString(), direction)
        },
        favoriteRequest = hackerNewsUserService::setFavorite,
    )
    private val screenStateViewModel = ViewModelProvider(activity)[ScreenStateViewModel::class.java]
    private val sessionState = screenStateViewModel.commentsStateFor(sessionKey, destination.storyId)
    private val restoringSession = sessionState.initialized
    private val scrollProgress = sessionState.scrollProgress
    private var restoringStoredProgress = scrollProgress.initialized
    private var callback: CommentsPaneCallback?
    private var started = false
    private var destroyed = false
    private val commentsPresenter = CommentsPresenter(
        coroutineScope,
        sessionState,
        CommentThreadRepository(
            NetworkComponent.algoliaRepository,
            NetworkComponent.hackerNewsRepository,
        ),
        NetworkComponent.pollOptionsRepository,
        savedItemActions,
    )
    private val commentThread: CommentThreadStore = commentsPresenter.thread
    private val comments: MutableList<Comment> get() = commentThread.displayedComments
    private val allComments: MutableList<Comment> get() = commentThread.allComments
    private var queue: RequestQueue? = null
    private val requestTag = Any()
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
    private var showWebsite by sessionState::showWebsite
    private var integratedWebview = true
    private var prefIntegratedWebview = true
    private var preloadWebview: String? = "never"
    private var preloadWebviewMinimumBattery = WebViewPreferences.DEFAULT_MINIMUM_BATTERY
    private var matchWebviewTheme = true
    private var readerModeEnabled = true
    private var readerModeDefault = false
    private var adBlockDisabledForSession = false
    private var closeWebViewOnBack = false
    private var topInset = 0
    private val lastLoaded: Long
        get() = commentsPresenter.state.value.lastLoadedMillis
    private var commentsLoaded: Boolean
        get() = commentsPresenter.state.value.loaded
        set(value) = commentsPresenter.dispatch(CommentsAction.SetLoaded(value))
    private var commentsRefreshing: Boolean
        get() = commentsPresenter.state.value.refreshing
        set(value) = commentsPresenter.dispatch(CommentsAction.SetRefreshing(value))
    private var loadingFailed: Boolean
        get() = commentsPresenter.state.value.failure != null
        set(value) {
            val current = commentsPresenter.state.value.failure
            commentsPresenter.dispatch(
                CommentsAction.SetFailure(
                    if (value) current ?: StoryLoadFailure.GENERAL else null,
                ),
            )
        }
    private var loadingFailedServerError: Boolean
        get() = commentsPresenter.state.value.failure == StoryLoadFailure.NOT_FOUND
        set(value) {
            val current = commentsPresenter.state.value.failure
            commentsPresenter.dispatch(
                CommentsAction.SetFailure(
                    when {
                        value -> StoryLoadFailure.NOT_FOUND
                        current == StoryLoadFailure.NOT_FOUND -> StoryLoadFailure.GENERAL
                        else -> current
                    },
                ),
            )
        }
    private var showUpdate: Boolean
        get() = commentsPresenter.state.value.showUpdate
        set(value) = commentsPresenter.dispatch(CommentsAction.SetShowUpdate(value))
    private var storyVoteLoading: Boolean
        get() = commentsPresenter.state.value.storyVoteLoading
        set(value) = commentsPresenter.dispatch(CommentsAction.SetStoryVoteLoading(value))
    private var storyFavoriteLoading: Boolean
        get() = commentsPresenter.state.value.storyFavoriteLoading
        set(value) = commentsPresenter.dispatch(CommentsAction.SetStoryFavoriteLoading(value))
    private var hasAccountDetails = false
    private var displaySettings: CommentDisplaySettings? = null
    private var backPressedCallback: OnBackPressedCallback? = null
    private var username: String? = null
    private var story by sessionState::story
    private var filteredUsers: MutableSet<String>? = null
    private var scrollToCommentId by sessionState::scrollToCommentId
    private val byOpFilterActive: Boolean
        get() = commentThread.state.value.commentsByOp
    private var originalStatusBarColor = Color.TRANSPARENT
    private var originalStatusBarColorCaptured = false
    private var commentsPaneStatusBarColor = Color.TRANSPARENT
    private var composeHeaderStatusBarCoverage = 0f
    private var commentsHeaderStatusBarColor = Color.TRANSPARENT
    private var appliedStatusBarProtectionKnown = false
    private var appliedStatusBarProtectionEnabled = false
    private var appliedStatusBarProtectionColor = Color.TRANSPARENT
    private var commentSorting: String?
        get() = commentThread.state.value.sorting
        set(value) {
            commentsPresenter.dispatch(CommentsAction.SetSorting(value.orEmpty()))
        }
    private var composeController: CommentsComposeController? = null

    init {
        coroutineScope.launch {
            commentsPresenter.effects.collect(::handleCommentsEffect)
        }
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
        filteredUsers = contentFilters.load().users.toMutableSet()

        if (restoringSession) {
            return
        }

        story = destination.toStory()
        showWebsite = destination.showWebsite
        scrollToCommentId = destination.scrollToCommentId
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
            if (!restoringSession) {
                commentSorting = savedInstanceState.getString(STATE_COMMENT_SORTING)
            }
        }

        if (!restoringSession && (savedInstanceState == null || TextUtils.isEmpty(commentSorting))) {
            commentSorting = userSettings.comments.sorting
        }

        originalStatusBarColor = requireActivity().getWindow().getStatusBarColor()
        originalStatusBarColorCaptured = true

        val readingPreferences = userSettings.reading
        prefIntegratedWebview = readingPreferences.integratedWebView
        loadInitialStorySummaryFromCache()
        uncachedStoryHeaderLoading = story!!.id > 0 && !story!!.loaded

        commentsPaneStatusBarColor =
            StatusBarProtectionUtils.getPaneBackgroundColor(requireContext())
        commentsHeaderStatusBarColor = commentsPaneStatusBarColor
        appliedStatusBarProtectionKnown = false
        updateCommentsStatusBarAppearance()

        integratedWebview = prefIntegratedWebview && story!!.isLink
        preloadWebview = readingPreferences.preloadWebViewMode
        preloadWebviewMinimumBattery = readingPreferences.preloadWebViewMinimumBattery
        matchWebviewTheme = readingPreferences.matchWebViewTheme
        readerModeEnabled = readingPreferences.readerModeEnabled
        readerModeDefault = readingPreferences.readerModeDefault
        val blockAds = readingPreferences.blockAds && !adBlockDisabledForSession
        closeWebViewOnBack = readingPreferences.closeWebViewOnBack

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

        if (!restoringSession) {
            val headerComment = Comment()
            commentsPresenter.dispatch(
                CommentsAction.ResetThread(story, headerComment, getCurrentCommentSorting()),
            )
        }
        sessionState.initialized = true

        username = AccountUtils.getAccountUsername(requireContext())
        hasAccountDetails = AccountUtils.hasAccountDetails(requireContext())
        displaySettings = createCommentDisplaySettings()

        initializeComposeUi()

        val restoreScrollFromCache = !showWebsite

        // Navigation Compose owns the screen transition. Do not hold the first frame
        // behind the old inset-gated postponed transition; render the header skeleton immediately
        // and start loading on the next main-loop turn.
        if (!commentsLoaded) {
            view.post(object : Runnable {
                override fun run() {
                    if (this@CommentsCoordinator.view !== view || !this@CommentsCoordinator.isAdded) {
                        return
                    }
                    loadInitialStoryAndComments(restoreScrollFromCache)
                }
            })
        }
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
            { userSettings.comments.smoothScroll },
            story!!,
            showWebsite,
            username,
            commentsPresenter.savedItemState,
            object : CommentsComposeController.Listener {
                override fun onToggleComment(comment: Comment, position: Int) {
                    toggleCommentExpanded(comment, position)
                }

                override fun onScrollPositionChanged(commentId: Int, offset: Int) {
                    if (restoringStoredProgress) return
                    scrollProgress.initialized = true
                    scrollProgress.storyId = story?.id ?: destination.storyId
                    scrollProgress.topCommentId = commentId
                    scrollProgress.topCommentOffset = -offset
                }

                override fun onCommentAction(comment: Comment, action: CommentMenuAction) {
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
                        externalLinks.open(ExternalLinkRequest(story!!.url.orEmpty()))
                    }
                }

                override fun onHeaderPreviewLoaded() {
                    story?.id?.takeIf { it > 0 }?.let(activity::onStoryPreviewImageLoaded)
                }

                override fun onHeaderPreviewLoadFailed() {
                    story?.id?.takeIf { it > 0 }?.let(activity::onStoryPreviewImageLoadFailed)
                }

                override fun onHeaderAction(action: CommentsHeaderAction) =
                    executeCommentsDecision(
                        CommentsUiOrchestrator.header(
                            action,
                            CommentsHeaderContext(story!!, hasAccountDetails),
                        ),
                    )

                override fun onShareAction(action: CommentsShareAction) {
                    handleCommentsPlatformEffect(CommentsUiOrchestrator.share(action, story!!))
                }

                override fun onMoreAction(action: CommentsMoreAction) {
                    executeCommentsDecision(
                        CommentsUiOrchestrator.more(action, story!!, byOpFilterActive),
                    )
                }

                override fun onSearchResultSelected(comment: Comment) {
                    selectComposeSearchResult(comment)
                }

                override fun onSearchQueryChanged(query: String) {
                    commentsPresenter.dispatch(CommentsAction.SetSearchQuery(query))
                    syncComposeState()
                }

                override fun onSortComments(sortType: String) {
                    changeCommentSorting(sortType)
                }

                override fun onSheetAction(action: CommentsSheetAction) {
                    handleCommentsPlatformEffect(CommentsUiOrchestrator.sheet(action))
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
                    performVote(
                        requireContext(),
                        optionId,
                        "up",
                        successMessage = "Poll vote successful",
                    )
                }
            })
        requireActivity().attachCommentsComposeController(composeController!!)
        restoreLinkSummaryAfterRecreation()
        syncComposeState()
        if (restoringSession && restoringStoredProgress) {
            restoreScrollProgress()
        }
        syncOnBackPressedCallbackEnabledState()
    }

    private fun syncComposeState() {
        val controller = composeController
        if (controller == null || story == null) {
            return
        }
        if (!restoringStoredProgress) {
            scrollProgress.initialized = true
            scrollProgress.storyId = story!!.id
            scrollProgress.collapsedIDs.clear()
            comments.asSequence()
                .filter { !it.expanded }
                .mapTo(scrollProgress.collapsedIDs) { it.id }
        }
        val readerAvailable =
            webViewController != null && webViewController!!.isReaderModeAvailable
        val readerEnabled = webViewController != null && webViewController!!.isReaderModeEnabled()
        if (displaySettings == null) {
            displaySettings = createCommentDisplaySettings()
        }
        controller.updateContent(
            CommentsScreenState(
                story = story!!,
                comments = comments,
                displaySettings = displaySettings!!,
                commentsLoaded = commentsLoaded,
                commentsRefreshInProgress = commentsRefreshing,
                loadingFailed = loadingFailed,
                loadingFailedServerError = loadingFailedServerError,
                showUpdate = showUpdate,
                lastRefreshed = lastLoaded,
                commentsByOpFilterActive = byOpFilterActive,
                hasCommentsByOp = hasCommentsByOp(),
                adBlockActive = webViewController != null && webViewController!!.isBlockingAds,
                integratedWebView = integratedWebview,
                readerModeAvailable = readerAvailable,
                readerModeEnabled = readerEnabled,
                currentSorting = getCurrentCommentSorting()!!,
                topInsetPx = topInset,
                contentInsetLeftPx = commentsContentInsetLeft,
                contentInsetRightPx = commentsContentInsetRight,
                storyVoteLoading = storyVoteLoading,
                storyFavoriteLoading = storyFavoriteLoading,
                searchQuery = commentThread.state.value.searchQuery,
                searchResults = commentThread.state.value.searchResults,
                visibleComments = commentThread.state.value.visibleComments,
            ),
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

    private fun executeCommentsDecision(
        decision: FeatureDecision<CommentsAction, CommentsPlatformEffect>,
    ) {
        decision.actions.forEach(commentsPresenter::dispatch)
        if (decision.refreshNavigation) updateNavigationVisibility()
        if (decision.refreshState) syncComposeState()
        decision.effects.forEach(::handleCommentsPlatformEffect)
    }

    private fun handleCommentsPlatformEffect(effect: CommentsPlatformEffect) {
        when (effect) {
            is CommentsPlatformEffect.OpenUser -> requireActivity().showUserDialog(
                effect.userName,
                Runnable { updateUserTags() },
            )
            is CommentsPlatformEffect.OpenEditor -> startActivity(
                ComposeEditorContract.createIntent(requireContext())
                    .putExtras(effect.destination.toBundle()),
            )
            CommentsPlatformEffect.RequestLogin ->
                AccountUtils.showLoginPrompt(requireContext())
            is CommentsPlatformEffect.ShowMessage -> Toast.makeText(
                requireContext(),
                effect.message,
                Toast.LENGTH_SHORT,
            ).show()
            is CommentsPlatformEffect.ShareText ->
                platformServices.sharing.share(effect.text)
            is CommentsPlatformEffect.CopyText -> {
                platformServices.clipboard.copy(effect.label, effect.text)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(
                        requireContext(),
                        "Text copied to clipboard",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            CommentsPlatformEffect.Refresh -> onRetry()
            CommentsPlatformEffect.Summarize -> requestComposeSummary()
            is CommentsPlatformEffect.OpenStory ->
                Utils.openCommentsActivity(effect.itemId, -1, requireContext())
            CommentsPlatformEffect.ShowSearch -> showComposeCommentSearch()
            CommentsPlatformEffect.OpenBrowser -> onOpenInBrowser()
            CommentsPlatformEffect.DisableAdBlock -> {
                adBlockDisabledForSession = true
                webViewController?.disableAdBlockAndReload()
            }
            is CommentsPlatformEffect.OpenArchive -> when (effect.provider) {
                com.simon.harmonichackernews.presentation.ArchiveProvider.ORG -> openArchiveOrg()
                com.simon.harmonichackernews.presentation.ArchiveProvider.IS -> openArchiveIs()
                com.simon.harmonichackernews.presentation.ArchiveProvider.TODAY -> openArchiveToday()
            }
            CommentsPlatformEffect.ReloadWebsite -> {
                val controller = webViewController ?: return
                if (controller.isShowingOfflineOrCachedPage && controller.hasLastFailedUrl()) {
                    controller.retryLastFailedUrl()
                } else {
                    controller.reload()
                }
            }
            CommentsPlatformEffect.ExpandSheet -> composeController?.requestExpandSheet()
            CommentsPlatformEffect.OpenWebsiteInBrowser -> webViewController?.let { clickBrowser() }
            CommentsPlatformEffect.ToggleReaderMode -> webViewController?.toggleReaderMode()
            CommentsPlatformEffect.ToggleDarkMode -> webViewController?.toggleDarkMode()
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

    private fun handleComposeCommentAction(comment: Comment, action: CommentMenuAction) {
        if (!this.isAdded || composeController == null) {
            return
        }
        executeCommentsDecision(
            CommentsUiOrchestrator.comment(
                action,
                CommentActionContext(
                    comment = comment,
                    storyTitle = story?.title,
                    hasAccount = hasAccountDetails,
                    voteLoading = composeController!!.isCommentActionVoteLoading(comment.id),
                    upvoted = commentsPresenter.savedItemState.isUpvoted(comment.id, true),
                    downvoted = composeController!!.isCommentActionDownvoted(comment.id),
                    nowMillis = clock.now().toEpochMilliseconds(),
                ),
            ),
        )
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
                userSettings.general.transparentStatusBar
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
        commentsPresenter.dispatch(CommentsAction.ToggleExpanded(comment.id))
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
            userSettings.comments,
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

        val updateWasShowing = showUpdate
        commentsPresenter.dispatch(
            CommentsAction.EvaluateUpdateAvailability(
                nowMillis = clock.now().toEpochMilliseconds(),
                alwaysShow = userSettings.story.alwaysShowTapToRefresh,
                storyTimeEpochSeconds = story!!.time,
            ),
        )
        if (!updateWasShowing && showUpdate && composeController != null) {
            composeController!!.clearSearchScrollTopTarget()
        }
        syncCommentsStatusBarProtection()
        syncComposeState()
    }

    fun onStop() {
        if (!started) return
        started = false
    }

    fun onSaveInstanceState(outState: Bundle) {
        if (composeController != null && composeController!!.isLinkPreviewReferenceShowing()) {
            outState.putString(
                STATE_REFERENCE_LINK_SUMMARY_URL,
                composeController!!.linkPreviewVisibleUrl
            )
            outState.putString(
                STATE_REFERENCE_LINK_SUMMARY_TITLE,
                composeController!!.getLinkPreviewFallbackTitle()
            )
        } else if (composeController != null && composeController!!.isLinkPreviewImageShowing()) {
            outState.putString(
                STATE_PREVIEW_IMAGE_DIALOG_URL,
                composeController!!.linkPreviewVisibleUrl
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

    private fun restoreScrollProgress() {
        val collapsedIds = scrollProgress.collapsedIDs.toSet()
        val topCommentId = scrollProgress.topCommentId
        val topCommentOffset = scrollProgress.topCommentOffset
        commentsPresenter.dispatch(CommentsAction.RestoreCollapsedComments(collapsedIds))
        restoringStoredProgress = false
        if (composeController != null) {
            syncComposeState()
            composeController!!.scrollToComment(
                topCommentId,
                topCommentOffset,
                false
            )
        }
    }

    private fun scrollToTargetComment() {
        if (scrollToCommentId == -1) return
        for (i in comments.indices) {
            if (comments[i].id == scrollToCommentId) {
                expandParentsForComment(comments[i])
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
        val previousRevision = commentThread.state.value.revision
        commentsPresenter.dispatch(CommentsAction.ExpandParents(comment.id))
        if (commentThread.state.value.revision != previousRevision) {
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
            composeController!!.linkPreviewVisibleUrl
        else
            null
        pendingReferenceLinkSummaryTitle = if (preserveReferenceSummary)
            composeController!!.getLinkPreviewFallbackTitle()
        else
            null
        pendingPreviewImageDialogUrl = if (preservePreviewImage)
            composeController!!.linkPreviewVisibleUrl
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
        commentsPresenter.dispatch(CommentsAction.CancelThreadLoad)
        commentsPresenter.dispatch(CommentsAction.CancelPollOptionsLoad)
        commentsRefreshing = false
        storyVoteLoading = false
        storyFavoriteLoading = false
        coroutineScope.cancel()
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
        Log.d(TAG, "Retry requested for storyId=" + story!!.id)
        setCommentsRefreshInProgress(true)
        loadStoryAndComments(story!!.id, null)
    }

    fun onOpenInBrowser() {
        externalLinks.open(
            ExternalLinkRequest(
                url = "https://news.ycombinator.com/item?id=" + story!!.id,
                preferInApp = false,
            ),
        )
    }

    private fun loadInitialStoryAndComments(restoreScrollFromCache: Boolean) {
        val context = this.context
        if (context == null || !this.isCommentsViewActive || story == null) {
            return
        }

        queue = NetworkComponent.getRequestQueueInstance(context)
        val cachedResponse = Utils.loadCachedStory(context, story!!.id)

        loadStoryAndComments(story!!.id, cachedResponse, restoreScrollFromCache)
    }

    private fun loadStoryAndComments(
        id: Int,
        oldCachedResponse: String?,
        restoreScrollFromCache: Boolean = false,
    ) {
        val context = this.context
        if (context == null || queue == null || !this.isCommentsViewActive) {
            Log.w(
                TAG, ("Skipping comments load for storyId=" + id
                        + ": contextPresent=" + (context != null)
                        + ", queuePresent=" + (queue != null)
                        + ", commentsViewActive=" + this.isCommentsViewActive)
            )
            return
        }

        Log.d(
            TAG,
            "Loading comments for storyId=" + id + ", hasCachedResponse=" + (oldCachedResponse != null)
        )
        val updateWasShowing = showUpdate
        commentsPresenter.dispatch(
            CommentsAction.BeginThreadLoad(clock.now().toEpochMilliseconds()),
        )
        if (updateWasShowing) {
            notifyHeaderChanged()
        }

        commentsPresenter.dispatch(
            CommentsAction.LoadThread(
                story = story!!,
                useAlgolia = userSettings.reading.useAlgoliaApi,
                filteredUsers = filteredUsers ?: emptySet(),
                sorting = getCurrentCommentSorting(),
                collapseTopLevel = userSettings.comments.collapseTopLevel,
                previousResponse = oldCachedResponse,
                restoreScrollFromCache = restoreScrollFromCache,
            ),
        )

        commentsPresenter.dispatch(CommentsAction.LoadPollOptions(story!!))

        if (linkPreviewController != null) {
            linkPreviewController!!.loadNetworkPreviews(context)
        }
    }

    private fun handleCommentsEffect(effect: CommentsEffect) {
        when (effect) {
            is CommentsEffect.ShowCommentActions ->
                composeController?.showCommentActions(effect.comment)
            is CommentsEffect.ThreadApplied -> {
                if (!isCurrentCommentsLoad(effect.requestId, effect.storyId)) {
                    Log.w(TAG, "Ignoring stale comments result for storyId=${effect.storyId}")
                    return
                }
                if (effect.usedOfficialFallback) {
                    context?.let {
                        Toast.makeText(
                            it,
                            "Algolia API failed, using official HN API",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                effect.responseToCache?.let { response ->
                    Utils.cacheStory(requireContext(), effect.storyId, response)
                }
                if (effect.broadcastStoryUpdate) StoryUpdate.updateStory(story!!)
                if (effect.contentApplied) {
                    linkPreviewController?.loadNetworkPreviews(context)
                    commentsPresenter.dispatch(CommentsAction.LoadPollOptions(story!!))
                    val wasIntegratedWebview = integratedWebview
                    integratedWebview = prefIntegratedWebview && story!!.isLink
                    if (integratedWebview && !wasIntegratedWebview) {
                        webViewController!!.setIntegratedWebview(true)
                        webViewController!!.initialize()
                    }
                    updateNavigationVisibility()
                    syncComposeState()
                    if (effect.restoreScroll && restoringStoredProgress &&
                        scrollProgress.storyId == story!!.id
                    ) {
                        restoreScrollProgress()
                    }
                    completeCommentsLoad(effect.headerChanged)
                }
                if (effect.networkCompleted) setCommentsRefreshInProgress(false)
            }
            is CommentsEffect.ThreadFailed -> {
                val result = effect.result
                Log.w(
                    TAG,
                    "${result.source} comments load failed for storyId=${effect.storyId}, " +
                        "noInternet=${result.noInternet}",
                    result.cause,
                )
                notifyHeaderChanged()
            }
            is CommentsEffect.PollOptionsChanged -> {
                effect.failedOptionId?.let {
                    Log.w(TAG, "Poll option request failed for id=$it")
                }
                if (story?.id == effect.storyId) notifyHeaderChanged()
            }
            is CommentsEffect.PollOptionsLookupFailed -> {
                if (story?.id == effect.storyId) {
                    Log.w(TAG, "Poll lookup failed for id=${effect.storyId}", effect.cause)
                }
            }
            is CommentsEffect.SavedItemActionStarted -> {
                when (val request = effect.request) {
                    is CommentsSavedItemRequest.CommentFavorite ->
                        composeController?.setCommentActionFavoriteLoading(request.itemId, true)
                    is CommentsSavedItemRequest.CommentVote ->
                        composeController?.setCommentActionVoteLoading(
                            request.itemId,
                            request.direction.toCommentAction(),
                        )
                    is CommentsSavedItemRequest.StoryFavorite,
                    is CommentsSavedItemRequest.StoryVote -> syncComposeState()
                }
            }
            is CommentsEffect.SavedItemActionCompleted ->
                completeSavedItemAction(effect)
        }
    }

    private fun completeSavedItemAction(effect: CommentsEffect.SavedItemActionCompleted) {
        val failure = effect.outcome as? SavedItemActionOutcome.Failure
        when (val request = effect.request) {
            is CommentsSavedItemRequest.CommentFavorite -> {
                composeController?.setCommentActionFavoriteLoading(request.itemId, false)
                failure?.let { showFavoriteFailure(it, adding = !it.action.previousPresent) }
            }
            is CommentsSavedItemRequest.CommentVote -> {
                composeController?.finishCommentActionVote(
                    request.itemId,
                    if (failure == null) request.direction == "down" else request.previousDownvoted,
                )
                failure?.let { showVoteFailure(requireContext(), it.result) }
            }
            is CommentsSavedItemRequest.StoryFavorite -> {
                syncComposeState()
                failure?.let { showFavoriteFailure(it, adding = !it.action.previousPresent) }
            }
            is CommentsSavedItemRequest.StoryVote -> {
                syncComposeState()
                failure?.let { showVoteFailure(requireContext(), it.result) }
            }
        }
    }

    private fun showFavoriteFailure(
        failure: SavedItemActionOutcome.Failure,
        adding: Boolean,
    ) {
        val context = context ?: return
        failure.result.showLoginPromptIfCredentialsMissing(context)
        val (summary, response) = failure.result.failureDetails()
        if (!adding) requireActivity().showFailureDetailDialog(summary, response)
        Toast.makeText(
            context,
            if (adding) "Couldn't add favorite" else "Couldn't update favorite",
            Toast.LENGTH_SHORT,
        ).show()
    }

    private fun String.toCommentAction(): CommentMenuAction = when (this) {
        VoteDirection.UP.wireValue -> CommentMenuAction.UPVOTE
        VoteDirection.DOWN.wireValue -> CommentMenuAction.DOWNVOTE
        else -> CommentMenuAction.UNVOTE
    }

    private fun onLinkPreviewChanged() {
        notifyHeaderChanged()
    }

    private fun notifyHeaderChanged() {
        syncComposeState()
    }

    private fun setCommentsRefreshInProgress(refreshInProgress: Boolean) {
        if (commentsRefreshing == refreshInProgress) {
            return
        }
        commentsRefreshing = refreshInProgress
        syncComposeState()
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
        get() = this.view != null

    private fun isCurrentCommentsLoad(loadGeneration: Int, storyId: Int): Boolean {
        return commentsPresenter.isCurrentThreadLoad(loadGeneration, storyId) &&
            story?.id == storyId &&
            isCommentsViewActive
    }

    private fun getCurrentCommentSorting(): String {
        if (TextUtils.isEmpty(commentSorting)) {
            commentSorting = userSettings.comments.sorting
        }
        return commentSorting.orEmpty()
    }

    private fun changeCommentSorting(sortType: String) {
        if (!this.isCommentsViewActive) {
            return
        }

        commentsPresenter.dispatch(CommentsAction.SetSorting(sortType))
        updateNavigationVisibility()
        syncComposeState()
    }

    private fun resetCommentsByOpFilter() {
        commentsPresenter.dispatch(CommentsAction.ResetCommentsByOp)
        updateNavigationVisibility()
        syncComposeState()
    }

    private fun hasCommentsByOp(): Boolean {
        return commentThread.state.value.hasCommentsByOp
    }

    fun clickBrowser() {
        webViewController!!.openCurrentOrStoryUrlInBrowser()
    }

    private fun openArchiveOrg() {
        Toast.makeText(this.context, "Contacting archive.org API...", Toast.LENGTH_SHORT).show()
        ArchiveOrgUrlGetter.getArchiveUrl(
            story!!.url,
            requireContext(),
            object : ArchiveOrgUrlGetter.GetterCallback {
                override fun onSuccess(url: String?) {
                    url?.let { externalLinks.open(ExternalLinkRequest(it)) }
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
        externalLinks.open(
            ExternalLinkRequest("https://archive.is/newest/" + Uri.encode(story!!.url)),
        )
    }

    private fun openArchiveToday() {
        externalLinks.open(
            ExternalLinkRequest("https://archive.today/newest/" + Uri.encode(story!!.url)),
        )
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
        return commentThread.findComment(commentId)
    }

    private fun updateUserTags() {
        composeController?.refreshContent()
    }

    private fun performVote(
        context: Context,
        id: Int,
        direction: String,
        successMessage: String? = null,
        onSuccess: () -> Unit = {},
        onFailure: (String?, String?) -> Unit = { _, _ -> },
    ) {
        coroutineScope.launch {
            val result = hackerNewsUserService.vote(id.toString(), direction)
            if (result is HackerNewsActionResult.Success) {
                successMessage?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
                onSuccess()
                return@launch
            }
            result.showLoginPromptIfCredentialsMissing(context)
            val (summary, detail) = result.failureDetails()
            MainActivity.showFailureDetailForActiveUi(summary, detail)
            Toast.makeText(
                context,
                "Vote unsuccessful, see dialog for response",
                Toast.LENGTH_SHORT,
            ).show()
            onFailure(summary, detail)
        }
    }

    private fun showVoteFailure(context: Context, result: HackerNewsActionResult) {
        result.showLoginPromptIfCredentialsMissing(context)
        val (summary, detail) = result.failureDetails()
        MainActivity.showFailureDetailForActiveUi(summary, detail)
        Toast.makeText(
            context,
            "Vote unsuccessful, see dialog for response",
            Toast.LENGTH_SHORT,
        ).show()
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
        val mode = AndroidAiSummarySettings.mode(context)

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
        context: Context,
        mode: String,
        articleText: String?,
        onUpdate: Runnable,
        onDone: Runnable
    ) {
        val hasArticleText = !TextUtils.isEmpty(articleText)
        if (mode == AndroidAiSummarySettings.MODE_LOCAL) {
            val callback = object : LocalSummaryCallback {
                override fun onDebugInfo(debugInfo: String?) {
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
                LocalSummaryManager.summarizeText(context, articleText, callback)
            } else {
                LocalSummaryManager.summarizeArticle(context, story!!.url, callback)
            }
        } else {
            val config = AndroidAiSummarySettings.cloudConfig(context)
            val summaryFlow = if (hasArticleText) {
                NetworkComponent.summaryUseCase.summarizeText(config, articleText)
            } else {
                NetworkComponent.summaryUseCase.summarizeArticle(config, story!!.url.orEmpty())
            }
            coroutineScope.launch {
                try {
                    summaryFlow.collect { event ->
                        when (event) {
                            is CloudSummaryEvent.DebugInfo -> onUpdate.run()
                            is CloudSummaryEvent.Progress -> {
                                story!!.summary = event.summary
                                onUpdate.run()
                            }
                            is CloudSummaryEvent.Success -> {
                                story!!.summary = event.summary
                                story!!.summaryGeneratedSuccessfully = true
                                onDone.run()
                            }
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    story!!.summary = "Failed to generate summary: " +
                        (error.message?.takeUnless(String::isEmpty) ?: "Unknown error")
                    story!!.summaryGeneratedSuccessfully = false
                    onDone.run()
                }
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
        // Keep WebView startup clear of the comments entrance transition. WebView process and
        // renderer initialization can otherwise land on the same frames as the shared transition
        // on physical devices, which makes opening a story feel much heavier than it is.
        private const val WEBVIEW_BACKGROUND_INITIALIZATION_DELAY_MS = 900L
    }
}
