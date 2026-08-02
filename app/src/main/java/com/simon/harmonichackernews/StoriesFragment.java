package com.simon.harmonichackernews;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.adapters.StoryDisplaySettings;
import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.data.History;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.BackgroundJSONParser;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.ui.settings.SettingsIntents;
import com.simon.harmonichackernews.ui.editor.ComposeEditorContract;
import com.simon.harmonichackernews.ui.stories.StoriesComposeController;
import com.simon.harmonichackernews.ui.stories.StoryListState;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.HistoriesUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryUpdate;
import com.simon.harmonichackernews.utils.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.Response;

public class StoriesFragment extends Fragment {
    private static final String TAG = "StoriesFragment";
    private static final int NO_POSITION = -1;
    private static final int NO_PENDING_LINK_SUMMARY_STORY_ID = -1;
    private static final String STATE_LINK_SUMMARY_STORY_ID =
            "com.simon.harmonichackernews.STATE_LINK_SUMMARY_STORY_ID";

    private StoryClickListener storyClickListener;
    @Nullable private StoriesComposeController composeController;
    private boolean refreshIndicatorShowing;
    private StoryUpdate.StoryUpdateListener storyUpdateListener;
    private final StorySearchController searchController = new StorySearchController();
    private StoriesViewModel storiesViewModel;
    @Nullable
    private StoriesViewModel.State restoredState;
    private boolean restoredStateForCurrentView;
    private StoryCacheController storyCacheController;
    private OnBackPressedCallback linkSummaryBackCallback;
    private int pendingLinkSummaryStoryId = NO_PENDING_LINK_SUMMARY_STORY_ID;

    private StoryListState mainAdapter;
    private StoryListState searchAdapter;
    private StoryListState adapter;
    private final ArrayList<Story> mainStories = new ArrayList<>();
    private final ArrayList<Story> searchStories = new ArrayList<>();
    private List<Story> stories;
    private final ArrayList<Story> bookmarkStories = new ArrayList<>();
    private final ArrayList<Story> userItemListStories = new ArrayList<>();
    private Set<Integer> userItemListCommentIds = new HashSet<>();
    private RequestQueue queue;
    private final Object requestTag = new Object();
    private final Map<Integer, Long> loadingStoryStartTimes = new HashMap<>();
    private ArrayList<String> filterWords;
    private ArrayList<String> filterDomains;
    private Set<String> filteredUsers;
    private boolean hideJobs, alwaysOpenComments, hideClicked;
    private long historiesChangeVersion = -1L;
    @Nullable
    private SharedPreferences bookmarkPreferences;
    private boolean bookmarksChanged = false;
    private final SharedPreferences.OnSharedPreferenceChangeListener bookmarkPreferenceChangeListener =
            (sharedPreferences, key) -> {
                if (Utils.KEY_SHARED_PREFERENCES_BOOKMARKS.equals(key)) {
                    bookmarksChanged = true;
                    refreshBookmarksIfNeeded();
                }
            };
    private boolean searching = false;
    private boolean loadingFailed = false;
    private boolean loadingFailedServerError = false;
    private boolean loadingFailedRateLimited = false;
    private String lastSearch = "";
    private int algoliaRequestGeneration = 0;
    private int storyListGeneration = 0;
    private final Map<Integer, Integer> pendingStoryRowChangeGenerations = new HashMap<>();
    private final Map<Integer, PendingStoryRemoval> pendingStoryRemovals = new HashMap<>();
    private boolean algoliaLoading = false;
    private String activeAlgoliaUrl = null;
    private boolean algoliaLoadMoreInProgress = false;
    private int algoliaLoadMoreVisibleStoryCount = -1;
    private List<Story> storiesBeforeSearch = null;
    private int loadedToBeforeSearch = -1;
    private int visibleStoryCountBeforeSearch = Integer.MAX_VALUE;
    private boolean showingCachedBeforeSearch = false;
    private boolean loadingFailedBeforeSearch = false;
    private boolean loadingFailedServerErrorBeforeSearch = false;
    private boolean loadingFailedRateLimitedBeforeSearch = false;
    private boolean showLoadMoreBeforeSearch = false;
    private int algoliaHitsPerPageBeforeSearch = StorySearchController.ALGOLIA_HITS_INCREMENT;
    private int lastAlgoliaTopStoriesStartTimeBeforeSearch = 0;
    private boolean loadPendingBeforeSearch = false;

    private boolean showingCached = false;

    private int loadedTo = -1;
    private boolean paginationMode = false;
    private final Set<Integer> paginationLoadMoreStoryIds = new HashSet<>();
    private int paginationLoadMoreGeneration = -1;
    private static final int STORY_VISIBLE_PREFETCH_THRESHOLD = 17;
    private static final long STORY_LOAD_STALE_TIMEOUT_MS = 30_000L;
    private static final int PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE = 10;
    private static final long PREVIEW_IMAGE_PREFETCH_RAMP_DELAY_MS = 450L;

    private static final class PendingStoryRemoval {
        final int generation;
        boolean updateHeader;

        PendingStoryRemoval(int generation, boolean updateHeader) {
            this.generation = generation;
            this.updateHeader = updateHeader;
        }
    }
    private int algoliaHitsPerPage = StorySearchController.ALGOLIA_HITS_INCREMENT;
    private int lastAlgoliaTopStoriesStartTime = 0;
    private final Handler previewImagePrefetchHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<Story> previewImagePrefetchQueue = new ArrayList<>();
    private final Set<Integer> queuedPreviewImagePrefetchStoryIds = new HashSet<>();
    private final Set<Integer> requestedPreviewImagePrefetchStoryIds = new HashSet<>();
    private final Runnable previewImagePrefetchRampRunnable = new Runnable() {
        @Override
        public void run() {
            previewImagePrefetchRampScheduled = false;
            previewImagePrefetchRampSlotsRemaining = PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE;
            drainPreviewImagePrefetchQueue();
        }
    };
    private boolean previewImagePrefetchRampScheduled = false;
    private boolean previewImagePrefetchRampComplete = false;
    private int previewImagePrefetchRampSlotsRemaining = PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE;
    private int previewImagePrefetchRampTargetIndex = -1;

    long lastLoaded = 0;
    long lastClick = 0;
    private boolean updateButtonShowing = false;
    private final static long CLICK_INTERVAL = 350;
    private static final int USER_ITEM_LIST_FILTER_STORIES = 0;
    private static final int USER_ITEM_LIST_FILTER_BOTH = 1;
    private static final int USER_ITEM_LIST_FILTER_COMMENTS = 2;

    private boolean predictiveSearchBackInProgress = false;
    private float predictiveSearchBackProgress = 0f;
    private boolean finishSearchBackFromCurrentVisualState = false;
    private boolean userItemListsDropdownVisible = false;
    private boolean userItemListInitialLoadInProgress = false;
    private int userItemListFilter = USER_ITEM_LIST_FILTER_BOTH;
    private Calendar frontPageDayUtc;
    @Nullable
    private String scrapedFrontpageNextPageUrl;
    private boolean scrapedFrontpageNextPageLoading = false;
    private StoryType scrapedFrontpageStoryType = StoryType.UNKNOWN;
    public StoriesFragment() {
    }

    @Nullable
    public StoriesComposeController getComposeController() {
        return composeController;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storiesViewModel = new ViewModelProvider(requireActivity()).get(StoriesViewModel.class);
        bookmarkPreferences = requireContext().getSharedPreferences(
                Utils.GLOBAL_SHARED_PREFERENCES_KEY,
                Context.MODE_PRIVATE);
        bookmarkPreferences.registerOnSharedPreferenceChangeListener(
                bookmarkPreferenceChangeListener);
        initializeComposeController(savedInstanceState);
    }

    private void initializeComposeController(@Nullable Bundle savedInstanceState) {
        if (composeController != null) {
            return;
        }
        if (savedInstanceState != null) {
            pendingLinkSummaryStoryId = savedInstanceState.getInt(
                    STATE_LINK_SUMMARY_STORY_ID, NO_PENDING_LINK_SUMMARY_STORY_ID);
        }

        HistoriesUtils.INSTANCE.init(requireContext());
        historiesChangeVersion = HistoriesUtils.INSTANCE.getChangeVersion();
        restoredState = storiesViewModel == null ? null : storiesViewModel.getState();
        queue = NetworkComponent.getRequestQueueInstance(requireContext());
        setupLinkSummaryBackCallback();
        storyCacheController = createStoryCacheController();

        stories = mainStories;
        filterWords = Utils.getFilterWords(requireContext());
        filterDomains = Utils.getFilterDomains(requireContext());
        filteredUsers = Utils.getFilteredUsers(requireContext());
        hideJobs = SettingsUtils.shouldHideJobs(requireContext());
        hideClicked = SettingsUtils.shouldHideClicked(requireContext());
        alwaysOpenComments = SettingsUtils.shouldAlwaysOpenComments(requireContext());
        userItemListsDropdownVisible = shouldShowUserItemLists(requireContext());
        restoreStoryLists(restoredState);
        setupAdapter();
        registerStoryAdapterDataObservers();
        restoredStateForCurrentView = restoreStoryStateAfterViewSetup(restoredState);
        initializeComposeUi();

        if (restoredStateForCurrentView) {
            updateHeader();
            if (shouldRefreshRestoredStoryState()) {
                attemptRefresh();
            } else if (!searching) {
                resumeInterruptedStoryLoads();
            }
        } else {
            attemptRefresh();
        }

        storyUpdateListener = story -> {
            if (story == null || stories == null || adapter == null) {
                return;
            }
            for (int index = 0; index < stories.size(); index++) {
                Story oldStory = stories.get(index);
                if (oldStory != null && story.id == oldStory.id) {
                    if (!TextUtils.equals(oldStory.title, story.title)
                            || oldStory.descendants != story.descendants
                            || oldStory.score != story.score
                            || oldStory.time != story.time
                            || !TextUtils.equals(oldStory.url, story.url)) {
                        oldStory.title = story.title;
                        oldStory.descendants = story.descendants;
                        oldStory.score = story.score;
                        oldStory.time = story.time;
                        oldStory.url = story.url;
                    }
                    break;
                }
            }
            syncComposeState();
        };
        StoryUpdate.setStoryUpdatedListener(storyUpdateListener);
        restoreLinkSummaryAfterRecreation();
    }

    @NonNull
    private StoryCacheController createStoryCacheController() {
        return new StoryCacheController(new StoryCacheController.Callbacks() {
            @Nullable
            @Override
            public Context getContext() {
                return StoriesFragment.this.getContext();
            }

            @Nullable
            @Override
            public RequestQueue getRequestQueue() {
                return queue;
            }

            @NonNull
            @Override
            public Object getRequestTag() {
                return requestTag;
            }

            @Override
            public void onCacheProgressChanged() {
                StoriesFragment.this.syncComposeState();
            }
        });
    }
    private void initializeComposeUi() {
        if (composeController != null) {
            return;
        }

        composeController = StoriesComposeController.create(
                this,
                new StoriesComposeController.Listener() {
                    @Override
                    public void onTypeSelected(int index) {
                        useMainStoryList();
                        if (index < 0 || index >= buildTypeAdapterList(requireContext()).size()
                                || index == adapter.type) {
                            return;
                        }
                        adapter.type = index;
                        updateAdapterCommentRows();
                        updateAdapterPaginationMode(adapter);
                        attemptStoryTypeRefresh();
                    }

                    @Override
                    public void onOpenSearch() {
                        openSearch();
                    }

                    @Override
                    public void onCloseSearch() {
                        closeSearch();
                    }

                    @Override
                    public void onSearch(@NonNull String query) {
                        search(query);
                    }

                    @Override
                    public void onSearchOption(int kind, int index) {
                        if (kind == StoriesComposeController.SEARCH_OPTION_SORT) {
                            searchController.setSortIndex(index);
                        } else if (kind == StoriesComposeController.SEARCH_OPTION_DATE) {
                            searchController.setDateRangeIndex(index);
                        } else if (kind == StoriesComposeController.SEARCH_OPTION_POINTS) {
                            searchController.setMinimumPointsIndex(index);
                        } else if (kind == StoriesComposeController.SEARCH_OPTION_COMMENTS) {
                            searchController.setMinimumCommentsIndex(index);
                        }
                        updateSearchOptionChips();
                        retrySearchWithCurrentOptions();
                    }

                    @Override
                    public void onToggleOnlyClicked() {
                        searchController.toggleOnlyClicked();
                        updateSearchOptionChips();
                        retrySearchWithCurrentOptions();
                    }

                    @Override
                    public void onRefresh() {
                        attemptRefresh();
                    }

                    @Override
                    public void onShowCached() {
                        showCachedStories();
                    }

                    @Override
                    public void onLoadMore() {
                        handleLoadMore(adapter);
                    }

                    @Override
                    public void onSavedFilterSelected(int filter) {
                        if (filter == userItemListFilter) {
                            return;
                        }
                        userItemListFilter = filter;
                        if (currentTypeUsesSavedItemFilter()) {
                            applySavedItemFilter(true);
                        }
                    }

                    @Override
                    public void onShiftFrontDate(int days) {
                        shiftFrontPageDay(days);
                    }

                    @Override
                    public void onPickFrontDate() {
                        showFrontPageDatePicker();
                    }

                    @Override
                    public void onFrontDateSelected(long day) {
                        selectFrontPageDay(day);
                    }

                    @Override
                    public void onMoreAction(int action) {
                        handleComposeMoreAction(action);
                    }

                    @Override
                    public void onCacheStoriesConfirmed(int storyCount) {
                        SettingsUtils.setStoriesToCache(requireContext(), storyCount);
                        if (storyCacheController != null) {
                            storyCacheController.cacheStories();
                        }
                    }

                    @Override
                    public void onLinkClick(@NonNull Story story) {
                        handleStoryLinkClick(adapter, stories.indexOf(story));
                    }

                    @Override
                    public void onCommentClick(@NonNull Story story) {
                        int position = stories.indexOf(story);
                        if (position >= 0) {
                            clickedComments(position);
                        }
                    }

                    @Override
                    public void onCommentStoryClick(@NonNull Story story) {
                        int position = stories.indexOf(story);
                        if (position >= 0) {
                            clickedCommentStory(position);
                        }
                    }

                    @Override
                    public void onCommentRepliesClick(@NonNull Story story) {
                        int position = stories.indexOf(story);
                        if (position >= 0) {
                            clickedComments(position);
                        }
                    }

                    @Override
                    public void onStoryLongClick(@NonNull Story story) {
                        int position = stories.indexOf(story);
                        if (position < 0) {
                            return;
                        }
                        showComposeStoryPreview(story);
                    }

                    @Override
                    public void onVisibleStoryRange(int lastVisibleIndex) {
                        loadComposeVisibleStories(lastVisibleIndex);
                    }

                    @Override
                    public void onStoryPreviewStopScroll() {
                        // Compose owns list scrolling and stops it before invoking this callback.
                    }

                    @Override
                    public void onStoryPreviewVisibilityChanged(boolean showing) {
                        if (linkSummaryBackCallback != null) {
                            linkSummaryBackCallback.setEnabled(showing);
                        }
                    }

                    @Override
                    public boolean onStoryPreviewNavigate(
                            @NonNull Story story, int position, boolean showWebsite) {
                        openStoryFromLinkSummary(story, position, showWebsite);
                        return isFoldableSplitLayout();
                    }

                    @Override
                    public void onStoryPreviewAction(
                            @NonNull Story story, int position, int action) {
                        StoriesComposeController controller = composeController;
                        if (controller == null) return;
                        if (action == StoriesComposeController.STORY_PREVIEW_ACTION_VOTE) {
                            boolean selected = Utils.isUpvoted(requireContext(), story.id, false);
                            toggleStoryVote(story, position, selected,
                                    () -> controller.finishStoryPreviewAction(story.id, action));
                        } else if (action == StoriesComposeController.STORY_PREVIEW_ACTION_READ) {
                            toggleStoryRead(story, position);
                        } else if (action == StoriesComposeController.STORY_PREVIEW_ACTION_BOOKMARK) {
                            toggleStoryBookmark(story, position,
                                    Utils.isBookmarked(requireContext(), story.id));
                        } else if (action == StoriesComposeController.STORY_PREVIEW_ACTION_FAVORITE) {
                            boolean selected = Utils.isFavorited(requireContext(), story.id);
                            toggleStoryFavorite(story, position, selected,
                                    () -> controller.finishStoryPreviewAction(story.id, action));
                        }
                    }
                });
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).attachStoriesComposeController(composeController);
        }
        syncComposeState();
    }

    private void showComposeStoryPreview(@NonNull Story openedStory) {
        StoriesComposeController controller = composeController;
        if (controller == null || stories == null || adapter == null) return;
        int visibleCount = Math.min(stories.size(), adapter.getVisibleStoryItemCount());
        ArrayList<Story> previewStories = new ArrayList<>();
        ArrayList<Integer> sourcePositions = new ArrayList<>();
        ArrayList<Integer> cardColors = new ArrayList<>();
        Context context = requireContext();
        for (int position = 0; position < visibleCount; position++) {
            Story story = stories.get(position);
            if (story == null || story.isComment || !story.loaded
                    || (story.isLink && TextUtils.isEmpty(story.url))) {
                continue;
            }
            previewStories.add(story);
            sourcePositions.add(position);
            cardColors.add(adapter.resolveStoryCardBackgroundColor(context, story));
        }
        boolean containsOpenedStory = false;
        for (Story story : previewStories) {
            if (story.id == openedStory.id) {
                containsOpenedStory = true;
                break;
            }
        }
        if (!containsOpenedStory) {
            return;
        }
        int[] positionArray = new int[sourcePositions.size()];
        int[] colorArray = new int[cardColors.size()];
        for (int index = 0; index < sourcePositions.size(); index++) {
            positionArray[index] = sourcePositions.get(index);
            colorArray[index] = cardColors.get(index);
        }
        controller.showStoryPreview(previewStories, positionArray, colorArray, openedStory.id);
    }

    private void loadComposeVisibleStories(int lastVisibleIndex) {
        if (composeController == null || stories == null || stories.isEmpty()) {
            return;
        }
        int targetIndex = Math.min(
                stories.size() - 1,
                Math.max(getInitialLoadCount() - 1,
                        lastVisibleIndex + STORY_VISIBLE_PREFETCH_THRESHOLD));
        loadStoriesThroughIndex(targetIndex, storyListGeneration);
        retryUnsettledStoriesThroughIndex(targetIndex, storyListGeneration);
        Context context = getContext();
        if (context != null) {
            int prefetchStart = Math.max(0, lastVisibleIndex - 2);
            for (int i = prefetchStart; i <= targetIndex; i++) {
                Story story = stories.get(i);
                if (story != null && story.loaded) {
                    requestPreviewImagePrefetch(context, story);
                }
            }
        }
    }

    private void handleComposeMoreAction(int action) {
        if (action == StoriesComposeController.MORE_SETTINGS) {
            requireActivity().startActivity(SettingsIntents.create(requireActivity()));
        } else if (action == StoriesComposeController.MORE_LOGIN) {
            if (TextUtils.isEmpty(AccountUtils.getAccountUsername(requireActivity()))) {
                AccountUtils.showLoginPrompt(requireContext());
            } else {
                AccountUtils.deleteAccountDetails(requireActivity());
                refreshTypeSpinnerItemsIfNeeded();
                Toast.makeText(getContext(), "Logged out", Toast.LENGTH_SHORT).show();
            }
        } else if (action == StoriesComposeController.MORE_PROFILE) {
            ((MainActivity) requireActivity()).showUserDialog(
                    AccountUtils.getAccountUsername(requireActivity()), null);
        } else if (action == StoriesComposeController.MORE_CACHE) {
            showCacheStoriesDialog();
        } else if (action == StoriesComposeController.MORE_SUBMIT) {
            Intent submitIntent = ComposeEditorContract.createIntent(requireContext());
            submitIntent.putExtra(
                    ComposeEditorContract.EXTRA_TYPE,
                    ComposeEditorContract.TYPE_POST);
            startActivity(submitIntent);
        } else if (action == StoriesComposeController.MORE_CLEAR_HISTORY) {
            HistoriesUtils.INSTANCE.clearHistories(requireContext());
            loadingFailed = false;
            loadingFailedServerError = false;
            loadingFailedRateLimited = false;
            clearStories();
            updateHeader();
        }
        syncComposeState();
    }

    private void showCacheStoriesDialog() {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).showCacheStoriesDialog();
        }
    }

    private void syncComposeState() {
        StoriesComposeController controller = composeController;
        if (controller == null || mainAdapter == null || searchAdapter == null
                || adapter == null || stories == null || getContext() == null) {
            return;
        }
        Context context = requireContext();
        ArrayList<CharSequence> labels = buildTypeAdapterList(context);
        ArrayList<String> stringLabels = new ArrayList<>(labels.size());
        for (CharSequence label : labels) {
            stringLabels.add(label == null ? "" : label.toString());
        }

        boolean bookmarksType = isBookmarksType(adapter.type);
        boolean historyType = isHistoryType(adapter.type);
        boolean favoritesType = isFavoritesType(adapter.type);
        boolean upvotedType = isUpvotedType(adapter.type);
        boolean userItemListType = favoritesType || upvotedType;
        boolean savedItemSourceHasItems = currentSavedItemSourceHasItems();
        boolean hasSubmittedSearch = !TextUtils.isEmpty(lastSearch.trim());
        boolean showEmptySearch = searching
                && hasSubmittedSearch
                && stories.isEmpty()
                && !algoliaLoading
                && !loadingFailed
                && !loadingFailedServerError;
        boolean showEmptySaved = !searching
                && stories.isEmpty()
                && !loadingFailed
                && !loadingFailedServerError
                && (bookmarksType
                || historyType
                || (userItemListType && !userItemListInitialLoadInProgress
                && !isRefreshIndicatorShowing()));
        boolean showLoading = searching
                ? algoliaLoading
                : stories.isEmpty()
                && !loadingFailed
                && !loadingFailedServerError
                && !bookmarksType
                && !historyType
                && (!userItemListType || userItemListInitialLoadInProgress);
        String loadingMessage;
        if (loadingFailedRateLimited) {
            loadingMessage = "Rate limited";
        } else if (!Utils.isNetworkAvailable(context)) {
            loadingMessage = "No internet connection";
        } else {
            loadingMessage = "Loading failed";
        }
        String lastUpdated = shouldShowLastUpdatedHeader()
                ? "Last updated: " + android.text.format.DateFormat.getTimeFormat(context)
                .format(new java.util.Date(lastLoaded))
                : null;
        boolean cacheInProgress = storyCacheController != null
                && storyCacheController.isCachingStories();
        boolean cacheProgressVisible = storyCacheController != null
                && storyCacheController.isProgressVisible();
        int cacheProgress = storyCacheController == null ? 0 : storyCacheController.getProgress();
        int cacheProgressMax = storyCacheController == null ? 1 : storyCacheController.getProgressMax();
        String cacheProgressStatus = storyCacheController == null
                ? "Caching stories" : storyCacheController.getProgressStatus();
        boolean hasVisibleStories = adapter.getVisibleStoryItemCount() > 0;
        boolean canCache = hasVisibleStories
                && !showingCached
                && !cacheInProgress
                && Utils.isNetworkAvailable(context);

        controller.updateContent(
                mainStories,
                searchStories,
                StoryDisplaySettings.from(context),
                stringLabels,
                mainAdapter.type,
                searching,
                lastSearch,
                searchController.getSortLabel(),
                searchController.getDateRangeLabel(),
                searchController.getMinimumPointsLabel(),
                searchController.getMinimumCommentsLabel(),
                searchController.getSortLabels(),
                searchController.getDateRangeLabels(),
                searchController.getMinimumPointsLabels(),
                searchController.getMinimumCommentsLabels(),
                searchController.isOnlyClicked(),
                showLoading,
                isRefreshIndicatorShowing(),
                loadingFailed,
                loadingFailedServerError,
                loadingMessage,
                showingCached,
                loadingFailed && !searching && Utils.hasCachedStories(context),
                showEmptySaved,
                getEmptySavedListText(
                        historyType, favoritesType, upvotedType, savedItemSourceHasItems),
                showEmptySearch,
                updateButtonShowing,
                lastUpdated,
                adapter.hasLoadMoreButton(),
                adapter.isLoadMoreLoading(),
                mainAdapter.getVisibleStoryItemCount(),
                searchAdapter.getVisibleStoryItemCount(),
                !searching && currentTypeUsesSavedItemFilter() && savedItemSourceHasItems,
                userItemListFilter,
                !searching && currentTypeIsFront(),
                getFrontPageDayParameter(),
                getFrontPageDayUtc().after(getEarliestFrontPageDayUtc()),
                getFrontPageDayUtc().before(getLatestFrontPageDayUtc()),
                !TextUtils.isEmpty(AccountUtils.getAccountUsername(requireActivity())),
                canCache,
                isHistoryType(adapter.type) && HistoriesUtils.INSTANCE.size() > 0,
                cacheProgressVisible,
                cacheProgress,
                cacheProgressMax,
                cacheProgressStatus,
                getSplitStoriesContentPaddingStart());
    }

    private void restoreLinkSummaryAfterRecreation() {
        if (pendingLinkSummaryStoryId == NO_PENDING_LINK_SUMMARY_STORY_ID) {
            return;
        }
        int storyId = pendingLinkSummaryStoryId;
        pendingLinkSummaryStoryId = NO_PENDING_LINK_SUMMARY_STORY_ID;
        previewImagePrefetchHandler.post(() -> {
            if (composeController == null || stories == null) {
                return;
            }
            for (int position = 0; position < stories.size(); position++) {
                Story story = stories.get(position);
                if (story.id == storyId
                        && (!story.isLink || !TextUtils.isEmpty(story.url))) {
                    showComposeStoryPreview(story);
                    return;
                }
            }
        });
    }

    private void restoreStoryLists(@Nullable StoriesViewModel.State state) {
        if (state == null) {
            return;
        }

        mainStories.clear();
        searchStories.clear();
        bookmarkStories.clear();
        userItemListStories.clear();
        mainStories.addAll(state.mainStories);
        searchStories.addAll(state.searchStories);
        bookmarkStories.addAll(state.bookmarkStories);
        userItemListStories.addAll(state.userItemListStories);
        userItemListCommentIds = new HashSet<>(state.userItemListCommentIds);
    }

    private boolean restoreStoryStateAfterViewSetup(@Nullable StoriesViewModel.State state) {
        if (state == null || mainAdapter == null || searchAdapter == null) {
            return false;
        }

        int restoredMainType = getTypeIndex(state.mainTypeLabel);
        if (restoredMainType >= 0) {
            mainAdapter.type = restoredMainType;
        }
        int restoredSearchType = getTypeIndex(state.searchTypeLabel);
        searchAdapter.type = restoredSearchType >= 0 ? restoredSearchType : mainAdapter.type;

        mainAdapter.visibleStoryCount = state.mainVisibleStoryCount;
        searchAdapter.visibleStoryCount = state.searchVisibleStoryCount;
        mainAdapter.showLoadMoreButton = state.mainShowLoadMoreButton;
        searchAdapter.showLoadMoreButton = state.searchShowLoadMoreButton;
        updateAdapterCommentRows();

        searching = state.searching;
        lastSearch = state.lastSearch;
        lastLoaded = state.lastLoaded;
        updateButtonShowing = state.updateButtonShowing;
        userItemListFilter = state.userItemListFilter;
        if (frontPageDayUtc == null && state.frontPageDayUtcMillis >= 0L) {
            frontPageDayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            frontPageDayUtc.setTimeInMillis(state.frontPageDayUtcMillis);
            clearTime(frontPageDayUtc);
        }
        scrapedFrontpageNextPageUrl = state.scrapedFrontpageNextPageUrl;
        scrapedFrontpageNextPageLoading = false;
        scrapedFrontpageStoryType = currentTypeIsScrapedFrontpage()
                ? getCurrentStoryType()
                : StoryType.UNKNOWN;

        searchController.setSortIndex(state.searchSortIndex);
        searchController.setDateRangeIndex(state.searchDateRangeIndex);
        searchController.setMinimumPointsIndex(state.searchMinimumPointsIndex);
        searchController.setMinimumCommentsIndex(state.searchMinimumCommentsIndex);
        if (searchController.isOnlyClicked() != state.searchOnlyClicked) {
            searchController.toggleOnlyClicked();
        }

        if (searching) {
            storiesBeforeSearch = new ArrayList<>(mainStories);
            loadedToBeforeSearch = state.mainLoadedTo;
            visibleStoryCountBeforeSearch = state.mainVisibleStoryCount;
            showingCachedBeforeSearch = state.mainShowingCached;
            loadingFailedBeforeSearch = state.mainLoadingFailed;
            loadingFailedServerErrorBeforeSearch = state.mainLoadingFailedServerError;
            loadingFailedRateLimitedBeforeSearch = state.mainLoadingFailedRateLimited;
            showLoadMoreBeforeSearch = state.mainShowLoadMoreButton;
            algoliaHitsPerPageBeforeSearch = state.mainAlgoliaHitsPerPage;
            lastAlgoliaTopStoriesStartTimeBeforeSearch = state.mainLastAlgoliaTopStoriesStartTime;
            loadPendingBeforeSearch = mainStories.isEmpty()
                    && !state.mainLoadingFailed
                    && !state.mainLoadingFailedServerError
                    && !isBookmarksType(mainAdapter.type)
                    && !isUserItemListType(mainAdapter.type);

            loadedTo = state.searchLoadedTo;
            showingCached = state.searchShowingCached;
            loadingFailed = state.searchLoadingFailed;
            loadingFailedServerError = state.searchLoadingFailedServerError;
            loadingFailedRateLimited = state.searchLoadingFailedRateLimited;
            algoliaHitsPerPage = state.searchAlgoliaHitsPerPage;
            lastAlgoliaTopStoriesStartTime = state.searchLastAlgoliaTopStoriesStartTime;
        } else {
            loadedTo = state.mainLoadedTo;
            showingCached = state.mainShowingCached;
            loadingFailed = state.mainLoadingFailed;
            loadingFailedServerError = state.mainLoadingFailedServerError;
            loadingFailedRateLimited = state.mainLoadingFailedRateLimited;
            algoliaHitsPerPage = state.mainAlgoliaHitsPerPage;
            lastAlgoliaTopStoriesStartTime = state.mainLastAlgoliaTopStoriesStartTime;
        }

        syncActiveStoryListToSearchState();
        updateSearchOptionChips(false);
        return true;
    }

    private boolean shouldRefreshRestoredStoryState() {
        if (loadingFailed || loadingFailedServerError || !stories.isEmpty()) {
            return false;
        }
        if (searching) {
            return !TextUtils.isEmpty(lastSearch);
        }
        return !isBookmarksType(adapter.type)
                && !isHistoryType(adapter.type)
                && !isUserItemListType(adapter.type);
    }

    private void saveStoryStateForRecreation() {
        if (storiesViewModel == null || mainAdapter == null || searchAdapter == null) {
            return;
        }

        StoriesViewModel.State state = new StoriesViewModel.State();
        state.mainStories.addAll(mainStories);
        state.searchStories.addAll(searchStories);
        state.bookmarkStories.addAll(bookmarkStories);
        state.userItemListStories.addAll(userItemListStories);
        state.userItemListCommentIds.addAll(userItemListCommentIds);
        CharSequence mainTypeLabel = getTypeLabel(mainAdapter.type);
        CharSequence searchTypeLabel = getTypeLabel(searchAdapter.type);
        state.mainTypeLabel = mainTypeLabel == null ? null : mainTypeLabel.toString();
        state.searchTypeLabel = searchTypeLabel == null ? null : searchTypeLabel.toString();
        state.mainVisibleStoryCount = mainAdapter.visibleStoryCount;
        state.searchVisibleStoryCount = searchAdapter.visibleStoryCount;
        state.mainShowLoadMoreButton = mainAdapter.showLoadMoreButton;
        state.searchShowLoadMoreButton = searchAdapter.showLoadMoreButton;
        state.searching = searching;
        state.lastSearch = lastSearch;
        state.lastLoaded = lastLoaded;
        state.updateButtonShowing = updateButtonShowing;
        state.userItemListFilter = userItemListFilter;
        state.frontPageDayUtcMillis = frontPageDayUtc == null ? -1L : frontPageDayUtc.getTimeInMillis();
        state.scrapedFrontpageNextPageUrl = scrapedFrontpageNextPageUrl;

        state.searchSortIndex = searchController.getSortIndex();
        state.searchDateRangeIndex = searchController.getDateRangeIndex();
        state.searchMinimumPointsIndex = searchController.getMinimumPointsIndex();
        state.searchMinimumCommentsIndex = searchController.getMinimumCommentsIndex();
        state.searchOnlyClicked = searchController.isOnlyClicked();

        // LazyListState is owned and saved by Compose. The controller state only persists data.
        state.mainFirstVisiblePosition = -1;
        state.mainFirstVisibleTop = 0;
        state.searchFirstVisiblePosition = -1;
        state.searchFirstVisibleTop = 0;
        state.appBarCollapsed = false;

        if (searching) {
            state.mainLoadedTo = loadedToBeforeSearch;
            state.mainShowingCached = showingCachedBeforeSearch;
            state.mainLoadingFailed = loadingFailedBeforeSearch;
            state.mainLoadingFailedServerError = loadingFailedServerErrorBeforeSearch;
            state.mainLoadingFailedRateLimited = loadingFailedRateLimitedBeforeSearch;
            state.mainAlgoliaHitsPerPage = algoliaHitsPerPageBeforeSearch;
            state.mainLastAlgoliaTopStoriesStartTime = lastAlgoliaTopStoriesStartTimeBeforeSearch;

            state.searchLoadedTo = loadedTo;
            state.searchShowingCached = showingCached;
            state.searchLoadingFailed = loadingFailed;
            state.searchLoadingFailedServerError = loadingFailedServerError;
            state.searchLoadingFailedRateLimited = loadingFailedRateLimited;
            state.searchAlgoliaHitsPerPage = algoliaHitsPerPage;
            state.searchLastAlgoliaTopStoriesStartTime = lastAlgoliaTopStoriesStartTime;
        } else {
            state.mainLoadedTo = loadedTo;
            state.mainShowingCached = showingCached;
            state.mainLoadingFailed = loadingFailed;
            state.mainLoadingFailedServerError = loadingFailedServerError;
            state.mainLoadingFailedRateLimited = loadingFailedRateLimited;
            state.mainAlgoliaHitsPerPage = algoliaHitsPerPage;
            state.mainLastAlgoliaTopStoriesStartTime = lastAlgoliaTopStoriesStartTime;
        }

        storiesViewModel.setState(state);
    }

    private int getPreferredTypeIndex() {
        ArrayList<CharSequence> typeAdapterList = buildTypeAdapterList(getContext());
        int preferredIndex = typeAdapterList.indexOf(SettingsUtils.getPreferredStoryType(getContext()));
        return preferredIndex >= 0 ? preferredIndex : 0;
    }

    private ArrayList<CharSequence> buildTypeAdapterList(Context ctx) {
        return StoryType.buildAdapterLabels(getResources(), ctx, shouldShowUserItemLists(ctx));
    }

    private boolean shouldShowUserItemLists(@Nullable Context ctx) {
        return ctx != null && AccountUtils.hasAccountDetails(ctx);
    }

    private void refreshTypeSpinnerItemsIfNeeded() {
        if (adapter == null || getContext() == null) {
            return;
        }

        CharSequence previousTypeLabel = getTypeLabel(adapter.type);
        boolean showUserItemLists = shouldShowUserItemLists(getContext());
        if (userItemListsDropdownVisible == showUserItemLists) {
            return;
        }

        userItemListsDropdownVisible = showUserItemLists;
        int newType = getTypeIndex(previousTypeLabel);
        if (newType < 0) {
            newType = 0;
        }

        CharSequence newTypeLabel = getTypeLabel(newType);
        boolean typeChanged = !TextUtils.equals(previousTypeLabel, newTypeLabel);
        if (adapter.type != newType || typeChanged) {
            adapter.type = newType;
            updateAdapterCommentRows();
            updateAdapterPaginationMode(adapter);
        }

        if (typeChanged) {
            attemptStoryTypeRefresh();
        } else {
            syncComposeState();
        }
    }

    private void enqueueStoryRowChange(@NonNull Story story, int loadGeneration) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return;
        }

        // Story requests frequently finish during a fling. Keep the model update, but coalesce
        // the adapter notifications until holders are no longer rapidly moving through scrap.
        pendingStoryRowChangeGenerations.put(story.id, loadGeneration);
        postPendingStoryAdapterUpdateIfNotSettling();
    }

    private void enqueueStoryRemoval(
            @NonNull Story story,
            int loadGeneration,
            boolean updateHeader) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return;
        }

        PendingStoryRemoval pendingRemoval = pendingStoryRemovals.get(story.id);
        if (pendingRemoval == null || pendingRemoval.generation != loadGeneration) {
            pendingStoryRemovals.put(
                    story.id,
                    new PendingStoryRemoval(loadGeneration, updateHeader));
        } else {
            pendingRemoval.updateHeader |= updateHeader;
        }
        pendingStoryRowChangeGenerations.remove(story.id);
        postPendingStoryAdapterUpdateIfNotSettling();
    }

    private void postPendingStoryAdapterUpdateIfNotSettling() {
        flushPendingStoryAdapterUpdates();
    }

    private void flushPendingStoryAdapterUpdates() {
        int currentGeneration = storyListGeneration;
        boolean shouldUpdateHeader = false;
        ArrayList<Integer> removalIds = new ArrayList<>();
        for (Map.Entry<Integer, PendingStoryRemoval> entry : pendingStoryRemovals.entrySet()) {
            PendingStoryRemoval removal = entry.getValue();
            if (removal.generation == currentGeneration) {
                removalIds.add(entry.getKey());
                shouldUpdateHeader |= removal.updateHeader;
            }
        }
        pendingStoryRemovals.clear();
        for (int storyId : removalIds) {
            int position = findStoryPositionById(storyId);
            if (position >= 0) {
                removeStoryAt(position, currentGeneration, false);
            }
        }
        pendingStoryRowChangeGenerations.clear();
        if (!removalIds.isEmpty()) {
            loadVisibleStories(currentGeneration);
        }
        if (shouldUpdateHeader) {
            updateHeader();
        } else {
            syncComposeState();
        }
    }

    private void syncActiveStoryListToSearchState() {
        if (searching) {
            useSearchStoryList();
        } else {
            useMainStoryList();
        }
    }

    private void useMainStoryList() {
        adapter = mainAdapter;
        stories = mainStories;
    }

    private void useSearchStoryList() {
        adapter = searchAdapter;
        stories = searchStories;
    }

    private void useStoryListForAdapter(@NonNull StoryListState sourceAdapter) {
        if (sourceAdapter == searchAdapter) {
            useSearchStoryList();
        } else {
            useMainStoryList();
        }
    }

    private void updateHeader() {
        updateHeader(false);
    }

    private void updateHeader(boolean animateSearchTransition) {
        syncComposeState();
    }

    private boolean shouldShowLastUpdatedHeader() {
        return updateButtonShowing && !searching && lastLoaded > 0;
    }

    private int getSplitStoriesContentPaddingStart() {
        Context ctx = getContext();
        if (ctx == null
                || !(getActivity() instanceof MainActivity)
                || ((MainActivity) getActivity()).isAdaptiveFoldableNavigation()
                || !Utils.isTablet(getResources())) {
            return 0;
        }

        return getResources().getDimensionPixelSize(R.dimen.extra_pane_padding);
    }

    private Calendar getFrontPageDayUtc() {
        if (frontPageDayUtc == null) {
            frontPageDayUtc = getLatestFrontPageDayUtc();
        }
        return frontPageDayUtc;
    }

    private Calendar getLatestFrontPageDayUtc() {
        Calendar latest = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        latest.add(Calendar.DAY_OF_MONTH, -1);
        clearTime(latest);
        return latest;
    }

    private Calendar getEarliestFrontPageDayUtc() {
        Calendar earliest = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        earliest.set(Calendar.YEAR, 2007);
        earliest.set(Calendar.MONTH, Calendar.FEBRUARY);
        earliest.set(Calendar.DAY_OF_MONTH, 19);
        clearTime(earliest);
        return earliest;
    }

    private void clearTime(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    private String getFrontPageDayParameter() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(getFrontPageDayUtc().getTime());
    }

    private void shiftFrontPageDay(int days) {
        Calendar day = (Calendar) getFrontPageDayUtc().clone();
        day.add(Calendar.DAY_OF_MONTH, days);
        Calendar latest = getLatestFrontPageDayUtc();
        if (day.after(latest)) {
            day = latest;
        }
        Calendar earliest = getEarliestFrontPageDayUtc();
        if (day.before(earliest)) {
            day = earliest;
        }
        clearTime(day);
        frontPageDayUtc = day;
        if (currentTypeIsFront()) {
            attemptStoryTypeRefresh();
        }
    }

    private void showFrontPageDatePicker() {
        if (composeController == null) {
            return;
        }
        composeController.showFrontDatePicker(
                getFrontPageDayUtc().getTimeInMillis(),
                getEarliestFrontPageDayUtc().getTimeInMillis(),
                getLatestFrontPageDayUtc().getTimeInMillis());
    }

    private void selectFrontPageDay(long selection) {
        Calendar selectedDay = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        selectedDay.setTimeInMillis(selection);
        clearTime(selectedDay);
        Calendar latest = getLatestFrontPageDayUtc();
        if (selectedDay.after(latest)) {
            selectedDay = latest;
        }
        Calendar earliest = getEarliestFrontPageDayUtc();
        if (selectedDay.before(earliest)) {
            selectedDay = earliest;
        }
        frontPageDayUtc = selectedDay;
        if (currentTypeIsFront()) {
            attemptStoryTypeRefresh();
        }
    }

    private String getEmptySavedListText(boolean historyType, boolean favoritesType, boolean upvotedType, boolean savedItemSourceHasItems) {
        if (historyType) {
            return "No history";
        }
        if (favoritesType) {
            if (!savedItemSourceHasItems) {
                return "No favorites";
            }
            if (userItemListFilter == USER_ITEM_LIST_FILTER_STORIES) {
                return "No favorite stories";
            }
            if (userItemListFilter == USER_ITEM_LIST_FILTER_COMMENTS) {
                return "No favorite comments";
            }
            return "No favorites";
        }
        if (upvotedType) {
            if (!savedItemSourceHasItems) {
                return "No upvoted items";
            }
            if (userItemListFilter == USER_ITEM_LIST_FILTER_STORIES) {
                return "No upvoted stories";
            }
            if (userItemListFilter == USER_ITEM_LIST_FILTER_COMMENTS) {
                return "No upvoted comments";
            }
            return "No upvoted items";
        }
        if (!savedItemSourceHasItems) {
            return "No bookmarks";
        }
        if (userItemListFilter == USER_ITEM_LIST_FILTER_STORIES) {
            return "No bookmarked stories";
        }
        if (userItemListFilter == USER_ITEM_LIST_FILTER_COMMENTS) {
            return "No bookmarked comments";
        }
        return "No bookmarks";
    }

    private void updateSearchOptionChips() {
        updateSearchOptionChips(true);
    }

    private void updateSearchOptionChips(boolean animate) {
        syncComposeState();
    }

    private void retrySearchWithCurrentOptions() {
        if (!searching) {
            return;
        }

        if (!TextUtils.isEmpty(lastSearch)) {
            search(lastSearch);
        }
    }

    private void openSearch() {
        finishSearchBackFromCurrentVisualState = false;
        searching = true;
        resetSearchOptions();
        updateSearchStatus();
        syncComposeState();
    }

    private void closeSearch() {
        finishSearchBackFromCurrentVisualState = false;
        predictiveSearchBackInProgress = false;
        predictiveSearchBackProgress = 0f;
        searching = false;
        lastSearch = "";
        resetSearchOptions();
        updateSearchStatus();
    }

    private void resetSearchOptions() {
        searchController.resetOptions();
        updateSearchOptionChips(false);
    }

    private void resetPaginationState() {
        loadedTo = -1;
        clearPaginationLoadMoreState();
        updateAdapterPaginationMode(adapter);
        adapter.visibleStoryCount = adapter.paginationMode ? StoryListState.PAGINATION_PAGE_SIZE : Integer.MAX_VALUE;
    }

    private boolean shouldUsePaginationForType(@Nullable StoryType storyType) {
        return paginationMode || (storyType != null && storyType.isScrapedFrontpage());
    }

    private void updateAdapterPaginationMode(@Nullable StoryListState targetAdapter) {
        if (targetAdapter == null) {
            return;
        }

        targetAdapter.paginationMode = shouldUsePaginationForType(getStoryType(targetAdapter.type));
    }

    private void resetAlgoliaResultLimit() {
        algoliaHitsPerPage = StorySearchController.ALGOLIA_HITS_INCREMENT;
        algoliaLoadMoreInProgress = false;
        if (adapter != null) {
            adapter.setLoadMoreLoading(false);
        }
        algoliaLoadMoreVisibleStoryCount = -1;
    }

    private int getInitialLoadCount() {
        return adapter != null && adapter.paginationMode ? StoryListState.PAGINATION_PAGE_SIZE : 20;
    }

    private void loadStoriesThroughIndex(int targetIndex, int loadGeneration) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return;
        }

        for (int i = loadedTo + 1; i <= targetIndex && i < stories.size(); i++) {
            loadedTo = i;
            loadStory(stories.get(i), 0, loadGeneration);
        }
    }

    private void startPaginationLoadMore(int targetIndex, int loadGeneration) {
        paginationLoadMoreStoryIds.clear();
        paginationLoadMoreGeneration = loadGeneration;
        int firstIndex = Math.max(0, loadedTo + 1);
        int lastIndex = Math.min(targetIndex, stories.size() - 1);
        for (int i = firstIndex; i <= lastIndex; i++) {
            Story story = stories.get(i);
            if (story != null && !story.loaded) {
                paginationLoadMoreStoryIds.add(story.id);
            }
        }
    }

    private void finishPaginationLoadMoreStory(Story story, int loadGeneration) {
        if (story == null || loadGeneration != paginationLoadMoreGeneration) {
            return;
        }

        paginationLoadMoreStoryIds.remove(story.id);
        if (paginationLoadMoreStoryIds.isEmpty()) {
            clearPaginationLoadMoreState();
        }
    }

    private void clearPaginationLoadMoreState() {
        paginationLoadMoreStoryIds.clear();
        paginationLoadMoreGeneration = -1;
        if (adapter != null) {
            adapter.setLoadMoreLoading(false);
        }
    }

    private void retryUnsettledStoriesThroughIndex(int targetIndex, int loadGeneration) {
        if (!isCurrentStoryListGeneration(loadGeneration) || targetIndex < 0 || stories.isEmpty()) {
            return;
        }

        int cappedTargetIndex = Math.min(targetIndex, stories.size() - 1);
        for (int i = 0; i <= cappedTargetIndex; i++) {
            Story story = stories.get(i);
            if (story != null
                    && !story.loaded
                    && !story.loadingFailed
                    && !isStoryLoadInProgress(story)) {
                loadStory(story, 0, loadGeneration);
            }
        }
    }

    private int getVisibleLoadTargetIndex() {
        if (stories.isEmpty()) {
            return -1;
        }

        int storiesToLoad = getInitialLoadCount();
        if (adapter != null && adapter.paginationMode) {
            storiesToLoad = adapter.visibleStoryCount;
        }

        return Math.min(storiesToLoad, stories.size()) - 1;
    }

    private void loadVisibleStories(int loadGeneration) {
        int targetIndex = getVisibleLoadTargetIndex();
        loadStoriesThroughIndex(targetIndex, loadGeneration);
        retryUnsettledStoriesThroughIndex(targetIndex, loadGeneration);
    }

    private void clearStories() {
        resetPreviewImagePrefetchRamp();
        int oldItemCount = adapter.getItemCount();
        stories.clear();
        resetPaginationState();
        adapter.showLoadMoreButton = false;

        if (oldItemCount > 0) {
            adapter.notifyItemRangeRemoved(0, oldItemCount);
        }
    }

    private void clearStoriesForSearchEntry() {
        clearStories();
    }

    private void replaceStories(List<Story> newStories) {
        replaceStories(newStories, false, false);
    }

    private void replaceStories(List<Story> newStories, boolean notifyDataSetChanged) {
        replaceStories(newStories, notifyDataSetChanged, false);
    }

    private void replaceStories(List<Story> newStories, boolean notifyDataSetChanged, boolean showLoadMoreButton) {
        resetPreviewImagePrefetchRamp();
        if (notifyDataSetChanged) {
            stories.clear();
            resetPaginationState();
            adapter.showLoadMoreButton = showLoadMoreButton;
            stories.addAll(newStories);
            adapter.notifyDataSetChanged();
            return;
        }

        clearStories();
        adapter.showLoadMoreButton = showLoadMoreButton;
        stories.addAll(newStories);

        int newItemCount = adapter.getItemCount();
        if (newItemCount > 0) {
            adapter.notifyItemRangeInserted(0, newItemCount);
        }
    }

    private void replaceAlgoliaLoadMoreStories(List<Story> newStories, boolean showLoadMoreButton) {
        resetPreviewImagePrefetchRamp();
        stories.clear();
        stories.addAll(newStories);
        adapter.showLoadMoreButton = showLoadMoreButton;

        if (adapter != null && adapter.paginationMode) {
            int requestedVisibleCount = algoliaLoadMoreVisibleStoryCount > 0
                    ? algoliaLoadMoreVisibleStoryCount
                    : adapter.visibleStoryCount;
            adapter.visibleStoryCount = Math.min(Math.max(requestedVisibleCount, StoryListState.PAGINATION_PAGE_SIZE), stories.size());
        } else {
            adapter.visibleStoryCount = Integer.MAX_VALUE;
        }

        adapter.notifyDataSetChanged();
    }

    private void saveStoriesBeforeSearch() {
        if (storiesBeforeSearch != null) {
            return;
        }

        storiesBeforeSearch = new ArrayList<>(stories);
        loadedToBeforeSearch = loadedTo;
        visibleStoryCountBeforeSearch = adapter.visibleStoryCount;
        showingCachedBeforeSearch = showingCached;
        loadingFailedBeforeSearch = loadingFailed;
        loadingFailedServerErrorBeforeSearch = loadingFailedServerError;
        loadingFailedRateLimitedBeforeSearch = loadingFailedRateLimited;
        showLoadMoreBeforeSearch = adapter.showLoadMoreButton;
        algoliaHitsPerPageBeforeSearch = algoliaHitsPerPage;
        lastAlgoliaTopStoriesStartTimeBeforeSearch = lastAlgoliaTopStoriesStartTime;
        loadPendingBeforeSearch = stories.isEmpty()
                && !loadingFailed
                && !loadingFailedServerError
                && !isBookmarksType(adapter.type)
                && !isUserItemListType(adapter.type);
    }

    private boolean restoreStoriesBeforeSearch() {
        if (storiesBeforeSearch == null) {
            return false;
        }

        // Search owns a separate list, so the main list never needs to be cleared and
        // reinserted. Restoring only its controller metadata keeps predictive back stable and
        // lets the already-preserved LazyListState remain on screen throughout the transition.
        restoreStoryStateBeforeSearch();
        clearStoriesBeforeSearchSnapshot();
        return true;
    }

    private void restoreStoryStateBeforeSearch() {
        loadedTo = loadedToBeforeSearch;
        adapter.visibleStoryCount = visibleStoryCountBeforeSearch;
        showingCached = showingCachedBeforeSearch;
        loadingFailed = loadingFailedBeforeSearch;
        loadingFailedServerError = loadingFailedServerErrorBeforeSearch;
        loadingFailedRateLimited = loadingFailedRateLimitedBeforeSearch;
        adapter.showLoadMoreButton = showLoadMoreBeforeSearch;
        algoliaHitsPerPage = algoliaHitsPerPageBeforeSearch;
        lastAlgoliaTopStoriesStartTime = lastAlgoliaTopStoriesStartTimeBeforeSearch;
    }

    private void clearStoriesBeforeSearchSnapshot() {
        storiesBeforeSearch = null;
        loadedToBeforeSearch = -1;
        visibleStoryCountBeforeSearch = Integer.MAX_VALUE;
        showingCachedBeforeSearch = false;
        loadingFailedBeforeSearch = false;
        loadingFailedServerErrorBeforeSearch = false;
        loadingFailedRateLimitedBeforeSearch = false;
        showLoadMoreBeforeSearch = false;
        algoliaHitsPerPageBeforeSearch = StorySearchController.ALGOLIA_HITS_INCREMENT;
        lastAlgoliaTopStoriesStartTimeBeforeSearch = 0;
    }

    private void resumeInterruptedStoryLoads() {
        if (currentTypeIsAlgolia() || stories.isEmpty() || loadedTo < 0) {
            return;
        }

        int lastIndexToLoad = Math.min(loadedTo, stories.size() - 1);
        for (int i = 0; i <= lastIndexToLoad; i++) {
            Story story = stories.get(i);
            if (!story.loaded && !story.loadingFailed) {
                loadStory(story, 0);
            }
        }
    }

    private void setupAdapter() {
        paginationMode = SettingsUtils.shouldUsePaginationMode(getContext());

        mainAdapter = createStoryAdapter(mainStories);
        searchAdapter = createStoryAdapter(searchStories);
        adapter = mainAdapter;
        stories = mainStories;

        configureStoryAdapter(mainAdapter);
        configureStoryAdapter(searchAdapter);
        updateAdapterCommentRows();
    }

    private void rebuildStoryAdapters() {
        int previousMainType = mainAdapter != null ? mainAdapter.type : getPreferredTypeIndex();
        int previousSearchType = searchAdapter != null ? searchAdapter.type : previousMainType;
        int previousMainVisibleStoryCount = mainAdapter != null
                ? mainAdapter.visibleStoryCount
                : (paginationMode ? StoryListState.PAGINATION_PAGE_SIZE : Integer.MAX_VALUE);
        int previousSearchVisibleStoryCount = searchAdapter != null
                ? searchAdapter.visibleStoryCount
                : (paginationMode ? StoryListState.PAGINATION_PAGE_SIZE : Integer.MAX_VALUE);
        boolean previousMainShowLoadMoreButton = mainAdapter != null && mainAdapter.showLoadMoreButton;
        boolean previousSearchShowLoadMoreButton = searchAdapter != null && searchAdapter.showLoadMoreButton;

        if (mainAdapter != null) mainAdapter.dispose();
        if (searchAdapter != null) searchAdapter.dispose();

        setupAdapter();

        mainAdapter.type = previousMainType;
        searchAdapter.type = previousSearchType;
        updateAdapterPaginationMode(mainAdapter);
        updateAdapterPaginationMode(searchAdapter);
        mainAdapter.visibleStoryCount = previousMainVisibleStoryCount;
        searchAdapter.visibleStoryCount = previousSearchVisibleStoryCount;
        mainAdapter.showLoadMoreButton = previousMainShowLoadMoreButton;
        searchAdapter.showLoadMoreButton = previousSearchShowLoadMoreButton;
        syncActiveStoryListToSearchState();
        updateAdapterCommentRows();

        registerStoryAdapterDataObservers();
        syncComposeState();
    }

    private void syncInactiveStoryAdapterDisplaySettings() {
        if (mainAdapter == null || searchAdapter == null || adapter == null) {
            return;
        }

        StoryListState inactiveAdapter = adapter == mainAdapter ? searchAdapter : mainAdapter;
        copyStoryAdapterDisplaySettings(adapter, inactiveAdapter);
        updateAdapterCommentRows(inactiveAdapter);
        if (inactiveAdapter.getItemCount() > 0) {
            inactiveAdapter.notifyItemRangeChanged(0, inactiveAdapter.getItemCount());
        }
    }

    private void copyStoryAdapterDisplaySettings(@NonNull StoryListState sourceAdapter,
                                                 @NonNull StoryListState targetAdapter) {
        StoryDisplaySettings.copyStateSettings(sourceAdapter, targetAdapter);
    }

    private StoryListState createStoryAdapter(List<Story> adapterStories) {
        return StoryDisplaySettings.from(requireContext()).createListState(adapterStories, getPreferredTypeIndex());
    }

    private void configureStoryAdapter(@NonNull StoryListState configuredAdapter) {
        updateAdapterPaginationMode(configuredAdapter);
        configuredAdapter.visibleStoryCount = configuredAdapter.paginationMode ? StoryListState.PAGINATION_PAGE_SIZE : Integer.MAX_VALUE;
        configuredAdapter.setChangedListener(this::onStoryListStateChanged);
    }

    private void handleStoryLinkClick(
            @NonNull StoryListState sourceAdapter,
            int position) {
        useStoryListForAdapter(sourceAdapter);
        if (position == NO_POSITION || position < 0 || position >= stories.size()) {
            return;
        }

        Story story = stories.get(position);
        if (alwaysOpenComments && !story.isFrontpageLink) {
            clickedComments(position);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastClick > CLICK_INTERVAL) {
            lastClick = now;
        } else {
            return;
        }

        if (story.loaded) {
            if (story.isFrontpageLink) {
                story.clicked = true;
                adapter.updateStoryClickedState(position);
                Utils.launchCustomTab(getContext(), story.url);
                return;
            }

            markStoryClicked(story);
            adapter.updateStoryClickedState(position);

            if (story.isLink) {
                if (SettingsUtils.shouldUseIntegratedWebView(getContext())) {
                    openComments(story, position, true);
                } else {
                    Utils.launchCustomTab(getContext(), story.url);
                }
            } else {
                openComments(story, position, false);
            }
        } else if (story.loadingFailed) {
            story.loadingFailed = false;
            loadStory(story, 0);
            adapter.notifyItemChanged(position);
        }
    }

    private void handleLoadMore(@Nullable StoryListState sourceAdapter) {
        if (sourceAdapter == null) {
            return;
        }
        useStoryListForAdapter(sourceAdapter);
        if (adapter.paginationMode && adapter.visibleStoryCount < stories.size()) {
            int newLoadedTo = Math.min(
                    loadedTo + StoryListState.PAGINATION_PAGE_SIZE,
                    stories.size() - 1);
            startPaginationLoadMore(newLoadedTo, storyListGeneration);
            adapter.setLoadMoreLoading(true);
            adapter.loadNextPage();
            if (paginationLoadMoreStoryIds.isEmpty()) {
                clearPaginationLoadMoreState();
            }
            loadStoriesThroughIndex(newLoadedTo, storyListGeneration);
            retryUnsettledStoriesThroughIndex(newLoadedTo, storyListGeneration);
        } else if (adapter.showLoadMoreButton && currentTypeIsScrapedFrontpage()) {
            loadMoreScrapedFrontpageStories(storyListGeneration);
        } else if (adapter.showLoadMoreButton) {
            loadMoreAlgoliaResults();
        }
        syncComposeState();
    }

    private void setupLinkSummaryBackCallback() {
        if (linkSummaryBackCallback != null) {
            return;
        }
        linkSummaryBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackCancelled() {
                if (composeController != null && composeController.isStoryPreviewShowing()) {
                    composeController.cancelStoryPreviewPredictiveBack();
                }
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                if (composeController != null && composeController.isStoryPreviewShowing()) {
                    composeController.updateStoryPreviewPredictiveBack(
                            backEvent.getProgress(), backEvent.getSwipeEdge(), backEvent.getTouchY());
                }
            }

            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                if (composeController != null && composeController.isStoryPreviewShowing()) {
                    composeController.startStoryPreviewPredictiveBack(
                            backEvent.getProgress(), backEvent.getSwipeEdge(), backEvent.getTouchY());
                }
            }

            @Override
            public void handleOnBackPressed() {
                if (composeController == null || !composeController.isStoryPreviewShowing()) {
                    return;
                }
                if (composeController.isStoryPreviewPredictiveBackActive()) {
                    composeController.commitStoryPreviewPredictiveBack();
                } else {
                    composeController.requestDismissStoryPreview();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(
                this, linkSummaryBackCallback);
    }

    private void openStoryFromLinkSummary(Story story, int position, boolean showWebsite) {
        if (!isAdded() || story == null) {
            return;
        }
        int currentPosition = stories.indexOf(story);
        if (currentPosition < 0) {
            currentPosition = position;
        }
        markStoryClicked(story);
        if (currentPosition >= 0 && currentPosition < stories.size()) {
            adapter.updateStoryClickedState(currentPosition);
        }
        openComments(story, currentPosition, showWebsite);
    }

    private boolean isFoldableSplitLayout() {
        if (!isAdded() || !(requireActivity() instanceof MainActivity)) {
            return false;
        }
        return ((MainActivity) requireActivity()).isAdaptiveFoldableNavigation();
    }

    private boolean isTabletSplitLayout() {
        if (!isAdded() || !(requireActivity() instanceof MainActivity)) {
            return false;
        }
        MainActivity activity = (MainActivity) requireActivity();
        return activity.isAdaptiveTwoPaneNavigation()
                && !activity.isAdaptiveFoldableNavigation();
    }

    private void toggleStoryRead(Story story, int position) {
        if (!isAdded() || story == null) {
            return;
        }
        story.clicked = !story.clicked;
        if (story.clicked) {
            HistoriesUtils.INSTANCE.addHistory(requireContext(), story.id);
        } else {
            HistoriesUtils.INSTANCE.removeHistoryById(requireContext(), story.id);
        }
        int currentPosition = stories.indexOf(story);
        if (currentPosition >= 0) {
            adapter.updateStoryClickedState(currentPosition);
        }
    }

    private void toggleStoryBookmark(Story story, int position, boolean currentlyBookmarked) {
        if (!isAdded() || story == null) {
            return;
        }
        Context context = requireContext();
        if (currentlyBookmarked) {
            Utils.removeBookmark(context, story.id);
            if (isBookmarksType(adapter.type)) {
                bookmarkStories.remove(story);
                int currentPosition = stories.indexOf(story);
                if (currentPosition >= 0) {
                    removeStoryAt(currentPosition, storyListGeneration, true);
                }
                updateHeader();
                return;
            }
        } else {
            Utils.addBookmark(context, story.id);
        }
        int currentPosition = stories.indexOf(story);
        if (currentPosition >= 0) {
            adapter.notifyItemChanged(currentPosition);
        }
    }

    private void toggleStoryVote(Story story, int position, boolean currentlyUpvoted,
                                 Runnable completion) {
        if (!isAdded() || story == null || adapter == null || stories == null) {
            completion.run();
            return;
        }
        Context context = requireContext();
        int actionGeneration = storyListGeneration;
        StoryListState actionAdapter = adapter;
        List<Story> actionStories = stories;
        boolean newUpvoted = !currentlyUpvoted;
        Utils.setUpvoted(context, story.id, false, newUpvoted);
        int currentPosition = stories.indexOf(story);
        if (currentPosition >= 0) {
            adapter.notifyItemChanged(currentPosition);
        }
        UserActions.ActionCallback callback = new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                if (response != null) {
                    response.close();
                }
                completion.run();
            }

            @Override
            public void onFailure(String summary, String response) {
                Utils.setUpvoted(context, story.id, false, currentlyUpvoted);
                if (isCurrentStoryActionContext(
                        actionGeneration, actionAdapter, actionStories)) {
                    int restoredPosition = stories.indexOf(story);
                    if (restoredPosition >= 0) {
                        adapter.notifyItemChanged(restoredPosition);
                    }
                }
                completion.run();
            }
        };
        if (newUpvoted) {
            UserActions.upvote(context, story.id, callback);
        } else {
            UserActions.unvote(context, story.id, callback);
        }
    }

    private void toggleStoryFavorite(Story story, int position, boolean currentlyFavorited,
                                     Runnable completion) {
        if (!isAdded() || story == null || adapter == null || stories == null) {
            completion.run();
            return;
        }
        Context context = requireContext();
        int actionGeneration = storyListGeneration;
        StoryListState actionAdapter = adapter;
        List<Story> actionStories = stories;
        boolean actionIsFavoritesList = isFavoritesType(actionAdapter.type);
        boolean newFavorited = !currentlyFavorited;
        int optimisticIndex = stories.indexOf(story);
        Utils.setFavorite(context, story.id, newFavorited);
        if (optimisticIndex >= 0) {
            if (currentlyFavorited && actionIsFavoritesList) {
                removeStoryAt(optimisticIndex, storyListGeneration, true);
                updateHeader();
            } else {
                adapter.notifyItemChanged(optimisticIndex);
            }
        }
        UserActions.setFavorite(context, story.id, newFavorited,
                new UserActions.ActionCallback() {
                    @Override
                    public void onSuccess(Response response) {
                        if (response != null) {
                            response.close();
                        }
                        completion.run();
                    }

                    @Override
                    public void onFailure(String summary, String response) {
                        Utils.setFavorite(context, story.id, currentlyFavorited);
                        if (!isCurrentStoryActionContext(
                                actionGeneration, actionAdapter, actionStories)) {
                            completion.run();
                            return;
                        }
                        int currentIndex = stories.indexOf(story);
                        if (currentlyFavorited && actionIsFavoritesList && currentIndex == -1) {
                            int restoreIndex = optimisticIndex >= 0
                                    ? Math.min(optimisticIndex, stories.size()) : 0;
                            stories.add(restoreIndex, story);
                            adapter.notifyItemInserted(restoreIndex);
                            updateHeader();
                        } else if (currentIndex >= 0) {
                            adapter.notifyItemChanged(currentIndex);
                        }
                        completion.run();
                    }
                });
    }

    private boolean isCurrentStoryActionContext(
            int generation,
            StoryListState expectedAdapter,
            List<Story> expectedStories) {
        return isAdded()
                && generation == storyListGeneration
                && adapter == expectedAdapter
                && stories == expectedStories;
    }

    private int findStoryPositionById(int storyId) {
        if (stories == null) return NO_POSITION;
        for (int position = 0; position < stories.size(); position++) {
            Story story = stories.get(position);
            if (story != null && story.id == storyId) return position;
        }
        return NO_POSITION;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (getActivity() instanceof MainActivity) {
            storyClickListener = (MainActivity) getActivity();
        }
    }

    @Override
    public void onDetach() {
        storyClickListener = null;
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();

        filterWords = Utils.getFilterWords(getContext());
        filterDomains = Utils.getFilterDomains(getContext());
        filteredUsers = Utils.getFilteredUsers(getContext());
        boolean newHideJobs = SettingsUtils.shouldHideJobs(getContext());
        hideClicked = SettingsUtils.shouldHideClicked(getContext());
        alwaysOpenComments = SettingsUtils.shouldAlwaysOpenComments(getContext());
        refreshTypeSpinnerItemsIfNeeded();
        syncVisibleUserItemListWithLocalCache();
        refreshBookmarksIfNeeded();

        long timeDiff = System.currentTimeMillis() - lastLoaded;

        // if more than 1 hr
        boolean shouldShowUpdateButton = SettingsUtils.shouldAlwaysShowTapToRefresh(getContext())
                || (timeDiff > 1000 * 60 * 60 && !searching && !isBookmarksType(adapter.type) && !isUserItemListType(adapter.type) && !currentTypeIsAlgolia());
        if (shouldShowUpdateButton) {
            showUpdateButton();
        } else {
            hideUpdateButton();
        }

        StoryDisplaySettings displaySettings = StoryDisplaySettings.from(requireContext());
        boolean fontCacheChanged = TextUtils.isEmpty(FontUtils.font) || !FontUtils.font.equals(displaySettings.font);
        if (fontCacheChanged) {
            FontUtils.init(getContext());
        }
        StoryDisplaySettings.UpdateResult displayUpdate = displaySettings.applyToState(adapter);

        if (displayUpdate.requiresRebuild) {
            rebuildStoryAdapters();
        } else if ((displayUpdate.itemsChanged || fontCacheChanged) && adapter.getItemCount() > 0) {
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        if (displayUpdate.previewImageModeChanged) {
            scheduleLoadedPreviewImagePrefetchNearViewport();
        }

        if (displayUpdate.compactHeaderChanged) {
            updateHeader();
        }

        boolean newPaginationMode = SettingsUtils.shouldUsePaginationMode(getContext());
        if (paginationMode != newPaginationMode) {
            int oldItemCount = adapter.getItemCount();
            paginationMode = newPaginationMode;
            updateAdapterPaginationMode(adapter);
            resetPaginationState();

            int newItemCount = adapter.getItemCount();
            int sharedItemCount = Math.min(oldItemCount, newItemCount);

            if (sharedItemCount > 0) {
                adapter.notifyItemRangeChanged(0, sharedItemCount);
            }

            if (oldItemCount > newItemCount) {
                adapter.notifyItemRangeRemoved(newItemCount, oldItemCount - newItemCount);
            } else if (newItemCount > oldItemCount) {
                adapter.notifyItemRangeInserted(oldItemCount, newItemCount - oldItemCount);
            }
        }

        if (hideJobs != newHideJobs) {
            hideJobs = newHideJobs;
            attemptRefresh();
        }

        syncInactiveStoryAdapterDisplaySettings();
        syncStoriesWithHistoriesIfNeeded();
        syncComposeState();
    }

    private void refreshBookmarksIfNeeded() {
        if (!bookmarksChanged
                || adapter == null
                || searching
                || !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)
                || !isBookmarksType(adapter.type)) {
            return;
        }

        bookmarksChanged = false;
        attemptRefresh();
    }

    public void onAccountStateChanged() {
        refreshTypeSpinnerItemsIfNeeded();
        updateHeader();
    }

    public void applyWelcomePresetSettings() {
        if (adapter == null || getContext() == null) {
            return;
        }

        String previewImageMode = SettingsUtils.getPreferredStoryPreviewImageMode(getContext());
        boolean previewImageModeChanged = !adapter.previewImageMode.equals(previewImageMode);
        boolean tintCardUsingPreview = SettingsUtils.shouldTintCardUsingPreview(getContext());
        boolean storyCardShellChanged = adapter.tintCardUsingPreview != tintCardUsingPreview && !adapter.cardStyle;
        String preferredFont = SettingsUtils.getPreferredFont(getContext());
        boolean fontChanged = !preferredFont.equals(adapter.font);
        boolean fontCacheChanged = TextUtils.isEmpty(FontUtils.font) || !FontUtils.font.equals(preferredFont);

        if (fontCacheChanged) {
            FontUtils.init(getContext());
        }

        adapter.previewImageMode = previewImageMode;
        adapter.showSummary = SettingsUtils.shouldShowStorySummary(getContext());
        adapter.tintCardUsingPreview = tintCardUsingPreview;
        adapter.font = preferredFont;

        if (storyCardShellChanged) {
            rebuildStoryAdapters();
        } else {
            syncInactiveStoryAdapterDisplaySettings();
            notifyStoryDisplaySettingsChanged(mainAdapter);
            notifyStoryDisplaySettingsChanged(searchAdapter);
        }

        if (previewImageModeChanged) {
            scheduleLoadedPreviewImagePrefetchNearViewport();
        }

    }

    private void notifyStoryDisplaySettingsChanged(@Nullable StoryListState targetAdapter) {
        if (targetAdapter != null && targetAdapter.getItemCount() > 0) {
            targetAdapter.notifyItemRangeChanged(0, targetAdapter.getItemCount());
        }
    }

    private void syncStoriesWithHistoriesIfNeeded() {
        long currentHistoriesChangeVersion = HistoriesUtils.INSTANCE.getChangeVersion();
        if (historiesChangeVersion == currentHistoriesChangeVersion || adapter == null || stories == null) {
            return;
        }

        historiesChangeVersion = currentHistoriesChangeVersion;

        if (searching && searchController.isOnlyClicked()) {
            boolean clickedStateChanged = false;
            for (Story story : stories) {
                if (story.clicked) {
                    story.clicked = false;
                    clickedStateChanged = true;
                }
            }

            if (clickedStateChanged) {
                adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            }
            return;
        }

        if (isHistoryType(adapter.type)) {
            attemptRefresh();
            return;
        }

        if (hideClicked) {
            if (removeClickedStoriesFromCurrentList()) {
                return;
            }

            attemptRefresh();
            return;
        }

        boolean clickedStateChanged = false;
        for (Story story : stories) {
            boolean clicked = HistoriesUtils.INSTANCE.isHistoryExist(story.id);
            if (story.clicked != clicked) {
                story.clicked = clicked;
                clickedStateChanged = true;
            }
        }

        if (clickedStateChanged) {
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    private boolean removeClickedStoriesFromCurrentList() {
        boolean removedStories = false;

        for (int i = stories.size() - 1; i >= 0; i--) {
            Story story = stories.get(i);
            if (HistoriesUtils.INSTANCE.isHistoryExist(story.id)) {
                removeStoryAt(i, storyListGeneration, false);
                removedStories = true;
            }
        }

        if (removedStories) {
            loadVisibleStories(storyListGeneration);
            updateHeader();
        }

        return removedStories;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        saveStoryStateForRecreation();
        int visibleStoryId = composeController == null
                ? NO_PENDING_LINK_SUMMARY_STORY_ID
                : composeController.getVisibleStoryPreviewId();
        if (visibleStoryId != NO_PENDING_LINK_SUMMARY_STORY_ID) {
            outState.putInt(STATE_LINK_SUMMARY_STORY_ID, visibleStoryId);
        }
        super.onSaveInstanceState(outState);
    }
    public void onDestroy() {
        saveStoryStateForRecreation();
        if (composeController != null) {
            composeController.completeStoryPreviewDismiss();
        }
        if (linkSummaryBackCallback != null) {
            linkSummaryBackCallback.remove();
            linkSummaryBackCallback = null;
        }
        if (storyUpdateListener != null) {
            StoryUpdate.clearStoryUpdatedListener(storyUpdateListener);
        }
        unregisterStoryAdapterDataObservers();
        if (mainAdapter != null) mainAdapter.dispose();
        if (searchAdapter != null) searchAdapter.dispose();
        if (queue != null) {
            storyListGeneration++;
            clearLoadingStoryState();
            resetPreviewImagePrefetchRamp();
            invalidateAlgoliaLoad();
            queue.cancelAll(requestTag);
        }
        pendingStoryRowChangeGenerations.clear();
        pendingStoryRemovals.clear();
        clearControllerReferences();
        if (bookmarkPreferences != null) {
            bookmarkPreferences.unregisterOnSharedPreferenceChangeListener(
                    bookmarkPreferenceChangeListener);
            bookmarkPreferences = null;
        }
        super.onDestroy();
    }

    private void clearControllerReferences() {
        if (composeController != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).detachStoriesComposeController(composeController);
        }
        if (storyCacheController != null) {
            storyCacheController.dispose();
            storyCacheController = null;
        }
        composeController = null;
        storyUpdateListener = null;
        mainAdapter = null;
        searchAdapter = null;
        adapter = null;
        stories = null;
    }

    private void clickedComments(int position) {
        // prevent double clicks
        long now = System.currentTimeMillis();
        if (now - lastClick > CLICK_INTERVAL) {
            lastClick = now;
        } else {
            return;
        }

        if (position == NO_POSITION) {
            return;
        }

        Story story = stories.get(position);
        if (story.loaded) {
            if (story.isFrontpageLink) {
                story.clicked = true;
                adapter.updateStoryClickedState(position);
                Utils.launchCustomTab(getContext(), story.url);
                return;
            }

            markStoryClicked(story);
            adapter.updateStoryClickedState(position);

            openComments(story, position, false);
        }
    }

    private void clickedCommentStory(int position) {
        if (position == NO_POSITION) {
            return;
        }

        Story story = stories.get(position);
        Story masterStory = story.toCommentMasterStory();
        if (masterStory != null) {
            openCommentMasterStory(story, masterStory, position);
        } else {
            clickedComments(position);
        }
    }

    private void openCommentMasterStory(Story sourceStory, Story masterStory, int position) {
        if (masterStory.loaded) {
            openComments(masterStory, position, false);
            return;
        }

        String url = "https://hacker-news.firebaseio.com/v0/item/" + masterStory.id + ".json";
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isAdded() || adapter == null) {
                        return;
                    }

                    try {
                        JSONParser.updateCommentMasterStoryWithHNJson(sourceStory, response);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    int index = stories.indexOf(sourceStory);
                    if (index >= 0) {
                        adapter.notifyItemChanged(index);
                    }

                    Story refreshedMasterStory = sourceStory.toCommentMasterStory();
                    openComments(refreshedMasterStory != null ? refreshedMasterStory : masterStory, position, false);
                }, error -> openComments(masterStory, position, false));

        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private void markStoryClicked(Story story) {
        if (!searchController.isOnlyClicked()) {
            story.clicked = true;
        }
        HistoriesUtils.INSTANCE.addHistory(requireContext(), story.id);
    }

    private void removeStoryAt(int index, int loadGeneration, boolean loadVisibleReplacement) {
        if (index < 0 || index >= stories.size()) {
            return;
        }

        Story removedStory = stories.remove(index);
        finishPaginationLoadMoreStory(removedStory, loadGeneration);
        clearStoryLoadState(removedStory);
        if (index <= loadedTo) {
            loadedTo = Math.max(-1, loadedTo - 1);
        }

        if (adapter != null && adapter.paginationMode) {
            adapter.notifyDataSetChanged();
        } else if (adapter != null) {
            adapter.notifyItemRemoved(index);
            adapter.updateStoryIndicesFromPosition(index);
        }

        if (loadVisibleReplacement) {
            loadVisibleStories(loadGeneration);
        }
    }

    private boolean shouldFilterLoadedStory(Story story) {
        if (story == null) {
            return false;
        }

        if (filteredUsers != null
                && !TextUtils.isEmpty(story.by)
                && filteredUsers.contains(story.by.toLowerCase().trim())) {
            return true;
        }

        if (filterWords != null && story.title != null) {
            String title = story.title.toLowerCase();
            for (String phrase : filterWords) {
                if (!TextUtils.isEmpty(phrase) && title.contains(phrase.toLowerCase())) {
                    return true;
                }
            }
        }

        if (filterDomains != null && story.url != null) {
            try {
                String domain = story.getDisplayDomain(true).toLowerCase();
                for (String phrase : filterDomains) {
                    if (!TextUtils.isEmpty(phrase)
                            && domain.contains(phrase.toLowerCase())) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // Invalid URLs cannot match a domain filter.
            }
        }

        return shouldHideStoryAsJob(story);
    }

    private boolean isStoryLoadInProgress(Story story) {
        if (story == null) {
            return false;
        }

        Long startedAt = loadingStoryStartTimes.get(story.id);
        if (startedAt == null) {
            return false;
        }

        if (System.currentTimeMillis() - startedAt > STORY_LOAD_STALE_TIMEOUT_MS) {
            loadingStoryStartTimes.remove(story.id);
            return false;
        }

        return true;
    }

    private long markStoryLoadStarted(Story story) {
        long startedAt = System.currentTimeMillis();
        if (story != null) {
            loadingStoryStartTimes.put(story.id, startedAt);
        }
        return startedAt;
    }

    private void clearStoryLoadState(Story story) {
        if (story != null) {
            loadingStoryStartTimes.remove(story.id);
        }
    }

    private void clearStoryLoadState(Story story, long startedAt) {
        if (story == null) {
            return;
        }

        Long currentStartedAt = loadingStoryStartTimes.get(story.id);
        if (currentStartedAt != null && currentStartedAt == startedAt) {
            loadingStoryStartTimes.remove(story.id);
        }
    }

    private boolean isCurrentStoryLoad(Story story, long startedAt) {
        if (story == null) {
            return false;
        }

        Long currentStartedAt = loadingStoryStartTimes.get(story.id);
        return currentStartedAt != null && currentStartedAt == startedAt;
    }

    private void clearLoadingStoryState() {
        loadingStoryStartTimes.clear();
    }

    private void loadStory(Story story, final int attempt) {
        loadStory(story, attempt, storyListGeneration);
    }

    private void loadStory(Story story, final int attempt, final int loadGeneration) {
        if (!isCurrentStoryListGeneration(loadGeneration)) {
            return;
        }

        PendingStoryRemoval pendingRemoval = pendingStoryRemovals.get(story.id);
        if (pendingRemoval != null && pendingRemoval.generation == loadGeneration) {
            return;
        }

        if (story.loaded) {
            int index = stories.indexOf(story);
            if (index >= 0 && shouldFilterLoadedStory(story)) {
                enqueueStoryRemoval(story, loadGeneration, false);
            }
            return;
        }

        if (attempt >= 3 || isStoryLoadInProgress(story)) {
            return;
        }

        final long startedAt = markStoryLoadStarted(story);

        String url = "https://hacker-news.firebaseio.com/v0/item/" + story.id + ".json";

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isCurrentStoryLoad(story, startedAt)) {
                        return;
                    }
                    clearStoryLoadState(story, startedAt);
                    if (!isCurrentStoryListGeneration(loadGeneration)) {
                        return;
                    }
                    int index = stories.indexOf(story);
                    if (index < 0) {
                        return;
                    }
                    try {
                        if (!JSONParser.updateStoryWithHNJson(response, story, isHistoryType(adapter.type))) {
                            enqueueStoryRemoval(story, loadGeneration, false);
                            return;
                        }

                        finishPaginationLoadMoreStory(story, loadGeneration);

                        if (story.isComment && currentTypeUsesCommentRows()) {
                            loadCommentMaster(story, story.parentId, 0, loadGeneration);
                        }

                        if (currentTypeUsesSavedItemFilter() && !shouldShowStoryForSavedItemFilter(story)) {
                            enqueueStoryRemoval(story, loadGeneration, true);
                            return;
                        }

                        if (shouldFilterLoadedStory(story)) {
                            enqueueStoryRemoval(story, loadGeneration, false);
                            return;
                        }

                        Context context = getContext();
                        if (context != null) {
                            requestPreviewImagePrefetch(context, story);
                        }

                        enqueueStoryRowChange(story, loadGeneration);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Utils.log("Failed to load story with id: " + story.id);
                        story.loadingFailed = true;
                        finishPaginationLoadMoreStory(story, loadGeneration);
                        updatePreviewImagePrefetchRampCompletion();
                        enqueueStoryRowChange(story, loadGeneration);
                    }
                }, error -> {
            if (!isCurrentStoryLoad(story, startedAt)) {
                return;
            }
            clearStoryLoadState(story, startedAt);
            if (!isCurrentStoryListGeneration(loadGeneration)) {
                return;
            }
            if (story.loaded) {
                return;
            }
            error.printStackTrace();
            story.loadingFailed = true;
            if (attempt >= 2) {
                finishPaginationLoadMoreStory(story, loadGeneration);
            }
            updatePreviewImagePrefetchRampCompletion();
            int index = stories.indexOf(story);
            if (index >= 0) {
                enqueueStoryRowChange(story, loadGeneration);
                loadStory(story, attempt + 1, loadGeneration);
            }
        });

        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    public void attemptRefresh() {
        attemptRefresh(false);
    }

    private void attemptStoryTypeRefresh() {
        attemptRefresh(false, true);
    }

    private void invalidateAlgoliaLoad() {
        algoliaRequestGeneration++;
        algoliaLoading = false;
        activeAlgoliaUrl = null;
    }

    private int beginStoryListRefresh() {
        storyListGeneration++;
        clearLoadingStoryState();
        resetPreviewImagePrefetchRamp();
        resetScrapedFrontpagePaginationState();
        invalidateAlgoliaLoad();
        queue.cancelAll(requestTag);
        return storyListGeneration;
    }

    private void resetScrapedFrontpagePaginationState() {
        scrapedFrontpageNextPageUrl = null;
        scrapedFrontpageNextPageLoading = false;
        scrapedFrontpageStoryType = StoryType.UNKNOWN;
        if (adapter != null) {
            adapter.setLoadMoreLoading(false);
        }
    }

    private boolean isCurrentStoryListGeneration(int generation) {
        return generation == storyListGeneration;
    }

    private void attemptRefresh(boolean showSwipeRefreshIndicator) {
        attemptRefresh(showSwipeRefreshIndicator, false);
    }

    private void attemptRefresh(boolean showSwipeRefreshIndicator, boolean showMainLoadingIndicator) {
        hideUpdateButton();
        if (searching) {
            Log.d(TAG, "Refreshing active search, queryLength=" + (lastSearch == null ? 0 : lastSearch.length()));
            search(lastSearch);
            return;
        }

        setRefreshIndicatorShowing(showSwipeRefreshIndicator && !showMainLoadingIndicator);

        // cancel all ongoing
        int refreshGeneration = beginStoryListRefresh();
        StoryType currentStoryType = getCurrentStoryType();
        Log.d(TAG, "Starting refresh generation=" + refreshGeneration
                + ", type=" + currentStoryType.getLabel()
                + ", showSwipeRefreshIndicator=" + showSwipeRefreshIndicator
                + ", showMainLoadingIndicator=" + showMainLoadingIndicator);

        boolean userItemListTypeForRefresh = isUserItemListType(adapter.type);
        if (showMainLoadingIndicator) {
            loadingFailed = false;
            loadingFailedServerError = false;
            loadingFailedRateLimited = false;
            showingCached = false;
            userItemListInitialLoadInProgress = userItemListTypeForRefresh;
            replaceStories(new ArrayList<>(), true);
            updateHeader();
        }

        if (currentTypeIsAlgolia()) {
            // algoliaStuff
            resetAlgoliaResultLimit();
            loadTopStoriesSince(getCurrentAlgoliaTopStoriesStartTime(), showSwipeRefreshIndicator && !showMainLoadingIndicator);

            return;
        }

        lastLoaded = System.currentTimeMillis();

        if (isBookmarksType(adapter.type)) {
            // lets load bookmarks instead - or rather add empty stories with correct id:s and start loading them
            ArrayList<Story> refreshedStories = new ArrayList<>();
            showingCached = false;

            bookmarksChanged = false;
            ArrayList<Bookmark> bookmarks = Utils.loadBookmarks(getContext(), true);

            for (int i = 0; i < bookmarks.size(); i++) {
                Story s = new Story("Loading...", bookmarks.get(i).id, false, false);
                refreshedStories.add(s);
            }

            bookmarkStories.clear();
            bookmarkStories.addAll(refreshedStories);
            replaceStories(getFilteredSavedItemStories(), true);
            loadInitialVisibleStories(refreshGeneration);

            updateHeader();
            setRefreshIndicatorShowing(false);

            return;
        } else if (isUserItemListType(adapter.type)) {
            boolean shouldLoadCachedUserItemList = showMainLoadingIndicator || stories.isEmpty();
            boolean hasCachedUserItemList = shouldLoadCachedUserItemList
                    ? loadUserItemListCache()
                    : !UserItemListRepository.loadCache(getContext(), getCurrentUserItemListSource()).isEmpty();
            if (!shouldLoadCachedUserItemList) {
                resumeInterruptedStoryLoads();
            }
            syncUserItemListFromServer(showSwipeRefreshIndicator || hasCachedUserItemList);
            return;
        } else if (isHistoryType(adapter.type)) {
            ArrayList<Story> refreshedStories = new ArrayList<>();
            showingCached = false;
            List<History> histories = HistoriesUtils.INSTANCE.loadHistories(requireContext(), true);

            for (int i = 0; i < histories.size(); i++) {
                Story s = new Story("Loading...", histories.get(i).getId(), false, false, histories.get(i).getCreated());
                refreshedStories.add(s);
            }

            replaceStories(refreshedStories, true);
            loadInitialVisibleStories(refreshGeneration);

            updateHeader();
            setRefreshIndicatorShowing(false);

            return;
        }

        if (currentStoryType.isFrontpageLinkList()) {
            loadFrontpageLinkRows(currentStoryType, refreshGeneration);
            return;
        }
        if (currentStoryType.isScrapedFrontpage()) {
            loadScrapedFrontpageStories(currentStoryType, refreshGeneration);
            return;
        }

        // if none of the above, do a normal loading
        String storyListUrl = currentStoryType.getHackerNewsUrl();
        if (storyListUrl == null) {
            setRefreshIndicatorShowing(false);
            loadingFailed = true;
            loadingFailedRateLimited = false;
            Log.w(TAG, "Story list refresh failed before request: missing URL for type=" + currentStoryType.getLabel()
                    + ", generation=" + refreshGeneration);
            updateHeader();
            return;
        }

        StringRequest stringRequest = new StringRequest(Request.Method.GET, storyListUrl,
                response -> {
                    if (!isCurrentStoryListGeneration(refreshGeneration)) {
                        Log.d(TAG, "Ignoring stale story list success for type=" + currentStoryType.getLabel()
                                + ", generation=" + refreshGeneration
                                + ", currentGeneration=" + storyListGeneration);
                        return;
                    }
                    setRefreshIndicatorShowing(false);
                    try {
                        JSONArray jsonArray = new JSONArray(response);
                        ArrayList<Integer> itemIds = new ArrayList<>();

                        for (int i = 0; i < jsonArray.length(); i++) {
                            int id = Integer.parseInt(jsonArray.get(i).toString());
                            itemIds.add(id);
                        }

                        showingCached = false;
                        replaceStories(createLoadingStoriesFromIds(itemIds));

                        if (loadingFailed) {
                            loadingFailed = false;
                            loadingFailedServerError = false;
                            loadingFailedRateLimited = false;
                        }

                        updateHeader();

                        loadInitialVisibleStories(refreshGeneration);

                    } catch (JSONException e) {
                        Log.w(TAG, "Failed to parse story list JSON for type=" + currentStoryType.getLabel()
                                + ", generation=" + refreshGeneration
                                + ", responseLength=" + (response == null ? 0 : response.length()), e);
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "Failed to parse story id in list for type=" + currentStoryType.getLabel()
                                + ", generation=" + refreshGeneration
                                + ", responseLength=" + (response == null ? 0 : response.length()), e);
                    }
                }, error -> {
            if (!isCurrentStoryListGeneration(refreshGeneration)) {
                Log.d(TAG, "Ignoring stale story list failure for type=" + currentStoryType.getLabel()
                        + ", generation=" + refreshGeneration
                        + ", currentGeneration=" + storyListGeneration);
                return;
            }
            setRefreshIndicatorShowing(false);
            loadingFailed = true;
            loadingFailedRateLimited = isRateLimitedError(error);
            Log.w(TAG, "Story list request failed for type=" + currentStoryType.getLabel()
                    + ", generation=" + refreshGeneration
                    + ", error=" + error);
            updateHeader();
        });

        updateHeader();
        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private void loadScrapedFrontpageStories(StoryType storyType, int refreshGeneration) {
        Context ctx = getContext();
        if (ctx == null) {
            setRefreshIndicatorShowing(false);
            loadingFailed = true;
            loadingFailedServerError = false;
            loadingFailedRateLimited = false;
            Log.w(TAG, "Scraped frontpage refresh failed before request: missing context for type="
                    + storyType.getLabel() + ", generation=" + refreshGeneration);
            updateHeader();
            return;
        }

        String frontDay = storyType.isFront() ? getFrontPageDayParameter() : null;
        Log.d(TAG, "Fetching scraped frontpage type=" + storyType.getLabel()
                + ", path=" + storyType.getHackerNewsPath()
                + ", commentsPage=" + storyType.usesCommentRows()
                + ", day=" + frontDay
                + ", generation=" + refreshGeneration);
        final boolean[] callbackReceived = {false};
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!callbackReceived[0]
                    && isAdded()
                    && adapter != null
                    && getCurrentStoryType() == storyType
                    && isCurrentStoryListGeneration(refreshGeneration)) {
                Log.w(TAG, "Scraped frontpage request still pending for type=" + storyType.getLabel()
                        + ", path=" + storyType.getHackerNewsPath()
                        + ", generation=" + refreshGeneration
                        + ", loadingFailed=" + loadingFailed
                        + ", networkAvailable=" + Utils.isNetworkAvailable(getContext()));
            }
        }, 15000);
        UserActions.fetchStoryListIds(
                ctx,
                storyType.getHackerNewsPath(),
                storyType.getLabel().toLowerCase(Locale.US),
                storyType.usesCommentRows(),
                frontDay,
                new UserActions.StoryListCallback() {
            @Override
            public void onSuccess(List<Integer> itemIds, List<Integer> commentIds, String nextPageUrl) {
                callbackReceived[0] = true;
                if (!isAdded()
                        || adapter == null
                        || getCurrentStoryType() != storyType
                        || !isCurrentStoryListGeneration(refreshGeneration)) {
                    Log.d(TAG, "Ignoring stale scraped frontpage success for type=" + storyType.getLabel()
                            + ", generation=" + refreshGeneration
                            + ", currentGeneration=" + storyListGeneration
                            + ", isAdded=" + isAdded()
                            + ", adapterPresent=" + (adapter != null)
                            + ", currentType=" + getCurrentStoryType().getLabel());
                    return;
                }

                setRefreshIndicatorShowing(false);
                loadingFailed = itemIds.isEmpty();
                loadingFailedServerError = false;
                loadingFailedRateLimited = false;
                showingCached = false;
                scrapedFrontpageStoryType = storyType;
                scrapedFrontpageNextPageUrl = nextPageUrl;
                scrapedFrontpageNextPageLoading = false;
                Log.d(TAG, "Scraped frontpage success for type=" + storyType.getLabel()
                        + ", generation=" + refreshGeneration
                        + ", itemCount=" + itemIds.size()
                        + ", commentIdCount=" + commentIds.size()
                        + ", hasNextPage=" + !TextUtils.isEmpty(nextPageUrl)
                        + ", loadingFailed=" + loadingFailed);

                if (!loadingFailed) {
                    replaceStories(createLoadingStoriesFromIds(itemIds, new HashSet<>(commentIds)),
                            false,
                            !TextUtils.isEmpty(scrapedFrontpageNextPageUrl));
                }

                updateHeader();
                loadInitialVisibleStories(refreshGeneration);
            }

            @Override
            public void onFailure(String summary, String response) {
                callbackReceived[0] = true;
                if (!isAdded()
                        || adapter == null
                        || getCurrentStoryType() != storyType
                        || !isCurrentStoryListGeneration(refreshGeneration)) {
                    Log.d(TAG, "Ignoring stale scraped frontpage failure for type=" + storyType.getLabel()
                            + ", generation=" + refreshGeneration
                            + ", currentGeneration=" + storyListGeneration
                            + ", isAdded=" + isAdded()
                            + ", adapterPresent=" + (adapter != null)
                            + ", currentType=" + getCurrentStoryType().getLabel()
                            + ", summary=" + summary
                            + ", response=" + response);
                    return;
                }

                setRefreshIndicatorShowing(false);
                loadingFailed = true;
                loadingFailedServerError = false;
                loadingFailedRateLimited = isRateLimitedResponse(summary, response);
                Log.w(TAG, "Scraped frontpage request failed for type=" + storyType.getLabel()
                        + ", path=" + storyType.getHackerNewsPath()
                        + ", generation=" + refreshGeneration
                        + ", summary=" + summary
                        + ", response=" + response);
                updateHeader();
            }
        });

        updateHeader();
    }

    private void loadMoreScrapedFrontpageStories(int refreshGeneration) {
        Context ctx = getContext();
        StoryType storyType = getCurrentStoryType();
        if (ctx == null
                || adapter == null
                || scrapedFrontpageNextPageLoading
                || storyType != scrapedFrontpageStoryType
                || TextUtils.isEmpty(scrapedFrontpageNextPageUrl)) {
            return;
        }

        scrapedFrontpageNextPageLoading = true;
        adapter.setLoadMoreLoading(true);
        String nextPageUrl = scrapedFrontpageNextPageUrl;
        UserActions.fetchStoryListPage(
                ctx,
                nextPageUrl,
                storyType.getLabel().toLowerCase(Locale.US),
                storyType.usesCommentRows(),
                new UserActions.StoryListCallback() {
                    @Override
                    public void onSuccess(List<Integer> itemIds, List<Integer> commentIds, String nextPageUrl) {
                        if (!isAdded()
                                || adapter == null
                                || getCurrentStoryType() != storyType
                                || scrapedFrontpageStoryType != storyType
                                || !isCurrentStoryListGeneration(refreshGeneration)) {
                            return;
                        }

                        scrapedFrontpageNextPageLoading = false;
                        adapter.setLoadMoreLoading(false);
                        scrapedFrontpageNextPageUrl = nextPageUrl;
                        ArrayList<Story> newStories = createNewLoadingStoriesFromIds(itemIds, new HashSet<>(commentIds));
                        stories.addAll(newStories);
                        adapter.showLoadMoreButton = !TextUtils.isEmpty(scrapedFrontpageNextPageUrl);
                        if (adapter.paginationMode && !newStories.isEmpty()) {
                            adapter.visibleStoryCount = Math.min(adapter.visibleStoryCount + newStories.size(), stories.size());
                        }
                        adapter.notifyDataSetChanged();
                        loadVisibleStories(refreshGeneration);
                        updateHeader();
                    }

                    @Override
                    public void onFailure(String summary, String response) {
                        if (!isAdded()
                                || adapter == null
                                || getCurrentStoryType() != storyType
                                || scrapedFrontpageStoryType != storyType
                                || !isCurrentStoryListGeneration(refreshGeneration)) {
                            return;
                        }

                        scrapedFrontpageNextPageLoading = false;
                        adapter.setLoadMoreLoading(false);
                        adapter.showLoadMoreButton = true;
                        adapter.notifyDataSetChanged();
                        updateHeader();
                    }
                });
    }

    private void loadFrontpageLinkRows(StoryType storyType, int refreshGeneration) {
        Context ctx = getContext();
        if (ctx == null) {
            setRefreshIndicatorShowing(false);
            loadingFailed = true;
            loadingFailedServerError = false;
            loadingFailedRateLimited = false;
            updateHeader();
            return;
        }

        UserActions.fetchHackerNewsListLinks(ctx, new UserActions.StoryRowsCallback() {
            @Override
            public void onSuccess(List<Story> linkRows) {
                if (!isAdded()
                        || adapter == null
                        || getCurrentStoryType() != storyType
                        || !isCurrentStoryListGeneration(refreshGeneration)) {
                    return;
                }

                setRefreshIndicatorShowing(false);
                loadingFailed = linkRows.isEmpty();
                loadingFailedServerError = false;
                loadingFailedRateLimited = false;
                showingCached = false;

                if (!loadingFailed) {
                    replaceStories(linkRows);
                    loadedTo = stories.size() - 1;
                }

                updateHeader();
            }

            @Override
            public void onFailure(String summary, String response) {
                if (!isAdded()
                        || adapter == null
                        || getCurrentStoryType() != storyType
                        || !isCurrentStoryListGeneration(refreshGeneration)) {
                    return;
                }

                setRefreshIndicatorShowing(false);
                loadingFailed = true;
                loadingFailedServerError = false;
                loadingFailedRateLimited = isRateLimitedResponse(summary, response);
                updateHeader();
            }
        });

        updateHeader();
    }

    private boolean isRateLimitedError(@Nullable VolleyError error) {
        return error != null
                && error.networkResponse != null
                && error.networkResponse.statusCode == 429;
    }

    private boolean isRateLimitedResponse(@Nullable String summary, @Nullable String response) {
        return containsHttp429(summary) || containsHttp429(response);
    }

    private boolean containsHttp429(@Nullable String text) {
        return text != null
                && (text.contains("429")
                || text.toLowerCase(Locale.US).contains("too many requests"));
    }

    private ArrayList<Story> createLoadingStoriesFromIds(List<Integer> itemIds) {
        return createLoadingStoriesFromIds(itemIds, new HashSet<>());
    }

    private ArrayList<Story> createNewLoadingStoriesFromIds(List<Integer> itemIds, Set<Integer> commentIds) {
        HashSet<Integer> existingStoryIds = new HashSet<>();
        for (Story story : stories) {
            existingStoryIds.add(story.id);
        }

        ArrayList<Integer> newItemIds = new ArrayList<>();
        for (int id : itemIds) {
            if (!existingStoryIds.contains(id)) {
                newItemIds.add(id);
            }
        }

        return createLoadingStoriesFromIds(newItemIds, commentIds);
    }

    private ArrayList<Story> createLoadingStoriesFromIds(List<Integer> itemIds, Set<Integer> commentIds) {
        ArrayList<Story> refreshedStories = new ArrayList<>();
        Context ctx = getContext();

        for (int id : itemIds) {
            if (hideClicked && HistoriesUtils.INSTANCE.isHistoryExist(id)) {
                continue;
            }

            Story story = new Story("Loading...", id, false, HistoriesUtils.INSTANCE.isHistoryExist(id));
            boolean isComment = commentIds.contains(id);
            story.isComment = isComment;
            if (Utils.loadCachedStorySummary(ctx, story) && shouldFilterLoadedStory(story)) {
                continue;
            }
            if (isComment) {
                story.isComment = true;
            }

            refreshedStories.add(story);
        }

        return refreshedStories;
    }

    private void loadCommentMaster(Story story, int parentId, int attempt) {
        loadCommentMaster(story, parentId, attempt, storyListGeneration);
    }

    private void loadCommentMaster(Story story, int parentId, int attempt, int loadGeneration) {
        if (parentId <= 0
                || attempt >= 8
                || (story.commentMasterId > 0 && !TextUtils.isEmpty(story.commentMasterTitle))
                || !isCurrentStoryListGeneration(loadGeneration)) {
            return;
        }

        String url = "https://hacker-news.firebaseio.com/v0/item/" + parentId + ".json";
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isCurrentStoryListGeneration(loadGeneration)) {
                        return;
                    }
                    try {
                        if (TextUtils.isEmpty(response) || "null".equals(response)) {
                            return;
                        }

                        JSONObject parent = new JSONObject(response);
                        String parentType = parent.optString("type");
                        if ("comment".equals(parentType)) {
                            loadCommentMaster(story, parent.optInt("parent", 0), attempt + 1, loadGeneration);
                            return;
                        }

                        if (!JSONParser.updateCommentMasterStoryWithHNJson(story, response)) {
                            return;
                        }

                        int index = stories.indexOf(story);
                        if (index >= 0) {
                            enqueueStoryRowChange(story, loadGeneration);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, error -> {
            if (attempt < 2 && isCurrentStoryListGeneration(loadGeneration)) {
                loadCommentMaster(story, parentId, attempt + 1, loadGeneration);
            }
        });

        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private boolean loadUserItemListCache() {
        showingCached = false;
        loadingFailed = false;
        loadingFailedServerError = false;
        loadingFailedRateLimited = false;
        userItemListInitialLoadInProgress = false;

        UserItemListRepository.Snapshot snapshot = UserItemListRepository.loadCachedSnapshot(getContext(), getCurrentUserItemListSource());
        replaceUserItemListStoriesWithIds(snapshot.itemIds, snapshot.commentIds);
        return !snapshot.itemIds.isEmpty();
    }

    private void syncUserItemListFromServer(boolean showSwipeRefreshIndicator) {
        Context ctx = getContext();
        if (ctx == null) {
            setRefreshIndicatorShowing(false);
            userItemListInitialLoadInProgress = false;
            updateHeader();
            return;
        }

        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(requireContext());
            setRefreshIndicatorShowing(false);
            userItemListInitialLoadInProgress = false;
            loadingFailed = stories.isEmpty();
            loadingFailedRateLimited = false;
            updateHeader();
            return;
        }

        UserItemListRepository.Source syncSource = getCurrentUserItemListSource();
        boolean upvotedTypeForSync = syncSource == UserItemListRepository.Source.UPVOTED;
        userItemListInitialLoadInProgress = stories.isEmpty() && !showSwipeRefreshIndicator;
        setRefreshIndicatorShowing(showSwipeRefreshIndicator);
        updateHeader();

        final int syncGeneration = storyListGeneration;
        UserActions.UserItemListCallback callback = new UserActions.UserItemListCallback() {
            @Override
            public void onSuccess(List<Integer> itemIds, List<Integer> commentIds) {
                if (!isAdded()
                        || adapter == null
                        || !isSameUserItemListType(adapter.type, upvotedTypeForSync)
                        || !isCurrentStoryListGeneration(syncGeneration)) {
                    return;
                }

                Context currentContext = getContext();
                if (currentContext == null) {
                    return;
                }

                UserItemListRepository.Snapshot snapshot = UserItemListRepository.normalizeSnapshot(itemIds, commentIds);
                if (!UserItemListRepository.idsMatchCache(currentContext, syncSource, snapshot)) {
                    UserItemListRepository.saveIds(currentContext, syncSource, snapshot);
                }
                syncUserItemListStoriesToIds(snapshot.itemIds, snapshot.commentIds);

                userItemListInitialLoadInProgress = false;
                loadingFailed = false;
                loadingFailedServerError = false;
                loadingFailedRateLimited = false;
                setRefreshIndicatorShowing(false);
                updateHeader();
            }

            @Override
            public void onFailure(String summary, String response) {
                if (!isAdded()
                        || adapter == null
                        || !isSameUserItemListType(adapter.type, upvotedTypeForSync)
                        || !isCurrentStoryListGeneration(syncGeneration)) {
                    return;
                }

                setRefreshIndicatorShowing(false);
                userItemListInitialLoadInProgress = false;
                loadingFailed = stories.isEmpty();
                loadingFailedRateLimited = isRateLimitedResponse(summary, response);
                updateHeader();
                Toast.makeText(requireContext(), summary, Toast.LENGTH_SHORT).show();
            }
        };

        if (upvotedTypeForSync) {
            UserActions.fetchUpvoted(ctx, callback);
        } else {
            UserActions.fetchFavorites(ctx, callback);
        }
    }

    private void syncVisibleUserItemListWithLocalCache() {
        if (adapter == null || stories == null || !isUserItemListType(adapter.type)) {
            return;
        }

        UserItemListRepository.Snapshot snapshot = UserItemListRepository.loadCachedSnapshot(getContext(), getCurrentUserItemListSource());
        syncUserItemListStoriesToIds(snapshot.itemIds, snapshot.commentIds);
    }

    private boolean syncUserItemListStoriesToIds(List<Integer> itemIds, Set<Integer> commentIds) {
        if (itemIdsMatchUserItemListStories(itemIds, commentIds)) {
            return false;
        }

        replaceUserItemListStoriesWithIds(itemIds, commentIds);
        return true;
    }

    private boolean itemIdsMatchUserItemListStories(List<Integer> itemIds, Set<Integer> commentIds) {
        if (userItemListStories.size() != itemIds.size() || !userItemListCommentIds.equals(commentIds)) {
            return false;
        }

        for (int i = 0; i < userItemListStories.size(); i++) {
            if (userItemListStories.get(i).id != itemIds.get(i)) {
                return false;
            }
        }

        return true;
    }

    private void replaceUserItemListStoriesWithIds(List<Integer> itemIds, Set<Integer> commentIds) {
        Map<Integer, Story> existingStories = new HashMap<>();
        for (Story story : userItemListStories.isEmpty() ? stories : userItemListStories) {
            existingStories.put(story.id, story);
        }

        ArrayList<Story> refreshedStories = new ArrayList<>();
        for (int id : itemIds) {
            Story existingStory = existingStories.get(id);
            Story story = existingStory != null ? existingStory : new Story("Loading...", id, false, false);
            if (commentIds.contains(id)) {
                story.isComment = true;
            }
            refreshedStories.add(story);
        }

        queue.cancelAll(requestTag);
        clearLoadingStoryState();
        userItemListStories.clear();
        userItemListStories.addAll(refreshedStories);
        userItemListCommentIds = new HashSet<>(commentIds);
        replaceStories(getFilteredSavedItemStories(), true);
        loadInitialVisibleStories();
        updateHeader();
    }

    private ArrayList<Story> getFilteredSavedItemStories() {
        ArrayList<Story> filteredStories = new ArrayList<>();
        ArrayList<Story> sourceStories = isBookmarksType(adapter.type) ? bookmarkStories : userItemListStories;
        for (Story story : sourceStories) {
            if (shouldShowStoryForSavedItemFilter(story)) {
                filteredStories.add(story);
            }
        }
        return filteredStories;
    }

    private boolean shouldShowStoryForSavedItemFilter(Story story) {
        if (isBookmarksType(adapter.type) && !story.loaded) {
            return true;
        }
        if (userItemListFilter == USER_ITEM_LIST_FILTER_STORIES) {
            return !story.isComment;
        }
        if (userItemListFilter == USER_ITEM_LIST_FILTER_COMMENTS) {
            return story.isComment;
        }
        return true;
    }

    private void applySavedItemFilter(boolean notifyDataSetChanged) {
        replaceStories(getFilteredSavedItemStories(), notifyDataSetChanged);
        loadInitialVisibleStories();
        updateHeader();
    }

    private void loadInitialVisibleStories() {
        loadInitialVisibleStories(storyListGeneration);
    }

    private void loadInitialVisibleStories(int loadGeneration) {
        int targetIndex = Math.min(getInitialLoadCount(), stories.size()) - 1;
        beginPreviewImagePrefetchRamp(targetIndex);
        loadStoriesThroughIndex(targetIndex, loadGeneration);
        retryUnsettledStoriesThroughIndex(targetIndex, loadGeneration);
    }

    private void beginPreviewImagePrefetchRamp(int targetIndex) {
        if (targetIndex < 0
                || adapter == null
                || SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(adapter.previewImageMode)
                || previewImagePrefetchRampComplete) {
            return;
        }

        previewImagePrefetchRampTargetIndex = Math.max(previewImagePrefetchRampTargetIndex, targetIndex);
    }

    private void requestPreviewImagePrefetch(Context context, Story story) {
        if (context == null || adapter == null || story == null || !story.loaded || story.loadingFailed) {
            return;
        }

        if (previewImagePrefetchRampComplete || previewImagePrefetchRampTargetIndex < 0) {
            adapter.prefetchPreviewImage(context, story);
            return;
        }

        if (story.id > 0) {
            if (requestedPreviewImagePrefetchStoryIds.contains(story.id)
                    || !queuedPreviewImagePrefetchStoryIds.add(story.id)) {
                return;
            }
        }

        previewImagePrefetchQueue.add(story);
        drainPreviewImagePrefetchQueue();
    }

    private void drainPreviewImagePrefetchQueue() {
        if (previewImagePrefetchRampScheduled) {
            return;
        }

        Context context = getContext();
        if (context == null || adapter == null) {
            return;
        }

        while (previewImagePrefetchRampSlotsRemaining > 0 && !previewImagePrefetchQueue.isEmpty()) {
            Story story = removeNextPreviewImagePrefetchStory();
            if (story == null) {
                break;
            }

            if (story.id > 0) {
                requestedPreviewImagePrefetchStoryIds.add(story.id);
            }
            previewImagePrefetchRampSlotsRemaining--;
            adapter.prefetchPreviewImage(context, story);
        }

        updatePreviewImagePrefetchRampCompletion();
        if (!previewImagePrefetchRampComplete && previewImagePrefetchRampSlotsRemaining <= 0) {
            scheduleNextPreviewImagePrefetchRampBatch();
        }
    }

    @Nullable
    private Story removeNextPreviewImagePrefetchStory() {
        int bestQueueIndex = -1;
        int bestStoryIndex = Integer.MAX_VALUE;
        for (int i = 0; i < previewImagePrefetchQueue.size(); i++) {
            Story story = previewImagePrefetchQueue.get(i);
            int storyIndex = stories == null ? -1 : stories.indexOf(story);
            if (storyIndex < 0 || !story.loaded || story.loadingFailed) {
                previewImagePrefetchQueue.remove(i);
                if (story.id > 0) {
                    queuedPreviewImagePrefetchStoryIds.remove(story.id);
                }
                i--;
                continue;
            }

            if (storyIndex < bestStoryIndex) {
                bestStoryIndex = storyIndex;
                bestQueueIndex = i;
            }
        }

        if (bestQueueIndex < 0) {
            return null;
        }

        Story story = previewImagePrefetchQueue.remove(bestQueueIndex);
        if (story.id > 0) {
            queuedPreviewImagePrefetchStoryIds.remove(story.id);
        }
        return story;
    }

    private void scheduleNextPreviewImagePrefetchRampBatch() {
        if (previewImagePrefetchRampScheduled) {
            return;
        }

        previewImagePrefetchRampScheduled = true;
        previewImagePrefetchHandler.postDelayed(
                previewImagePrefetchRampRunnable,
                PREVIEW_IMAGE_PREFETCH_RAMP_DELAY_MS);
    }

    private void updatePreviewImagePrefetchRampCompletion() {
        if (previewImagePrefetchRampComplete
                || previewImagePrefetchRampTargetIndex < 0
                || !previewImagePrefetchQueue.isEmpty()
                || !arePreviewImagePrefetchRampStoriesSettled()) {
            return;
        }

        previewImagePrefetchRampComplete = true;
        previewImagePrefetchRampTargetIndex = -1;
        previewImagePrefetchHandler.removeCallbacks(previewImagePrefetchRampRunnable);
        previewImagePrefetchRampScheduled = false;
        queuedPreviewImagePrefetchStoryIds.clear();
        requestedPreviewImagePrefetchStoryIds.clear();
    }

    private boolean arePreviewImagePrefetchRampStoriesSettled() {
        if (stories == null || stories.isEmpty()) {
            return true;
        }

        int targetIndex = Math.min(previewImagePrefetchRampTargetIndex, stories.size() - 1);
        for (int i = 0; i <= targetIndex; i++) {
            Story story = stories.get(i);
            if (!story.loaded && !story.loadingFailed) {
                return false;
            }
        }
        return true;
    }

    private void resetPreviewImagePrefetchRamp() {
        previewImagePrefetchHandler.removeCallbacks(previewImagePrefetchRampRunnable);
        previewImagePrefetchQueue.clear();
        queuedPreviewImagePrefetchStoryIds.clear();
        requestedPreviewImagePrefetchStoryIds.clear();
        previewImagePrefetchRampScheduled = false;
        previewImagePrefetchRampComplete = false;
        previewImagePrefetchRampSlotsRemaining = PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE;
        previewImagePrefetchRampTargetIndex = -1;
    }

    private void scheduleLoadedPreviewImagePrefetchNearViewport() {
        prefetchLoadedPreviewImagesNearViewport(NO_POSITION, NO_POSITION);
    }

    private void prefetchLoadedPreviewImagesNearViewport(int firstVisibleItem, int lastVisibleItem) {
        Context context = getContext();
        if (context == null
                || adapter == null
                || stories == null
                || stories.isEmpty()
                || SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(adapter.previewImageMode)) {
            return;
        }

        int firstIndex = firstVisibleItem == NO_POSITION ? 0 : Math.max(0, firstVisibleItem);
        int lastIndex = lastVisibleItem == NO_POSITION
                ? Math.min(getInitialLoadCount() - 1, stories.size() - 1)
                : Math.min(lastVisibleItem + STORY_VISIBLE_PREFETCH_THRESHOLD, stories.size() - 1);
        if (adapter.paginationMode) {
            lastIndex = Math.min(lastIndex, adapter.visibleStoryCount - 1);
        }

        if (lastIndex < firstIndex) {
            return;
        }

        beginPreviewImagePrefetchRamp(lastIndex);
        for (int i = firstIndex; i <= lastIndex; i++) {
            requestPreviewImagePrefetch(context, stories.get(i));
        }
    }

    private void updateSearchStatus() {
        hideUpdateButton();
        boolean restoredStories = false;
        boolean shouldRefreshAfterRestore = false;

        if (getActivity() != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setSearchBackEnabled(searching);
        }

        if (searching) {
            useMainStoryList();
            saveStoriesBeforeSearch();

            // cancel all ongoing
            storyListGeneration++;
            clearLoadingStoryState();
            resetPreviewImagePrefetchRamp();
            invalidateAlgoliaLoad();
            queue.cancelAll(requestTag);
            setRefreshIndicatorShowing(false);
            loadingFailed = false;
            loadingFailedServerError = false;
            loadingFailedRateLimited = false;
            useSearchStoryList();
            searchAdapter.type = mainAdapter.type;
            updateAdapterCommentRows();
            clearStoriesForSearchEntry();
        } else {
            shouldRefreshAfterRestore = loadPendingBeforeSearch
                    && storiesBeforeSearch != null
                    && storiesBeforeSearch.isEmpty();
            loadPendingBeforeSearch = false;

            storyListGeneration++;
            clearLoadingStoryState();
            resetPreviewImagePrefetchRamp();
            invalidateAlgoliaLoad();
            queue.cancelAll(requestTag);
            setRefreshIndicatorShowing(false);
            useMainStoryList();
            restoredStories = restoreStoriesBeforeSearch();

            if (!restoredStories) {
                clearStories();
            }
            useSearchStoryList();
            clearStories();
            useMainStoryList();
        }

        updateHeader(true);

        if (!searching) {
            if (restoredStories) {
                if (shouldRefreshAfterRestore) {
                    attemptRefresh();
                } else {
                    resumeInterruptedStoryLoads();
                }
            } else {
                attemptRefresh();
            }
            refreshBookmarksIfNeeded();
        }
    }

    private int getCurrentAlgoliaTopStoriesStartTime() {
        return searchController.getCurrentTopStoriesStartTime(getCurrentStoryType());
    }

    private void loadTopStoriesSince(int start_i, boolean showSwipeRefreshIndicator) {
        lastAlgoliaTopStoriesStartTime = start_i;
        loadAlgolia(searchController.buildTopStoriesUrl(start_i, algoliaHitsPerPage), showSwipeRefreshIndicator);
    }

    private void search(String query) {
        search(query, true);
    }

    private void search(String query, boolean resetResultLimit) {
        lastSearch = query;
        if (resetResultLimit) {
            resetAlgoliaResultLimit();
        }

        if (searchController.isOnlyClicked()) {
            loadOnlyClickedSearch(query);
            return;
        }

        loadAlgolia(searchController.buildSearchUrl(query, algoliaHitsPerPage));
    }

    private boolean canLoadMoreAlgoliaResults(int rawParsedStoryCount) {
        return searchController.canLoadMoreResults(rawParsedStoryCount, algoliaHitsPerPage);
    }

    private void loadMoreAlgoliaResults() {
        if (algoliaLoading) {
            return;
        }

        if (adapter != null) {
            adapter.setLoadMoreLoading(true);
        }
        algoliaLoadMoreInProgress = true;
        if (adapter != null && adapter.paginationMode) {
            algoliaLoadMoreVisibleStoryCount = adapter.visibleStoryCount + StoryListState.PAGINATION_PAGE_SIZE;
        } else {
            algoliaLoadMoreVisibleStoryCount = -1;
        }
        algoliaHitsPerPage += StorySearchController.ALGOLIA_HITS_INCREMENT;
        if (searching) {
            search(lastSearch, false);
        } else if (currentTypeIsAlgolia()) {
            int startTime = lastAlgoliaTopStoriesStartTime > 0
                    ? lastAlgoliaTopStoriesStartTime
                    : getCurrentAlgoliaTopStoriesStartTime();
            loadTopStoriesSince(startTime, false);
        }
    }

    private void loadOnlyClickedSearch(String query) {
        storyListGeneration++;
        clearLoadingStoryState();
        resetPreviewImagePrefetchRamp();
        invalidateAlgoliaLoad();
        final int requestGeneration = algoliaRequestGeneration;
        algoliaLoading = true;
        activeAlgoliaUrl = null;
        loadingFailed = false;
        loadingFailedServerError = false;
        loadingFailedRateLimited = false;
        showingCached = false;
        queue.cancelAll(requestTag);
        clearLoadingStoryState();

        if (!stories.isEmpty()) {
            clearStories();
        }

        List<History> histories = HistoriesUtils.INSTANCE.loadHistories(requireContext(), true);
        if (histories.isEmpty()) {
            completeOnlyClickedSearch(requestGeneration, new ArrayList<>(), 0, 0);
            return;
        }

        String normalizedQuery = searchController.normalizeQuery(query);
        List<Story> matchedStories = new ArrayList<>(histories.size());
        for (int i = 0; i < histories.size(); i++) {
            matchedStories.add(null);
        }

        final int[] pendingRequests = new int[]{histories.size()};
        final int[] failedRequests = new int[]{0};

        for (int i = 0; i < histories.size(); i++) {
            History history = histories.get(i);
            final int storyIndex = i;
            Story story = new Story("Loading...", history.getId(), false, false);
            String url = "https://hacker-news.firebaseio.com/v0/item/" + history.getId() + ".json";
            StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                    response -> {
                        if (requestGeneration != algoliaRequestGeneration) {
                            return;
                        }

                        try {
                            if (JSONParser.updateStoryWithHNJson(response, story, false)
                                    && searchController.shouldIncludeOnlyClickedStory(story, normalizedQuery, thisStory -> shouldFilterLoadedStory(thisStory))) {
                                matchedStories.set(storyIndex, story);
                            }
                        } catch (JSONException e) {
                            failedRequests[0]++;
                            e.printStackTrace();
                        }

                        finishOnlyClickedSearchRequest(requestGeneration, pendingRequests, failedRequests, matchedStories);
                    }, error -> {
                if (requestGeneration != algoliaRequestGeneration) {
                    return;
                }

                failedRequests[0]++;
                error.printStackTrace();
                finishOnlyClickedSearchRequest(requestGeneration, pendingRequests, failedRequests, matchedStories);
            });

            stringRequest.setShouldCache(false);
            stringRequest.setTag(requestTag);
            queue.add(stringRequest);
        }

        updateHeader();
    }

    private void finishOnlyClickedSearchRequest(int requestGeneration,
                                                int[] pendingRequests,
                                                int[] failedRequests,
                                                List<Story> matchedStories) {
        pendingRequests[0]--;
        if (pendingRequests[0] > 0 || requestGeneration != algoliaRequestGeneration) {
            return;
        }

        ArrayList<Story> finishedStories = new ArrayList<>();
        for (Story story : matchedStories) {
            if (story != null) {
                finishedStories.add(story);
            }
        }
        searchController.sortOnlyClickedResultsIfNeeded(finishedStories, lastSearch);

        completeOnlyClickedSearch(requestGeneration, finishedStories, failedRequests[0], matchedStories.size());
    }

    private void completeOnlyClickedSearch(int requestGeneration,
                                           List<Story> finishedStories,
                                           int failedRequests,
                                           int totalRequests) {
        if (requestGeneration != algoliaRequestGeneration) {
            return;
        }

        algoliaLoading = false;
        activeAlgoliaUrl = null;
        setRefreshIndicatorShowing(false);
        loadingFailed = totalRequests > 0 && failedRequests == totalRequests;
        loadingFailedServerError = false;
        loadingFailedRateLimited = false;
        replaceStories(finishedStories);
        loadedTo = stories.size() - 1;
        scheduleLoadedPreviewImagePrefetchNearViewport();
        updateHeader();
    }

    private void loadAlgolia(String url) {
        loadAlgolia(url, false);
    }

    private void loadAlgolia(String url, boolean showSwipeRefreshIndicator) {
        if (algoliaLoading && TextUtils.equals(activeAlgoliaUrl, url)) {
            return;
        }

        invalidateAlgoliaLoad();
        final int requestGeneration = algoliaRequestGeneration;
        algoliaLoading = true;
        activeAlgoliaUrl = url;
        loadingFailed = false;
        loadingFailedServerError = false;
        loadingFailedRateLimited = false;
        queue.cancelAll(requestTag);

        setRefreshIndicatorShowing(!searching && showSwipeRefreshIndicator);
        if (searching && !stories.isEmpty()) {
            clearStories();
        }
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    // Parse JSON on background thread
                    BackgroundJSONParser.parseAlgoliaJson(response, new BackgroundJSONParser.AlgoliaParseCallback() {
                        @Override
                        public void onParseSuccess(List<Story> parsedStories) {
                            if (requestGeneration != algoliaRequestGeneration) {
                                return;
                            }

                            algoliaLoading = false;
                            activeAlgoliaUrl = null;
                            setRefreshIndicatorShowing(false);
                            boolean preservePaginationForLoadMore = algoliaLoadMoreInProgress;
                            int rawParsedStoryCount = parsedStories.size();

                            Iterator<Story> iterator = parsedStories.iterator();
                            while (iterator.hasNext()) {
                                Story story = iterator.next();
                                story.clicked = HistoriesUtils.INSTANCE.isHistoryExist(story.id);
                                boolean shouldRemove = shouldFilterLoadedStory(story);

                                if (!shouldRemove && hideClicked && story.clicked) {
                                    shouldRemove = true;
                                }

                                if (shouldRemove) {
                                    iterator.remove();
                                }
                            }

                            loadingFailed = false;
                            loadingFailedServerError = false;
                            loadingFailedRateLimited = false;
                            showingCached = false;

                            if (preservePaginationForLoadMore) {
                                replaceAlgoliaLoadMoreStories(parsedStories, canLoadMoreAlgoliaResults(rawParsedStoryCount));
                                loadedTo = stories.size() - 1;
                                scheduleLoadedPreviewImagePrefetchNearViewport();
                            } else {
                                replaceStories(parsedStories, false, canLoadMoreAlgoliaResults(rawParsedStoryCount));
                                loadedTo = stories.size() - 1;
                                scheduleLoadedPreviewImagePrefetchNearViewport();
                            }
                            algoliaLoadMoreInProgress = false;
                            adapter.setLoadMoreLoading(false);
                            algoliaLoadMoreVisibleStoryCount = -1;
                            updateHeader();
                        }

                        @Override
                        public void onParseError(JSONException error) {
                            if (requestGeneration != algoliaRequestGeneration) {
                                return;
                            }

                            algoliaLoading = false;
                            activeAlgoliaUrl = null;
                            algoliaLoadMoreInProgress = false;
                            if (adapter != null) {
                                adapter.setLoadMoreLoading(false);
                            }
                            algoliaLoadMoreVisibleStoryCount = -1;
                            setRefreshIndicatorShowing(false);
                            error.printStackTrace();
                        }
                    });

                }, error -> {
            if (requestGeneration != algoliaRequestGeneration) {
                return;
            }

            algoliaLoading = false;
            activeAlgoliaUrl = null;
            algoliaLoadMoreInProgress = false;
            if (adapter != null) {
                adapter.setLoadMoreLoading(false);
            }
            algoliaLoadMoreVisibleStoryCount = -1;

            if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                loadingFailedServerError = true;
            }
            loadingFailedRateLimited = isRateLimitedError(error);

            error.printStackTrace();
            setRefreshIndicatorShowing(false);
            loadingFailed = true;
            updateHeader();
        });

        updateHeader();

        stringRequest.setShouldCache(false);
        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private void registerStoryAdapterDataObservers() {
        if (mainAdapter != null) mainAdapter.setChangedListener(this::onStoryListStateChanged);
        if (searchAdapter != null) searchAdapter.setChangedListener(this::onStoryListStateChanged);
    }

    private void unregisterStoryAdapterDataObservers() {
        if (mainAdapter != null) mainAdapter.setChangedListener(null);
        if (searchAdapter != null) searchAdapter.setChangedListener(null);
    }

    private void onStoryListStateChanged() {
        syncComposeState();
    }

    public boolean currentTypeIsAlgolia() {
        return getCurrentStoryType().isAlgolia();
    }

    private boolean currentTypeIsActive() {
        return getCurrentStoryType().isActive();
    }

    private boolean currentTypeIsFront() {
        return getCurrentStoryType().isFront();
    }

    private boolean currentTypeIsScrapedFrontpage() {
        return getCurrentStoryType().isScrapedFrontpage();
    }

    private boolean isBookmarksType(int type) {
        return getStoryType(type).isBookmarks();
    }

    private boolean isHistoryType(int type) {
        return getStoryType(type).isHistory();
    }

    private boolean isFavoritesType(int type) {
        return getStoryType(type).isFavorites();
    }

    private boolean isUpvotedType(int type) {
        return getStoryType(type).isUpvoted();
    }

    private boolean isUserItemListType(int type) {
        return getStoryType(type).isUserItemList();
    }

    private boolean currentTypeUsesSavedItemFilter() {
        return getCurrentStoryType().usesSavedItemFilter();
    }

    private boolean currentSavedItemSourceHasItems() {
        if (isBookmarksType(adapter.type)) {
            return !bookmarkStories.isEmpty();
        }
        if (isUserItemListType(adapter.type)) {
            return !userItemListStories.isEmpty();
        }
        return false;
    }

    private boolean isSameUserItemListType(int type, boolean upvotedType) {
        return upvotedType ? isUpvotedType(type) : isFavoritesType(type);
    }

    private UserItemListRepository.Source getCurrentUserItemListSource() {
        return isUpvotedType(adapter.type)
                ? UserItemListRepository.Source.UPVOTED
                : UserItemListRepository.Source.FAVORITES;
    }

    private boolean currentTypeUsesCommentRows() {
        return getCurrentStoryType().usesCommentRows();
    }

    private StoryType getCurrentStoryType() {
        return getStoryType(adapter.type);
    }

    private StoryType getStoryType(int type) {
        return StoryType.fromLabel(getTypeLabel(type));
    }

    @Nullable
    private CharSequence getTypeLabel(int type) {
        if (type < 0) {
            return null;
        }

        Context ctx = getContext();
        if (ctx == null) {
            return null;
        }

        ArrayList<CharSequence> typeAdapterList = buildTypeAdapterList(ctx);
        return type < typeAdapterList.size() ? typeAdapterList.get(type) : null;
    }

    private int getTypeIndex(@Nullable CharSequence label) {
        if (label == null || getContext() == null) {
            return -1;
        }

        ArrayList<CharSequence> typeLabels = buildTypeAdapterList(requireContext());
        for (int i = 0; i < typeLabels.size(); i++) {
            if (TextUtils.equals(label, typeLabels.get(i))) {
                return i;
            }
        }

        return -1;
    }

    private void updateAdapterCommentRows() {
        updateAdapterCommentRows(mainAdapter);
        updateAdapterCommentRows(searchAdapter);
    }

    private void updateAdapterCommentRows(@Nullable StoryListState targetAdapter) {
        if (targetAdapter == null) {
            return;
        }

        targetAdapter.allowCommentRows = getStoryType(targetAdapter.type).usesCommentRows();
        targetAdapter.disableClickedEffects = targetAdapter.allowCommentRows || isHistoryType(targetAdapter.type);
        updateAdapterPaginationMode(targetAdapter);
    }

    private boolean shouldHideStoryAsJob(Story story) {
        return hideJobs
                && getCurrentStoryType() != StoryType.HN_JOBS
                && (story.isJob
                || "whoishiring".equals(story.by));
    }

    public boolean exitSearch() {
        if (searching) {
            closeSearch();
            return true;
        }
        return false;
    }

    public void startSearchBackProgress(float progress) {
        if (!searching) {
            return;
        }
        predictiveSearchBackInProgress = true;
        predictiveSearchBackProgress = Math.max(0f, Math.min(1f, progress));
        if (composeController != null) {
            composeController.beginPredictiveBack(predictiveSearchBackProgress);
        }
    }

    public void updateSearchBackProgress(float progress) {
        if (!searching) {
            return;
        }

        if (!predictiveSearchBackInProgress) {
            startSearchBackProgress(progress);
            return;
        }

        predictiveSearchBackProgress = Math.max(0f, Math.min(1f, progress));
        if (composeController != null) {
            composeController.updatePredictiveBack(predictiveSearchBackProgress);
        }
    }

    public void cancelSearchBackProgress() {
        if (!predictiveSearchBackInProgress) {
            return;
        }

        finishSearchBackFromCurrentVisualState = false;
        predictiveSearchBackInProgress = false;
        predictiveSearchBackProgress = 0f;
        useSearchStoryList();
        if (composeController != null) {
            composeController.cancelPredictiveBack();
        }
        updateHeader(false);
    }

    public boolean finishSearchBackProgress() {
        if (!searching) {
            finishSearchBackFromCurrentVisualState = false;
            return false;
        }

        finishSearchBackFromCurrentVisualState = predictiveSearchBackInProgress;
        predictiveSearchBackInProgress = false;
        if (!finishSearchBackFromCurrentVisualState) {
            return exitSearch();
        }
        if (composeController != null) {
            composeController.commitPredictiveBack();
            finishSearchBackFromCurrentVisualState = false;
            return true;
        }
        return exitSearch();
    }

    private void showCachedStories() {
        showingCached = true;
        setRefreshIndicatorShowing(false);

        replaceStories(Utils.loadCachedStories(getContext()));
        loadedTo = stories.size() - 1;
        loadingFailed = false;
        loadingFailedServerError = false;
        loadingFailedRateLimited = false;
        updateHeader();
    }

    private boolean isRefreshIndicatorShowing() {
        return refreshIndicatorShowing;
    }

    private void setRefreshIndicatorShowing(boolean showing) {
        boolean changed = refreshIndicatorShowing != showing;
        refreshIndicatorShowing = showing;
        if (changed && composeController != null) {
            syncComposeState();
        }
    }

    private void hideUpdateButton() {
        updateButtonShowing = false;
        syncComposeState();
    }

    private void showUpdateButton() {
        updateButtonShowing = true;
        syncComposeState();
    }

    private void openComments(Story story, int pos, boolean showWebsite) {
        storyClickListener.openStory(story, pos, showWebsite);
    }

    public interface StoryClickListener {
        void openStory(Story story, int pos, boolean showWebsite);
    }
}
