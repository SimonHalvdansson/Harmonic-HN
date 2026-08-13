package com.simon.harmonichackernews

import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.StoriesFeatureHost
import com.simon.harmonichackernews.app.createStoriesFeatureSession
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.StoriesPlatformDependencies
import com.simon.harmonichackernews.presentation.StoriesRuntimeEffect
import com.simon.harmonichackernews.presentation.StoriesPlatformEffect
import com.simon.harmonichackernews.navigation.EditorDestination
import com.simon.harmonichackernews.navigation.EditorType
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.ui.navigation.MainNavigationController
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesComposeController.Companion.create
import com.simon.harmonichackernews.ui.stories.StoriesPlatformPresentation
import com.simon.harmonichackernews.ui.stories.StoriesScreenStateFactory
import com.simon.harmonichackernews.ui.stories.StoriesFeatureListener
import com.simon.harmonichackernews.utils.FontUtils
import com.simon.harmonichackernews.utils.PreviewImageTintUtils
import com.simon.harmonichackernews.utils.StoryUpdate
import com.simon.harmonichackernews.utils.StoryUpdate.StoryUpdateListener
import com.simon.harmonichackernews.utils.AndroidStoryCache
import java.util.Date
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class StoriesCoordinator(
    private val activity: MainActivity,
    savedInstanceState: Bundle?,
    private val navigation: MainNavigationController,
    private val appComposition: HarmonicAppComposition = AndroidAppComposition.get(activity),
    private val userSettings: UserSettings = appComposition.userSettings,
    platformDependencies: StoriesPlatformDependencies =
        appComposition.storiesPlatformDependencies(),
    private val clock: Clock = Clock.System,
) {
    private val connectivity = platformDependencies.connectivity
    private val externalLinks = platformDependencies.externalLinks
    private val historyStore = platformDependencies.history
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val savedItems = appComposition.savedItems
    private val storiesViewModel = ViewModelProvider(activity)[StoriesViewModel::class.java]
    private val sessionState = storiesViewModel.state
    private var started = false
    private var destroyed = false
    var composeController: StoriesComposeController? = null
        private set
    private var storyUpdateListener: StoryUpdateListener? = null
    private val featureSession = appComposition.createStoriesFeatureSession(
        StoriesFeatureHost(
        scope = coroutineScope,
        sessionState = sessionState,
        platform = platformDependencies,
        userSettings = userSettings,
        nowMillis = { clock.now().toEpochMilliseconds() },
        hydrateCachedStory = { story -> AndroidStoryCache.hydrate(context, story) },
        loadCachedStories = { AndroidStoryCache.recentStories(activity) },
        hasCachedStories = { AndroidStoryCache.hasRecentStories(activity) },
        ),
    )
    private val storiesPresenter = featureSession.presenter
    private val storiesFeature = featureSession.runtime
    private var restoredStateForCurrentView = false
    private var storyCacheController: StoryCacheController? = null
    private var linkSummaryBackCallback: OnBackPressedCallback? = null
    private var pendingLinkSummaryStoryId: Int = NO_PENDING_LINK_SUMMARY_STORY_ID

    private var appliedFontRefreshVersion = -1L
    private val searching: Boolean
        get() = storiesPresenter.state.value.searching
    init {
        initializeComposeController(savedInstanceState)
    }

    private val context: Context?
        get() = if (destroyed) null else activity

    private val resources: Resources
        get() = activity.resources

    private fun initializeComposeController(savedInstanceState: Bundle?) {
        if (composeController != null) {
            return
        }
        if (savedInstanceState != null) {
            pendingLinkSummaryStoryId = savedInstanceState.getInt(
                STATE_LINK_SUMMARY_STORY_ID, NO_PENDING_LINK_SUMMARY_STORY_ID
            )
        }

        storiesFeature.initializeHistory()
        val restoringSession = sessionState.initialized
        setupLinkSummaryBackCallback()
        storyCacheController = createStoryCacheController()

        setupStoryResources()
        restoredStateForCurrentView = restoringSession
        initializeComposeUi()
        coroutineScope.launch {
            storiesFeature.effects.collect(::handleStoriesRuntimeEffect)
        }
        coroutineScope.launch {
            storiesFeature.settingsState.collect(::applyPlatformSettingsState)
        }
        coroutineScope.launch {
            savedItems.changes.collect { change ->
                storiesFeature.notifySavedItemsChanged(change.source)
                if (storiesFeature.refreshBookmarksIfNeeded(started)) syncComposeState()
            }
        }
        sessionState.initialized = true

        if (restoredStateForCurrentView) {
            syncComposeState()
            if (storiesFeature.shouldRefreshRestoredState()) {
                storiesFeature.refresh(showSwipeRefreshIndicator = false)
            } else if (!searching) {
                storiesFeature.resumeRetainedLoads()
            }
        } else {
            storiesFeature.refresh(showSwipeRefreshIndicator = false)
        }

        storyUpdateListener = StoryUpdateListener { story: Story? ->
            if (story == null) {
                return@StoryUpdateListener
            }
            storiesFeature.mergeExternalStoryUpdate(story)
        }
        StoryUpdate.setStoryUpdatedListener(storyUpdateListener)
        restoreLinkSummaryAfterRecreation()
    }

    private fun createStoryCacheController(): StoryCacheController {
        return StoryCacheController(object : StoryCacheController.Callbacks {
            override val context: Context?
                get() = this@StoriesCoordinator.context

            override fun onCacheProgressChanged() {
                this@StoriesCoordinator.syncComposeState()
            }
        })
    }

    private fun initializeComposeUi() {
        if (composeController != null) return
        val platformCallbacks = object : StoriesFeatureListener.PlatformCallbacks {
            override val hostStarted: Boolean get() = started

            override fun onSearchStateChanged(searching: Boolean) {
                composeController?.endPredictiveBack()
                syncComposeState()
            }
            override fun showFrontDatePicker() {
                composeController?.showFrontDatePicker(
                    storiesFeature.frontPageDay.selectedMillis,
                    storiesFeature.frontPageDay.earliestMillis,
                    storiesFeature.frontPageDay.latestMillis,
                )
            }
            override fun onStoryPreviewVisibilityChanged(showing: Boolean) {
                linkSummaryBackCallback?.isEnabled = showing
            }

            override fun isSplitLayout(): Boolean = isFoldableSplitLayout
        }
        composeController = create(
            (96f * resources.displayMetrics.density).roundToInt(),
            storiesFeature.savedItemActions,
            StoriesFeatureListener(storiesFeature, platformCallbacks),
        )
        navigation.attachStoriesComposeController(composeController!!)
        syncComposeState()
    }

    private fun handleStoriesPlatformEffect(effect: StoriesPlatformEffect) {
        when (effect) {
            StoriesPlatformEffect.OpenSettings ->
                navigation.openSettings(null)
            StoriesPlatformEffect.RequestLogin -> navigation.showLoginDialog()
            is StoriesPlatformEffect.OpenProfile ->
                navigation.showUserDialog(effect.userName, null)
            StoriesPlatformEffect.ShowCacheDialog -> navigation.showCacheStoriesDialog()
            StoriesPlatformEffect.OpenSubmitEditor -> navigation.openEditor(
                EditorDestination(type = EditorType.POST),
            )
        }
        syncComposeState()
    }

    private fun handleStoriesRuntimeEffect(effect: StoriesRuntimeEffect) {
        when (effect) {
            is StoriesRuntimeEffect.OpenStory -> navigation.openStory(effect.destination)
            is StoriesRuntimeEffect.OpenExternalLink ->
                externalLinks.open(ExternalLinkRequest(effect.url))
            is StoriesRuntimeEffect.Platform -> handleStoriesPlatformEffect(effect.effect)
            is StoriesRuntimeEffect.PreviewActionCompleted ->
                composeController?.finishStoryPreviewAction(effect.storyId, effect.action)
            is StoriesRuntimeEffect.StoryChanged -> {
                val changedStory = effect.story
                if (changedStory == null) syncComposeState()
                else composeController?.invalidateStory(changedStory.id)
            }
            is StoriesRuntimeEffect.CacheStories ->
                storyCacheController?.cacheStories(effect.request)
            StoriesRuntimeEffect.LoginRequired -> navigation.showLoginDialog()
            is StoriesRuntimeEffect.UserMessage ->
                navigation.showMessage(effect.message)
            is StoriesRuntimeEffect.SavedActionFailed -> {
                if (effect.presentation.showDetails) {
                    navigation.showFailureDetailDialog(
                        effect.presentation.failureSummary,
                        effect.presentation.failureDetail,
                        null,
                    )
                }
                navigation.showMessage(effect.presentation.message)
            }
        }
    }

    private fun syncComposeState() {
        val controller = composeController ?: return
        val context = context ?: return
        val cache = storyCacheController
        val lastUpdated = storiesFeature.lastUpdatedMillisForHeader()?.let { millis ->
            "Last updated: " + DateFormat.getTimeFormat(context).format(Date(millis))
        }
        controller.updateContent(
            StoriesScreenStateFactory.create(
                feature = storiesFeature,
                platform = StoriesPlatformPresentation(
                    searchSortLabels = StorySearchController.sortLabels.toList(),
                    searchDateLabels = StorySearchController.dateRangeLabels.toList(),
                    searchPointsLabels = StorySearchController.minimumPointsLabels.toList(),
                    searchCommentsLabels = StorySearchController.minimumCommentsLabels.toList(),
                    lastUpdatedText = lastUpdated,
                    cacheInProgress = cache?.isCachingStories == true,
                    cacheProgressVisible = cache?.isProgressVisible == true,
                    cacheProgress = cache?.progress ?: 0,
                    cacheProgressMax = cache?.progressMax ?: 1,
                    cacheProgressStatus = cache?.getProgressStatus() ?: "Caching stories",
                    contentInsetStartPx = splitStoriesContentPaddingStart,
                    previewResources = storiesFeature.previewResourceStates,
                ),
            ),
        )
    }

    private fun restoreLinkSummaryAfterRecreation() {
        if (pendingLinkSummaryStoryId == NO_PENDING_LINK_SUMMARY_STORY_ID) {
            return
        }
        val storyId = pendingLinkSummaryStoryId
        pendingLinkSummaryStoryId = NO_PENDING_LINK_SUMMARY_STORY_ID
        activity.window.decorView.post {
            if (composeController == null) {
                return@post
            }
            storiesFeature.previewStory(storyId)?.let { story ->
                storiesFeature.previewDeck(
                    story.id,
                    PreviewImageTintUtils.getTintBaseColor(activity),
                )?.let { composeController?.showStoryPreview(it) }
            }
        }
    }

    private val splitStoriesContentPaddingStart: Int
        get() = resources.getDimensionPixelSize(R.dimen.extra_pane_padding)

    private fun setupStoryResources() {
        storiesFeature.storyResources?.setResourceChangedListener(::syncComposeState)
        storiesFeature.initialize(restoring = sessionState.initialized)
    }

    private fun setupLinkSummaryBackCallback() {
        if (linkSummaryBackCallback != null) {
            return
        }
        linkSummaryBackCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackCancelled() {
                if (composeController != null && composeController!!.isStoryPreviewShowing()) {
                    composeController!!.cancelStoryPreviewPredictiveBack()
                }
            }

            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (composeController != null && composeController!!.isStoryPreviewShowing()) {
                    composeController!!.updateStoryPreviewPredictiveBack(
                        backEvent.progress, backEvent.swipeEdge, backEvent.touchY
                    )
                }
            }

            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                if (composeController != null && composeController!!.isStoryPreviewShowing()) {
                    composeController!!.startStoryPreviewPredictiveBack(
                        backEvent.progress, backEvent.swipeEdge, backEvent.touchY
                    )
                }
            }

            override fun handleOnBackPressed() {
                if (composeController == null || !composeController!!.isStoryPreviewShowing()) {
                    return
                }
                if (composeController!!.isStoryPreviewPredictiveBackActive()) {
                    composeController!!.commitStoryPreviewPredictiveBack()
                } else {
                    composeController!!.requestDismissStoryPreview()
                }
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, linkSummaryBackCallback!!)
    }

    private val isFoldableSplitLayout: Boolean
        get() = !destroyed && navigation.isAdaptiveFoldable()

    fun onStart() {
        started = true
        if (storiesFeature.refreshBookmarksIfNeeded(hostStarted = true)) syncComposeState()
    }

    fun onStop() {
        started = false
    }

    fun onResume() {
        storiesFeature.resume(hostStarted = started)
        syncComposeState()
    }

    /** Executes only Android facilities selected by the shared settings reconciler. */
    private fun applyPlatformSettingsState(
        state: com.simon.harmonichackernews.presentation.StoriesSettingsState,
    ) {
        if (state.fontRefreshVersion != appliedFontRefreshVersion) {
            appliedFontRefreshVersion = state.fontRefreshVersion
            FontUtils.init(activity)
        }
        syncComposeState()
    }

    fun onSaveInstanceState(outState: Bundle) {
        val visibleStoryId = if (composeController == null)
            NO_PENDING_LINK_SUMMARY_STORY_ID
        else
            composeController!!.visibleStoryPreviewId
        if (visibleStoryId != NO_PENDING_LINK_SUMMARY_STORY_ID) {
            outState.putInt(STATE_LINK_SUMMARY_STORY_ID, visibleStoryId)
        }
    }

    fun onDestroy() {
        if (destroyed) return
        if (composeController != null) {
            composeController!!.completeStoryPreviewDismiss()
        }
        if (linkSummaryBackCallback != null) {
            linkSummaryBackCallback!!.remove()
            linkSummaryBackCallback = null
        }
        if (storyUpdateListener != null) {
            StoryUpdate.clearStoryUpdatedListener(storyUpdateListener)
        }
        storiesFeature.storyResources?.setResourceChangedListener(null)
        storiesFeature.dispose()
        coroutineScope.cancel()
        clearControllerReferences()
        started = false
        destroyed = true
    }

    private fun clearControllerReferences() {
        val controller = composeController
        if (controller != null) {
            navigation.detachStoriesComposeController(controller)
        }
        if (storyCacheController != null) {
            storyCacheController!!.dispose()
            storyCacheController = null
        }
        composeController = null
        storyUpdateListener = null
    }

    companion object {
        private const val NO_PENDING_LINK_SUMMARY_STORY_ID = -1
        private const val STATE_LINK_SUMMARY_STORY_ID =
            "com.simon.harmonichackernews.STATE_LINK_SUMMARY_STORY_ID"

    }
}
