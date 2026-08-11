package com.simon.harmonichackernews

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.Log
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.RequestQueue
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.SavedItemSnapshot
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemKeys
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.NetworkErrorUtils
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.failureDetails
import com.simon.harmonichackernews.network.StoryFeedRepository
import com.simon.harmonichackernews.platform.AndroidPlatformServices
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.PlatformServices
import com.simon.harmonichackernews.presentation.StoriesSessionState
import com.simon.harmonichackernews.presentation.StoriesAction
import com.simon.harmonichackernews.presentation.StoriesEffect
import com.simon.harmonichackernews.presentation.StoriesPresenter
import com.simon.harmonichackernews.presentation.SavedItemFilter
import com.simon.harmonichackernews.presentation.SavedItemActionOutcome
import com.simon.harmonichackernews.presentation.SavedItemActionUseCase
import com.simon.harmonichackernews.presentation.StoryListStore
import com.simon.harmonichackernews.presentation.StoryHistorySyncResult
import com.simon.harmonichackernews.presentation.StoryListTarget
import com.simon.harmonichackernews.presentation.CommentMasterResolver
import com.simon.harmonichackernews.presentation.StoryLoadFailure
import com.simon.harmonichackernews.presentation.StorySearchRuntime
import com.simon.harmonichackernews.presentation.StorySearchUiState
import com.simon.harmonichackernews.presentation.StoryPaginationPolicy
import com.simon.harmonichackernews.presentation.StoryFeedRefreshPolicy
import com.simon.harmonichackernews.presentation.StoryFeedRuntime
import com.simon.harmonichackernews.presentation.StoryFeedSource
import com.simon.harmonichackernews.presentation.StoryVisibilityConfig
import com.simon.harmonichackernews.presentation.StoryVisibilityPolicy
import com.simon.harmonichackernews.presentation.PreviewPrefetchPlanner
import com.simon.harmonichackernews.presentation.FrontPageDayState
import com.simon.harmonichackernews.presentation.SavedListKind
import com.simon.harmonichackernews.presentation.SavedListPresentationPolicy
import com.simon.harmonichackernews.presentation.SavedItemStoryReconciler
import com.simon.harmonichackernews.presentation.StoryRowMergePolicy
import com.simon.harmonichackernews.presentation.StoriesShellPresentationInput
import com.simon.harmonichackernews.presentation.StoriesShellPresentationPolicy
import com.simon.harmonichackernews.presentation.StoriesMenuAction
import com.simon.harmonichackernews.presentation.StoriesPlatformEffect
import com.simon.harmonichackernews.presentation.StoriesUiOrchestrator
import com.simon.harmonichackernews.presentation.StoryPreviewActionKind
import com.simon.harmonichackernews.presentation.StorySearchOption
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.StoryPreviewPreferences
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.ui.settings.SettingsIntents.create
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesComposeController.Companion.create
import com.simon.harmonichackernews.ui.stories.StoriesScreenState
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

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
    private var isRefreshIndicatorShowing = false
        private set(showing) {
            val changed = field != showing
            field = showing
            if (changed && composeController != null) {
                syncComposeState()
            }
        }
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
        shouldHideClickedStories = { hideClicked },
    )
    private val storyFeedRuntime = StoryFeedRuntime(
        sessionState = sessionState,
        clickedStoryIds = { historyStore.load().mapTo(mutableSetOf()) { it.id } },
        shouldHideClickedStories = { hideClicked },
        hydrateCachedStory = { story -> Utils.loadCachedStorySummary(context, story) },
        shouldHideHydratedStory = ::shouldFilterLoadedStory,
    )
    private val searchStore = storiesPresenter.searchStore
    private val searchRuntime = StorySearchRuntime()
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
    private val bookmarkStories = sessionState.bookmarkStories
    private val userItemListStories = sessionState.userItemListStories
    private val userItemListCommentIds = sessionState.userItemListCommentIds
    private var queue: RequestQueue? = null
    private val requestTag = Any()
    private val storyVisibilityPolicy = StoryVisibilityPolicy()
    private var alwaysOpenComments = false
    private var hideClicked = false
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
    private var searching: Boolean
        get() = storiesPresenter.state.value.searching
        set(value) {
            storiesPresenter.dispatch(StoriesAction.SetSearching(value))
        }
    private var loadingFailed: Boolean
        get() = activeStoryListStore.state.value.failure != null
        set(value) {
            activeStoryListStore.setFailure(if (value) StoryLoadFailure.GENERAL else null)
        }
    private var loadingFailedServerError: Boolean
        get() = activeStoryListStore.state.value.failure == StoryLoadFailure.NOT_FOUND
        set(value) {
            val current = activeStoryListStore.state.value.failure
            if (value) {
                activeStoryListStore.setFailure(StoryLoadFailure.NOT_FOUND)
            } else if (current == StoryLoadFailure.NOT_FOUND) {
                activeStoryListStore.setFailure(StoryLoadFailure.GENERAL)
            }
        }
    private var loadingFailedRateLimited: Boolean
        get() = activeStoryListStore.state.value.failure == StoryLoadFailure.RATE_LIMITED
        set(value) {
            val current = activeStoryListStore.state.value.failure
            if (value) {
                activeStoryListStore.setFailure(StoryLoadFailure.RATE_LIMITED)
            } else if (current == StoryLoadFailure.RATE_LIMITED) {
                activeStoryListStore.setFailure(StoryLoadFailure.GENERAL)
            }
        }
    private var lastSearch: String?
        get() = storiesPresenter.state.value.searchDraft
        set(value) {
            storiesPresenter.dispatch(StoriesAction.SetSearchDraft(value.orEmpty()))
        }
    private val storyListGeneration: Int
        get() = storiesPresenter.storyLoadGeneration
    private var storiesBeforeSearch: MutableList<Story>? = null
    private var loadPendingBeforeSearch = false

    private var showingCached: Boolean
        get() = activeStoryListStore.state.value.showingCached
        set(value) {
            activeStoryListStore.setShowingCached(value)
        }

    private var loadedTo: Int
        get() = activeStoryListStore.state.value.loadedThroughIndex
        set(value) {
            activeStoryListStore.markLoadedThrough(value)
        }
    private var paginationMode = false

    private val previewImagePrefetchHandler = Handler(Looper.getMainLooper())
    private val previewPrefetchPlanner = PreviewPrefetchPlanner(
        batchSize = PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE,
        visibleThreshold = STORY_VISIBLE_PREFETCH_THRESHOLD,
    )
    private val previewImagePrefetchRampRunnable: Runnable = object : Runnable {
        override fun run() {
            previewPrefetchPlanner.startNextBatch()
            drainPreviewImagePrefetchQueue()
        }
    }

    var lastLoaded by sessionState::lastLoaded
    var lastClick: Long = 0
    private val updateButtonShowing: Boolean
        get() = storiesPresenter.state.value.updateAvailable
    private var predictiveSearchBackInProgress = false
    private var predictiveSearchBackProgress = 0f
    private var finishSearchBackFromCurrentVisualState = false
    private var userItemListsDropdownVisible = false
    private var userItemListInitialLoadInProgress = false
    private var userItemListFilter by sessionState::userItemListFilter
    private val frontPageDay = FrontPageDayState(
        restoredMillis = sessionState.frontPageDayUtcMillis,
        nowMillis = clock.now().toEpochMilliseconds(),
    )
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
        queue = NetworkComponent.getRequestQueueInstance(requireContext())
        setupLinkSummaryBackCallback()
        storyCacheController = createStoryCacheController()

        stories = mainStories
        val storyPreferences = userSettings.story
        updateStoryVisibilityPolicy(storyPreferences.hideJobs)
        hideClicked = storyPreferences.hideClicked
        alwaysOpenComments = storyPreferences.alwaysOpenComments
        userItemListsDropdownVisible = shouldShowUserItemLists(requireContext())
        setupStoryResources()
        registerStoryResourceListeners()
        restoredStateForCurrentView = restoringSession && restoreStoryStateAfterViewSetup(sessionState)
        initializeComposeUi()
        coroutineScope.launch {
            searchStore.state.collect(::applyStorySearchState)
        }
        coroutineScope.launch {
            storiesPresenter.effects.collect(::applyStoriesEffect)
        }
        sessionState.initialized = true

        if (restoredStateForCurrentView) {
            updateHeader()
            if (shouldRefreshRestoredStoryState()) {
                attemptRefresh()
            } else if (!searching) {
                resumeInterruptedStoryLoads()
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
        if (composeController != null) {
            return
        }

        composeController = create(
            (96f * activity.resources.displayMetrics.density).roundToInt(),
            savedItemActions,
            object : StoriesComposeController.Listener {
                override fun onTypeSelected(index: Int) {
                    useMainStoryList()
                    val type = getStoryType(index)
                    if (type == StoryType.UNKNOWN || type == currentStoryType) {
                        return
                    }
                    setStoryType(StoryListTarget.MAIN, type)
                    updateStoryRowMode()
                    updatePaginationMode(StoryListTarget.MAIN)
                    attemptStoryTypeRefresh()
                }

                override fun onOpenSearch() {
                    openSearch()
                }

                override fun onCloseSearch() {
                    closeSearch()
                }

                override fun onSearch(query: String) {
                    search(query)
                }

                override fun onSearchOption(kind: StorySearchOption, index: Int) {
                    storiesPresenter.dispatch(StoriesUiOrchestrator.searchOption(kind, index))
                    updateSearchOptionChips()
                    retrySearchWithCurrentOptions()
                }

                override fun onToggleOnlyClicked() {
                    storiesPresenter.dispatch(StoriesAction.ToggleOnlyClicked)
                    updateSearchOptionChips()
                    retrySearchWithCurrentOptions()
                }

                override fun onRefresh() {
                    attemptRefresh()
                }

                override fun onShowCached() {
                    showCachedStories()
                }

                override fun onLoadMore() {
                    handleLoadMore()
                }

                override fun onSavedFilterSelected(filter: SavedItemFilter) {
                    val storedFilter = filter.toStoredFilter()
                    if (storedFilter == userItemListFilter) {
                        return
                    }
                    userItemListFilter = storedFilter
                    if (currentTypeUsesSavedItemFilter()) {
                        applySavedItemFilter()
                    }
                }

                override fun onShiftFrontDate(days: Int) {
                    shiftFrontPageDay(days)
                }

                override fun onPickFrontDate() {
                    showFrontPageDatePicker()
                }

                override fun onFrontDateSelected(day: Long) {
                    selectFrontPageDay(day)
                }

                override fun onMoreAction(action: StoriesMenuAction) {
                    StoriesUiOrchestrator.menu(
                        action,
                        AccountUtils.getAccountUsername(requireActivity()),
                    )?.let(::handleStoriesPlatformEffect)
                }

                override fun onCacheStoriesConfirmed(storyCount: Int) {
                    userSettings.setStoriesToCache(storyCount)
                    if (storyCacheController != null) {
                        storyCacheController!!.cacheStories()
                    }
                }

                override fun onLinkClick(story: Story) {
                    handleStoryLinkClick(stories!!.indexOf(story))
                }

                override fun onCommentClick(story: Story) {
                    val position = stories!!.indexOf(story)
                    if (position >= 0) {
                        clickedComments(position)
                    }
                }

                override fun onCommentStoryClick(story: Story) {
                    val position = stories!!.indexOf(story)
                    if (position >= 0) {
                        clickedCommentStory(position)
                    }
                }

                override fun onCommentRepliesClick(story: Story) {
                    val position = stories!!.indexOf(story)
                    if (position >= 0) {
                        clickedComments(position)
                    }
                }

                override fun onStoryLongClick(story: Story) {
                    val position = stories!!.indexOf(story)
                    if (position < 0) {
                        return
                    }
                    showComposeStoryPreview(story)
                }

                override fun onVisibleStoryRange(lastVisibleIndex: Int) {
                    loadComposeVisibleStories(lastVisibleIndex)
                }

                override fun onStoryPreviewStopScroll() {
                    // Compose owns list scrolling and stops it before invoking this callback.
                }

                override fun onStoryPreviewVisibilityChanged(showing: Boolean) {
                    if (linkSummaryBackCallback != null) {
                        linkSummaryBackCallback!!.isEnabled = showing
                    }
                }

                override fun onStoryPreviewNavigate(
                    story: Story, position: Int, showWebsite: Boolean
                ): Boolean {
                    openStoryFromLinkSummary(story, position, showWebsite)
                    return this@StoriesCoordinator.isFoldableSplitLayout
                }

                override fun onStoryPreviewAction(
                    story: Story,
                    position: Int,
                    action: StoryPreviewActionKind,
                ) {
                    handleStoriesPlatformEffect(
                        StoriesUiOrchestrator.preview(story, position, action),
                    )
                }
            })
        requireActivity().attachStoriesComposeController(composeController!!)
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

    private fun loadComposeVisibleStories(lastVisibleIndex: Int) {
        if (composeController == null || stories == null || stories!!.isEmpty()) {
            return
        }
        val targetIndex = min(
            stories!!.size - 1,
            max(
                this.initialLoadCount - 1,
                lastVisibleIndex + STORY_VISIBLE_PREFETCH_THRESHOLD
            )
        )
        loadStoriesThroughIndex(targetIndex, storyListGeneration)
        retryUnsettledStoriesThroughIndex(targetIndex, storyListGeneration)
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
                loadingFailed = false
                loadingFailedServerError = false
                loadingFailedRateLimited = false
                clearStories()
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
                    StoryPreviewActionKind.Read -> toggleStoryRead(effect.story, effect.position)
                    StoryPreviewActionKind.Bookmark -> toggleStoryBookmark(effect.story)
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

    private fun showCacheStoriesDialog() {
        requireActivity().showCacheStoriesDialog()
    }

    private fun syncComposeState() {
        val controller = composeController
        if (controller == null || storyResources == null || stories == null || this.context == null) {
            return
        }
        val context = requireContext()
        val labels = buildStoryTypeLabels(context)
        val stringLabels = java.util.ArrayList(labels.map(CharSequence::toString))

        val bookmarksType = currentStoryType.isBookmarks
        val historyType = currentStoryType.isHistory
        val favoritesType = currentStoryType.isFavorites
        val upvotedType = currentStoryType.isUpvoted
        val userItemListType = favoritesType || upvotedType
        val savedItemSourceHasItems = currentSavedItemSourceHasItems()
        val online = connectivity.isOnline()
        val lastUpdated = if (shouldShowLastUpdatedHeader())
            "Last updated: " + DateFormat.getTimeFormat(context)
                .format(Date(lastLoaded))
        else
            null
        val cacheInProgress = storyCacheController != null
                && storyCacheController!!.isCachingStories
        val cacheProgressVisible = storyCacheController != null
                && storyCacheController!!.isProgressVisible
        val cacheProgress =
            if (storyCacheController == null) 0 else storyCacheController!!.progress
        val cacheProgressMax =
            if (storyCacheController == null) 1 else storyCacheController!!.progressMax
        val cacheProgressStatus = if (storyCacheController == null)
            "Caching stories"
        else
            storyCacheController!!.getProgressStatus()
        val shellPresentation = StoriesShellPresentationPolicy.present(
            StoriesShellPresentationInput(
                searching = searching,
                submittedSearch = lastSearch.orEmpty().trim().isNotEmpty(),
                storyCount = stories!!.size,
                searchLoading = searchStore.state.value.loading,
                loadingFailed = loadingFailed,
                notFound = loadingFailedServerError,
                rateLimited = loadingFailedRateLimited,
                online = online,
                bookmarks = bookmarksType,
                history = historyType,
                userItems = userItemListType,
                userItemsInitialLoadInProgress = userItemListInitialLoadInProgress,
                refreshIndicatorShowing = isRefreshIndicatorShowing,
                showingCached = showingCached,
                cacheInProgress = cacheInProgress,
                visibleStoryCount = activeStoryListStore.visibleStoryItemCount,
            ),
        )

        controller.updateContent(
            StoriesScreenState(
                mainStories = mainStories,
                searchStories = searchStories,
                displaySettings = StoryDisplaySettings.from(userSettings.story),
                typeLabels = stringLabels,
                selectedTypeIndex = getTypeIndex(storiesPresenter.state.value.mainStoryType.label),
                searching = searching,
                lastSearch = lastSearch!!,
                searchSortLabel = searchStore.sortLabel,
                searchDateLabel = searchStore.dateRangeLabel,
                searchPointsLabel = searchStore.minimumPointsLabel,
                searchCommentsLabel = searchStore.minimumCommentsLabel,
                searchSortLabels = StorySearchController.sortLabels.toList(),
                searchDateLabels = StorySearchController.dateRangeLabels.toList(),
                searchPointsLabels = StorySearchController.minimumPointsLabels.toList(),
                searchCommentsLabels = StorySearchController.minimumCommentsLabels.toList(),
                searchOnlyClicked = searchStore.state.value.options.onlyClicked,
                loading = shellPresentation.showLoading,
                refreshing = this.isRefreshIndicatorShowing,
                loadingFailed = loadingFailed,
                loadingFailedServerError = loadingFailedServerError,
                loadingFailedMessage = shellPresentation.loadingFailureMessage,
                showingCached = showingCached,
                showCachedAction = loadingFailed && !searching && Utils.hasCachedStories(context),
                showEmptySavedList = shellPresentation.showEmptySavedList,
                emptySavedListText = getEmptySavedListText(
                    historyType, favoritesType, upvotedType, savedItemSourceHasItems
                ),
                emptySavedListIcon = getEmptySavedListIcon(historyType, favoritesType, upvotedType),
                showEmptySearch = shellPresentation.showEmptySearch,
                showUpdate = updateButtonShowing,
                lastUpdatedText = lastUpdated,
                showLoadMore = activeStoryListStore.hasLoadMore,
                loadMoreLoading = activeStoryListStore.state.value.loadMoreInProgress,
                mainVisibleCount = mainStoryListStore.visibleStoryItemCount,
                searchVisibleCount = searchStoryListStore.visibleStoryItemCount,
                showSavedFilter = !searching && currentTypeUsesSavedItemFilter() && savedItemSourceHasItems,
                savedFilter = currentSavedItemFilter,
                showFrontDate = !searching && currentTypeIsFront(),
                frontDateLabel = this.frontPageDayParameter,
                frontPreviousEnabled = frontPageDay.selectedMillis > frontPageDay.earliestMillis,
                frontNextEnabled = frontPageDay.selectedMillis < frontPageDay.latestMillis,
                loggedIn = !TextUtils.isEmpty(AccountUtils.getAccountUsername(requireActivity())),
                canCache = shellPresentation.canCacheStories,
                canClearHistory = currentStoryType.isHistory && historyStore.size > 0,
                cacheProgressVisible = cacheProgressVisible,
                cacheProgress = cacheProgress,
                cacheProgressMax = cacheProgressMax,
                cacheProgressStatus = cacheProgressStatus,
                contentInsetStartPx = this.splitStoriesContentPaddingStart,
            ),
        )
    }

    private fun restoreLinkSummaryAfterRecreation() {
        if (pendingLinkSummaryStoryId == NO_PENDING_LINK_SUMMARY_STORY_ID) {
            return
        }
        val storyId = pendingLinkSummaryStoryId
        pendingLinkSummaryStoryId = NO_PENDING_LINK_SUMMARY_STORY_ID
        previewImagePrefetchHandler.post(object : Runnable {
            override fun run() {
            if (composeController == null || stories == null) {
                return
            }
            for (position in stories!!.indices) {
                val story = stories!!.get(position)
                if (story.id == storyId
                    && (!story.isLink || !TextUtils.isEmpty(story.url))
                ) {
                    showComposeStoryPreview(story)
                    return
                }
            }
            }
        })
    }

    private fun restoreStoryStateAfterViewSetup(state: StoriesSessionState?): Boolean {
        if (state == null || !state.initialized || storyResources == null) {
            return false
        }

        val mainListState = mainStoryListStore.state.value
        val searchListState = searchStoryListStore.state.value
        updateStoryRowMode()

        syncActiveStoryListToSearchState()
        storyFeedRuntime.restoreScrapedPagination(
            currentStoryType.takeIf { currentTypeIsScrapedFrontpage() },
        )

        if (searching) {
            storiesBeforeSearch = java.util.ArrayList<Story>(mainStories)
            loadPendingBeforeSearch = mainStories.isEmpty()
                    && mainListState.failure == null &&
                    !storiesPresenter.state.value.mainStoryType.isBookmarks &&
                    !storiesPresenter.state.value.mainStoryType.isUserItemList
        }

        syncActiveStoryListToSearchState()
        updateSearchOptionChips(false)
        return true
    }

    private fun shouldRefreshRestoredStoryState(): Boolean {
        return StoryFeedRefreshPolicy.shouldRefreshRestoredState(
            failure = activeStoryListStore.state.value.failure,
            listIsEmpty = stories!!.isEmpty(),
            searching = searching,
            searchQuery = lastSearch.orEmpty(),
            storyType = currentStoryType,
        )
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
            setStoryType(StoryListTarget.MAIN, newType)
            updateStoryRowMode()
            updatePaginationMode(StoryListTarget.MAIN)
        }

        if (typeChanged) {
            attemptStoryTypeRefresh()
        } else {
            syncComposeState()
        }
    }

    private fun enqueueStoryRowChange(story: Story, loadGeneration: Int) {
        if (isCurrentStoryListGeneration(loadGeneration)) publishStoryContentChange(story)
    }

    private fun enqueueStoryRemoval(
        story: Story,
        loadGeneration: Int,
        updateHeader: Boolean
    ) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return
        }

        val position = findStoryPositionById(story.id)
        if (position < 0) return
        removeStoryAt(position, loadGeneration, false)
        loadVisibleStories(loadGeneration)
        if (updateHeader) updateHeader() else syncComposeState()
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

    private val frontPageDayParameter: String
        get() = frontPageDay.requestParameter

    private fun shiftFrontPageDay(days: Int) {
        frontPageDay.shift(days)
        sessionState.frontPageDayUtcMillis = frontPageDay.selectedMillis
        if (currentTypeIsFront()) {
            attemptStoryTypeRefresh()
        }
    }

    private fun showFrontPageDatePicker() {
        if (composeController == null) {
            return
        }
        composeController!!.showFrontDatePicker(
            frontPageDay.selectedMillis,
            frontPageDay.earliestMillis,
            frontPageDay.latestMillis,
        )
    }

    private fun selectFrontPageDay(selection: Long) {
        frontPageDay.select(selection)
        sessionState.frontPageDayUtcMillis = frontPageDay.selectedMillis
        if (currentTypeIsFront()) {
            attemptStoryTypeRefresh()
        }
    }

    private fun getEmptySavedListText(
        historyType: Boolean,
        favoritesType: Boolean,
        upvotedType: Boolean,
        savedItemSourceHasItems: Boolean
    ): String = SavedListPresentationPolicy.emptyMessage(
        kind = when {
            historyType -> SavedListKind.HISTORY
            favoritesType -> SavedListKind.FAVORITES
            upvotedType -> SavedListKind.UPVOTED
            else -> SavedListKind.BOOKMARKS
        },
        filter = currentSavedItemFilter,
        sourceHasItems = savedItemSourceHasItems,
    )

    private fun getEmptySavedListIcon(
        historyType: Boolean,
        favoritesType: Boolean,
        upvotedType: Boolean
    ): DrawableResource {
        if (historyType) {
            return Res.drawable.ic_history
        }
        if (favoritesType) {
            return Res.drawable.ic_star
        }
        if (upvotedType) {
            return Res.drawable.ic_thumb_up_filled
        }
        return Res.drawable.ic_bookmark
    }

    private fun updateSearchOptionChips(animate: Boolean = true) {
        syncComposeState()
    }

    private fun retrySearchWithCurrentOptions() {
        if (!searching) {
            return
        }

        if (!TextUtils.isEmpty(lastSearch)) {
            search(lastSearch)
        }
    }

    private fun openSearch() {
        finishSearchBackFromCurrentVisualState = false
        searching = true
        resetSearchOptions()
        updateSearchStatus()
        syncComposeState()
    }

    private fun closeSearch() {
        finishSearchBackFromCurrentVisualState = false
        predictiveSearchBackInProgress = false
        predictiveSearchBackProgress = 0f
        lastSearch = ""
        // Resetting search emits a search-store update. Keep the search list active until that
        // update is applied so it can never clear the retained main stories list on the way out.
        resetSearchOptions()
        searching = false
        updateSearchStatus()
    }

    private fun resetSearchOptions() {
        storiesPresenter.dispatch(StoriesAction.ResetSearchOptions)
        updateSearchOptionChips(false)
    }

    private fun resetPaginationState() {
        loadedTo = -1
        clearPaginationLoadMoreState()
        activeStoryListStore.setPaginationEnabled(shouldUsePaginationForType(currentStoryType))
        activeStoryListStore.setVisibleStoryCount(
            StoryPaginationPolicy.initialVisibleCount(
                activeStoryListStore.state.value.paginationEnabled,
            ),
        )
    }

    private fun shouldUsePaginationForType(storyType: StoryType?): Boolean {
        return StoryPaginationPolicy.isEnabled(paginationMode, storyType)
    }

    private fun updatePaginationMode(target: StoryListTarget) {
        val targetType = if (target == StoryListTarget.SEARCH) {
            storiesPresenter.state.value.searchStoryType
        } else {
            storiesPresenter.state.value.mainStoryType
        }
        val targetStore = if (target == StoryListTarget.SEARCH) searchStoryListStore else mainStoryListStore
        targetStore.setPaginationEnabled(shouldUsePaginationForType(targetType))
    }

    private val initialLoadCount: Int
        get() = if (activeStoryListStore.state.value.paginationEnabled) {
            StoryPaginationPolicy.DEFAULT_PAGE_SIZE
        } else {
            StoryPaginationPolicy.DEFAULT_INITIAL_LOAD_COUNT
        }

    private fun loadStoriesThroughIndex(targetIndex: Int, loadGeneration: Int) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return
        }

        var i = loadedTo + 1
        while (i <= targetIndex && i < stories!!.size) {
            loadedTo = i
            loadStory(stories!!.get(i), loadGeneration)
            i++
        }
    }

    private fun finishPaginationLoadMoreStory(story: Story?, loadGeneration: Int) {
        if (story != null) activeStoryListStore.finishNextPageStory(story.id, loadGeneration)
    }

    private fun clearPaginationLoadMoreState() {
        activeStoryListStore.clearPendingPage()
    }

    private fun retryUnsettledStoriesThroughIndex(targetIndex: Int, loadGeneration: Int) {
        if (!isCurrentStoryListGeneration(loadGeneration) || targetIndex < 0 || stories!!.isEmpty()) {
            return
        }

        val cappedTargetIndex = min(targetIndex, stories!!.size - 1)
        for (i in 0..cappedTargetIndex) {
            val story = stories!!.get(i)
            if (!story.loaded && !story.loadingFailed && !isStoryLoadInProgress(
                    story
                )
            ) {
                loadStory(story, loadGeneration)
            }
        }
    }

    private val visibleLoadTargetIndex: Int
        get() = StoryPaginationPolicy.visibleLoadTargetIndex(
            storyCount = stories?.size ?: 0,
            paginationEnabled = activeStoryListStore.state.value.paginationEnabled,
            visibleStoryCount = activeStoryListStore.state.value.visibleStoryCount,
        )

    private fun loadVisibleStories(loadGeneration: Int) {
        val targetIndex = this.visibleLoadTargetIndex
        loadStoriesThroughIndex(targetIndex, loadGeneration)
        retryUnsettledStoriesThroughIndex(targetIndex, loadGeneration)
    }

    private fun clearStories() {
        resetPreviewImagePrefetchRamp()
        activeStoryListStore.clear()
        resetPaginationState()
        activeStoryListStore.setCanLoadMore(false)
    }

    private fun clearStoriesForSearchEntry() {
        clearStories()
    }

    private fun clearSearchStoriesAfterExit() {
        resetPreviewImagePrefetchRamp()
        searchStoryListStore.clear()
        searchStoryListStore.setPaginationEnabled(
            shouldUsePaginationForType(storiesPresenter.state.value.searchStoryType),
        )
        searchStoryListStore.setVisibleStoryCount(
            StoryPaginationPolicy.initialVisibleCount(
                searchStoryListStore.state.value.paginationEnabled,
            ),
        )
        searchStoryListStore.setCanLoadMore(false)
    }

    private fun replaceStories(
        newStories: MutableList<Story>,
        replaceExisting: Boolean = false,
        showLoadMoreButton: Boolean = false
    ) {
        resetPreviewImagePrefetchRamp()
        if (replaceExisting) {
            resetPaginationState()
            activeStoryListStore.replace(newStories, showLoadMoreButton, showingCached)
            return
        }

        clearStories()
        activeStoryListStore.replace(newStories, showLoadMoreButton, showingCached)

    }

    private fun saveStoriesBeforeSearch() {
        if (storiesBeforeSearch != null) {
            return
        }

        storiesBeforeSearch = java.util.ArrayList<Story>(stories ?: emptyList())
        loadPendingBeforeSearch = stories!!.isEmpty()
                && !loadingFailed && !loadingFailedServerError &&
                !currentStoryType.isBookmarks && !currentStoryType.isUserItemList
    }

    private fun restoreStoriesBeforeSearch(): Boolean {
        if (storiesBeforeSearch == null) {
            return false
        }

        // Search owns a separate retained store. Switching the active store is the restoration;
        // there is no controller snapshot to copy back into it.
        clearStoriesBeforeSearchSnapshot()
        return true
    }

    private fun clearStoriesBeforeSearchSnapshot() {
        storiesBeforeSearch = null
    }

    private fun resumeInterruptedStoryLoads() {
        if (currentTypeIsAlgolia() || stories!!.isEmpty() || loadedTo < 0) {
            return
        }

        val lastIndexToLoad = min(loadedTo, stories!!.size - 1)
        for (i in 0..lastIndexToLoad) {
            val story = stories!!.get(i)
            if (!story.loaded && !story.loadingFailed) {
                loadStory(story)
            }
        }
    }

    private fun setupStoryResources() {
        paginationMode = userSettings.story.pagination

        storyResources = AndroidStoryListResources(
            StoryDisplaySettings.from(userSettings.story),
        ).also { it.setStoryResourceChangedListener(::onStoryResourceChanged) }
        if (!sessionState.initialized) {
            setStoryType(StoryListTarget.MAIN, preferredStoryType)
            setStoryType(StoryListTarget.SEARCH, preferredStoryType)
        }
        stories = mainStories

        updatePaginationMode(StoryListTarget.MAIN)
        updatePaginationMode(StoryListTarget.SEARCH)
        if (!sessionState.initialized) {
            mainStoryListStore.setVisibleStoryCount(
                StoryPaginationPolicy.initialVisibleCount(mainStoryListStore.state.value.paginationEnabled),
            )
            searchStoryListStore.setVisibleStoryCount(
                StoryPaginationPolicy.initialVisibleCount(searchStoryListStore.state.value.paginationEnabled),
            )
        }
        updateStoryRowMode()
    }

    private fun syncInactiveStoryDisplaySettings() {
        updateStoryRowMode()
        mainStoryListStore.contentChanged()
        searchStoryListStore.contentChanged()
    }

    private fun handleStoryLinkClick(position: Int) {
        if (position == NO_POSITION || position < 0 || position >= stories!!.size) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastClick > CLICK_INTERVAL) {
            lastClick = now
        } else {
            return
        }

        storiesPresenter.dispatch(
            StoriesAction.SelectStoryLink(
                story = stories!![position],
                position = position,
                alwaysOpenComments = alwaysOpenComments,
                useIntegratedWebView = userSettings.reading.integratedWebView,
            ),
        )
    }

    private fun applyStoriesEffect(effect: StoriesEffect) {
        when (effect) {
            is StoriesEffect.OpenComments -> {
                markStoryClicked(effect.story)
                publishStoryContentChange(effect.story)
                openComments(effect.story, effect.position, effect.showWebsite)
            }
            is StoriesEffect.OpenExternalStory -> {
                if (effect.story.isFrontpageLink) {
                    effect.story.clicked = true
                } else {
                    markStoryClicked(effect.story)
                }
                publishStoryContentChange(effect.story)
                externalLinks.open(ExternalLinkRequest(effect.url))
            }
            is StoriesEffect.RetryStory -> {
                effect.story.loadingFailed = false
                loadStory(effect.story)
                publishStoryContentChange(effect.story)
            }
            is StoriesEffect.FeedLoaded -> applyLoadedStoryFeed(effect)
            is StoriesEffect.FeedFailed -> applyFailedStoryFeed(effect)
            is StoriesEffect.NextScrapedPageLoaded -> applyNextScrapedPage(effect)
            is StoriesEffect.NextScrapedPageFailed -> {
                if (!isCurrentStoryFeedEffect(effect.storyType, effect.generation) ||
                    !storyFeedRuntime.failNextScrapedPage(activeStoryListStore, effect.storyType)
                ) {
                    return
                }
                Log.w(
                    TAG,
                    "Next scraped page failed for type=${effect.storyType.label}, " +
                        "generation=${effect.generation}",
                    effect.cause,
                )
                updateHeader()
            }
            is StoriesEffect.StoryRowLoaded -> applyLoadedStoryRow(effect)
            is StoriesEffect.StoryRowRejected -> {
                if (isCurrentStoryRowEffect(effect.story, effect.generation)) {
                    enqueueStoryRemoval(effect.story, effect.generation, false)
                }
            }
            is StoriesEffect.StoryRowLoadAttemptFailed -> {
                if (!isCurrentStoryRowEffect(effect.story, effect.generation)) return
                Log.w(
                    TAG,
                    "Failed to load story id=${effect.story.id}, attempt=${effect.attempt}",
                    effect.cause,
                )
                if (effect.finalAttempt) {
                    finishPaginationLoadMoreStory(effect.story, effect.generation)
                }
                drainPreviewImagePrefetchQueue()
                enqueueStoryRowChange(effect.story, effect.generation)
            }
            is StoriesEffect.UserItemsSynced -> applySyncedUserItems(effect)
            is StoriesEffect.UserItemsSyncFailed -> applyFailedUserItemsSync(effect)
        }
    }

    private fun applySyncedUserItems(effect: StoriesEffect.UserItemsSynced) {
        val upvoted = effect.source == SavedItemSource.UPVOTED
        if (!isCurrentUserItemsEffect(upvoted, effect.generation)) return
        syncUserItemListStoriesToIds(effect.snapshot.itemIds, effect.snapshot.commentIds)
        userItemListInitialLoadInProgress = false
        activeStoryListStore.setFailure(null)
        isRefreshIndicatorShowing = false
        updateHeader()
    }

    private fun applyFailedUserItemsSync(effect: StoriesEffect.UserItemsSyncFailed) {
        val upvoted = effect.source == SavedItemSource.UPVOTED
        if (!isCurrentUserItemsEffect(upvoted, effect.generation)) return
        isRefreshIndicatorShowing = false
        userItemListInitialLoadInProgress = false
        loadingFailed = stories!!.isEmpty()
        loadingFailedRateLimited = NetworkErrorUtils.isRateLimitedText(
            effect.summary,
            effect.detail,
        )
        effect.cause?.let {
            Log.w(TAG, "User item sync failed for ${effect.source}", it)
        }
        updateHeader()
        Toast.makeText(requireContext(), effect.summary, Toast.LENGTH_SHORT).show()
    }

    private fun isCurrentUserItemsEffect(upvoted: Boolean, generation: Int): Boolean =
        isAdded && storyResources != null && isSameUserItemListType(currentStoryType, upvoted) &&
            isCurrentStoryListGeneration(generation)

    private fun applyLoadedStoryRow(effect: StoriesEffect.StoryRowLoaded) {
        val story = effect.story
        if (!isCurrentStoryRowEffect(story, effect.generation)) return
        finishPaginationLoadMoreStory(story, effect.generation)
        if (story.isComment && currentTypeUsesCommentRows()) {
            loadCommentMaster(story, story.parentId, effect.generation)
        }
        if (currentTypeUsesSavedItemFilter() && !shouldShowStoryForSavedItemFilter(story)) {
            enqueueStoryRemoval(story, effect.generation, true)
            return
        }
        if (shouldFilterLoadedStory(story)) {
            enqueueStoryRemoval(story, effect.generation, false)
            return
        }
        context?.let { requestPreviewImagePrefetch(it, story) }
        enqueueStoryRowChange(story, effect.generation)
    }

    private fun isCurrentStoryRowEffect(story: Story, generation: Int): Boolean =
        isCurrentStoryListGeneration(generation) && stories?.indexOf(story) != -1

    private fun applyLoadedStoryFeed(effect: StoriesEffect.FeedLoaded) {
        if (!isCurrentStoryFeedEffect(effect.storyType, effect.generation)) return
        isRefreshIndicatorShowing = false
        resetPreviewImagePrefetchRamp()
        val application = storyFeedRuntime.applyInitial(
            activeStoryListStore,
            effect.storyType,
            effect.result,
        )
        if (application.loadVisibleStories) loadInitialVisibleStories(effect.generation)
        updateHeader()
    }

    private fun applyFailedStoryFeed(effect: StoriesEffect.FeedFailed) {
        if (!isCurrentStoryFeedEffect(effect.storyType, effect.generation)) return
        isRefreshIndicatorShowing = false
        activeStoryListStore.fail(StoryFeedRefreshPolicy.failureFor(effect.cause))
        Log.w(
            TAG,
            "Story feed failed for type=${effect.storyType.label}, " +
                "generation=${effect.generation}",
            effect.cause,
        )
        updateHeader()
    }

    private fun applyNextScrapedPage(effect: StoriesEffect.NextScrapedPageLoaded) {
        if (!isCurrentStoryFeedEffect(effect.storyType, effect.generation)) return
        val application = storyFeedRuntime.applyNextScrapedPage(
            activeStoryListStore,
            effect.storyType,
            effect.page,
        )
        if (!application.applied) return
        if (application.loadVisibleStories) loadVisibleStories(effect.generation)
        updateHeader()
    }

    private fun isCurrentStoryFeedEffect(storyType: StoryType, generation: Int): Boolean =
        isAdded && storyResources != null && currentStoryType == storyType &&
            isCurrentStoryListGeneration(generation)

    private fun handleLoadMore() {
        val listState = activeStoryListStore.state.value
        if (listState.paginationEnabled && listState.visibleStoryCount < stories!!.size) {
            val plan = activeStoryListStore.beginNextPage(storyListGeneration) ?: return
            if (!activeStoryListStore.hasPendingPageStories()) {
                clearPaginationLoadMoreState()
            }
            loadStoriesThroughIndex(plan.targetLoadedIndex, storyListGeneration)
            retryUnsettledStoriesThroughIndex(plan.targetLoadedIndex, storyListGeneration)
        } else if (listState.canLoadMore && currentTypeIsScrapedFrontpage()) {
            loadMoreScrapedFrontpageStories(storyListGeneration)
        } else if (listState.canLoadMore) {
            loadMoreAlgoliaResults()
        }
        syncComposeState()
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
        markStoryClicked(story)
        publishStoryContentChange(story)
        openComments(story, currentPosition, showWebsite)
    }

    private val isFoldableSplitLayout: Boolean
        get() {
            if (!this.isAdded) {
                return false
            }
            return requireActivity().isAdaptiveFoldableNavigation
        }

    private fun toggleStoryRead(story: Story?, position: Int) {
        if (!this.isAdded || story == null) {
            return
        }
        story.clicked = !story.clicked
        if (story.clicked) {
            historyStore.record(story.id, clock.now().toEpochMilliseconds())
        } else {
            historyStore.remove(story.id)
        }
        publishStoryContentChange(story)
    }

    private fun toggleStoryBookmark(story: Story?) {
        if (!this.isAdded || story == null) {
            return
        }
        val bookmarked = savedItemActions.toggleBookmark(story.id)
        if (!bookmarked) {
            if (currentStoryType.isBookmarks) {
                bookmarkStories.remove(story)
                val currentPosition = stories!!.indexOf(story)
                if (currentPosition >= 0) {
                    removeStoryAt(currentPosition, storyListGeneration, true)
                }
                updateHeader()
                return
            }
        }
        publishStoryContentChange(story)
    }

    private fun toggleStoryVote(
        story: Story?,
        completion: Runnable
    ) {
        if (!this.isAdded || story == null || storyResources == null || stories == null) {
            completion.run()
            return
        }
        val actionGeneration = storyListGeneration
        val actionStories = stories
        val context = requireContext()
        val action = savedItemActions.beginVote(
            itemId = story.id,
            isComment = false,
            direction = if (savedItemActions.isUpvoted(story.id, false)) "un" else "up",
        )
        publishStoryContentChange(story)
        coroutineScope.launch {
            val outcome = savedItemActions.execute(action)
            if (outcome is SavedItemActionOutcome.Success) {
                completion.run()
                return@launch
            }
            if (isCurrentStoryActionContext(actionGeneration, actionStories)) {
                publishStoryContentChange(story)
            }
            val result = (outcome as SavedItemActionOutcome.Failure).result
            val (summary, detail) = result.failureDetails()
            MainActivity.showFailureDetailForActiveUi(summary, detail)
            Toast.makeText(
                context,
                "Vote unsuccessful, see dialog for response",
                Toast.LENGTH_SHORT,
            ).show()
            completion.run()
        }
    }

    private fun toggleStoryFavorite(
        story: Story?,
        completion: Runnable
    ) {
        if (!this.isAdded || story == null || storyResources == null || stories == null) {
            completion.run()
            return
        }
        val actionGeneration = storyListGeneration
        val actionStories = stories
        val actionIsFavoritesList = currentStoryType.isFavorites
        val action = savedItemActions.beginFavorite(story.id)
        val currentlyFavorited = action.previousPresent
        val optimisticIndex = stories!!.indexOf(story)
        if (optimisticIndex >= 0) {
            if (currentlyFavorited && actionIsFavoritesList) {
                removeStoryAt(optimisticIndex, storyListGeneration, true)
                updateHeader()
            } else {
                publishStoryContentChange(story)
            }
        }
        coroutineScope.launch {
            val outcome = savedItemActions.execute(action)
            if (outcome is SavedItemActionOutcome.Success) {
                completion.run()
                return@launch
            }
            if (!isCurrentStoryActionContext(actionGeneration, actionStories)) {
                completion.run()
                return@launch
            }
            val currentIndex = stories!!.indexOf(story)
            if (currentlyFavorited && actionIsFavoritesList && currentIndex == -1) {
                val restoreIndex =
                    if (optimisticIndex >= 0) min(optimisticIndex, stories!!.size) else 0
                activeStoryListStore.insertAt(restoreIndex, story)
                updateHeader()
            } else if (currentIndex >= 0) {
                publishStoryContentChange(story)
            }
            completion.run()
        }
    }

    private fun isCurrentStoryActionContext(
        generation: Int,
        expectedStories: MutableList<Story>?
    ): Boolean {
        return this.isAdded
                && generation == storyListGeneration && stories === expectedStories
    }

    private fun findStoryPositionById(storyId: Int): Int {
        if (stories == null) return NO_POSITION
        for (position in stories!!.indices) {
            val story = stories!![position]
            if (story.id == storyId) return position
        }
        return NO_POSITION
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
        hideClicked = storyPreferences.hideClicked
        alwaysOpenComments = storyPreferences.alwaysOpenComments
        refreshTypeSpinnerItemsIfNeeded()
        syncVisibleUserItemListWithLocalCache()
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

        val newPaginationMode = storyPreferences.pagination
        if (paginationMode != newPaginationMode) {
            paginationMode = newPaginationMode
            updatePaginationMode(StoryListTarget.MAIN)
            updatePaginationMode(StoryListTarget.SEARCH)
            resetPaginationState()
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

        historiesChangeVersion = currentHistoriesChangeVersion

        when (activeStoryListStore.syncHistory(
            clickedStoryIds = historyStore.load().mapTo(mutableSetOf()) { it.id },
            searchingOnlyClicked = searching && searchStore.state.value.options.onlyClicked,
            showingHistory = currentStoryType.isHistory,
            hideClicked = hideClicked,
        )) {
            StoryHistorySyncResult.ITEMS_REMOVED -> {
                loadVisibleStories(storyListGeneration)
                updateHeader()
            }
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
        if (queue != null) {
            storiesPresenter.beginStoryLoadGeneration()
            mainStoryListStore.clearPendingPage()
            searchStoryListStore.clearPendingPage()
            resetPreviewImagePrefetchRamp()
            invalidateAlgoliaLoad()
            queue!!.cancelAll(requestTag)
        }
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

    private fun clickedComments(position: Int) {
        // prevent double clicks
        val now = System.currentTimeMillis()
        if (now - lastClick > CLICK_INTERVAL) {
            lastClick = now
        } else {
            return
        }

        if (position == NO_POSITION) {
            return
        }

        storiesPresenter.dispatch(
            StoriesAction.SelectStoryComments(stories!![position], position),
        )
    }

    private fun clickedCommentStory(position: Int) {
        if (position == NO_POSITION) {
            return
        }

        val story = stories!!.get(position)
        val masterStory = story.toCommentMasterStory()
        if (masterStory != null) {
            openCommentMasterStory(story, masterStory, position)
        } else {
            clickedComments(position)
        }
    }

    private fun openCommentMasterStory(sourceStory: Story, masterStory: Story, position: Int) {
        if (masterStory.loaded) {
            openComments(masterStory, position, false)
            return
        }

        coroutineScope.launch {
            try {
                val resolved = commentMasterResolver.resolve(sourceStory)
                if (!isAdded || storyResources == null) return@launch
                publishStoryContentChange(sourceStory)
                openComments(resolved, position, false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Failed to load comment master id=${masterStory.id}", error)
                if (isAdded) openComments(masterStory, position, false)
            }
        }
    }

    private fun markStoryClicked(story: Story) {
        if (!searchStore.state.value.options.onlyClicked) {
            story.clicked = true
        }
        historyStore.record(story.id, clock.now().toEpochMilliseconds())
    }

    private fun removeStoryAt(index: Int, loadGeneration: Int, loadVisibleReplacement: Boolean) {
        if (index < 0 || index >= stories!!.size) {
            return
        }

        val removedStory = activeStoryListStore.removeAt(index) ?: return
        finishPaginationLoadMoreStory(removedStory, loadGeneration)
        clearStoryLoadState(removedStory)
        if (index <= loadedTo) {
            loadedTo = max(-1, loadedTo - 1)
        }

        if (loadVisibleReplacement) {
            loadVisibleStories(loadGeneration)
        }
    }

    private fun shouldFilterLoadedStory(story: Story?): Boolean {
        return story?.let { storyVisibilityPolicy.shouldHide(it, currentStoryType) } == true
    }

    private fun isStoryLoadInProgress(story: Story?): Boolean {
        return story?.let { storiesPresenter.isStoryRowLoadInProgress(it.id) } == true
    }

    private fun clearStoryLoadState(story: Story?) {
        story?.let { storiesPresenter.cancelStoryRowLoad(it.id) }
    }

    private fun clearLoadingStoryState() {
        storiesPresenter.clearStoryRowLoads()
    }

    private fun loadStory(story: Story, loadGeneration: Int = storyListGeneration) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return
        }

        if (story.loaded) {
            val index = stories!!.indexOf(story)
            if (index >= 0 && shouldFilterLoadedStory(story)) {
                enqueueStoryRemoval(story, loadGeneration, false)
            }
            return
        }

        if (isStoryLoadInProgress(story)) {
            return
        }

        storiesPresenter.dispatch(
            StoriesAction.LoadStoryRow(
                story = story,
                preserveTime = currentStoryType.isHistory,
                generation = loadGeneration,
            ),
        )
    }

    fun attemptRefresh() {
        attemptRefresh(false)
    }

    private fun attemptStoryTypeRefresh() {
        attemptRefresh(false, true)
    }

    private fun invalidateAlgoliaLoad() {
        searchStore.cancel(clearResults = false)
        searchRuntime.cancel(activeStoryListStore)
    }

    private fun beginStoryListRefresh(): Int {
        storiesPresenter.dispatch(StoriesAction.CancelFeedLoads)
        storiesPresenter.beginStoryLoadGeneration()
        activeStoryListStore.clearPendingPage()
        resetPreviewImagePrefetchRamp()
        resetScrapedFrontpagePaginationState()
        invalidateAlgoliaLoad()
        queue!!.cancelAll(requestTag)
        return storyListGeneration
    }

    private fun resetScrapedFrontpagePaginationState() {
        storyFeedRuntime.resetScrapedPagination(activeStoryListStore)
    }

    private fun isCurrentStoryListGeneration(generation: Int): Boolean {
        return storiesPresenter.isCurrentStoryLoadGeneration(generation)
    }

    private fun attemptRefresh(
        showSwipeRefreshIndicator: Boolean,
        showMainLoadingIndicator: Boolean = false
    ) {
        hideUpdateButton()
        val storyType = currentStoryType
        val refreshPlan = StoryFeedRefreshPolicy.plan(
            searching = searching,
            storyType = storyType,
            showSwipeRefreshIndicator = showSwipeRefreshIndicator,
            showMainLoadingIndicator = showMainLoadingIndicator,
            listIsEmpty = stories.isNullOrEmpty(),
        )
        if (refreshPlan.source == StoryFeedSource.SEARCH) {
            Log.d(
                TAG,
                "Refreshing active search, queryLength=" + (if (lastSearch == null) 0 else lastSearch!!.length)
            )
            search(lastSearch)
            return
        }

        this.isRefreshIndicatorShowing = refreshPlan.showRefreshIndicator

        // cancel all ongoing
        val refreshGeneration = beginStoryListRefresh()
        activeStoryListStore.beginLoad(
            refreshing = refreshPlan.showRefreshIndicator,
            clearItems = refreshPlan.clearItems,
        )
        Log.d(
            TAG, ("Starting refresh generation=" + refreshGeneration
                    + ", type=" + storyType.label
                    + ", source=" + refreshPlan.source
                    + ", showSwipeRefreshIndicator=" + showSwipeRefreshIndicator
                    + ", showMainLoadingIndicator=" + showMainLoadingIndicator)
        )

        if (refreshPlan.clearItems) {
            loadingFailed = false
            loadingFailedServerError = false
            loadingFailedRateLimited = false
            showingCached = false
            userItemListInitialLoadInProgress = refreshPlan.source == StoryFeedSource.USER_ITEMS
            replaceStories(java.util.ArrayList<Story>(), true)
            updateHeader()
        }

        if (refreshPlan.recordRefreshTime) {
            lastLoaded = clock.now().toEpochMilliseconds()
        }

        when (refreshPlan.source) {
            StoryFeedSource.SEARCH -> Unit // Handled before starting a feed refresh.
            StoryFeedSource.ALGOLIA -> {
                loadTopStoriesSince(
                    currentAlgoliaTopStoriesStartTime,
                    refreshPlan.showRefreshIndicator,
                )
            }
            StoryFeedSource.BOOKMARKS -> {
                val refreshedStories = java.util.ArrayList<Story>()
                showingCached = false
                bookmarksChanged = false
                val bookmarks = savedItems.loadItems(
                    SavedItemSource.BOOKMARKS,
                    sortedByCreated = true,
                )
                for (bookmark in bookmarks) {
                    refreshedStories.add(Story("Loading...", bookmark.id, false, false))
                }
                bookmarkStories.clear()
                bookmarkStories.addAll(refreshedStories)
                replaceStories(filteredSavedItemStories, true)
                loadInitialVisibleStories(refreshGeneration)
                updateHeader()
                isRefreshIndicatorShowing = false
            }
            StoryFeedSource.USER_ITEMS -> {
                val hasCachedUserItemList = if (refreshPlan.loadCachedUserItems) {
                    loadUserItemListCache()
                } else {
                    savedItems.loadItems(currentUserItemListSource).isNotEmpty()
                }
                if (!refreshPlan.loadCachedUserItems) resumeInterruptedStoryLoads()
                syncUserItemListFromServer(showSwipeRefreshIndicator || hasCachedUserItemList)
            }
            StoryFeedSource.HISTORY -> {
                val refreshedStories = java.util.ArrayList<Story>()
                showingCached = false
                for (history in historyStore.load()) {
                    refreshedStories.add(
                        Story("Loading...", history.id, false, false, history.created),
                    )
                }
                replaceStories(refreshedStories, true)
                loadInitialVisibleStories(refreshGeneration)
                updateHeader()
                isRefreshIndicatorShowing = false
            }
            StoryFeedSource.FRONTPAGE_LINKS ->
                loadFrontpageLinkRows(storyType, refreshGeneration)
            StoryFeedSource.SCRAPED_FRONTPAGE ->
                loadScrapedFrontpageStories(storyType, refreshGeneration)
            StoryFeedSource.HACKER_NEWS_API -> {
                updateHeader()
                requestStoryFeed(storyType, refreshGeneration)
            }
        }
    }

    private fun loadScrapedFrontpageStories(storyType: StoryType, refreshGeneration: Int) {
        requestStoryFeed(storyType, refreshGeneration)
        updateHeader()
    }

    private fun loadMoreScrapedFrontpageStories(refreshGeneration: Int) {
        val storyType = this.currentStoryType
        if (storyResources == null) return
        val nextPageUrl = storyFeedRuntime.beginNextScrapedPage(
            activeStoryListStore,
            storyType,
        ) ?: return
        storiesPresenter.dispatch(
            StoriesAction.LoadNextScrapedPage(
                storyType = storyType,
                nextPageUrl = nextPageUrl,
                generation = refreshGeneration,
            ),
        )
    }

    private fun loadFrontpageLinkRows(storyType: StoryType?, refreshGeneration: Int) {
        requestStoryFeed(requireNotNull(storyType), refreshGeneration)
        updateHeader()
    }

    private fun requestStoryFeed(storyType: StoryType, refreshGeneration: Int) {
        storiesPresenter.dispatch(
            StoriesAction.LoadFeed(
                storyType = storyType,
                frontDay = frontPageDayParameter.takeIf { storyType.isFront },
                generation = refreshGeneration,
            ),
        )
    }

    private fun loadCommentMaster(
        story: Story,
        parentId: Int,
        loadGeneration: Int = storyListGeneration
    ) {
        if (parentId <= 0 || (story.commentMasterId > 0 && !TextUtils.isEmpty(story.commentMasterTitle))
            || !isCurrentStoryListGeneration(loadGeneration)
        ) {
            return
        }

        coroutineScope.launch {
            try {
                commentMasterResolver.resolveParentChain(story, parentId) ?: return@launch
                if (!isCurrentStoryListGeneration(loadGeneration)) return@launch
                if (stories?.indexOf(story) != -1) enqueueStoryRowChange(story, loadGeneration)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Failed to load comment master parent id=$parentId", error)
            }
        }
    }

    private fun loadUserItemListCache(): Boolean {
        showingCached = false
        loadingFailed = false
        loadingFailedServerError = false
        loadingFailedRateLimited = false
        userItemListInitialLoadInProgress = false

        val snapshot = loadCachedUserItemSnapshot()
        syncUserItemListStoriesToIds(snapshot.itemIds, snapshot.commentIds)
        return !snapshot.itemIds.isEmpty()
    }

    private fun syncUserItemListFromServer(showSwipeRefreshIndicator: Boolean) {
        val ctx = this.context
        if (ctx == null) {
            this.isRefreshIndicatorShowing = false
            userItemListInitialLoadInProgress = false
            updateHeader()
            return
        }

        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(requireContext())
            this.isRefreshIndicatorShowing = false
            userItemListInitialLoadInProgress = false
            loadingFailed = stories!!.isEmpty()
            loadingFailedRateLimited = false
            updateHeader()
            return
        }

        val syncSource = this.currentUserItemListSource
        userItemListInitialLoadInProgress = stories!!.isEmpty() && !showSwipeRefreshIndicator
        this.isRefreshIndicatorShowing = showSwipeRefreshIndicator
        updateHeader()

        storiesPresenter.dispatch(
            StoriesAction.SyncUserItems(
                source = syncSource,
                generation = storyListGeneration,
                savedAtMillis = clock.now().toEpochMilliseconds(),
            ),
        )
    }

    private fun syncVisibleUserItemListWithLocalCache() {
        if (storyResources == null || stories == null || !currentStoryType.isUserItemList) {
            return
        }

        val snapshot = loadCachedUserItemSnapshot()
        syncUserItemListStoriesToIds(snapshot.itemIds, snapshot.commentIds)
    }

    private fun loadCachedUserItemSnapshot(): SavedItemSnapshot =
        if (context != null) savedItems.loadSnapshot(currentUserItemListSource)
        else SavedItemSnapshot(emptyList(), emptySet())

    private fun syncUserItemListStoriesToIds(
        itemIds: List<Int>,
        commentIds: Set<Int>
    ): Boolean {
        val currentStories = if (userItemListStories.isEmpty()) {
            stories.orEmpty()
        } else {
            userItemListStories
        }
        val reconciliation = SavedItemStoryReconciler.reconcile(
            currentStories = currentStories,
            currentCommentIds = userItemListCommentIds,
            itemIds = itemIds,
            commentIds = commentIds,
        )
        if (!reconciliation.changed) return false
        replaceUserItemListStories(reconciliation.stories, commentIds)
        return true
    }

    private fun replaceUserItemListStories(
        refreshedStories: List<Story>,
        commentIds: Set<Int>
    ) {
        queue!!.cancelAll(requestTag)
        clearLoadingStoryState()
        userItemListStories.clear()
        userItemListStories.addAll(refreshedStories)
        userItemListCommentIds.clear()
        userItemListCommentIds.addAll(commentIds)
        replaceStories(this.filteredSavedItemStories, true)
        loadInitialVisibleStories()
        updateHeader()
    }

    private val filteredSavedItemStories: ArrayList<Story>
        get() {
            val sourceStories =
                if (currentStoryType.isBookmarks) bookmarkStories else userItemListStories
            return java.util.ArrayList(
                activeStoryListStore.filteredSavedItems(
                    source = sourceStories,
                    filter = currentSavedItemFilter,
                    keepUnloadedItems = currentStoryType.isBookmarks,
                )
            )
        }

    private val currentSavedItemFilter: SavedItemFilter
        get() = when (userItemListFilter) {
            USER_ITEM_LIST_FILTER_STORIES -> SavedItemFilter.STORIES
            USER_ITEM_LIST_FILTER_COMMENTS -> SavedItemFilter.COMMENTS
            else -> SavedItemFilter.BOTH
        }

    private fun SavedItemFilter.toStoredFilter(): Int = when (this) {
        SavedItemFilter.STORIES -> USER_ITEM_LIST_FILTER_STORIES
        SavedItemFilter.BOTH -> USER_ITEM_LIST_FILTER_BOTH
        SavedItemFilter.COMMENTS -> USER_ITEM_LIST_FILTER_COMMENTS
    }

    private fun shouldShowStoryForSavedItemFilter(story: Story): Boolean =
        activeStoryListStore.filteredSavedItems(
            source = listOf(story),
            filter = currentSavedItemFilter,
            keepUnloadedItems = currentStoryType.isBookmarks,
        ).isNotEmpty()

    private fun applySavedItemFilter() {
        replaceStories(this.filteredSavedItemStories, true)
        loadInitialVisibleStories()
        updateHeader()
    }

    private fun loadInitialVisibleStories(loadGeneration: Int = storyListGeneration) {
        val targetIndex = min(this.initialLoadCount, stories!!.size) - 1
        beginPreviewImagePrefetchRamp(targetIndex)
        loadStoriesThroughIndex(targetIndex, loadGeneration)
        retryUnsettledStoriesThroughIndex(targetIndex, loadGeneration)
    }

    private fun beginPreviewImagePrefetchRamp(targetIndex: Int) {
        previewPrefetchPlanner.begin(
            targetIndex,
            enabled = storyResources != null &&
                StoryPreviewPreferences.OFF !=
                    storyResources!!.settings.previewImageMode,
        )
    }

    private fun requestPreviewImagePrefetch(context: Context?, story: Story?) {
        val currentStories = stories
        if (context == null || storyResources == null || story == null || currentStories == null) return
        previewPrefetchPlanner.enqueue(story, currentStories).forEach {
            storyResources!!.prefetchPreviewImage(context, it)
        }
        scheduleNextPreviewImagePrefetchRampBatch()
    }

    private fun drainPreviewImagePrefetchQueue() {
        val context = this.context
        val currentStories = stories
        if (context == null || storyResources == null || currentStories == null) return
        previewPrefetchPlanner.drain(currentStories).forEach {
            storyResources!!.prefetchPreviewImage(context, it)
        }
        scheduleNextPreviewImagePrefetchRampBatch()
    }

    private fun scheduleNextPreviewImagePrefetchRampBatch() {
        if (previewPrefetchPlanner.requestNextBatchSchedule()) {
            previewImagePrefetchHandler.postDelayed(
                previewImagePrefetchRampRunnable,
                PREVIEW_IMAGE_PREFETCH_RAMP_DELAY_MS,
            )
        }
    }

    private fun resetPreviewImagePrefetchRamp() {
        previewImagePrefetchHandler.removeCallbacks(previewImagePrefetchRampRunnable)
        previewPrefetchPlanner.reset()
    }

    private fun scheduleLoadedPreviewImagePrefetchNearViewport() {
        prefetchLoadedPreviewImagesNearViewport(NO_POSITION, NO_POSITION)
    }

    private fun prefetchLoadedPreviewImagesNearViewport(
        firstVisibleItem: Int,
        lastVisibleItem: Int
    ) {
        val context = this.context
        val currentResources = storyResources
        val currentStories = stories
        if (context == null || currentResources == null || currentStories.isNullOrEmpty()) return
        if (StoryPreviewPreferences.OFF ==
            currentResources.settings.previewImageMode
        ) return

        val range = previewPrefetchPlanner.prefetchRange(
            storyCount = currentStories.size,
            initialLoadCount = initialLoadCount,
            firstVisibleItem = firstVisibleItem,
            lastVisibleItem = lastVisibleItem,
            paginationVisibleCount = activeStoryListStore.state.value.visibleStoryCount
                .takeIf { activeStoryListStore.state.value.paginationEnabled },
        ) ?: return

        beginPreviewImagePrefetchRamp(range.last)
        for (i in range) {
            requestPreviewImagePrefetch(context, currentStories[i])
        }
    }

    private fun updateSearchStatus() {
        hideUpdateButton()
        var restoredStories = false
        var shouldRefreshAfterRestore = false

        activity.setSearchBackEnabled(searching)

        if (searching) {
            useMainStoryList()
            saveStoriesBeforeSearch()

            // cancel all ongoing
            storiesPresenter.beginStoryLoadGeneration()
            activeStoryListStore.clearPendingPage()
            resetPreviewImagePrefetchRamp()
            invalidateAlgoliaLoad()
            queue!!.cancelAll(requestTag)
            this.isRefreshIndicatorShowing = false
            loadingFailed = false
            loadingFailedServerError = false
            loadingFailedRateLimited = false
            useSearchStoryList()
            setStoryType(StoryListTarget.SEARCH, storiesPresenter.state.value.mainStoryType)
            updateStoryRowMode()
            clearStoriesForSearchEntry()
        } else {
            shouldRefreshAfterRestore = loadPendingBeforeSearch
                    && storiesBeforeSearch != null && storiesBeforeSearch!!.isEmpty()
            loadPendingBeforeSearch = false

            storiesPresenter.beginStoryLoadGeneration()
            activeStoryListStore.clearPendingPage()
            resetPreviewImagePrefetchRamp()
            invalidateAlgoliaLoad()
            queue!!.cancelAll(requestTag)
            this.isRefreshIndicatorShowing = false
            useMainStoryList()
            restoredStories = restoreStoriesBeforeSearch()

            if (!restoredStories) {
                clearStories()
            }
            clearSearchStoriesAfterExit()
            useMainStoryList()
        }

        updateHeader(true)

        if (!searching) {
            if (restoredStories) {
                if (shouldRefreshAfterRestore) {
                    attemptRefresh()
                } else {
                    resumeInterruptedStoryLoads()
                }
            } else {
                attemptRefresh()
            }
            refreshBookmarksIfNeeded()
        }
    }

    private val currentAlgoliaTopStoriesStartTime: Int
        get() = searchStore.getTopStoriesStartTime(this.currentStoryType)

    private fun loadTopStoriesSince(start_i: Int, showSwipeRefreshIndicator: Boolean) {
        this.isRefreshIndicatorShowing = showSwipeRefreshIndicator
        storiesPresenter.dispatch(
            StoriesAction.LoadTopStories(
                storyType = currentStoryType,
                startTime = start_i,
            ),
        )
    }

    private fun search(query: String?, resetResultLimit: Boolean = true) {
        lastSearch = query
        storiesPresenter.dispatch(
            StoriesAction.Search(query.orEmpty(), resetResultLimit),
        )
    }

    private fun loadMoreAlgoliaResults() {
        if (searchStore.state.value.loading) return
        searchRuntime.beginLoadMore(activeStoryListStore)
        storiesPresenter.dispatch(StoriesAction.LoadMoreSearchResults)
    }

    private fun applyStorySearchState(state: StorySearchUiState) {
        if (storyResources == null || stories == null) return
        if (state.loading && !state.loadingMore) resetPreviewImagePrefetchRamp()
        val application = searchRuntime.apply(
            store = activeStoryListStore,
            state = state,
            searching = searching,
            activeTypeIsAlgolia = currentTypeIsAlgolia(),
        )
        if (!application.consumed) return
        if (application.completed) isRefreshIndicatorShowing = false
        if (application.contentApplied) scheduleLoadedPreviewImagePrefetchNearViewport()
        updateHeader()
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

    private fun currentTypeIsFront(): Boolean = currentStoryType.isFront

    private fun currentTypeIsScrapedFrontpage(): Boolean = currentStoryType.isScrapedFrontpage

    private fun currentTypeUsesSavedItemFilter(): Boolean = currentStoryType.usesSavedItemFilter()

    private fun currentSavedItemSourceHasItems(): Boolean {
        if (currentStoryType.isBookmarks) {
            return bookmarkStories.isNotEmpty()
        }
        if (currentStoryType.isUserItemList) {
            return userItemListStories.isNotEmpty()
        }
        return false
    }

    private fun isSameUserItemListType(type: StoryType, upvotedType: Boolean): Boolean {
        return if (upvotedType) type.isUpvoted else type.isFavorites
    }

    private val currentUserItemListSource: SavedItemSource
        get() = if (currentStoryType.isUpvoted)
            SavedItemSource.UPVOTED
        else
            SavedItemSource.FAVORITES

    private fun currentTypeUsesCommentRows(): Boolean = currentStoryType.usesCommentRows()

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

    private fun setStoryType(target: StoryListTarget, type: StoryType) {
        storiesPresenter.dispatch(StoriesAction.SelectStoryType(type, target))
    }

    private fun getTypeIndex(label: CharSequence?): Int {
        if (label == null || this.context == null) {
            return -1
        }

        return buildStoryTypeLabels(requireContext())
            .indexOfFirst { it.contentEquals(label) }
    }

    private fun updateStoryRowMode() {
        updatePaginationMode(StoryListTarget.MAIN)
        updatePaginationMode(StoryListTarget.SEARCH)
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
        showingCached = true
        this.isRefreshIndicatorShowing = false

        replaceStories(Utils.loadCachedStories(requireContext()))
        loadedTo = stories!!.size - 1
        loadingFailed = false
        loadingFailedServerError = false
        loadingFailedRateLimited = false
        updateHeader()
    }

    private fun hideUpdateButton() {
        storiesPresenter.dispatch(StoriesAction.DismissUpdateAvailability)
        syncComposeState()
    }

    private fun openComments(story: Story?, pos: Int, showWebsite: Boolean) {
        storyClickListener!!.openStory(story, pos, showWebsite)
    }

    interface StoryClickListener {
        fun openStory(story: Story?, pos: Int, showWebsite: Boolean)
    }

    companion object {
        private const val TAG = "StoriesCoordinator"
        private const val NO_POSITION = -1
        private const val NO_PENDING_LINK_SUMMARY_STORY_ID = -1
        private const val STATE_LINK_SUMMARY_STORY_ID =
            "com.simon.harmonichackernews.STATE_LINK_SUMMARY_STORY_ID"

        private const val STORY_VISIBLE_PREFETCH_THRESHOLD = 17
        private const val PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE = 10
        private const val PREVIEW_IMAGE_PREFETCH_RAMP_DELAY_MS = 450L

        private const val CLICK_INTERVAL: Long = 350
        private const val USER_ITEM_LIST_FILTER_STORIES = 0
        private const val USER_ITEM_LIST_FILTER_BOTH = 1
        private const val USER_ITEM_LIST_FILTER_COMMENTS = 2
    }
}
