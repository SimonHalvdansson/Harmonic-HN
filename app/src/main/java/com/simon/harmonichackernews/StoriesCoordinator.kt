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
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.HackerNewsActionResult
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
import com.simon.harmonichackernews.presentation.StoryListStore
import com.simon.harmonichackernews.presentation.StoryLoadFailure
import com.simon.harmonichackernews.presentation.StorySearchMode
import com.simon.harmonichackernews.presentation.StorySearchUiState
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.resources.*
import com.simon.harmonichackernews.settings.AndroidUserSettings
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
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
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val hackerNewsUserService = HackerNewsUserService(
        NetworkComponent.hackerNewsSession,
        platformServices.credentials,
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
    private val loadingStoryStartTimes = mutableMapOf<Int, Long>()
    private var filterWords: java.util.ArrayList<String>? = null
    private var filterDomains: java.util.ArrayList<String>? = null
    private var filteredUsers: MutableSet<String>? = null
    private var hideJobs = false
    private var alwaysOpenComments = false
    private var hideClicked = false
    private var historiesChangeVersion = -1L
    private var bookmarkPreferences: SharedPreferences?
    private var bookmarksChanged = false
    private val bookmarkPreferenceChangeListener =
        OnSharedPreferenceChangeListener { sharedPreferences: SharedPreferences?, key: String? ->
            if (Utils.KEY_SHARED_PREFERENCES_BOOKMARKS == key) {
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
    private var storyListGeneration = 0
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
    private val paginationLoadMoreStoryIds: MutableSet<Int> = HashSet<Int>()
    private var paginationLoadMoreGeneration = -1

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
    private val previewImagePrefetchQueue = java.util.ArrayList<Story>()
    private val queuedPreviewImagePrefetchStoryIds: MutableSet<Int> = HashSet<Int>()
    private val requestedPreviewImagePrefetchStoryIds: MutableSet<Int> = HashSet<Int>()
    private val previewImagePrefetchRampRunnable: Runnable = object : Runnable {
        override fun run() {
            previewImagePrefetchRampScheduled = false
            previewImagePrefetchRampSlotsRemaining = PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE
            drainPreviewImagePrefetchQueue()
        }
    }
    private var previewImagePrefetchRampScheduled = false
    private var previewImagePrefetchRampComplete = false
    private var previewImagePrefetchRampSlotsRemaining: Int = PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE
    private var previewImagePrefetchRampTargetIndex = -1

    var lastLoaded by sessionState::lastLoaded
    var lastClick: Long = 0
    private var updateButtonShowing by sessionState::updateButtonShowing
    private var predictiveSearchBackInProgress = false
    private var predictiveSearchBackProgress = 0f
    private var finishSearchBackFromCurrentVisualState = false
    private var userItemListsDropdownVisible = false
    private var userItemListInitialLoadInProgress = false
    private var userItemListFilter by sessionState::userItemListFilter
    private var frontPageDayUtc: Calendar? = sessionState.frontPageDayUtcMillis
        .takeIf { it >= 0L }
        ?.let { millis ->
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = millis
                clearTime(this)
            }
        }
        set(value) {
            field = value
            sessionState.frontPageDayUtcMillis = value?.timeInMillis ?: -1L
        }
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
        filterWords = Utils.getFilterWords(requireContext())
        filterDomains = Utils.getFilterDomains(requireContext())
        filteredUsers = Utils.getFilteredUsers(requireContext())
        val storyPreferences = userSettings.story
        hideJobs = storyPreferences.hideJobs
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
            for (index in stories!!.indices) {
                val oldStory = stories!!.get(index)
                if (oldStory != null && story.id == oldStory.id) {
                    if (!TextUtils.equals(
                            oldStory.title,
                            story.title
                        ) || oldStory.descendants != story.descendants || oldStory.score != story.score || oldStory.time != story.time || !TextUtils.equals(
                            oldStory.url,
                            story.url
                        )
                    ) {
                        oldStory.title = story.title
                        oldStory.descendants = story.descendants
                        oldStory.score = story.score
                        oldStory.time = story.time
                        oldStory.url = story.url
                    }
                    break
                }
            }
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
            activity,
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
                        val selected = Utils.isUpvoted(requireContext(), story.id, false)
                        toggleStoryVote(
                            story, selected,
                            Runnable { controller.finishStoryPreviewAction(story.id, action) })
                    } else if (action == StoriesComposeController.STORY_PREVIEW_ACTION_READ) {
                        toggleStoryRead(story, position)
                    } else if (action == StoriesComposeController.STORY_PREVIEW_ACTION_BOOKMARK) {
                        toggleStoryBookmark(
                            story, position,
                            Utils.isBookmarked(requireContext(), story.id)
                        )
                    } else if (action == StoriesComposeController.STORY_PREVIEW_ACTION_FAVORITE) {
                        val selected = Utils.isFavorited(requireContext(), story.id)
                        toggleStoryFavorite(
                            story, position, selected,
                            Runnable { controller.finishStoryPreviewAction(story.id, action) })
                    }
                }
            })
        if (requireActivity() is MainActivity) {
            requireActivity().attachStoriesComposeController(composeController!!)
        }
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
            positionArray[index] = sourcePositions.get(index)!!
            colorArray[index] = cardColors.get(index)!!
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
        if (requireActivity() is MainActivity) {
            requireActivity().showCacheStoriesDialog()
        }
    }

    private fun syncComposeState() {
        val controller = composeController
        if (controller == null || mainAdapter == null || searchAdapter == null || adapter == null || stories == null || this.context == null) {
            return
        }
        val context = requireContext()
        val labels = buildTypeAdapterList(context)
        val stringLabels = java.util.ArrayList<String>(labels.size)
        for (label in labels) {
            stringLabels.add(if (label == null) "" else label.toString())
        }

        val bookmarksType = isBookmarksType(adapter!!.type)
        val historyType = isHistoryType(adapter!!.type)
        val favoritesType = isFavoritesType(adapter!!.type)
        val upvotedType = isUpvotedType(adapter!!.type)
        val userItemListType = favoritesType || upvotedType
        val savedItemSourceHasItems = currentSavedItemSourceHasItems()
        val hasSubmittedSearch = !TextUtils.isEmpty(lastSearch!!.trim { it <= ' ' })
        val showEmptySearch = searching
                && hasSubmittedSearch
                && stories!!.isEmpty()
                && !algoliaLoading && !loadingFailed && !loadingFailedServerError
        val showEmptySaved = !searching && stories!!.isEmpty()
                && !loadingFailed && !loadingFailedServerError && (bookmarksType
                || historyType
                || (userItemListType && !userItemListInitialLoadInProgress && !this.isRefreshIndicatorShowing))
        val showLoading = if (searching)
            algoliaLoading
        else
            (stories!!.isEmpty()
                    && !loadingFailed && !loadingFailedServerError && !bookmarksType && !historyType && (!userItemListType || userItemListInitialLoadInProgress))
        val loadingMessage: String?
        if (loadingFailedRateLimited) {
            loadingMessage = "Rate limited"
        } else if (!connectivity.isOnline()) {
            loadingMessage = "No internet connection"
        } else {
            loadingMessage = "Loading failed"
        }
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
        val hasVisibleStories = adapter!!.visibleStoryItemCount > 0
        val canCache = hasVisibleStories
                && !showingCached && !cacheInProgress && connectivity.isOnline()

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
            showLoading,
            this.isRefreshIndicatorShowing,
            loadingFailed,
            loadingFailedServerError,
            loadingMessage,
            showingCached,
            loadingFailed && !searching && Utils.hasCachedStories(context),
            showEmptySaved,
            getEmptySavedListText(
                historyType, favoritesType, upvotedType, savedItemSourceHasItems
            ),
            getEmptySavedListIcon(historyType, favoritesType, upvotedType),
            showEmptySearch,
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
            getFrontPageDayUtc().after(this.earliestFrontPageDayUtc),
            getFrontPageDayUtc().before(this.latestFrontPageDayUtc),
            !TextUtils.isEmpty(AccountUtils.getAccountUsername(requireActivity())),
            canCache,
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
        if (loadingFailed || loadingFailedServerError || !stories!!.isEmpty()) {
            return false
        }
        if (searching) {
            return !TextUtils.isEmpty(lastSearch)
        }
        return !isBookmarksType(adapter!!.type) && !isHistoryType(adapter!!.type) && !isUserItemListType(
            adapter!!.type
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
        return ctx != null && AccountUtils.hasAccountDetails(ctx)
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

    private fun getFrontPageDayUtc(): Calendar {
        if (frontPageDayUtc == null) {
            frontPageDayUtc = this.latestFrontPageDayUtc
        }
        return frontPageDayUtc!!
    }

    private val latestFrontPageDayUtc: Calendar
        get() {
            val latest =
                Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            latest.add(Calendar.DAY_OF_MONTH, -1)
            clearTime(latest)
            return latest
        }

    private val earliestFrontPageDayUtc: Calendar
        get() {
            val earliest =
                Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            earliest.set(Calendar.YEAR, 2007)
            earliest.set(Calendar.MONTH, Calendar.FEBRUARY)
            earliest.set(Calendar.DAY_OF_MONTH, 19)
            clearTime(earliest)
            return earliest
        }

    private fun clearTime(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    private val frontPageDayParameter: String
        get() {
            val format =
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            format.setTimeZone(TimeZone.getTimeZone("UTC"))
            return format.format(getFrontPageDayUtc().getTime())
        }

    private fun shiftFrontPageDay(days: Int) {
        var day = getFrontPageDayUtc().clone() as Calendar
        day.add(Calendar.DAY_OF_MONTH, days)
        val latest = this.latestFrontPageDayUtc
        if (day.after(latest)) {
            day = latest
        }
        val earliest = this.earliestFrontPageDayUtc
        if (day.before(earliest)) {
            day = earliest
        }
        clearTime(day)
        frontPageDayUtc = day
        if (currentTypeIsFront()) {
            attemptStoryTypeRefresh()
        }
    }

    private fun showFrontPageDatePicker() {
        if (composeController == null) {
            return
        }
        composeController!!.showFrontDatePicker(
            getFrontPageDayUtc().getTimeInMillis(),
            this.earliestFrontPageDayUtc.getTimeInMillis(),
            this.latestFrontPageDayUtc.getTimeInMillis()
        )
    }

    private fun selectFrontPageDay(selection: Long) {
        var selectedDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        selectedDay.setTimeInMillis(selection)
        clearTime(selectedDay)
        val latest = this.latestFrontPageDayUtc
        if (selectedDay.after(latest)) {
            selectedDay = latest
        }
        val earliest = this.earliestFrontPageDayUtc
        if (selectedDay.before(earliest)) {
            selectedDay = earliest
        }
        frontPageDayUtc = selectedDay
        if (currentTypeIsFront()) {
            attemptStoryTypeRefresh()
        }
    }

    private fun getEmptySavedListText(
        historyType: Boolean,
        favoritesType: Boolean,
        upvotedType: Boolean,
        savedItemSourceHasItems: Boolean
    ): String {
        if (historyType) {
            return "No history"
        }
        if (favoritesType) {
            if (!savedItemSourceHasItems) {
                return "No favorites"
            }
            if (userItemListFilter == USER_ITEM_LIST_FILTER_STORIES) {
                return "No favorite stories"
            }
            if (userItemListFilter == USER_ITEM_LIST_FILTER_COMMENTS) {
                return "No favorite comments"
            }
            return "No favorites"
        }
        if (upvotedType) {
            if (!savedItemSourceHasItems) {
                return "No upvoted items"
            }
            if (userItemListFilter == USER_ITEM_LIST_FILTER_STORIES) {
                return "No upvoted stories"
            }
            if (userItemListFilter == USER_ITEM_LIST_FILTER_COMMENTS) {
                return "No upvoted comments"
            }
            return "No upvoted items"
        }
        if (!savedItemSourceHasItems) {
            return "No bookmarks"
        }
        if (userItemListFilter == USER_ITEM_LIST_FILTER_STORIES) {
            return "No bookmarked stories"
        }
        if (userItemListFilter == USER_ITEM_LIST_FILTER_COMMENTS) {
            return "No bookmarked comments"
        }
        return "No bookmarks"
    }

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
        paginationLoadMoreStoryIds.clear()
        paginationLoadMoreGeneration = loadGeneration
        val firstIndex = max(0, loadedTo + 1)
        val lastIndex = min(targetIndex, stories!!.size - 1)
        for (i in firstIndex..lastIndex) {
            val story = stories!!.get(i)
            if (story != null && !story.loaded) {
                paginationLoadMoreStoryIds.add(story.id)
            }
        }
    }

    private fun finishPaginationLoadMoreStory(story: Story?, loadGeneration: Int) {
        if (story == null || loadGeneration != paginationLoadMoreGeneration) {
            return
        }

        paginationLoadMoreStoryIds.remove(story.id)
        if (paginationLoadMoreStoryIds.isEmpty()) {
            clearPaginationLoadMoreState()
        }
    }

    private fun clearPaginationLoadMoreState() {
        paginationLoadMoreStoryIds.clear()
        paginationLoadMoreGeneration = -1
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
            if (story != null && !story.loaded && !story.loadingFailed && !isStoryLoadInProgress(
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
            if (paginationLoadMoreStoryIds.isEmpty()) {
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

    private fun toggleStoryBookmark(story: Story?, position: Int, currentlyBookmarked: Boolean) {
        if (!this.isAdded || story == null) {
            return
        }
        val context = requireContext()
        if (currentlyBookmarked) {
            Utils.removeBookmark(context, story.id)
            if (isBookmarksType(adapter!!.type)) {
                bookmarkStories.remove(story)
                val currentPosition = stories!!.indexOf(story)
                if (currentPosition >= 0) {
                    removeStoryAt(currentPosition, storyListGeneration, true)
                }
                updateHeader()
                return
            }
        } else {
            Utils.addBookmark(context, story.id)
        }
        val currentPosition = stories!!.indexOf(story)
        if (currentPosition >= 0) {
            adapter!!.notifyItemChanged(currentPosition)
        }
    }

    private fun toggleStoryVote(
        story: Story?, currentlyUpvoted: Boolean,
        completion: Runnable
    ) {
        if (!this.isAdded || story == null || adapter == null || stories == null) {
            completion.run()
            return
        }
        val context = requireContext()
        val actionGeneration = storyListGeneration
        val actionAdapter = adapter
        val actionStories = stories
        val newUpvoted = !currentlyUpvoted
        Utils.setUpvoted(context, story.id, false, newUpvoted)
        val currentPosition = stories!!.indexOf(story)
        if (currentPosition >= 0) {
            adapter!!.notifyItemChanged(currentPosition)
        }
        coroutineScope.launch {
            val result = hackerNewsUserService.vote(
                story.id.toString(),
                if (newUpvoted) "up" else "un",
            )
            if (result is HackerNewsActionResult.Success) {
                completion.run()
                return@launch
            }
            Utils.setUpvoted(context, story.id, false, currentlyUpvoted)
            if (isCurrentStoryActionContext(actionGeneration, actionAdapter, actionStories)) {
                val restoredPosition = stories!!.indexOf(story)
                if (restoredPosition >= 0) {
                    adapter!!.notifyItemChanged(restoredPosition)
                }
            }
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
        story: Story?, position: Int, currentlyFavorited: Boolean,
        completion: Runnable
    ) {
        if (!this.isAdded || story == null || adapter == null || stories == null) {
            completion.run()
            return
        }
        val context = requireContext()
        val actionGeneration = storyListGeneration
        val actionAdapter = adapter
        val actionStories = stories
        val actionIsFavoritesList = isFavoritesType(actionAdapter!!.type)
        val newFavorited = !currentlyFavorited
        val optimisticIndex = stories!!.indexOf(story)
        Utils.setFavorite(context, story.id, newFavorited)
        if (optimisticIndex >= 0) {
            if (currentlyFavorited && actionIsFavoritesList) {
                removeStoryAt(optimisticIndex, storyListGeneration, true)
                updateHeader()
            } else {
                adapter!!.notifyItemChanged(optimisticIndex)
            }
        }
        coroutineScope.launch {
            val result = hackerNewsUserService.setFavorite(story.id, newFavorited)
            if (result is HackerNewsActionResult.Success) {
                completion.run()
                return@launch
            }
            Utils.setFavorite(context, story.id, currentlyFavorited)
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
        filterWords = Utils.getFilterWords(requireContext())
        filterDomains = Utils.getFilterDomains(requireContext())
        filteredUsers = Utils.getFilteredUsers(requireContext())
        val storyPreferences = userSettings.story
        val newHideJobs = storyPreferences.hideJobs
        hideClicked = storyPreferences.hideClicked
        alwaysOpenComments = storyPreferences.alwaysOpenComments
        refreshTypeSpinnerItemsIfNeeded()
        syncVisibleUserItemListWithLocalCache()
        refreshBookmarksIfNeeded()

        val timeDiff = System.currentTimeMillis() - lastLoaded

        // if more than 1 hr
        val shouldShowUpdateButton = storyPreferences.alwaysShowTapToRefresh
                || (timeDiff > 1000 * 60 * 60 && !searching && !isBookmarksType(adapter!!.type) && !isUserItemListType(
            adapter!!.type
        ) && !currentTypeIsAlgolia())
        if (shouldShowUpdateButton) {
            showUpdateButton()
        } else {
            hideUpdateButton()
        }

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

        if (hideJobs != newHideJobs) {
            hideJobs = newHideJobs
            attemptRefresh()
        }

        syncInactiveStoryAdapterDisplaySettings()
        syncStoriesWithHistoriesIfNeeded()
        syncComposeState()
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
            composeController!!.getVisibleStoryPreviewId()
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
            storyListGeneration++
            clearLoadingStoryState()
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
        if (story == null) {
            return false
        }

        val author = story.by
        if (filteredUsers != null && !TextUtils.isEmpty(author) && filteredUsers!!.contains(
                author!!.lowercase(
                    Locale.getDefault()
                ).trim { it <= ' ' })
        ) {
            return true
        }

        val storyTitle = story.title
        if (filterWords != null && storyTitle != null) {
            val title = storyTitle.lowercase(Locale.getDefault())
            for (phrase in filterWords) {
                if (!TextUtils.isEmpty(phrase) && title.contains(phrase.lowercase(Locale.getDefault()))) {
                    return true
                }
            }
        }

        val storyUrl = story.url
        if (filterDomains != null && storyUrl != null) {
            try {
                val domain = story.getDisplayDomain(true).orEmpty().lowercase(Locale.getDefault())
                for (phrase in filterDomains) {
                    if (!TextUtils.isEmpty(phrase)
                        && domain.contains(phrase!!.lowercase(Locale.getDefault()))
                    ) {
                        return true
                    }
                }
            } catch (ignored: Exception) {
                // Invalid URLs cannot match a domain filter.
            }
        }

        return shouldHideStoryAsJob(story)
    }

    private fun isStoryLoadInProgress(story: Story?): Boolean {
        if (story == null) {
            return false
        }

        val startedAt = loadingStoryStartTimes[story.id]
        if (startedAt == null) {
            return false
        }

        if (System.currentTimeMillis() - startedAt > STORY_LOAD_STALE_TIMEOUT_MS) {
            loadingStoryStartTimes.remove(story.id)
            return false
        }

        return true
    }

    private fun markStoryLoadStarted(story: Story?): Long {
        val startedAt = System.currentTimeMillis()
        if (story != null) {
            loadingStoryStartTimes[story.id] = startedAt
        }
        return startedAt
    }

    private fun clearStoryLoadState(story: Story?) {
        if (story != null) {
            loadingStoryStartTimes.remove(story.id)
        }
    }

    private fun clearStoryLoadState(story: Story?, startedAt: Long) {
        if (story == null) {
            return
        }

        val currentStartedAt = loadingStoryStartTimes[story.id]
        if (currentStartedAt != null && currentStartedAt == startedAt) {
            loadingStoryStartTimes.remove(story.id)
        }
    }

    private fun isCurrentStoryLoad(story: Story?, startedAt: Long): Boolean {
        if (story == null) {
            return false
        }

        val currentStartedAt = loadingStoryStartTimes[story.id]
        return currentStartedAt != null && currentStartedAt == startedAt
    }

    private fun clearLoadingStoryState() {
        loadingStoryStartTimes.clear()
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
                updatePreviewImagePrefetchRampCompletion()
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
        storyListGeneration++
        clearLoadingStoryState()
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
        return generation == storyListGeneration
    }

    private fun attemptRefresh(
        showSwipeRefreshIndicator: Boolean,
        showMainLoadingIndicator: Boolean = false
    ) {
        hideUpdateButton()
        if (searching) {
            Log.d(
                TAG,
                "Refreshing active search, queryLength=" + (if (lastSearch == null) 0 else lastSearch!!.length)
            )
            search(lastSearch)
            return
        }

        this.isRefreshIndicatorShowing = showSwipeRefreshIndicator && !showMainLoadingIndicator

        // cancel all ongoing
        val refreshGeneration = beginStoryListRefresh()
        activeStoryListStore.beginLoad(
            refreshing = showSwipeRefreshIndicator && !showMainLoadingIndicator,
            clearItems = showMainLoadingIndicator,
        )
        val currentStoryType = this.currentStoryType
        Log.d(
            TAG, ("Starting refresh generation=" + refreshGeneration
                    + ", type=" + currentStoryType.label
                    + ", showSwipeRefreshIndicator=" + showSwipeRefreshIndicator
                    + ", showMainLoadingIndicator=" + showMainLoadingIndicator)
        )

        val userItemListTypeForRefresh = isUserItemListType(adapter!!.type)
        if (showMainLoadingIndicator) {
            loadingFailed = false
            loadingFailedServerError = false
            loadingFailedRateLimited = false
            showingCached = false
            userItemListInitialLoadInProgress = userItemListTypeForRefresh
            replaceStories(java.util.ArrayList<Story>(), true)
            updateHeader()
        }

        if (currentTypeIsAlgolia()) {
            // algoliaStuff
            resetAlgoliaResultLimit()
            loadTopStoriesSince(
                this.currentAlgoliaTopStoriesStartTime,
                showSwipeRefreshIndicator && !showMainLoadingIndicator
            )

            return
        }

        lastLoaded = System.currentTimeMillis()

        if (isBookmarksType(adapter!!.type)) {
            // let's load bookmarks instead - or rather add empty stories with correct id:s and start loading them
            val refreshedStories = java.util.ArrayList<Story>()
            showingCached = false

            bookmarksChanged = false
            val bookmarks = Utils.loadBookmarks(
                requireContext(), true
            )

            for (i in bookmarks.indices) {
                val s = Story("Loading...", bookmarks.get(i)!!.id, false, false)
                refreshedStories.add(s)
            }

            bookmarkStories.clear()
            bookmarkStories.addAll(refreshedStories)
            replaceStories(this.filteredSavedItemStories, true)
            loadInitialVisibleStories(refreshGeneration)

            updateHeader()
            this.isRefreshIndicatorShowing = false

            return
        } else if (isUserItemListType(adapter!!.type)) {
            val shouldLoadCachedUserItemList = showMainLoadingIndicator || stories!!.isEmpty()
            val hasCachedUserItemList = if (shouldLoadCachedUserItemList)
                loadUserItemListCache()
            else
                !UserItemListRepository.loadCache(this.context, this.currentUserItemListSource)
                    .isEmpty()
            if (!shouldLoadCachedUserItemList) {
                resumeInterruptedStoryLoads()
            }
            syncUserItemListFromServer(showSwipeRefreshIndicator || hasCachedUserItemList)
            return
        } else if (isHistoryType(adapter!!.type)) {
            val refreshedStories = java.util.ArrayList<Story>()
            showingCached = false
            val histories = historyStore.load()

            for (i in histories.indices) {
                val s =
                    Story("Loading...", histories.get(i).id, false, false, histories.get(i).created)
                refreshedStories.add(s)
            }

            replaceStories(refreshedStories, true)
            loadInitialVisibleStories(refreshGeneration)

            updateHeader()
            this.isRefreshIndicatorShowing = false

            return
        }

        if (currentStoryType.isFrontpageLinkList) {
            loadFrontpageLinkRows(currentStoryType, refreshGeneration)
            return
        }
        if (currentStoryType.isScrapedFrontpage) {
            loadScrapedFrontpageStories(currentStoryType, refreshGeneration)
            return
        }

        val storyType = currentStoryType
        updateHeader()
        coroutineScope.launch {
            try {
                val itemIds = (storyFeedRepository.load(storyType) as StoryFeedResult.ItemIds).ids
                if (!isCurrentStoryListGeneration(refreshGeneration)) return@launch
                isRefreshIndicatorShowing = false
                showingCached = false
                replaceStories(createLoadingStoriesFromIds(itemIds))
                loadingFailed = false
                loadingFailedServerError = false
                loadingFailedRateLimited = false
                updateHeader()
                loadInitialVisibleStories(refreshGeneration)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (!isCurrentStoryListGeneration(refreshGeneration)) return@launch
                isRefreshIndicatorShowing = false
                loadingFailed = true
                loadingFailedServerError = error is HttpStatusException && error.statusCode == 404
                loadingFailedRateLimited = error is HttpStatusException && error.statusCode == 429
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
                loadingFailedRateLimited = isRateLimitedResponse(summary, response)
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

    private fun isRateLimitedResponse(summary: String?, response: String?): Boolean {
        return containsHttp429(summary) || containsHttp429(response)
    }

    private fun containsHttp429(text: String?): Boolean {
        return text != null
                && (text.contains("429")
                || text.lowercase().contains("too many requests"))
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

        val snapshot = UserItemListRepository.loadCachedSnapshot(
            this.context,
            this.currentUserItemListSource
        )
        replaceUserItemListStoriesWithIds(snapshot.itemIds, snapshot.commentIds)
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
        val upvotedTypeForSync = syncSource == UserItemListRepository.Source.UPVOTED
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

                val currentContext: Context? = this@StoriesCoordinator.context
                if (currentContext == null) {
                    return@launch
                }

                val snapshot = UserItemListRepository.normalizeSnapshot(itemIds, commentIds)
                if (!UserItemListRepository.idsMatchCache(currentContext, syncSource, snapshot)) {
                    UserItemListRepository.saveIds(currentContext, syncSource, snapshot)
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
                loadingFailedRateLimited = isRateLimitedResponse(summary, response)
                updateHeader()
                Toast.makeText(requireContext(), summary, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncVisibleUserItemListWithLocalCache() {
        if (adapter == null || stories == null || !isUserItemListType(adapter!!.type)) {
            return
        }

        val snapshot = UserItemListRepository.loadCachedSnapshot(
            this.context,
            this.currentUserItemListSource
        )
        syncUserItemListStoriesToIds(snapshot.itemIds, snapshot.commentIds)
    }

    private fun syncUserItemListStoriesToIds(
        itemIds: List<Int>,
        commentIds: Set<Int>
    ): Boolean {
        if (itemIdsMatchUserItemListStories(itemIds, commentIds)) {
            return false
        }

        replaceUserItemListStoriesWithIds(itemIds, commentIds)
        return true
    }

    private fun itemIdsMatchUserItemListStories(
        itemIds: List<Int>,
        commentIds: Set<Int>
    ): Boolean {
        if (userItemListStories.size != itemIds.size || userItemListCommentIds != commentIds) {
            return false
        }

        for (i in userItemListStories.indices) {
            if (userItemListStories[i].id != itemIds[i]) {
                return false
            }
        }

        return true
    }

    private fun replaceUserItemListStoriesWithIds(
        itemIds: List<Int>,
        commentIds: Set<Int>
    ) {
        val existingStories: MutableMap<Int, Story> = HashMap<Int, Story>()
        for (story in (if (userItemListStories.isEmpty()) stories else userItemListStories)!!) {
            existingStories.put(story.id, story)
        }

        val refreshedStories = java.util.ArrayList<Story>()
        for (id in itemIds) {
            val story = existingStories[id] ?: Story("Loading...", id, false, false)
            if (commentIds.contains(id)) {
                story.isComment = true
            }
            refreshedStories.add(story)
        }

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
        if (targetIndex < 0 || adapter == null || SettingsUtils.STORY_PREVIEW_IMAGE_OFF == adapter!!.previewImageMode
            || previewImagePrefetchRampComplete
        ) {
            return
        }

        previewImagePrefetchRampTargetIndex = max(previewImagePrefetchRampTargetIndex, targetIndex)
    }

    private fun requestPreviewImagePrefetch(context: Context?, story: Story?) {
        if (context == null || adapter == null || story == null || !story.loaded || story.loadingFailed) {
            return
        }

        if (previewImagePrefetchRampComplete || previewImagePrefetchRampTargetIndex < 0) {
            adapter!!.prefetchPreviewImage(context, story)
            return
        }

        if (story.id > 0) {
            if (requestedPreviewImagePrefetchStoryIds.contains(story.id)
                || !queuedPreviewImagePrefetchStoryIds.add(story.id)
            ) {
                return
            }
        }

        previewImagePrefetchQueue.add(story)
        drainPreviewImagePrefetchQueue()
    }

    private fun drainPreviewImagePrefetchQueue() {
        if (previewImagePrefetchRampScheduled) {
            return
        }

        val context = this.context
        if (context == null || adapter == null) {
            return
        }

        while (previewImagePrefetchRampSlotsRemaining > 0 && !previewImagePrefetchQueue.isEmpty()) {
            val story = removeNextPreviewImagePrefetchStory()
            if (story == null) {
                break
            }

            if (story.id > 0) {
                requestedPreviewImagePrefetchStoryIds.add(story.id)
            }
            previewImagePrefetchRampSlotsRemaining--
            adapter!!.prefetchPreviewImage(context, story)
        }

        updatePreviewImagePrefetchRampCompletion()
        if (!previewImagePrefetchRampComplete && previewImagePrefetchRampSlotsRemaining <= 0) {
            scheduleNextPreviewImagePrefetchRampBatch()
        }
    }

    private fun removeNextPreviewImagePrefetchStory(): Story? {
        var bestQueueIndex = -1
        var bestStoryIndex = Int.MAX_VALUE
        var i = 0
        while (i < previewImagePrefetchQueue.size) {
            val story = previewImagePrefetchQueue.get(i)
            val storyIndex = if (stories == null) -1 else stories!!.indexOf(story)
            if (storyIndex < 0 || !story.loaded || story.loadingFailed) {
                previewImagePrefetchQueue.removeAt(i)
                if (story.id > 0) {
                    queuedPreviewImagePrefetchStoryIds.remove(story.id)
                }
                i--
                i++
                continue
            }

            if (storyIndex < bestStoryIndex) {
                bestStoryIndex = storyIndex
                bestQueueIndex = i
            }
            i++
        }

        if (bestQueueIndex < 0) {
            return null
        }

        val story = previewImagePrefetchQueue.removeAt(bestQueueIndex)
        if (story.id > 0) {
            queuedPreviewImagePrefetchStoryIds.remove(story.id)
        }
        return story
    }

    private fun scheduleNextPreviewImagePrefetchRampBatch() {
        if (previewImagePrefetchRampScheduled) {
            return
        }

        previewImagePrefetchRampScheduled = true
        previewImagePrefetchHandler.postDelayed(
            previewImagePrefetchRampRunnable,
            PREVIEW_IMAGE_PREFETCH_RAMP_DELAY_MS
        )
    }

    private fun updatePreviewImagePrefetchRampCompletion() {
        if (previewImagePrefetchRampComplete
            || previewImagePrefetchRampTargetIndex < 0 || !previewImagePrefetchQueue.isEmpty() || !arePreviewImagePrefetchRampStoriesSettled()
        ) {
            return
        }

        previewImagePrefetchRampComplete = true
        previewImagePrefetchRampTargetIndex = -1
        previewImagePrefetchHandler.removeCallbacks(previewImagePrefetchRampRunnable)
        previewImagePrefetchRampScheduled = false
        queuedPreviewImagePrefetchStoryIds.clear()
        requestedPreviewImagePrefetchStoryIds.clear()
    }

    private fun arePreviewImagePrefetchRampStoriesSettled(): Boolean {
        if (stories == null || stories!!.isEmpty()) {
            return true
        }

        val targetIndex = min(previewImagePrefetchRampTargetIndex, stories!!.size - 1)
        for (i in 0..targetIndex) {
            val story = stories!!.get(i)
            if (!story.loaded && !story.loadingFailed) {
                return false
            }
        }
        return true
    }

    private fun resetPreviewImagePrefetchRamp() {
        previewImagePrefetchHandler.removeCallbacks(previewImagePrefetchRampRunnable)
        previewImagePrefetchQueue.clear()
        queuedPreviewImagePrefetchStoryIds.clear()
        requestedPreviewImagePrefetchStoryIds.clear()
        previewImagePrefetchRampScheduled = false
        previewImagePrefetchRampComplete = false
        previewImagePrefetchRampSlotsRemaining = PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE
        previewImagePrefetchRampTargetIndex = -1
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

        val firstIndex = if (firstVisibleItem == NO_POSITION) 0 else max(0, firstVisibleItem)
        var lastIndex = if (lastVisibleItem == NO_POSITION) min(
            initialLoadCount - 1, currentStories.size - 1
        ) else min(lastVisibleItem + STORY_VISIBLE_PREFETCH_THRESHOLD, currentStories.size - 1)
        if (currentAdapter.paginationMode) {
            lastIndex = min(lastIndex, currentAdapter.visibleStoryCount - 1)
        }

        if (lastIndex < firstIndex) {
            return
        }

        beginPreviewImagePrefetchRamp(lastIndex)
        for (i in firstIndex..lastIndex) {
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
            storyListGeneration++
            clearLoadingStoryState()
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

            storyListGeneration++
            clearLoadingStoryState()
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

    private val currentUserItemListSource: UserItemListRepository.Source
        get() = if (isUpvotedType(adapter!!.type))
            UserItemListRepository.Source.UPVOTED
        else
            UserItemListRepository.Source.FAVORITES

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

    private fun shouldHideStoryAsJob(story: Story): Boolean {
        return hideJobs
                && this.currentStoryType != StoryType.HN_JOBS && (story.isJob
                || "whoishiring" == story.by)
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
        updateButtonShowing = false
        syncComposeState()
    }

    private fun showUpdateButton() {
        updateButtonShowing = true
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
