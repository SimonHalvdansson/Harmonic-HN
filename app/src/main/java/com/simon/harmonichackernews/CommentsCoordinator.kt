package com.simon.harmonichackernews

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
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
import androidx.webkit.WebViewFeature
import com.google.android.material.progressindicator.LinearProgressIndicator
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

class CommentsCoordinator(
    private val activity: MainActivity,
    private val destination: StoryDestination,
    sessionKey: Int,
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
        ),
    )
    private var webViewHost: CommentsWebViewHost?
    private var commentsContentInsetLeft = 0
    private var commentsContentInsetRight = 0
    private var hostRestoration by sessionState::hostRestoration
    private var progressIndicator: LinearProgressIndicator? = null
    private var linkPreviewController: LinkPreviewController? = null
    private var webViewController: CommentsWebViewController? = null
    private var showWebsite by sessionState::showWebsite
    private var integratedWebview = true
    private var topInset = 0
    private val commentsLoaded: Boolean
        get() = commentsStore.state.value.presenter.loaded
    private var appliedCommentsThemeVersion = -1L
    private var appliedCommentsSettingsVersion = -1L
    private var backPressedCallback: OnBackPressedCallback? = null
    private var story by sessionState::story
    private var originalStatusBarColor = Color.TRANSPARENT
    private var originalStatusBarColorCaptured = false
    private var commentsPaneStatusBarColor = Color.TRANSPARENT
    private var composeHeaderStatusBarCoverage = 0f
    private var commentsHeaderStatusBarColor = Color.TRANSPARENT
    private var appliedStatusBarProtectionKnown = false
    private var appliedStatusBarProtectionEnabled = false
    private var appliedStatusBarProtectionColor = Color.TRANSPARENT
    private var composeController: CommentsComposeController? = null
    private var hostActive = true

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

    private fun initializeView(savedInstanceState: Bundle?) {
        val view = this.webViewRoot
        if (savedInstanceState != null) {
            hostRestoration = restoreHostState(savedInstanceState)
        }
        val restoredSorting = hostRestoration.sorting
        val host = webViewHost
        checkNotNull(host) { "Comments WebView host was not created" }
        topInset = 0

        commentsStore.start(
            initialStory = destination.toStory(),
            showWebsite = destination.showWebsite,
            scrollToCommentId = destination.scrollToCommentId,
            restoring = restoringSession,
            restoredSorting = restoredSorting,
        )

        originalStatusBarColor = requireActivity().getWindow().getStatusBarColor()
        originalStatusBarColorCaptured = true


        commentsPaneStatusBarColor =
            StatusBarProtectionUtils.getPaneBackgroundColor(requireContext())
        commentsHeaderStatusBarColor = commentsPaneStatusBarColor
        appliedStatusBarProtectionKnown = false
        updateCommentsStatusBarAppearance()

        refreshPresentationCapabilities()
        val featureSettings = checkNotNull(commentsStore.state.value.settings)
        val readingPreferences = featureSettings.reading
        integratedWebview = featureSettings.integratedWebView
        val blockAds = readingPreferences.blockAds && !hostRestoration.adBlockDisabled

        progressIndicator = host.progressIndicator
        linkPreviewController = LinkPreviewController(
            story,
            appComposition,
            readingPreferences,
            LinkPreviewController.Callbacks(commentsStore::refreshStoryPresentation),
        )
        webViewController = CommentsWebViewController(
            this,
            story,
            linkPreviewController!!,
            appComposition.webContent.createRuntime(),
            appComposition.storyCache,
            appComposition.pdfDownloads,
            coroutineScope,
            object : CommentsWebViewController.Callbacks {
                override fun startActivity(intent: Intent) {
                    activity.startActivity(intent)
                }

                override fun openExternalLink(url: String) {
                    navigation.scene.links.openExternal(
                        ExternalLinkRequest(url, preferInApp = false),
                    )
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
            readingPreferences,
            blockAds
        )

        if (story!!.id <= 0 && story!!.title == null) {
            // Empty view for tablets
            webViewController!!.setContainerVisibility(View.GONE)

            return
        }

        backPressedCallback = object : OnBackPressedCallback(true) {
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
                        webViewController?.hideCustomView(true)
                    CommentsBackTarget.READER_MODE -> webViewController?.disableReaderMode()
                    CommentsBackTarget.CLOSE_WEBSITE -> {
                        composeController?.requestExpandSheet()
                        endCommentsPredictiveBackVisuals()
                    }
                    CommentsBackTarget.WEB_HISTORY ->
                        webViewController?.goBackFromVisibleWebView()
                    CommentsBackTarget.NONE -> navigation.closeStory()
                }
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
                if (AndroidDisplay.isTablet(this@CommentsCoordinator.resources)) {
                    contentPaddingRight =
                        this@CommentsCoordinator.resources.getDimensionPixelSize(R.dimen.extra_pane_padding)
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

        progressIndicator = host.progressIndicator

        val shouldInitializeWebViewBeforeFirstDraw = integratedWebview && showWebsite

        if (shouldInitializeWebViewBeforeFirstDraw) {
            webViewController!!.initialize()
        }

        // The pane color was already resolved from the active theme above. Reusing it avoids two
        // cold theme/preference/resource lookups on the comments-open frame.
        webViewController!!.setContainerBackgroundColor(commentsPaneStatusBarColor)

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
        if (webViewController != null) {
            webViewController!!.endPredictiveBackScrollFreeze()
        }
        if (composeController != null) {
            composeController!!.endPredictiveBack()
            return
        }
    }

    private fun initializeComposeUi() {
        val currentStory = story ?: return
        if (webViewHost == null) return
        val platformCallbacks = object : CommentsFeatureListener.PlatformCallbacks {
            override fun isRestoringScroll() = restoringStoredProgress
            override fun canHandleCommentAction() = isAdded && composeController != null
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
                ) webViewController?.initializeForVisibleWebsite()
                updateCommentsStatusBarAppearance()
            }

            override fun onSheetSettled(expanded: Boolean) {
                if (!expanded && integratedWebview && webViewController?.hasWebView() == false) {
                    webViewController?.initializeForVisibleWebsite()
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
        composeController = CommentsComposeController.create(
            shouldSmoothScroll = {
                commentsStore.state.value.settings?.smoothScroll ?: true
            },
            story = storySnapshot,
            showWebsite = showWebsite,
            accountUser = commentsStore.state.value.accountUser,
            savedItemState = commentsStore.savedItemState,
            listener = CommentsFeatureListener(commentsStore, platformCallbacks),
        )
        navigation.attachCommentsComposeController(composeController!!)
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
            if (integratedWebview && !wasIntegrated) controller.initialize()
        }
        updateWebViewContainerPadding()
        if (state.themeRefreshVersion != appliedCommentsThemeVersion) {
            appliedCommentsThemeVersion = state.themeRefreshVersion
            context?.let { currentContext ->
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
                linkPreviewController?.loadNetworkPreviews(context)
            CommentsPlatformEffect.Summarize -> requestComposeSummary()
            is CommentsPlatformEffect.OpenStory ->
                navigation.openStory(effect.destination)
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
            this.resources, (if (AndroidDisplay.isTablet(
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
            if (navigation.isAdaptiveTwoPane() ||
                commentsStore.state.value.settings?.transparentStatusBar == true
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
        updateWebViewContainerPadding()
    }

    private fun updateWebViewContainerPadding() {
        val upButtonInset = if (
            integratedWebview &&
            !navigation.isAdaptiveTwoPane() &&
            commentsStore.state.value.settings?.displaySettings?.showUpButton == true
        ) {
            AndroidDisplay.dpToPxInt(resources, 64f)
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

    val isBottomSheetFullyExpanded: Boolean
        get() = composeController != null && composeController!!.isSheetExpanded()

    fun switchStoryViewIfMatching(storyId: Int, showWebsite: Boolean): Boolean {
        if (!isAdded || !commentsStore.canSwitchStoryView(storyId) || webViewController == null) {
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
        if (this.context != null) refreshPresentationCapabilities()
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
                isTablet = AndroidDisplay.isTablet(resources),
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
        if (composeController != null && restoration != null) {
            syncComposeState()
            composeController!!.scrollToComment(
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
        val controllerToDetach = composeController
        val changingConfigurations =
            getActivity() != null && requireActivity().isChangingConfigurations()
        hostRestoration = captureHostRestoration(preserveOverlay = changingConfigurations)
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

        commentsStore.close()
        linkPreviewController?.dispose()
        coroutineScope.cancel()
        if (webViewController != null) {
            webViewController!!.onDestroyView(rootView)
        }
        if (controllerToDetach != null) {
            navigation.detachCommentsComposeController(controllerToDetach)
        }

        clearViewReferences()
        destroyed = true
    }

    private fun restoreLinkSummaryAfterRecreation() {
        val rootView = this.view
        val overlay = hostRestoration.overlay
        if (overlay is CommentsOverlayRestoration.Image && rootView != null) {
            hostRestoration = hostRestoration.copy(overlay = null)
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
                        overlay.url,
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
        if (overlay !is CommentsOverlayRestoration.Reference || referenceRootView == null) {
            return
        }
        hostRestoration = hostRestoration.copy(overlay = null)
        referenceRootView.post(Runnable {
            if (composeController != null) {
                composeController!!.showReferencePreview(overlay.url, overlay.fallbackTitle)
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

    private fun loadInitialStoryAndComments(restoreScrollFromCache: Boolean) {
        val context = this.context
        if (context == null || !this.isCommentsViewActive || story == null) {
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
                ) restoreScrollProgress()
                completeCommentsLoad(effect.headerChanged)
            }
            is CommentsRuntimeEffect.Diagnostic ->
                Log.w(TAG, effect.message, effect.cause)
            is CommentsRuntimeEffect.ActionFailed ->
                showActionFailure(effect.presentation)
        }
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
        }
        navigation.showMessage(presentation.message)
    }

    internal fun showMessage(
        message: String?,
        duration: com.simon.harmonichackernews.presentation.UserMessageDuration =
            com.simon.harmonichackernews.presentation.UserMessageDuration.SHORT,
    ) {
        navigation.showMessage(message, duration)
    }

    private fun completeCommentsLoad(updateHeaderAfterLoad: Boolean) {
        if (!this.isCommentsViewActive) {
            return
        }
        if (updateHeaderAfterLoad) syncComposeState()
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
        val pendingCommentActionId = hostRestoration.commentActionId
        if (pendingCommentActionId == -1) return
        val controller = composeController ?: return
        val comment = commentsStore.comment(pendingCommentActionId) ?: return
        hostRestoration = hostRestoration.copy(commentActionId = -1)
        controller.restoreCommentActions(comment)
        syncOnBackPressedCallbackEnabledState()
    }

    private val isCommentsViewActive: Boolean
        get() = this.view != null

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
