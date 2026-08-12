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
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.webkit.WebViewFeature
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.toBundle
import com.simon.harmonichackernews.linkpreview.LinkPreviewController
import com.simon.harmonichackernews.network.CommentThreadRepository
import com.simon.harmonichackernews.network.AndroidLocalStorySummaryBackend
import com.simon.harmonichackernews.network.LocalSummaryManager
import com.simon.harmonichackernews.network.LinkPreviewUseCase
import com.simon.harmonichackernews.network.failureDetails
import com.simon.harmonichackernews.network.showLoginPromptIfCredentialsMissing
import com.simon.harmonichackernews.settings.AndroidAiSummarySettings
import com.simon.harmonichackernews.summary.CloudStorySummaryBackend
import com.simon.harmonichackernews.summary.StorySummaryRuntime
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toStory
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.CommentsPlatformDependencies
import com.simon.harmonichackernews.platform.AndroidStoryPreviewResourceService
import com.simon.harmonichackernews.presentation.ArchiveUrlResolver
import com.simon.harmonichackernews.presentation.CommentTargetResolution
import com.simon.harmonichackernews.presentation.CommentsFeatureRuntime
import com.simon.harmonichackernews.presentation.CommentsPresenter
import com.simon.harmonichackernews.presentation.CommentsPlatformEffect
import com.simon.harmonichackernews.presentation.CommentsPresentationCapabilities
import com.simon.harmonichackernews.presentation.CommentsRuntimeEffect
import com.simon.harmonichackernews.presentation.CommentsSettingsState
import com.simon.harmonichackernews.presentation.SavedItemActionUseCase
import com.simon.harmonichackernews.ui.comments.CommentsComposeController
import com.simon.harmonichackernews.ui.comments.CommentsPlatformPresentation
import com.simon.harmonichackernews.ui.comments.CommentsScreenStateFactory
import com.simon.harmonichackernews.ui.comments.CommentsFeatureListener
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
    private val appComposition: HarmonicAppComposition = AndroidAppComposition.get(activity),
    private val platformDependencies: CommentsPlatformDependencies =
        appComposition.commentsPlatformDependencies(),
    userSettings: UserSettings = appComposition.userSettings,
    private val clock: Clock = Clock.System,
) {
    private val externalLinks = platformDependencies.externalLinks
    private val contentFilters = appComposition.contentFilters
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hackerNewsUserService = appComposition.hackerNewsUser
    private val savedItemActions = SavedItemActionUseCase(
        repository = appComposition.savedItems,
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
    private var started = false
    private var destroyed = false
    private val commentsPresenter = CommentsPresenter(
        coroutineScope,
        sessionState,
        CommentThreadRepository(
            appComposition.network.algoliaRepository,
            appComposition.network.hackerNewsRepository,
        ),
        appComposition.network.pollOptionsRepository,
        savedItemActions,
        hackerNewsUserService,
    )
    private val aiSummarySettings = AndroidAiSummarySettings.repository(activity)
    private val storySummaryRuntime = StorySummaryRuntime(
        scope = coroutineScope,
        cloudBackend = CloudStorySummaryBackend(appComposition.network.summaryUseCase) {
            aiSummarySettings.cloudConfig()
        },
        localBackend = AndroidLocalStorySummaryBackend(
            activity,
            appComposition.network.summaryUseCase,
        ),
    )
    private val commentsFeature = CommentsFeatureRuntime(
        scope = coroutineScope,
        sessionState = sessionState,
        presenter = commentsPresenter,
        nowMillis = { clock.now().toEpochMilliseconds() },
        archiveUrlResolver = ArchiveUrlResolver(appComposition.network.linkPreviewRepository),
        userSettings = userSettings,
        loadContentFilters = contentFilters::load,
        accounts = platformDependencies.accounts,
        summarySettings = aiSummarySettings,
        localSummaryAvailable = LocalSummaryManager::canAttemptLocalSummarization,
        summaryRuntime = storySummaryRuntime,
        hydrateCachedStory = { cachedStory ->
            Utils.loadCachedStorySummary(activity, cachedStory)
        },
        loadCachedThread = { storyId -> Utils.loadCachedStory(activity, storyId) },
        storeCachedThread = { storyId, response ->
            Utils.cacheStory(activity, storyId, response)
        },
        previewResourceService = AndroidStoryPreviewResourceService { context },
        storyResourceTints = appComposition.storyResourceTints,
    )
    private var webViewHost: CommentsWebViewHost?
    private var commentsContentInsetLeft = 0
    private var commentsContentInsetRight = 0
    private var pendingCommentActionId = -1
    private var pendingReferenceLinkSummaryUrl: String? = null
    private var pendingReferenceLinkSummaryTitle: String? = null
    private var pendingPreviewImageDialogUrl: String? = null
    private var progressIndicator: LinearProgressIndicator? = null
    private var linkPreviewController: LinkPreviewController? = null
    private var webViewController: CommentsWebViewController? = null
    private var showWebsite by sessionState::showWebsite
    private var integratedWebview = true
    private var adBlockDisabledForSession = false
    private var topInset = 0
    private val commentsLoaded: Boolean
        get() = commentsPresenter.state.value.loaded
    private var appliedCommentsThemeVersion = -1L
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

    init {
        coroutineScope.launch {
            commentsFeature.effects.collect(::handleCommentsRuntimeEffect)
        }
        coroutineScope.launch {
            commentsFeature.settingsState.collect { it?.let(::applyPlatformSettingsState) }
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

    fun startActivity(intent: Intent) {
        activity.startActivity(intent)
    }

    private fun initializeView(savedInstanceState: Bundle?) {
        val view = this.webViewRoot
        val restoredSorting = savedInstanceState?.getString(STATE_COMMENT_SORTING)
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
        }

        commentsFeature.initializeFromSettings(
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
        val featureSettings = checkNotNull(commentsFeature.settingsState.value)
        val readingPreferences = featureSettings.reading
        integratedWebview = featureSettings.integratedWebView
        val blockAds = readingPreferences.blockAds && !adBlockDisabledForSession

        progressIndicator = host.progressIndicator
        linkPreviewController = LinkPreviewController(
            story,
            LinkPreviewUseCase(appComposition.network.linkPreviewRepository),
            LinkPreviewController.Callbacks(::syncComposeState),
        )
        webViewController = CommentsWebViewController(
            this,
            story,
            linkPreviewController!!,
            object : CommentsWebViewController.Callbacks {
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
            readingPreferences.preloadWebViewMode,
            readingPreferences.preloadWebViewMinimumBattery,
            readingPreferences.matchWebViewTheme,
            readingPreferences.readerModeEnabled,
            readingPreferences.readerModeDefault,
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
                if (expandedFraction < 0.999f && integratedWebview &&
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
        composeController = CommentsComposeController.create(
            { commentsFeature.settingsState.value?.smoothScroll ?: true },
            currentStory,
            showWebsite,
            commentsFeature.accountUser,
            commentsPresenter.savedItemState,
            CommentsFeatureListener(commentsFeature, platformCallbacks),
        )
        activity.attachCommentsComposeController(composeController!!)
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
            commentsFeature.captureCollapsedComments()
        }
        val readerAvailable =
            webViewController != null && webViewController!!.isReaderModeAvailable
        val readerEnabled = webViewController != null && webViewController!!.isReaderModeEnabled()
        CommentsScreenStateFactory.create(
            feature = commentsFeature,
            platform = CommentsPlatformPresentation(
                adBlockActive = webViewController?.isBlockingAds == true,
                readerModeAvailable = readerAvailable,
                readerModeEnabled = readerEnabled,
                topInsetPx = topInset,
                contentInsetLeftPx = commentsContentInsetLeft,
                contentInsetRightPx = commentsContentInsetRight,
            ),
        )?.let(controller::updateContent)
    }

    private fun requestComposeSummary() {
        val beginSummary: (String?) -> Unit = commentsFeature::startSummary
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
                reading.preloadWebViewMode,
                reading.preloadWebViewMinimumBattery,
                reading.matchWebViewTheme,
                reading.readerModeEnabled,
                reading.readerModeDefault,
                reading.blockAds && !adBlockDisabledForSession,
            )
            if (integratedWebview && !wasIntegrated) controller.initialize()
        }
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
            is CommentsPlatformEffect.OpenUser -> requireActivity().showUserDialog(
                effect.userName,
                Runnable { commentsFeature.reconcileSettings() },
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
                platformDependencies.sharing.share(effect.text)
            is CommentsPlatformEffect.CopyText -> {
                platformDependencies.clipboard.copy(effect.label, effect.text)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(
                        requireContext(),
                        "Text copied to clipboard",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            CommentsPlatformEffect.ReloadLinkPreviews ->
                linkPreviewController?.loadNetworkPreviews(context)
            CommentsPlatformEffect.Summarize -> requestComposeSummary()
            is CommentsPlatformEffect.OpenStory ->
                activity.openStory(effect.destination)
            is CommentsPlatformEffect.OpenExternalLink -> externalLinks.open(
                ExternalLinkRequest(effect.url, preferInApp = effect.preferInApp),
            )
            CommentsPlatformEffect.ShowSearch -> composeController?.showCommentSearch()
            CommentsPlatformEffect.DisableAdBlock -> {
                adBlockDisabledForSession = true
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
            commentsFeature.settingsState.value?.reading?.closeWebViewOnBack == true ->
                webViewVisible
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
                commentsFeature.settingsState.value?.transparentStatusBar == true
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
        if (!isAdded || !commentsFeature.canSwitchStoryView(storyId) || webViewController == null) {
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
        commentsFeature.updatePresentationCapabilities(
            CommentsPresentationCapabilities(
                showInvertAction = shouldShowInvertAction(),
                isTablet = Utils.isTablet(resources),
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
        commentsFeature.resume()
        syncComposeState()
    }

    fun onResume() {
        if (destroyed) return

        commentsFeature.resume()
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
        outState.putString(STATE_COMMENT_SORTING, commentsFeature.thread.state.value.sorting)
    }

    private fun restoreScrollProgress() {
        val restoration = commentsFeature.restoreScrollProgress()
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
        when (val target = commentsFeature.consumeCommentTarget()) {
            CommentTargetResolution.None -> Unit
            is CommentTargetResolution.Found -> {
                syncComposeState()
                composeController?.scrollToComment(target.commentId, topInset, false)
            }
            is CommentTargetResolution.NotFound -> Toast.makeText(
                context,
                "Comment not found",
                Toast.LENGTH_SHORT,
            ).show()
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

        commentsFeature.dispose()
        linkPreviewController?.dispose()
        coroutineScope.cancel()
        if (webViewController != null) {
            webViewController!!.onDestroyView(rootView)
        }
        if (controllerToDetach != null) {
            activity.detachCommentsComposeController(controllerToDetach)
        }

        clearViewReferences()
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

    private fun loadInitialStoryAndComments(restoreScrollFromCache: Boolean) {
        val context = this.context
        if (context == null || !this.isCommentsViewActive || story == null) {
            return
        }

        commentsFeature.loadInitial(restoreScrollFromCache)
    }

    private fun handleCommentsRuntimeEffect(effect: CommentsRuntimeEffect) {
        when (effect) {
            is CommentsRuntimeEffect.Platform -> handleCommentsPlatformEffect(effect.effect)
            is CommentsRuntimeEffect.StateChanged -> syncComposeState()
            is CommentsRuntimeEffect.ShowCommentActions ->
                composeController?.showCommentActions(effect.comment)
            is CommentsRuntimeEffect.BroadcastStoryUpdate ->
                StoryUpdate.updateStory(effect.story)
            is CommentsRuntimeEffect.ThreadReady -> {
                if (!isCommentsViewActive) return
                commentsFeature.settingsState.value?.let(::applyPlatformSettingsState)
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
        val context = context ?: return
        if (presentation.requestLoginIfMissing) {
            presentation.result.showLoginPromptIfCredentialsMissing(context)
        }
        val (summary, response) = presentation.result.failureDetails()
        if (presentation.showDetails) requireActivity().showFailureDetailDialog(summary, response)
        Toast.makeText(
            context,
            presentation.message,
            Toast.LENGTH_SHORT,
        ).show()
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
        if (pendingCommentActionId == -1) return
        val controller = composeController ?: return
        val comment = commentsFeature.comment(pendingCommentActionId) ?: return
        pendingCommentActionId = -1
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
        // Keep WebView startup clear of the comments entrance transition. WebView process and
        // renderer initialization can otherwise land on the same frames as the shared transition
        // on physical devices, which makes opening a story feel much heavier than it is.
        private const val WEBVIEW_BACKGROUND_INITIALIZATION_DELAY_MS = 900L
    }
}
