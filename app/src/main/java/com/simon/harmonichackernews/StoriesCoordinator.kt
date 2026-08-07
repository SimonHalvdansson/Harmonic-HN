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
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.StorySearchController.StoryFilter
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.BackgroundJSONParser
import com.simon.harmonichackernews.network.BackgroundJSONParser.AlgoliaParseCallback
import com.simon.harmonichackernews.network.JSONParser
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.UserActions
import com.simon.harmonichackernews.network.UserActions.ActionCallback
import com.simon.harmonichackernews.network.UserActions.StoryListCallback
import com.simon.harmonichackernews.network.UserActions.StoryRowsCallback
import com.simon.harmonichackernews.network.UserActions.UserItemListCallback
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract
import com.simon.harmonichackernews.ui.settings.SettingsIntents.create
import com.simon.harmonichackernews.ui.stories.StoriesComposeController
import com.simon.harmonichackernews.ui.stories.StoriesComposeController.Companion.create
import com.simon.harmonichackernews.ui.stories.StoryListState
import com.simon.harmonichackernews.utils.AccountUtils
import com.simon.harmonichackernews.utils.FontUtils
import com.simon.harmonichackernews.utils.HistoriesUtils
import com.simon.harmonichackernews.utils.HistoriesUtils.addHistory
import com.simon.harmonichackernews.utils.HistoriesUtils.clearHistories
import com.simon.harmonichackernews.utils.HistoriesUtils.getChangeVersion
import com.simon.harmonichackernews.utils.HistoriesUtils.init
import com.simon.harmonichackernews.utils.HistoriesUtils.isHistoryExist
import com.simon.harmonichackernews.utils.HistoriesUtils.loadHistories
import com.simon.harmonichackernews.utils.HistoriesUtils.removeHistoryById
import com.simon.harmonichackernews.utils.HistoriesUtils.size
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.StoryUpdate
import com.simon.harmonichackernews.utils.StoryUpdate.StoryUpdateListener
import com.simon.harmonichackernews.utils.Utils
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

class StoriesCoordinator(private val activity: MainActivity, savedInstanceState: Bundle?) {
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
    private val searchController = StorySearchController()
    private val storiesViewModel: StoriesViewModel?
    private var restoredState: StoriesViewModel.State? = null
    private var restoredStateForCurrentView = false
    private var storyCacheController: StoryCacheController? = null
    private var linkSummaryBackCallback: OnBackPressedCallback? = null
    private var pendingLinkSummaryStoryId: Int = NO_PENDING_LINK_SUMMARY_STORY_ID

    private var mainAdapter: StoryListState? = null
    private var searchAdapter: StoryListState? = null
    private var adapter: StoryListState? = null
    private val mainStories = java.util.ArrayList<Story>()
    private val searchStories = java.util.ArrayList<Story>()
    private var stories: MutableList<Story>? = null
    private val bookmarkStories = java.util.ArrayList<Story>()
    private val userItemListStories = java.util.ArrayList<Story>()
    private var userItemListCommentIds: MutableSet<Int> = HashSet<Int>()
    private var queue: RequestQueue? = null
    private val requestTag = Any()
    private val loadingStoryStartTimes: MutableMap<Int, Long?> = HashMap<Int, Long?>()
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
    private var searching = false
    private var loadingFailed = false
    private var loadingFailedServerError = false
    private var loadingFailedRateLimited = false
    private var lastSearch: String? = ""
    private var algoliaRequestGeneration = 0
    private var storyListGeneration = 0
    private val pendingStoryRowChangeGenerations: MutableMap<Int, Int> = HashMap<Int, Int>()
    private val pendingStoryRemovals: MutableMap<Int, PendingStoryRemoval?> =
        HashMap<Int, PendingStoryRemoval?>()
    private var algoliaLoading = false
    private var activeAlgoliaUrl: String? = null
    private var algoliaLoadMoreInProgress = false
    private var algoliaLoadMoreVisibleStoryCount = -1
    private var storiesBeforeSearch: MutableList<Story>? = null
    private var loadedToBeforeSearch = -1
    private var visibleStoryCountBeforeSearch = Int.MAX_VALUE
    private var showingCachedBeforeSearch = false
    private var loadingFailedBeforeSearch = false
    private var loadingFailedServerErrorBeforeSearch = false
    private var loadingFailedRateLimitedBeforeSearch = false
    private var showLoadMoreBeforeSearch = false
    private var algoliaHitsPerPageBeforeSearch = StorySearchController.ALGOLIA_HITS_INCREMENT
    private var lastAlgoliaTopStoriesStartTimeBeforeSearch = 0
    private var loadPendingBeforeSearch = false

    private var showingCached = false

    private var loadedTo = -1
    private var paginationMode = false
    private val paginationLoadMoreStoryIds: MutableSet<Int> = HashSet<Int>()
    private var paginationLoadMoreGeneration = -1

    private class PendingStoryRemoval(val generation: Int, var updateHeader: Boolean)

    private var algoliaHitsPerPage = StorySearchController.ALGOLIA_HITS_INCREMENT
    private var lastAlgoliaTopStoriesStartTime = 0
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

    var lastLoaded: Long = 0
    var lastClick: Long = 0
    private var updateButtonShowing = false
    private var predictiveSearchBackInProgress = false
    private var predictiveSearchBackProgress = 0f
    private var finishSearchBackFromCurrentVisualState = false
    private var userItemListsDropdownVisible = false
    private var userItemListInitialLoadInProgress = false
    private var userItemListFilter: Int = USER_ITEM_LIST_FILTER_BOTH
    private var frontPageDayUtc: Calendar? = null
    private var scrapedFrontpageNextPageUrl: String? = null
    private var scrapedFrontpageNextPageLoading = false
    private var scrapedFrontpageStoryType: StoryType? = StoryType.UNKNOWN

    init {
        storyClickListener = activity
        storiesViewModel = ViewModelProvider(activity)[StoriesViewModel::class.java]
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

        init(requireContext())
        historiesChangeVersion = getChangeVersion()
        restoredState = storiesViewModel?.state
        queue = NetworkComponent.getRequestQueueInstance(requireContext())
        setupLinkSummaryBackCallback()
        storyCacheController = createStoryCacheController()

        stories = mainStories
        filterWords = Utils.getFilterWords(requireContext())
        filterDomains = Utils.getFilterDomains(requireContext())
        filteredUsers = Utils.getFilteredUsers(requireContext())
        hideJobs = SettingsUtils.shouldHideJobs(requireContext())
        hideClicked = SettingsUtils.shouldHideClicked(requireContext())
        alwaysOpenComments = SettingsUtils.shouldAlwaysOpenComments(requireContext())
        userItemListsDropdownVisible = shouldShowUserItemLists(requireContext())
        restoreStoryLists(restoredState)
        setupAdapter()
        registerStoryAdapterDataObservers()
        restoredStateForCurrentView = restoreStoryStateAfterViewSetup(restoredState)
        initializeComposeUi()

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

            override val requestQueue: RequestQueue?
                get() = queue

            override val requestTag: Any
                get() = this@StoriesCoordinator.requestTag

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
                    adapter!!.type = index
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
                        searchController.sortIndex = index
                    } else if (kind == StoriesComposeController.SEARCH_OPTION_DATE) {
                        searchController.dateRangeIndex = index
                    } else if (kind == StoriesComposeController.SEARCH_OPTION_POINTS) {
                        searchController.minimumPointsIndex = index
                    } else if (kind == StoriesComposeController.SEARCH_OPTION_COMMENTS) {
                        searchController.minimumCommentsIndex = index
                    }
                    updateSearchOptionChips()
                    retrySearchWithCurrentOptions()
                }

                override fun onToggleOnlyClicked() {
                    searchController.toggleOnlyClicked()
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
                        applySavedItemFilter(true)
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
                            story, position, selected,
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
            val story = stories!!.get(position)
            if (story == null || story.isComment || !story.loaded || (story.isLink && TextUtils.isEmpty(
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
                val story = stories!!.get(i)
                if (story != null && story.loaded) {
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
            clearHistories(requireContext())
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
        } else if (!Utils.isNetworkAvailable(context)) {
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
                && !showingCached && !cacheInProgress && Utils.isNetworkAvailable(context)

        controller.updateContent(
            mainStories,
            searchStories,
            StoryDisplaySettings.from(context),
            stringLabels,
            mainAdapter!!.type,
            searching,
            lastSearch!!,
            searchController.sortLabel,
            searchController.dateRangeLabel,
            searchController.minimumPointsLabel,
            searchController.minimumCommentsLabel,
            StorySearchController.sortLabels,
            StorySearchController.dateRangeLabels,
            StorySearchController.minimumPointsLabels,
            StorySearchController.minimumCommentsLabels,
            searchController.isOnlyClicked,
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
            isHistoryType(adapter!!.type) && size() > 0,
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

    private fun restoreStoryLists(state: StoriesViewModel.State?) {
        if (state == null) {
            return
        }

        mainStories.clear()
        searchStories.clear()
        bookmarkStories.clear()
        userItemListStories.clear()
        mainStories.addAll(state.mainStories)
        searchStories.addAll(state.searchStories)
        bookmarkStories.addAll(state.bookmarkStories)
        userItemListStories.addAll(state.userItemListStories)
        userItemListCommentIds = HashSet<Int>(state.userItemListCommentIds)
    }

    private fun restoreStoryStateAfterViewSetup(state: StoriesViewModel.State?): Boolean {
        if (state == null || mainAdapter == null || searchAdapter == null) {
            return false
        }

        val restoredMainType = getTypeIndex(state.mainTypeLabel)
        if (restoredMainType >= 0) {
            mainAdapter!!.type = restoredMainType
        }
        val restoredSearchType = getTypeIndex(state.searchTypeLabel)
        searchAdapter!!.type =
            if (restoredSearchType >= 0) restoredSearchType else mainAdapter!!.type

        mainAdapter!!.visibleStoryCount = state.mainVisibleStoryCount
        searchAdapter!!.visibleStoryCount = state.searchVisibleStoryCount
        mainAdapter!!.showLoadMoreButton = state.mainShowLoadMoreButton
        searchAdapter!!.showLoadMoreButton = state.searchShowLoadMoreButton
        updateAdapterCommentRows()

        searching = state.searching
        lastSearch = state.lastSearch
        lastLoaded = state.lastLoaded
        updateButtonShowing = state.updateButtonShowing
        userItemListFilter = state.userItemListFilter
        if (frontPageDayUtc == null && state.frontPageDayUtcMillis >= 0L) {
            frontPageDayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            frontPageDayUtc!!.setTimeInMillis(state.frontPageDayUtcMillis)
            clearTime(frontPageDayUtc!!)
        }
        scrapedFrontpageNextPageUrl = state.scrapedFrontpageNextPageUrl
        scrapedFrontpageNextPageLoading = false
        scrapedFrontpageStoryType = if (currentTypeIsScrapedFrontpage())
            this.currentStoryType
        else
            StoryType.UNKNOWN

        searchController.sortIndex = state.searchSortIndex
        searchController.dateRangeIndex = state.searchDateRangeIndex
        searchController.minimumPointsIndex = state.searchMinimumPointsIndex
        searchController.minimumCommentsIndex = state.searchMinimumCommentsIndex
        if (searchController.isOnlyClicked != state.searchOnlyClicked) {
            searchController.toggleOnlyClicked()
        }

        if (searching) {
            storiesBeforeSearch = java.util.ArrayList<Story>(mainStories)
            loadedToBeforeSearch = state.mainLoadedTo
            visibleStoryCountBeforeSearch = state.mainVisibleStoryCount
            showingCachedBeforeSearch = state.mainShowingCached
            loadingFailedBeforeSearch = state.mainLoadingFailed
            loadingFailedServerErrorBeforeSearch = state.mainLoadingFailedServerError
            loadingFailedRateLimitedBeforeSearch = state.mainLoadingFailedRateLimited
            showLoadMoreBeforeSearch = state.mainShowLoadMoreButton
            algoliaHitsPerPageBeforeSearch = state.mainAlgoliaHitsPerPage
            lastAlgoliaTopStoriesStartTimeBeforeSearch = state.mainLastAlgoliaTopStoriesStartTime
            loadPendingBeforeSearch = mainStories.isEmpty()
                    && !state.mainLoadingFailed && !state.mainLoadingFailedServerError && !isBookmarksType(
                mainAdapter!!.type
            ) && !isUserItemListType(mainAdapter!!.type)

            loadedTo = state.searchLoadedTo
            showingCached = state.searchShowingCached
            loadingFailed = state.searchLoadingFailed
            loadingFailedServerError = state.searchLoadingFailedServerError
            loadingFailedRateLimited = state.searchLoadingFailedRateLimited
            algoliaHitsPerPage = state.searchAlgoliaHitsPerPage
            lastAlgoliaTopStoriesStartTime = state.searchLastAlgoliaTopStoriesStartTime
        } else {
            loadedTo = state.mainLoadedTo
            showingCached = state.mainShowingCached
            loadingFailed = state.mainLoadingFailed
            loadingFailedServerError = state.mainLoadingFailedServerError
            loadingFailedRateLimited = state.mainLoadingFailedRateLimited
            algoliaHitsPerPage = state.mainAlgoliaHitsPerPage
            lastAlgoliaTopStoriesStartTime = state.mainLastAlgoliaTopStoriesStartTime
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

    private fun saveStoryStateForRecreation() {
        if (storiesViewModel == null || mainAdapter == null || searchAdapter == null) {
            return
        }

        val state = StoriesViewModel.State()
        state.mainStories.addAll(mainStories)
        state.searchStories.addAll(searchStories)
        state.bookmarkStories.addAll(bookmarkStories)
        state.userItemListStories.addAll(userItemListStories)
        state.userItemListCommentIds.addAll(userItemListCommentIds)
        val mainTypeLabel = getTypeLabel(mainAdapter!!.type)
        val searchTypeLabel = getTypeLabel(searchAdapter!!.type)
        state.mainTypeLabel = if (mainTypeLabel == null) null else mainTypeLabel.toString()
        state.searchTypeLabel = if (searchTypeLabel == null) null else searchTypeLabel.toString()
        state.mainVisibleStoryCount = mainAdapter!!.visibleStoryCount
        state.searchVisibleStoryCount = searchAdapter!!.visibleStoryCount
        state.mainShowLoadMoreButton = mainAdapter!!.showLoadMoreButton
        state.searchShowLoadMoreButton = searchAdapter!!.showLoadMoreButton
        state.searching = searching
        state.lastSearch = lastSearch.orEmpty()
        state.lastLoaded = lastLoaded
        state.updateButtonShowing = updateButtonShowing
        state.userItemListFilter = userItemListFilter
        state.frontPageDayUtcMillis =
            if (frontPageDayUtc == null) -1L else frontPageDayUtc!!.getTimeInMillis()
        state.scrapedFrontpageNextPageUrl = scrapedFrontpageNextPageUrl

        state.searchSortIndex = searchController.sortIndex
        state.searchDateRangeIndex = searchController.dateRangeIndex
        state.searchMinimumPointsIndex = searchController.minimumPointsIndex
        state.searchMinimumCommentsIndex = searchController.minimumCommentsIndex
        state.searchOnlyClicked = searchController.isOnlyClicked

        // LazyListState is owned and saved by Compose. The controller state only persists data.
        state.mainFirstVisiblePosition = -1
        state.mainFirstVisibleTop = 0
        state.searchFirstVisiblePosition = -1
        state.searchFirstVisibleTop = 0
        state.appBarCollapsed = false

        if (searching) {
            state.mainLoadedTo = loadedToBeforeSearch
            state.mainShowingCached = showingCachedBeforeSearch
            state.mainLoadingFailed = loadingFailedBeforeSearch
            state.mainLoadingFailedServerError = loadingFailedServerErrorBeforeSearch
            state.mainLoadingFailedRateLimited = loadingFailedRateLimitedBeforeSearch
            state.mainAlgoliaHitsPerPage = algoliaHitsPerPageBeforeSearch
            state.mainLastAlgoliaTopStoriesStartTime = lastAlgoliaTopStoriesStartTimeBeforeSearch

            state.searchLoadedTo = loadedTo
            state.searchShowingCached = showingCached
            state.searchLoadingFailed = loadingFailed
            state.searchLoadingFailedServerError = loadingFailedServerError
            state.searchLoadingFailedRateLimited = loadingFailedRateLimited
            state.searchAlgoliaHitsPerPage = algoliaHitsPerPage
            state.searchLastAlgoliaTopStoriesStartTime = lastAlgoliaTopStoriesStartTime
        } else {
            state.mainLoadedTo = loadedTo
            state.mainShowingCached = showingCached
            state.mainLoadingFailed = loadingFailed
            state.mainLoadingFailedServerError = loadingFailedServerError
            state.mainLoadingFailedRateLimited = loadingFailedRateLimited
            state.mainAlgoliaHitsPerPage = algoliaHitsPerPage
            state.mainLastAlgoliaTopStoriesStartTime = lastAlgoliaTopStoriesStartTime
        }

        storiesViewModel.state = state
    }

    private val preferredTypeIndex: Int
        get() {
            val typeAdapterList =
                buildTypeAdapterList(requireContext())
            val preferredIndex =
                typeAdapterList.indexOf(SettingsUtils.getPreferredStoryType(requireContext()))
            return if (preferredIndex >= 0) preferredIndex else 0
        }

    private fun buildTypeAdapterList(ctx: Context): java.util.ArrayList<CharSequence> {
        return StoryType.buildAdapterLabels(this.resources, ctx, shouldShowUserItemLists(ctx))
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
            adapter!!.type = newType
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
        pendingStoryRowChangeGenerations.put(story.id, loadGeneration)
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

        val pendingRemoval = pendingStoryRemovals.get(story.id)
        if (pendingRemoval == null || pendingRemoval.generation != loadGeneration) {
            pendingStoryRemovals.put(
                story.id,
                PendingStoryRemoval(loadGeneration, updateHeader)
            )
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
        for (entry in pendingStoryRemovals.entries) {
            val removal: PendingStoryRemoval = entry.value!!
            if (removal.generation == currentGeneration) {
                removalIds.add(entry.key)
                shouldUpdateHeader = shouldUpdateHeader or removal.updateHeader
            }
        }
        pendingStoryRemovals.clear()
        for (storyId in removalIds) {
            val position = findStoryPositionById(storyId!!)
            if (position >= 0) {
                removeStoryAt(position, currentGeneration, false)
            }
        }
        pendingStoryRowChangeGenerations.clear()
        if (!removalIds.isEmpty()) {
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
        searchController.resetOptions()
        updateSearchOptionChips(false)
    }

    private fun resetPaginationState() {
        loadedTo = -1
        clearPaginationLoadMoreState()
        updateAdapterPaginationMode(adapter)
        adapter!!.visibleStoryCount =
            if (adapter!!.paginationMode) StoryListState.PAGINATION_PAGE_SIZE else Int.MAX_VALUE
    }

    private fun shouldUsePaginationForType(storyType: StoryType?): Boolean {
        return paginationMode || (storyType != null && storyType.isScrapedFrontpage)
    }

    private fun updateAdapterPaginationMode(targetAdapter: StoryListState?) {
        if (targetAdapter == null) {
            return
        }

        targetAdapter.paginationMode = shouldUsePaginationForType(getStoryType(targetAdapter.type))
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
        stories!!.clear()
        resetPaginationState()
        adapter!!.showLoadMoreButton = false

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
            stories!!.clear()
            resetPaginationState()
            adapter!!.showLoadMoreButton = showLoadMoreButton
            stories!!.addAll(newStories.filterNotNull())
            adapter!!.notifyDataSetChanged()
            return
        }

        clearStories()
        adapter!!.showLoadMoreButton = showLoadMoreButton
        stories!!.addAll(newStories.filterNotNull())

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
        stories!!.clear()
        stories!!.addAll(newStories.filterNotNull())
        adapter!!.showLoadMoreButton = showLoadMoreButton

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

        adapter!!.notifyDataSetChanged()
    }

    private fun saveStoriesBeforeSearch() {
        if (storiesBeforeSearch != null) {
            return
        }

        storiesBeforeSearch = java.util.ArrayList<Story>(stories ?: emptyList())
        loadedToBeforeSearch = loadedTo
        visibleStoryCountBeforeSearch = adapter!!.visibleStoryCount
        showingCachedBeforeSearch = showingCached
        loadingFailedBeforeSearch = loadingFailed
        loadingFailedServerErrorBeforeSearch = loadingFailedServerError
        loadingFailedRateLimitedBeforeSearch = loadingFailedRateLimited
        showLoadMoreBeforeSearch = adapter!!.showLoadMoreButton
        algoliaHitsPerPageBeforeSearch = algoliaHitsPerPage
        lastAlgoliaTopStoriesStartTimeBeforeSearch = lastAlgoliaTopStoriesStartTime
        loadPendingBeforeSearch = stories!!.isEmpty()
                && !loadingFailed && !loadingFailedServerError && !isBookmarksType(adapter!!.type) && !isUserItemListType(
            adapter!!.type
        )
    }

    private fun restoreStoriesBeforeSearch(): Boolean {
        if (storiesBeforeSearch == null) {
            return false
        }

        // Search owns a separate list, so the main list never needs to be cleared and
        // reinserted. Restoring only its controller metadata keeps predictive back stable and
        // lets the already-preserved LazyListState remain on screen throughout the transition.
        restoreStoryStateBeforeSearch()
        clearStoriesBeforeSearchSnapshot()
        return true
    }

    private fun restoreStoryStateBeforeSearch() {
        loadedTo = loadedToBeforeSearch
        adapter!!.visibleStoryCount = visibleStoryCountBeforeSearch
        showingCached = showingCachedBeforeSearch
        loadingFailed = loadingFailedBeforeSearch
        loadingFailedServerError = loadingFailedServerErrorBeforeSearch
        loadingFailedRateLimited = loadingFailedRateLimitedBeforeSearch
        adapter!!.showLoadMoreButton = showLoadMoreBeforeSearch
        algoliaHitsPerPage = algoliaHitsPerPageBeforeSearch
        lastAlgoliaTopStoriesStartTime = lastAlgoliaTopStoriesStartTimeBeforeSearch
    }

    private fun clearStoriesBeforeSearchSnapshot() {
        storiesBeforeSearch = null
        loadedToBeforeSearch = -1
        visibleStoryCountBeforeSearch = Int.MAX_VALUE
        showingCachedBeforeSearch = false
        loadingFailedBeforeSearch = false
        loadingFailedServerErrorBeforeSearch = false
        loadingFailedRateLimitedBeforeSearch = false
        showLoadMoreBeforeSearch = false
        algoliaHitsPerPageBeforeSearch = StorySearchController.ALGOLIA_HITS_INCREMENT
        lastAlgoliaTopStoriesStartTimeBeforeSearch = 0
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
        paginationMode = SettingsUtils.shouldUsePaginationMode(requireContext())

        mainAdapter = createStoryAdapter(mainStories)
        searchAdapter = createStoryAdapter(searchStories)
        adapter = mainAdapter
        stories = mainStories

        configureStoryAdapter(mainAdapter!!)
        configureStoryAdapter(searchAdapter!!)
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

        mainAdapter!!.type = previousMainType
        searchAdapter!!.type = previousSearchType
        updateAdapterPaginationMode(mainAdapter)
        updateAdapterPaginationMode(searchAdapter)
        mainAdapter!!.visibleStoryCount = previousMainVisibleStoryCount
        searchAdapter!!.visibleStoryCount = previousSearchVisibleStoryCount
        mainAdapter!!.showLoadMoreButton = previousMainShowLoadMoreButton
        searchAdapter!!.showLoadMoreButton = previousSearchShowLoadMoreButton
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
        return StoryDisplaySettings.from(requireContext()).createListState(
            adapterStories,
            this.preferredTypeIndex
        )
    }

    private fun configureStoryAdapter(configuredAdapter: StoryListState) {
        updateAdapterPaginationMode(configuredAdapter)
        configuredAdapter.visibleStoryCount =
            if (configuredAdapter.paginationMode) StoryListState.PAGINATION_PAGE_SIZE else Int.MAX_VALUE
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

        val story = stories!!.get(position)
        if (alwaysOpenComments && !story.isFrontpageLink) {
            clickedComments(position)
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastClick > CLICK_INTERVAL) {
            lastClick = now
        } else {
            return
        }

        if (story.loaded) {
            if (story.isFrontpageLink) {
                story.clicked = true
                adapter!!.updateStoryClickedState(position)
                Utils.launchCustomTab(requireContext(), story.url)
                return
            }

            markStoryClicked(story)
            adapter!!.updateStoryClickedState(position)

            if (story.isLink) {
                if (SettingsUtils.shouldUseIntegratedWebView(requireContext())) {
                    openComments(story, position, true)
                } else {
                    Utils.launchCustomTab(requireContext(), story.url)
                }
            } else {
                openComments(story, position, false)
            }
        } else if (story.loadingFailed) {
            story.loadingFailed = false
            loadStory(story, 0)
            adapter!!.notifyItemChanged(position)
        }
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

    private val isTabletSplitLayout: Boolean
        get() {
            if (!this.isAdded) {
                return false
            }
            val activity = requireActivity()
            return activity.isAdaptiveTwoPaneNavigation
                    && !activity.isAdaptiveFoldableNavigation
        }

    private fun toggleStoryRead(story: Story?, position: Int) {
        if (!this.isAdded || story == null) {
            return
        }
        story.clicked = !story.clicked
        if (story.clicked) {
            addHistory(requireContext(), story.id)
        } else {
            removeHistoryById(requireContext(), story.id)
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
        story: Story?, position: Int, currentlyUpvoted: Boolean,
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
        val callback: ActionCallback = object : ActionCallback {
            override fun onSuccess(response: Response) {
                if (response != null) {
                    response.close()
                }
                completion.run()
            }

            override fun onFailure(summary: String?, response: String?) {
                Utils.setUpvoted(context, story.id, false, currentlyUpvoted)
                if (isCurrentStoryActionContext(
                        actionGeneration, actionAdapter, actionStories
                    )
                ) {
                    val restoredPosition = stories!!.indexOf(story)
                    if (restoredPosition >= 0) {
                        adapter!!.notifyItemChanged(restoredPosition)
                    }
                }
                completion.run()
            }
        }
        if (newUpvoted) {
            UserActions.upvote(context, story.id, callback)
        } else {
            UserActions.unvote(context, story.id, callback)
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
        UserActions.setFavorite(
            context, story.id, newFavorited,
            object : ActionCallback {
                override fun onSuccess(response: Response) {
                    if (response != null) {
                        response.close()
                    }
                    completion.run()
                }

                override fun onFailure(summary: String?, response: String?) {
                    Utils.setFavorite(context, story.id, currentlyFavorited)
                    if (!isCurrentStoryActionContext(
                            actionGeneration, actionAdapter, actionStories
                        )
                    ) {
                        completion.run()
                        return
                    }
                    val currentIndex = stories!!.indexOf(story)
                    if (currentlyFavorited && actionIsFavoritesList && currentIndex == -1) {
                        val restoreIndex =
                            if (optimisticIndex >= 0) min(optimisticIndex, stories!!.size) else
                                0
                        stories!!.add(restoreIndex, story)
                        adapter!!.notifyItemInserted(restoreIndex)
                        updateHeader()
                    } else if (currentIndex >= 0) {
                        adapter!!.notifyItemChanged(currentIndex)
                    }
                    completion.run()
                }
            })
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
            val story = stories!!.get(position)
            if (story != null && story.id == storyId) return position
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
        val newHideJobs = SettingsUtils.shouldHideJobs(requireContext())
        hideClicked = SettingsUtils.shouldHideClicked(requireContext())
        alwaysOpenComments = SettingsUtils.shouldAlwaysOpenComments(requireContext())
        refreshTypeSpinnerItemsIfNeeded()
        syncVisibleUserItemListWithLocalCache()
        refreshBookmarksIfNeeded()

        val timeDiff = System.currentTimeMillis() - lastLoaded

        // if more than 1 hr
        val shouldShowUpdateButton = SettingsUtils.shouldAlwaysShowTapToRefresh(requireContext())
                || (timeDiff > 1000 * 60 * 60 && !searching && !isBookmarksType(adapter!!.type) && !isUserItemListType(
            adapter!!.type
        ) && !currentTypeIsAlgolia())
        if (shouldShowUpdateButton) {
            showUpdateButton()
        } else {
            hideUpdateButton()
        }

        val displaySettings = StoryDisplaySettings.from(requireContext())
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

        val newPaginationMode = SettingsUtils.shouldUsePaginationMode(requireContext())
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

        val previewImageMode = SettingsUtils.getPreferredStoryPreviewImageMode(requireContext())
        val previewImageModeChanged = adapter!!.previewImageMode != previewImageMode
        val tintCardUsingPreview = SettingsUtils.shouldTintCardUsingPreview(requireContext())
        val storyCardShellChanged =
            adapter!!.tintCardUsingPreview != tintCardUsingPreview && !adapter!!.cardStyle
        val preferredFont = SettingsUtils.getPreferredFont(requireContext())
        val fontChanged = preferredFont != adapter!!.font
        val fontCacheChanged = TextUtils.isEmpty(FontUtils.font) || FontUtils.font != preferredFont

        if (fontCacheChanged) {
            FontUtils.init(requireContext())
        }

        adapter!!.previewImageMode = previewImageMode
        adapter!!.showSummary = SettingsUtils.shouldShowStorySummary(requireContext())
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
        val currentHistoriesChangeVersion = getChangeVersion()
        if (historiesChangeVersion == currentHistoriesChangeVersion || adapter == null || stories == null) {
            return
        }

        historiesChangeVersion = currentHistoriesChangeVersion

        if (searching && searchController.isOnlyClicked) {
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
            val clicked = isHistoryExist(story.id)
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
            if (isHistoryExist(story.id)) {
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
        saveStoryStateForRecreation()
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
        saveStoryStateForRecreation()
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

        val story = stories!!.get(position)
        if (story.loaded) {
            if (story.isFrontpageLink) {
                story.clicked = true
                adapter!!.updateStoryClickedState(position)
                Utils.launchCustomTab(requireContext(), story.url)
                return
            }

            markStoryClicked(story)
            adapter!!.updateStoryClickedState(position)

            openComments(story, position, false)
        }
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

        val url = "https://hacker-news.firebaseio.com/v0/item/" + masterStory.id + ".json"
        val stringRequest = StringRequest(
            Request.Method.GET,
            url,
            com.android.volley.Response.Listener { response: String? ->
                if (!this.isAdded || adapter == null) {
                    return@Listener
                }
                try {
                    JSONParser.updateCommentMasterStoryWithHNJson(sourceStory, response)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }

                val index = stories!!.indexOf(sourceStory)
                if (index >= 0) {
                    adapter!!.notifyItemChanged(index)
                }

                val refreshedMasterStory = sourceStory.toCommentMasterStory()
                openComments(
                    if (refreshedMasterStory != null) refreshedMasterStory else masterStory,
                    position,
                    false
                )
            },
            com.android.volley.Response.ErrorListener { error: VolleyError? ->
                openComments(
                    masterStory,
                    position,
                    false
                )
            })

        stringRequest.setTag(requestTag)
        queue!!.add<String?>(stringRequest)
    }

    private fun markStoryClicked(story: Story) {
        if (!searchController.isOnlyClicked) {
            story.clicked = true
        }
        addHistory(requireContext(), story.id)
    }

    private fun removeStoryAt(index: Int, loadGeneration: Int, loadVisibleReplacement: Boolean) {
        if (index < 0 || index >= stories!!.size) {
            return
        }

        val removedStory = stories!!.removeAt(index)
        finishPaginationLoadMoreStory(removedStory, loadGeneration)
        clearStoryLoadState(removedStory)
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
                if (!TextUtils.isEmpty(phrase) && title.contains(phrase!!.lowercase(Locale.getDefault()))) {
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

        val startedAt = loadingStoryStartTimes.get(story.id)
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
            loadingStoryStartTimes.put(story.id, startedAt)
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

        val currentStartedAt = loadingStoryStartTimes.get(story.id)
        if (currentStartedAt != null && currentStartedAt == startedAt) {
            loadingStoryStartTimes.remove(story.id)
        }
    }

    private fun isCurrentStoryLoad(story: Story?, startedAt: Long): Boolean {
        if (story == null) {
            return false
        }

        val currentStartedAt = loadingStoryStartTimes.get(story.id)
        return currentStartedAt != null && currentStartedAt == startedAt
    }

    private fun clearLoadingStoryState() {
        loadingStoryStartTimes.clear()
    }

    private fun loadStory(story: Story, attempt: Int, loadGeneration: Int = storyListGeneration) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return
        }

        val pendingRemoval = pendingStoryRemovals.get(story.id)
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

        val url = "https://hacker-news.firebaseio.com/v0/item/" + story.id + ".json"

        val stringRequest = StringRequest(
            Request.Method.GET, url,
            com.android.volley.Response.Listener { response: String? ->
                if (!isCurrentStoryLoad(story, startedAt)) {
                    return@Listener
                }
                clearStoryLoadState(story, startedAt)
                if (!isCurrentStoryListGeneration(loadGeneration)) {
                    return@Listener
                }
                val index = stories!!.indexOf(story)
                if (index < 0) {
                    return@Listener
                }
                try {
                    if (!JSONParser.updateStoryWithHNJson(
                            response,
                            story,
                            isHistoryType(adapter!!.type)
                        )
                    ) {
                        enqueueStoryRemoval(story, loadGeneration, false)
                        return@Listener
                    }

                    finishPaginationLoadMoreStory(story, loadGeneration)

                    if (story.isComment && currentTypeUsesCommentRows()) {
                        loadCommentMaster(story, story.parentId, 0, loadGeneration)
                    }

                    if (currentTypeUsesSavedItemFilter() && !shouldShowStoryForSavedItemFilter(story)) {
                        enqueueStoryRemoval(story, loadGeneration, true)
                        return@Listener
                    }

                    if (shouldFilterLoadedStory(story)) {
                        enqueueStoryRemoval(story, loadGeneration, false)
                        return@Listener
                    }

                    val context = this.context
                    if (context != null) {
                        requestPreviewImagePrefetch(context, story)
                    }

                    enqueueStoryRowChange(story, loadGeneration)
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Utils.log("Failed to load story with id: " + story.id)
                    story.loadingFailed = true
                    finishPaginationLoadMoreStory(story, loadGeneration)
                    updatePreviewImagePrefetchRampCompletion()
                    enqueueStoryRowChange(story, loadGeneration)
                }
            }, com.android.volley.Response.ErrorListener { error: VolleyError? ->
                if (!isCurrentStoryLoad(story, startedAt)) {
                    return@ErrorListener
                }
                clearStoryLoadState(story, startedAt)
                if (!isCurrentStoryListGeneration(loadGeneration)) {
                    return@ErrorListener
                }
                if (story.loaded) {
                    return@ErrorListener
                }
                error!!.printStackTrace()
                story.loadingFailed = true
                if (attempt >= 2) {
                    finishPaginationLoadMoreStory(story, loadGeneration)
                }
                updatePreviewImagePrefetchRampCompletion()
                val index = stories!!.indexOf(story)
                if (index >= 0) {
                    enqueueStoryRowChange(story, loadGeneration)
                    loadStory(story, attempt + 1, loadGeneration)
                }
            })

        stringRequest.setTag(requestTag)
        queue!!.add<String?>(stringRequest)
    }

    fun attemptRefresh() {
        attemptRefresh(false)
    }

    private fun attemptStoryTypeRefresh() {
        attemptRefresh(false, true)
    }

    private fun invalidateAlgoliaLoad() {
        algoliaRequestGeneration++
        algoliaLoading = false
        activeAlgoliaUrl = null
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
            // lets load bookmarks instead - or rather add empty stories with correct id:s and start loading them
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
            val histories = loadHistories(requireContext(), true)

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

        // if none of the above, do a normal loading
        val storyListUrl = currentStoryType.hackerNewsUrl
        if (storyListUrl == null) {
            this.isRefreshIndicatorShowing = false
            loadingFailed = true
            loadingFailedRateLimited = false
            Log.w(
                TAG,
                ("Story list refresh failed before request: missing URL for type=" + currentStoryType.label
                        + ", generation=" + refreshGeneration)
            )
            updateHeader()
            return
        }

        val stringRequest = StringRequest(
            Request.Method.GET, storyListUrl,
            com.android.volley.Response.Listener { response: String? ->
                if (!isCurrentStoryListGeneration(refreshGeneration)) {
                    Log.d(
                        TAG,
                        ("Ignoring stale story list success for type=" + currentStoryType.label
                                + ", generation=" + refreshGeneration
                                + ", currentGeneration=" + storyListGeneration)
                    )
                    return@Listener
                }
                this.isRefreshIndicatorShowing = false
                try {
                    val jsonArray = JSONArray(response)
                    val itemIds = java.util.ArrayList<Int>()

                    for (i in 0..<jsonArray.length()) {
                        val id = jsonArray.get(i).toString().toInt()
                        itemIds.add(id)
                    }

                    showingCached = false
                    replaceStories(createLoadingStoriesFromIds(itemIds))

                    if (loadingFailed) {
                        loadingFailed = false
                        loadingFailedServerError = false
                        loadingFailedRateLimited = false
                    }

                    updateHeader()

                    loadInitialVisibleStories(refreshGeneration)
                } catch (e: JSONException) {
                    Log.w(
                        TAG,
                        ("Failed to parse story list JSON for type=" + currentStoryType.label
                                + ", generation=" + refreshGeneration
                                + ", responseLength=" + (if (response == null) 0 else response.length)),
                        e
                    )
                } catch (e: NumberFormatException) {
                    Log.w(
                        TAG,
                        ("Failed to parse story id in list for type=" + currentStoryType.label
                                + ", generation=" + refreshGeneration
                                + ", responseLength=" + (if (response == null) 0 else response.length)),
                        e
                    )
                }
            }, com.android.volley.Response.ErrorListener { error: VolleyError? ->
                if (!isCurrentStoryListGeneration(refreshGeneration)) {
                    Log.d(
                        TAG,
                        ("Ignoring stale story list failure for type=" + currentStoryType.label
                                + ", generation=" + refreshGeneration
                                + ", currentGeneration=" + storyListGeneration)
                    )
                    return@ErrorListener
                }
                this.isRefreshIndicatorShowing = false
                loadingFailed = true
                loadingFailedRateLimited = isRateLimitedError(error)
                Log.w(
                    TAG, ("Story list request failed for type=" + currentStoryType.label
                            + ", generation=" + refreshGeneration
                            + ", error=" + error)
                )
                updateHeader()
            })

        updateHeader()
        stringRequest.setTag(requestTag)
        queue!!.add<String?>(stringRequest)
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
        Handler(Looper.getMainLooper()).postDelayed(Runnable {
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
                            + ", networkAvailable=" + Utils.isNetworkAvailable(
                        this@StoriesCoordinator.requireContext()
                    ))
                )
            }
        }, 15000)
        UserActions.fetchStoryListIds(
            ctx,
            storyType.hackerNewsPath,
            storyType.label.lowercase(),
            storyType.usesCommentRows(),
            frontDay,
            object : StoryListCallback {
                override fun onSuccess(
                    itemIds: MutableList<Int>,
                    commentIds: MutableList<Int>,
                    nextPageUrl: String?
                ) {
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
                        return
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
                }

                override fun onFailure(summary: String?, response: String?) {
                    callbackReceived[0] = true
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
                        return
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
            })

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
        UserActions.fetchStoryListPage(
            ctx,
            nextPageUrl,
            storyType.label.lowercase(),
            storyType.usesCommentRows(),
            object : StoryListCallback {
                override fun onSuccess(
                    itemIds: MutableList<Int>,
                    commentIds: MutableList<Int>,
                    nextPageUrl: String?
                ) {
                    if (!this@StoriesCoordinator.isAdded || adapter == null || this@StoriesCoordinator.currentStoryType != storyType || scrapedFrontpageStoryType != storyType || !isCurrentStoryListGeneration(
                            refreshGeneration
                        )
                    ) {
                        return
                    }

                    scrapedFrontpageNextPageLoading = false
                    adapter!!.setLoadMoreLoading(false)
                    scrapedFrontpageNextPageUrl = nextPageUrl
                    val newStories =
                        createNewLoadingStoriesFromIds(itemIds, HashSet<Int>(commentIds))
                    stories!!.addAll(newStories.filterNotNull())
                    adapter!!.showLoadMoreButton = !TextUtils.isEmpty(scrapedFrontpageNextPageUrl)
                    if (adapter!!.paginationMode && !newStories.isEmpty()) {
                        adapter!!.visibleStoryCount =
                            min(adapter!!.visibleStoryCount + newStories.size, stories!!.size)
                    }
                    adapter!!.notifyDataSetChanged()
                    loadVisibleStories(refreshGeneration)
                    updateHeader()
                }

                override fun onFailure(summary: String?, response: String?) {
                    if (!this@StoriesCoordinator.isAdded || adapter == null || this@StoriesCoordinator.currentStoryType != storyType || scrapedFrontpageStoryType != storyType || !isCurrentStoryListGeneration(
                            refreshGeneration
                        )
                    ) {
                        return
                    }

                    scrapedFrontpageNextPageLoading = false
                    adapter!!.setLoadMoreLoading(false)
                    adapter!!.showLoadMoreButton = true
                    adapter!!.notifyDataSetChanged()
                    updateHeader()
                }
            })
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

        UserActions.fetchHackerNewsListLinks(ctx, object : StoryRowsCallback {
            override fun onSuccess(linkRows: MutableList<Story>) {
                if (!this@StoriesCoordinator.isAdded || adapter == null || this@StoriesCoordinator.currentStoryType != storyType || !isCurrentStoryListGeneration(
                        refreshGeneration
                    )
                ) {
                    return
                }

                this@StoriesCoordinator.isRefreshIndicatorShowing = false
                loadingFailed = linkRows.isEmpty()
                loadingFailedServerError = false
                loadingFailedRateLimited = false
                showingCached = false

                if (!loadingFailed) {
                    replaceStories(linkRows)
                    loadedTo = stories!!.size - 1
                }

                updateHeader()
            }

            override fun onFailure(summary: String?, response: String?) {
                if (!this@StoriesCoordinator.isAdded || adapter == null || this@StoriesCoordinator.currentStoryType != storyType || !isCurrentStoryListGeneration(
                        refreshGeneration
                    )
                ) {
                    return
                }

                this@StoriesCoordinator.isRefreshIndicatorShowing = false
                loadingFailed = true
                loadingFailedServerError = false
                loadingFailedRateLimited = isRateLimitedResponse(summary, response)
                updateHeader()
            }
        })

        updateHeader()
    }

    private fun isRateLimitedError(error: VolleyError?): Boolean {
        return error != null && error.networkResponse != null && error.networkResponse.statusCode == 429
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
        itemIds: MutableList<Int>,
        commentIds: MutableSet<Int> = HashSet<Int>()
    ): java.util.ArrayList<Story> {
        val refreshedStories = java.util.ArrayList<Story>()
        val ctx = this.context

        for (id in itemIds) {
            if (hideClicked && HistoriesUtils.isHistoryExist(id!!)) {
                continue
            }

            val story = Story("Loading...", id!!, false, HistoriesUtils.isHistoryExist(id))
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

        val url = "https://hacker-news.firebaseio.com/v0/item/" + parentId + ".json"
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            com.android.volley.Response.Listener { response: String? ->
                if (!isCurrentStoryListGeneration(loadGeneration)) {
                    return@Listener
                }
                try {
                    if (TextUtils.isEmpty(response) || "null" == response) {
                        return@Listener
                    }

                    val parent = JSONObject(response ?: return@Listener)
                    val parentType = parent.optString("type")
                    if ("comment" == parentType) {
                        loadCommentMaster(
                            story,
                            parent.optInt("parent", 0),
                            attempt + 1,
                            loadGeneration
                        )
                        return@Listener
                    }

                    if (!JSONParser.updateCommentMasterStoryWithHNJson(story, response)) {
                        return@Listener
                    }

                    val index = stories!!.indexOf(story)
                    if (index >= 0) {
                        enqueueStoryRowChange(story, loadGeneration)
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }, com.android.volley.Response.ErrorListener { error: VolleyError? ->
                if (attempt < 2 && isCurrentStoryListGeneration(loadGeneration)) {
                    loadCommentMaster(story, parentId, attempt + 1, loadGeneration)
                }
            })

        stringRequest.setTag(requestTag)
        queue!!.add<String?>(stringRequest)
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
        val callback: UserItemListCallback = object : UserItemListCallback {
            override fun onSuccess(itemIds: MutableList<Int>, commentIds: MutableList<Int>) {
                if (!this@StoriesCoordinator.isAdded || adapter == null || !isSameUserItemListType(
                        adapter!!.type,
                        upvotedTypeForSync
                    ) || !isCurrentStoryListGeneration(syncGeneration)
                ) {
                    return
                }

                val currentContext: Context? = this@StoriesCoordinator.context
                if (currentContext == null) {
                    return
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
            }

            override fun onFailure(summary: String?, response: String?) {
                if (!this@StoriesCoordinator.isAdded || adapter == null || !isSameUserItemListType(
                        adapter!!.type,
                        upvotedTypeForSync
                    ) || !isCurrentStoryListGeneration(syncGeneration)
                ) {
                    return
                }

                this@StoriesCoordinator.isRefreshIndicatorShowing = false
                userItemListInitialLoadInProgress = false
                loadingFailed = stories!!.isEmpty()
                loadingFailedRateLimited = isRateLimitedResponse(summary, response)
                updateHeader()
                Toast.makeText(requireContext(), summary, Toast.LENGTH_SHORT).show()
            }
        }

        if (upvotedTypeForSync) {
            UserActions.fetchUpvoted(ctx, callback)
        } else {
            UserActions.fetchFavorites(ctx, callback)
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
        itemIds: MutableList<Int>,
        commentIds: MutableSet<Int>
    ): Boolean {
        if (itemIdsMatchUserItemListStories(itemIds, commentIds)) {
            return false
        }

        replaceUserItemListStoriesWithIds(itemIds, commentIds)
        return true
    }

    private fun itemIdsMatchUserItemListStories(
        itemIds: MutableList<Int>,
        commentIds: MutableSet<Int>?
    ): Boolean {
        if (userItemListStories.size != itemIds.size || userItemListCommentIds != commentIds) {
            return false
        }

        for (i in userItemListStories.indices) {
            if (userItemListStories.get(i).id != itemIds.get(i)) {
                return false
            }
        }

        return true
    }

    private fun replaceUserItemListStoriesWithIds(
        itemIds: MutableList<Int>,
        commentIds: MutableSet<Int>
    ) {
        val existingStories: MutableMap<Int, Story> = HashMap<Int, Story>()
        for (story in (if (userItemListStories.isEmpty()) stories else userItemListStories)!!) {
            existingStories.put(story.id, story)
        }

        val refreshedStories = java.util.ArrayList<Story>()
        for (id in itemIds) {
            val existingStory = existingStories.get(id)
            val story = if (existingStory != null) existingStory else Story(
                "Loading...",
                id!!,
                false,
                false
            )
            if (commentIds.contains(id)) {
                story.isComment = true
            }
            refreshedStories.add(story)
        }

        queue!!.cancelAll(requestTag)
        clearLoadingStoryState()
        userItemListStories.clear()
        userItemListStories.addAll(refreshedStories)
        userItemListCommentIds = HashSet<Int>(commentIds)
        replaceStories(this.filteredSavedItemStories, true)
        loadInitialVisibleStories()
        updateHeader()
    }

    private val filteredSavedItemStories: ArrayList<Story>
        get() {
            val filteredStories = java.util.ArrayList<Story>()
            val sourceStories =
                if (isBookmarksType(adapter!!.type)) bookmarkStories else userItemListStories
            for (story in sourceStories) {
                if (shouldShowStoryForSavedItemFilter(story)) {
                    filteredStories.add(story)
                }
            }
            return filteredStories
        }

    private fun shouldShowStoryForSavedItemFilter(story: Story): Boolean {
        if (isBookmarksType(adapter!!.type) && !story.loaded) {
            return true
        }
        if (userItemListFilter == USER_ITEM_LIST_FILTER_STORIES) {
            return !story.isComment
        }
        if (userItemListFilter == USER_ITEM_LIST_FILTER_COMMENTS) {
            return story.isComment
        }
        return true
    }

    private fun applySavedItemFilter(notifyDataSetChanged: Boolean) {
        replaceStories(this.filteredSavedItemStories, notifyDataSetChanged)
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
        if (context == null || adapter == null || stories == null || stories!!.isEmpty()
            || SettingsUtils.STORY_PREVIEW_IMAGE_OFF == adapter!!.previewImageMode
        ) {
            return
        }

        val firstIndex = if (firstVisibleItem == NO_POSITION) 0 else max(0, firstVisibleItem)
        var lastIndex = if (lastVisibleItem == NO_POSITION) min(
            this.initialLoadCount - 1, stories!!.size - 1
        ) else min(lastVisibleItem + STORY_VISIBLE_PREFETCH_THRESHOLD, stories!!.size - 1)
        if (adapter!!.paginationMode) {
            lastIndex = min(lastIndex, adapter!!.visibleStoryCount - 1)
        }

        if (lastIndex < firstIndex) {
            return
        }

        beginPreviewImagePrefetchRamp(lastIndex)
        for (i in firstIndex..lastIndex) {
            requestPreviewImagePrefetch(context, stories!!.get(i))
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
            searchAdapter!!.type = mainAdapter!!.type
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
        get() = searchController.getCurrentTopStoriesStartTime(this.currentStoryType)

    private fun loadTopStoriesSince(start_i: Int, showSwipeRefreshIndicator: Boolean) {
        lastAlgoliaTopStoriesStartTime = start_i
        loadAlgolia(
            searchController.buildTopStoriesUrl(start_i, algoliaHitsPerPage),
            showSwipeRefreshIndicator
        )
    }

    private fun search(query: String?, resetResultLimit: Boolean = true) {
        lastSearch = query
        if (resetResultLimit) {
            resetAlgoliaResultLimit()
        }

        if (searchController.isOnlyClicked) {
            loadOnlyClickedSearch(query)
            return
        }

        loadAlgolia(searchController.buildSearchUrl(query, algoliaHitsPerPage))
    }

    private fun canLoadMoreAlgoliaResults(rawParsedStoryCount: Int): Boolean {
        return searchController.canLoadMoreResults(rawParsedStoryCount, algoliaHitsPerPage)
    }

    private fun loadMoreAlgoliaResults() {
        if (algoliaLoading) {
            return
        }

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
        algoliaHitsPerPage += StorySearchController.ALGOLIA_HITS_INCREMENT
        if (searching) {
            search(lastSearch, false)
        } else if (currentTypeIsAlgolia()) {
            val startTime = if (lastAlgoliaTopStoriesStartTime > 0)
                lastAlgoliaTopStoriesStartTime
            else
                this.currentAlgoliaTopStoriesStartTime
            loadTopStoriesSince(startTime, false)
        }
    }

    private fun loadOnlyClickedSearch(query: String?) {
        storyListGeneration++
        clearLoadingStoryState()
        resetPreviewImagePrefetchRamp()
        invalidateAlgoliaLoad()
        val requestGeneration = algoliaRequestGeneration
        algoliaLoading = true
        activeAlgoliaUrl = null
        loadingFailed = false
        loadingFailedServerError = false
        loadingFailedRateLimited = false
        showingCached = false
        queue!!.cancelAll(requestTag)
        clearLoadingStoryState()

        if (!stories!!.isEmpty()) {
            clearStories()
        }

        val histories = loadHistories(requireContext(), true)
        if (histories.isEmpty()) {
            completeOnlyClickedSearch(requestGeneration, java.util.ArrayList<Story>(), 0, 0)
            return
        }

        val normalizedQuery = searchController.normalizeQuery(query)
        val matchedStories: MutableList<Story?> = java.util.ArrayList(histories.size)
        for (i in histories.indices) {
            matchedStories.add(null)
        }

        val pendingRequests = intArrayOf(histories.size)
        val failedRequests = intArrayOf(0)

        for (i in histories.indices) {
            val history = histories.get(i)
            val storyIndex = i
            val story = Story("Loading...", history.id, false, false)
            val url = "https://hacker-news.firebaseio.com/v0/item/" + history.id + ".json"
            val stringRequest = StringRequest(
                Request.Method.GET, url,
                com.android.volley.Response.Listener { response: String? ->
                    if (requestGeneration != algoliaRequestGeneration) {
                        return@Listener
                    }
                    try {
                        if (JSONParser.updateStoryWithHNJson(response, story, false)
                            && searchController.shouldIncludeOnlyClickedStory(
                                story,
                                normalizedQuery,
                                StoryFilter { thisStory: Story -> shouldFilterLoadedStory(thisStory) })
                        ) {
                            matchedStories.set(storyIndex, story)
                        }
                    } catch (e: JSONException) {
                        failedRequests[0]++
                        e.printStackTrace()
                    }
                    finishOnlyClickedSearchRequest(
                        requestGeneration,
                        pendingRequests,
                        failedRequests,
                        matchedStories
                    )
                }, com.android.volley.Response.ErrorListener { error: VolleyError? ->
                    if (requestGeneration != algoliaRequestGeneration) {
                        return@ErrorListener
                    }
                    failedRequests[0]++
                    error!!.printStackTrace()
                    finishOnlyClickedSearchRequest(
                        requestGeneration,
                        pendingRequests,
                        failedRequests,
                        matchedStories
                    )
                })

            stringRequest.setShouldCache(false)
            stringRequest.setTag(requestTag)
            queue!!.add<String?>(stringRequest)
        }

        updateHeader()
    }

    private fun finishOnlyClickedSearchRequest(
        requestGeneration: Int,
        pendingRequests: IntArray,
        failedRequests: IntArray,
        matchedStories: MutableList<Story?>
    ) {
        pendingRequests[0]--
        if (pendingRequests[0] > 0 || requestGeneration != algoliaRequestGeneration) {
            return
        }

        val finishedStories = java.util.ArrayList<Story>()
        for (story in matchedStories) {
            if (story != null) {
                finishedStories.add(story)
            }
        }
        searchController.sortOnlyClickedResultsIfNeeded(finishedStories, lastSearch)

        completeOnlyClickedSearch(
            requestGeneration,
            finishedStories,
            failedRequests[0],
            matchedStories.size
        )
    }

    private fun completeOnlyClickedSearch(
        requestGeneration: Int,
        finishedStories: MutableList<Story>,
        failedRequests: Int,
        totalRequests: Int
    ) {
        if (requestGeneration != algoliaRequestGeneration) {
            return
        }

        algoliaLoading = false
        activeAlgoliaUrl = null
        this.isRefreshIndicatorShowing = false
        loadingFailed = totalRequests > 0 && failedRequests == totalRequests
        loadingFailedServerError = false
        loadingFailedRateLimited = false
        replaceStories(finishedStories)
        loadedTo = stories!!.size - 1
        scheduleLoadedPreviewImagePrefetchNearViewport()
        updateHeader()
    }

    private fun loadAlgolia(url: String?, showSwipeRefreshIndicator: Boolean = false) {
        if (algoliaLoading && TextUtils.equals(activeAlgoliaUrl, url)) {
            return
        }

        invalidateAlgoliaLoad()
        val requestGeneration = algoliaRequestGeneration
        algoliaLoading = true
        activeAlgoliaUrl = url
        loadingFailed = false
        loadingFailedServerError = false
        loadingFailedRateLimited = false
        queue!!.cancelAll(requestTag)

        this.isRefreshIndicatorShowing = !searching && showSwipeRefreshIndicator
        if (searching && !stories!!.isEmpty()) {
            clearStories()
        }
        val stringRequest = StringRequest(
            Request.Method.GET, url,
            com.android.volley.Response.Listener { response: String? ->
                // Parse JSON on background thread
                BackgroundJSONParser.parseAlgoliaJson(response, object : AlgoliaParseCallback {
                    override fun onParseSuccess(parsedStories: MutableList<Story>) {
                        if (requestGeneration != algoliaRequestGeneration) {
                            return
                        }

                        algoliaLoading = false
                        activeAlgoliaUrl = null
                        this@StoriesCoordinator.isRefreshIndicatorShowing = false
                        val preservePaginationForLoadMore = algoliaLoadMoreInProgress
                        val rawParsedStoryCount = parsedStories.size

                        val iterator: MutableIterator<Story> = parsedStories.iterator()
                        while (iterator.hasNext()) {
                            val story = iterator.next()
                            if (story == null) {
                                iterator.remove()
                                continue
                            }
                            story.clicked = isHistoryExist(story.id)
                            var shouldRemove = shouldFilterLoadedStory(story)

                            if (!shouldRemove && hideClicked && story.clicked) {
                                shouldRemove = true
                            }

                            if (shouldRemove) {
                                iterator.remove()
                            }
                        }

                        loadingFailed = false
                        loadingFailedServerError = false
                        loadingFailedRateLimited = false
                        showingCached = false

                        if (preservePaginationForLoadMore) {
                            replaceAlgoliaLoadMoreStories(
                                parsedStories,
                                canLoadMoreAlgoliaResults(rawParsedStoryCount)
                            )
                            loadedTo = stories!!.size - 1
                            scheduleLoadedPreviewImagePrefetchNearViewport()
                        } else {
                            replaceStories(
                                parsedStories,
                                false,
                                canLoadMoreAlgoliaResults(rawParsedStoryCount)
                            )
                            loadedTo = stories!!.size - 1
                            scheduleLoadedPreviewImagePrefetchNearViewport()
                        }
                        algoliaLoadMoreInProgress = false
                        adapter!!.setLoadMoreLoading(false)
                        algoliaLoadMoreVisibleStoryCount = -1
                        updateHeader()
                    }

                    override fun onParseError(error: JSONException) {
                        if (requestGeneration != algoliaRequestGeneration) {
                            return
                        }

                        algoliaLoading = false
                        activeAlgoliaUrl = null
                        algoliaLoadMoreInProgress = false
                        if (adapter != null) {
                            adapter!!.setLoadMoreLoading(false)
                        }
                        algoliaLoadMoreVisibleStoryCount = -1
                        this@StoriesCoordinator.isRefreshIndicatorShowing = false
                        error.printStackTrace()
                    }
                })
            }, com.android.volley.Response.ErrorListener { error: VolleyError? ->
                if (requestGeneration != algoliaRequestGeneration) {
                    return@ErrorListener
                }
                algoliaLoading = false
                activeAlgoliaUrl = null
                algoliaLoadMoreInProgress = false
                if (adapter != null) {
                    adapter!!.setLoadMoreLoading(false)
                }
                algoliaLoadMoreVisibleStoryCount = -1

                if (error!!.networkResponse != null && error.networkResponse.statusCode == 404) {
                    loadingFailedServerError = true
                }
                loadingFailedRateLimited = isRateLimitedError(error)

                error.printStackTrace()
                this.isRefreshIndicatorShowing = false
                loadingFailed = true
                updateHeader()
            })

        updateHeader()

        stringRequest.setShouldCache(false)
        stringRequest.setTag(requestTag)
        queue!!.add<String?>(stringRequest)
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
        if (story == null) {
            syncComposeState()
        } else {
            composeController?.invalidateStory(story.id)
        }
    }

    fun currentTypeIsAlgolia(): Boolean {
        return this.currentStoryType.isAlgolia
    }

    private fun currentTypeIsActive(): Boolean {
        return this.currentStoryType.isActive
    }

    private fun currentTypeIsFront(): Boolean {
        return this.currentStoryType.isFront
    }

    private fun currentTypeIsScrapedFrontpage(): Boolean {
        return this.currentStoryType.isScrapedFrontpage
    }

    private fun isBookmarksType(type: Int): Boolean {
        return getStoryType(type).isBookmarks
    }

    private fun isHistoryType(type: Int): Boolean {
        return getStoryType(type).isHistory
    }

    private fun isFavoritesType(type: Int): Boolean {
        return getStoryType(type).isFavorites
    }

    private fun isUpvotedType(type: Int): Boolean {
        return getStoryType(type).isUpvoted
    }

    private fun isUserItemListType(type: Int): Boolean {
        return getStoryType(type).isUserItemList
    }

    private fun currentTypeUsesSavedItemFilter(): Boolean {
        return this.currentStoryType.usesSavedItemFilter()
    }

    private fun currentSavedItemSourceHasItems(): Boolean {
        if (isBookmarksType(adapter!!.type)) {
            return !bookmarkStories.isEmpty()
        }
        if (isUserItemListType(adapter!!.type)) {
            return !userItemListStories.isEmpty()
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

    private fun currentTypeUsesCommentRows(): Boolean {
        return this.currentStoryType.usesCommentRows()
    }

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
        return if (type < typeAdapterList.size) typeAdapterList.get(type) else null
    }

    private fun getTypeIndex(label: CharSequence?): Int {
        if (label == null || this.context == null) {
            return -1
        }

        val typeLabels = buildTypeAdapterList(requireContext())
        for (i in typeLabels.indices) {
            if (TextUtils.equals(label, typeLabels.get(i))) {
                return i
            }
        }

        return -1
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
        if (composeController != null) {
            composeController!!.beginPredictiveBack(predictiveSearchBackProgress)
        }
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
        if (composeController != null) {
            composeController!!.updatePredictiveBack(predictiveSearchBackProgress)
        }
    }

    fun cancelSearchBackProgress() {
        if (!predictiveSearchBackInProgress) {
            return
        }

        finishSearchBackFromCurrentVisualState = false
        predictiveSearchBackInProgress = false
        predictiveSearchBackProgress = 0f
        useSearchStoryList()
        if (composeController != null) {
            composeController!!.cancelPredictiveBack()
        }
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
        if (composeController != null) {
            composeController!!.commitPredictiveBack()
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

    companion object {
        private const val TAG = "StoriesCoordinator"
        private val NO_POSITION = -1
        private val NO_PENDING_LINK_SUMMARY_STORY_ID = -1
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
