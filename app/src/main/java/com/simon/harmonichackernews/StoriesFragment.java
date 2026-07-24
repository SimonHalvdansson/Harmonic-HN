package com.simon.harmonichackernews;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.TooltipCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.search.SearchBar;
import com.simon.harmonichackernews.adapters.StoryDisplaySettings;
import com.simon.harmonichackernews.adapters.StoryRecyclerViewAdapter;
import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.data.History;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.databinding.FragmentStoriesBinding;
import com.simon.harmonichackernews.databinding.StoriesHeaderBinding;
import com.simon.harmonichackernews.network.BackgroundJSONParser;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.FoldableSplitInitializer;
import com.simon.harmonichackernews.utils.HistoriesUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StatusBarProtectionUtils;
import com.simon.harmonichackernews.utils.StoryUpdate;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final int SWIPE_REFRESH_PROGRESS_START_OFFSET_DP = -32;
    private static final int SWIPE_REFRESH_PROGRESS_END_OFFSET_DP = -64;
    private static final int SWIPE_REFRESH_SLINGSHOT_DISTANCE_OFFSET_DP = -20;
    private static final int UPDATE_FAB_LOAD_MORE_CLEARANCE_DP = 8;
    private static final int NO_PENDING_LINK_SUMMARY_STORY_ID = -1;
    private static final String STATE_LINK_SUMMARY_STORY_ID =
            "com.simon.harmonichackernews.STATE_LINK_SUMMARY_STORY_ID";

    private StoryClickListener storyClickListener;
    private FragmentStoriesBinding binding;
    private StoriesHeaderBinding headerBinding;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ExtendedFloatingActionButton updateFab;
    private RecyclerView mainRecyclerView;
    private RecyclerView searchRecyclerView;
    private RecyclerView recyclerView;
    private AppBarLayout appBarLayout;
    private AppBarLayout.OnOffsetChangedListener appBarOffsetChangedListener;
    private RecyclerView.OnScrollListener recyclerViewScrollListener;
    private RecyclerView.OnScrollListener searchRecyclerViewScrollListener;
    private RecyclerView.AdapterDataObserver storyAdapterDataObserver;
    private RecyclerView.AdapterDataObserver searchStoryAdapterDataObserver;
    private MaterialButtonToggleGroup.OnButtonCheckedListener userItemFilterCheckedListener;
    private StoryUpdate.StoryUpdateListener storyUpdateListener;
    private final StorySearchController searchController = new StorySearchController();
    private StoriesViewModel storiesViewModel;
    @Nullable
    private StoriesViewModel.State restoredState;
    private boolean restoredStateForCurrentView;
    private StoryCacheController storyCacheController;
    private int systemBottomInset = 0;
    private OnBackPressedCallback linkSummaryBackCallback;
    private int pendingLinkSummaryStoryId = NO_PENDING_LINK_SUMMARY_STORY_ID;
    private final SparseIntArray linkSummaryStoryHeights = new SparseIntArray();
    private final LinkSummaryOverlayController linkSummaryOverlayController =
            new LinkSummaryOverlayController(new LinkSummaryOverlayController.Host() {
                @Nullable
                @Override
                public Context getLinkSummaryContext() {
                    return StoriesFragment.this.getContext();
                }

                @NonNull
                @Override
                public Context requireLinkSummaryContext() {
                    return StoriesFragment.this.requireContext();
                }

                @Nullable
                @Override
                public ViewGroup getLinkSummaryOverlayHost() {
                    return StoriesFragment.this.getLinkSummaryOverlayHost();
                }

                @Nullable
                @Override
                public View findLinkSummarySourceView(int storyId) {
                    return StoriesFragment.this.findLinkSummarySourceView(storyId);
                }

                @Nullable
                @Override
                public LinkSummaryOverlayController.StorySharedElements findLinkSummaryStorySharedElements(
                        int storyId) {
                    return StoriesFragment.this.findLinkSummaryStorySharedElements(storyId);
                }

                @Override
                public int getLinkSummaryStoryCount() {
                    if (stories == null || adapter == null) return 0;
                    return Math.min(stories.size(), adapter.getVisibleStoryItemCount());
                }

                @Nullable
                @Override
                public Story getLinkSummaryStoryAt(int position) {
                    if (stories == null || position < 0 || position >= stories.size()) {
                        return null;
                    }
                    return stories.get(position);
                }

                @Override
                public int getLinkSummaryStoryPagingDistance(
                        int firstStoryId, int secondStoryId) {
                    return StoriesFragment.this.getLinkSummaryStoryPagingDistance(
                            firstStoryId, secondStoryId);
                }

                @Override
                public void scrollLinkSummaryStoryListBy(int dy) {
                    if (recyclerView != null && dy != 0) {
                        recyclerView.scrollBy(0, dy);
                    }
                }

                @Override
                public void setLinkSummaryStoryPagingAlphas(
                        int firstStoryId, float firstAlpha,
                        int secondStoryId, float secondAlpha) {
                    if (adapter != null) {
                        adapter.setPreviewPagingAlphas(
                                firstStoryId, firstAlpha, secondStoryId, secondAlpha);
                    }
                }

                @Override
                public void clearLinkSummaryStoryPagingAlphas(boolean animate) {
                    if (mainAdapter != null) {
                        mainAdapter.clearPreviewPagingAlphas(animate);
                    }
                    if (searchAdapter != null) {
                        searchAdapter.clearPreviewPagingAlphas(animate);
                    }
                }

                @Override
                public int resolveStoryCardBackgroundColor(Story story) {
                    Context context = StoriesFragment.this.getContext();
                    if (adapter != null) {
                        return adapter.resolveStoryCardBackgroundColor(context, story);
                    }
                    return PreviewImageTintUtils.getTintBaseColor(context);
                }

                @Override
                public void stopLinkSummaryListScroll() {
                    if (recyclerView != null) {
                        recyclerView.stopScroll();
                    }
                }

                @Override
                public void syncLinkSummaryBackState() {
                    if (linkSummaryBackCallback != null) {
                        linkSummaryBackCallback.setEnabled(linkSummaryOverlayController.isShowing());
                    }
                }

                @Override
                public void openStoryLinkSummary(Story story, int position, boolean showWebsite) {
                    StoriesFragment.this.openStoryFromLinkSummary(story, position, showWebsite);
                }

                @Override
                public void toggleStoryVote(Story story, int position, boolean currentlyUpvoted,
                                            Runnable completion) {
                    StoriesFragment.this.toggleStoryVote(story, position, currentlyUpvoted, completion);
                }

                @Override
                public void toggleStoryRead(Story story, int position) {
                    StoriesFragment.this.toggleStoryRead(story, position);
                }

                @Override
                public void toggleStoryBookmark(Story story, int position, boolean currentlyBookmarked) {
                    StoriesFragment.this.toggleStoryBookmark(story, position, currentlyBookmarked);
                }

                @Override
                public void toggleStoryFavorite(Story story, int position, boolean currentlyFavorited,
                                                Runnable completion) {
                    StoriesFragment.this.toggleStoryFavorite(story, position, currentlyFavorited, completion);
                }
            });

    // Header views
    private LinearLayout headerContainer;
    private Spinner typeSpinner;
    private LinearLayout spinnerContainer;
    private View searchContainer;
    private SearchBar searchBar;
    private EditText searchEditText;
    private View searchOptionsScroll;
    private int headerBasePaddingStart;
    private int headerBasePaddingEnd;
    private Chip searchSortChip;
    private Chip searchDateChip;
    private Chip searchPointsChip;
    private Chip searchCommentsChip;
    private Chip searchOnlyClickedChip;
    private ImageButton searchButton;
    private ImageButton closeSearchButton;
    private ImageButton moreButton;
    private TextView lastUpdatedHeaderText;
    private TextView cacheProgressStatusText;
    private LinearProgressIndicator cacheProgressIndicator;
    private MaterialButtonToggleGroup userItemFilterGroup;
    private RelativeLayout loadingIndicator;
    private LinearLayout loadingFailedLayout;
    private TextView loadingFailedText;
    private TextView loadingFailedAlgoliaLayout;
    private LinearLayout noBookmarksLayout;
    private ImageView noBookmarksImage;
    private TextView noBookmarksText;
    private LinearLayout searchEmptyContainer;
    private Button retryButton;
    private Button showCachedButton;
    private LinearLayout frontPageDateControls;
    private Button frontPagePreviousDayButton;
    private Button frontPageDateButton;
    private Button frontPageNextDayButton;

    private StoryRecyclerViewAdapter mainAdapter;
    private StoryRecyclerViewAdapter searchAdapter;
    private StoryRecyclerViewAdapter adapter;
    private StoryTypeSpinnerAdapter typeSpinnerAdapter;
    private final ArrayList<Story> mainStories = new ArrayList<>();
    private final ArrayList<Story> searchStories = new ArrayList<>();
    private List<Story> stories;
    private final ArrayList<Story> bookmarkStories = new ArrayList<>();
    private final ArrayList<Story> userItemListStories = new ArrayList<>();
    private Set<Integer> userItemListCommentIds = new HashSet<>();
    private RequestQueue queue;
    private final Object requestTag = new Object();
    private final Map<Integer, Long> loadingStoryStartTimes = new HashMap<>();
    private LinearLayoutManager mainLinearLayoutManager;
    private LinearLayoutManager searchLinearLayoutManager;
    private LinearLayoutManager linearLayoutManager;
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
    private boolean algoliaLoading = false;
    private String activeAlgoliaUrl = null;
    private boolean algoliaLoadMoreInProgress = false;
    private int algoliaLoadMoreVisibleStoryCount = -1;
    private int algoliaLoadMoreFirstVisiblePosition = RecyclerView.NO_POSITION;
    private int algoliaLoadMoreFirstVisibleTop = 0;
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
    private int firstVisiblePositionBeforeSearch = RecyclerView.NO_POSITION;
    private int firstVisibleTopBeforeSearch = 0;

    private boolean showingCached = false;

    private int loadedTo = -1;
    private boolean paginationMode = false;
    private final Set<Integer> paginationLoadMoreStoryIds = new HashSet<>();
    private int paginationLoadMoreGeneration = -1;
    private static final int STORY_VISIBLE_PREFETCH_THRESHOLD = 17;
    private static final long STORY_LOAD_STALE_TIMEOUT_MS = 30_000L;
    private static final int PREVIEW_IMAGE_PREFETCH_RAMP_BATCH_SIZE = 10;
    private static final long PREVIEW_IMAGE_PREFETCH_RAMP_DELAY_MS = 450L;
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
    private static final long SEARCH_HEADER_ANIMATION_DURATION_MS = 180;
    private static final long SEARCH_OPTIONS_ENTRANCE_ANIMATION_DELAY_MS = 100;
    private static final long SEARCH_OPTIONS_ENTRANCE_ANIMATION_DURATION_MS = 140;
    private static final long SEARCH_CONTENT_EXIT_ANIMATION_DURATION_MS = 140;
    private static final long SEARCH_CONTENT_RETURN_ANIMATION_DURATION_MS = 180;
    private static final long HEADER_LAYOUT_ANIMATION_DURATION_MS = 220;
    private static final float SEARCH_BACK_HEADER_SWITCH_PROGRESS = 0.5f;
    private static final float SEARCH_BACK_CONTENT_TRANSLATION_DP = 24f;
    private static final int USER_ITEM_LIST_FILTER_STORIES = 0;
    private static final int USER_ITEM_LIST_FILTER_BOTH = 1;
    private static final int USER_ITEM_LIST_FILTER_COMMENTS = 2;

    private int topInset = 0;
    private int appBarOffset = 0;
    private boolean predictiveSearchBackInProgress = false;
    private boolean predictiveSearchBackShowingMainHeader = false;
    private float predictiveSearchBackProgress = 0f;
    private boolean suppressNextSearchRestoreAnimations = false;
    private boolean skipNextSearchRestoreDataSwap = false;
    private boolean finishSearchBackFromCurrentVisualState = false;
    private boolean userItemListsDropdownVisible = false;
    private boolean userItemListInitialLoadInProgress = false;
    private int userItemListFilter = USER_ITEM_LIST_FILTER_BOTH;
    private Calendar frontPageDayUtc;
    @Nullable
    private String scrapedFrontpageNextPageUrl;
    private boolean scrapedFrontpageNextPageLoading = false;
    private StoryType scrapedFrontpageStoryType = StoryType.UNKNOWN;
    private RecyclerView.ItemAnimator defaultMainStoryItemAnimator;
    private RecyclerView.ItemAnimator defaultSearchStoryItemAnimator;
    private RecyclerView.ItemAnimator defaultStoryItemAnimator;
    private boolean searchContentExitAnimationRunning = false;
    private int searchContentExitAnimationGeneration = 0;
    private boolean deferSearchRecyclerClearForReturnAnimation = false;
    private boolean skipNextSearchContentReturnAnimation = false;

    public StoriesFragment() {
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        storiesViewModel = new ViewModelProvider(this).get(StoriesViewModel.class);
        bookmarkPreferences = requireContext().getSharedPreferences(
                Utils.GLOBAL_SHARED_PREFERENCES_KEY,
                Context.MODE_PRIVATE);
        bookmarkPreferences.registerOnSharedPreferenceChangeListener(
                bookmarkPreferenceChangeListener);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        HistoriesUtils.INSTANCE.init(requireContext());
        historiesChangeVersion = HistoriesUtils.INSTANCE.getChangeVersion();
        restoredState = storiesViewModel == null ? null : storiesViewModel.getState();

        return FragmentStoriesBinding.inflate(inflater, container, false).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) {
            pendingLinkSummaryStoryId = savedInstanceState.getInt(
                    STATE_LINK_SUMMARY_STORY_ID, NO_PENDING_LINK_SUMMARY_STORY_ID);
        }
        binding = FragmentStoriesBinding.bind(view);
        headerBinding = binding.storiesHeaderContainer;
        applyStatusBarProtection();
        setupLinkSummaryBackCallback();

        mainRecyclerView = binding.storiesRecyclerview;
        searchRecyclerView = binding.storiesSearchRecyclerview;
        recyclerView = mainRecyclerView;
        swipeRefreshLayout = binding.storiesSwipeRefresh;
        updateFab = binding.storiesUpdateFab;
        appBarLayout = binding.storiesAppbar;
        appBarOffsetChangedListener = (appBar, verticalOffset) -> {
            appBarOffset = verticalOffset;
            float totalScrollRange = appBar.getTotalScrollRange();
            if (totalScrollRange > 0) {
                headerContainer.setAlpha(1f - (Math.abs(verticalOffset) / totalScrollRange));
            }
        };
        appBarLayout.addOnOffsetChangedListener(appBarOffsetChangedListener);
        configureAppBarDragBehavior();

        // Bind header views
        headerContainer = headerBinding.storiesHeaderContainer;
        headerBasePaddingStart = headerContainer.getPaddingStart();
        headerBasePaddingEnd = headerContainer.getPaddingEnd();
        typeSpinner = headerBinding.storiesHeaderSpinner;
        spinnerContainer = headerBinding.storiesHeaderSpinnerContainer;
        searchContainer = headerBinding.storiesHeaderSearchContainer;
        searchBar = headerBinding.storiesHeaderSearchBar;
        searchEditText = headerBinding.storiesHeaderSearchEdittext;
        searchOptionsScroll = headerBinding.storiesHeaderSearchOptionsScroll;
        searchSortChip = headerBinding.storiesHeaderSearchSortChip;
        searchDateChip = headerBinding.storiesHeaderSearchDateChip;
        searchPointsChip = headerBinding.storiesHeaderSearchPointsChip;
        searchCommentsChip = headerBinding.storiesHeaderSearchCommentsChip;
        searchOnlyClickedChip = headerBinding.storiesHeaderSearchOnlyClickedChip;
        searchBar.setElevation(0f);
        searchEditText.bringToFront();
        searchButton = headerBinding.storiesHeaderSearchButton;
        closeSearchButton = headerBinding.storiesHeaderCloseSearchButton;
        moreButton = headerBinding.storiesHeaderMore;
        lastUpdatedHeaderText = headerBinding.storiesHeaderLastUpdated;
        cacheProgressStatusText = headerBinding.storiesHeaderCacheStatus;
        cacheProgressIndicator = headerBinding.storiesHeaderCacheProgress;
        frontPageDateControls = headerBinding.storiesHeaderFrontDateControls;
        frontPagePreviousDayButton = headerBinding.storiesHeaderFrontPreviousDay;
        frontPageDateButton = headerBinding.storiesHeaderFrontDate;
        frontPageNextDayButton = headerBinding.storiesHeaderFrontNextDay;
        storyCacheController = new StoryCacheController(new StoryCacheController.Callbacks() {
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
            public void beginHeaderTransition() {
                StoriesFragment.this.beginHeaderTransition(false);
            }
        });
        storyCacheController.bindViews(cacheProgressIndicator, cacheProgressStatusText);
        getParentFragmentManager().setFragmentResultListener(
                CacheStoriesDialogFragment.RESULT_KEY,
                getViewLifecycleOwner(),
                (requestKey, result) -> {
                    if (storyCacheController != null) {
                        storyCacheController.cacheStories();
                    }
                });
        userItemFilterGroup = headerBinding.storiesHeaderUserItemFilterGroup;
        loadingIndicator = headerBinding.storiesHeaderLoadingIndicator;
        loadingFailedLayout = headerBinding.storiesHeaderLoadingFailed;
        loadingFailedText = headerBinding.storiesHeaderLoadingFailedText;
        loadingFailedAlgoliaLayout = headerBinding.storiesHeaderLoadingFailedAlgolia;
        noBookmarksLayout = headerBinding.storiesHeaderNoBookmarks;
        noBookmarksImage = headerBinding.storiesHeaderNoBookmarksIcon;
        noBookmarksText = headerBinding.storiesHeaderNoBookmarksText;
        searchEmptyContainer = headerBinding.storiesHeaderSearchEmptyContainer;
        retryButton = headerBinding.storiesHeaderRetryButton;
        showCachedButton = headerBinding.storiesHeaderShowCached;

        swipeRefreshLayout.setOnRefreshListener(() -> attemptRefresh(true));
        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) ->
                recyclerView != null && recyclerView.canScrollVertically(-1));
        ViewUtils.setUpSwipeRefreshWithStatusBarOffset(swipeRefreshLayout,
                Utils.pxFromDpInt(getResources(), SWIPE_REFRESH_PROGRESS_START_OFFSET_DP),
                Utils.pxFromDpInt(getResources(), SWIPE_REFRESH_PROGRESS_END_OFFSET_DP),
                Utils.pxFromDpInt(getResources(), SWIPE_REFRESH_SLINGSHOT_DISTANCE_OFFSET_DP));

        mainLinearLayoutManager = createStoryLayoutManager();
        searchLinearLayoutManager = createStoryLayoutManager();
        setupStoryRecyclerView(mainRecyclerView, mainLinearLayoutManager);
        setupStoryRecyclerView(searchRecyclerView, searchLinearLayoutManager);
        defaultMainStoryItemAnimator = mainRecyclerView.getItemAnimator();
        defaultSearchStoryItemAnimator = searchRecyclerView.getItemAnimator();
        configureStoryItemAnimator(defaultMainStoryItemAnimator);
        configureStoryItemAnimator(defaultSearchStoryItemAnimator);
        linearLayoutManager = mainLinearLayoutManager;
        defaultStoryItemAnimator = defaultMainStoryItemAnimator;

        stories = mainStories;
        filterWords = Utils.getFilterWords(requireContext());
        filterDomains = Utils.getFilterDomains(requireContext());
        filteredUsers = Utils.getFilteredUsers(requireContext());
        hideJobs = SettingsUtils.shouldHideJobs(requireContext());
        hideClicked = SettingsUtils.shouldHideClicked(requireContext());
        alwaysOpenComments = SettingsUtils.shouldAlwaysOpenComments(requireContext());
        restoreStoryLists(restoredState);
        setupAdapter();
        mainRecyclerView.setAdapter(mainAdapter);
        searchRecyclerView.setAdapter(searchAdapter);
        registerStoryAdapterDataObservers();
        syncActiveStoryListToSearchState();
        applySearchRecyclerVisibility(false);

        // Setup header after adapter so spinner callback can safely access adapter.type
        setupHeader();
        restoredStateForCurrentView = restoreStoryStateAfterViewSetup(restoredState);

        final int rootPaddingLeft = view.getPaddingLeft();
        final int rootPaddingTop = view.getPaddingTop();
        final int rootPaddingRight = view.getPaddingRight();
        final int rootPaddingBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

                v.setPadding(
                        rootPaddingLeft + Math.max(insets.left, cutoutInsets.left),
                        rootPaddingTop,
                        rootPaddingRight + Math.max(insets.right, cutoutInsets.right),
                        rootPaddingBottom
                );

                CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) updateFab.getLayoutParams();
                params.bottomMargin = insets.bottom + Utils.pxFromDpInt(getResources(), 8);
                updateFab.setLayoutParams(params);


                topInset = insets.top;

                applyHeaderPadding(getContext());

                systemBottomInset = insets.bottom;
                applyStoriesRecyclerPadding();
                requestRecyclerScrollStateUpdate();

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(view);

        updateFab.setOnClickListener((v) -> {
            attemptRefresh();
            recyclerView.smoothScrollToPosition(0);
        });

        recyclerViewScrollListener = createStoryScrollListener(mainAdapter);
        searchRecyclerViewScrollListener = createStoryScrollListener(searchAdapter);
        mainRecyclerView.addOnScrollListener(recyclerViewScrollListener);
        searchRecyclerView.addOnScrollListener(searchRecyclerViewScrollListener);

        queue = NetworkComponent.getRequestQueueInstance(requireContext());
        if (restoredStateForCurrentView) {
            updateHeader();
            restoreRecyclerViewPositions(restoredState);
            if (shouldRefreshRestoredStoryState()) {
                attemptRefresh();
            } else if (!searching) {
                resumeInterruptedStoryLoads();
            }
        } else {
            attemptRefresh();
        }

        storyUpdateListener = new StoryUpdate.StoryUpdateListener() {
            @Override
            public void callback(Story story) {
                if (story == null || stories == null || adapter == null) {
                    return;
                }

                for (int i = 0; i < stories.size(); i++) {
                    Story oldStory = stories.get(i);
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
            }
        };
        StoryUpdate.setStoryUpdatedListener(storyUpdateListener);
        restoreLinkSummaryAfterRecreation(view);
    }

    private void restoreLinkSummaryAfterRecreation(@NonNull View rootView) {
        if (pendingLinkSummaryStoryId == NO_PENDING_LINK_SUMMARY_STORY_ID) {
            return;
        }
        int storyId = pendingLinkSummaryStoryId;
        pendingLinkSummaryStoryId = NO_PENDING_LINK_SUMMARY_STORY_ID;
        rootView.post(() -> {
            if (binding == null || stories == null) {
                return;
            }
            for (int position = 0; position < stories.size(); position++) {
                Story story = stories.get(position);
                if (story.id == storyId
                        && (!story.isLink || !TextUtils.isEmpty(story.url))) {
                    linkSummaryOverlayController.showStory(
                            story,
                            position,
                            resolveLinkSummarySourceView(position, null),
                            resolveLinkSummaryStorySharedElements(position));
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
            firstVisiblePositionBeforeSearch = state.mainFirstVisiblePosition;
            firstVisibleTopBeforeSearch = state.mainFirstVisibleTop;
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
        applySearchRecyclerVisibility(false);
        if (typeSpinner != null) {
            typeSpinner.setSelection(mainAdapter.type);
        }
        if (userItemFilterGroup != null) {
            userItemFilterGroup.check(userItemListFilterButtonId(userItemListFilter));
        }
        updateSearchOptionChips(false);
        return true;
    }

    private int userItemListFilterButtonId(int filter) {
        if (filter == USER_ITEM_LIST_FILTER_STORIES) {
            return R.id.stories_header_user_item_filter_stories;
        }
        if (filter == USER_ITEM_LIST_FILTER_COMMENTS) {
            return R.id.stories_header_user_item_filter_comments;
        }
        return R.id.stories_header_user_item_filter_both;
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

    private void restoreRecyclerViewPositions(@Nullable StoriesViewModel.State state) {
        if (state == null) {
            return;
        }

        restoreRecyclerViewPosition(mainRecyclerView, mainLinearLayoutManager,
                mainAdapter, state.mainFirstVisiblePosition, state.mainFirstVisibleTop);
        restoreRecyclerViewPosition(searchRecyclerView, searchLinearLayoutManager,
                searchAdapter, state.searchFirstVisiblePosition, state.searchFirstVisibleTop);
        if (appBarLayout != null && state.appBarCollapsed) {
            appBarLayout.post(() -> {
                if (appBarLayout != null) {
                    appBarLayout.setExpanded(false, false);
                }
            });
        }
    }

    private void restoreRecyclerViewPosition(@Nullable RecyclerView targetRecyclerView,
                                             @Nullable LinearLayoutManager targetLayoutManager,
                                             @Nullable StoryRecyclerViewAdapter targetAdapter,
                                             int firstVisiblePosition,
                                             int firstVisibleTop) {
        if (targetRecyclerView == null
                || targetLayoutManager == null
                || targetAdapter == null
                || firstVisiblePosition == RecyclerView.NO_POSITION
                || targetAdapter.getItemCount() == 0) {
            return;
        }

        targetRecyclerView.post(() -> {
            if (targetRecyclerView.getLayoutManager() != targetLayoutManager
                    || targetRecyclerView.getAdapter() != targetAdapter) {
                return;
            }
            int position = Math.min(firstVisiblePosition, targetAdapter.getItemCount() - 1);
            targetLayoutManager.scrollToPositionWithOffset(
                    position,
                    firstVisibleTop - targetRecyclerView.getPaddingTop());
        });
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

        state.mainFirstVisiblePosition = getFirstVisiblePosition(mainLinearLayoutManager);
        state.mainFirstVisibleTop = getFirstVisibleTop(mainRecyclerView, mainLinearLayoutManager,
                state.mainFirstVisiblePosition);
        state.searchFirstVisiblePosition = getFirstVisiblePosition(searchLinearLayoutManager);
        state.searchFirstVisibleTop = getFirstVisibleTop(searchRecyclerView, searchLinearLayoutManager,
                state.searchFirstVisiblePosition);
        state.appBarCollapsed = appBarOffset < 0;

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

    private int getFirstVisiblePosition(@Nullable LinearLayoutManager targetLayoutManager) {
        return targetLayoutManager == null
                ? RecyclerView.NO_POSITION
                : targetLayoutManager.findFirstVisibleItemPosition();
    }

    private int getFirstVisibleTop(@Nullable RecyclerView targetRecyclerView,
                                   @Nullable LinearLayoutManager targetLayoutManager,
                                   int firstVisiblePosition) {
        if (targetRecyclerView == null
                || targetLayoutManager == null
                || firstVisiblePosition == RecyclerView.NO_POSITION) {
            return 0;
        }
        View firstVisibleView = targetLayoutManager.findViewByPosition(firstVisiblePosition);
        return firstVisibleView == null ? targetRecyclerView.getPaddingTop() : firstVisibleView.getTop();
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
        if (typeSpinnerAdapter == null || typeSpinner == null || adapter == null || getContext() == null) {
            return;
        }

        CharSequence previousTypeLabel = getTypeLabel(adapter.type);
        boolean showUserItemLists = shouldShowUserItemLists(getContext());
        if (userItemListsDropdownVisible == showUserItemLists) {
            return;
        }

        userItemListsDropdownVisible = showUserItemLists;
        typeSpinnerAdapter.clear();
        typeSpinnerAdapter.addAll(buildTypeAdapterList(getContext()));
        typeSpinnerAdapter.notifyDataSetChanged();

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

        typeSpinner.setSelection(newType);
        if (typeChanged) {
            attemptStoryTypeRefresh();
        }
    }

    private void configureAppBarDragBehavior() {
        CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        AppBarLayout.Behavior behavior;

        if (layoutParams.getBehavior() instanceof AppBarLayout.Behavior) {
            behavior = (AppBarLayout.Behavior) layoutParams.getBehavior();
        } else {
            behavior = new AppBarLayout.Behavior();
            layoutParams.setBehavior(behavior);
            appBarLayout.setLayoutParams(layoutParams);
        }

        behavior.setDragCallback(new AppBarLayout.Behavior.DragCallback() {
            @Override
            public boolean canDrag(@NonNull AppBarLayout appBarLayout) {
                return !shouldLockRecyclerScroll();
            }
        });
    }

    private LinearLayoutManager createStoryLayoutManager() {
        return new LinearLayoutManager(getContext()) {
            @Nullable
            private RecyclerView attachedRecyclerView;
            private boolean staleHolderResetPosted;

            @Override
            public void onAttachedToWindow(RecyclerView view) {
                super.onAttachedToWindow(view);
                attachedRecyclerView = view;
            }

            @Override
            public void onDetachedFromWindow(RecyclerView view, RecyclerView.Recycler recycler) {
                if (attachedRecyclerView == view) {
                    attachedRecyclerView = null;
                }
                staleHolderResetPosted = false;
                super.onDetachedFromWindow(view, recycler);
            }

            @Override
            public boolean canScrollVertically() {
                return !shouldLockRecyclerScroll() && super.canScrollVertically();
            }

            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            public void onLayoutChildren(@NonNull RecyclerView.Recycler recycler,
                                         @NonNull RecyclerView.State state) {
                try {
                    super.onLayoutChildren(recycler, state);
                } catch (IllegalArgumentException exception) {
                    if (!isStaleDetachedStoryHolderException(exception)) {
                        throw exception;
                    }

                    Log.w(TAG, "Recovering from stale detached story holder", exception);
                    removeAndRecycleAllViews(recycler);
                    recycler.clear();
                    // Finish this layout pass normally, then reset the adapter after RecyclerView
                    // has decremented its layout counter. Retrying here can reuse the same holder.
                    postStaleHolderAdapterReset();
                }
            }

            @Override
            public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler,
                                          RecyclerView.State state) {
                try {
                    return super.scrollVerticallyBy(dy, recycler, state);
                } catch (IllegalArgumentException exception) {
                    if (!isStaleDetachedStoryHolderException(exception)) {
                        throw exception;
                    }

                    // RecyclerView increments its layout/scroll counter before asking the layout
                    // manager to fill rows. Recover here so the call can return normally and
                    // RecyclerView gets a chance to decrement that counter.
                    Log.w(TAG, "Recovering from stale detached story holder while scrolling",
                            exception);
                    removeAndRecycleAllViews(recycler);
                    recycler.clear();
                    postStaleHolderAdapterReset();
                    return 0;
                }
            }

            private void postStaleHolderAdapterReset() {
                RecyclerView currentRecyclerView = attachedRecyclerView;
                if (currentRecyclerView == null || staleHolderResetPosted) {
                    return;
                }

                staleHolderResetPosted = true;
                currentRecyclerView.post(() -> {
                    staleHolderResetPosted = false;
                    if (attachedRecyclerView != currentRecyclerView
                            || currentRecyclerView.getLayoutManager() != this) {
                        return;
                    }

                    RecyclerView.Adapter<?> currentAdapter = currentRecyclerView.getAdapter();
                    if (currentAdapter == null) {
                        currentRecyclerView.requestLayout();
                        return;
                    }

                    RecyclerView.ItemAnimator itemAnimator =
                            currentRecyclerView.getItemAnimator();
                    if (itemAnimator != null) {
                        itemAnimator.endAnimations();
                    }
                    currentRecyclerView.stopScroll();
                    currentRecyclerView.setAdapter(null);
                    currentRecyclerView.getRecycledViewPool().clear();
                    currentRecyclerView.setAdapter(currentAdapter);
                });
            }
        };
    }

    private boolean isStaleDetachedStoryHolderException(
            @NonNull IllegalArgumentException exception) {
        String message = exception.getMessage();
        return message != null
                && message.startsWith("Called attach on a child which is not detached");
    }

    private void setupStoryRecyclerView(@NonNull RecyclerView targetRecyclerView,
                                        @NonNull LinearLayoutManager targetLayoutManager) {
        targetRecyclerView.setHasFixedSize(true);
        targetRecyclerView.setLayoutManager(targetLayoutManager);
    }

    private void configureStoryItemAnimator(@Nullable RecyclerView.ItemAnimator itemAnimator) {
        if (itemAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }
    }

    private RecyclerView.OnScrollListener createStoryScrollListener(@NonNull StoryRecyclerViewAdapter sourceAdapter) {
        return new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NotNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (adapter != sourceAdapter || linearLayoutManager == null) {
                    return;
                }

                int firstVisibleItem = linearLayoutManager.findFirstVisibleItemPosition();
                int lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
                prefetchLoadedPreviewImagesNearViewport(firstVisibleItem, lastVisibleItem);

                // Only enable infinite scroll if pagination mode is OFF
                if (!searching && adapter != null && !adapter.paginationMode && !currentTypeIsAlgolia()) {
                    int targetIndex = Math.min(lastVisibleItem + STORY_VISIBLE_PREFETCH_THRESHOLD, stories.size()) - 1;
                    loadStoriesThroughIndex(targetIndex, storyListGeneration);
                    retryUnsettledStoriesThroughIndex(targetIndex, storyListGeneration);
                }
            }
        };
    }

    private void syncActiveStoryListToSearchState() {
        if (searching) {
            useSearchStoryList();
        } else {
            useMainStoryList();
        }
    }

    private void useMainStoryList() {
        recyclerView = mainRecyclerView;
        adapter = mainAdapter;
        stories = mainStories;
        linearLayoutManager = mainLinearLayoutManager;
        defaultStoryItemAnimator = defaultMainStoryItemAnimator;
    }

    private void useSearchStoryList() {
        recyclerView = searchRecyclerView;
        adapter = searchAdapter;
        stories = searchStories;
        linearLayoutManager = searchLinearLayoutManager;
        defaultStoryItemAnimator = defaultSearchStoryItemAnimator;
    }

    private void useStoryListForAdapter(@NonNull StoryRecyclerViewAdapter sourceAdapter) {
        if (sourceAdapter == searchAdapter) {
            useSearchStoryList();
        } else {
            useMainStoryList();
        }
    }

    private void applySearchRecyclerVisibility(boolean predictiveBackInProgress) {
        if (mainRecyclerView == null || searchRecyclerView == null) {
            return;
        }

        if (predictiveBackInProgress) {
            mainRecyclerView.setVisibility(View.VISIBLE);
            searchRecyclerView.setVisibility(View.VISIBLE);
            mainRecyclerView.setEnabled(false);
            searchRecyclerView.setEnabled(false);
            return;
        }

        mainRecyclerView.animate().cancel();
        searchRecyclerView.animate().cancel();
        mainRecyclerView.setTranslationY(0f);
        searchRecyclerView.setTranslationY(0f);

        if (searching) {
            mainRecyclerView.setAlpha(0f);
            mainRecyclerView.setVisibility(View.GONE);
            mainRecyclerView.setEnabled(false);
            searchRecyclerView.setVisibility(View.VISIBLE);
            searchRecyclerView.setAlpha(1f);
            searchRecyclerView.setEnabled(true);
        } else {
            searchRecyclerView.setAlpha(0f);
            searchRecyclerView.setVisibility(View.GONE);
            searchRecyclerView.setEnabled(false);
            mainRecyclerView.setVisibility(View.VISIBLE);
            mainRecyclerView.setAlpha(1f);
            mainRecyclerView.setEnabled(true);
        }
    }

    private void setupHeader() {
        final Context ctx = requireContext();

        // Tap empty header area to scroll to top
        headerContainer.setOnClickListener(v -> {
            recyclerView.smoothScrollToPosition(0);
            appBarLayout.setExpanded(true, true);
        });

        // Set up retry button
        retryButton.setOnClickListener(v -> {
            Log.d(TAG, "Retry button pressed for type=" + getCurrentStoryType().getLabel()
                    + ", currentStories=" + (stories == null ? 0 : stories.size())
                    + ", loadingFailed=" + loadingFailed
                    + ", loadingFailedServerError=" + loadingFailedServerError);
            attemptStoryTypeRefresh();
        });
        showCachedButton.setOnClickListener(v -> showCachedStories());

        // Set up more button
        moreButton.setOnClickListener(this::moreClick);
        userItemFilterCheckedListener = (group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            int newFilter = userItemListFilterFromButtonId(checkedId);
            if (newFilter == userItemListFilter) {
                return;
            }

            userItemListFilter = newFilter;
            if (currentTypeUsesSavedItemFilter()) {
                applySavedItemFilter(true);
            }
        };
        userItemFilterGroup.addOnButtonCheckedListener(userItemFilterCheckedListener);
        frontPagePreviousDayButton.setOnClickListener(v -> shiftFrontPageDay(-1));
        frontPageDateButton.setOnClickListener(v -> showFrontPageDatePicker());
        frontPageNextDayButton.setOnClickListener(v -> shiftFrontPageDay(1));

        // Set up search
        searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (isSearchSubmitAction(actionId, keyEvent)) {
                    return submitSearchFromInput(textView);
                }

                return isEnterKeyEvent(keyEvent);
            }
        });
        searchEditText.setOnKeyListener((v, keyCode, keyEvent) -> {
            if (!isEnterKeyCode(keyCode)) {
                return false;
            }

            if (keyEvent != null
                    && keyEvent.getAction() == KeyEvent.ACTION_DOWN
                    && keyEvent.getRepeatCount() == 0) {
                return submitSearchFromInput(searchEditText);
            }

            return true;
        });

        searchBar.setOnClickListener(v -> focusSearchInput());
        searchSortChip.setOnClickListener(v -> showSearchOptionMenu(v, searchController.getSortLabels(), searchController.getSortIndex(), selectedIndex -> {
            searchController.setSortIndex(selectedIndex);
            updateSearchOptionChips();
            retrySearchWithCurrentOptions();
        }));
        searchDateChip.setOnClickListener(v -> showSearchOptionMenu(v, searchController.getDateRangeLabels(), searchController.getDateRangeIndex(), selectedIndex -> {
            searchController.setDateRangeIndex(selectedIndex);
            updateSearchOptionChips();
            retrySearchWithCurrentOptions();
        }));
        searchPointsChip.setOnClickListener(v -> showSearchOptionMenu(v, searchController.getMinimumPointsLabels(), searchController.getMinimumPointsIndex(), selectedIndex -> {
            searchController.setMinimumPointsIndex(selectedIndex);
            updateSearchOptionChips();
            retrySearchWithCurrentOptions();
        }));
        searchCommentsChip.setOnClickListener(v -> showSearchOptionMenu(v, searchController.getMinimumCommentsLabels(), searchController.getMinimumCommentsIndex(), selectedIndex -> {
            searchController.setMinimumCommentsIndex(selectedIndex);
            updateSearchOptionChips();
            retrySearchWithCurrentOptions();
        }));
        searchOnlyClickedChip.setOnClickListener(v -> {
            searchController.toggleOnlyClicked();
            updateSearchOptionChips();
            retrySearchWithCurrentOptions();
        });
        updateSearchOptionChips();

        searchButton.setOnClickListener(view -> openSearch());
        closeSearchButton.setOnClickListener(view -> closeSearch(view));

        // Set up spinner
        userItemListsDropdownVisible = shouldShowUserItemLists(ctx);
        ArrayList<CharSequence> typeAdapterList = buildTypeAdapterList(ctx);
        typeSpinnerAdapter = new StoryTypeSpinnerAdapter(ctx, typeAdapterList);

        typeSpinner.setAdapter(typeSpinnerAdapter);
        typeSpinner.setSelection(getPreferredTypeIndex());
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i != adapter.type) {
                    adapter.type = i;
                    updateAdapterCommentRows();
                    updateAdapterPaginationMode(adapter);
                    attemptStoryTypeRefresh();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    private void updateHeader() {
        updateHeader(false);
    }

    private void applyHeaderPadding(@Nullable Context ctx) {
        applyHeaderPadding(ctx, searching);
    }

    private void applyHeaderPadding(@Nullable Context ctx, boolean searchMode) {
        if (ctx == null || headerContainer == null) return;

        boolean compactHeader = SettingsUtils.shouldUseCompactHeader(ctx);
        int topPad = topInset + Utils.pxFromDpInt(getResources(), compactHeader ? 20 : 40);
        int bottomPad = Utils.pxFromDpInt(getResources(), compactHeader
                ? (searchMode ? 6 : 10)
                : (searchMode ? 18 : 26));
        if (!searchMode && shouldShowLastUpdatedHeader()) {
            bottomPad = Utils.pxFromDpInt(getResources(), compactHeader ? 4 : 8);
        }
        int sidePaddingStart = headerBasePaddingStart + getSplitStoriesContentPaddingStart();
        int sidePaddingEnd = headerBasePaddingEnd;
        headerContainer.setPaddingRelative(sidePaddingStart, topPad, sidePaddingEnd, bottomPad);

        if (searchOptionsScroll == null) return;

        searchOptionsScroll.setPaddingRelative(
                sidePaddingStart,
                searchOptionsScroll.getPaddingTop(),
                0,
                searchOptionsScroll.getPaddingBottom()
        );

        ViewGroup.LayoutParams layoutParams = searchOptionsScroll.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
            marginParams.setMarginStart(-sidePaddingStart);
            marginParams.setMarginEnd(-sidePaddingEnd);
            searchOptionsScroll.setLayoutParams(marginParams);
        }
    }

    private void updateHeader(boolean animateSearchTransition) {
        if (headerContainer == null) return;

        Context ctx = getContext();
        if (ctx == null) return;

        updateSwipeRefreshEnabled();
        beginHeaderTransition(animateSearchTransition);

        updateLastUpdatedHeader(ctx);
        applyHeaderPadding(ctx);

        moreButton.setVisibility(searching ? View.GONE : View.VISIBLE);
        spinnerContainer.setVisibility(searching ? View.GONE : View.VISIBLE);
        searchButton.setVisibility(searching ? View.GONE : View.VISIBLE);
        closeSearchButton.setVisibility(searching ? View.VISIBLE : View.GONE);

        searchContainer.setVisibility(searching ? View.VISIBLE : View.GONE);
        searchOptionsScroll.setVisibility(searching ? View.VISIBLE : View.GONE);

        boolean bookmarksType = isBookmarksType(adapter.type);
        boolean historyType = isHistoryType(adapter.type);
        boolean favoritesType = isFavoritesType(adapter.type);
        boolean upvotedType = isUpvotedType(adapter.type);
        boolean userItemListType = favoritesType || upvotedType;
        boolean savedItemSourceHasItems = currentSavedItemSourceHasItems();
        userItemFilterGroup.setVisibility(!searching && currentTypeUsesSavedItemFilter() && savedItemSourceHasItems ? View.VISIBLE : View.GONE);
        frontPageDateControls.setVisibility(!searching && currentTypeIsFront() ? View.VISIBLE : View.GONE);
        updateFrontPageDateControls();
        if (noBookmarksImage != null && noBookmarksText != null) {
            noBookmarksImage.setImageResource(getEmptySavedListIcon(historyType, favoritesType, upvotedType));
            noBookmarksText.setText(getEmptySavedListText(historyType, favoritesType, upvotedType, savedItemSourceHasItems));
        }

        if (searching) {
            loadingIndicator.setVisibility(algoliaLoading ? View.VISIBLE : View.GONE);
            searchEditText.requestFocus();
            searchEditText.setText(lastSearch);
            searchEditText.setSelection(lastSearch.length());

            boolean hasSubmittedSearch = !TextUtils.isEmpty(lastSearch.trim());
            boolean showSearchEmpty = hasSubmittedSearch
                    && stories.isEmpty()
                    && !algoliaLoading
                    && !loadingFailed
                    && !loadingFailedServerError;
            searchEmptyContainer.setVisibility(showSearchEmpty ? View.VISIBLE : View.GONE);
            noBookmarksLayout.setVisibility(View.GONE);
        } else {
            boolean showEmptySavedList = stories.isEmpty()
                    && !loadingFailed
                    && !loadingFailedServerError
                    && (bookmarksType
                    || historyType
                    || (userItemListType && !userItemListInitialLoadInProgress && !swipeRefreshLayout.isRefreshing()));
            noBookmarksLayout.setVisibility(showEmptySavedList ? View.VISIBLE : View.GONE);
            searchEmptyContainer.setVisibility(View.GONE);

            boolean showLoading = stories.isEmpty()
                    && !loadingFailed
                    && !loadingFailedServerError
                    && !bookmarksType
                    && !historyType
                    && (!userItemListType || userItemListInitialLoadInProgress);
            loadingIndicator.setVisibility(showLoading ? View.VISIBLE : View.GONE);
        }

        typeSpinnerAdapter.setSelectedTitle(showingCached ? "Cached stories" : null);
        typeSpinner.setSelection(adapter.type);

        TooltipCompat.setTooltipText(searchButton, "Search");
        TooltipCompat.setTooltipText(closeSearchButton, "Close");
        TooltipCompat.setTooltipText(moreButton, "More");

        loadingFailedLayout.setVisibility(loadingFailed ? View.VISIBLE : View.GONE);
        if (loadingFailed) {
            if (loadingFailedRateLimited) {
                loadingFailedText.setText("Rate limited");
            } else if (!Utils.isNetworkAvailable(ctx)) {
                loadingFailedText.setText("No internet connection");
            } else {
                loadingFailedText.setText("Loading failed");
            }
        }

        showCachedButton.setVisibility(loadingFailed && !searching && Utils.hasCachedStories(ctx) ? View.VISIBLE : View.GONE);
        if (storyCacheController != null) {
            storyCacheController.updateProgressIndicator();
        }

        loadingFailedAlgoliaLayout.setVisibility(loadingFailedServerError ? View.VISIBLE : View.GONE);
        requestRecyclerScrollStateUpdate();

        if (predictiveSearchBackInProgress) {
            applySearchBackVisualProgress(predictiveSearchBackProgress);
        }
    }

    private boolean shouldShowLastUpdatedHeader() {
        return updateButtonShowing && !searching && lastLoaded > 0;
    }

    private void updateLastUpdatedHeader(@Nullable Context ctx) {
        if (lastUpdatedHeaderText == null) return;

        boolean showLastUpdated = ctx != null && shouldShowLastUpdatedHeader();
        lastUpdatedHeaderText.setVisibility(showLastUpdated ? View.VISIBLE : View.GONE);
        if (showLastUpdated) {
            lastUpdatedHeaderText.setText("Last updated: "
                    + android.text.format.DateFormat.getTimeFormat(ctx).format(new java.util.Date(lastLoaded)));
        }
    }

    private int getSplitStoriesContentPaddingStart() {
        Context ctx = getContext();
        if (ctx == null
                || !(getActivity() instanceof MainActivity)
                || FoldableSplitInitializer.isFoldableSplitEnabled(ctx)
                || !Utils.isTablet(getResources())) {
            return 0;
        }

        return getResources().getDimensionPixelSize(R.dimen.extra_pane_padding);
    }

    private void applyStoriesRecyclerPadding() {
        int bottomPadding = systemBottomInset;
        if (appBarLayout != null && (adapter == null || !adapter.hasLoadMoreButton())) {
            // The scrolling view is measured for the collapsed app bar. Reserve its scroll
            // range so a list that fills the expanded viewport can still collapse the header.
            // Lists ending in a load-more row already fill it, and reserving the range leaves
            // a large gap below that row.
            bottomPadding += appBarLayout.getTotalScrollRange();
        }
        if (updateButtonShowing && updateFab != null) {
            int updateFabHeight = updateFab.getHeight();
            ViewGroup.LayoutParams layoutParams = updateFab.getLayoutParams();
            if (updateFabHeight <= 0 && layoutParams != null && layoutParams.height > 0) {
                updateFabHeight = layoutParams.height;
            }
            if (updateFabHeight <= 0) {
                updateFabHeight = Utils.pxFromDpInt(getResources(), 56);
            }

            int updateFabBottomMargin = 0;
            if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                updateFabBottomMargin = ((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin;
            }

            bottomPadding = Math.max(
                    bottomPadding,
                    updateFabBottomMargin
                            + updateFabHeight
                            + Utils.pxFromDpInt(getResources(), UPDATE_FAB_LOAD_MORE_CLEARANCE_DP)
            );
        }

        applyStoriesRecyclerPadding(mainRecyclerView, bottomPadding);
        applyStoriesRecyclerPadding(searchRecyclerView, bottomPadding);
    }

    private void applyStoriesRecyclerPadding(@Nullable RecyclerView storiesRecyclerView, int bottomPadding) {
        if (storiesRecyclerView == null) {
            return;
        }

        storiesRecyclerView.setPadding(
                getSplitStoriesContentPaddingStart(),
                storiesRecyclerView.getPaddingTop(),
                storiesRecyclerView.getPaddingRight(),
                bottomPadding
        );
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

    private void updateFrontPageDateControls() {
        if (frontPageDateButton == null || frontPageNextDayButton == null) {
            return;
        }

        frontPageDateButton.setText(getFrontPageDayParameter());
        if (frontPagePreviousDayButton != null) {
            frontPagePreviousDayButton.setEnabled(getFrontPageDayUtc().after(getEarliestFrontPageDayUtc()));
        }
        frontPageNextDayButton.setEnabled(getFrontPageDayUtc().before(getLatestFrontPageDayUtc()));
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
        updateFrontPageDateControls();
        if (currentTypeIsFront()) {
            attemptStoryTypeRefresh();
        }
    }

    private void showFrontPageDatePicker() {
        if (getParentFragmentManager().isStateSaved()) {
            return;
        }

        long latestDay = getLatestFrontPageDayUtc().getTimeInMillis();
        long earliestDay = getEarliestFrontPageDayUtc().getTimeInMillis();
        CalendarConstraints constraints = new CalendarConstraints.Builder()
                .setStart(earliestDay)
                .setEnd(latestDay)
                .setValidator(CompositeDateValidator.allOf(Arrays.asList(
                        DateValidatorPointForward.from(earliestDay),
                        DateValidatorPointBackward.before(latestDay + 24L * 60L * 60L * 1000L))))
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select front page day")
                .setSelection(getFrontPageDayUtc().getTimeInMillis())
                .setCalendarConstraints(constraints)
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null) {
                return;
            }

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
            updateFrontPageDateControls();
            if (currentTypeIsFront()) {
                attemptStoryTypeRefresh();
            }
        });
        picker.show(getParentFragmentManager(), "front_page_date_picker");
    }

    private void beginLastUpdatedHeaderTransitionIfNeeded(@Nullable Context ctx) {
        if (lastUpdatedHeaderText == null) return;

        boolean showLastUpdated = ctx != null && shouldShowLastUpdatedHeader();
        boolean isLastUpdatedVisible = lastUpdatedHeaderText.getVisibility() == View.VISIBLE;
        if (isLastUpdatedVisible != showLastUpdated) {
            beginHeaderTransition(false);
        }
    }

    private int getEmptySavedListIcon(boolean historyType, boolean favoritesType, boolean upvotedType) {
        if (historyType) {
            return R.drawable.ic_history;
        }
        if (favoritesType) {
            return R.drawable.ic_star;
        }
        if (upvotedType) {
            return R.drawable.ic_thumb_up_filled;
        }
        return R.drawable.ic_bookmark;
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

    private int userItemListFilterFromButtonId(int buttonId) {
        if (buttonId == R.id.stories_header_user_item_filter_stories) {
            return USER_ITEM_LIST_FILTER_STORIES;
        }
        if (buttonId == R.id.stories_header_user_item_filter_comments) {
            return USER_ITEM_LIST_FILTER_COMMENTS;
        }
        return USER_ITEM_LIST_FILTER_BOTH;
    }

    private boolean isSearchSubmitAction(int actionId, @Nullable KeyEvent keyEvent) {
        return actionId == EditorInfo.IME_ACTION_SEARCH
                || actionId == EditorInfo.IME_ACTION_DONE
                || actionId == EditorInfo.IME_ACTION_GO
                || isEnterKeyDown(keyEvent);
    }

    private boolean isEnterKeyDown(@Nullable KeyEvent keyEvent) {
        return isEnterKeyEvent(keyEvent)
                && keyEvent.getAction() == KeyEvent.ACTION_DOWN
                && keyEvent.getRepeatCount() == 0;
    }

    private boolean isEnterKeyEvent(@Nullable KeyEvent keyEvent) {
        return keyEvent != null && isEnterKeyCode(keyEvent.getKeyCode());
    }

    private boolean isEnterKeyCode(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private boolean submitSearchFromInput(@Nullable TextView textView) {
        search(searchEditText.getText().toString());
        if (textView != null) {
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
        }
        return true;
    }

    private void showSearchOptionMenu(View anchor, String[] labels, int selectedIndex, SearchOptionSelectedListener listener) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        for (int i = 0; i < labels.length; i++) {
            MenuItem item = popup.getMenu().add(Menu.NONE, i, i, labels[i]);
            item.setCheckable(true);
            item.setChecked(i == selectedIndex);
        }
        popup.getMenu().setGroupCheckable(Menu.NONE, true, true);
        popup.setOnMenuItemClickListener(item -> {
            listener.onSearchOptionSelected(item.getItemId());
            return true;
        });
        popup.show();
    }

    private void updateSearchOptionChips() {
        updateSearchOptionChips(true);
    }

    private void updateSearchOptionChips(boolean animate) {
        if (animate) {
            beginSearchOptionsTransition();
        }

        searchSortChip.setText(searchController.getSortLabel());
        searchDateChip.setText(searchController.getDateRangeLabel());
        searchPointsChip.setText(searchController.getMinimumPointsLabel());
        searchCommentsChip.setText(searchController.getMinimumCommentsLabel());
        searchOnlyClickedChip.setChecked(searchController.isOnlyClicked());

        searchSortChip.setContentDescription("Search sort: " + searchController.getSortLabel());
        searchDateChip.setContentDescription("Search date range: " + searchController.getDateRangeLabel());
        searchPointsChip.setContentDescription("Search minimum points: " + searchController.getMinimumPointsLabel());
        searchCommentsChip.setContentDescription("Search minimum comments: " + searchController.getMinimumCommentsLabel());
        searchOnlyClickedChip.setContentDescription(searchController.isOnlyClicked()
                ? "From history search enabled"
                : "From history search disabled");
    }

    private void beginSearchOptionsTransition() {
        if (!(searchOptionsScroll instanceof ViewGroup)
                || searchOptionsScroll.getVisibility() != View.VISIBLE
                || !ViewCompat.isLaidOut(searchOptionsScroll)) {
            return;
        }

        AutoTransition transition = new AutoTransition();
        transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
        transition.setDuration(SEARCH_HEADER_ANIMATION_DURATION_MS);
        transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        TransitionManager.beginDelayedTransition((ViewGroup) searchOptionsScroll, transition);
    }

    private void retrySearchWithCurrentOptions() {
        if (!searching) {
            return;
        }

        String query = searchEditText.getText().toString();
        if (TextUtils.isEmpty(query)) {
            query = lastSearch;
        }

        if (!TextUtils.isEmpty(query)) {
            search(query);
        }
    }

    private void beginHeaderTransition(boolean animateSearchTransition) {
        if (appBarLayout == null || !(appBarLayout.getParent() instanceof ViewGroup)) {
            return;
        }

        ViewGroup transitionRoot = (ViewGroup) appBarLayout.getParent();
        if (!ViewCompat.isLaidOut(transitionRoot)) {
            return;
        }

        AutoTransition transition = new AutoTransition();
        transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
        transition.setDuration(animateSearchTransition
                ? SEARCH_HEADER_ANIMATION_DURATION_MS
                : HEADER_LAYOUT_ANIMATION_DURATION_MS);
        transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        TransitionManager.beginDelayedTransition(transitionRoot, transition);
    }

    private void openSearch() {
        cancelSearchContentReturnAnimation();
        deferSearchRecyclerClearForReturnAnimation = false;
        skipNextSearchContentReturnAnimation = false;
        finishSearchBackFromCurrentVisualState = false;
        resetSearchBackVisualAlphas();
        searching = true;
        resetSearchOptions();
        updateSearchStatus();
        animateSearchOptionsIn();

        focusSearchInput();
    }

    private void closeSearch(@Nullable View view) {
        boolean animateFromSearchBackProgress = finishSearchBackFromCurrentVisualState;
        float searchBackFinishProgress = predictiveSearchBackProgress;
        finishSearchBackFromCurrentVisualState = false;

        cancelSearchContentExitAnimation(false);
        cancelSearchOptionsAnimation(!animateFromSearchBackProgress);
        predictiveSearchBackInProgress = false;
        predictiveSearchBackShowingMainHeader = false;
        predictiveSearchBackProgress = 0f;
        if (!animateFromSearchBackProgress) {
            resetSearchBackVisualAlphas();
        }

        boolean animateContentReturn = !skipNextSearchContentReturnAnimation
                && mainRecyclerView != null
                && searchRecyclerView != null
                && ViewCompat.isLaidOut(searchRecyclerView);
        skipNextSearchContentReturnAnimation = false;
        deferSearchRecyclerClearForReturnAnimation = animateContentReturn;
        searching = false;
        lastSearch = "";
        skipNextSearchRestoreDataSwap = true;
        resetSearchOptions();
        updateSearchStatus();
        if (animateFromSearchBackProgress) {
            long searchBackFinishAnimationDuration = getSearchBackFinishAnimationDuration(searchBackFinishProgress);
            animateSearchBackHeaderToMain(searchBackFinishAnimationDuration);
            if (animateContentReturn) {
                animateSearchContentReturn(true, searchBackFinishAnimationDuration);
            }
        } else if (animateContentReturn) {
            animateSearchContentReturn();
        }
        if (!animateContentReturn) {
            resetSearchBackContentVisualState();
        }

        View tokenView = view != null ? view : searchEditText;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(tokenView.getWindowToken(), 0);
        searchEditText.clearFocus();
    }

    private void focusSearchInput() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        View currentSearchEditText = searchEditText;
        if (currentSearchEditText == null) {
            return;
        }
        currentSearchEditText.post(() -> {
            if (searchEditText != currentSearchEditText) {
                return;
            }
            currentSearchEditText.requestFocus();
            imm.showSoftInput(currentSearchEditText, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void resetSearchOptionsScroll() {
        View currentSearchOptionsScroll = searchOptionsScroll;
        if (currentSearchOptionsScroll == null) {
            return;
        }
        currentSearchOptionsScroll.post(() -> {
            if (searchOptionsScroll == currentSearchOptionsScroll) {
                currentSearchOptionsScroll.scrollTo(0, 0);
            }
        });
    }

    private void resetSearchOptions() {
        searchController.resetOptions();
        updateSearchOptionChips(false);
        resetSearchOptionsScroll();
    }

    private void animateSearchOptionsIn() {
        View currentSearchOptionsScroll = searchOptionsScroll;
        if (currentSearchOptionsScroll == null) {
            return;
        }

        currentSearchOptionsScroll.animate().cancel();
        currentSearchOptionsScroll.setAlpha(0f);
        currentSearchOptionsScroll.setTranslationY(-Utils.pxFromDpInt(getResources(), 6));
        currentSearchOptionsScroll.post(() -> {
            if (!searching || searchOptionsScroll != currentSearchOptionsScroll) {
                return;
            }

            currentSearchOptionsScroll.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(SEARCH_OPTIONS_ENTRANCE_ANIMATION_DELAY_MS)
                    .setDuration(SEARCH_OPTIONS_ENTRANCE_ANIMATION_DURATION_MS)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .start();
        });
    }

    private void cancelSearchOptionsAnimation() {
        cancelSearchOptionsAnimation(true);
    }

    private void cancelSearchOptionsAnimation(boolean resetVisualState) {
        if (searchOptionsScroll == null) {
            return;
        }

        searchOptionsScroll.animate().cancel();
        if (resetVisualState) {
            searchOptionsScroll.setAlpha(1f);
            searchOptionsScroll.setTranslationY(0f);
        }
        searchOptionsScroll.animate().setStartDelay(0);
    }

    private void resetPaginationState() {
        loadedTo = -1;
        clearPaginationLoadMoreState();
        updateAdapterPaginationMode(adapter);
        adapter.visibleStoryCount = adapter.paginationMode ? StoryRecyclerViewAdapter.PAGINATION_PAGE_SIZE : Integer.MAX_VALUE;
    }

    private boolean shouldUsePaginationForType(@Nullable StoryType storyType) {
        return paginationMode || (storyType != null && storyType.isScrapedFrontpage());
    }

    private void updateAdapterPaginationMode(@Nullable StoryRecyclerViewAdapter targetAdapter) {
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
        algoliaLoadMoreFirstVisiblePosition = RecyclerView.NO_POSITION;
        algoliaLoadMoreFirstVisibleTop = 0;
    }

    private int getInitialLoadCount() {
        return adapter != null && adapter.paginationMode ? StoryRecyclerViewAdapter.PAGINATION_PAGE_SIZE : 20;
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
        } else if (linearLayoutManager != null) {
            int lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
            if (lastVisibleItem != RecyclerView.NO_POSITION) {
                storiesToLoad = Math.max(storiesToLoad, lastVisibleItem + STORY_VISIBLE_PREFETCH_THRESHOLD);
            }
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
        clearStoriesWithoutItemAnimations();
        if (searchRecyclerView != null) {
            searchRecyclerView.setVisibility(View.VISIBLE);
            searchRecyclerView.setAlpha(1f);
            searchRecyclerView.setTranslationY(0f);
            searchRecyclerView.setEnabled(true);
        }

        if (mainRecyclerView == null || mainAdapter == null || mainAdapter.getItemCount() == 0) {
            resetSearchContentExitVisualState();
            return;
        }

        if (!ViewCompat.isLaidOut(mainRecyclerView)) {
            resetSearchContentExitVisualState();
            return;
        }

        animateSearchContentExitAndClear();
    }

    private void animateSearchContentExitAndClear() {
        final RecyclerView currentRecyclerView = mainRecyclerView;
        final int animationGeneration = ++searchContentExitAnimationGeneration;
        searchContentExitAnimationRunning = true;

        currentRecyclerView.animate().cancel();
        currentRecyclerView.animate().setStartDelay(0);
        currentRecyclerView.stopScroll();
        currentRecyclerView.setEnabled(false);
        currentRecyclerView.setAlpha(1f);
        currentRecyclerView.setTranslationY(0f);
        currentRecyclerView.animate()
                .alpha(0f)
                .setStartDelay(0)
                .setDuration(SEARCH_CONTENT_EXIT_ANIMATION_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(() -> {
                    if (animationGeneration != searchContentExitAnimationGeneration
                            || mainRecyclerView != currentRecyclerView) {
                        return;
                    }

                    searchContentExitAnimationRunning = false;
                    if (searching) {
                        currentRecyclerView.setVisibility(View.GONE);
                        requestRecyclerScrollStateUpdate();
                    }
                    resetSearchContentExitVisualState();
                })
                .start();
    }

    private void finishSearchContentExitAnimationIfNeeded() {
        if (searchContentExitAnimationRunning) {
            cancelSearchContentExitAnimation(true);
        }
    }

    private void cancelSearchContentExitAnimation(boolean clearPendingStories) {
        if (!searchContentExitAnimationRunning) {
            searchContentExitAnimationGeneration++;
            restoreStoryItemAnimator();
            return;
        }

        searchContentExitAnimationGeneration++;
        if (mainRecyclerView != null) {
            mainRecyclerView.animate().cancel();
            mainRecyclerView.animate().setStartDelay(0);
        }

        searchContentExitAnimationRunning = false;
        if (clearPendingStories && searching) {
            if (mainRecyclerView != null) {
                mainRecyclerView.setVisibility(View.GONE);
            }
            requestRecyclerScrollStateUpdate();
        }
        resetSearchContentExitVisualState();
    }

    private void clearStoriesWithoutItemAnimations() {
        resetPreviewImagePrefetchRamp();
        int oldItemCount = adapter.getItemCount();
        boolean detachedAdapter = detachAdapterForHardSwap();
        stories.clear();
        resetPaginationState();
        adapter.showLoadMoreButton = false;

        if (detachedAdapter) {
            recyclerView.setAdapter(adapter);
        } else if (oldItemCount > 0) {
            adapter.notifyDataSetChanged();
        }
        restoreStoryItemAnimator();
    }

    private void disableStoryItemAnimations() {
        if (recyclerView == null) {
            return;
        }

        if (recyclerView.getItemAnimator() != null) {
            recyclerView.getItemAnimator().endAnimations();
            recyclerView.setItemAnimator(null);
        }
    }

    private void restoreStoryItemAnimator() {
        if (recyclerView != null
                && recyclerView.getItemAnimator() == null
                && defaultStoryItemAnimator != null) {
            recyclerView.setItemAnimator(defaultStoryItemAnimator);
        }
    }

    private void resetSearchContentExitVisualState() {
        applySearchRecyclerVisibility(false);
        if (recyclerView != null) {
            recyclerView.animate().setStartDelay(0);
        }
    }

    private void animateSearchContentReturn() {
        animateSearchContentReturn(false, SEARCH_CONTENT_RETURN_ANIMATION_DURATION_MS);
    }

    private void animateSearchContentReturn(boolean fromCurrentVisualState, long duration) {
        if (mainRecyclerView == null || searchRecyclerView == null) {
            resetSearchBackContentVisualState();
            return;
        }

        final RecyclerView currentMainRecyclerView = mainRecyclerView;
        final RecyclerView currentSearchRecyclerView = searchRecyclerView;
        final int animationGeneration = ++searchContentExitAnimationGeneration;
        final float contentTranslation = getSearchBackContentTranslation(1f);

        currentMainRecyclerView.animate().cancel();
        currentSearchRecyclerView.animate().cancel();
        currentMainRecyclerView.animate().setStartDelay(0);
        currentSearchRecyclerView.animate().setStartDelay(0);

        currentMainRecyclerView.setVisibility(View.VISIBLE);
        currentSearchRecyclerView.setVisibility(View.VISIBLE);
        currentMainRecyclerView.setEnabled(false);
        currentSearchRecyclerView.setEnabled(false);
        if (!fromCurrentVisualState) {
            currentMainRecyclerView.setAlpha(0f);
            currentMainRecyclerView.setTranslationY(contentTranslation);
            currentSearchRecyclerView.setAlpha(1f);
            currentSearchRecyclerView.setTranslationY(0f);
        }

        PathInterpolator interpolator = new PathInterpolator(0.2f, 0f, 0f, 1f);
        currentSearchRecyclerView.animate()
                .alpha(0f)
                .translationY(contentTranslation)
                .setStartDelay(0)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start();
        currentMainRecyclerView.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(0)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction(() -> {
                    if (animationGeneration != searchContentExitAnimationGeneration
                            || mainRecyclerView != currentMainRecyclerView
                            || searchRecyclerView != currentSearchRecyclerView
                            || searching) {
                        return;
                    }

                    finishDeferredSearchRecyclerClear();
                    applySearchRecyclerVisibility(false);
                    requestRecyclerScrollStateUpdate();
                })
                .start();
    }

    private void finishDeferredSearchRecyclerClear() {
        if (!deferSearchRecyclerClearForReturnAnimation) {
            return;
        }

        deferSearchRecyclerClearForReturnAnimation = false;
        useSearchStoryList();
        clearStoriesWithoutItemAnimations();
        useMainStoryList();
    }

    private void cancelSearchContentReturnAnimation() {
        searchContentExitAnimationGeneration++;
        if (mainRecyclerView != null) {
            mainRecyclerView.animate().cancel();
            mainRecyclerView.animate().setStartDelay(0);
        }
        if (searchRecyclerView != null) {
            searchRecyclerView.animate().cancel();
            searchRecyclerView.animate().setStartDelay(0);
        }
    }

    private void replaceStories(List<Story> newStories) {
        replaceStories(newStories, false, false);
    }

    private void replaceStories(List<Story> newStories, boolean notifyDataSetChanged) {
        replaceStories(newStories, notifyDataSetChanged, false);
    }

    private void replaceStories(List<Story> newStories, boolean notifyDataSetChanged, boolean showLoadMoreButton) {
        resetPreviewImagePrefetchRamp();
        // A full remove followed immediately by an insert can leave disappearing holders in
        // RecyclerView's hidden-child list while item animations are still running. Detach for
        // non-empty replacements so those holders are fully recycled before the new list binds.
        if (notifyDataSetChanged || adapter.getItemCount() > 0) {
            boolean detachedAdapter = detachAdapterForHardSwap();
            stories.clear();
            resetPaginationState();
            adapter.showLoadMoreButton = showLoadMoreButton;
            stories.addAll(newStories);
            if (detachedAdapter) {
                recyclerView.setAdapter(adapter);
            } else {
                adapter.notifyDataSetChanged();
            }
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
            adapter.visibleStoryCount = Math.min(Math.max(requestedVisibleCount, StoryRecyclerViewAdapter.PAGINATION_PAGE_SIZE), stories.size());
        } else {
            adapter.visibleStoryCount = Integer.MAX_VALUE;
        }

        adapter.notifyDataSetChanged();
        restoreAlgoliaLoadMoreScrollPosition();
    }

    private void saveAlgoliaLoadMoreScrollPosition() {
        algoliaLoadMoreFirstVisiblePosition = RecyclerView.NO_POSITION;
        algoliaLoadMoreFirstVisibleTop = 0;

        if (linearLayoutManager == null || recyclerView == null) {
            return;
        }

        int firstVisiblePosition = linearLayoutManager.findFirstVisibleItemPosition();
        if (firstVisiblePosition == RecyclerView.NO_POSITION) {
            return;
        }

        View firstVisibleView = linearLayoutManager.findViewByPosition(firstVisiblePosition);
        algoliaLoadMoreFirstVisiblePosition = firstVisiblePosition;
        algoliaLoadMoreFirstVisibleTop = firstVisibleView == null ? recyclerView.getPaddingTop() : firstVisibleView.getTop();
    }

    private void restoreAlgoliaLoadMoreScrollPosition() {
        if (linearLayoutManager == null
                || recyclerView == null
                || algoliaLoadMoreFirstVisiblePosition == RecyclerView.NO_POSITION) {
            return;
        }

        int position = Math.min(algoliaLoadMoreFirstVisiblePosition, Math.max(0, adapter.getItemCount() - 1));
        int offset = algoliaLoadMoreFirstVisibleTop - recyclerView.getPaddingTop();
        recyclerView.post(() -> {
            if (linearLayoutManager != null) {
                linearLayoutManager.scrollToPositionWithOffset(position, offset);
            }
        });
    }

    private void endRecyclerViewAnimations() {
        if (recyclerView != null && recyclerView.getItemAnimator() != null) {
            recyclerView.getItemAnimator().endAnimations();
        }
    }

    private boolean detachAdapterForHardSwap() {
        if (recyclerView == null || adapter == null || recyclerView.getAdapter() != adapter) {
            endRecyclerViewAnimations();
            return false;
        }

        endRecyclerViewAnimations();
        recyclerView.stopScroll();
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            child.animate().cancel();
            child.clearAnimation();
        }
        recyclerView.getOverlay().clear();
        recyclerView.setAdapter(null);
        recyclerView.getRecycledViewPool().clear();
        return true;
    }

    private int getFirstVisibleStoryPosition() {
        if (linearLayoutManager == null) {
            return RecyclerView.NO_POSITION;
        }

        return linearLayoutManager.findFirstVisibleItemPosition();
    }

    private int getFirstVisibleStoryTop(int firstVisiblePosition) {
        if (linearLayoutManager == null
                || recyclerView == null
                || firstVisiblePosition == RecyclerView.NO_POSITION) {
            return 0;
        }

        View firstVisibleView = linearLayoutManager.findViewByPosition(firstVisiblePosition);
        return firstVisibleView == null ? recyclerView.getPaddingTop() : firstVisibleView.getTop();
    }

    private void restoreStoryScrollPosition(int firstVisiblePosition, int firstVisibleTop) {
        if (linearLayoutManager == null
                || recyclerView == null
                || firstVisiblePosition == RecyclerView.NO_POSITION
                || adapter.getItemCount() == 0) {
            return;
        }

        int position = Math.min(firstVisiblePosition, adapter.getItemCount() - 1);
        int offset = firstVisibleTop - recyclerView.getPaddingTop();
        linearLayoutManager.scrollToPositionWithOffset(position, offset);
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
        firstVisiblePositionBeforeSearch = getFirstVisibleStoryPosition();
        firstVisibleTopBeforeSearch = getFirstVisibleStoryTop(firstVisiblePositionBeforeSearch);
    }

    private boolean restoreStoriesBeforeSearch() {
        if (storiesBeforeSearch == null) {
            suppressNextSearchRestoreAnimations = false;
            skipNextSearchRestoreDataSwap = false;
            return false;
        }

        if (skipNextSearchRestoreDataSwap) {
            skipNextSearchRestoreDataSwap = false;
            suppressNextSearchRestoreAnimations = false;
            restoreStoryStateBeforeSearch();
            restoreStoryScrollPosition(firstVisiblePositionBeforeSearch, firstVisibleTopBeforeSearch);
            clearStoriesBeforeSearchSnapshot();
            return true;
        }

        int oldItemCount = adapter.getItemCount();
        stories.clear();
        if (!suppressNextSearchRestoreAnimations && oldItemCount > 0) {
            adapter.notifyItemRangeRemoved(0, oldItemCount);
        }

        stories.addAll(storiesBeforeSearch);
        loadedTo = loadedToBeforeSearch;
        adapter.visibleStoryCount = visibleStoryCountBeforeSearch;
        showingCached = showingCachedBeforeSearch;
        loadingFailed = loadingFailedBeforeSearch;
        loadingFailedServerError = loadingFailedServerErrorBeforeSearch;
        loadingFailedRateLimited = loadingFailedRateLimitedBeforeSearch;
        adapter.showLoadMoreButton = showLoadMoreBeforeSearch;
        algoliaHitsPerPage = algoliaHitsPerPageBeforeSearch;
        lastAlgoliaTopStoriesStartTime = lastAlgoliaTopStoriesStartTimeBeforeSearch;

        int newItemCount = adapter.getItemCount();
        if (suppressNextSearchRestoreAnimations) {
            adapter.notifyDataSetChanged();
            suppressNextSearchRestoreAnimations = false;
        } else if (newItemCount > 0) {
            adapter.notifyItemRangeInserted(0, newItemCount);
        }

        restoreStoryScrollPosition(firstVisiblePositionBeforeSearch, firstVisibleTopBeforeSearch);
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
        firstVisiblePositionBeforeSearch = RecyclerView.NO_POSITION;
        firstVisibleTopBeforeSearch = 0;
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

    private void applyStatusBarProtection() {
        if (binding == null || getContext() == null) {
            return;
        }
        StatusBarProtectionUtils.setTopProtection(
                binding.listProtection,
                StatusBarProtectionUtils.getPaneBackgroundColor(requireContext()));
    }

    private void rebuildStoryAdapters() {
        int previousMainType = mainAdapter != null ? mainAdapter.type : getPreferredTypeIndex();
        int previousSearchType = searchAdapter != null ? searchAdapter.type : previousMainType;
        int previousMainVisibleStoryCount = mainAdapter != null
                ? mainAdapter.visibleStoryCount
                : (paginationMode ? StoryRecyclerViewAdapter.PAGINATION_PAGE_SIZE : Integer.MAX_VALUE);
        int previousSearchVisibleStoryCount = searchAdapter != null
                ? searchAdapter.visibleStoryCount
                : (paginationMode ? StoryRecyclerViewAdapter.PAGINATION_PAGE_SIZE : Integer.MAX_VALUE);
        boolean previousMainShowLoadMoreButton = mainAdapter != null && mainAdapter.showLoadMoreButton;
        boolean previousSearchShowLoadMoreButton = searchAdapter != null && searchAdapter.showLoadMoreButton;

        unregisterStoryAdapterDataObservers();
        if (mainRecyclerView != null) {
            mainRecyclerView.setAdapter(null);
        }
        if (searchRecyclerView != null) {
            searchRecyclerView.setAdapter(null);
        }

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

        if (mainRecyclerView != null) {
            mainRecyclerView.setAdapter(mainAdapter);
        }
        if (searchRecyclerView != null) {
            searchRecyclerView.setAdapter(searchAdapter);
        }
        registerStoryAdapterDataObservers();
        applySearchRecyclerVisibility(predictiveSearchBackInProgress);
        requestRecyclerScrollStateUpdate();
    }

    private void syncInactiveStoryAdapterDisplaySettings() {
        if (mainAdapter == null || searchAdapter == null || adapter == null) {
            return;
        }

        StoryRecyclerViewAdapter inactiveAdapter = adapter == mainAdapter ? searchAdapter : mainAdapter;
        copyStoryAdapterDisplaySettings(adapter, inactiveAdapter);
        updateAdapterCommentRows(inactiveAdapter);
        if (inactiveAdapter.getItemCount() > 0) {
            inactiveAdapter.notifyItemRangeChanged(0, inactiveAdapter.getItemCount());
        }
    }

    private void copyStoryAdapterDisplaySettings(@NonNull StoryRecyclerViewAdapter sourceAdapter,
                                                 @NonNull StoryRecyclerViewAdapter targetAdapter) {
        StoryDisplaySettings.copyAdapterSettings(sourceAdapter, targetAdapter);
    }

    private StoryRecyclerViewAdapter createStoryAdapter(List<Story> adapterStories) {
        return StoryDisplaySettings.from(requireContext()).createAdapter(adapterStories, null, getPreferredTypeIndex());
    }

    private void configureStoryAdapter(@NonNull StoryRecyclerViewAdapter configuredAdapter) {
        updateAdapterPaginationMode(configuredAdapter);
        configuredAdapter.visibleStoryCount = configuredAdapter.paginationMode ? StoryRecyclerViewAdapter.PAGINATION_PAGE_SIZE : Integer.MAX_VALUE;

        configuredAdapter.setOnLinkClickListener(position -> {
            useStoryListForAdapter(configuredAdapter);
            if (position == RecyclerView.NO_POSITION) {
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
        });

        configuredAdapter.setOnCommentClickListener(position -> {
            useStoryListForAdapter(configuredAdapter);
            clickedComments(position);
        });
        configuredAdapter.setOnCommentStoryClickListener(position -> {
            useStoryListForAdapter(configuredAdapter);
            clickedCommentStory(position);
        });
        configuredAdapter.setOnCommentRepliesClickListener(position -> {
            useStoryListForAdapter(configuredAdapter);
            clickedComments(position);
        });

        // Set up pagination "Load More" button click listener
        configuredAdapter.setOnLoadMoreClickListener(v -> {
            useStoryListForAdapter(configuredAdapter);
            if (adapter.paginationMode && adapter.visibleStoryCount < stories.size()) {
                int newLoadedTo = Math.min(
                        loadedTo + StoryRecyclerViewAdapter.PAGINATION_PAGE_SIZE,
                        stories.size() - 1
                );

                startPaginationLoadMore(newLoadedTo, storyListGeneration);
                adapter.setLoadMoreLoading(true);
                // Move the busy load-more row after the newly revealed page.
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
        });

        configuredAdapter.setOnLongClickListener(new StoryRecyclerViewAdapter.LongClickCoordinateListener() {
            @Override
            public boolean onLongClick(View v, int position, int x, int y) {
                useStoryListForAdapter(configuredAdapter);
                if (position == RecyclerView.NO_POSITION) {
                    return false;
                }

                Story story = stories.get(position);
                linkSummaryOverlayController.showStory(
                        story,
                        position,
                        resolveLinkSummarySourceView(position, v),
                        resolveLinkSummaryStorySharedElements(position));
                return true;
            }
        });
    }

    private void setupLinkSummaryBackCallback() {
        linkSummaryBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackCancelled() {
                if (linkSummaryOverlayController.isShowing()) {
                    linkSummaryOverlayController.cancelPredictiveBack();
                }
            }

            @Override
            public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                if (linkSummaryOverlayController.isShowing()) {
                    linkSummaryOverlayController.updatePredictiveBack(backEvent);
                }
            }

            @Override
            public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                if (linkSummaryOverlayController.isShowing()) {
                    linkSummaryOverlayController.startPredictiveBack(backEvent);
                }
            }

            @Override
            public void handleOnBackPressed() {
                if (!linkSummaryOverlayController.isShowing()) {
                    return;
                }
                if (linkSummaryOverlayController.isPredictiveBackActive()) {
                    linkSummaryOverlayController.commitPredictiveBack();
                } else {
                    linkSummaryOverlayController.dismiss(true);
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), linkSummaryBackCallback);
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
        if (!isAdded() || getView() == null || story == null || adapter == null || stories == null) {
            completion.run();
            return;
        }
        Context context = requireContext();
        int actionGeneration = storyListGeneration;
        StoryRecyclerViewAdapter actionAdapter = adapter;
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
            UserActions.upvote(context, story.id, getParentFragmentManager(), callback);
        } else {
            UserActions.unvote(context, story.id, getParentFragmentManager(), callback);
        }
    }

    private void toggleStoryFavorite(Story story, int position, boolean currentlyFavorited,
                                     Runnable completion) {
        if (!isAdded() || getView() == null || story == null || adapter == null || stories == null) {
            completion.run();
            return;
        }
        Context context = requireContext();
        int actionGeneration = storyListGeneration;
        StoryRecyclerViewAdapter actionAdapter = adapter;
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
        UserActions.setFavorite(context, story.id, newFavorited, getParentFragmentManager(),
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
            StoryRecyclerViewAdapter expectedAdapter,
            List<Story> expectedStories) {
        return isAdded()
                && getView() != null
                && generation == storyListGeneration
                && adapter == expectedAdapter
                && stories == expectedStories;
    }

    @Nullable
    private ViewGroup getLinkSummaryOverlayHost() {
        if (!isAdded()) {
            return null;
        }
        View activityContent = requireActivity().findViewById(android.R.id.content);
        if (activityContent instanceof ViewGroup) {
            return (ViewGroup) activityContent;
        }
        View fragmentView = getView();
        return fragmentView instanceof ViewGroup ? (ViewGroup) fragmentView : null;
    }

    @Nullable
    private View findLinkSummarySourceView(int storyId) {
        if (stories == null || recyclerView == null) {
            return null;
        }
        for (int position = 0; position < stories.size(); position++) {
            Story candidate = stories.get(position);
            if (candidate != null && candidate.id == storyId) {
                return resolveLinkSummarySourceView(position, null);
            }
        }
        return null;
    }

    @Nullable
    private LinkSummaryOverlayController.StorySharedElements findLinkSummaryStorySharedElements(int storyId) {
        if (stories == null || recyclerView == null) {
            return null;
        }
        for (int position = 0; position < stories.size(); position++) {
            Story candidate = stories.get(position);
            if (candidate != null && candidate.id == storyId) {
                return resolveLinkSummaryStorySharedElements(position);
            }
        }
        return null;
    }

    private int getLinkSummaryStoryPagingDistance(int firstStoryId, int secondStoryId) {
        if (stories == null || recyclerView == null) return 0;
        cacheVisibleLinkSummaryStoryHeights();
        int firstPosition = findStoryPositionById(firstStoryId);
        int secondPosition = findStoryPositionById(secondStoryId);
        if (firstPosition == RecyclerView.NO_POSITION
                || secondPosition == RecyclerView.NO_POSITION
                || firstPosition == secondPosition) {
            return resolveLinkSummaryStoryHeight(firstStoryId, firstPosition);
        }
        int start = Math.min(firstPosition, secondPosition);
        int end = Math.max(firstPosition, secondPosition);
        int distance = 0;
        for (int position = start; position < end; position++) {
            Story story = stories.get(position);
            distance += resolveLinkSummaryStoryHeight(story.id, position);
        }
        return distance;
    }

    private void cacheVisibleLinkSummaryStoryHeights() {
        if (recyclerView == null || recyclerView.getLayoutManager() == null
                || stories == null) {
            return;
        }
        RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
        for (int childIndex = 0; childIndex < recyclerView.getChildCount(); childIndex++) {
            View child = recyclerView.getChildAt(childIndex);
            int position = recyclerView.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION || position >= stories.size()) continue;
            int height = layoutManager.getDecoratedMeasuredHeight(child);
            if (height > 0) {
                linkSummaryStoryHeights.put(stories.get(position).id, height);
            }
        }
    }

    private int resolveLinkSummaryStoryHeight(int storyId, int position) {
        int cachedHeight = linkSummaryStoryHeights.get(storyId, 0);
        if (cachedHeight > 0) return cachedHeight;
        if (recyclerView != null && recyclerView.getLayoutManager() != null
                && position != RecyclerView.NO_POSITION) {
            RecyclerView.ViewHolder holder =
                    recyclerView.findViewHolderForAdapterPosition(position);
            if (holder != null) {
                int measuredHeight = recyclerView.getLayoutManager()
                        .getDecoratedMeasuredHeight(holder.itemView);
                if (measuredHeight > 0) {
                    linkSummaryStoryHeights.put(storyId, measuredHeight);
                    return measuredHeight;
                }
            }
        }
        if (linkSummaryStoryHeights.size() > 0) {
            int totalHeight = 0;
            for (int index = 0; index < linkSummaryStoryHeights.size(); index++) {
                totalHeight += linkSummaryStoryHeights.valueAt(index);
            }
            return Math.max(1, totalHeight / linkSummaryStoryHeights.size());
        }
        return Utils.pxFromDpInt(getResources(), 96);
    }

    private int findStoryPositionById(int storyId) {
        if (stories == null) return RecyclerView.NO_POSITION;
        for (int position = 0; position < stories.size(); position++) {
            Story story = stories.get(position);
            if (story != null && story.id == storyId) return position;
        }
        return RecyclerView.NO_POSITION;
    }

    @Nullable
    private View resolveLinkSummarySourceView(int position, @Nullable View fallback) {
        if (recyclerView == null) {
            return fallback;
        }
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (!(holder instanceof StoryRecyclerViewAdapter.StoryViewHolder)) {
            return fallback;
        }
        StoryRecyclerViewAdapter.StoryViewHolder storyHolder =
                (StoryRecyclerViewAdapter.StoryViewHolder) holder;
        return storyHolder.storyCard != null ? storyHolder.storyCard : storyHolder.linkLayoutView;
    }

    @Nullable
    private LinkSummaryOverlayController.StorySharedElements resolveLinkSummaryStorySharedElements(
            int position) {
        if (recyclerView == null) {
            return null;
        }
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (!(holder instanceof StoryRecyclerViewAdapter.StoryViewHolder)) {
            return null;
        }
        StoryRecyclerViewAdapter.StoryViewHolder storyHolder =
                (StoryRecyclerViewAdapter.StoryViewHolder) holder;
        ImageView preferred = adapter != null
                && SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(adapter.previewImageMode)
                ? storyHolder.largePreviewImage
                : storyHolder.smallPreviewImage;
        ImageView image = preferred;
        if (!isVisibleStoryPreviewImage(image)) {
            ImageView fallback = preferred == storyHolder.largePreviewImage
                    ? storyHolder.smallPreviewImage
                    : storyHolder.largePreviewImage;
            image = isVisibleStoryPreviewImage(fallback) ? fallback : null;
        }
        View title = isVisibleStorySharedElement(storyHolder.titleView)
                ? storyHolder.titleView : null;
        View summary = isVisibleStorySharedElement(storyHolder.summaryView)
                ? storyHolder.summaryView : null;
        View meta = isVisibleStorySharedElement(storyHolder.metaContainer)
                ? storyHolder.metaContainer : null;
        return new LinkSummaryOverlayController.StorySharedElements(
                image, title, summary, meta);
    }

    private boolean isVisibleStoryPreviewImage(@Nullable ImageView image) {
        return isVisibleStorySharedElement(image) && image.getDrawable() != null;
    }

    private boolean isVisibleStorySharedElement(@Nullable View view) {
        return view != null
                && view.getVisibility() == View.VISIBLE
                && view.getWidth() > 0
                && view.getHeight() > 0;
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
        StoryDisplaySettings.UpdateResult displayUpdate = displaySettings.applyToAdapter(adapter);

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

        if ((displayUpdate.fontChanged || fontCacheChanged) && typeSpinnerAdapter != null) {
            typeSpinnerAdapter.notifyDataSetChanged();
        }

        applyStatusBarProtection();

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

        if ((fontChanged || fontCacheChanged) && typeSpinnerAdapter != null) {
            typeSpinnerAdapter.notifyDataSetChanged();
        }
    }

    private void notifyStoryDisplaySettingsChanged(@Nullable StoryRecyclerViewAdapter targetAdapter) {
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
        int visibleStoryId = linkSummaryOverlayController.getVisibleStoryId();
        if (visibleStoryId != NO_PENDING_LINK_SUMMARY_STORY_ID) {
            outState.putInt(STATE_LINK_SUMMARY_STORY_ID, visibleStoryId);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        saveStoryStateForRecreation();
        pendingLinkSummaryStoryId = getActivity() != null
                && requireActivity().isChangingConfigurations()
                && linkSummaryOverlayController.getVisibleStoryId() != NO_PENDING_LINK_SUMMARY_STORY_ID
                ? linkSummaryOverlayController.getVisibleStoryId()
                : NO_PENDING_LINK_SUMMARY_STORY_ID;
        linkSummaryOverlayController.removeNow();
        linkSummaryBackCallback = null;
        View rootView = getView();

        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, null);
        }
        if (appBarLayout != null) {
            if (appBarOffsetChangedListener != null) {
                appBarLayout.removeOnOffsetChangedListener(appBarOffsetChangedListener);
            }
            clearAppBarDragCallback();
        }
        if (mainRecyclerView != null || searchRecyclerView != null) {
            cancelSearchContentExitAnimation(false);
            if (mainRecyclerView != null) {
                if (recyclerViewScrollListener != null) {
                    mainRecyclerView.removeOnScrollListener(recyclerViewScrollListener);
                }
                mainRecyclerView.setAdapter(null);
                mainRecyclerView.setLayoutManager(null);
            }
            if (searchRecyclerView != null) {
                if (searchRecyclerViewScrollListener != null) {
                    searchRecyclerView.removeOnScrollListener(searchRecyclerViewScrollListener);
                }
                searchRecyclerView.setAdapter(null);
                searchRecyclerView.setLayoutManager(null);
            }
            unregisterStoryAdapterDataObservers();
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(null);
            swipeRefreshLayout.setOnChildScrollUpCallback(null);
        }
        if (typeSpinner != null) {
            typeSpinner.setOnItemSelectedListener(null);
            typeSpinner.setAdapter(null);
        }
        if (searchEditText != null) {
            searchEditText.setOnEditorActionListener(null);
            searchEditText.setOnKeyListener(null);
        }
        if (userItemFilterGroup != null && userItemFilterCheckedListener != null) {
            userItemFilterGroup.removeOnButtonCheckedListener(userItemFilterCheckedListener);
        }
        if (storyUpdateListener != null) {
            StoryUpdate.clearStoryUpdatedListener(storyUpdateListener);
        }

        if (queue != null) {
            storyListGeneration++;
            clearLoadingStoryState();
            resetPreviewImagePrefetchRamp();
            invalidateAlgoliaLoad();
            queue.cancelAll(requestTag);
        }

        clearViewReferences();

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (bookmarkPreferences != null) {
            bookmarkPreferences.unregisterOnSharedPreferenceChangeListener(
                    bookmarkPreferenceChangeListener);
            bookmarkPreferences = null;
        }
        super.onDestroy();
    }

    private void clearAppBarDragCallback() {
        CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        if (layoutParams.getBehavior() instanceof AppBarLayout.Behavior) {
            ((AppBarLayout.Behavior) layoutParams.getBehavior()).setDragCallback(null);
        }
    }

    private void clearViewReferences() {
        if (storyCacheController != null) {
            storyCacheController.clearViewReferences();
            storyCacheController = null;
        }

        binding = null;
        headerBinding = null;
        swipeRefreshLayout = null;
        updateFab = null;
        mainRecyclerView = null;
        searchRecyclerView = null;
        recyclerView = null;
        appBarLayout = null;
        appBarOffsetChangedListener = null;
        recyclerViewScrollListener = null;
        searchRecyclerViewScrollListener = null;
        storyAdapterDataObserver = null;
        searchStoryAdapterDataObserver = null;
        userItemFilterCheckedListener = null;
        storyUpdateListener = null;

        headerContainer = null;
        typeSpinner = null;
        spinnerContainer = null;
        searchContainer = null;
        searchBar = null;
        searchEditText = null;
        searchOptionsScroll = null;
        searchSortChip = null;
        searchDateChip = null;
        searchPointsChip = null;
        searchCommentsChip = null;
        searchOnlyClickedChip = null;
        searchButton = null;
        closeSearchButton = null;
        moreButton = null;
        lastUpdatedHeaderText = null;
        cacheProgressStatusText = null;
        cacheProgressIndicator = null;
        frontPageDateControls = null;
        frontPagePreviousDayButton = null;
        frontPageDateButton = null;
        frontPageNextDayButton = null;
        userItemFilterGroup = null;
        loadingIndicator = null;
        loadingFailedLayout = null;
        loadingFailedText = null;
        loadingFailedAlgoliaLayout = null;
        noBookmarksLayout = null;
        noBookmarksImage = null;
        noBookmarksText = null;
        searchEmptyContainer = null;
        retryButton = null;
        showCachedButton = null;

        mainAdapter = null;
        searchAdapter = null;
        typeSpinnerAdapter = null;
        mainLinearLayoutManager = null;
        searchLinearLayoutManager = null;
        linearLayoutManager = null;
        defaultMainStoryItemAnimator = null;
        defaultSearchStoryItemAnimator = null;
        defaultStoryItemAnimator = null;
    }

    private void clickedComments(int position) {
        // prevent double clicks
        long now = System.currentTimeMillis();
        if (now - lastClick > CLICK_INTERVAL) {
            lastClick = now;
        } else {
            return;
        }

        if (position == RecyclerView.NO_POSITION) {
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
        if (position == RecyclerView.NO_POSITION) {
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
            for (String phrase : filterDomains) {
                if (TextUtils.isEmpty(phrase)) {
                    continue;
                }

                try {
                    String domain = Utils.getDomainName(story.url);
                    if (domain.toLowerCase().contains(phrase.toLowerCase())) {
                        return true;
                    }
                } catch (Exception e) {
                    // Nothing
                }
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

        if (story.loaded) {
            int index = stories.indexOf(story);
            if (index >= 0 && shouldFilterLoadedStory(story)) {
                removeStoryAt(index, loadGeneration, true);
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
                            removeStoryAt(index, loadGeneration, true);
                            return;
                        }

                        finishPaginationLoadMoreStory(story, loadGeneration);

                        if (story.isComment && currentTypeUsesCommentRows()) {
                            loadCommentMaster(story, story.parentId, 0, loadGeneration);
                        }

                        if (currentTypeUsesSavedItemFilter() && !shouldShowStoryForSavedItemFilter(story)) {
                            removeStoryAt(index, loadGeneration, true);
                            updateHeader();
                            return;
                        }

                        if (shouldFilterLoadedStory(story)) {
                            removeStoryAt(index, loadGeneration, true);
                            return;
                        }

                        Context context = getContext();
                        if (context != null) {
                            requestPreviewImagePrefetch(context, story);
                        }

                        notifyLoadedStoryChangedWhenIdle(story, loadGeneration);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Utils.log("Failed to load story with id: " + story.id);
                        story.loadingFailed = true;
                        finishPaginationLoadMoreStory(story, loadGeneration);
                        updatePreviewImagePrefetchRampCompletion();
                        notifyLoadedStoryChangedWhenIdle(story, loadGeneration);
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
                notifyLoadedStoryChangedWhenIdle(story, loadGeneration);
                loadStory(story, attempt + 1, loadGeneration);
            }
        });

        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private void notifyLoadedStoryChangedWhenIdle(Story story, int loadGeneration) {
        notifyLoadedStoryChangedWhenIdle(
                story,
                loadGeneration,
                stories,
                adapter,
                recyclerView,
                0);
    }

    private void notifyLoadedStoryChangedWhenIdle(
            Story story,
            int loadGeneration,
            List<Story> expectedStories,
            StoryRecyclerViewAdapter expectedAdapter,
            RecyclerView expectedRecyclerView,
            int deferredFrameCount) {
        if (!isCurrentStoryListGeneration(loadGeneration)
                || stories != expectedStories
                || adapter != expectedAdapter
                || recyclerView != expectedRecyclerView
                || expectedAdapter == null
                || expectedRecyclerView == null
                || expectedRecyclerView.getAdapter() != expectedAdapter) {
            return;
        }

        int index = expectedStories.indexOf(story);
        if (index < 0 || index >= expectedAdapter.getItemCount()) {
            return;
        }

        if (expectedRecyclerView.isComputingLayout()) {
            if (deferredFrameCount >= 3) {
                // The Story object is already updated. Avoid crashing if RecyclerView retained a
                // poisoned layout counter; a later bind will still display the current data.
                Log.w(TAG, "Skipping story row notification while RecyclerView remains in layout");
                return;
            }

            expectedRecyclerView.postOnAnimation(() ->
                    notifyLoadedStoryChangedWhenIdle(
                            story,
                            loadGeneration,
                            expectedStories,
                            expectedAdapter,
                            expectedRecyclerView,
                            deferredFrameCount + 1));
            return;
        }

        expectedAdapter.notifyItemChanged(index);
    }

    public void moreClick(View view) {
        PopupMenu popup = new PopupMenu(requireActivity(), view);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.menu_settings) {
                    requireActivity().startActivity(new Intent(requireActivity(), SettingsActivity.class));
                } else if (item.getItemId() == R.id.menu_log) {
                    if (TextUtils.isEmpty(AccountUtils.getAccountUsername(requireActivity()))) {
                        AccountUtils.showLoginPrompt(requireActivity().getSupportFragmentManager());
                    } else {
                        AccountUtils.deleteAccountDetails(requireActivity());
                        refreshTypeSpinnerItemsIfNeeded();
                        Toast.makeText(getContext(), "Logged out", Toast.LENGTH_SHORT).show();
                    }
                } else if (item.getItemId() == R.id.menu_profile) {
                    UserDialogFragment.showUserDialog(requireActivity().getSupportFragmentManager(), AccountUtils.getAccountUsername(requireActivity()));
                } else if (item.getItemId() == R.id.menu_cache) {
                    CacheStoriesDialogFragment.show(getParentFragmentManager());
                } else if (item.getItemId() == R.id.menu_submit) {
                    Intent submitIntent = new Intent(getContext(), ComposeActivity.class);
                    submitIntent.putExtra(ComposeActivity.EXTRA_TYPE, ComposeActivity.TYPE_POST);
                    startActivity(submitIntent);
                } else if (item.getItemId() == R.id.menu_clear_history) {
                    HistoriesUtils.INSTANCE.clearHistories(requireContext());
                    loadingFailed = false;
                    loadingFailedServerError = false;
                    loadingFailedRateLimited = false;
                    clearStories();
                    updateHeader();
                }
                return true;
            }
        });
        popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());

        Menu menu = popup.getMenu();

        boolean loggedIn = !TextUtils.isEmpty(AccountUtils.getAccountUsername(requireActivity()));

        menu.findItem(R.id.menu_log).setTitle(loggedIn ? "Log out" : "Log in");
        menu.findItem(R.id.menu_profile).setVisible(loggedIn);
        menu.findItem(R.id.menu_submit).setVisible(loggedIn);
        //first only show cache button if we're not already looking at the cache
        boolean cacheInProgress = storyCacheController != null && storyCacheController.isCachingStories();
        menu.findItem(R.id.menu_cache).setVisible(!showingCached && !cacheInProgress);
        menu.findItem(R.id.menu_clear_history).setVisible(isHistoryType(adapter.type) && HistoriesUtils.INSTANCE.size() > 0);
        //also if we don't have internet, no need to show at all
        if (getContext() != null) {
            if (!Utils.isNetworkAvailable(getContext())) {
                menu.findItem(R.id.menu_cache).setVisible(false);
            }
        }

        popup.show();
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

        swipeRefreshLayout.setRefreshing(showSwipeRefreshIndicator && !showMainLoadingIndicator);

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
            appBarLayout.setExpanded(true, false);
            recyclerView.scrollToPosition(0);
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
            swipeRefreshLayout.setRefreshing(false);

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
            swipeRefreshLayout.setRefreshing(false);

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
            swipeRefreshLayout.setRefreshing(false);
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
                    swipeRefreshLayout.setRefreshing(false);
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
            swipeRefreshLayout.setRefreshing(false);
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
            swipeRefreshLayout.setRefreshing(false);
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

                swipeRefreshLayout.setRefreshing(false);
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

                swipeRefreshLayout.setRefreshing(false);
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
            swipeRefreshLayout.setRefreshing(false);
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

                swipeRefreshLayout.setRefreshing(false);
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

                swipeRefreshLayout.setRefreshing(false);
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
                            adapter.notifyItemChanged(index);
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
            swipeRefreshLayout.setRefreshing(false);
            userItemListInitialLoadInProgress = false;
            updateHeader();
            return;
        }

        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(getParentFragmentManager());
            swipeRefreshLayout.setRefreshing(false);
            userItemListInitialLoadInProgress = false;
            loadingFailed = stories.isEmpty();
            loadingFailedRateLimited = false;
            updateHeader();
            return;
        }

        UserItemListRepository.Source syncSource = getCurrentUserItemListSource();
        boolean upvotedTypeForSync = syncSource == UserItemListRepository.Source.UPVOTED;
        userItemListInitialLoadInProgress = stories.isEmpty() && !showSwipeRefreshIndicator;
        swipeRefreshLayout.setRefreshing(showSwipeRefreshIndicator);
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
                swipeRefreshLayout.setRefreshing(false);
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

                swipeRefreshLayout.setRefreshing(false);
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
        if (recyclerView == null) {
            return;
        }

        recyclerView.post(this::prefetchLoadedPreviewImagesNearViewport);
    }

    private void prefetchLoadedPreviewImagesNearViewport() {
        if (linearLayoutManager == null) {
            return;
        }

        prefetchLoadedPreviewImagesNearViewport(
                linearLayoutManager.findFirstVisibleItemPosition(),
                linearLayoutManager.findLastVisibleItemPosition()
        );
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

        int firstIndex = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.max(0, firstVisibleItem);
        int lastIndex = lastVisibleItem == RecyclerView.NO_POSITION
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

        updateSwipeRefreshEnabled();

        if (searching) {
            useMainStoryList();
            saveStoriesBeforeSearch();

            // cancel all ongoing
            storyListGeneration++;
            clearLoadingStoryState();
            resetPreviewImagePrefetchRamp();
            invalidateAlgoliaLoad();
            queue.cancelAll(requestTag);
            swipeRefreshLayout.setRefreshing(false);
            loadingFailed = false;
            loadingFailedServerError = false;
            loadingFailedRateLimited = false;
            useSearchStoryList();
            searchAdapter.type = mainAdapter.type;
            updateAdapterCommentRows();
            clearStoriesForSearchEntry();
            appBarLayout.setExpanded(true, false);
        } else {
            boolean deferSearchRecyclerClear = deferSearchRecyclerClearForReturnAnimation;
            shouldRefreshAfterRestore = loadPendingBeforeSearch
                    && storiesBeforeSearch != null
                    && storiesBeforeSearch.isEmpty();
            loadPendingBeforeSearch = false;

            storyListGeneration++;
            clearLoadingStoryState();
            resetPreviewImagePrefetchRamp();
            invalidateAlgoliaLoad();
            queue.cancelAll(requestTag);
            swipeRefreshLayout.setRefreshing(false);
            useMainStoryList();
            restoredStories = restoreStoriesBeforeSearch();

            if (!restoredStories) {
                clearStories();
            }
            useSearchStoryList();
            if (!deferSearchRecyclerClear) {
                clearStoriesWithoutItemAnimations();
            }
            useMainStoryList();
            if (!deferSearchRecyclerClear) {
                applySearchRecyclerVisibility(false);
            }
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
        finishSearchContentExitAnimationIfNeeded();
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
        saveAlgoliaLoadMoreScrollPosition();
        if (adapter != null && adapter.paginationMode) {
            algoliaLoadMoreVisibleStoryCount = adapter.visibleStoryCount + StoryRecyclerViewAdapter.PAGINATION_PAGE_SIZE;
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
        swipeRefreshLayout.setRefreshing(false);
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

        updateSwipeRefreshEnabled();
        swipeRefreshLayout.setRefreshing(!searching && showSwipeRefreshIndicator);
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
                            swipeRefreshLayout.setRefreshing(false);
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
                            algoliaLoadMoreFirstVisiblePosition = RecyclerView.NO_POSITION;
                            algoliaLoadMoreFirstVisibleTop = 0;
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
                            algoliaLoadMoreFirstVisiblePosition = RecyclerView.NO_POSITION;
                            algoliaLoadMoreFirstVisibleTop = 0;
                            swipeRefreshLayout.setRefreshing(false);
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
            algoliaLoadMoreFirstVisiblePosition = RecyclerView.NO_POSITION;
            algoliaLoadMoreFirstVisibleTop = 0;

            if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                loadingFailedServerError = true;
            }
            loadingFailedRateLimited = isRateLimitedError(error);

            error.printStackTrace();
            swipeRefreshLayout.setRefreshing(false);
            loadingFailed = true;
            updateHeader();
        });

        updateHeader();

        stringRequest.setShouldCache(false);
        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private boolean shouldLockRecyclerScroll() {
        if (searching && stories != null && stories.isEmpty()) {
            return true;
        }

        if (recyclerView == null || adapter == null) {
            return false;
        }

        if (adapter.getItemCount() == 0) {
            return true;
        }

        if (!ViewCompat.isLaidOut(recyclerView)) {
            return false;
        }

        return visibleStoryContentFitsInRecyclerView();
    }

    private boolean visibleStoryContentFitsInRecyclerView() {
        if (linearLayoutManager == null || recyclerView.getChildCount() == 0) {
            return false;
        }

        int firstVisibleItem = linearLayoutManager.findFirstVisibleItemPosition();
        int lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
        if (firstVisibleItem != 0 || lastVisibleItem != adapter.getItemCount() - 1) {
            return false;
        }

        View firstChild = linearLayoutManager.findViewByPosition(firstVisibleItem);
        View lastChild = linearLayoutManager.findViewByPosition(lastVisibleItem);
        if (firstChild == null || lastChild == null) {
            return false;
        }

        int contentTop = linearLayoutManager.getDecoratedTop(firstChild);
        int contentBottom = linearLayoutManager.getDecoratedBottom(lastChild);
        Rect visibleBounds = new Rect();
        if (!recyclerView.getLocalVisibleRect(visibleBounds)) {
            return false;
        }

        int viewportTop = Math.max(recyclerView.getPaddingTop(), visibleBounds.top);
        int bottomClearance = recyclerView.getPaddingBottom();
        WindowInsetsCompat rootWindowInsets = ViewCompat.getRootWindowInsets(recyclerView);
        if (rootWindowInsets != null) {
            bottomClearance = Math.max(
                    bottomClearance,
                    rootWindowInsets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom);
        }
        int viewportBottom = visibleBounds.bottom - bottomClearance;

        return contentTop >= viewportTop && contentBottom <= viewportBottom;
    }

    private void requestRecyclerScrollStateUpdate() {
        refreshRecyclerScrollState();

        if (recyclerView != null) {
            recyclerView.post(this::refreshRecyclerScrollState);
        }
    }

    private void refreshRecyclerScrollState() {
        applyStoriesRecyclerPadding();
        updateRecyclerScrollState();
    }

    private void updateRecyclerScrollState() {
        if (recyclerView == null) {
            return;
        }

        boolean lockRecyclerScroll = shouldLockRecyclerScroll();
        recyclerView.setNestedScrollingEnabled(!lockRecyclerScroll);
        recyclerView.setOverScrollMode(lockRecyclerScroll ? View.OVER_SCROLL_NEVER : View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        if (lockRecyclerScroll) {
            recyclerView.stopScroll();
            recyclerView.scrollToPosition(0);
            appBarLayout.setExpanded(true, false);
        }
    }

    private void registerStoryAdapterDataObservers() {
        if (mainAdapter == null || searchAdapter == null) {
            return;
        }

        unregisterStoryAdapterDataObservers();
        storyAdapterDataObserver = createStoryAdapterDataObserver();
        searchStoryAdapterDataObserver = createStoryAdapterDataObserver();
        mainAdapter.registerAdapterDataObserver(storyAdapterDataObserver);
        searchAdapter.registerAdapterDataObserver(searchStoryAdapterDataObserver);
    }

    private void unregisterStoryAdapterDataObservers() {
        if (mainAdapter != null && storyAdapterDataObserver != null) {
            mainAdapter.unregisterAdapterDataObserver(storyAdapterDataObserver);
        }
        if (searchAdapter != null && searchStoryAdapterDataObserver != null) {
            searchAdapter.unregisterAdapterDataObserver(searchStoryAdapterDataObserver);
        }
        storyAdapterDataObserver = null;
        searchStoryAdapterDataObserver = null;
    }

    private RecyclerView.AdapterDataObserver createStoryAdapterDataObserver() {
        return new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                requestRecyclerScrollStateUpdate();
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                requestRecyclerScrollStateUpdate();
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
                requestRecyclerScrollStateUpdate();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                requestRecyclerScrollStateUpdate();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                requestRecyclerScrollStateUpdate();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                requestRecyclerScrollStateUpdate();
            }
        };
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

        if (typeSpinnerAdapter != null && type < typeSpinnerAdapter.getCount()) {
            return typeSpinnerAdapter.getItem(type);
        }

        Context ctx = getContext();
        if (ctx == null) {
            return null;
        }

        ArrayList<CharSequence> typeAdapterList = buildTypeAdapterList(ctx);
        return type < typeAdapterList.size() ? typeAdapterList.get(type) : null;
    }

    private int getTypeIndex(@Nullable CharSequence label) {
        if (label == null || typeSpinnerAdapter == null) {
            return -1;
        }

        for (int i = 0; i < typeSpinnerAdapter.getCount(); i++) {
            if (TextUtils.equals(label, typeSpinnerAdapter.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    private void updateAdapterCommentRows() {
        updateAdapterCommentRows(mainAdapter);
        updateAdapterCommentRows(searchAdapter);
        if (recyclerView != null) {
            if (recyclerView.getItemAnimator() != null) {
                recyclerView.getItemAnimator().endAnimations();
            }
            recyclerView.stopScroll();
            if (recyclerView.getItemAnimator() == null && defaultStoryItemAnimator != null) {
                recyclerView.setItemAnimator(defaultStoryItemAnimator);
            }
        }
    }

    private void updateAdapterCommentRows(@Nullable StoryRecyclerViewAdapter targetAdapter) {
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
            closeSearch(null);
            return true;
        }
        return false;
    }

    public void startSearchBackProgress(float progress) {
        if (!searching) {
            return;
        }

        cancelSearchOptionsAnimation();
        predictiveSearchBackInProgress = true;
        predictiveSearchBackShowingMainHeader = false;
        applySearchRecyclerVisibility(true);
        appBarLayout.setExpanded(true, false);
        applySearchBackVisualProgress(progress);
    }

    public void updateSearchBackProgress(float progress) {
        if (!searching) {
            return;
        }

        if (!predictiveSearchBackInProgress) {
            startSearchBackProgress(progress);
            return;
        }

        applySearchBackVisualProgress(progress);
    }

    public void cancelSearchBackProgress() {
        if (!predictiveSearchBackInProgress) {
            return;
        }

        finishSearchBackFromCurrentVisualState = false;
        predictiveSearchBackInProgress = false;
        predictiveSearchBackShowingMainHeader = false;
        predictiveSearchBackProgress = 0f;
        useSearchStoryList();
        updateHeader(false);
        resetSearchBackVisualAlphas();
        resetSearchBackContentVisualState();
        appBarLayout.setExpanded(true, false);
    }

    public boolean finishSearchBackProgress() {
        if (!searching) {
            finishSearchBackFromCurrentVisualState = false;
            return false;
        }

        suppressNextSearchRestoreAnimations = true;
        skipNextSearchRestoreDataSwap = false;
        skipNextSearchContentReturnAnimation = false;
        finishSearchBackFromCurrentVisualState = predictiveSearchBackInProgress;
        predictiveSearchBackInProgress = false;
        predictiveSearchBackShowingMainHeader = false;
        return exitSearch();
    }

    private void applySearchBackVisualProgress(float progress) {
        predictiveSearchBackProgress = Math.max(0f, Math.min(1f, progress));

        boolean showMainHeader = predictiveSearchBackProgress >= SEARCH_BACK_HEADER_SWITCH_PROGRESS;
        if (showMainHeader != predictiveSearchBackShowingMainHeader) {
            predictiveSearchBackShowingMainHeader = showMainHeader;
            applySearchBackHeaderMode(showMainHeader);
        }

        applySearchRecyclerVisibility(true);

        if (showMainHeader) {
            float mainAlpha = (predictiveSearchBackProgress - SEARCH_BACK_HEADER_SWITCH_PROGRESS)
                    / (1f - SEARCH_BACK_HEADER_SWITCH_PROGRESS);
            setSearchBackMainHeaderAlpha(mainAlpha);
            setSearchBackSearchHeaderAlpha(0f);
            setSearchBackContentVisualState(
                    0f,
                    getSearchBackContentTranslation(1f),
                    mainAlpha,
                    getSearchBackContentTranslation(1f - mainAlpha)
            );
        } else {
            float searchProgress = predictiveSearchBackProgress / SEARCH_BACK_HEADER_SWITCH_PROGRESS;
            float searchAlpha = 1f - searchProgress;
            setSearchBackSearchHeaderAlpha(searchAlpha);
            setSearchBackMainHeaderAlpha(0f);
            setSearchBackContentVisualState(
                    searchAlpha,
                    getSearchBackContentTranslation(searchProgress),
                    0f,
                    getSearchBackContentTranslation(1f)
            );
        }
    }

    private void applySearchBackHeaderMode(boolean showMainHeader) {
        Context ctx = getContext();
        if (ctx != null) {
            applyHeaderPadding(ctx, !showMainHeader);
        }

        moreButton.setVisibility(showMainHeader ? View.VISIBLE : View.GONE);
        spinnerContainer.setVisibility(showMainHeader ? View.VISIBLE : View.GONE);
        searchButton.setVisibility(showMainHeader ? View.VISIBLE : View.GONE);
        closeSearchButton.setVisibility(showMainHeader ? View.GONE : View.VISIBLE);
        searchContainer.setVisibility(showMainHeader ? View.GONE : View.VISIBLE);
        searchOptionsScroll.setVisibility(showMainHeader ? View.GONE : View.VISIBLE);
        userItemFilterGroup.setVisibility(showMainHeader && shouldShowUserItemFilterGroupInMain()
                ? View.VISIBLE
                : View.GONE);
        frontPageDateControls.setVisibility(showMainHeader && shouldShowFrontPageDateControlsInMain()
                ? View.VISIBLE
                : View.GONE);
    }

    private void setSearchBackSearchHeaderAlpha(float alpha) {
        searchContainer.setAlpha(alpha);
        searchOptionsScroll.setAlpha(alpha);
        closeSearchButton.setAlpha(alpha);
    }

    private void setSearchBackMainHeaderAlpha(float alpha) {
        spinnerContainer.setAlpha(1f);
        typeSpinner.setAlpha(alpha);
        searchButton.setAlpha(alpha);
        moreButton.setAlpha(alpha);
        userItemFilterGroup.setAlpha(alpha);
        frontPageDateControls.setAlpha(alpha);
    }

    private void resetSearchBackVisualAlphas() {
        setSearchBackSearchHeaderAlpha(1f);
        setSearchBackMainHeaderAlpha(1f);
    }

    private long getSearchBackFinishAnimationDuration(float progress) {
        float finishProgress = Math.max(0f, Math.min(1f, progress));
        float remainingProgress = 1f;
        if (finishProgress >= SEARCH_BACK_HEADER_SWITCH_PROGRESS) {
            remainingProgress = 1f - ((finishProgress - SEARCH_BACK_HEADER_SWITCH_PROGRESS)
                    / (1f - SEARCH_BACK_HEADER_SWITCH_PROGRESS));
        }

        return Math.max(
                SEARCH_CONTENT_RETURN_ANIMATION_DURATION_MS / 3,
                Math.round(SEARCH_CONTENT_RETURN_ANIMATION_DURATION_MS * remainingProgress)
        );
    }

    private void animateSearchBackHeaderToMain(long duration) {
        PathInterpolator interpolator = new PathInterpolator(0.2f, 0f, 0f, 1f);
        typeSpinner.animate().cancel();
        searchButton.animate().cancel();
        moreButton.animate().cancel();
        userItemFilterGroup.animate().cancel();
        frontPageDateControls.animate().cancel();

        typeSpinner.setVisibility(View.VISIBLE);
        searchButton.setVisibility(View.VISIBLE);
        moreButton.setVisibility(View.VISIBLE);
        userItemFilterGroup.setVisibility(shouldShowUserItemFilterGroupInMain() ? View.VISIBLE : View.GONE);
        frontPageDateControls.setVisibility(shouldShowFrontPageDateControlsInMain() ? View.VISIBLE : View.GONE);

        typeSpinner.animate()
                .alpha(1f)
                .setStartDelay(0)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start();
        searchButton.animate()
                .alpha(1f)
                .setStartDelay(0)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start();
        moreButton.animate()
                .alpha(1f)
                .setStartDelay(0)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction(this::resetSearchBackVisualAlphas)
                .start();
        userItemFilterGroup.animate()
                .alpha(1f)
                .setStartDelay(0)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start();
        frontPageDateControls.animate()
                .alpha(1f)
                .setStartDelay(0)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .start();
    }

    private boolean shouldShowUserItemFilterGroupInMain() {
        return currentTypeUsesSavedItemFilter() && currentSavedItemSourceHasItems();
    }

    private boolean shouldShowFrontPageDateControlsInMain() {
        return currentTypeIsFront();
    }

    private float getSearchBackContentTranslation(float progress) {
        return Utils.pxFromDp(getResources(), SEARCH_BACK_CONTENT_TRANSLATION_DP)
                * Math.max(0f, Math.min(1f, progress));
    }

    private void setSearchBackContentVisualState(float searchAlpha,
                                                 float searchTranslationY,
                                                 float mainAlpha,
                                                 float mainTranslationY) {
        if (searchRecyclerView != null) {
            searchRecyclerView.setAlpha(searchAlpha);
            searchRecyclerView.setTranslationY(searchTranslationY);
        }
        if (mainRecyclerView != null) {
            mainRecyclerView.setAlpha(mainAlpha);
            mainRecyclerView.setTranslationY(mainTranslationY);
        }
    }

    private void resetSearchBackContentVisualState() {
        applySearchRecyclerVisibility(false);
    }

    private void showCachedStories() {
        showingCached = true;
        swipeRefreshLayout.setRefreshing(false);

        replaceStories(Utils.loadCachedStories(getContext()));
        loadedTo = stories.size() - 1;
        loadingFailed = false;
        loadingFailedServerError = false;
        loadingFailedRateLimited = false;
        updateHeader();
    }

    private void updateSwipeRefreshEnabled() {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(!searching && !showingCached);
        }
    }

    private void hideUpdateButton() {
        updateButtonShowing = false;
        Context ctx = getContext();
        beginLastUpdatedHeaderTransitionIfNeeded(ctx);
        updateLastUpdatedHeader(ctx);
        applyHeaderPadding(ctx);

        if (updateFab != null && updateFab.getVisibility() == View.VISIBLE) {
            updateFab.hide();
        }
        applyStoriesRecyclerPadding();
    }

    private void showUpdateButton() {
        updateButtonShowing = true;
        Context ctx = getContext();
        beginLastUpdatedHeaderTransitionIfNeeded(ctx);
        updateLastUpdatedHeader(ctx);
        applyHeaderPadding(ctx);

        if (updateFab != null && updateFab.getVisibility() != View.VISIBLE) {
            updateFab.show();
        }
        applyStoriesRecyclerPadding();
    }

    private void openComments(Story story, int pos, boolean showWebsite) {
        storyClickListener.openStory(story, pos, showWebsite);
    }

    private interface SearchOptionSelectedListener {
        void onSearchOptionSelected(int selectedIndex);
    }

    public interface StoryClickListener {
        void openStory(Story story, int pos, boolean showWebsite);
    }
}
