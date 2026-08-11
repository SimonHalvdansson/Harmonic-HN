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
import com.simon.harmonichackernews.network.HttpStatusException
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.RequestQueue
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.SavedItemSnapshot
import com.simon.harmonichackernews.data.SavedItemSnapshots
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.SavedItemKeys
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.NetworkErrorUtils
import com.simon.harmonichackernews.network.HackerNewsUserItemsResult
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.failureDetails
import com.simon.harmonichackernews.network.StoryFeedRepository
import com.simon.harmonichackernews.network.StoryFeedResult
import com.simon.harmonichackernews.network.dto.applyTo
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
import com.simon.harmonichackernews.presentation.StoryLoadFailure
import com.simon.harmonichackernews.presentation.StorySearchMode
import com.simon.harmonichackernews.presentation.StorySearchUiState
import com.simon.harmonichackernews.presentation.StoryFeedLoadSession
import com.simon.harmonichackernews.presentation.StoryFeedRefreshPolicy
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
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.ui.settings.SettingsIntents.create
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesComposeController.Companion.create
import com.simon.harmonichackernews.ui.stories.StoryListState
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.FontUtils
import com.simon.harmonichackernews.utils.SettingsUtils
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
        clickedStoryIds = { historyStore.load().map { it.id } },
        isStoryClicked = historyStore::contains,
        shouldFilterStory = ::shouldFilterLoadedStory,
        shouldHideClickedStories = { hideClicked },
    )
    private val searchStore = storiesPresenter.searchStore
    private var restoredStateForCurrentView = false
    private var storyCacheController: StoryCacheController? = null
    private var linkSummaryBackCallback: OnBackPressedCallback? = null
    private var pendingLinkSummaryStoryId: Int = NO_PENDING_LINK_SUMMARY_STORY_ID

    private var mainAdapter: StoryListState? = null
    private var searchAdapter: StoryListState? = null
    private var adapter: StoryListState? = null
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
    private val storyFeedLoadSession = StoryFeedLoadSession(STORY_LOAD_STALE_TIMEOUT_MS)
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
        get() = storyFeedLoadSession.generation
    private val pendingStoryRowChangeGenerations = mutableMapOf<Int, Int>()
    private val pendingStoryRemovals = mutableMapOf<Int, PendingStoryRemoval>()
    private var algoliaLoading = false
    private var algoliaLoadMoreInProgress = false
    private var algoliaLoadMoreVisibleStoryCount = -1
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

    private class PendingStoryRemoval(val generation: Int, var updateHeader: Boolean)

    private var algoliaHitsPerPage: Int
        get() = if (searching) {
            sessionState.searchAlgoliaHitsPerPage
        } else {
            sessionState.mainAlgoliaHitsPerPage
        }
        set(value) {
            if (searching) {
                sessionState.searchAlgoliaHitsPerPage = value
            } else {
                sessionState.mainAlgoliaHitsPerPage = value
            }
        }
    private var lastAlgoliaTopStoriesStartTime: Int
        get() = if (searching) {
            sessionState.searchLastAlgoliaTopStoriesStartTime
        } else {
            sessionState.mainLastAlgoliaTopStoriesStartTime
        }
        set(value) {
            if (searching) {
                sessionState.searchLastAlgoliaTopStoriesStartTime = value
            } else {
                sessionState.mainLastAlgoliaTopStoriesStartTime = value
            }
        }
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
    private var scrapedFrontpageNextPageUrl by sessionState::scrapedFrontpageNextPageUrl
    private var scrapedFrontpageNextPageLoading = false
    private var scrapedFrontpageStoryType: StoryType? = StoryType.UNKNOWN

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
        setupAdapter()
        registerStoryAdapterDataObservers()
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
            if (story == null || stories == null || adapter == null) {
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
                    if (index < 0 || index >= buildTypeAdapterList(requireContext()).size || index == adapter!!.type) {
                        return
                    }
                    setStoryType(adapter!!, index)
                    updateAdapterCommentRows()
                    updateAdapterPaginationMode(adapter)
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

                override fun onSearchOption(kind: Int, index: Int) {
                    if (kind == StoriesComposeController.SEARCH_OPTION_SORT) {
                        storiesPresenter.dispatch(StoriesAction.SelectSearchSort(index))
                    } else if (kind == StoriesComposeController.SEARCH_OPTION_DATE) {
                        storiesPresenter.dispatch(StoriesAction.SelectSearchDateRange(index))
                    } else if (kind == StoriesComposeController.SEARCH_OPTION_POINTS) {
                        storiesPresenter.dispatch(StoriesAction.SelectSearchMinimumPoints(index))
                    } else if (kind == StoriesComposeController.SEARCH_OPTION_COMMENTS) {
                        storiesPresenter.dispatch(StoriesAction.SelectSearchMinimumComments(index))
                    }
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
                    handleLoadMore(adapter)
                }

                override fun onSavedFilterSelected(filter: Int) {
                    if (filter == userItemListFilter) {
                        return
                    }
                    userItemListFilter = filter
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

                override fun onMoreAction(action: Int) {
                    handleComposeMoreAction(action)
                }

                override fun onCacheStoriesConfirmed(storyCount: Int) {
                    SettingsUtils.setStoriesToCache(requireContext(), storyCount)
                    if (storyCacheController != null) {
                        storyCacheController!!.cacheStories()
                    }
                }

                override fun onLinkClick(story: Story) {
                    handleStoryLinkClick(adapter!!, stories!!.indexOf(story))
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
                    story: Story, position: Int, action: Int
                ) {
                    val controller = composeController
                    if (controller == null) return
                    if (action == StoriesComposeController.STORY_PREVIEW_ACTION_VOTE) {
                        toggleStoryVote(
                            story,
                            Runnable { controller.finishStoryPreviewAction(story.id, action) })
                    } else if (action == StoriesComposeController.STORY_PREVIEW_ACTION_READ) {
                        toggleStoryRead(story, position)
                    } else if (action == StoriesComposeController.STORY_PREVIEW_ACTION_BOOKMARK) {
                        toggleStoryBookmark(story)
                    } else if (action == StoriesComposeController.STORY_PREVIEW_ACTION_FAVORITE) {
                        toggleStoryFavorite(
                            story,
                            Runnable { controller.finishStoryPreviewAction(story.id, action) })
                    }
                }
            })
        requireActivity().attachStoriesComposeController(composeController!!)
        syncComposeState()
    }

    private fun showComposeStoryPreview(openedStory: Story) {
        val controller = composeController
        if (controller == null || stories == null || adapter == null) return
        val visibleCount = min(stories!!.size, adapter!!.visibleStoryItemCount)
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
            cardColors.add(adapter!!.resolveStoryCardBackgroundColor(context, story))
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

    private fun handleComposeMoreAction(action: Int) {
        if (action == StoriesComposeController.MORE_SETTINGS) {
            requireActivity().startActivity(create(requireActivity()))
        } else if (action == StoriesComposeController.MORE_LOGIN) {
            if (TextUtils.isEmpty(AccountUtils.getAccountUsername(requireActivity()))) {
                AccountUtils.showLoginPrompt(requireContext())
            } else {
                AccountUtils.deleteAccountDetails(requireActivity())
                refreshTypeSpinnerItemsIfNeeded()
                Toast.makeText(this.context, "Logged out", Toast.LENGTH_SHORT).show()
            }
        } else if (action == StoriesComposeController.MORE_PROFILE) {
            requireActivity().showUserDialog(
                AccountUtils.getAccountUsername(requireActivity()).orEmpty(), null
            )
        } else if (action == StoriesComposeController.MORE_CACHE) {
            showCacheStoriesDialog()
        } else if (action == StoriesComposeController.MORE_SUBMIT) {
            val submitIntent = ComposeEditorContract.createIntent(requireContext())
            submitIntent.putExtra(
                ComposeEditorContract.EXTRA_TYPE,
                ComposeEditorContract.TYPE_POST
            )
            startActivity(submitIntent)
        } else if (action == StoriesComposeController.MORE_CLEAR_HISTORY) {
            historyStore.clear()
            loadingFailed = false
            loadingFailedServerError = false
            loadingFailedRateLimited = false
            clearStories()
            updateHeader()
        }
        syncComposeState()
    }

    private fun showCacheStoriesDialog() {
        requireActivity().showCacheStoriesDialog()
    }

    private fun syncComposeState() {
        val controller = composeController
        if (controller == null || mainAdapter == null || searchAdapter == null || adapter == null || stories == null || this.context == null) {
            return
        }
        val context = requireContext()
        val labels = buildTypeAdapterList(context)
        val stringLabels = java.util.ArrayList(labels.map(CharSequence::toString))

        val bookmarksType = isBookmarksType(adapter!!.type)
        val historyType = isHistoryType(adapter!!.type)
        val favoritesType = isFavoritesType(adapter!!.type)
        val upvotedType = isUpvotedType(adapter!!.type)
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
                searchLoading = algoliaLoading,
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
                visibleStoryCount = adapter!!.visibleStoryItemCount,
            ),
        )

        controller.updateContent(
            mainStories,
            searchStories,
            StoryDisplaySettings.from(userSettings.story),
            stringLabels,
            mainAdapter!!.type,
            searching,
            lastSearch!!,
            searchStore.sortLabel,
            searchStore.dateRangeLabel,
            searchStore.minimumPointsLabel,
            searchStore.minimumCommentsLabel,
            StorySearchController.sortLabels,
            StorySearchController.dateRangeLabels,
            StorySearchController.minimumPointsLabels,
            StorySearchController.minimumCommentsLabels,
            searchStore.state.value.options.onlyClicked,
            shellPresentation.showLoading,
            this.isRefreshIndicatorShowing,
            loadingFailed,
            loadingFailedServerError,
            shellPresentation.loadingFailureMessage,
            showingCached,
            loadingFailed && !searching && Utils.hasCachedStories(context),
            shellPresentation.showEmptySavedList,
            getEmptySavedListText(
                historyType, favoritesType, upvotedType, savedItemSourceHasItems
            ),
            getEmptySavedListIcon(historyType, favoritesType, upvotedType),
            shellPresentation.showEmptySearch,
            updateButtonShowing,
            lastUpdated,
            adapter!!.hasLoadMoreButton(),
            adapter!!.isLoadMoreLoading(),
            mainAdapter!!.visibleStoryItemCount,
            searchAdapter!!.visibleStoryItemCount,
            !searching && currentTypeUsesSavedItemFilter() && savedItemSourceHasItems,
            userItemListFilter,
            !searching && currentTypeIsFront(),
            this.frontPageDayParameter,
            frontPageDay.selectedMillis > frontPageDay.earliestMillis,
            frontPageDay.selectedMillis < frontPageDay.latestMillis,
            !TextUtils.isEmpty(AccountUtils.getAccountUsername(requireActivity())),
            shellPresentation.canCacheStories,
            isHistoryType(adapter!!.type) && historyStore.size > 0,
            cacheProgressVisible,
            cacheProgress,
            cacheProgressMax,
            cacheProgressStatus,
            this.splitStoriesContentPaddingStart
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
        if (state == null || !state.initialized || mainAdapter == null || searchAdapter == null) {
            return false
        }

        val mainListState = mainStoryListStore.state.value
        val searchListState = searchStoryListStore.state.value
        mainAdapter!!.visibleStoryCount = mainListState.visibleStoryCount
        searchAdapter!!.visibleStoryCount = searchListState.visibleStoryCount
        mainAdapter!!.showLoadMoreButton = mainListState.canLoadMore
        searchAdapter!!.showLoadMoreButton = searchListState.canLoadMore
        updateAdapterCommentRows()

        syncActiveStoryListToSearchState()
        scrapedFrontpageNextPageLoading = false
        scrapedFrontpageStoryType = if (currentTypeIsScrapedFrontpage())
            this.currentStoryType
        else
            StoryType.UNKNOWN

        if (searching) {
            storiesBeforeSearch = java.util.ArrayList<Story>(mainStories)
            loadPendingBeforeSearch = mainStories.isEmpty()
                    && mainListState.failure == null && !isBookmarksType(
                mainAdapter!!.type
            ) && !isUserItemListType(mainAdapter!!.type)
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

    private val preferredTypeIndex: Int
        get() {
            val typeAdapterList =
                buildTypeAdapterList(requireContext())
            val preferredIndex =
                typeAdapterList.indexOf(userSettings.story.preferredStoryType)
            return if (preferredIndex >= 0) preferredIndex else 0
        }

    private fun buildTypeAdapterList(ctx: Context): java.util.ArrayList<CharSequence> {
        return StoryTypeAndroid.buildAdapterLabels(
            resources,
            ctx,
            shouldShowUserItemLists(ctx),
        )
    }

    private fun shouldShowUserItemLists(ctx: Context): Boolean {
        return AccountUtils.hasAccountDetails(ctx)
    }

    private fun refreshTypeSpinnerItemsIfNeeded() {
        if (adapter == null || this.context == null) {
            return
        }

        val previousTypeLabel = getTypeLabel(adapter!!.type)
        val showUserItemLists = shouldShowUserItemLists(requireContext())
        if (userItemListsDropdownVisible == showUserItemLists) {
            return
        }

        userItemListsDropdownVisible = showUserItemLists
        var newType = getTypeIndex(previousTypeLabel)
        if (newType < 0) {
            newType = 0
        }

        val newTypeLabel = getTypeLabel(newType)
        val typeChanged = !TextUtils.equals(previousTypeLabel, newTypeLabel)
        if (adapter!!.type != newType || typeChanged) {
            setStoryType(adapter!!, newType)
            updateAdapterCommentRows()
            updateAdapterPaginationMode(adapter)
        }

        if (typeChanged) {
            attemptStoryTypeRefresh()
        } else {
            syncComposeState()
        }
    }

    private fun enqueueStoryRowChange(story: Story, loadGeneration: Int) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return
        }

        // Story requests frequently finish during a fling. Keep the model update, but coalesce
        // the adapter notifications until holders are no longer rapidly moving through scrap.
        pendingStoryRowChangeGenerations[story.id] = loadGeneration
        postPendingStoryAdapterUpdateIfNotSettling()
    }

    private fun enqueueStoryRemoval(
        story: Story,
        loadGeneration: Int,
        updateHeader: Boolean
    ) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return
        }

        val pendingRemoval = pendingStoryRemovals[story.id]
        if (pendingRemoval == null || pendingRemoval.generation != loadGeneration) {
            pendingStoryRemovals[story.id] =
                PendingStoryRemoval(loadGeneration, updateHeader)
        } else {
            pendingRemoval.updateHeader = pendingRemoval.updateHeader or updateHeader
        }
        pendingStoryRowChangeGenerations.remove(story.id)
        postPendingStoryAdapterUpdateIfNotSettling()
    }

    private fun postPendingStoryAdapterUpdateIfNotSettling() {
        flushPendingStoryAdapterUpdates()
    }

    private fun flushPendingStoryAdapterUpdates() {
        val currentGeneration = storyListGeneration
        var shouldUpdateHeader = false
        val removalIds = java.util.ArrayList<Int>()
        for ((storyId, removal) in pendingStoryRemovals) {
            if (removal.generation == currentGeneration) {
                removalIds.add(storyId)
                shouldUpdateHeader = shouldUpdateHeader or removal.updateHeader
            }
        }
        pendingStoryRemovals.clear()
        for (storyId in removalIds) {
            val position = findStoryPositionById(storyId)
            if (position >= 0) {
                removeStoryAt(position, currentGeneration, false)
            }
        }
        pendingStoryRowChangeGenerations.clear()
        if (removalIds.isNotEmpty()) {
            loadVisibleStories(currentGeneration)
        }
        if (shouldUpdateHeader) {
            updateHeader()
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
        adapter = mainAdapter
        stories = mainStories
    }

    private fun useSearchStoryList() {
        adapter = searchAdapter
        stories = searchStories
    }

    private val activeStoryListStore: StoryListStore
        get() = if (stories === searchStories) searchStoryListStore else mainStoryListStore

    private fun storyListStoreFor(targetAdapter: StoryListState?): StoryListStore =
        if (targetAdapter === searchAdapter) searchStoryListStore else mainStoryListStore

    private fun setCanLoadMore(targetAdapter: StoryListState, canLoadMore: Boolean) {
        targetAdapter.showLoadMoreButton = canLoadMore
        storyListStoreFor(targetAdapter).setCanLoadMore(canLoadMore)
    }

    private fun useStoryListForAdapter(sourceAdapter: StoryListState) {
        if (sourceAdapter == searchAdapter) {
            useSearchStoryList()
        } else {
            useMainStoryList()
        }
    }

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
        searching = false
        lastSearch = ""
        resetSearchOptions()
        updateSearchStatus()
    }

    private fun resetSearchOptions() {
        storiesPresenter.dispatch(StoriesAction.ResetSearchOptions)
        updateSearchOptionChips(false)
    }

    private fun resetPaginationState() {
        loadedTo = -1
        clearPaginationLoadMoreState()
        updateAdapterPaginationMode(adapter)
        adapter!!.visibleStoryCount =
            if (adapter!!.paginationMode) StoryListState.PAGINATION_PAGE_SIZE else Int.MAX_VALUE
        activeStoryListStore.setVisibleStoryCount(adapter!!.visibleStoryCount)
    }

    private fun shouldUsePaginationForType(storyType: StoryType?): Boolean {
        return paginationMode || (storyType != null && storyType.isScrapedFrontpage)
    }

    private fun updateAdapterPaginationMode(targetAdapter: StoryListState?) {
        if (targetAdapter == null) {
            return
        }

        targetAdapter.paginationMode = shouldUsePaginationForType(getStoryType(targetAdapter.type))
        storyListStoreFor(targetAdapter).setPaginationEnabled(targetAdapter.paginationMode)
    }

    private fun resetAlgoliaResultLimit() {
        algoliaHitsPerPage = StorySearchController.ALGOLIA_HITS_INCREMENT
        algoliaLoadMoreInProgress = false
        if (adapter != null) {
            adapter!!.setLoadMoreLoading(false)
        }
        algoliaLoadMoreVisibleStoryCount = -1
    }

    private val initialLoadCount: Int
        get() = if (adapter != null && adapter!!.paginationMode) StoryListState.PAGINATION_PAGE_SIZE else 20

    private fun loadStoriesThroughIndex(targetIndex: Int, loadGeneration: Int) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return
        }

        var i = loadedTo + 1
        while (i <= targetIndex && i < stories!!.size) {
            loadedTo = i
            loadStory(stories!!.get(i), 0, loadGeneration)
            i++
        }
    }

    private fun startPaginationLoadMore(targetIndex: Int, loadGeneration: Int) {
        storyFeedLoadSession.beginPagination(
            stories = stories.orEmpty(),
            loadedThroughIndex = loadedTo,
            targetIndex = targetIndex,
            requestGeneration = loadGeneration,
        )
    }

    private fun finishPaginationLoadMoreStory(story: Story?, loadGeneration: Int) {
        if (story != null && storyFeedLoadSession.finishPaginationStory(story.id, loadGeneration)) {
            clearPaginationLoadMoreState()
        }
    }

    private fun clearPaginationLoadMoreState() {
        storyFeedLoadSession.clearPagination()
        if (adapter != null) {
            adapter!!.setLoadMoreLoading(false)
        }
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
                loadStory(story, 0, loadGeneration)
            }
        }
    }

    private val visibleLoadTargetIndex: Int
        get() {
            if (stories!!.isEmpty()) {
                return -1
            }

            var storiesToLoad = this.initialLoadCount
            if (adapter != null && adapter!!.paginationMode) {
                storiesToLoad = adapter!!.visibleStoryCount
            }

            return min(storiesToLoad, stories!!.size) - 1
        }

    private fun loadVisibleStories(loadGeneration: Int) {
        val targetIndex = this.visibleLoadTargetIndex
        loadStoriesThroughIndex(targetIndex, loadGeneration)
        retryUnsettledStoriesThroughIndex(targetIndex, loadGeneration)
    }

    private fun clearStories() {
        resetPreviewImagePrefetchRamp()
        val oldItemCount = adapter!!.itemCount
        activeStoryListStore.clear()
        resetPaginationState()
        setCanLoadMore(adapter!!, false)

        if (oldItemCount > 0) {
            adapter!!.notifyItemRangeRemoved(0, oldItemCount)
        }
    }

    private fun clearStoriesForSearchEntry() {
        clearStories()
    }

    private fun replaceStories(
        newStories: MutableList<Story>,
        notifyDataSetChanged: Boolean = false,
        showLoadMoreButton: Boolean = false
    ) {
        resetPreviewImagePrefetchRamp()
        if (notifyDataSetChanged) {
            resetPaginationState()
            setCanLoadMore(adapter!!, showLoadMoreButton)
            activeStoryListStore.replace(newStories, showLoadMoreButton, showingCached)
            adapter!!.notifyDataSetChanged()
            return
        }

        clearStories()
        setCanLoadMore(adapter!!, showLoadMoreButton)
        activeStoryListStore.replace(newStories, showLoadMoreButton, showingCached)

        val newItemCount = adapter!!.itemCount
        if (newItemCount > 0) {
            adapter!!.notifyItemRangeInserted(0, newItemCount)
        }
    }

    private fun replaceAlgoliaLoadMoreStories(
        newStories: MutableList<Story>,
        showLoadMoreButton: Boolean
    ) {
        resetPreviewImagePrefetchRamp()
        activeStoryListStore.replace(newStories, showLoadMoreButton, showingCached)
        setCanLoadMore(adapter!!, showLoadMoreButton)

        if (adapter != null && adapter!!.paginationMode) {
            val requestedVisibleCount = if (algoliaLoadMoreVisibleStoryCount > 0)
                algoliaLoadMoreVisibleStoryCount
            else
                adapter!!.visibleStoryCount
            adapter!!.visibleStoryCount =
                min(max(requestedVisibleCount, StoryListState.PAGINATION_PAGE_SIZE), stories!!.size)
        } else {
            adapter!!.visibleStoryCount = Int.MAX_VALUE
        }
        activeStoryListStore.setVisibleStoryCount(adapter!!.visibleStoryCount)

        adapter!!.notifyDataSetChanged()
    }

    private fun saveStoriesBeforeSearch() {
        if (storiesBeforeSearch != null) {
            return
        }

        storiesBeforeSearch = java.util.ArrayList<Story>(stories ?: emptyList())
        loadPendingBeforeSearch = stories!!.isEmpty()
                && !loadingFailed && !loadingFailedServerError && !isBookmarksType(adapter!!.type) && !isUserItemListType(
            adapter!!.type
        )
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
                loadStory(story, 0)
            }
        }
    }

    private fun setupAdapter() {
        paginationMode = userSettings.story.pagination

        mainAdapter = createStoryAdapter(mainStories)
        searchAdapter = createStoryAdapter(searchStories)
        if (sessionState.initialized) {
            val restoredMainType = getTypeIndex(sessionState.mainTypeLabel)
            if (restoredMainType >= 0) mainAdapter!!.type = restoredMainType
            val restoredSearchType = getTypeIndex(sessionState.searchTypeLabel)
            searchAdapter!!.type =
                if (restoredSearchType >= 0) restoredSearchType else mainAdapter!!.type
        }
        adapter = mainAdapter
        stories = mainStories

        configureStoryAdapter(mainAdapter!!)
        configureStoryAdapter(searchAdapter!!)
        setStoryType(mainAdapter!!, mainAdapter!!.type)
        setStoryType(searchAdapter!!, searchAdapter!!.type)
        updateAdapterCommentRows()
    }

    private fun rebuildStoryAdapters() {
        val previousMainType =
            if (mainAdapter != null) mainAdapter!!.type else this.preferredTypeIndex
        val previousSearchType =
            if (searchAdapter != null) searchAdapter!!.type else previousMainType
        val previousMainVisibleStoryCount = if (mainAdapter != null)
            mainAdapter!!.visibleStoryCount
        else
            (if (paginationMode) StoryListState.PAGINATION_PAGE_SIZE else Int.MAX_VALUE)
        val previousSearchVisibleStoryCount = if (searchAdapter != null)
            searchAdapter!!.visibleStoryCount
        else
            (if (paginationMode) StoryListState.PAGINATION_PAGE_SIZE else Int.MAX_VALUE)
        val previousMainShowLoadMoreButton = mainAdapter != null && mainAdapter!!.showLoadMoreButton
        val previousSearchShowLoadMoreButton =
            searchAdapter != null && searchAdapter!!.showLoadMoreButton

        if (mainAdapter != null) mainAdapter!!.dispose()
        if (searchAdapter != null) searchAdapter!!.dispose()

        setupAdapter()

        setStoryType(mainAdapter!!, previousMainType)
        setStoryType(searchAdapter!!, previousSearchType)
        updateAdapterPaginationMode(mainAdapter)
        updateAdapterPaginationMode(searchAdapter)
        mainAdapter!!.visibleStoryCount = previousMainVisibleStoryCount
        searchAdapter!!.visibleStoryCount = previousSearchVisibleStoryCount
        mainStoryListStore.setVisibleStoryCount(previousMainVisibleStoryCount)
        searchStoryListStore.setVisibleStoryCount(previousSearchVisibleStoryCount)
        setCanLoadMore(mainAdapter!!, previousMainShowLoadMoreButton)
        setCanLoadMore(searchAdapter!!, previousSearchShowLoadMoreButton)
        syncActiveStoryListToSearchState()
        updateAdapterCommentRows()

        registerStoryAdapterDataObservers()
        syncComposeState()
    }

    private fun syncInactiveStoryAdapterDisplaySettings() {
        if (mainAdapter == null || searchAdapter == null || adapter == null) {
            return
        }

        val inactiveAdapter = (if (adapter == mainAdapter) searchAdapter else mainAdapter)!!
        copyStoryAdapterDisplaySettings(adapter!!, inactiveAdapter)
        updateAdapterCommentRows(inactiveAdapter)
        if (inactiveAdapter.itemCount > 0) {
            inactiveAdapter.notifyItemRangeChanged(0, inactiveAdapter.itemCount)
        }
    }

    private fun copyStoryAdapterDisplaySettings(
        sourceAdapter: StoryListState,
        targetAdapter: StoryListState
    ) {
        StoryDisplaySettings.copyStateSettings(sourceAdapter, targetAdapter)
    }

    private fun createStoryAdapter(adapterStories: MutableList<Story>): StoryListState {
        return StoryListState(
            adapterStories,
            StoryDisplaySettings.from(userSettings.story),
            this.preferredTypeIndex
        )
    }

    private fun configureStoryAdapter(configuredAdapter: StoryListState) {
        val retainedListState = storyListStoreFor(configuredAdapter).state.value
        updateAdapterPaginationMode(configuredAdapter)
        configuredAdapter.visibleStoryCount = if (sessionState.initialized) {
            retainedListState.visibleStoryCount
        } else if (configuredAdapter.paginationMode) {
            StoryListState.PAGINATION_PAGE_SIZE
        } else {
            Int.MAX_VALUE
        }
        configuredAdapter.showLoadMoreButton = retainedListState.canLoadMore
        if (!sessionState.initialized) {
            storyListStoreFor(configuredAdapter).setVisibleStoryCount(configuredAdapter.visibleStoryCount)
        }
        configuredAdapter.setChangedListener(::onStoryListStateChanged)
    }

    private fun handleStoryLinkClick(
        sourceAdapter: StoryListState,
        position: Int
    ) {
        useStoryListForAdapter(sourceAdapter)
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
                useIntegratedWebView = SettingsUtils.shouldUseIntegratedWebView(requireContext()),
            ),
        )
    }

    private fun applyStoriesEffect(effect: StoriesEffect) {
        when (effect) {
            is StoriesEffect.OpenComments -> {
                markStoryClicked(effect.story)
                resolveStoryPosition(effect.story, effect.position)
                    .takeIf { it >= 0 }
                    ?.let { adapter?.updateStoryClickedState(it) }
                openComments(effect.story, effect.position, effect.showWebsite)
            }
            is StoriesEffect.OpenExternalStory -> {
                if (effect.story.isFrontpageLink) {
                    effect.story.clicked = true
                } else {
                    markStoryClicked(effect.story)
                }
                resolveStoryPosition(effect.story, effect.position)
                    .takeIf { it >= 0 }
                    ?.let { adapter?.updateStoryClickedState(it) }
                externalLinks.open(ExternalLinkRequest(effect.url))
            }
            is StoriesEffect.RetryStory -> {
                effect.story.loadingFailed = false
                loadStory(effect.story, 0)
                resolveStoryPosition(effect.story, effect.position)
                    .takeIf { it >= 0 }
                    ?.let { adapter?.notifyItemChanged(it) }
            }
        }
    }

    private fun resolveStoryPosition(story: Story, fallback: Int): Int {
        val currentStories = stories
        if (currentStories != null && fallback in currentStories.indices &&
            currentStories[fallback].id == story.id
        ) {
            return fallback
        }
        return currentStories?.indexOfFirst { it.id == story.id } ?: fallback
    }

    private fun handleLoadMore(sourceAdapter: StoryListState?) {
        if (sourceAdapter == null) {
            return
        }
        useStoryListForAdapter(sourceAdapter)
        if (adapter!!.paginationMode && adapter!!.visibleStoryCount < stories!!.size) {
            val newLoadedTo = min(
                loadedTo + StoryListState.PAGINATION_PAGE_SIZE,
                stories!!.size - 1
            )
            startPaginationLoadMore(newLoadedTo, storyListGeneration)
            adapter!!.setLoadMoreLoading(true)
            adapter!!.loadNextPage()
            activeStoryListStore.setVisibleStoryCount(adapter!!.visibleStoryCount)
            if (!storyFeedLoadSession.hasPendingPaginationStories()) {
                clearPaginationLoadMoreState()
            }
            loadStoriesThroughIndex(newLoadedTo, storyListGeneration)
            retryUnsettledStoriesThroughIndex(newLoadedTo, storyListGeneration)
        } else if (adapter!!.showLoadMoreButton && currentTypeIsScrapedFrontpage()) {
            loadMoreScrapedFrontpageStories(storyListGeneration)
        } else if (adapter!!.showLoadMoreButton) {
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
        if (currentPosition >= 0 && currentPosition < stories!!.size) {
            adapter!!.updateStoryClickedState(currentPosition)
        }
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
        val currentPosition = stories!!.indexOf(story)
        if (currentPosition >= 0) {
            adapter!!.updateStoryClickedState(currentPosition)
        }
    }

    private fun toggleStoryBookmark(story: Story?) {
        if (!this.isAdded || story == null) {
            return
        }
        val bookmarked = savedItemActions.toggleBookmark(story.id)
        if (!bookmarked) {
            if (isBookmarksType(adapter!!.type)) {
                bookmarkStories.remove(story)
                val currentPosition = stories!!.indexOf(story)
                if (currentPosition >= 0) {
                    removeStoryAt(currentPosition, storyListGeneration, true)
                }
                updateHeader()
                return
            }
        }
        val currentPosition = stories!!.indexOf(story)
        if (currentPosition >= 0) {
            adapter!!.notifyItemChanged(currentPosition)
        }
    }

    private fun toggleStoryVote(
        story: Story?,
        completion: Runnable
    ) {
        if (!this.isAdded || story == null || adapter == null || stories == null) {
            completion.run()
            return
        }
        val actionGeneration = storyListGeneration
        val actionAdapter = adapter
        val actionStories = stories
        val context = requireContext()
        val action = savedItemActions.beginVote(
            itemId = story.id,
            isComment = false,
            direction = if (savedItemActions.isUpvoted(story.id, false)) "un" else "up",
        )
        val currentPosition = stories!!.indexOf(story)
        if (currentPosition >= 0) {
            adapter!!.notifyItemChanged(currentPosition)
        }
        coroutineScope.launch {
            val outcome = savedItemActions.execute(action)
            if (outcome is SavedItemActionOutcome.Success) {
                completion.run()
                return@launch
            }
            if (isCurrentStoryActionContext(actionGeneration, actionAdapter, actionStories)) {
                val restoredPosition = stories!!.indexOf(story)
                if (restoredPosition >= 0) {
                    adapter!!.notifyItemChanged(restoredPosition)
                }
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
        if (!this.isAdded || story == null || adapter == null || stories == null) {
            completion.run()
            return
        }
        val actionGeneration = storyListGeneration
        val actionAdapter = adapter
        val actionStories = stories
        val actionIsFavoritesList = isFavoritesType(actionAdapter!!.type)
        val action = savedItemActions.beginFavorite(story.id)
        val currentlyFavorited = action.previousPresent
        val optimisticIndex = stories!!.indexOf(story)
        if (optimisticIndex >= 0) {
            if (currentlyFavorited && actionIsFavoritesList) {
                removeStoryAt(optimisticIndex, storyListGeneration, true)
                updateHeader()
            } else {
                adapter!!.notifyItemChanged(optimisticIndex)
            }
        }
        coroutineScope.launch {
            val outcome = savedItemActions.execute(action)
            if (outcome is SavedItemActionOutcome.Success) {
                completion.run()
                return@launch
            }
            if (!isCurrentStoryActionContext(actionGeneration, actionAdapter, actionStories)) {
                completion.run()
                return@launch
            }
            val currentIndex = stories!!.indexOf(story)
            if (currentlyFavorited && actionIsFavoritesList && currentIndex == -1) {
                val restoreIndex =
                    if (optimisticIndex >= 0) min(optimisticIndex, stories!!.size) else 0
                activeStoryListStore.mutateStories { add(restoreIndex, story) }
                adapter!!.notifyItemInserted(restoreIndex)
                updateHeader()
            } else if (currentIndex >= 0) {
                adapter!!.notifyItemChanged(currentIndex)
            }
            completion.run()
        }
    }

    private fun isCurrentStoryActionContext(
        generation: Int,
        expectedAdapter: StoryListState?,
        expectedStories: MutableList<Story>?
    ): Boolean {
        return this.isAdded
                && generation == storyListGeneration && adapter == expectedAdapter && stories === expectedStories
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
        val displayUpdate = displaySettings.applyToState(adapter!!)

        if (displayUpdate.requiresRebuild) {
            rebuildStoryAdapters()
        } else if ((displayUpdate.itemsChanged || fontCacheChanged) && adapter!!.itemCount > 0) {
            adapter!!.notifyItemRangeChanged(0, adapter!!.itemCount)
        }

        if (displayUpdate.previewImageModeChanged) {
            scheduleLoadedPreviewImagePrefetchNearViewport()
        }

        if (displayUpdate.compactHeaderChanged) {
            updateHeader()
        }

        val newPaginationMode = storyPreferences.pagination
        if (paginationMode != newPaginationMode) {
            val oldItemCount = adapter!!.itemCount
            paginationMode = newPaginationMode
            updateAdapterPaginationMode(adapter)
            resetPaginationState()

            val newItemCount = adapter!!.itemCount
            val sharedItemCount = min(oldItemCount, newItemCount)

            if (sharedItemCount > 0) {
                adapter!!.notifyItemRangeChanged(0, sharedItemCount)
            }

            if (oldItemCount > newItemCount) {
                adapter!!.notifyItemRangeRemoved(newItemCount, oldItemCount - newItemCount)
            } else if (newItemCount > oldItemCount) {
                adapter!!.notifyItemRangeInserted(oldItemCount, newItemCount - oldItemCount)
            }
        }

        if (hideJobsChanged) {
            attemptRefresh()
        }

        syncInactiveStoryAdapterDisplaySettings()
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
        if (!bookmarksChanged || adapter == null || searching
            || !started || !isBookmarksType(adapter!!.type)
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
        if (adapter == null || this.context == null) {
            return
        }

        val storyPreferences = userSettings.story
        val previewImageMode = storyPreferences.previewImageMode
        val previewImageModeChanged = adapter!!.previewImageMode != previewImageMode
        val tintCardUsingPreview = storyPreferences.tintCardUsingPreview
        val storyCardShellChanged =
            adapter!!.tintCardUsingPreview != tintCardUsingPreview && !adapter!!.cardStyle
        val preferredFont = storyPreferences.font
        val fontCacheChanged = TextUtils.isEmpty(FontUtils.font) || FontUtils.font != preferredFont

        if (fontCacheChanged) {
            FontUtils.init(requireContext())
        }

        adapter!!.previewImageMode = previewImageMode
        adapter!!.showSummary = storyPreferences.showSummary
        adapter!!.tintCardUsingPreview = tintCardUsingPreview
        adapter!!.font = preferredFont

        if (storyCardShellChanged) {
            rebuildStoryAdapters()
        } else {
            syncInactiveStoryAdapterDisplaySettings()
            notifyStoryDisplaySettingsChanged(mainAdapter)
            notifyStoryDisplaySettingsChanged(searchAdapter)
        }

        if (previewImageModeChanged) {
            scheduleLoadedPreviewImagePrefetchNearViewport()
        }
    }

    private fun notifyStoryDisplaySettingsChanged(targetAdapter: StoryListState?) {
        if (targetAdapter != null && targetAdapter.itemCount > 0) {
            targetAdapter.notifyItemRangeChanged(0, targetAdapter.itemCount)
        }
    }

    private fun syncStoriesWithHistoriesIfNeeded() {
        val currentHistoriesChangeVersion = historyStore.changeVersion
        if (historiesChangeVersion == currentHistoriesChangeVersion || adapter == null || stories == null) {
            return
        }

        historiesChangeVersion = currentHistoriesChangeVersion

        if (searching && searchStore.state.value.options.onlyClicked) {
            var clickedStateChanged = false
            for (story in stories) {
                if (story.clicked) {
                    story.clicked = false
                    clickedStateChanged = true
                }
            }

            if (clickedStateChanged) {
                adapter!!.notifyItemRangeChanged(0, adapter!!.itemCount)
            }
            return
        }

        if (isHistoryType(adapter!!.type)) {
            attemptRefresh()
            return
        }

        if (hideClicked) {
            if (removeClickedStoriesFromCurrentList()) {
                return
            }

            attemptRefresh()
            return
        }

        var clickedStateChanged = false
        for (story in stories) {
            val clicked = historyStore.contains(story.id)
            if (story.clicked != clicked) {
                story.clicked = clicked
                clickedStateChanged = true
            }
        }

        if (clickedStateChanged) {
            adapter!!.notifyItemRangeChanged(0, adapter!!.itemCount)
        }
    }

    private fun removeClickedStoriesFromCurrentList(): Boolean {
        var removedStories = false

        for (i in stories!!.indices.reversed()) {
            val story = stories!!.get(i)
            if (historyStore.contains(story.id)) {
                removeStoryAt(i, storyListGeneration, false)
                removedStories = true
            }
        }

        if (removedStories) {
            loadVisibleStories(storyListGeneration)
            updateHeader()
        }

        return removedStories
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
        unregisterStoryAdapterDataObservers()
        if (mainAdapter != null) mainAdapter!!.dispose()
        if (searchAdapter != null) searchAdapter!!.dispose()
        if (queue != null) {
            storyFeedLoadSession.beginGeneration()
            resetPreviewImagePrefetchRamp()
            invalidateAlgoliaLoad()
            queue!!.cancelAll(requestTag)
        }
        coroutineScope.cancel()
        pendingStoryRowChangeGenerations.clear()
        pendingStoryRemovals.clear()
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
        mainAdapter = null
        searchAdapter = null
        adapter = null
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
                hackerNewsRepository.getStory(masterStory.id)?.let { loadedMaster ->
                    sourceStory.updateCommentMasterFrom(loadedMaster)
                }
                if (!isAdded || adapter == null) return@launch
                val index = stories?.indexOf(sourceStory) ?: -1
                if (index >= 0) adapter?.notifyItemChanged(index)
                openComments(sourceStory.toCommentMasterStory() ?: masterStory, position, false)
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

        var removedStory: Story? = null
        activeStoryListStore.mutateStories { removedStory = removeAt(index) }
        finishPaginationLoadMoreStory(removedStory, loadGeneration)
        clearStoryLoadState(removedStory!!)
        if (index <= loadedTo) {
            loadedTo = max(-1, loadedTo - 1)
        }

        if (adapter != null && adapter!!.paginationMode) {
            adapter!!.notifyDataSetChanged()
        } else if (adapter != null) {
            adapter!!.notifyItemRemoved(index)
            adapter!!.updateStoryIndicesFromPosition(index)
        }

        if (loadVisibleReplacement) {
            loadVisibleStories(loadGeneration)
        }
    }

    private fun shouldFilterLoadedStory(story: Story?): Boolean {
        return story?.let { storyVisibilityPolicy.shouldHide(it, currentStoryType) } == true
    }

    private fun isStoryLoadInProgress(story: Story?): Boolean {
        return story?.let {
            storyFeedLoadSession.isStoryInProgress(it.id, clock.now().toEpochMilliseconds())
        } == true
    }

    private fun markStoryLoadStarted(story: Story?): Long {
        val startedAt = clock.now().toEpochMilliseconds()
        story?.let { storyFeedLoadSession.markStoryStarted(it.id, startedAt) }
        return startedAt
    }

    private fun clearStoryLoadState(story: Story?) {
        story?.let { storyFeedLoadSession.clearStory(it.id) }
    }

    private fun clearStoryLoadState(story: Story?, startedAt: Long) {
        if (story == null) {
            return
        }

        storyFeedLoadSession.clearStory(story.id, startedAt)
    }

    private fun isCurrentStoryLoad(story: Story?, startedAt: Long): Boolean {
        if (story == null) {
            return false
        }

        return storyFeedLoadSession.isCurrentStoryLoad(story.id, startedAt)
    }

    private fun clearLoadingStoryState() {
        storyFeedLoadSession.clearStoryLoads()
    }

    private fun loadStory(story: Story, attempt: Int, loadGeneration: Int = storyListGeneration) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return
        }

        val pendingRemoval = pendingStoryRemovals[story.id]
        if (pendingRemoval != null && pendingRemoval.generation == loadGeneration) {
            return
        }

        if (story.loaded) {
            val index = stories!!.indexOf(story)
            if (index >= 0 && shouldFilterLoadedStory(story)) {
                enqueueStoryRemoval(story, loadGeneration, false)
            }
            return
        }

        if (attempt >= 3 || isStoryLoadInProgress(story)) {
            return
        }

        val startedAt = markStoryLoadStarted(story)

        coroutineScope.launch {
            try {
                val item = hackerNewsApi.getItem(story.id)
                if (!isCurrentStoryLoad(story, startedAt)) return@launch
                clearStoryLoadState(story, startedAt)
                if (!isCurrentStoryListGeneration(loadGeneration)) return@launch
                if (stories?.indexOf(story) == -1) return@launch
                if (item == null || !item.applyTo(story, isHistoryType(adapter!!.type))) {
                    enqueueStoryRemoval(story, loadGeneration, false)
                    return@launch
                }

                finishPaginationLoadMoreStory(story, loadGeneration)
                if (story.isComment && currentTypeUsesCommentRows()) {
                    loadCommentMaster(story, story.parentId, 0, loadGeneration)
                }
                if (currentTypeUsesSavedItemFilter() && !shouldShowStoryForSavedItemFilter(story)) {
                    enqueueStoryRemoval(story, loadGeneration, true)
                    return@launch
                }
                if (shouldFilterLoadedStory(story)) {
                    enqueueStoryRemoval(story, loadGeneration, false)
                    return@launch
                }
                context?.let { requestPreviewImagePrefetch(it, story) }
                enqueueStoryRowChange(story, loadGeneration)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!isCurrentStoryLoad(story, startedAt)) return@launch
                clearStoryLoadState(story, startedAt)
                if (!isCurrentStoryListGeneration(loadGeneration) || story.loaded) return@launch
                Log.w(TAG, "Failed to load story id=${story.id}, attempt=$attempt", error)
                story.loadingFailed = true
                if (attempt >= 2) finishPaginationLoadMoreStory(story, loadGeneration)
                drainPreviewImagePrefetchQueue()
                if (stories?.indexOf(story) != -1) {
                    enqueueStoryRowChange(story, loadGeneration)
                    loadStory(story, attempt + 1, loadGeneration)
                }
            }
        }
    }

    fun attemptRefresh() {
        attemptRefresh(false)
    }

    private fun attemptStoryTypeRefresh() {
        attemptRefresh(false, true)
    }

    private fun invalidateAlgoliaLoad() {
        searchStore.cancel(clearResults = false)
        algoliaLoading = false
    }

    private fun beginStoryListRefresh(): Int {
        storyFeedLoadSession.beginGeneration()
        resetPreviewImagePrefetchRamp()
        resetScrapedFrontpagePaginationState()
        invalidateAlgoliaLoad()
        queue!!.cancelAll(requestTag)
        return storyListGeneration
    }

    private fun resetScrapedFrontpagePaginationState() {
        scrapedFrontpageNextPageUrl = null
        scrapedFrontpageNextPageLoading = false
        scrapedFrontpageStoryType = StoryType.UNKNOWN
        if (adapter != null) {
            adapter!!.setLoadMoreLoading(false)
        }
    }

    private fun isCurrentStoryListGeneration(generation: Int): Boolean {
        return storyFeedLoadSession.isCurrent(generation)
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
                resetAlgoliaResultLimit()
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
                coroutineScope.launch {
                    try {
                        val result = storyFeedRepository.load(storyType) as StoryFeedResult.ItemIds
                        if (!isCurrentStoryListGeneration(refreshGeneration)) return@launch
                        isRefreshIndicatorShowing = false
                        showingCached = false
                        replaceStories(createLoadingStoriesFromIds(result.ids))
                        activeStoryListStore.setFailure(null)
                        updateHeader()
                        loadInitialVisibleStories(refreshGeneration)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        if (!isCurrentStoryListGeneration(refreshGeneration)) return@launch
                        isRefreshIndicatorShowing = false
                        activeStoryListStore.fail(StoryFeedRefreshPolicy.failureFor(error))
                        Log.w(
                            TAG,
                            "Story list request failed for type=${storyType.label}, " +
                                "generation=$refreshGeneration",
                            error,
                        )
                        updateHeader()
                    }
                }
            }
        }
    }

    private fun loadScrapedFrontpageStories(storyType: StoryType, refreshGeneration: Int) {
        val ctx = this.context
        if (ctx == null) {
            this.isRefreshIndicatorShowing = false
            loadingFailed = true
            loadingFailedServerError = false
            loadingFailedRateLimited = false
            Log.w(
                TAG, ("Scraped frontpage refresh failed before request: missing context for type="
                        + storyType.label + ", generation=" + refreshGeneration)
            )
            updateHeader()
            return
        }

        val frontDay = if (storyType.isFront) this.frontPageDayParameter else null
        Log.d(
            TAG, ("Fetching scraped frontpage type=" + storyType.label
                    + ", path=" + storyType.hackerNewsPath
                    + ", commentsPage=" + storyType.usesCommentRows()
                    + ", day=" + frontDay
                    + ", generation=" + refreshGeneration)
        )
        val callbackReceived = booleanArrayOf(false)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!callbackReceived[0] && this@StoriesCoordinator.isAdded
                && adapter != null && this@StoriesCoordinator.currentStoryType == storyType && isCurrentStoryListGeneration(
                    refreshGeneration
                )
            ) {
                Log.w(
                    TAG, ("Scraped frontpage request still pending for type=" + storyType.label
                            + ", path=" + storyType.hackerNewsPath
                            + ", generation=" + refreshGeneration
                            + ", loadingFailed=" + loadingFailed
                            + ", networkAvailable=" + connectivity.isOnline())
                )
            }
        }, 15000)
        coroutineScope.launch {
            try {
                val page = (
                    storyFeedRepository.load(storyType, frontDay) as StoryFeedResult.Scraped
                ).page
                val itemIds = page.itemIds
                val commentIds = page.commentIds
                val nextPageUrl = page.nextPageUrl
                callbackReceived[0] = true
                if (!this@StoriesCoordinator.isAdded || adapter == null || this@StoriesCoordinator.currentStoryType != storyType || !isCurrentStoryListGeneration(
                        refreshGeneration
                    )
                ) {
                    Log.d(
                        TAG,
                        ("Ignoring stale scraped frontpage success for type=" + storyType.label
                                + ", generation=" + refreshGeneration
                                + ", currentGeneration=" + storyListGeneration
                                + ", isAdded=" + this@StoriesCoordinator.isAdded
                                + ", adapterPresent=" + (adapter != null)
                                + ", currentType=" + this@StoriesCoordinator.currentStoryType.label)
                    )
                    return@launch
                }

                this@StoriesCoordinator.isRefreshIndicatorShowing = false
                loadingFailed = itemIds.isEmpty()
                loadingFailedServerError = false
                loadingFailedRateLimited = false
                showingCached = false
                scrapedFrontpageStoryType = storyType
                scrapedFrontpageNextPageUrl = nextPageUrl
                scrapedFrontpageNextPageLoading = false
                Log.d(
                    TAG, ("Scraped frontpage success for type=" + storyType.label
                            + ", generation=" + refreshGeneration
                            + ", itemCount=" + itemIds.size
                            + ", commentIdCount=" + commentIds.size
                            + ", hasNextPage=" + !TextUtils.isEmpty(nextPageUrl) + ", loadingFailed=" + loadingFailed)
                )

                if (!loadingFailed) {
                    replaceStories(
                        createLoadingStoriesFromIds(itemIds, HashSet<Int>(commentIds)),
                        false,
                        !TextUtils.isEmpty(scrapedFrontpageNextPageUrl)
                    )
                }

                updateHeader()
                loadInitialVisibleStories(refreshGeneration)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                callbackReceived[0] = true
                val summary = "Couldn't fetch ${storyType.label.lowercase()}"
                val response = error.message
                if (!this@StoriesCoordinator.isAdded || adapter == null || this@StoriesCoordinator.currentStoryType != storyType || !isCurrentStoryListGeneration(
                        refreshGeneration
                    )
                ) {
                    Log.d(
                        TAG,
                        ("Ignoring stale scraped frontpage failure for type=" + storyType.label
                                + ", generation=" + refreshGeneration
                                + ", currentGeneration=" + storyListGeneration
                                + ", isAdded=" + this@StoriesCoordinator.isAdded
                                + ", adapterPresent=" + (adapter != null)
                                + ", currentType=" + this@StoriesCoordinator.currentStoryType.label
                                + ", summary=" + summary
                                + ", response=" + response)
                    )
                    return@launch
                }

                this@StoriesCoordinator.isRefreshIndicatorShowing = false
                loadingFailed = true
                loadingFailedServerError = false
                loadingFailedRateLimited = NetworkErrorUtils.isRateLimitedText(summary, response)
                Log.w(
                    TAG, ("Scraped frontpage request failed for type=" + storyType.label
                            + ", path=" + storyType.hackerNewsPath
                            + ", generation=" + refreshGeneration
                            + ", summary=" + summary
                            + ", response=" + response)
                )
                updateHeader()
            }
        }

        updateHeader()
    }

    private fun loadMoreScrapedFrontpageStories(refreshGeneration: Int) {
        val ctx = this.context
        val storyType = this.currentStoryType
        if (ctx == null || adapter == null || scrapedFrontpageNextPageLoading
            || storyType != scrapedFrontpageStoryType || TextUtils.isEmpty(
                scrapedFrontpageNextPageUrl
            )
        ) {
            return
        }

        scrapedFrontpageNextPageLoading = true
        adapter!!.setLoadMoreLoading(true)
        val nextPageUrl = scrapedFrontpageNextPageUrl
        coroutineScope.launch {
            try {
                val page = storyFeedRepository.loadNextScrapedPage(
                    storyType,
                    checkNotNull(nextPageUrl),
                )
                val itemIds = page.itemIds
                val commentIds = page.commentIds
                if (!this@StoriesCoordinator.isAdded || adapter == null || this@StoriesCoordinator.currentStoryType != storyType || scrapedFrontpageStoryType != storyType || !isCurrentStoryListGeneration(
                        refreshGeneration
                    )
                ) {
                    return@launch
                }

                scrapedFrontpageNextPageLoading = false
                adapter!!.setLoadMoreLoading(false)
                scrapedFrontpageNextPageUrl = page.nextPageUrl
                val newStories =
                    createNewLoadingStoriesFromIds(itemIds.toMutableList(), HashSet(commentIds))
                activeStoryListStore.mutateStories { addAll(newStories) }
                setCanLoadMore(adapter!!, !TextUtils.isEmpty(scrapedFrontpageNextPageUrl))
                if (adapter!!.paginationMode && !newStories.isEmpty()) {
                    adapter!!.visibleStoryCount =
                        min(adapter!!.visibleStoryCount + newStories.size, stories!!.size)
                }
                activeStoryListStore.setVisibleStoryCount(adapter!!.visibleStoryCount)
                adapter!!.notifyDataSetChanged()
                loadVisibleStories(refreshGeneration)
                updateHeader()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!this@StoriesCoordinator.isAdded || adapter == null || this@StoriesCoordinator.currentStoryType != storyType || scrapedFrontpageStoryType != storyType || !isCurrentStoryListGeneration(
                        refreshGeneration
                    )
                ) {
                    return@launch
                }

                scrapedFrontpageNextPageLoading = false
                adapter!!.setLoadMoreLoading(false)
                setCanLoadMore(adapter!!, true)
                adapter!!.notifyDataSetChanged()
                updateHeader()
            }
        }
    }

    private fun loadFrontpageLinkRows(storyType: StoryType?, refreshGeneration: Int) {
        val ctx = this.context
        if (ctx == null) {
            this.isRefreshIndicatorShowing = false
            loadingFailed = true
            loadingFailedServerError = false
            loadingFailedRateLimited = false
            updateHeader()
            return
        }

        coroutineScope.launch {
            try {
                val linkRows = (
                    storyFeedRepository.load(requireNotNull(storyType)) as
                        StoryFeedResult.LinkDirectory
                ).stories
                if (!this@StoriesCoordinator.isAdded || adapter == null || this@StoriesCoordinator.currentStoryType != storyType || !isCurrentStoryListGeneration(
                        refreshGeneration
                    )
                ) {
                    return@launch
                }

                this@StoriesCoordinator.isRefreshIndicatorShowing = false
                loadingFailed = linkRows.isEmpty()
                loadingFailedServerError = false
                loadingFailedRateLimited = false
                showingCached = false

                if (!loadingFailed) {
                    replaceStories(linkRows.toMutableList())
                    loadedTo = stories!!.size - 1
                }

                updateHeader()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!this@StoriesCoordinator.isAdded || adapter == null || this@StoriesCoordinator.currentStoryType != storyType || !isCurrentStoryListGeneration(
                        refreshGeneration
                    )
                ) {
                    return@launch
                }

                this@StoriesCoordinator.isRefreshIndicatorShowing = false
                loadingFailed = true
                loadingFailedServerError = false
                loadingFailedRateLimited = error is HttpStatusException && error.statusCode == 429
                updateHeader()
            }
        }

        updateHeader()
    }

    private fun createNewLoadingStoriesFromIds(
        itemIds: MutableList<Int>,
        commentIds: MutableSet<Int>
    ): java.util.ArrayList<Story> {
        val existingStoryIds = HashSet<Int>()
        for (story in stories!!) {
            existingStoryIds.add(story.id)
        }

        val newItemIds = java.util.ArrayList<Int>()
        for (id in itemIds) {
            if (!existingStoryIds.contains(id)) {
                newItemIds.add(id)
            }
        }

        return createLoadingStoriesFromIds(newItemIds, commentIds)
    }

    private fun createLoadingStoriesFromIds(
        itemIds: List<Int>,
        commentIds: Set<Int> = emptySet(),
    ): java.util.ArrayList<Story> {
        val refreshedStories = java.util.ArrayList<Story>()
        val ctx = this.context

        for (id in itemIds) {
            if (hideClicked && historyStore.contains(id)) {
                continue
            }

            val story = Story("Loading...", id, false, historyStore.contains(id))
            val isComment = commentIds.contains(id)
            story.isComment = isComment
            if (Utils.loadCachedStorySummary(ctx, story) && shouldFilterLoadedStory(story)) {
                continue
            }
            if (isComment) {
                story.isComment = true
            }

            refreshedStories.add(story)
        }

        return refreshedStories
    }

    private fun loadCommentMaster(
        story: Story,
        parentId: Int,
        attempt: Int,
        loadGeneration: Int = storyListGeneration
    ) {
        if (parentId <= 0 || attempt >= 8 || (story.commentMasterId > 0 && !TextUtils.isEmpty(story.commentMasterTitle))
            || !isCurrentStoryListGeneration(loadGeneration)
        ) {
            return
        }

        coroutineScope.launch {
            try {
                val parent = hackerNewsRepository.getStory(parentId) ?: return@launch
                if (!isCurrentStoryListGeneration(loadGeneration)) return@launch
                if (parent.isComment) {
                    loadCommentMaster(story, parent.parentId, attempt + 1, loadGeneration)
                    return@launch
                }
                story.updateCommentMasterFrom(parent)
                if (stories?.indexOf(story) != -1) enqueueStoryRowChange(story, loadGeneration)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Failed to load comment master parent id=$parentId", error)
                if (attempt < 2 && isCurrentStoryListGeneration(loadGeneration)) {
                    loadCommentMaster(story, parentId, attempt + 1, loadGeneration)
                }
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
        val upvotedTypeForSync = syncSource == SavedItemSource.UPVOTED
        userItemListInitialLoadInProgress = stories!!.isEmpty() && !showSwipeRefreshIndicator
        this.isRefreshIndicatorShowing = showSwipeRefreshIndicator
        updateHeader()

        val syncGeneration = storyListGeneration
        coroutineScope.launch {
            try {
                val path = if (upvotedTypeForSync) "upvoted" else "favorites"
                val userItems = when (
                    val result = hackerNewsUserService.getUserItems(
                        path,
                        loginRequired = upvotedTypeForSync,
                    )
                ) {
                    is HackerNewsUserItemsResult.Success -> result.items
                    is HackerNewsUserItemsResult.Failure ->
                        throw UserItemSyncException(result.summary, result.detail)
                    is HackerNewsUserItemsResult.Captcha ->
                        throw UserItemSyncException(
                            "Captcha required",
                            "HN asked for a captcha before syncing $path.",
                        )
                }
                val itemIds = userItems.itemIds
                val commentIds = userItems.commentIds
                if (!this@StoriesCoordinator.isAdded || adapter == null || !isSameUserItemListType(
                        adapter!!.type,
                        upvotedTypeForSync
                    ) || !isCurrentStoryListGeneration(syncGeneration)
                ) {
                    return@launch
                }

                if (this@StoriesCoordinator.context == null) {
                    return@launch
                }

                val snapshot = SavedItemSnapshots.normalize(itemIds, commentIds)
                if (savedItems.loadSnapshot(syncSource) != snapshot) {
                    savedItems.saveSnapshot(
                        syncSource,
                        snapshot,
                        clock.now().toEpochMilliseconds(),
                    )
                }
                syncUserItemListStoriesToIds(snapshot.itemIds, snapshot.commentIds)

                userItemListInitialLoadInProgress = false
                loadingFailed = false
                loadingFailedServerError = false
                loadingFailedRateLimited = false
                this@StoriesCoordinator.isRefreshIndicatorShowing = false
                updateHeader()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!this@StoriesCoordinator.isAdded || adapter == null || !isSameUserItemListType(
                        adapter!!.type,
                        upvotedTypeForSync
                    ) || !isCurrentStoryListGeneration(syncGeneration)
                ) {
                    return@launch
                }

                val summary = (error as? UserItemSyncException)?.summary
                    ?: "Couldn't sync ${if (upvotedTypeForSync) "upvoted" else "favorites"}"
                val response = (error as? UserItemSyncException)?.detail ?: error.message
                this@StoriesCoordinator.isRefreshIndicatorShowing = false
                userItemListInitialLoadInProgress = false
                loadingFailed = stories!!.isEmpty()
                loadingFailedRateLimited = NetworkErrorUtils.isRateLimitedText(summary, response)
                updateHeader()
                Toast.makeText(requireContext(), summary, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncVisibleUserItemListWithLocalCache() {
        if (adapter == null || stories == null || !isUserItemListType(adapter!!.type)) {
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
                if (isBookmarksType(adapter!!.type)) bookmarkStories else userItemListStories
            return java.util.ArrayList(
                activeStoryListStore.filteredSavedItems(
                    source = sourceStories,
                    filter = currentSavedItemFilter,
                    keepUnloadedItems = isBookmarksType(adapter!!.type),
                )
            )
        }

    private val currentSavedItemFilter: SavedItemFilter
        get() = when (userItemListFilter) {
            USER_ITEM_LIST_FILTER_STORIES -> SavedItemFilter.STORIES
            USER_ITEM_LIST_FILTER_COMMENTS -> SavedItemFilter.COMMENTS
            else -> SavedItemFilter.BOTH
        }

    private fun shouldShowStoryForSavedItemFilter(story: Story): Boolean =
        activeStoryListStore.filteredSavedItems(
            source = listOf(story),
            filter = currentSavedItemFilter,
            keepUnloadedItems = isBookmarksType(adapter!!.type),
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
            enabled = adapter != null &&
                SettingsUtils.STORY_PREVIEW_IMAGE_OFF != adapter!!.previewImageMode,
        )
    }

    private fun requestPreviewImagePrefetch(context: Context?, story: Story?) {
        val currentStories = stories
        if (context == null || adapter == null || story == null || currentStories == null) return
        previewPrefetchPlanner.enqueue(story, currentStories).forEach {
            adapter!!.prefetchPreviewImage(context, it)
        }
        scheduleNextPreviewImagePrefetchRampBatch()
    }

    private fun drainPreviewImagePrefetchQueue() {
        val context = this.context
        val currentStories = stories
        if (context == null || adapter == null || currentStories == null) return
        previewPrefetchPlanner.drain(currentStories).forEach {
            adapter!!.prefetchPreviewImage(context, it)
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
        val currentAdapter = adapter
        val currentStories = stories
        if (context == null || currentAdapter == null || currentStories.isNullOrEmpty()) return
        if (SettingsUtils.STORY_PREVIEW_IMAGE_OFF == currentAdapter.previewImageMode) return

        val range = previewPrefetchPlanner.prefetchRange(
            storyCount = currentStories.size,
            initialLoadCount = initialLoadCount,
            firstVisibleItem = firstVisibleItem,
            lastVisibleItem = lastVisibleItem,
            paginationVisibleCount = currentAdapter.visibleStoryCount
                .takeIf { currentAdapter.paginationMode },
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
            storyFeedLoadSession.beginGeneration()
            resetPreviewImagePrefetchRamp()
            invalidateAlgoliaLoad()
            queue!!.cancelAll(requestTag)
            this.isRefreshIndicatorShowing = false
            loadingFailed = false
            loadingFailedServerError = false
            loadingFailedRateLimited = false
            useSearchStoryList()
            setStoryType(searchAdapter!!, mainAdapter!!.type)
            updateAdapterCommentRows()
            clearStoriesForSearchEntry()
        } else {
            shouldRefreshAfterRestore = loadPendingBeforeSearch
                    && storiesBeforeSearch != null && storiesBeforeSearch!!.isEmpty()
            loadPendingBeforeSearch = false

            storyFeedLoadSession.beginGeneration()
            resetPreviewImagePrefetchRamp()
            invalidateAlgoliaLoad()
            queue!!.cancelAll(requestTag)
            this.isRefreshIndicatorShowing = false
            useMainStoryList()
            restoredStories = restoreStoriesBeforeSearch()

            if (!restoredStories) {
                clearStories()
            }
            useSearchStoryList()
            clearStories()
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
        lastAlgoliaTopStoriesStartTime = start_i
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

        if (adapter != null) {
            adapter!!.setLoadMoreLoading(true)
        }
        algoliaLoadMoreInProgress = true
        if (adapter != null && adapter!!.paginationMode) {
            algoliaLoadMoreVisibleStoryCount =
                adapter!!.visibleStoryCount + StoryListState.PAGINATION_PAGE_SIZE
        } else {
            algoliaLoadMoreVisibleStoryCount = -1
        }
        storiesPresenter.dispatch(StoriesAction.LoadMoreSearchResults)
    }

    private fun applyStorySearchState(state: StorySearchUiState) {
        if (state.mode == StorySearchMode.NONE || adapter == null || stories == null) return
        if (state.mode == StorySearchMode.QUERY && !searching) return
        if (state.mode == StorySearchMode.TOP_STORIES && (searching || !currentTypeIsAlgolia())) return

        algoliaHitsPerPage = state.hitsPerPage
        if (state.mode == StorySearchMode.QUERY) {
            lastSearch = state.query
        } else {
            lastAlgoliaTopStoriesStartTime = state.topStoriesStartTime
        }

        algoliaLoading = state.loading
        loadingFailed = state.failure != null
        loadingFailedServerError = state.failure == StoryLoadFailure.NOT_FOUND
        loadingFailedRateLimited = state.failure == StoryLoadFailure.RATE_LIMITED
        if (state.loading) {
            algoliaLoadMoreInProgress = state.loadingMore
            if (state.loadingMore) {
                adapter!!.setLoadMoreLoading(true)
            } else if (state.mode == StorySearchMode.QUERY && stories!!.isNotEmpty()) {
                clearStories()
            }
            updateHeader()
            return
        }

        this.isRefreshIndicatorShowing = false
        adapter!!.setLoadMoreLoading(false)
        if (state.failure != null) {
            algoliaLoadMoreInProgress = false
            algoliaLoadMoreVisibleStoryCount = -1
            updateHeader()
            return
        }

        showingCached = false
        val nextStories = state.stories.toMutableList()
        if (algoliaLoadMoreInProgress) {
            replaceAlgoliaLoadMoreStories(nextStories, state.canLoadMore)
        } else {
            replaceStories(nextStories, false, state.canLoadMore)
        }
        loadedTo = stories!!.size - 1
        activeStoryListStore.markLoadedThrough(loadedTo)
        activeStoryListStore.finishLoadMore(state.canLoadMore)
        scheduleLoadedPreviewImagePrefetchNearViewport()
        algoliaLoadMoreInProgress = false
        algoliaLoadMoreVisibleStoryCount = -1
        updateHeader()
    }

    private fun registerStoryAdapterDataObservers() {
        mainAdapter?.setChangedListener(::onStoryListStateChanged)
        searchAdapter?.setChangedListener(::onStoryListStateChanged)
    }

    private fun unregisterStoryAdapterDataObservers() {
        if (mainAdapter != null) mainAdapter!!.setChangedListener(null)
        if (searchAdapter != null) searchAdapter!!.setChangedListener(null)
    }

    private fun onStoryListStateChanged(story: Story?) {
        when {
            story == null -> activeStoryListStore.notifyStoryChanged()
            mainStories.contains(story) -> mainStoryListStore.notifyStoryChanged()
            searchStories.contains(story) -> searchStoryListStore.notifyStoryChanged()
        }
        if (story == null) {
            syncComposeState()
        } else {
            composeController?.invalidateStory(story.id)
        }
    }

    fun onStoryPreviewImageLoaded(storyId: Int) {
        // The comments pane receives a bundle copy of the story, so its header preview load does
        // not otherwise invalidate the row that is still visible in the stories pane.
        if (storyId <= 0) return
        val currentContext = context ?: return
        val currentAdapter = adapter ?: return
        val currentStories = stories ?: return
        currentStories.firstOrNull { it.id == storyId }?.let { story ->
            currentAdapter.prefetchPreviewImage(currentContext, story)
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

    private fun isBookmarksType(type: Int): Boolean = getStoryType(type).isBookmarks

    private fun isHistoryType(type: Int): Boolean = getStoryType(type).isHistory

    private fun isFavoritesType(type: Int): Boolean = getStoryType(type).isFavorites

    private fun isUpvotedType(type: Int): Boolean = getStoryType(type).isUpvoted

    private fun isUserItemListType(type: Int): Boolean = getStoryType(type).isUserItemList

    private fun currentTypeUsesSavedItemFilter(): Boolean = currentStoryType.usesSavedItemFilter()

    private fun currentSavedItemSourceHasItems(): Boolean {
        val type = checkNotNull(adapter).type
        if (isBookmarksType(type)) {
            return bookmarkStories.isNotEmpty()
        }
        if (isUserItemListType(type)) {
            return userItemListStories.isNotEmpty()
        }
        return false
    }

    private fun isSameUserItemListType(type: Int, upvotedType: Boolean): Boolean {
        return if (upvotedType) isUpvotedType(type) else isFavoritesType(type)
    }

    private val currentUserItemListSource: SavedItemSource
        get() = if (isUpvotedType(adapter!!.type))
            SavedItemSource.UPVOTED
        else
            SavedItemSource.FAVORITES

    private fun currentTypeUsesCommentRows(): Boolean = currentStoryType.usesCommentRows()

    private val currentStoryType: StoryType
        get() = getStoryType(adapter!!.type)

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

        val typeAdapterList = buildTypeAdapterList(ctx)
        return typeAdapterList.getOrNull(type)
    }

    private fun setStoryType(targetAdapter: StoryListState, type: Int) {
        targetAdapter.type = type
        val label = getTypeLabel(type)?.toString()
        if (targetAdapter === searchAdapter) {
            sessionState.searchTypeLabel = label
        } else {
            sessionState.mainTypeLabel = label
        }
    }

    private fun getTypeIndex(label: CharSequence?): Int {
        if (label == null || this.context == null) {
            return -1
        }

        return buildTypeAdapterList(requireContext())
            .indexOfFirst { it.contentEquals(label) }
    }

    private fun updateAdapterCommentRows() {
        updateAdapterCommentRows(mainAdapter)
        updateAdapterCommentRows(searchAdapter)
    }

    private fun updateAdapterCommentRows(targetAdapter: StoryListState?) {
        if (targetAdapter == null) {
            return
        }

        targetAdapter.allowCommentRows = getStoryType(targetAdapter.type).usesCommentRows()
        targetAdapter.disableClickedEffects =
            targetAdapter.allowCommentRows || isHistoryType(targetAdapter.type)
        updateAdapterPaginationMode(targetAdapter)
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

    private class UserItemSyncException(
        val summary: String,
        val detail: String?,
    ) : Exception(detail ?: summary)

    companion object {
        private const val TAG = "StoriesCoordinator"
        private const val NO_POSITION = -1
        private const val NO_PENDING_LINK_SUMMARY_STORY_ID = -1
        private const val STATE_LINK_SUMMARY_STORY_ID =
            "com.simon.harmonichackernews.STATE_LINK_SUMMARY_STORY_ID"

        private const val STORY_VISIBLE_PREFETCH_THRESHOLD = 17
        private const val STORY_LOAD_STALE_TIMEOUT_MS = 30000L
        private const val PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE = 10
        private const val PREVIEW_IMAGE_PREFETCH_RAMP_DELAY_MS = 450L

        private const val CLICK_INTERVAL: Long = 350
        private const val USER_ITEM_LIST_FILTER_STORIES = 0
        private const val USER_ITEM_LIST_FILTER_BOTH = 1
        private const val USER_ITEM_LIST_FILTER_COMMENTS = 2
    }
}
