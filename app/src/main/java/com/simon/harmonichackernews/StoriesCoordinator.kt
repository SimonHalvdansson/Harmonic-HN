package com.simon.harmonichackernews

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.SavedItemKeys
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.failureDetails
import com.simon.harmonichackernews.network.StoryFeedRepository
import com.simon.harmonichackernews.platform.AndroidPlatformServices
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.PlatformServices
import com.simon.harmonichackernews.presentation.StoriesSessionState
import com.simon.harmonichackernews.presentation.StoriesAction
import com.simon.harmonichackernews.presentation.StoriesFeatureRuntime
import com.simon.harmonichackernews.presentation.StoriesRuntimeEffect
import com.simon.harmonichackernews.presentation.StoriesPresenter
import com.simon.harmonichackernews.presentation.SavedItemActionUseCase
import com.simon.harmonichackernews.presentation.StoryListStore
import com.simon.harmonichackernews.presentation.StoryHistorySyncResult
import com.simon.harmonichackernews.presentation.StoryListTarget
import com.simon.harmonichackernews.presentation.CommentMasterResolver
import com.simon.harmonichackernews.presentation.StoryPaginationPolicy
import com.simon.harmonichackernews.presentation.StoryVisibilityConfig
import com.simon.harmonichackernews.presentation.StoryVisibilityPolicy
import com.simon.harmonichackernews.presentation.StoryRowMergePolicy
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.presentation.StoriesPlatformEffect
import com.simon.harmonichackernews.presentation.StoriesUiOrchestrator
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.ui.settings.SettingsIntents.create
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesComposeController.Companion.create
import com.simon.harmonichackernews.ui.stories.StoriesPlatformPresentation
import com.simon.harmonichackernews.ui.stories.StoriesScreenStateFactory
import com.simon.harmonichackernews.ui.stories.StoriesFeatureListener
import com.simon.harmonichackernews.ui.stories.AndroidStoryListResources
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.FontUtils
import com.simon.harmonichackernews.utils.StoryUpdate
import com.simon.harmonichackernews.utils.StoryUpdate.StoryUpdateListener
import com.simon.harmonichackernews.utils.Utils
import java.util.Date
import kotlin.math.max
import kotlin.math.min
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
    private val userSettings: UserSettings = AndroidUserSettings(activity),
    private val platformServices: PlatformServices = AndroidPlatformServices.create(activity),
    private val hackerNewsApi: HackerNewsApi = NetworkComponent.hackerNewsApi,
    private val hackerNewsRepository: HackerNewsRepository =
        NetworkComponent.hackerNewsRepository,
    private val algoliaRepository: AlgoliaRepository = NetworkComponent.algoliaRepository,
    private val storyFeedRepository: StoryFeedRepository = StoryFeedRepository(
        hackerNewsRepository,
        NetworkComponent.hackerNewsWebRepository,
    ),
    private val clock: Clock = Clock.System,
) {
    private val connectivity = platformServices.connectivity
    private val externalLinks = platformServices.externalLinks
    private val historyStore = platformServices.history
    private val contentFilters = ContentFilterRepository(AndroidKeyValueStore.defaults(activity))
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hackerNewsUserService = HackerNewsUserService(
        NetworkComponent.hackerNewsSession,
        platformServices.credentials,
    )
    private val savedItems = SavedItemsRepository(AndroidKeyValueStore.global(activity))
    private val commentMasterResolver = CommentMasterResolver(hackerNewsRepository)
    private val savedItemActions = SavedItemActionUseCase(
        repository = savedItems,
        nowMillis = { clock.now().toEpochMilliseconds() },
        voteRequest = { id, direction ->
            hackerNewsUserService.vote(id.toString(), direction)
        },
        favoriteRequest = hackerNewsUserService::setFavorite,
    )
    private val storiesViewModel = ViewModelProvider(activity)[StoriesViewModel::class.java]
    private val sessionState = storiesViewModel.state
    private var storyClickListener: StoryClickListener?
    private var started = false
    private var destroyed = false
    var composeController: StoriesComposeController? = null
        private set
    private var storyUpdateListener: StoryUpdateListener? = null
    private val storiesPresenter = StoriesPresenter(
        scope = coroutineScope,
        sessionState = sessionState,
        algoliaRepository = algoliaRepository,
        hackerNewsRepository = hackerNewsRepository,
        hackerNewsApi = hackerNewsApi,
        userItemsLoader = hackerNewsUserService,
        savedItemsRepository = savedItems,
        storyFeedLoader = storyFeedRepository,
        clickedStoryIds = { historyStore.load().map { it.id } },
        isStoryClicked = historyStore::contains,
        shouldFilterStory = ::shouldFilterLoadedStory,
        shouldHideClickedStories = { userSettings.story.hideClicked },
    )
    private val storiesFeature = StoriesFeatureRuntime(
        scope = coroutineScope,
        sessionState = sessionState,
        presenter = storiesPresenter,
        savedItems = savedItems,
        savedItemActions = savedItemActions,
        historyStore = historyStore,
        commentMasterResolver = commentMasterResolver,
        nowMillis = { clock.now().toEpochMilliseconds() },
        hydrateCachedStory = { story -> Utils.loadCachedStorySummary(context, story) },
        shouldFilterStory = { story, type -> storyVisibilityPolicy.shouldHide(story, type) },
        hasAccountDetails = { context?.let(AccountUtils::hasAccountDetails) == true },
    )
    private var restoredStateForCurrentView = false
    private var storyCacheController: StoryCacheController? = null
    private var linkSummaryBackCallback: OnBackPressedCallback? = null
    private var pendingLinkSummaryStoryId: Int = NO_PENDING_LINK_SUMMARY_STORY_ID

    private var storyResources: AndroidStoryListResources? = null
    private val mainStoryListStore = storiesPresenter.mainStoryList
    private val searchStoryListStore = storiesPresenter.searchStoryList
    private val mainStories = mainStoryListStore.stories
    private val searchStories = searchStoryListStore.stories
    private var stories: MutableList<Story>? = null
    private val storyVisibilityPolicy = StoryVisibilityPolicy()
    private var historiesChangeVersion = -1L
    private var bookmarkPreferences: SharedPreferences?
    private var bookmarksChanged = false
    private val bookmarkPreferenceChangeListener =
        OnSharedPreferenceChangeListener { sharedPreferences: SharedPreferences?, key: String? ->
            if (SavedItemKeys.BOOKMARKS == key) {
                bookmarksChanged = true
                refreshBookmarksIfNeeded()
            }
        }
    private val searching: Boolean
        get() = storiesPresenter.state.value.searching
    var lastLoaded by sessionState::lastLoaded
    private val updateButtonShowing: Boolean
        get() = storiesPresenter.state.value.updateAvailable
    private var predictiveSearchBackInProgress = false
    private var predictiveSearchBackProgress = 0f
    private var finishSearchBackFromCurrentVisualState = false
    private var userItemListsDropdownVisible = false
    init {
        storyClickListener = activity
        bookmarkPreferences = activity.getSharedPreferences(
            Utils.GLOBAL_SHARED_PREFERENCES_KEY,
            Context.MODE_PRIVATE
        )
        bookmarkPreferences!!.registerOnSharedPreferenceChangeListener(
            bookmarkPreferenceChangeListener
        )
        initializeComposeController(savedInstanceState)
    }

    private val context: Context?
        get() = if (destroyed) null else activity

    private fun requireActivity(): MainActivity {
        return activity
    }

    private fun requireContext(): Context {
        return activity
    }

    private val resources: Resources
        get() = activity.getResources()

    private val isAdded: Boolean
        get() = !destroyed

    private fun startActivity(intent: Intent) {
        activity.startActivity(intent)
    }

    private fun initializeComposeController(savedInstanceState: Bundle?) {
        if (composeController != null) {
            return
        }
        if (savedInstanceState != null) {
            pendingLinkSummaryStoryId = savedInstanceState.getInt(
                STATE_LINK_SUMMARY_STORY_ID, NO_PENDING_LINK_SUMMARY_STORY_ID
            )
        }

        historyStore.initialize()
        historiesChangeVersion = historyStore.changeVersion
        val restoringSession = sessionState.initialized
        setupLinkSummaryBackCallback()
        storyCacheController = createStoryCacheController()

        stories = mainStories
        val storyPreferences = userSettings.story
        updateStoryVisibilityPolicy(storyPreferences.hideJobs)
        storiesFeature.configure(
            pagination = storyPreferences.pagination,
            hideClicked = storyPreferences.hideClicked,
            alwaysOpenComments = storyPreferences.alwaysOpenComments,
            useIntegratedWebView = userSettings.reading.integratedWebView,
        )
        userItemListsDropdownVisible = shouldShowUserItemLists(requireContext())
        setupStoryResources()
        registerStoryResourceListeners()
        restoredStateForCurrentView = restoringSession && restoreStoryStateAfterViewSetup(sessionState)
        initializeComposeUi()
        coroutineScope.launch {
            storiesFeature.effects.collect(::handleStoriesRuntimeEffect)
        }
        sessionState.initialized = true

        if (restoredStateForCurrentView) {
            updateHeader()
            if (shouldRefreshRestoredStoryState()) {
                attemptRefresh()
            } else if (!searching) {
                storiesFeature.resumeRetainedLoads()
            }
        } else {
            attemptRefresh()
        }

        storyUpdateListener = StoryUpdateListener { story: Story? ->
            if (story == null || stories == null || storyResources == null) {
                return@StoryUpdateListener
            }
            stories!!.firstOrNull { it.id == story.id }
                ?.let { StoryRowMergePolicy.mergeSummaryFields(it, story) }
            syncComposeState()
        }
        StoryUpdate.setStoryUpdatedListener(storyUpdateListener)
        restoreLinkSummaryAfterRecreation()
    }

    private fun createStoryCacheController(): StoryCacheController {
        return StoryCacheController(object : StoryCacheController.Callbacks {
            override val context: Context?
                get() = this@StoriesCoordinator.context

            override val userSettings
                get() = this@StoriesCoordinator.userSettings

            override fun onCacheProgressChanged() {
                this@StoriesCoordinator.syncComposeState()
            }
        })
    }

    private fun initializeComposeUi() {
        if (composeController != null) return
        val platformCallbacks = object : StoriesFeatureListener.PlatformCallbacks {
            override fun storyTypeAt(index: Int) = getStoryType(index)

            override fun onActiveListChanged(searching: Boolean) {
                finishSearchBackFromCurrentVisualState = false
                predictiveSearchBackInProgress = false
                predictiveSearchBackProgress = 0f
                if (searching) useSearchStoryList() else useMainStoryList()
                activity.setSearchBackEnabled(searching)
                if (!searching) refreshBookmarksIfNeeded()
                syncComposeState()
            }

            override fun showCachedStories() = this@StoriesCoordinator.showCachedStories()
            override fun showFrontDatePicker() = this@StoriesCoordinator.showFrontPageDatePicker()
            override fun onMoreAction(action: StoriesMenuAction) {
                StoriesUiOrchestrator.menu(
                    action,
                    AccountUtils.getAccountUsername(activity),
                )?.let(::handleStoriesPlatformEffect)
            }

            override fun cacheStories(storyCount: Int) {
                userSettings.setStoriesToCache(storyCount)
                storyCacheController?.cacheStories()
            }

            override fun showStoryPreview(story: Story) {
                if (stories?.contains(story) == true) showComposeStoryPreview(story)
            }

            override fun onVisibleStoryRange(lastVisibleIndex: Int) {
                prefetchVisibleStoryImages(lastVisibleIndex)
            }

            override fun onStoryPreviewVisibilityChanged(showing: Boolean) {
                linkSummaryBackCallback?.isEnabled = showing
            }

            override fun onStoryPreviewNavigate(
                story: Story,
                position: Int,
                showWebsite: Boolean,
            ): Boolean {
                openStoryFromLinkSummary(story, position, showWebsite)
                return isFoldableSplitLayout
            }

            override fun onStoryPreviewAction(
                story: Story,
                position: Int,
                action: StoryPreviewActionKind,
            ) {
                handleStoriesPlatformEffect(StoriesUiOrchestrator.preview(story, position, action))
            }
        }
        composeController = create(
            (96f * resources.displayMetrics.density).roundToInt(),
            savedItemActions,
            StoriesFeatureListener(storiesFeature, platformCallbacks),
        )
        activity.attachStoriesComposeController(composeController!!)
        syncComposeState()
    }

    private fun showComposeStoryPreview(openedStory: Story) {
        val controller = composeController
        if (controller == null || stories == null || storyResources == null) return
        val visibleCount = activeStoryListStore.visibleStoryItemCount
        val previewStories = java.util.ArrayList<Story>()
        val sourcePositions = java.util.ArrayList<Int>()
        val cardColors = java.util.ArrayList<Int>()
        val context = requireContext()
        for (position in 0..<visibleCount) {
            val story = stories!![position]
            if (story.isComment || !story.loaded || (story.isLink && TextUtils.isEmpty(
                    story.url
                ))
            ) {
                continue
            }
            previewStories.add(story)
            sourcePositions.add(position)
            cardColors.add(storyResources!!.resolveStoryCardBackgroundColor(context, story))
        }
        var containsOpenedStory = false
        for (story in previewStories) {
            if (story.id == openedStory.id) {
                containsOpenedStory = true
                break
            }
        }
        if (!containsOpenedStory) {
            return
        }
        val positionArray = IntArray(sourcePositions.size)
        val colorArray = IntArray(cardColors.size)
        for (index in sourcePositions.indices) {
            positionArray[index] = sourcePositions[index]
            colorArray[index] = cardColors[index]
        }
        controller.showStoryPreview(previewStories, positionArray, colorArray, openedStory.id)
    }

    private fun prefetchVisibleStoryImages(lastVisibleIndex: Int) {
        if (composeController == null || stories == null || stories!!.isEmpty()) {
            return
        }
        val targetIndex = min(
            stories!!.size - 1,
            max(
                (if (activeStoryListStore.state.value.paginationEnabled) {
                    StoryPaginationPolicy.DEFAULT_PAGE_SIZE
                } else {
                    StoryPaginationPolicy.DEFAULT_INITIAL_LOAD_COUNT
                }) - 1,
                lastVisibleIndex + STORY_VISIBLE_PREFETCH_THRESHOLD
            )
        )
        val context = this.context
        if (context != null) {
            val prefetchStart = max(0, lastVisibleIndex - 2)
            for (i in prefetchStart..targetIndex) {
                val story = stories!![i]
                if (story.loaded) {
                    requestPreviewImagePrefetch(context, story)
                }
            }
        }
    }

    private fun handleStoriesPlatformEffect(effect: StoriesPlatformEffect) {
        when (effect) {
            StoriesPlatformEffect.OpenSettings ->
                requireActivity().startActivity(create(requireActivity()))
            StoriesPlatformEffect.RequestLogin -> AccountUtils.showLoginPrompt(requireContext())
            StoriesPlatformEffect.Logout -> {
                AccountUtils.deleteAccountDetails(requireActivity())
                refreshTypeSpinnerItemsIfNeeded()
                Toast.makeText(this.context, "Logged out", Toast.LENGTH_SHORT).show()
            }
            is StoriesPlatformEffect.OpenProfile ->
                requireActivity().showUserDialog(effect.userName, null)
            StoriesPlatformEffect.ShowCacheDialog -> showCacheStoriesDialog()
            StoriesPlatformEffect.OpenSubmitEditor -> {
                val submitIntent = ComposeEditorContract.createIntent(requireContext())
                submitIntent.putExtra(
                    ComposeEditorContract.EXTRA_TYPE,
                    ComposeEditorContract.TYPE_POST
                )
                startActivity(submitIntent)
            }
            StoriesPlatformEffect.ClearHistory -> {
                historyStore.clear()
                storiesFeature.clearActiveStories()
                updateHeader()
            }
            is StoriesPlatformEffect.PreviewAction -> {
                val controller = composeController ?: return
                when (effect.action) {
                    StoryPreviewActionKind.Vote -> toggleStoryVote(
                        effect.story,
                        Runnable {
                            controller.finishStoryPreviewAction(effect.story.id, effect.action)
                        },
                    )
                    StoryPreviewActionKind.Read -> storiesFeature.toggleRead(effect.story)
                    StoryPreviewActionKind.Bookmark -> storiesFeature.toggleBookmark(effect.story)
                    StoryPreviewActionKind.Favorite -> toggleStoryFavorite(
                        effect.story,
                        Runnable {
                            controller.finishStoryPreviewAction(effect.story.id, effect.action)
                        },
                    )
                }
            }
        }
        syncComposeState()
    }

    private fun handleStoriesRuntimeEffect(effect: StoriesRuntimeEffect) {
        when (effect) {
            is StoriesRuntimeEffect.OpenComments ->
                openComments(effect.story, effect.position, effect.showWebsite)
            is StoriesRuntimeEffect.OpenExternalLink ->
                externalLinks.open(ExternalLinkRequest(effect.url))
            is StoriesRuntimeEffect.StoryChanged -> {
                val changedStory = effect.story
                if (changedStory == null) syncComposeState()
                else composeController?.invalidateStory(changedStory.id)
            }
            is StoriesRuntimeEffect.PrefetchStoryResources ->
                requestPreviewImagePrefetch(context, effect.story)
            StoriesRuntimeEffect.LoginRequired -> AccountUtils.showLoginPrompt(requireContext())
            is StoriesRuntimeEffect.UserMessage ->
                Toast.makeText(requireContext(), effect.message, Toast.LENGTH_SHORT).show()
            is StoriesRuntimeEffect.SavedActionFailed -> {
                val (summary, detail) = effect.outcome.result.failureDetails()
                MainActivity.showFailureDetailForActiveUi(summary, detail)
                Toast.makeText(
                    requireContext(),
                    "Action unsuccessful, see dialog for response",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    private fun showCacheStoriesDialog() {
        requireActivity().showCacheStoriesDialog()
    }

    private fun syncComposeState() {
        val controller = composeController ?: return
        val currentResources = storyResources ?: return
        val context = context ?: return
        val cache = storyCacheController
        val lastUpdated = if (shouldShowLastUpdatedHeader()) {
            "Last updated: " + DateFormat.getTimeFormat(context).format(Date(lastLoaded))
        } else {
            null
        }
        controller.updateContent(
            StoriesScreenStateFactory.create(
                feature = storiesFeature,
                platform = StoriesPlatformPresentation(
                    displaySettings = currentResources.settings,
                    typeLabels = buildStoryTypeLabels(context).map(CharSequence::toString),
                    selectedTypeIndex = getTypeIndex(
                        storiesPresenter.state.value.mainStoryType.label,
                    ),
                    searchSortLabels = StorySearchController.sortLabels.toList(),
                    searchDateLabels = StorySearchController.dateRangeLabels.toList(),
                    searchPointsLabels = StorySearchController.minimumPointsLabels.toList(),
                    searchCommentsLabels = StorySearchController.minimumCommentsLabels.toList(),
                    online = connectivity.isOnline(),
                    lastUpdatedText = lastUpdated,
                    hasCachedStories = Utils.hasCachedStories(context),
                    loggedIn = !AccountUtils.getAccountUsername(activity).isNullOrEmpty(),
                    cacheInProgress = cache?.isCachingStories == true,
                    cacheProgressVisible = cache?.isProgressVisible == true,
                    cacheProgress = cache?.progress ?: 0,
                    cacheProgressMax = cache?.progressMax ?: 1,
                    cacheProgressStatus = cache?.getProgressStatus() ?: "Caching stories",
                    canClearHistory = storiesFeature.currentType.isHistory && historyStore.size > 0,
                    contentInsetStartPx = splitStoriesContentPaddingStart,
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
            if (composeController == null || stories == null) {
                return@post
            }
            for (position in stories!!.indices) {
                val story = stories!!.get(position)
                if (story.id == storyId
                    && (!story.isLink || !TextUtils.isEmpty(story.url))
                ) {
                    showComposeStoryPreview(story)
                    return@post
                }
            }
        }
    }

    private fun restoreStoryStateAfterViewSetup(state: StoriesSessionState?): Boolean {
        if (state == null || !state.initialized || storyResources == null) {
            return false
        }
        syncActiveStoryListToSearchState()
        return true
    }

    private fun shouldRefreshRestoredStoryState(): Boolean {
        return storiesFeature.shouldRefreshRestoredState()
    }

    private val preferredStoryType: StoryType
        get() = StoryType.fromLabel(userSettings.story.preferredStoryType)
            .takeUnless { it == StoryType.UNKNOWN }
            ?: StoryType.TOP_STORIES

    private fun buildStoryTypeLabels(ctx: Context): java.util.ArrayList<CharSequence> {
        return StoryTypeAndroid.buildStoryTypeLabels(
            resources,
            ctx,
            shouldShowUserItemLists(ctx),
        )
    }

    private fun shouldShowUserItemLists(ctx: Context): Boolean {
        return AccountUtils.hasAccountDetails(ctx)
    }

    private fun refreshTypeSpinnerItemsIfNeeded() {
        if (storyResources == null || this.context == null) {
            return
        }

        val previousType = currentStoryType
        val showUserItemLists = shouldShowUserItemLists(requireContext())
        if (userItemListsDropdownVisible == showUserItemLists) {
            return
        }

        userItemListsDropdownVisible = showUserItemLists
        val availableTypes = buildStoryTypeLabels(requireContext()).map(StoryType::fromLabel)
        val newType = previousType.takeIf { it in availableTypes } ?: StoryType.TOP_STORIES
        val typeChanged = previousType != newType
        if (typeChanged) {
            storiesFeature.selectType(StoryListTarget.MAIN, newType)
        }

        if (typeChanged) {
            attemptStoryTypeRefresh()
        } else {
            syncComposeState()
        }
    }

    private fun syncActiveStoryListToSearchState() {
        if (searching) {
            useSearchStoryList()
        } else {
            useMainStoryList()
        }
    }

    private fun useMainStoryList() {
        stories = mainStories
    }

    private fun useSearchStoryList() {
        stories = searchStories
    }

    private val activeStoryListStore: StoryListStore
        get() = if (searching) searchStoryListStore else mainStoryListStore

    private fun updateHeader(animateSearchTransition: Boolean = false) {
        syncComposeState()
    }

    private fun shouldShowLastUpdatedHeader(): Boolean {
        return updateButtonShowing && !searching && lastLoaded > 0
    }

    private val splitStoriesContentPaddingStart: Int
        get() = resources.getDimensionPixelSize(R.dimen.extra_pane_padding)

    private fun showFrontPageDatePicker() {
        if (composeController == null) {
            return
        }
        composeController!!.showFrontDatePicker(
            storiesFeature.frontPageDay.selectedMillis,
            storiesFeature.frontPageDay.earliestMillis,
            storiesFeature.frontPageDay.latestMillis,
        )
    }

    private fun closeSearch() {
        finishSearchBackFromCurrentVisualState = false
        predictiveSearchBackInProgress = false
        predictiveSearchBackProgress = 0f
        storiesFeature.closeSearch()
        useMainStoryList()
        activity.setSearchBackEnabled(false)
        refreshBookmarksIfNeeded()
        syncComposeState()
    }

    private fun setupStoryResources() {
        storyResources = AndroidStoryListResources(
            StoryDisplaySettings.from(userSettings.story),
        ).also { it.setStoryResourceChangedListener(::onStoryResourceChanged) }
        storiesFeature.initialize(preferredStoryType, restoring = sessionState.initialized)
        syncActiveStoryListToSearchState()
    }

    private fun syncInactiveStoryDisplaySettings() {
        mainStoryListStore.contentChanged()
        searchStoryListStore.contentChanged()
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
        requireActivity().onBackPressedDispatcher.addCallback(
            activity, linkSummaryBackCallback!!
        )
    }

    private fun openStoryFromLinkSummary(story: Story?, position: Int, showWebsite: Boolean) {
        if (!this.isAdded || story == null) {
            return
        }
        var currentPosition = stories!!.indexOf(story)
        if (currentPosition < 0) {
            currentPosition = position
        }
        storiesFeature.openComments(story, currentPosition, showWebsite)
    }

    private val isFoldableSplitLayout: Boolean
        get() {
            if (!this.isAdded) {
                return false
            }
            return requireActivity().isAdaptiveFoldableNavigation
        }

    private fun toggleStoryVote(
        story: Story?,
        completion: Runnable
    ) {
        if (story == null) {
            completion.run()
            return
        }
        storiesFeature.toggleVote(story, completion::run)
    }

    private fun toggleStoryFavorite(
        story: Story?,
        completion: Runnable
    ) {
        if (story == null) {
            completion.run()
            return
        }
        storiesFeature.toggleFavorite(story, completion::run)
    }

    fun onStart() {
        started = true
    }

    fun onStop() {
        started = false
    }

    fun onResume() {
        val storyPreferences = userSettings.story
        val newHideJobs = storyPreferences.hideJobs
        val hideJobsChanged = storyVisibilityPolicy.config.hideJobs != newHideJobs
        updateStoryVisibilityPolicy(newHideJobs)
        storiesFeature.configure(
            pagination = storyPreferences.pagination,
            hideClicked = storyPreferences.hideClicked,
            alwaysOpenComments = storyPreferences.alwaysOpenComments,
            useIntegratedWebView = userSettings.reading.integratedWebView,
        )
        refreshTypeSpinnerItemsIfNeeded()
        storiesFeature.syncVisibleUserItemsWithCache()
        refreshBookmarksIfNeeded()

        storiesPresenter.dispatch(
            StoriesAction.EvaluateUpdateAvailability(
                nowMillis = clock.now().toEpochMilliseconds(),
                lastLoadedMillis = lastLoaded,
                alwaysShow = storyPreferences.alwaysShowTapToRefresh,
                storyType = currentStoryType,
            ),
        )

        val displaySettings = StoryDisplaySettings.from(storyPreferences)
        val fontCacheChanged =
            TextUtils.isEmpty(FontUtils.font) || FontUtils.font != displaySettings.font
        if (fontCacheChanged) {
            FontUtils.init(requireContext())
        }
        val displayUpdate = storyResources!!.updateSettings(displaySettings)

        if (displayUpdate.itemsChanged || fontCacheChanged) {
            publishStoryContentChange()
        }

        if (displayUpdate.previewImageModeChanged) {
            scheduleLoadedPreviewImagePrefetchNearViewport()
        }

        if (displayUpdate.compactHeaderChanged) {
            updateHeader()
        }

        if (hideJobsChanged) {
            attemptRefresh()
        }

        syncInactiveStoryDisplaySettings()
        syncStoriesWithHistoriesIfNeeded()
        syncComposeState()
    }

    private fun updateStoryVisibilityPolicy(hideJobs: Boolean) {
        val filters = contentFilters.load()
        storyVisibilityPolicy.config = StoryVisibilityConfig(
            filteredWords = filters.words,
            filteredDomains = filters.domains,
            filteredUsers = filters.users,
            hideJobs = hideJobs,
        )
    }

    private fun refreshBookmarksIfNeeded() {
        if (!bookmarksChanged || storyResources == null || searching
            || !started || !currentStoryType.isBookmarks
        ) {
            return
        }

        bookmarksChanged = false
        attemptRefresh()
    }

    fun onAccountStateChanged() {
        refreshTypeSpinnerItemsIfNeeded()
        updateHeader()
    }

    fun applyWelcomePresetSettings() {
        if (storyResources == null || this.context == null) {
            return
        }

        val storyPreferences = userSettings.story
        val previewImageMode = storyPreferences.previewImageMode
        val previewImageModeChanged =
            storyResources!!.settings.previewImageMode != previewImageMode
        val preferredFont = storyPreferences.font
        val fontCacheChanged = TextUtils.isEmpty(FontUtils.font) || FontUtils.font != preferredFont

        if (fontCacheChanged) {
            FontUtils.init(requireContext())
        }

        storyResources!!.updateSettings(StoryDisplaySettings.from(storyPreferences))

        syncInactiveStoryDisplaySettings()
        mainStoryListStore.contentChanged()
        searchStoryListStore.contentChanged()

        if (previewImageModeChanged) {
            scheduleLoadedPreviewImagePrefetchNearViewport()
        }
    }

    private fun syncStoriesWithHistoriesIfNeeded() {
        val currentHistoriesChangeVersion = historyStore.changeVersion
        if (historiesChangeVersion == currentHistoriesChangeVersion || storyResources == null || stories == null) {
            return
        }

        val previousVersion = historiesChangeVersion
        historiesChangeVersion = currentHistoriesChangeVersion

        when (storiesFeature.syncHistoryIfChanged(previousVersion)) {
            StoryHistorySyncResult.ITEMS_REMOVED -> updateHeader()
            StoryHistorySyncResult.REFRESH_REQUIRED -> attemptRefresh()
            StoryHistorySyncResult.CONTENT_CHANGED -> syncComposeState()
            StoryHistorySyncResult.UNCHANGED -> Unit
        }
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
        mainStoryListStore.cancelTransientLoads()
        searchStoryListStore.cancelTransientLoads()
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
        unregisterStoryResourceListeners()
        storyResources?.dispose()
        storiesFeature.dispose()
        coroutineScope.cancel()
        clearControllerReferences()
        if (bookmarkPreferences != null) {
            bookmarkPreferences!!.unregisterOnSharedPreferenceChangeListener(
                bookmarkPreferenceChangeListener
            )
            bookmarkPreferences = null
        }
        storyClickListener = null
        started = false
        destroyed = true
    }

    private fun clearControllerReferences() {
        val controller = composeController
        if (controller != null) {
            activity.detachStoriesComposeController(controller)
        }
        if (storyCacheController != null) {
            storyCacheController!!.dispose()
            storyCacheController = null
        }
        composeController = null
        storyUpdateListener = null
        storyResources = null
        stories = null
    }

    private fun shouldFilterLoadedStory(story: Story?): Boolean {
        return story?.let { storyVisibilityPolicy.shouldHide(it, currentStoryType) } == true
    }

    fun attemptRefresh() {
        attemptRefresh(false)
    }

    private fun attemptStoryTypeRefresh() {
        attemptRefresh(false, true)
    }

    private fun attemptRefresh(
        showSwipeRefreshIndicator: Boolean,
        showMainLoadingIndicator: Boolean = false
    ) {
        storiesFeature.refresh(
            showSwipeRefreshIndicator = showSwipeRefreshIndicator,
            showMainLoadingIndicator = showMainLoadingIndicator,
        )
        bookmarksChanged = false
        syncComposeState()
    }

    private fun requestPreviewImagePrefetch(context: Context?, story: Story?) {
        val currentStories = stories ?: return
        storyResources?.prefetchStory(context, story, currentStories)
    }

    private fun scheduleLoadedPreviewImagePrefetchNearViewport() {
        val store = activeStoryListStore
        storyResources?.prefetchNearViewport(
            context = context,
            stories = stories.orEmpty(),
            initialLoadCount = if (store.state.value.paginationEnabled) {
                StoryPaginationPolicy.DEFAULT_PAGE_SIZE
            } else {
                StoryPaginationPolicy.DEFAULT_INITIAL_LOAD_COUNT
            },
            paginationVisibleCount = store.state.value.visibleStoryCount
                .takeIf { store.state.value.paginationEnabled },
        )
    }

    private fun registerStoryResourceListeners() {
        storyResources?.setStoryResourceChangedListener(::onStoryResourceChanged)
    }

    private fun unregisterStoryResourceListeners() {
        storyResources?.setStoryResourceChangedListener(null)
    }

    private fun onStoryResourceChanged(story: Story) {
        publishStoryContentChange(story)
    }

    private fun publishStoryContentChange(story: Story? = null) {
        when {
            story == null -> activeStoryListStore.contentChanged()
            mainStories.contains(story) -> mainStoryListStore.contentChanged()
            searchStories.contains(story) -> searchStoryListStore.contentChanged()
        }
        if (story == null) syncComposeState() else composeController?.invalidateStory(story.id)
    }

    fun onStoryPreviewImageLoaded(storyId: Int) {
        // The comments pane receives a bundle copy of the story, so its header preview load does
        // not otherwise invalidate the row that is still visible in the stories pane.
        if (storyId <= 0) return
        val currentContext = context ?: return
        val currentResources = storyResources ?: return
        val currentStories = stories ?: return
        currentStories.firstOrNull { it.id == storyId }?.let { story ->
            currentResources.prefetchPreviewImage(currentContext, story)
        }
    }

    fun onStoryPreviewImageLoadFailed(storyId: Int) {
        if (storyId <= 0) return
        stories?.firstOrNull { it.id == storyId }?.let { story ->
            story.previewImageLoadFailed = true
            context?.let { Utils.cacheStoryPreviewState(it, story) }
            composeController?.invalidateStory(storyId)
        }
    }

    fun currentTypeIsAlgolia(): Boolean = currentStoryType.isAlgolia

    private val currentStoryType: StoryType
        get() = storiesPresenter.state.value.activeStoryType

    private fun getStoryType(type: Int): StoryType {
        return StoryType.fromLabel(getTypeLabel(type))
    }

    private fun getTypeLabel(type: Int): CharSequence? {
        if (type < 0) {
            return null
        }

        val ctx = this.context
        if (ctx == null) {
            return null
        }

        val typeLabels = buildStoryTypeLabels(ctx)
        return typeLabels.getOrNull(type)
    }

    private fun getTypeIndex(label: CharSequence?): Int {
        if (label == null || this.context == null) {
            return -1
        }

        return buildStoryTypeLabels(requireContext())
            .indexOfFirst { it.contentEquals(label) }
    }

    fun exitSearch(): Boolean {
        if (searching) {
            closeSearch()
            return true
        }
        return false
    }

    fun startSearchBackProgress(progress: Float) {
        if (!searching) {
            return
        }
        predictiveSearchBackInProgress = true
        predictiveSearchBackProgress = max(0f, min(1f, progress))
        composeController?.beginPredictiveBack(predictiveSearchBackProgress)
    }

    fun updateSearchBackProgress(progress: Float) {
        if (!searching) {
            return
        }

        if (!predictiveSearchBackInProgress) {
            startSearchBackProgress(progress)
            return
        }

        predictiveSearchBackProgress = max(0f, min(1f, progress))
        composeController?.updatePredictiveBack(predictiveSearchBackProgress)
    }

    fun cancelSearchBackProgress() {
        if (!predictiveSearchBackInProgress) {
            return
        }

        finishSearchBackFromCurrentVisualState = false
        predictiveSearchBackInProgress = false
        predictiveSearchBackProgress = 0f
        useSearchStoryList()
        composeController?.cancelPredictiveBack()
        updateHeader(false)
    }

    fun finishSearchBackProgress(): Boolean {
        if (!searching) {
            finishSearchBackFromCurrentVisualState = false
            return false
        }

        finishSearchBackFromCurrentVisualState = predictiveSearchBackInProgress
        predictiveSearchBackInProgress = false
        if (!finishSearchBackFromCurrentVisualState) {
            return exitSearch()
        }
        val controller = composeController
        if (controller != null) {
            controller.commitPredictiveBack()
            finishSearchBackFromCurrentVisualState = false
            return true
        }
        return exitSearch()
    }

    private fun showCachedStories() {
        storiesFeature.showCachedStories(Utils.loadCachedStories(requireContext()))
        syncComposeState()
    }

    private fun openComments(story: Story?, pos: Int, showWebsite: Boolean) {
        storyClickListener!!.openStory(story, pos, showWebsite)
    }

    interface StoryClickListener {
        fun openStory(story: Story?, pos: Int, showWebsite: Boolean)
    }

    companion object {
        private const val NO_PENDING_LINK_SUMMARY_STORY_ID = -1
        private const val STATE_LINK_SUMMARY_STORY_ID =
            "com.simon.harmonichackernews.STATE_LINK_SUMMARY_STORY_ID"

        private const val STORY_VISIBLE_PREFETCH_THRESHOLD = 17
    }
}
