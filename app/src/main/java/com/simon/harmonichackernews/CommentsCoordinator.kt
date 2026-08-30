package com.simon.harmonichackernews

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnPreDraw
import androidx.webkit.WebViewFeature
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.CommentsFeatureHost
import com.simon.harmonichackernews.app.createCommentsStore
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.linkpreview.LinkPreviewController
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toStory
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.CommentsPlatformDependencies
import com.simon.harmonichackernews.presentation.CommentTargetResolution
import com.simon.harmonichackernews.presentation.CommentsBackContext
import com.simon.harmonichackernews.presentation.CommentsBackPolicy
import com.simon.harmonichackernews.presentation.CommentsBackTarget
import com.simon.harmonichackernews.presentation.CommentsHostRestoration
import com.simon.harmonichackernews.presentation.CommentsOverlayRestoration
import com.simon.harmonichackernews.presentation.CommentsPlatformEffect
import com.simon.harmonichackernews.presentation.CommentsPresentationCapabilities
import com.simon.harmonichackernews.presentation.CommentsRuntimeEffect
import com.simon.harmonichackernews.presentation.CommentsSettingsState
import com.simon.harmonichackernews.presentation.CommentsState
import com.simon.harmonichackernews.presentation.UserMessageDuration
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.comments.CommentsPlatformPresentation
import com.simon.harmonichackernews.ui.comments.CommentsFeatureListener
import com.simon.harmonichackernews.ui.comments.CommentsScreenStateFactory
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.utils.StatusBarProtectionUtils
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.utils.AndroidDisplay
import com.simon.harmonichackernews.utils.ViewUtils
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.simon.harmonichackernews.settings.UserSettings

private class CommentsViewSession(
    val host: CommentsWebViewHost,
    val linkPreviewController: LinkPreviewController,
    val webViewController: CommentsWebViewController,
) {
    var backPressedCallback: OnBackPressedCallback? = null
    var composeController: CommentsComposeController? = null
}

class CommentsCoordinator(
    private val activity: MainActivity,
    private val destination: StoryDestination,
    internal val sessionKey: Int,
    savedInstanceState: Bundle?,
    private val navigation: MainNavigationController,
    private val appComposition: HarmonicAppComposition = activity.harmonicAppComposition,
    private val platformDependencies: CommentsPlatformDependencies =
        appComposition.commentsPlatformDependencies(),
    userSettings: UserSettings = appComposition.userSettings,
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessionState = navigation.scene.sessions.commentsStateFor(
        sessionKey,
        destination.storyId,
    )
    private val restoringSession = sessionState.initialized
    private val scrollProgress = sessionState.scrollProgress
    private var restoringStoredProgress = scrollProgress.initialized
    private var started = false
    private var destroyed = false
    private val commentsStore = appComposition.createCommentsStore(
        CommentsFeatureHost(
            scope = coroutineScope,
            sessionState = sessionState,
            platform = platformDependencies,
            userSettings = userSettings,
            canLoadArticleTextOnDemand = true,
        ),
    )
    private var viewSession: CommentsViewSession? = null
    private val webViewHost: CommentsWebViewHost?
        get() = viewSession?.host
    private val linkPreviewController: LinkPreviewController?
        get() = viewSession?.linkPreviewController
    private val webViewController: CommentsWebViewController?
        get() = viewSession?.webViewController
    private val backPressedCallback: OnBackPressedCallback?
        get() = viewSession?.backPressedCallback
    private val composeController: CommentsComposeController?
        get() = viewSession?.composeController
    private var commentsContentInsetLeft = 0
    private var commentsContentInsetRight = 0
    private var hostRestoration by sessionState::hostRestoration
    private var showWebsite by sessionState::showWebsite
    private var integratedWebview = true
    private var topInset = 0
    private val commentsLoaded: Boolean
        get() = commentsStore.state.value.presenter.loaded
    private var appliedCommentsThemeVersion = -1L
    private var appliedCommentsSettingsVersion = -1L
    private var story by sessionState::story
    private var originalStatusBarColor = Color.TRANSPARENT
    private var originalStatusBarColorCaptured = false
    private var commentsPaneStatusBarColor = Color.TRANSPARENT
    private var composeHeaderStatusBarCoverage = 0f
    private var commentsHeaderStatusBarColor = Color.TRANSPARENT
    private var appliedStatusBarProtectionKnown = false
    private var appliedStatusBarProtectionEnabled = false
    private var appliedStatusBarProtectionColor = Color.TRANSPARENT
    internal val composeUiController: CommentsComposeController?
        get() = composeController
    private var hostActive = true
    private var firstDrawCompleted = false
    private var pendingVisibleWebsiteInitialization = false
    private var pendingComposeSummaryRequest = false

    init {
        coroutineScope.launch { commentsStore.effects.collect(::handleCommentsRuntimeEffect) }
        coroutineScope.launch {
            commentsStore.state.collect { state ->
                state.settings?.takeIf { it.version != appliedCommentsSettingsVersion }?.let {
                    appliedCommentsSettingsVersion = it.version
                    applyPlatformSettingsState(it)
                }
                renderCommentsState(state)
            }
        }
        initializeView(CommentsWebViewHost(activity), savedInstanceState)
    }

    val webViewRoot: View
        get() {
            val host = webViewHost
            checkNotNull(host) { "Comments coordinator is destroyed" }
            return host.root
        }

    private val activeContext: Context?
        get() = if (destroyed) null else activity

    private val attachedRoot: View?
        get() = if (destroyed) null else viewSession?.host?.root

    private val isActive: Boolean
        get() = !destroyed

    private fun initializeView(host: CommentsWebViewHost, savedInstanceState: Bundle?) {
        val view = host.root
        if (savedInstanceState != null) {
            hostRestoration = restoreHostState(savedInstanceState)
        }
        val restoredSorting = hostRestoration.sorting
        topInset = 0

        commentsStore.start(
            initialStory = destination.toStory(),
            showWebsite = destination.showWebsite,
            scrollToCommentId = destination.scrollToCommentId,
            restoring = restoringSession,
            restoredSorting = restoredSorting,
        )

        originalStatusBarColor = activity.window.statusBarColor
        originalStatusBarColorCaptured = true


        commentsPaneStatusBarColor =
            StatusBarProtectionUtils.getPaneBackgroundColor(activity)
        commentsHeaderStatusBarColor = commentsPaneStatusBarColor
        appliedStatusBarProtectionKnown = false
        updateCommentsStatusBarAppearance()

        refreshPresentationCapabilities()
        val featureSettings = checkNotNull(commentsStore.state.value.settings)
        val readingPreferences = featureSettings.reading
        integratedWebview = featureSettings.integratedWebView
        val blockAds = readingPreferences.blockAds && !hostRestoration.adBlockDisabled

        val progressIndicator = host.progressIndicator
        val linkPreviewController = LinkPreviewController(
            story,
            appComposition,
            readingPreferences,
            LinkPreviewController.Callbacks(commentsStore::refreshStoryPresentation),
        )
        val webViewController = CommentsWebViewController(
            object : CommentsWebViewHostGateway {
                override val context: Context?
                    get() = activeContext

                override val isAttached: Boolean
                    get() = attachedRoot != null
            },
            story,
            linkPreviewController,
            appComposition.webContent.createRuntime(),
            appComposition.storyCache,
            appComposition.pdfDownloads,
            coroutineScope,
            object : CommentsWebViewController.Callbacks {
                override fun openExternalLink(url: String) {
                    navigation.scene.links.openExternal(
                        ExternalLinkRequest(url, preferInApp = false),
                    )
                }

                override fun showMessage(
                    message: String?,
                    duration: UserMessageDuration,
                ) {
                    this@CommentsCoordinator.showMessage(message, duration)
                }

                override fun setFullscreenSystemBarsHidden(hidden: Boolean) {
                    if (destroyed) return
                    val windowInsetsController = ViewCompat.getWindowInsetsController(
                        activity.window.decorView,
                    ) ?: return
                    if (hidden) {
                        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                        windowInsetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                override fun syncOnBackPressedCallbackEnabledState() {
                    this@CommentsCoordinator.syncOnBackPressedCallbackEnabledState()
                }

                override fun onReaderModeChanged(enabled: Boolean) {
                    val session = viewSession ?: return
                    session.composeController?.updateReaderMode(
                        session.webViewController.isReaderModeAvailable,
                        enabled,
                    )
                }

                override fun onReaderModeAvailabilityChanged(available: Boolean) {
                    val session = viewSession ?: return
                    session.composeController?.updateReaderMode(
                        available,
                        session.webViewController.isReaderModeEnabled(),
                    )
                }

                override fun onFullscreenChanged(fullscreen: Boolean) {
                    composeController?.updateWebViewFullscreen(fullscreen)
                }
            })
        val session = CommentsViewSession(
            host = host,
            linkPreviewController = linkPreviewController,
            webViewController = webViewController,
        )
        viewSession = session
        webViewController.bindViews(host, progressIndicator)
        webViewController.configure(
            showWebsite,
            integratedWebview,
            readingPreferences,
            blockAds
        )

        val initialStory = checkNotNull(story)
        if (initialStory.id <= 0 && initialStory.title == null) {
            // Empty view for tablets
            webViewController.setContainerVisibility(View.GONE)

            return
        }

        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackCancelled() {
                when (commentsBackTarget()) {
                    CommentsBackTarget.LINK_PREVIEW ->
                        composeController?.cancelLinkPreviewPredictiveBack()
                    CommentsBackTarget.COMMENT_ACTION ->
                        composeController?.cancelCommentActionPredictiveBack()
                    CommentsBackTarget.CLOSE_WEBSITE -> endCommentsPredictiveBackVisuals()
                    else -> Unit
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                when (commentsBackTarget()) {
                    CommentsBackTarget.LINK_PREVIEW -> composeController?.updateLinkPreviewPredictiveBack(
                        backEvent.progress,
                        backEvent.swipeEdge,
                        backEvent.touchY
                    )
                    CommentsBackTarget.COMMENT_ACTION -> composeController?.updateCommentActionPredictiveBack(
                        backEvent.progress,
                        backEvent.swipeEdge,
                        backEvent.touchY
                    )
                    CommentsBackTarget.CLOSE_WEBSITE ->
                        updateCommentsPredictiveBackVisuals(backEvent.progress, false)
                    else -> Unit
                }
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                when (commentsBackTarget()) {
                    CommentsBackTarget.LINK_PREVIEW -> composeController?.startLinkPreviewPredictiveBack(
                        backEvent.progress,
                        backEvent.swipeEdge,
                        backEvent.touchY
                    )
                    CommentsBackTarget.COMMENT_ACTION -> composeController?.updateCommentActionPredictiveBack(
                        backEvent.progress,
                        backEvent.swipeEdge,
                        backEvent.touchY
                    )
                    CommentsBackTarget.CLOSE_WEBSITE ->
                        updateCommentsPredictiveBackVisuals(backEvent.progress, true)
                    else -> Unit
                }
            }

            override fun handleOnBackPressed() {
                when (commentsBackTarget()) {
                    CommentsBackTarget.LINK_PREVIEW -> {
                        if (composeController?.isLinkPreviewPredictiveBackActive() == true) {
                            composeController?.commitLinkPreviewPredictiveBack()
                        } else {
                            composeController?.requestDismissLinkPreview()
                        }
                    }
                    CommentsBackTarget.COMMENT_ACTION -> {
                        if (composeController?.isCommentActionPredictiveBackActive() == true) {
                            composeController?.commitCommentActionPredictiveBack()
                        } else {
                            composeController?.requestDismissCommentActions()
                        }
                    }
                    CommentsBackTarget.CUSTOM_WEB_CONTENT ->
                        webViewController.hideCustomView(true)
                    CommentsBackTarget.READER_MODE -> webViewController.disableReaderMode()
                    CommentsBackTarget.CLOSE_WEBSITE -> {
                        composeController?.requestExpandSheet()
                        endCommentsPredictiveBackVisuals()
                    }
                    CommentsBackTarget.WEB_HISTORY ->
                        webViewController.goBackFromVisibleWebView()
                    CommentsBackTarget.NONE -> navigation.closeStory()
                }
            }
        }
        session.backPressedCallback = backPressedCallback
        activity.onBackPressedDispatcher.addCallback(backPressedCallback)

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
                if (AndroidDisplay.isTablet(activity.resources)) {
                    contentPaddingRight =
                        activity.resources.getDimensionPixelSize(R.dimen.extra_pane_padding)
                }
                val leftPadding = max(max(cutoutInsets.left, systemInsets.left), contentPaddingLeft)
                val rightPadding =
                    max(max(cutoutInsets.right, systemInsets.right), contentPaddingRight)
                setCommentsContentSideInsets(leftPadding, rightPadding)
                updateWebViewContainerPadding()

                return windowInsets
            }
        })
        ViewUtils.requestApplyInsetsWhenAttached(view)

        syncOnBackPressedCallbackEnabledState()

        // The pane color was already resolved from the active theme above. Reusing it avoids two
        // cold theme/preference/resource lookups on the comments-open frame.
        webViewController.setContainerBackgroundColor(commentsPaneStatusBarColor)

        initializeComposeUi()
        scheduleWebViewInitializationAfterFirstDraw(view)

        val restoreScrollFromCache = !showWebsite

        // Navigation Compose owns the screen transition. Do not hold the first frame
        // behind the old inset-gated postponed transition; render the header skeleton immediately
        // and start loading on the next main-loop turn.
        if (!commentsLoaded) {
            view.post(object : Runnable {
                override fun run() {
                    if (attachedRoot !== view || !isActive) {
                        return
                    }
                    loadInitialStoryAndComments(restoreScrollFromCache)
                }
            })
        }
    }

    private fun updateCommentsPredictiveBackVisuals(progress: Float, started: Boolean) {
        val session = viewSession ?: return
        if (started) {
            session.webViewController.beginPredictiveBackScrollFreeze()
        } else {
            session.webViewController.maintainPredictiveBackScrollFreeze()
        }
        session.composeController?.let { controller ->
            if (started) {
                controller.beginPredictiveBack(progress)
            } else {
                controller.updatePredictiveBack(progress)
            }
        }
    }

    fun beginVisibleWebViewPredictiveBackScrollFreeze(): Boolean {
        val session = viewSession ?: return false
        if (session.composeController?.isWebsiteVisible() != true) return false
        session.webViewController.beginPredictiveBackScrollFreeze()
        return true
    }

    fun maintainVisibleWebViewPredictiveBackScrollFreeze() {
        viewSession?.webViewController?.maintainPredictiveBackScrollFreeze()
    }

    fun endVisibleWebViewPredictiveBackScrollFreeze() {
        viewSession?.webViewController?.endPredictiveBackScrollFreeze()
    }

    fun handlesBackInternally(): Boolean {
        syncOnBackPressedCallbackEnabledState()
        return backPressedCallback?.isEnabled == true
    }

    fun setHostActive(active: Boolean) {
        if (hostActive == active) return
        hostActive = active
        syncOnBackPressedCallbackEnabledState()
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
        val session = viewSession ?: return
        session.webViewController.endPredictiveBackScrollFreeze()
        session.composeController?.endPredictiveBack()
    }

    private fun initializeComposeUi() {
        val currentStory = story ?: return
        val session = viewSession ?: return
        val platformCallbacks = object : CommentsFeatureListener.PlatformCallbacks {
            override fun isRestoringScroll() = restoringStoredProgress
            override fun canHandleCommentAction() = isActive && composeController != null
            override fun onCommentActionOverlayVisibilityChanged() {
                syncOnBackPressedCallbackEnabledState()
                updateCommentsStatusBarAppearance()
            }

            override fun onLinkPreviewOverlayVisibilityChanged() =
                syncOnBackPressedCallbackEnabledState()

            override fun scrollToSearchResult(commentId: Int) {
                syncComposeState()
                composeController?.scrollToSearchResult(commentId)
            }

            override fun collapseSheetForWebsite() = collapseBottomSheetForWebsite()

            override fun onSheetProgressChanged(expandedFraction: Float) {
                if (expandedFraction < WEBSITE_PRELOAD_SHEET_THRESHOLD && integratedWebview &&
                    webViewController?.hasWebView() == false
                ) requestVisibleWebsiteInitialization()
                updateCommentsStatusBarAppearance()
            }

            override fun onSheetSettled(expanded: Boolean) {
                if (!expanded && integratedWebview && webViewController?.hasWebView() == false) {
                    requestVisibleWebsiteInitialization()
                }
                syncOnBackPressedCallbackEnabledState()
                updateCommentsStatusBarAppearance()
            }

            override fun onHeaderColorChanged(color: Int) = updateHeaderStatusBarColor(color)

            override fun onHeaderCoverageChanged(coverage: Float) {
                composeHeaderStatusBarCoverage = max(0f, min(1f, coverage))
                updateCommentsStatusBarAppearance()
            }

        }
        val storySnapshot = checkNotNull(commentsStore.state.value.story)
        val controller = CommentsComposeController.create(
            shouldSmoothScroll = {
                commentsStore.state.value.settings?.smoothScroll ?: true
            },
            story = storySnapshot,
            initialThreadCached = commentsStore.state.value.initialThreadCached,
            showWebsite = showWebsite,
            initialScrollRestorationPending = restoringStoredProgress && !showWebsite,
            accountUser = commentsStore.state.value.accountUser,
            savedItemState = commentsStore.savedItemState,
            listener = CommentsFeatureListener(commentsStore, platformCallbacks),
        )
        session.composeController = controller
        navigation.attachCommentsComposeController(this, controller)
        restoreLinkSummaryAfterRecreation()
        syncComposeState()
        if (restoringSession && restoringStoredProgress) restoreScrollProgress()
        syncOnBackPressedCallbackEnabledState()
    }

    private fun syncComposeState() {
        val controller = composeController
        if (controller == null || story == null) {
            return
        }
        if (!restoringStoredProgress) {
            commentsStore.captureCollapsedComments()
        }
        renderCommentsState(commentsStore.state.value)
    }

    private fun renderCommentsState(state: CommentsState) {
        val controller = composeController ?: return
        CommentsScreenStateFactory.create(state, commentsPlatformPresentation())?.let {
            controller.updateContent(it)
        }
    }

    private fun commentsPlatformPresentation(): CommentsPlatformPresentation =
        CommentsPlatformPresentation(
            adBlockActive = webViewController?.isBlockingAds == true,
            readerModeAvailable = webViewController?.isReaderModeAvailable == true,
            readerModeEnabled = webViewController?.isReaderModeEnabled() == true,
            topInsetPx = topInset,
            contentInsetLeftPx = commentsContentInsetLeft,
            contentInsetRightPx = commentsContentInsetRight,
        )

    private fun requestComposeSummary() {
        if (!firstDrawCompleted) {
            pendingComposeSummaryRequest = true
            return
        }
        val beginSummary: (String?) -> Unit = commentsStore::startSummary
        webViewController?.getLoadedPageText(
            CommentsWebViewController.PageTextCallback(beginSummary),
        ) ?: beginSummary(null)
    }

    /** Applies WebView/theme facilities after the shared runtime reconciles preference changes. */
    private fun applyPlatformSettingsState(state: CommentsSettingsState) {
        val wasIntegrated = integratedWebview
        integratedWebview = state.integratedWebView
        val reading = state.reading
        webViewController?.let { controller ->
            controller.setIntegratedWebview(integratedWebview)
            controller.configure(
                showWebsite,
                integratedWebview,
                reading,
                reading.blockAds && !hostRestoration.adBlockDisabled,
            )
            if (integratedWebview && !wasIntegrated) requestConfiguredWebViewInitialization()
        }
        updateWebViewContainerPadding()
        if (state.themeRefreshVersion != appliedCommentsThemeVersion) {
            appliedCommentsThemeVersion = state.themeRefreshVersion
            activeContext?.let { currentContext ->
                webViewController?.setContainerBackgroundColor(
                    ContextCompat.getColor(
                        currentContext,
                        ThemeUtils.getBackgroundColorResource(currentContext),
                    ),
                )
            }
        }
        syncOnBackPressedCallbackEnabledState()
        syncComposeState()
    }

    private fun handleCommentsPlatformEffect(effect: CommentsPlatformEffect) {
        when (effect) {
            is CommentsPlatformEffect.OpenUser -> navigation.showUserDialog(
                effect.userName,
                Runnable { commentsStore.reconcileSettings() },
            )
            is CommentsPlatformEffect.OpenEditor -> navigation.openEditor(effect.destination)
            CommentsPlatformEffect.RequestLogin ->
                navigation.showLoginDialog()
            is CommentsPlatformEffect.ShowMessage -> navigation.showMessage(effect.message)
            is CommentsPlatformEffect.ShareText ->
                platformDependencies.sharing.share(effect.text)
            is CommentsPlatformEffect.CopyText -> {
                platformDependencies.clipboard.copy(effect.label, effect.text)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    navigation.showMessage("Text copied to clipboard")
                }
            }
            CommentsPlatformEffect.ReloadLinkPreviews ->
                linkPreviewController?.loadNetworkPreviews(activeContext)
            CommentsPlatformEffect.Summarize -> requestComposeSummary()
            is CommentsPlatformEffect.OpenStory ->
                navigation.openLinkedStory(effect.destination)
            is CommentsPlatformEffect.OpenExternalLink -> navigation.scene.links.openExternal(
                ExternalLinkRequest(effect.url, preferInApp = effect.preferInApp),
            )
            CommentsPlatformEffect.ShowSearch -> composeController?.showCommentSearch()
            CommentsPlatformEffect.DisableAdBlock -> {
                hostRestoration = hostRestoration.copy(adBlockDisabled = true)
                webViewController?.disableAdBlockAndReload()
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
            CommentsPlatformEffect.OpenWebsiteInBrowser ->
                webViewController?.openCurrentOrStoryUrlInBrowser()
            CommentsPlatformEffect.ToggleReaderMode -> webViewController?.toggleReaderMode()
            CommentsPlatformEffect.ToggleDarkMode -> webViewController?.toggleDarkMode()
        }
    }

    private fun commentsBackTarget(): CommentsBackTarget {
        val commentsController = composeController
        val websiteController = webViewController
        val websiteVisible = websiteController?.hasWebView() == true &&
            commentsController?.isWebsiteVisible() == true
        return CommentsBackPolicy.target(
            CommentsBackContext(
                hostActive = hostActive,
                linkPreviewVisible = commentsController?.isLinkPreviewOverlayShowing() == true,
                commentActionVisible = commentsController?.isCommentActionOverlayShowing() == true,
                customWebContentVisible = websiteController?.isShowingCustomView == true,
                readerModeEnabled = websiteController?.isReaderModeEnabled() == true,
                websiteVisible = websiteVisible,
                webHistoryAvailable = websiteController?.canGoBack() == true,
                closeWebsiteOnBack =
                    commentsStore.state.value.settings?.reading?.closeWebViewOnBack == true,
            ),
        )
    }

    private fun syncOnBackPressedCallbackEnabledState() {
        backPressedCallback?.isEnabled = commentsBackTarget() != CommentsBackTarget.NONE
    }

    private fun updateBottomSheetMargin(navbarHeight: Int) {
        val standardMargin = AndroidDisplay.dpToPxInt(
            activity.resources, (if (AndroidDisplay.isTablet(
                    activity.resources
                )
            ) 81 else 68).toFloat()
        )

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        params.setMargins(0, 0, 0, standardMargin + navbarHeight)

        webViewController?.setContainerLayoutParams(params)
    }

    private fun updateHeaderStatusBarColor(color: Int) {
        commentsHeaderStatusBarColor = color
        updateCommentsStatusBarAppearance()
    }

    private fun syncCommentsStatusBarProtection() {
        if (!isActive) {
            return
        }
        commentsPaneStatusBarColor =
            StatusBarProtectionUtils.getPaneBackgroundColor(activity)
        updateCommentsStatusBarAppearance()
    }

    private fun updateCommentsStatusBarAppearance(commentsStatusBarColor: Int = this.currentCommentsStatusBarColor) {
        val host = webViewHost
        if (host == null || !isActive) {
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
        if (!isActive) {
            return
        }
        val windowStatusBarColor =
            if (navigation.isAdaptiveTwoPane() ||
                commentsStore.state.value.settings?.transparentStatusBar == true
            )
                Color.TRANSPARENT
            else
                statusBarColor
        if (activity.window.statusBarColor != windowStatusBarColor) {
            activity.window.statusBarColor = windowStatusBarColor
        }
    }

    fun onAdaptiveLayoutChanged() {
        updateCommentsStatusBarAppearance()
        updateWebViewContainerPadding()
    }

    private fun updateWebViewContainerPadding() {
        val upButtonInset = if (
            integratedWebview &&
            !navigation.isAdaptiveTwoPane() &&
            commentsStore.state.value.settings?.displaySettings?.showUpButton == true
        ) {
            AndroidDisplay.dpToPxInt(activity.resources, 64f)
        } else {
            0
        }
        webViewController?.setContainerPadding(
            commentsContentInsetLeft,
            topInset + upButtonInset,
            commentsContentInsetRight,
            0,
        )
    }

    private fun shouldShowCommentsStatusBarProtection(): Boolean {
        return this.isBottomSheetFullyExpanded
    }

    private val isBottomSheetFullyExpanded: Boolean
        get() = composeController?.isSheetExpanded() == true

    internal fun canNavigateCommentsWithVolumeButtons(): Boolean =
        isActive && isBottomSheetFullyExpanded

    fun switchStoryViewIfMatching(storyId: Int, showWebsite: Boolean): Boolean {
        if (!isActive || !commentsStore.canSwitchStoryView(storyId) || webViewController == null) {
            return false
        }
        if (showWebsite) {
            requestVisibleWebsiteInitialization()
            val controller = composeController
            if (controller != null) {
                controller.requestWebsite()
                return true
            }
            scrollCommentsToTopThenCollapseBottomSheet()
        } else {
            composeController?.requestStopScroll()
            composeController?.requestExpandSheet()
        }
        return true
    }

    private fun scrollCommentsToTopThenCollapseBottomSheet() {
        val controller = composeController
        if (controller != null) {
            controller.requestWebsite()
        } else {
            collapseBottomSheetForWebsite()
        }
    }

    private fun collapseBottomSheetForWebsite() {
        requestVisibleWebsiteInitialization()
        composeController?.requestCollapseSheet()
    }

    private fun scheduleWebViewInitializationAfterFirstDraw(root: View) {
        root.doOnPreDraw {
            root.post {
                if (attachedRoot !== root || !isActive) return@post
                firstDrawCompleted = true
                if (pendingVisibleWebsiteInitialization) {
                    pendingVisibleWebsiteInitialization = false
                    webViewController?.initializeForVisibleWebsite()
                } else {
                    requestConfiguredWebViewInitialization()
                }
                if (pendingComposeSummaryRequest) {
                    pendingComposeSummaryRequest = false
                    requestComposeSummary()
                }
            }
        }
    }

    private fun requestVisibleWebsiteInitialization() {
        if (!integratedWebview) return
        if (!firstDrawCompleted) {
            pendingVisibleWebsiteInitialization = true
            return
        }
        webViewController?.initializeForVisibleWebsite()
    }

    private fun requestConfiguredWebViewInitialization() {
        if (!integratedWebview || !firstDrawCompleted) return
        webViewController?.initializeAfterFirstDraw()
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
        if (isActive) refreshPresentationCapabilities()
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

    private fun refreshPresentationCapabilities() {
        commentsStore.updatePresentationCapabilities(
            CommentsPresentationCapabilities(
                showInvertAction = shouldShowInvertAction(),
                isTablet = AndroidDisplay.isTablet(activity.resources),
            ),
        )
    }

    private fun shouldShowInvertAction(): Boolean {
        return WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)
                || WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
    }


    fun onStart() {
        if (destroyed || started) return
        started = true
        refreshPresentationCapabilities()
        commentsStore.onResume()
        syncComposeState()
    }

    fun onResume() {
        if (destroyed) return

        commentsStore.onResume()
        syncCommentsStatusBarProtection()
        syncComposeState()
    }

    fun onStop() {
        if (!started) return
        started = false
    }

    private fun captureHostRestoration(preserveOverlay: Boolean): CommentsHostRestoration {
        val controller = composeController
        val overlay = if (!preserveOverlay || controller == null) {
            null
        } else when {
            controller.isLinkPreviewReferenceShowing() ->
                controller.linkPreviewVisibleUrl?.let {
                    CommentsOverlayRestoration.Reference(
                        it,
                        controller.getLinkPreviewFallbackTitle(),
                    )
                }
            controller.isLinkPreviewImageShowing() ->
                controller.linkPreviewVisibleUrl?.let { CommentsOverlayRestoration.Image(it) }
            else -> null
        }
        return hostRestoration.copy(
            sorting = commentsStore.state.value.thread.sorting,
            commentActionId = controller?.getVisibleCommentActionId()
                ?.takeIf { it != -1 }
                ?: hostRestoration.commentActionId,
            overlay = overlay,
        )
    }

    private fun restoreHostState(bundle: Bundle): CommentsHostRestoration {
        val referenceUrl = bundle.getString(STATE_REFERENCE_LINK_SUMMARY_URL)
        val imageUrl = bundle.getString(STATE_PREVIEW_IMAGE_DIALOG_URL)
        val overlay = when {
            !referenceUrl.isNullOrBlank() -> CommentsOverlayRestoration.Reference(
                referenceUrl,
                bundle.getString(STATE_REFERENCE_LINK_SUMMARY_TITLE),
            )
            !imageUrl.isNullOrBlank() -> CommentsOverlayRestoration.Image(imageUrl)
            else -> null
        }
        return CommentsHostRestoration(
            sorting = bundle.getString(STATE_COMMENT_SORTING),
            commentActionId = bundle.getInt(STATE_COMMENT_ACTION_COMMENT_ID, -1),
            adBlockDisabled = bundle.getBoolean(STATE_ADBLOCK_DISABLED_FOR_SESSION, false),
            overlay = overlay,
        )
    }

    private fun writeHostState(bundle: Bundle, restoration: CommentsHostRestoration) {
        bundle.putString(STATE_COMMENT_SORTING, restoration.sorting)
        bundle.putInt(STATE_COMMENT_ACTION_COMMENT_ID, restoration.commentActionId)
        bundle.putBoolean(STATE_ADBLOCK_DISABLED_FOR_SESSION, restoration.adBlockDisabled)
        when (val overlay = restoration.overlay) {
            is CommentsOverlayRestoration.Reference -> {
                bundle.putString(STATE_REFERENCE_LINK_SUMMARY_URL, overlay.url)
                bundle.putString(STATE_REFERENCE_LINK_SUMMARY_TITLE, overlay.fallbackTitle)
            }
            is CommentsOverlayRestoration.Image ->
                bundle.putString(STATE_PREVIEW_IMAGE_DIALOG_URL, overlay.url)
            null -> Unit
        }
    }

    fun onSaveInstanceState(outState: Bundle) {
        hostRestoration = captureHostRestoration(preserveOverlay = true)
        writeHostState(outState, hostRestoration)
    }

    private fun restoreScrollProgress() {
        val restoration = commentsStore.restoreScrollProgress()
        restoringStoredProgress = false
        val controller = composeController
        if (controller != null && restoration != null) {
            syncComposeState()
            controller.scrollToComment(
                restoration.commentId,
                restoration.offset,
                false
            )
        }
    }

    private fun scrollToTargetComment() {
        when (val target = commentsStore.consumeCommentTarget()) {
            CommentTargetResolution.None -> Unit
            is CommentTargetResolution.Found -> {
                syncComposeState()
                composeController?.scrollToComment(target.commentId, topInset, false)
            }
            is CommentTargetResolution.NotFound -> navigation.showMessage("Comment not found")
        }
    }

    fun onDestroy() {
        if (destroyed) return
        if (started) onStop()
        val session = viewSession
        val controllerToDetach = session?.composeController
        val changingConfigurations = activity.isChangingConfigurations
        hostRestoration = captureHostRestoration(preserveOverlay = changingConfigurations)
        controllerToDetach?.let { controller ->
            if (controller.isLinkPreviewOverlayShowing()) {
                controller.completeLinkPreviewDismiss()
            }
            controller.completeCommentActionDismiss(dispatchPendingAction = false)
        }
        if (originalStatusBarColorCaptured) {
            activity.window.statusBarColor = originalStatusBarColor
            originalStatusBarColorCaptured = false
        }

        val rootView = attachedRoot
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, null)
        }

        session?.backPressedCallback?.remove()

        commentsStore.close()
        session?.linkPreviewController?.dispose()
        coroutineScope.cancel()
        session?.webViewController?.onDestroyView(rootView)
        if (controllerToDetach != null) {
            navigation.detachCommentsComposeController(this, controllerToDetach)
        }

        session?.webViewController?.clearViewReferences()
        session?.linkPreviewController?.cancelPendingNitterLinkPreviewRead()
        session?.backPressedCallback = null
        session?.composeController = null
        viewSession = null
        appliedStatusBarProtectionKnown = false
        destroyed = true
    }

    private fun restoreLinkSummaryAfterRecreation() {
        val rootView = attachedRoot
        val overlay = hostRestoration.overlay
        if (overlay is CommentsOverlayRestoration.Image && rootView != null) {
            hostRestoration = hostRestoration.copy(overlay = null)
            rootView.post(Runnable {
                composeController?.let { controller ->
                    val backgroundColor = if (commentsHeaderStatusBarColor != Color.TRANSPARENT)
                        commentsHeaderStatusBarColor
                    else
                        ContextCompat.getColor(
                            activity,
                            ThemeUtils.getBackgroundColorResource(activity)
                        )
                    val storyTitle = story?.title
                    controller.showImagePreview(
                        overlay.url,
                        if (TextUtils.isEmpty(storyTitle))
                            "Story preview image"
                        else
                            "Preview image for $storyTitle",
                        null,
                        backgroundColor
                    )
                }
            })
            return
        }
        val referenceRootView = attachedRoot
        if (overlay !is CommentsOverlayRestoration.Reference || referenceRootView == null) {
            return
        }
        hostRestoration = hostRestoration.copy(overlay = null)
        referenceRootView.post(Runnable {
            composeController?.showReferencePreview(overlay.url, overlay.fallbackTitle)
        })
    }

    private fun loadInitialStoryAndComments(restoreScrollFromCache: Boolean) {
        if (!isActive || !isCommentsViewActive || story == null) {
            return
        }

        commentsStore.loadInitial(restoreScrollFromCache)
    }

    private fun handleCommentsRuntimeEffect(effect: CommentsRuntimeEffect) {
        when (effect) {
            is CommentsRuntimeEffect.Platform -> handleCommentsPlatformEffect(effect.effect)
            is CommentsRuntimeEffect.StateChanged -> syncComposeState()
            is CommentsRuntimeEffect.ShowCommentActions ->
                composeController?.showCommentActions(effect.comment)
            is CommentsRuntimeEffect.ThreadReady -> {
                if (!isCommentsViewActive) return
                commentsStore.state.value.settings?.let(::applyPlatformSettingsState)
                if (effect.restoreScroll && restoringStoredProgress &&
                    scrollProgress.storyId == story?.id
                ) {
                    restoreScrollProgress()
                } else if (restoringStoredProgress) {
                    // A cache-bypassing load deliberately does not restore saved progress. Leaving
                    // the pending flag set here keeps the already-loaded Compose list transparent.
                    restoringStoredProgress = false
                    composeController?.completeInitialScrollRestoration()
                }
                completeCommentsLoad(effect.headerChanged)
            }
            is CommentsRuntimeEffect.Diagnostic ->
                Log.w(TAG, effect.message, effect.cause)
            is CommentsRuntimeEffect.ActionFailed ->
                showActionFailure(effect.presentation)
            CommentsRuntimeEffect.RequestSummaryPageTextRetry ->
                retryComposeSummaryWithWebView()
        }
    }

    private fun retryComposeSummaryWithWebView() {
        val controller = webViewController ?: run {
            commentsStore.startSummary(null)
            return
        }
        controller.requestSummary(
            CommentsWebViewController.PageTextCallback(commentsStore::startSummary),
        )
    }

    private fun showActionFailure(
        presentation: com.simon.harmonichackernews.presentation.ActionFailurePresentation,
    ) {
        if (presentation.requestLogin) navigation.showLoginDialog()
        if (presentation.showDetails) {
            navigation.showFailureDetailDialog(
                presentation.failureSummary,
                presentation.failureDetail,
                null,
            )
        } else {
            navigation.showMessage(presentation.message)
        }
    }

    internal fun showMessage(
        message: String?,
        duration: UserMessageDuration = UserMessageDuration.SHORT,
    ) {
        navigation.showMessage(message, duration)
    }

    private fun completeCommentsLoad(updateHeaderAfterLoad: Boolean) {
        if (!this.isCommentsViewActive) {
            return
        }
        if (updateHeaderAfterLoad) syncComposeState()
        val commentsView = attachedRoot ?: return
        commentsView.post {
            if (!isCommentsViewActive) {
                return@post
            }
            scrollToTargetComment()
            restorePendingCommentAction()
        }
    }

    private fun restorePendingCommentAction() {
        val pendingCommentActionId = hostRestoration.commentActionId
        if (pendingCommentActionId == -1) return
        val controller = composeController ?: return
        val comment = commentsStore.comment(pendingCommentActionId) ?: return
        hostRestoration = hostRestoration.copy(commentActionId = -1)
        controller.restoreCommentActions(comment)
        syncOnBackPressedCallbackEnabledState()
    }

    private val isCommentsViewActive: Boolean
        get() = attachedRoot != null

    @JvmOverloads
    fun navigateToNextComment(topLevelOnly: Boolean = true, scaleLongScrollSpeed: Boolean = false) {
        composeController?.navigateNext(topLevelOnly, scaleLongScrollSpeed)
    }

    @JvmOverloads
    fun navigateToPreviousComment(
        topLevelOnly: Boolean = true,
        scaleLongScrollSpeed: Boolean = false
    ) {
        composeController?.navigatePrevious(topLevelOnly, scaleLongScrollSpeed)
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
        // Ignore tiny nested-scroll/settling deviations while the comments sheet is effectively
        // fully expanded. A real website reveal still preloads after the first 2% of travel, and
        // the settled callback remains the correctness fallback.
        private const val WEBSITE_PRELOAD_SHEET_THRESHOLD = 0.98f
    }
}
