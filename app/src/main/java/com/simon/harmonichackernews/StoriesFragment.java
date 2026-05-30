package com.simon.harmonichackernews;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.search.SearchBar;
import com.simon.harmonichackernews.adapters.StoryRecyclerViewAdapter;
import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.data.History;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.BackgroundJSONParser;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.HistoriesUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.SearchRelevanceUtils;
import com.simon.harmonichackernews.utils.StoryUpdate;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.UtilsKt;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Response;

public class StoriesFragment extends Fragment {

    private StoryClickListener storyClickListener;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ExtendedFloatingActionButton updateFab;
    private RecyclerView recyclerView;
    private AppBarLayout appBarLayout;
    private AppBarLayout.OnOffsetChangedListener appBarOffsetChangedListener;
    private RecyclerView.OnScrollListener recyclerViewScrollListener;
    private RecyclerView.AdapterDataObserver storyAdapterDataObserver;
    private MaterialButtonToggleGroup.OnButtonCheckedListener userItemFilterCheckedListener;
    private StoryUpdate.StoryUpdateListener storyUpdateListener;

    // Header views
    private LinearLayout headerContainer;
    private Spinner typeSpinner;
    private LinearLayout spinnerContainer;
    private View searchContainer;
    private SearchBar searchBar;
    private EditText searchEditText;
    private View searchOptionsScroll;
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
    private TextView showingCachedText;
    private LinearLayout searchEmptyContainer;
    private Button retryButton;
    private Button showCachedButton;

    private StoryRecyclerViewAdapter adapter;
    private ArrayAdapter<CharSequence> typeSpinnerAdapter;
    private List<Story> stories;
    private final ArrayList<Story> bookmarkStories = new ArrayList<>();
    private final ArrayList<Story> userItemListStories = new ArrayList<>();
    private Set<Integer> userItemListCommentIds = new HashSet<>();
    private RequestQueue queue;
    private final Object requestTag = new Object();
    private final Set<Integer> loadingStoryIds = new HashSet<>();
    private LinearLayoutManager linearLayoutManager;
    private ArrayList<String> filterWords;
    private ArrayList<String> filterDomains;
    private boolean hideJobs, alwaysOpenComments, hideClicked;
    private long historiesChangeVersion = -1L;
    private boolean searching = false;
    private boolean loadingFailed = false;
    private boolean loadingFailedServerError = false;
    private String lastSearch = "";
    private int searchSortIndex = 0;
    private int searchDateRangeIndex = 0;
    private int searchMinimumPointsIndex = 0;
    private int searchMinimumCommentsIndex = 0;
    private boolean searchOnlyClicked = false;
    private int algoliaRequestGeneration = 0;
    private int storyListGeneration = 0;
    private boolean algoliaLoading = false;
    private String activeAlgoliaUrl = null;
    private boolean algoliaLoadMoreInProgress = false;
    private int algoliaLoadMoreVisibleStoryCount = -1;
    private int algoliaLoadMoreFirstVisiblePosition = RecyclerView.NO_POSITION;
    private int algoliaLoadMoreFirstVisibleTop = 0;
    private static final int ALGOLIA_HITS_INCREMENT = 200;
    private List<Story> storiesBeforeSearch = null;
    private int loadedToBeforeSearch = -1;
    private int visibleStoryCountBeforeSearch = Integer.MAX_VALUE;
    private boolean showingCachedBeforeSearch = false;
    private boolean loadingFailedBeforeSearch = false;
    private boolean loadingFailedServerErrorBeforeSearch = false;
    private boolean showLoadMoreBeforeSearch = false;
    private int algoliaHitsPerPageBeforeSearch = ALGOLIA_HITS_INCREMENT;
    private int lastAlgoliaTopStoriesStartTimeBeforeSearch = 0;
    private boolean loadPendingBeforeSearch = false;

    public static boolean showingCached = false;

    private int loadedTo = -1;
    private boolean paginationMode = false;
    private static final int PAGINATION_PAGE_SIZE = 30;
    private static final int STORY_VISIBLE_PREFETCH_THRESHOLD = 17;
    private int algoliaHitsPerPage = ALGOLIA_HITS_INCREMENT;
    private int lastAlgoliaTopStoriesStartTime = 0;

    public final static String[] hnUrls = new String[]{Utils.URL_TOP, Utils.URL_NEW, Utils.URL_BEST, Utils.URL_ASK, Utils.URL_SHOW, Utils.URL_JOBS};

    long lastLoaded = 0;
    long lastClick = 0;
    private boolean updateButtonShowing = false;
    private final static long CLICK_INTERVAL = 350;
    private static final long SEARCH_HEADER_ANIMATION_DURATION_MS = 180;
    private static final long SEARCH_OPTIONS_ENTRANCE_ANIMATION_DELAY_MS = 100;
    private static final long SEARCH_OPTIONS_ENTRANCE_ANIMATION_DURATION_MS = 140;
    private static final long HEADER_LAYOUT_ANIMATION_DURATION_MS = 220;
    private static final long CACHE_PROGRESS_FINISHED_HOLD_MS = 1000;
    private static final long CACHE_PROGRESS_FADE_DURATION_MS = 140;
    private static final String CACHE_PROGRESS_STATUS_CACHING = "Caching stories";
    private static final String CACHE_PROGRESS_STATUS_FINISHED = "Finished";
    private static final String CACHE_PROGRESS_STATUS_FAILED = "Caching failed";
    private static final String CACHE_PROGRESS_STATUS_EMPTY = "No stories to cache";
    private static final float SEARCH_BACK_HEADER_SWITCH_PROGRESS = 0.5f;
    private static final String[] SEARCH_SORT_LABELS = new String[]{"Relevance", "Newest"};
    private static final String[] SEARCH_DATE_RANGE_LABELS = new String[]{"All time", "Past day", "Past week", "Past month", "Past year"};
    private static final int[] SEARCH_DATE_RANGE_DAYS = new int[]{0, 1, 7, 30, 365};
    private static final String[] SEARCH_MINIMUM_POINTS_LABELS = new String[]{"Any points", "5+ points", "25+ points", "100+ points"};
    private static final int[] SEARCH_MINIMUM_POINTS = new int[]{0, 5, 25, 100};
    private static final String[] SEARCH_MINIMUM_COMMENTS_LABELS = new String[]{"Any comments", "5+ comments", "25+ comments", "100+ comments"};
    private static final int[] SEARCH_MINIMUM_COMMENTS = new int[]{0, 5, 25, 100};
    private static final int USER_ITEM_LIST_FILTER_STORIES = 0;
    private static final int USER_ITEM_LIST_FILTER_BOTH = 1;
    private static final int USER_ITEM_LIST_FILTER_COMMENTS = 2;

    private int topInset = 0;
    private boolean predictiveSearchBackInProgress = false;
    private boolean predictiveSearchBackShowingMainHeader = false;
    private boolean predictiveSearchBackShowingMainContent = false;
    private float predictiveSearchBackProgress = 0f;
    private List<Story> predictiveSearchBackSearchStories = null;
    private int predictiveSearchBackLoadedTo = -1;
    private int predictiveSearchBackVisibleStoryCount = Integer.MAX_VALUE;
    private boolean predictiveSearchBackShowingCached = false;
    private boolean predictiveSearchBackLoadingFailed = false;
    private boolean predictiveSearchBackLoadingFailedServerError = false;
    private boolean predictiveSearchBackShowLoadMore = false;
    private boolean suppressNextSearchRestoreAnimations = false;
    private boolean skipNextSearchRestoreDataSwap = false;
    private boolean userItemListsDropdownVisible = false;
    private boolean userItemListInitialLoadInProgress = false;
    private int userItemListFilter = USER_ITEM_LIST_FILTER_BOTH;
    private RecyclerView.ItemAnimator defaultStoryItemAnimator;
    private boolean cachingStories = false;
    private boolean cacheProgressIndicatorVisible = false;
    private boolean cacheProgressHidePending = false;
    private int cacheProgressAnimationGeneration = 0;
    private int cacheStoriesTotal = 1;
    private int cacheStoriesCompleted = 0;
    private String cacheProgressStatus = CACHE_PROGRESS_STATUS_CACHING;

    public StoriesFragment() {
        super(R.layout.fragment_stories);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        HistoriesUtils.INSTANCE.init(requireContext());
        historiesChangeVersion = HistoriesUtils.INSTANCE.getChangeVersion();

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.stories_recyclerview);
        swipeRefreshLayout = view.findViewById(R.id.stories_swipe_refresh);
        updateFab = view.findViewById(R.id.stories_update_fab);
        appBarLayout = view.findViewById(R.id.stories_appbar);
        appBarOffsetChangedListener = (appBar, verticalOffset) -> {
            float totalScrollRange = appBar.getTotalScrollRange();
            if (totalScrollRange > 0) {
                headerContainer.setAlpha(1f - (Math.abs(verticalOffset) / totalScrollRange));
            }
        };
        appBarLayout.addOnOffsetChangedListener(appBarOffsetChangedListener);
        configureAppBarDragBehavior();

        // Bind header views
        headerContainer = view.findViewById(R.id.stories_header_container);
        typeSpinner = view.findViewById(R.id.stories_header_spinner);
        spinnerContainer = view.findViewById(R.id.stories_header_spinner_container);
        searchContainer = view.findViewById(R.id.stories_header_search_container);
        searchBar = view.findViewById(R.id.stories_header_search_bar);
        searchEditText = view.findViewById(R.id.stories_header_search_edittext);
        searchOptionsScroll = view.findViewById(R.id.stories_header_search_options_scroll);
        searchSortChip = view.findViewById(R.id.stories_header_search_sort_chip);
        searchDateChip = view.findViewById(R.id.stories_header_search_date_chip);
        searchPointsChip = view.findViewById(R.id.stories_header_search_points_chip);
        searchCommentsChip = view.findViewById(R.id.stories_header_search_comments_chip);
        searchOnlyClickedChip = view.findViewById(R.id.stories_header_search_only_clicked_chip);
        searchBar.setElevation(0f);
        searchEditText.bringToFront();
        searchButton = view.findViewById(R.id.stories_header_search_button);
        closeSearchButton = view.findViewById(R.id.stories_header_close_search_button);
        moreButton = view.findViewById(R.id.stories_header_more);
        lastUpdatedHeaderText = view.findViewById(R.id.stories_header_last_updated);
        cacheProgressStatusText = view.findViewById(R.id.stories_header_cache_status);
        cacheProgressIndicator = view.findViewById(R.id.stories_header_cache_progress);
        userItemFilterGroup = view.findViewById(R.id.stories_header_user_item_filter_group);
        loadingIndicator = view.findViewById(R.id.stories_header_loading_indicator);
        loadingFailedLayout = view.findViewById(R.id.stories_header_loading_failed);
        loadingFailedText = view.findViewById(R.id.stories_header_loading_failed_text);
        loadingFailedAlgoliaLayout = view.findViewById(R.id.stories_header_loading_failed_algolia);
        noBookmarksLayout = view.findViewById(R.id.stories_header_no_bookmarks);
        noBookmarksImage = view.findViewById(R.id.stories_header_no_bookmarks_icon);
        noBookmarksText = view.findViewById(R.id.stories_header_no_bookmarks_text);
        showingCachedText = view.findViewById(R.id.stories_header_cached_stories_header);
        searchEmptyContainer = view.findViewById(R.id.stories_header_search_empty_container);
        retryButton = view.findViewById(R.id.stories_header_retry_button);
        showCachedButton = view.findViewById(R.id.stories_header_show_cached);

        swipeRefreshLayout.setOnRefreshListener(() -> attemptRefresh(true));
        ViewUtils.setUpSwipeRefreshWithStatusBarOffset(swipeRefreshLayout);

        linearLayoutManager = new LinearLayoutManager(getContext()) {
            @Override
            public boolean canScrollVertically() {
                return !shouldLockRecyclerScroll() && super.canScrollVertically();
            }
        };
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(linearLayoutManager);
        defaultStoryItemAnimator = recyclerView.getItemAnimator();

        stories = new ArrayList<>();
        filterWords = Utils.getFilterWords(requireContext());
        filterDomains = Utils.getFilterDomains(requireContext());
        hideJobs = SettingsUtils.shouldHideJobs(requireContext());
        hideClicked = SettingsUtils.shouldHideClicked(requireContext());
        alwaysOpenComments = SettingsUtils.shouldAlwaysOpenComments(requireContext());
        setupAdapter();
        recyclerView.setAdapter(adapter);
        registerStoryAdapterDataObserver();

        // Setup header after adapter so spinner callback can safely access adapter.type
        setupHeader();

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

                // Apply bottom inset to RecyclerView so last item isn't behind nav bar
                recyclerView.setPadding(
                        recyclerView.getPaddingLeft(),
                        recyclerView.getPaddingTop(),
                        recyclerView.getPaddingRight(),
                        insets.bottom
                );

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(view);

        updateFab.setOnClickListener((v) -> {
            attemptRefresh();
            recyclerView.smoothScrollToPosition(0);
        });

        recyclerViewScrollListener = new RecyclerView.OnScrollListener() {

            @Override
            public void onScrolled(@NotNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                int firstVisibleItem = linearLayoutManager.findFirstVisibleItemPosition();
                int lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
                prefetchLoadedPreviewImagesNearViewport(firstVisibleItem, lastVisibleItem);

                // Only enable infinite scroll if pagination mode is OFF
                if (!searching && !paginationMode && !currentTypeIsAlgolia()) {
                    loadStoriesThroughIndex(Math.min(lastVisibleItem + STORY_VISIBLE_PREFETCH_THRESHOLD, stories.size()) - 1, storyListGeneration);
                }
            }
        };
        recyclerView.addOnScrollListener(recyclerViewScrollListener);

        queue = NetworkComponent.getRequestQueueInstance(requireContext());
        attemptRefresh();

        storyUpdateListener = new StoryUpdate.StoryUpdateListener() {
            @Override
            public void callback(Story story) {
                for (int i = 0; i < stories.size(); i++) {
                    if (story.id == stories.get(i).id) {
                        Story oldStory = stories.get(i);

                        if (!oldStory.title.equals(story.title) || oldStory.descendants != story.descendants || oldStory.score != story.score || oldStory.time != story.time || !oldStory.url.equals(story.url)) {
                            oldStory.title = story.title;
                            oldStory.descendants = story.descendants;
                            oldStory.score = story.score;
                            oldStory.time = story.time;
                            oldStory.url = story.url;

                            adapter.notifyItemChanged(i);
                        }
                        break;
                    }
                }
            }
        };
        StoryUpdate.setStoryUpdatedListener(storyUpdateListener);
    }

    private int getPreferredTypeIndex() {
        ArrayList<CharSequence> typeAdapterList = buildTypeAdapterList(getContext());
        int preferredIndex = typeAdapterList.indexOf(SettingsUtils.getPreferredStoryType(getContext()));
        return preferredIndex >= 0 ? preferredIndex : 0;
    }

    private ArrayList<CharSequence> buildTypeAdapterList(Context ctx) {
        String[] sortingOptions = getResources().getStringArray(R.array.sorting_options);
        ArrayList<CharSequence> typeAdapterList = new ArrayList<>(Arrays.asList(sortingOptions));
        if (shouldShowUserItemLists(ctx)) {
            int favoritesIndex = Math.min(SettingsUtils.getBookmarksIndex(getResources()) + 1, typeAdapterList.size());
            typeAdapterList.add(favoritesIndex, SettingsUtils.FAVORITES_LABEL);
            typeAdapterList.add(SettingsUtils.UPVOTED_LABEL);
        }
        return typeAdapterList;
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

    private void setupHeader() {
        final Context ctx = requireContext();

        // Tap empty header area to scroll to top
        headerContainer.setOnClickListener(v -> {
            recyclerView.smoothScrollToPosition(0);
            appBarLayout.setExpanded(true, true);
        });

        // Set up retry button
        retryButton.setOnClickListener(v -> attemptRefresh());
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
        searchSortChip.setOnClickListener(v -> showSearchOptionMenu(v, SEARCH_SORT_LABELS, searchSortIndex, selectedIndex -> {
            searchSortIndex = selectedIndex;
            updateSearchOptionChips();
            retrySearchWithCurrentOptions();
        }));
        searchDateChip.setOnClickListener(v -> showSearchOptionMenu(v, SEARCH_DATE_RANGE_LABELS, searchDateRangeIndex, selectedIndex -> {
            searchDateRangeIndex = selectedIndex;
            updateSearchOptionChips();
            retrySearchWithCurrentOptions();
        }));
        searchPointsChip.setOnClickListener(v -> showSearchOptionMenu(v, SEARCH_MINIMUM_POINTS_LABELS, searchMinimumPointsIndex, selectedIndex -> {
            searchMinimumPointsIndex = selectedIndex;
            updateSearchOptionChips();
            retrySearchWithCurrentOptions();
        }));
        searchCommentsChip.setOnClickListener(v -> showSearchOptionMenu(v, SEARCH_MINIMUM_COMMENTS_LABELS, searchMinimumCommentsIndex, selectedIndex -> {
            searchMinimumCommentsIndex = selectedIndex;
            updateSearchOptionChips();
            retrySearchWithCurrentOptions();
        }));
        searchOnlyClickedChip.setOnClickListener(v -> {
            searchOnlyClicked = !searchOnlyClicked;
            updateSearchOptionChips();
            retrySearchWithCurrentOptions();
        });
        updateSearchOptionChips();

        searchButton.setOnClickListener(view -> openSearch());
        closeSearchButton.setOnClickListener(view -> closeSearch(view));

        // Set up spinner
        userItemListsDropdownVisible = shouldShowUserItemLists(ctx);
        ArrayList<CharSequence> typeAdapterList = buildTypeAdapterList(ctx);
        typeSpinnerAdapter = new ArrayAdapter<>(ctx, R.layout.spinner_top_layout, R.id.selection_dropdown_item_textview, typeAdapterList);
        typeSpinnerAdapter.setDropDownViewResource(R.layout.spinner_item_layout);

        typeSpinner.setAdapter(typeSpinnerAdapter);
        typeSpinner.setSelection(getPreferredTypeIndex());
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i != adapter.type) {
                    adapter.type = i;
                    updateAdapterCommentRows();
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
        int sidePaddingStart = headerContainer.getPaddingStart();
        int sidePaddingEnd = headerContainer.getPaddingEnd();
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

        updateLastUpdatedHeader(ctx);
        applyHeaderPadding(ctx);

        beginHeaderTransition(animateSearchTransition);

        moreButton.setVisibility(searching ? View.GONE : View.VISIBLE);
        spinnerContainer.setVisibility(searching ? View.GONE : View.VISIBLE);
        searchButton.setVisibility(searching ? View.GONE : View.VISIBLE);
        closeSearchButton.setVisibility(searching ? View.VISIBLE : View.GONE);

        searchContainer.setVisibility(searching ? View.VISIBLE : View.GONE);
        searchOptionsScroll.setVisibility(searching ? View.VISIBLE : View.GONE);

        boolean bookmarksType = isBookmarksType(adapter.type);
        boolean favoritesType = isFavoritesType(adapter.type);
        boolean upvotedType = isUpvotedType(adapter.type);
        boolean userItemListType = favoritesType || upvotedType;
        boolean savedItemSourceHasItems = currentSavedItemSourceHasItems();
        userItemFilterGroup.setVisibility(!searching && currentTypeUsesSavedItemFilter() && savedItemSourceHasItems ? View.VISIBLE : View.GONE);
        if (noBookmarksImage != null && noBookmarksText != null) {
            noBookmarksImage.setImageResource(getEmptySavedListIcon(favoritesType, upvotedType));
            noBookmarksText.setText(getEmptySavedListText(favoritesType, upvotedType, savedItemSourceHasItems));
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
                    && (bookmarksType || (userItemListType && !userItemListInitialLoadInProgress && !swipeRefreshLayout.isRefreshing()));
            noBookmarksLayout.setVisibility(showEmptySavedList ? View.VISIBLE : View.GONE);
            searchEmptyContainer.setVisibility(View.GONE);

            boolean showLoading = stories.isEmpty()
                    && !loadingFailed
                    && !loadingFailedServerError
                    && !bookmarksType
                    && (!userItemListType || userItemListInitialLoadInProgress);
            loadingIndicator.setVisibility(showLoading ? View.VISIBLE : View.GONE);
        }

        showingCachedText.setVisibility(showingCached && !searching ? View.VISIBLE : View.GONE);

        typeSpinner.setSelection(adapter.type);

        TooltipCompat.setTooltipText(searchButton, "Search");
        TooltipCompat.setTooltipText(closeSearchButton, "Close");
        TooltipCompat.setTooltipText(moreButton, "More");

        loadingFailedLayout.setVisibility(loadingFailed ? View.VISIBLE : View.GONE);
        if (loadingFailed) {
            if (!Utils.isNetworkAvailable(ctx)) {
                loadingFailedText.setText("No internet connection");
            } else {
                loadingFailedText.setText("Loading failed");
            }
        }

        showCachedButton.setVisibility(loadingFailed && !searching && Utils.hasCachedStories(ctx) ? View.VISIBLE : View.GONE);
        updateCacheProgressIndicator();

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

    private int getEmptySavedListIcon(boolean favoritesType, boolean upvotedType) {
        if (favoritesType) {
            return R.drawable.ic_action_star;
        }
        if (upvotedType) {
            return R.drawable.ic_action_thumbs_up;
        }
        return R.drawable.ic_action_bookmark_border;
    }

    private String getEmptySavedListText(boolean favoritesType, boolean upvotedType, boolean savedItemSourceHasItems) {
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

        searchSortChip.setText(SEARCH_SORT_LABELS[searchSortIndex]);
        searchDateChip.setText(SEARCH_DATE_RANGE_LABELS[searchDateRangeIndex]);
        searchPointsChip.setText(SEARCH_MINIMUM_POINTS_LABELS[searchMinimumPointsIndex]);
        searchCommentsChip.setText(SEARCH_MINIMUM_COMMENTS_LABELS[searchMinimumCommentsIndex]);
        searchOnlyClickedChip.setChecked(searchOnlyClicked);

        searchSortChip.setContentDescription("Search sort: " + SEARCH_SORT_LABELS[searchSortIndex]);
        searchDateChip.setContentDescription("Search date range: " + SEARCH_DATE_RANGE_LABELS[searchDateRangeIndex]);
        searchPointsChip.setContentDescription("Search minimum points: " + SEARCH_MINIMUM_POINTS_LABELS[searchMinimumPointsIndex]);
        searchCommentsChip.setContentDescription("Search minimum comments: " + SEARCH_MINIMUM_COMMENTS_LABELS[searchMinimumCommentsIndex]);
        searchOnlyClickedChip.setContentDescription(searchOnlyClicked
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
        searching = true;
        resetSearchOptions();
        updateSearchStatus();
        animateSearchOptionsIn();

        focusSearchInput();
    }

    private void closeSearch(@Nullable View view) {
        cancelSearchOptionsAnimation();
        predictiveSearchBackInProgress = false;
        predictiveSearchBackShowingMainHeader = false;
        predictiveSearchBackShowingMainContent = false;
        predictiveSearchBackProgress = 0f;
        clearSearchBackSearchSnapshot();
        resetSearchBackVisualAlphas();

        searching = false;
        lastSearch = "";
        resetSearchOptions();
        updateSearchStatus();
        resetSearchBackContentAlpha();

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
        searchSortIndex = 0;
        searchDateRangeIndex = 0;
        searchMinimumPointsIndex = 0;
        searchMinimumCommentsIndex = 0;
        searchOnlyClicked = false;
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
        if (searchOptionsScroll == null) {
            return;
        }

        searchOptionsScroll.animate().cancel();
        searchOptionsScroll.setAlpha(1f);
        searchOptionsScroll.setTranslationY(0f);
        searchOptionsScroll.animate().setStartDelay(0);
    }

    private void resetPaginationState() {
        loadedTo = -1;
        adapter.visibleStoryCount = paginationMode ? PAGINATION_PAGE_SIZE : Integer.MAX_VALUE;
    }

    private void resetAlgoliaResultLimit() {
        algoliaHitsPerPage = ALGOLIA_HITS_INCREMENT;
        algoliaLoadMoreInProgress = false;
        algoliaLoadMoreVisibleStoryCount = -1;
        algoliaLoadMoreFirstVisiblePosition = RecyclerView.NO_POSITION;
        algoliaLoadMoreFirstVisibleTop = 0;
    }

    private int getInitialLoadCount() {
        return paginationMode ? PAGINATION_PAGE_SIZE : 20;
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

    private int getVisibleLoadTargetIndex() {
        if (stories.isEmpty()) {
            return -1;
        }

        int storiesToLoad = getInitialLoadCount();
        if (paginationMode) {
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
        loadStoriesThroughIndex(getVisibleLoadTargetIndex(), loadGeneration);
    }

    private void clearStories() {
        int oldItemCount = adapter.getItemCount();
        stories.clear();
        resetPaginationState();
        adapter.showLoadMoreButton = false;

        if (oldItemCount > 0) {
            adapter.notifyItemRangeRemoved(0, oldItemCount);
        }
    }

    private void replaceStories(List<Story> newStories) {
        replaceStories(newStories, false, false);
    }

    private void replaceStories(List<Story> newStories, boolean notifyDataSetChanged) {
        replaceStories(newStories, notifyDataSetChanged, false);
    }

    private void replaceStories(List<Story> newStories, boolean notifyDataSetChanged, boolean showLoadMoreButton) {
        if (notifyDataSetChanged) {
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
        stories.clear();
        stories.addAll(newStories);
        adapter.showLoadMoreButton = showLoadMoreButton;

        if (paginationMode) {
            int requestedVisibleCount = algoliaLoadMoreVisibleStoryCount > 0
                    ? algoliaLoadMoreVisibleStoryCount
                    : adapter.visibleStoryCount;
            adapter.visibleStoryCount = Math.min(Math.max(requestedVisibleCount, PAGINATION_PAGE_SIZE), stories.size());
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

    private void displayStorySnapshot(List<Story> snapshot,
                                      int snapshotLoadedTo,
                                      int snapshotVisibleStoryCount,
                                      boolean snapshotShowingCached,
                                      boolean snapshotLoadingFailed,
                                      boolean snapshotLoadingFailedServerError,
                                      boolean snapshotShowLoadMoreButton,
                                      boolean notifyDataSetChanged) {
        int oldItemCount = adapter.getItemCount();
        stories.clear();
        if (!notifyDataSetChanged && oldItemCount > 0) {
            adapter.notifyItemRangeRemoved(0, oldItemCount);
        }

        stories.addAll(snapshot);
        loadedTo = snapshotLoadedTo;
        adapter.visibleStoryCount = snapshotVisibleStoryCount;
        showingCached = snapshotShowingCached;
        loadingFailed = snapshotLoadingFailed;
        loadingFailedServerError = snapshotLoadingFailedServerError;
        adapter.showLoadMoreButton = snapshotShowLoadMoreButton;

        int newItemCount = adapter.getItemCount();
        if (notifyDataSetChanged) {
            adapter.notifyDataSetChanged();
        } else if (newItemCount > 0) {
            adapter.notifyItemRangeInserted(0, newItemCount);
        }
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
            suppressNextSearchRestoreAnimations = false;
            skipNextSearchRestoreDataSwap = false;
            return false;
        }

        if (skipNextSearchRestoreDataSwap) {
            skipNextSearchRestoreDataSwap = false;
            suppressNextSearchRestoreAnimations = false;
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

        clearStoriesBeforeSearchSnapshot();

        return true;
    }

    private void clearStoriesBeforeSearchSnapshot() {
        storiesBeforeSearch = null;
        loadedToBeforeSearch = -1;
        visibleStoryCountBeforeSearch = Integer.MAX_VALUE;
        showingCachedBeforeSearch = false;
        loadingFailedBeforeSearch = false;
        loadingFailedServerErrorBeforeSearch = false;
        showLoadMoreBeforeSearch = false;
        algoliaHitsPerPageBeforeSearch = ALGOLIA_HITS_INCREMENT;
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

        adapter = new StoryRecyclerViewAdapter(stories,
                SettingsUtils.shouldShowPoints(getContext()),
                SettingsUtils.shouldShowCommentsCount(getContext()),
                SettingsUtils.shouldUseCompactView(getContext()),
                SettingsUtils.shouldShowThumbnails(getContext()),
                SettingsUtils.getPreferredStoryPreviewImageMode(getContext()),
                SettingsUtils.getPreferredStoryTextSize(getContext()),
                SettingsUtils.shouldShowIndex(getContext()),
                SettingsUtils.shouldUseCompactHeader(getContext()),
                SettingsUtils.shouldUseLeftAlign(getContext()),
                SettingsUtils.shouldUseCardStoryDisplayStyle(getContext()),
                SettingsUtils.shouldGrayOutClicked(getContext()),
                SettingsUtils.getPreferredHotness(getContext()),
                SettingsUtils.getPreferredFaviconProvider(getContext()),
                null,
                getPreferredTypeIndex()
        );

        adapter.paginationMode = paginationMode;
        adapter.visibleStoryCount = paginationMode ? PAGINATION_PAGE_SIZE : Integer.MAX_VALUE;
        updateAdapterCommentRows();

        adapter.setOnLinkClickListener(position -> {
            if (position == RecyclerView.NO_POSITION) {
                return;
            }

            if (alwaysOpenComments) {
                clickedComments(position);
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastClick > CLICK_INTERVAL) {
                lastClick = now;
            } else {
                return;
            }

            Story story = stories.get(position);
            if (story.loaded) {
                markStoryClicked(story);

                if (story.isLink) {
                    if (SettingsUtils.shouldUseIntegratedWebView(getContext())) {
                        openComments(story, position, true);
                    } else {
                        Utils.launchCustomTab(getContext(), story.url);
                    }
                } else {
                    openComments(story, position, false);
                }

                adapter.notifyItemChanged(position);
            } else if (story.loadingFailed) {
                story.loadingFailed = false;
                loadStory(story, 0);
                adapter.notifyItemChanged(position);
            }
        });

        adapter.setOnCommentClickListener(this::clickedComments);
        adapter.setOnCommentStoryClickListener(this::clickedCommentStory);
        adapter.setOnCommentRepliesClickListener(this::clickedComments);

        // Set up pagination "Load More" button click listener
        adapter.setOnLoadMoreClickListener(v -> {
            if (paginationMode && adapter.visibleStoryCount < stories.size()) {
                int newLoadedTo = Math.min(
                        loadedTo + PAGINATION_PAGE_SIZE,
                        stories.size() - 1
                );

                loadStoriesThroughIndex(newLoadedTo, storyListGeneration);

                // Update adapter to show more items
                adapter.loadNextPage();
            } else if (adapter.showLoadMoreButton) {
                loadMoreAlgoliaResults();
            }
        });

        adapter.setOnLongClickListener(new StoryRecyclerViewAdapter.LongClickCoordinateListener() {
            @Override
            public boolean onLongClick(View v, int position, int x, int y) {
                if (position == RecyclerView.NO_POSITION) {
                    return false;
                }

                Context ctx = v.getContext();

                PopupMenu popupMenu = new PopupMenu(ctx, v);

                Story story = stories.get(position);
                boolean oldClicked = story.clicked;
                boolean bookmarksEnabled = SettingsUtils.shouldUseBookmarks(ctx);
                boolean oldBookmarked = bookmarksEnabled && Utils.isBookmarked(ctx, story.id);
                boolean oldFavorited = Utils.isFavorited(ctx, story.id);
                History h = HistoriesUtils.INSTANCE.getHistorybyId(story.id);

                popupMenu.getMenu().add("Upvote").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(@NonNull MenuItem item) {
                        UserActions.upvote(getContext(), story.id, getParentFragmentManager());

                        adapter.notifyItemChanged(position);
                        return true;
                    }
                });

                popupMenu.getMenu().add(oldClicked ? "Mark as unread" : "Mark as read").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(@NonNull MenuItem item) {
                        story.clicked = !oldClicked;
                        if (oldClicked) {
                            HistoriesUtils.INSTANCE.removeHistoryById(requireContext(), story.id);
                        } else {
                            HistoriesUtils.INSTANCE.addHistory(requireContext(), story.id);
                        }

                        adapter.notifyItemChanged(position);
                        return true;
                    }
                });

                if (bookmarksEnabled) {
                    popupMenu.getMenu().add(oldBookmarked ? "Remove bookmark" : "Bookmark").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(@NonNull MenuItem item) {
                            if (oldBookmarked) {
                                Utils.removeBookmark(ctx, story.id);
                                if (isBookmarksType(adapter.type)) {
                                    bookmarkStories.remove(story);
                                    int removeIndex = stories.indexOf(story);
                                    if (removeIndex >= 0) {
                                        removeStoryAt(removeIndex, storyListGeneration, true);
                                    }
                                    updateHeader();
                                    return true;
                                }
                            } else {
                                Utils.addBookmark(ctx, story.id);
                            }

                            adapter.notifyItemChanged(position);
                            return true;
                        }
                    });
                }

                popupMenu.getMenu().add(oldFavorited ? "Remove favorite" : "Favorite").setIcon(oldFavorited ? R.drawable.ic_action_star_filled : R.drawable.ic_action_star).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(@NonNull MenuItem item) {
                        if (!AccountUtils.hasAccountDetails(ctx)) {
                            AccountUtils.showLoginPrompt(getParentFragmentManager());
                            return true;
                        }

                        boolean newFavorited = !oldFavorited;
                        int optimisticIndex = stories.indexOf(story);
                        Utils.setFavorite(ctx, story.id, newFavorited);
                        if (optimisticIndex >= 0) {
                            if (oldFavorited && isFavoritesType(adapter.type)) {
                                removeStoryAt(optimisticIndex, storyListGeneration, true);
                                updateHeader();
                            } else {
                                adapter.notifyItemChanged(optimisticIndex);
                            }
                        }

                        UserActions.setFavorite(ctx, story.id, !oldFavorited, getParentFragmentManager(), new UserActions.ActionCallback() {
                            @Override
                            public void onSuccess(Response response) {
                            }

                            @Override
                            public void onFailure(String summary, String response) {
                                Utils.setFavorite(ctx, story.id, oldFavorited);
                                int currentIndex = stories.indexOf(story);
                                if (oldFavorited && isFavoritesType(adapter.type) && currentIndex == -1) {
                                    int restoreIndex = optimisticIndex >= 0 ? Math.min(optimisticIndex, stories.size()) : 0;
                                    stories.add(restoreIndex, story);
                                    adapter.notifyItemInserted(restoreIndex);
                                    updateHeader();
                                } else if (currentIndex >= 0) {
                                    adapter.notifyItemChanged(currentIndex);
                                }
                                if (newFavorited) {
                                    Toast.makeText(ctx, "Couldn't add favorite", Toast.LENGTH_SHORT).show();
                                } else {
                                    UserActions.showFailureDetailDialog(ctx, summary, response);
                                    Toast.makeText(ctx, "Couldn't update favorite", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        return true;
                    }
                });

                try {
                    // Reflection code to force show the popup at x,y position
                    java.lang.reflect.Field fieldPopup = popupMenu.getClass().getDeclaredField("mPopup");
                    fieldPopup.setAccessible(true);
                    Object menuPopupHelper = fieldPopup.get(popupMenu);

                    // the reason for the -10 - height/3 thing is so match the popup location better
                    // with the press location - for some reason this is necessary.
                    int targetX = x - Utils.pxFromDpInt(getResources(), 56);
                    int targetY = y - topInset - Utils.pxFromDpInt(getResources(), 10) - v.getHeight() / 3;

                    menuPopupHelper.getClass().getDeclaredMethod("show", int.class, int.class).invoke(menuPopupHelper, targetX, targetY);
                } catch (Exception e) {
                    // In case reflection fails, show the popup the usual way
                    popupMenu.show();
                }

                return false;
            }
        });
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
        boolean newHideJobs = SettingsUtils.shouldHideJobs(getContext());
        hideClicked = SettingsUtils.shouldHideClicked(getContext());
        alwaysOpenComments = SettingsUtils.shouldAlwaysOpenComments(getContext());
        refreshTypeSpinnerItemsIfNeeded();
        syncVisibleUserItemListWithLocalCache();

        long timeDiff = System.currentTimeMillis() - lastLoaded;

        // if more than 1 hr
        boolean shouldShowUpdateButton = SettingsUtils.shouldAlwaysShowTapToRefresh(getContext())
                || (timeDiff > 1000 * 60 * 60 && !searching && !isBookmarksType(adapter.type) && !isUserItemListType(adapter.type) && !currentTypeIsAlgolia());
        if (shouldShowUpdateButton) {
            showUpdateButton();
        } else {
            hideUpdateButton();
        }

        if (adapter.showPoints != SettingsUtils.shouldShowPoints(getContext())) {
            adapter.showPoints = !adapter.showPoints;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        if (adapter.showCommentsCount != SettingsUtils.shouldShowCommentsCount(getContext())) {
            adapter.showCommentsCount = !adapter.showCommentsCount;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        if (adapter.compactView != SettingsUtils.shouldUseCompactView(getContext())) {
            adapter.compactView = !adapter.compactView;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        if (adapter.thumbnails != SettingsUtils.shouldShowThumbnails(getContext())) {
            adapter.thumbnails = !adapter.thumbnails;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        String previewImageMode = SettingsUtils.getPreferredStoryPreviewImageMode(getContext());
        if (!adapter.previewImageMode.equals(previewImageMode)) {
            adapter.previewImageMode = previewImageMode;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
            scheduleLoadedPreviewImagePrefetchNearViewport();
        }

        float storyTextSize = SettingsUtils.getPreferredStoryTextSize(getContext());
        if (Float.compare(adapter.storyTextSize, storyTextSize) != 0) {
            adapter.storyTextSize = storyTextSize;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        if (adapter.showIndex != SettingsUtils.shouldShowIndex(getContext())) {
            adapter.showIndex = !adapter.showIndex;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        if (adapter.leftAlign != SettingsUtils.shouldUseLeftAlign(getContext())) {
            adapter.leftAlign = !adapter.leftAlign;
            setupAdapter();
            recyclerView.setAdapter(adapter);
        }

        if (adapter.cardStyle != SettingsUtils.shouldUseCardStoryDisplayStyle(getContext())) {
            adapter.cardStyle = !adapter.cardStyle;
            setupAdapter();
            recyclerView.setAdapter(adapter);
        }

        if (adapter.grayOutClicked != SettingsUtils.shouldGrayOutClicked(getContext())) {
            adapter.grayOutClicked = SettingsUtils.shouldGrayOutClicked(getContext());
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        boolean newPaginationMode = SettingsUtils.shouldUsePaginationMode(getContext());
        if (paginationMode != newPaginationMode) {
            int oldItemCount = adapter.getItemCount();
            paginationMode = newPaginationMode;
            adapter.paginationMode = paginationMode;
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

        if (TextUtils.isEmpty(FontUtils.font) || !FontUtils.font.equals(SettingsUtils.getPreferredFont(getContext()))) {
            FontUtils.init(getContext());
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        if (adapter.compactHeader != SettingsUtils.shouldUseCompactHeader(getContext())) {
            adapter.compactHeader = SettingsUtils.shouldUseCompactHeader(getContext());
            updateHeader();
        }

        if (adapter.hotness != SettingsUtils.getPreferredHotness(getContext())) {
            adapter.hotness = SettingsUtils.getPreferredHotness(getContext());
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        if (hideJobs != newHideJobs) {
            hideJobs = newHideJobs;
            attemptRefresh();
        }

        if (adapter.faviconProvider != SettingsUtils.getPreferredFaviconProvider(getContext())) {
            adapter.faviconProvider = SettingsUtils.getPreferredFaviconProvider(getContext());
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        syncStoriesWithHistoriesIfNeeded();
    }

    public void onAccountStateChanged() {
        refreshTypeSpinnerItemsIfNeeded();
        updateHeader();
    }

    private void syncStoriesWithHistoriesIfNeeded() {
        long currentHistoriesChangeVersion = HistoriesUtils.INSTANCE.getChangeVersion();
        if (historiesChangeVersion == currentHistoriesChangeVersion || adapter == null || stories == null) {
            return;
        }

        historiesChangeVersion = currentHistoriesChangeVersion;

        if (searching && searchOnlyClicked) {
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

    @Override
    public void onDestroyView() {
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
        if (recyclerView != null) {
            if (recyclerViewScrollListener != null) {
                recyclerView.removeOnScrollListener(recyclerViewScrollListener);
            }
            if (adapter != null && storyAdapterDataObserver != null) {
                adapter.unregisterAdapterDataObserver(storyAdapterDataObserver);
            }
            recyclerView.setAdapter(null);
            recyclerView.setLayoutManager(null);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(null);
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
            loadingStoryIds.clear();
            invalidateAlgoliaLoad();
            queue.cancelAll(requestTag);
        }

        clearViewReferences();

        super.onDestroyView();
    }

    private void clearAppBarDragCallback() {
        CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams();
        if (layoutParams.getBehavior() instanceof AppBarLayout.Behavior) {
            ((AppBarLayout.Behavior) layoutParams.getBehavior()).setDragCallback(null);
        }
    }

    private void clearViewReferences() {
        swipeRefreshLayout = null;
        updateFab = null;
        recyclerView = null;
        appBarLayout = null;
        appBarOffsetChangedListener = null;
        recyclerViewScrollListener = null;
        storyAdapterDataObserver = null;
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
        userItemFilterGroup = null;
        loadingIndicator = null;
        loadingFailedLayout = null;
        loadingFailedText = null;
        loadingFailedAlgoliaLayout = null;
        noBookmarksLayout = null;
        noBookmarksImage = null;
        noBookmarksText = null;
        showingCachedText = null;
        searchEmptyContainer = null;
        retryButton = null;
        showCachedButton = null;

        typeSpinnerAdapter = null;
        linearLayoutManager = null;
        defaultStoryItemAnimator = null;
        cachingStories = false;
        cacheProgressIndicatorVisible = false;
        cacheProgressHidePending = false;
        cacheProgressAnimationGeneration++;
        cacheStoriesTotal = 1;
        cacheStoriesCompleted = 0;
        cacheProgressStatus = CACHE_PROGRESS_STATUS_CACHING;
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
            markStoryClicked(story);

            openComments(story, position, false);

            adapter.notifyItemChanged(position);
        }
    }

    private void clickedCommentStory(int position) {
        if (position == RecyclerView.NO_POSITION) {
            return;
        }

        Story story = stories.get(position);
        int targetId = story.commentMasterId > 0 ? story.commentMasterId : story.parentId;
        if (targetId > 0) {
            Utils.openCommentsActivity(targetId, -1, requireContext());
        } else {
            clickedComments(position);
        }
    }

    private void markStoryClicked(Story story) {
        if (!searchOnlyClicked) {
            story.clicked = true;
        }
        HistoriesUtils.INSTANCE.addHistory(requireContext(), story.id);
    }

    private void removeStoryAt(int index, int loadGeneration, boolean loadVisibleReplacement) {
        if (index < 0 || index >= stories.size()) {
            return;
        }

        Story removedStory = stories.remove(index);
        loadingStoryIds.remove(removedStory.id);
        if (index <= loadedTo) {
            loadedTo = Math.max(-1, loadedTo - 1);
        }

        if (paginationMode) {
            adapter.notifyDataSetChanged();
        } else {
            adapter.notifyItemRemoved(index);
        }

        if (loadVisibleReplacement) {
            loadVisibleStories(loadGeneration);
        }
    }

    private boolean shouldFilterLoadedStory(Story story) {
        if (story == null) {
            return false;
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

        if (attempt >= 3 || loadingStoryIds.contains(story.id)) {
            return;
        }

        loadingStoryIds.add(story.id);

        String url = "https://hacker-news.firebaseio.com/v0/item/" + story.id + ".json";

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    if (!isCurrentStoryListGeneration(loadGeneration)) {
                        return;
                    }
                    loadingStoryIds.remove(story.id);
                    int index = stories.indexOf(story);
                    if (index < 0) {
                        return;
                    }
                    try {
                        if (!JSONParser.updateStoryWithHNJson(response, story, isHistoryType(adapter.type))) {
                            removeStoryAt(index, loadGeneration, true);
                            return;
                        }

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
                            adapter.prefetchPreviewImage(context, story);
                        }

                        adapter.notifyItemChanged(index);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Utils.log("Failed to load story with id: " + story.id);
                        story.loadingFailed = true;
                        if (index >= 0) {
                            adapter.notifyItemChanged(index);
                        }
                    }
                }, error -> {
            if (!isCurrentStoryListGeneration(loadGeneration)) {
                return;
            }
            loadingStoryIds.remove(story.id);
            error.printStackTrace();
            story.loadingFailed = true;
            int index = stories.indexOf(story);
            if (index >= 0) {
                adapter.notifyItemChanged(index);
                loadStory(story, attempt + 1, loadGeneration);
            }
        });

        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
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
                    cacheStories();
                } else if (item.getItemId() == R.id.menu_submit) {
                    Intent submitIntent = new Intent(getContext(), ComposeActivity.class);
                    submitIntent.putExtra(ComposeActivity.EXTRA_TYPE, ComposeActivity.TYPE_POST);
                    startActivity(submitIntent);
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
        menu.findItem(R.id.menu_cache).setVisible(!showingCached && !cachingStories);
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
        loadingStoryIds.clear();
        invalidateAlgoliaLoad();
        queue.cancelAll(requestTag);
        return storyListGeneration;
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
            search(lastSearch);
            return;
        }

        swipeRefreshLayout.setRefreshing(showSwipeRefreshIndicator && !showMainLoadingIndicator);

        // cancel all ongoing
        int refreshGeneration = beginStoryListRefresh();

        boolean userItemListTypeForRefresh = isUserItemListType(adapter.type);
        if (showMainLoadingIndicator) {
            loadingFailed = false;
            loadingFailedServerError = false;
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
                    : !loadCurrentUserItemListCache(getContext()).isEmpty();
            if (!shouldLoadCachedUserItemList) {
                resumeInterruptedStoryLoads();
            }
            syncUserItemListFromServer(showSwipeRefreshIndicator || hasCachedUserItemList);
            return;
        } else if (isHistoryType(adapter.type)) {
            ArrayList<Story> refreshedStories = new ArrayList<>();
            showingCached = false;
            List<History> histories = UtilsKt.INSTANCE.loadHistories(requireContext(), true);

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

        // if none of the above, do a normal loading
        StringRequest stringRequest = new StringRequest(Request.Method.GET, hnUrls[adapter.type == 0 ? 0 : adapter.type - 3],
                response -> {
                    if (!isCurrentStoryListGeneration(refreshGeneration)) {
                        return;
                    }
                    swipeRefreshLayout.setRefreshing(false);
                    try {
                        JSONArray jsonArray = new JSONArray(response);
                        ArrayList<Story> refreshedStories = new ArrayList<>();
                        showingCached = false;

                        for (int i = 0; i < jsonArray.length(); i++) {
                            int id = Integer.parseInt(jsonArray.get(i).toString());
                            if (hideClicked && HistoriesUtils.INSTANCE.isHistoryExist(id)) {
                                continue;
                            }

                            Story s = new Story("Loading...", id, false, HistoriesUtils.INSTANCE.isHistoryExist(id));
                            // let's try to fill this with old information if possible

                            String cachedResponse = Utils.loadCachedStory(getContext(), id);
                            if (cachedResponse != null && !cachedResponse.equals(JSONParser.ALGOLIA_ERROR_STRING)) {
                                JSONParser.updateStoryWithAlgoliaResponse(s, cachedResponse);
                                if (shouldFilterLoadedStory(s)) {
                                    continue;
                                }
                            }

                            refreshedStories.add(s);
                        }

                        replaceStories(refreshedStories);

                        if (loadingFailed) {
                            loadingFailed = false;
                            loadingFailedServerError = false;
                        }

                        updateHeader();

                        loadInitialVisibleStories(refreshGeneration);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, error -> {
            if (!isCurrentStoryListGeneration(refreshGeneration)) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
            loadingFailed = true;
            updateHeader();
        });

        updateHeader();
        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private void loadCommentMaster(Story story, int parentId, int attempt) {
        loadCommentMaster(story, parentId, attempt, storyListGeneration);
    }

    private void loadCommentMaster(Story story, int parentId, int attempt, int loadGeneration) {
        if (parentId <= 0 || attempt >= 8 || story.commentMasterId > 0 || !isCurrentStoryListGeneration(loadGeneration)) {
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

                        story.commentMasterId = parent.optInt("id", parentId);
                        story.commentMasterTitle = parent.optString("title", "Hacker News thread");
                        if (parent.has("url")) {
                            story.commentMasterUrl = parent.optString("url");
                        } else {
                            story.commentMasterUrl = "https://news.ycombinator.com/item?id=" + story.commentMasterId;
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
        userItemListInitialLoadInProgress = false;

        UserItemListSnapshot snapshot = loadCachedUserItemListSnapshot(getContext());
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
            updateHeader();
            return;
        }

        boolean upvotedTypeForSync = isUpvotedType(adapter.type);
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

                ArrayList<Integer> normalizedItemIds = normalizeUserItemListIds(itemIds);
                Set<Integer> normalizedCommentIds = normalizeUserItemListCommentIds(normalizedItemIds, commentIds);
                if (!userItemListIdsMatchCache(currentContext, normalizedItemIds, normalizedCommentIds)) {
                    saveCurrentUserItemListIds(currentContext, normalizedItemIds, normalizedCommentIds);
                }
                syncUserItemListStoriesToIds(normalizedItemIds, normalizedCommentIds);

                userItemListInitialLoadInProgress = false;
                loadingFailed = false;
                loadingFailedServerError = false;
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

    private boolean userItemListIdsMatchCache(Context ctx, List<Integer> itemIds, Set<Integer> commentIds) {
        ArrayList<Bookmark> cachedItems = loadCurrentUserItemListCache(ctx);
        ArrayList<Integer> normalizedItemIds = normalizeUserItemListIds(itemIds);
        Set<Integer> cachedCommentIds = loadCurrentUserItemListCommentIds(ctx);

        if (cachedItems.size() != normalizedItemIds.size() || !cachedCommentIds.equals(commentIds)) {
            return false;
        }

        for (int i = 0; i < cachedItems.size(); i++) {
            if (cachedItems.get(i).id != normalizedItemIds.get(i)) {
                return false;
            }
        }

        return true;
    }

    private UserItemListSnapshot loadCachedUserItemListSnapshot(@Nullable Context ctx) {
        ArrayList<Integer> itemIds = new ArrayList<>();
        if (ctx == null) {
            return new UserItemListSnapshot(itemIds, new HashSet<>());
        }

        ArrayList<Bookmark> items = loadCurrentUserItemListCache(ctx);
        for (Bookmark item : items) {
            if (!itemIds.contains(item.id)) {
                itemIds.add(item.id);
            }
        }

        Collections.sort(itemIds, (id1, id2) -> Integer.compare(id2, id1));
        return new UserItemListSnapshot(itemIds, loadCurrentUserItemListCommentIds(ctx));
    }

    private ArrayList<Bookmark> loadCurrentUserItemListCache(@Nullable Context ctx) {
        if (ctx == null) {
            return new ArrayList<>();
        }
        if (isUpvotedType(adapter.type)) {
            return Utils.loadUpvoted(ctx, true);
        }
        return Utils.loadFavorites(ctx, true);
    }

    private Set<Integer> loadCurrentUserItemListCommentIds(Context ctx) {
        if (isUpvotedType(adapter.type)) {
            return Utils.loadUpvotedCommentIds(ctx);
        }
        return Utils.loadFavoriteCommentIds(ctx);
    }

    private void saveCurrentUserItemListIds(Context ctx, List<Integer> itemIds, Set<Integer> commentIds) {
        if (isUpvotedType(adapter.type)) {
            Utils.saveUpvotedIds(ctx, itemIds);
            Utils.saveUpvotedCommentIds(ctx, commentIds);
        } else {
            Utils.saveFavoriteIds(ctx, itemIds);
            Utils.saveFavoriteCommentIds(ctx, commentIds);
        }
    }

    private void syncVisibleUserItemListWithLocalCache() {
        if (adapter == null || stories == null || !isUserItemListType(adapter.type)) {
            return;
        }

        UserItemListSnapshot snapshot = loadCachedUserItemListSnapshot(getContext());
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
        loadingStoryIds.clear();
        userItemListStories.clear();
        userItemListStories.addAll(refreshedStories);
        userItemListCommentIds = new HashSet<>(commentIds);
        replaceStories(getFilteredSavedItemStories(), true);
        loadInitialVisibleStories();
        updateHeader();
    }

    private ArrayList<Integer> normalizeUserItemListIds(List<Integer> itemIds) {
        ArrayList<Integer> normalizedItemIds = new ArrayList<>();
        for (int id : itemIds) {
            if (!normalizedItemIds.contains(id)) {
                normalizedItemIds.add(id);
            }
        }

        Collections.sort(normalizedItemIds, (id1, id2) -> Integer.compare(id2, id1));
        return normalizedItemIds;
    }

    private Set<Integer> normalizeUserItemListCommentIds(List<Integer> itemIds, List<Integer> commentIds) {
        Set<Integer> itemIdSet = new HashSet<>(itemIds);
        Set<Integer> normalizedCommentIds = new HashSet<>();
        for (int id : commentIds) {
            if (itemIdSet.contains(id)) {
                normalizedCommentIds.add(id);
            }
        }
        return normalizedCommentIds;
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

    private static class UserItemListSnapshot {
        final ArrayList<Integer> itemIds;
        final Set<Integer> commentIds;

        UserItemListSnapshot(ArrayList<Integer> itemIds, Set<Integer> commentIds) {
            this.itemIds = itemIds;
            this.commentIds = commentIds;
        }
    }

    private void loadInitialVisibleStories() {
        loadInitialVisibleStories(storyListGeneration);
    }

    private void loadInitialVisibleStories(int loadGeneration) {
        loadStoriesThroughIndex(Math.min(getInitialLoadCount(), stories.size()) - 1, loadGeneration);
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
        if (paginationMode) {
            lastIndex = Math.min(lastIndex, adapter.visibleStoryCount - 1);
        }

        if (lastIndex < firstIndex) {
            return;
        }

        for (int i = firstIndex; i <= lastIndex; i++) {
            adapter.prefetchPreviewImage(context, stories.get(i));
        }
    }

    private void updateSearchStatus() {
        hideUpdateButton();
        boolean restoredStories = false;
        boolean shouldRefreshAfterRestore = false;

        if (getActivity() != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).backPressedCallback.setEnabled(searching);
        }

        swipeRefreshLayout.setEnabled(!searching);

        if (searching) {
            saveStoriesBeforeSearch();

            // cancel all ongoing
            storyListGeneration++;
            loadingStoryIds.clear();
            invalidateAlgoliaLoad();
            queue.cancelAll(requestTag);
            swipeRefreshLayout.setRefreshing(false);
            loadingFailed = false;
            loadingFailedServerError = false;
            clearStories();
            appBarLayout.setExpanded(true, false);
        } else {
            shouldRefreshAfterRestore = loadPendingBeforeSearch
                    && storiesBeforeSearch != null
                    && storiesBeforeSearch.isEmpty();
            loadPendingBeforeSearch = false;

            storyListGeneration++;
            loadingStoryIds.clear();
            invalidateAlgoliaLoad();
            queue.cancelAll(requestTag);
            swipeRefreshLayout.setRefreshing(false);
            restoredStories = restoreStoriesBeforeSearch();

            if (!restoredStories) {
                clearStories();
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
        }
    }

    private int getCurrentAlgoliaTopStoriesStartTime() {
        int currentTime = (int) (System.currentTimeMillis() / 1000);
        if (adapter.type == 1) {
            return currentTime - 60 * 60 * 24;
        } else if (adapter.type == 2) {
            return currentTime - 60 * 60 * 48;
        } else if (adapter.type == 3) {
            return currentTime - 60 * 60 * 24 * 7;
        }

        return currentTime;
    }

    private void loadTopStoriesSince(int start_i, boolean showSwipeRefreshIndicator) {
        lastAlgoliaTopStoriesStartTime = start_i;
        Uri uri = Uri.parse("https://hn.algolia.com/api/v1/search")
                .buildUpon()
                .appendQueryParameter("tags", "story")
                .appendQueryParameter("numericFilters", "created_at_i>" + start_i)
                .appendQueryParameter("hitsPerPage", String.valueOf(algoliaHitsPerPage))
                .build();
        loadAlgolia(uri.toString(), showSwipeRefreshIndicator);
    }

    private void search(String query) {
        search(query, true);
    }

    private void search(String query, boolean resetResultLimit) {
        lastSearch = query;
        if (resetResultLimit) {
            resetAlgoliaResultLimit();
        }

        if (searchOnlyClicked) {
            loadOnlyClickedSearch(query);
            return;
        }

        String endpoint = searchSortIndex == 0
                ? "https://hn.algolia.com/api/v1/search"
                : "https://hn.algolia.com/api/v1/search_by_date";
        Uri.Builder builder = Uri.parse(endpoint).buildUpon()
                .appendQueryParameter("query", query)
                .appendQueryParameter("tags", "story")
                .appendQueryParameter("hitsPerPage", String.valueOf(algoliaHitsPerPage))
                .appendQueryParameter("typoTolerance", "min");

        List<String> numericFilters = new ArrayList<>();
        int days = SEARCH_DATE_RANGE_DAYS[searchDateRangeIndex];
        if (days > 0) {
            long startTime = (System.currentTimeMillis() / 1000L) - (days * 24L * 60L * 60L);
            numericFilters.add("created_at_i>=" + startTime);
        }

        int minimumPoints = SEARCH_MINIMUM_POINTS[searchMinimumPointsIndex];
        if (minimumPoints > 0) {
            numericFilters.add("points>=" + minimumPoints);
        }

        int minimumComments = SEARCH_MINIMUM_COMMENTS[searchMinimumCommentsIndex];
        if (minimumComments > 0) {
            numericFilters.add("num_comments>=" + minimumComments);
        }

        if (!numericFilters.isEmpty()) {
            builder.appendQueryParameter("numericFilters", TextUtils.join(",", numericFilters));
        }

        loadAlgolia(builder.build().toString());
    }

    private boolean canLoadMoreAlgoliaResults(int rawParsedStoryCount) {
        return rawParsedStoryCount >= algoliaHitsPerPage;
    }

    private void loadMoreAlgoliaResults() {
        if (algoliaLoading) {
            return;
        }

        algoliaLoadMoreInProgress = true;
        saveAlgoliaLoadMoreScrollPosition();
        if (paginationMode) {
            algoliaLoadMoreVisibleStoryCount = adapter.visibleStoryCount + PAGINATION_PAGE_SIZE;
        } else {
            algoliaLoadMoreVisibleStoryCount = -1;
        }
        algoliaHitsPerPage += ALGOLIA_HITS_INCREMENT;
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
        loadingStoryIds.clear();
        invalidateAlgoliaLoad();
        final int requestGeneration = algoliaRequestGeneration;
        algoliaLoading = true;
        activeAlgoliaUrl = null;
        loadingFailed = false;
        loadingFailedServerError = false;
        showingCached = false;
        queue.cancelAll(requestTag);
        loadingStoryIds.clear();

        if (!stories.isEmpty()) {
            clearStories();
        }

        List<History> histories = UtilsKt.INSTANCE.loadHistories(requireContext(), true);
        if (histories.isEmpty()) {
            completeOnlyClickedSearch(requestGeneration, new ArrayList<>(), 0, 0);
            return;
        }

        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        int minimumTime = getSearchMinimumTimeSeconds();
        int minimumPoints = SEARCH_MINIMUM_POINTS[searchMinimumPointsIndex];
        int minimumComments = SEARCH_MINIMUM_COMMENTS[searchMinimumCommentsIndex];
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
                                    && shouldIncludeOnlyClickedSearchStory(story, normalizedQuery, minimumTime, minimumPoints, minimumComments)) {
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

    private int getSearchMinimumTimeSeconds() {
        int days = SEARCH_DATE_RANGE_DAYS[searchDateRangeIndex];
        if (days <= 0) {
            return 0;
        }

        return (int) ((System.currentTimeMillis() / 1000L) - (days * 24L * 60L * 60L));
    }

    private boolean shouldIncludeOnlyClickedSearchStory(Story story,
                                                        String normalizedQuery,
                                                        int minimumTime,
                                                        int minimumPoints,
                                                        int minimumComments) {
        if (story.title == null || !story.title.toLowerCase().contains(normalizedQuery)) {
            return false;
        }

        if (minimumTime > 0 && story.time < minimumTime) {
            return false;
        }

        if (minimumPoints > 0 && story.score < minimumPoints) {
            return false;
        }

        if (minimumComments > 0 && story.descendants < minimumComments) {
            return false;
        }

        return !shouldFilterLoadedStory(story);
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
        if (searchSortIndex == 0) {
            SearchRelevanceUtils.sortStoriesByRelevance(finishedStories, lastSearch);
        }

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
        queue.cancelAll(requestTag);

        swipeRefreshLayout.setEnabled(!searching);
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
                            showingCached = false;

                            if (predictiveSearchBackInProgress && predictiveSearchBackShowingMainContent) {
                                predictiveSearchBackSearchStories = new ArrayList<>(parsedStories);
                                predictiveSearchBackLoadedTo = parsedStories.size() - 1;
                                predictiveSearchBackVisibleStoryCount = preservePaginationForLoadMore && paginationMode
                                        ? Math.min(Math.max(algoliaLoadMoreVisibleStoryCount, PAGINATION_PAGE_SIZE), parsedStories.size())
                                        : adapter.visibleStoryCount;
                                predictiveSearchBackShowingCached = false;
                                predictiveSearchBackLoadingFailed = false;
                                predictiveSearchBackLoadingFailedServerError = false;
                                predictiveSearchBackShowLoadMore = canLoadMoreAlgoliaResults(rawParsedStoryCount);
                            } else if (preservePaginationForLoadMore) {
                                replaceAlgoliaLoadMoreStories(parsedStories, canLoadMoreAlgoliaResults(rawParsedStoryCount));
                                loadedTo = stories.size() - 1;
                                scheduleLoadedPreviewImagePrefetchNearViewport();
                            } else {
                                replaceStories(parsedStories, false, canLoadMoreAlgoliaResults(rawParsedStoryCount));
                                loadedTo = stories.size() - 1;
                                scheduleLoadedPreviewImagePrefetchNearViewport();
                            }
                            algoliaLoadMoreInProgress = false;
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
            algoliaLoadMoreVisibleStoryCount = -1;
            algoliaLoadMoreFirstVisiblePosition = RecyclerView.NO_POSITION;
            algoliaLoadMoreFirstVisibleTop = 0;

            if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                loadingFailedServerError = true;
            }

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
        int viewportTop = recyclerView.getPaddingTop();
        int viewportBottom = recyclerView.getHeight() - recyclerView.getPaddingBottom();

        return contentTop >= viewportTop && contentBottom <= viewportBottom;
    }

    private void requestRecyclerScrollStateUpdate() {
        updateRecyclerScrollState();

        if (recyclerView != null) {
            recyclerView.post(this::updateRecyclerScrollState);
        }
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

    private void registerStoryAdapterDataObserver() {
        if (adapter == null) {
            return;
        }

        storyAdapterDataObserver = new RecyclerView.AdapterDataObserver() {
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
        adapter.registerAdapterDataObserver(storyAdapterDataObserver);
    }

    public boolean currentTypeIsAlgolia() {
        return 0 < adapter.type && 4 > adapter.type;
    }

    private boolean isBookmarksType(int type) {
        return TextUtils.equals(getTypeLabel(type), "Bookmarks");
    }

    private boolean isHistoryType(int type) {
        return TextUtils.equals(getTypeLabel(type), "History");
    }

    private boolean isFavoritesType(int type) {
        return TextUtils.equals(getTypeLabel(type), SettingsUtils.FAVORITES_LABEL);
    }

    private boolean isUpvotedType(int type) {
        return TextUtils.equals(getTypeLabel(type), SettingsUtils.UPVOTED_LABEL);
    }

    private boolean isUserItemListType(int type) {
        return isFavoritesType(type) || isUpvotedType(type);
    }

    private boolean currentTypeUsesSavedItemFilter() {
        return isBookmarksType(adapter.type) || isUserItemListType(adapter.type);
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

    private boolean currentTypeUsesCommentRows() {
        return isBookmarksType(adapter.type) || isUserItemListType(adapter.type);
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
        boolean usesCommentRows = adapter != null && currentTypeUsesCommentRows();
        if (adapter != null) {
            adapter.allowCommentRows = usesCommentRows;
            adapter.disableClickedEffects = isBookmarksType(adapter.type) || isUserItemListType(adapter.type) || isHistoryType(adapter.type);
        }
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

    private boolean shouldHideStoryAsJob(Story story) {
        return hideJobs
                && adapter.type != SettingsUtils.getJobsIndex(getResources())
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
        predictiveSearchBackShowingMainContent = false;
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

        predictiveSearchBackInProgress = false;
        predictiveSearchBackShowingMainHeader = false;
        restoreSearchBackSearchContentIfNeeded();
        predictiveSearchBackShowingMainContent = false;
        predictiveSearchBackProgress = 0f;
        updateHeader(false);
        resetSearchBackVisualAlphas();
        resetSearchBackContentAlpha();
        appBarLayout.setExpanded(true, false);
        clearSearchBackSearchSnapshot();
    }

    public boolean finishSearchBackProgress() {
        setSearchBackContentAlpha(0f);
        if (!predictiveSearchBackShowingMainContent) {
            showSearchBackMainContent();
        }
        suppressNextSearchRestoreAnimations = true;
        skipNextSearchRestoreDataSwap = predictiveSearchBackShowingMainContent;
        predictiveSearchBackInProgress = false;
        predictiveSearchBackShowingMainHeader = false;
        predictiveSearchBackShowingMainContent = false;
        predictiveSearchBackProgress = 0f;
        clearSearchBackSearchSnapshot();
        resetSearchBackVisualAlphas();
        return exitSearch();
    }

    private void applySearchBackVisualProgress(float progress) {
        predictiveSearchBackProgress = Math.max(0f, Math.min(1f, progress));

        boolean showMainHeader = predictiveSearchBackProgress >= SEARCH_BACK_HEADER_SWITCH_PROGRESS;
        if (showMainHeader != predictiveSearchBackShowingMainHeader) {
            predictiveSearchBackShowingMainHeader = showMainHeader;
            applySearchBackHeaderMode(showMainHeader);
        }

        if (showMainHeader) {
            showSearchBackMainContent();
        } else {
            restoreSearchBackSearchContentIfNeeded();
        }

        if (showMainHeader) {
            float mainAlpha = (predictiveSearchBackProgress - SEARCH_BACK_HEADER_SWITCH_PROGRESS)
                    / (1f - SEARCH_BACK_HEADER_SWITCH_PROGRESS);
            setSearchBackMainHeaderAlpha(mainAlpha);
            setSearchBackSearchHeaderAlpha(0f);
            setSearchBackContentAlpha(mainAlpha);
        } else {
            float searchAlpha = 1f - (predictiveSearchBackProgress / SEARCH_BACK_HEADER_SWITCH_PROGRESS);
            setSearchBackSearchHeaderAlpha(searchAlpha);
            setSearchBackMainHeaderAlpha(0f);
            setSearchBackContentAlpha(searchAlpha);
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
    }

    private void showSearchBackMainContent() {
        if (predictiveSearchBackShowingMainContent || storiesBeforeSearch == null) {
            return;
        }

        saveSearchBackSearchSnapshot();
        setSearchBackContentAlpha(0f);
        predictiveSearchBackShowingMainContent = true;
        displayStorySnapshot(
                storiesBeforeSearch,
                loadedToBeforeSearch,
                visibleStoryCountBeforeSearch,
                showingCachedBeforeSearch,
                loadingFailedBeforeSearch,
                loadingFailedServerErrorBeforeSearch,
                showLoadMoreBeforeSearch,
                true
        );
    }

    private void saveSearchBackSearchSnapshot() {
        if (predictiveSearchBackSearchStories != null) {
            return;
        }

        predictiveSearchBackSearchStories = new ArrayList<>(stories);
        predictiveSearchBackLoadedTo = loadedTo;
        predictiveSearchBackVisibleStoryCount = adapter.visibleStoryCount;
        predictiveSearchBackShowingCached = showingCached;
        predictiveSearchBackLoadingFailed = loadingFailed;
        predictiveSearchBackLoadingFailedServerError = loadingFailedServerError;
        predictiveSearchBackShowLoadMore = adapter.showLoadMoreButton;
    }

    private void restoreSearchBackSearchContentIfNeeded() {
        if (!predictiveSearchBackShowingMainContent || predictiveSearchBackSearchStories == null) {
            return;
        }

        predictiveSearchBackShowingMainContent = false;
        setSearchBackContentAlpha(0f);
        displayStorySnapshot(
                predictiveSearchBackSearchStories,
                predictiveSearchBackLoadedTo,
                predictiveSearchBackVisibleStoryCount,
                predictiveSearchBackShowingCached,
                predictiveSearchBackLoadingFailed,
                predictiveSearchBackLoadingFailedServerError,
                predictiveSearchBackShowLoadMore,
                true
        );
    }

    private void clearSearchBackSearchSnapshot() {
        predictiveSearchBackSearchStories = null;
        predictiveSearchBackLoadedTo = -1;
        predictiveSearchBackVisibleStoryCount = Integer.MAX_VALUE;
        predictiveSearchBackShowingCached = false;
        predictiveSearchBackLoadingFailed = false;
        predictiveSearchBackLoadingFailedServerError = false;
        predictiveSearchBackShowLoadMore = false;
    }

    private void setSearchBackSearchHeaderAlpha(float alpha) {
        searchContainer.setAlpha(alpha);
        searchOptionsScroll.setAlpha(alpha);
        closeSearchButton.setAlpha(alpha);
    }

    private void setSearchBackMainHeaderAlpha(float alpha) {
        spinnerContainer.setAlpha(alpha);
        searchButton.setAlpha(alpha);
        moreButton.setAlpha(alpha);
    }

    private void resetSearchBackVisualAlphas() {
        setSearchBackSearchHeaderAlpha(1f);
        setSearchBackMainHeaderAlpha(1f);
    }

    private void setSearchBackContentAlpha(float alpha) {
        recyclerView.setAlpha(alpha);
    }

    private void resetSearchBackContentAlpha() {
        recyclerView.setAlpha(1f);
    }

    private void startCacheProgress() {
        cachingStories = true;
        cacheStoriesTotal = 1;
        cacheStoriesCompleted = 0;
        cacheProgressStatus = CACHE_PROGRESS_STATUS_CACHING;
        updateCacheProgressIndicator();
    }

    private void setCacheProgressTotal(int total) {
        cacheStoriesTotal = Math.max(total, 1);
        cacheStoriesCompleted = 0;
        updateCacheProgressIndicator();
    }

    private void incrementCacheProgress() {
        cacheStoriesCompleted = Math.min(cacheStoriesCompleted + 1, cacheStoriesTotal);
        updateCacheProgressIndicator();
    }

    private void finishCacheProgress() {
        finishCacheProgress(CACHE_PROGRESS_STATUS_FINISHED);
    }

    private void finishCacheProgress(@NonNull String status) {
        cachingStories = false;
        cacheProgressStatus = status;
        updateCacheProgressIndicator();
    }

    private void updateCacheProgressIndicator() {
        if (cacheProgressIndicator == null || cacheProgressStatusText == null) {
            return;
        }

        LinearProgressIndicator currentProgressIndicator = cacheProgressIndicator;
        TextView currentStatusText = cacheProgressStatusText;
        currentProgressIndicator.setMax(Math.max(cacheStoriesTotal, 1));
        currentProgressIndicator.setProgressCompat(cacheStoriesCompleted, true);
        currentStatusText.setText(cachingStories ? CACHE_PROGRESS_STATUS_CACHING : cacheProgressStatus);

        if (cachingStories) {
            showCacheProgressIndicator(currentProgressIndicator, currentStatusText);
            return;
        }

        hideCacheProgressIndicator(currentProgressIndicator, currentStatusText);
    }

    private void showCacheProgressIndicator(@NonNull LinearProgressIndicator currentProgressIndicator,
                                            @NonNull TextView currentStatusText) {
        if (cacheProgressIndicatorVisible
                && currentProgressIndicator.getVisibility() == View.VISIBLE
                && currentStatusText.getVisibility() == View.VISIBLE) {
            return;
        }

        cacheProgressAnimationGeneration++;
        cacheProgressHidePending = false;
        currentProgressIndicator.animate().cancel();
        currentStatusText.animate().cancel();
        cacheProgressIndicatorVisible = true;
        beginHeaderTransition(false);
        currentStatusText.setAlpha(0f);
        currentStatusText.setVisibility(View.VISIBLE);
        currentProgressIndicator.setAlpha(0f);
        currentProgressIndicator.setVisibility(View.VISIBLE);
        currentStatusText.animate()
                .alpha(1f)
                .setDuration(CACHE_PROGRESS_FADE_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .start();
        currentProgressIndicator.animate()
                .alpha(1f)
                .setDuration(CACHE_PROGRESS_FADE_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .start();
    }

    private void hideCacheProgressIndicator(@NonNull LinearProgressIndicator currentProgressIndicator,
                                            @NonNull TextView currentStatusText) {
        if (cacheProgressHidePending) {
            return;
        }

        if (!cacheProgressIndicatorVisible
                && currentProgressIndicator.getVisibility() != View.VISIBLE
                && currentStatusText.getVisibility() != View.VISIBLE) {
            resetCacheProgressState();
            return;
        }

        cacheProgressIndicatorVisible = false;
        cacheProgressHidePending = true;
        int animationGeneration = ++cacheProgressAnimationGeneration;
        currentProgressIndicator.animate().cancel();
        currentStatusText.animate().cancel();
        currentProgressIndicator.postDelayed(() -> {
            if (cacheProgressAnimationGeneration != animationGeneration
                    || cacheProgressIndicator != currentProgressIndicator
                    || cacheProgressStatusText != currentStatusText) {
                return;
            }

            beginHeaderTransition(false);
            currentStatusText.setVisibility(View.GONE);
            currentProgressIndicator.setVisibility(View.GONE);
            currentProgressIndicator.postDelayed(() -> {
                if (cacheProgressAnimationGeneration != animationGeneration
                        || cacheProgressIndicator != currentProgressIndicator
                        || cacheProgressStatusText != currentStatusText) {
                    return;
                }

                currentStatusText.setAlpha(1f);
                currentStatusText.setText(CACHE_PROGRESS_STATUS_CACHING);
                currentProgressIndicator.setAlpha(1f);
                currentProgressIndicator.setProgressCompat(0, false);
                resetCacheProgressState();
                cacheProgressHidePending = false;
            }, HEADER_LAYOUT_ANIMATION_DURATION_MS);
        }, CACHE_PROGRESS_FINISHED_HOLD_MS);
    }

    private void resetCacheProgressState() {
        cacheStoriesTotal = 1;
        cacheStoriesCompleted = 0;
        cacheProgressStatus = CACHE_PROGRESS_STATUS_CACHING;
    }

    private void cacheStories() {
        if (cachingStories) {
            return;
        }

        startCacheProgress();
        boolean cacheArticles = SettingsUtils.shouldUseIntegratedWebView(getContext());
        StringRequest request = new StringRequest(Request.Method.GET, Utils.URL_TOP,
                response -> {
                    try {
                        JSONArray arr = new JSONArray(response);
                        int storyCount = Math.min(20, arr.length());
                        if (storyCount == 0) {
                            finishCacheProgress(CACHE_PROGRESS_STATUS_EMPTY);
                            return;
                        }

                        setCacheProgressTotal(storyCount);
                        final int[] remaining = { storyCount };
                        final int[] articleFailures = { 0 };
                        for (int i = 0; i < storyCount; i++) {
                            int id = arr.getInt(i);
                            String url = "https://hn.algolia.com/api/v1/items/" + id;
                            StringRequest r = new StringRequest(Request.Method.GET, url,
                                    res -> {
                                        Utils.cacheStory(getContext(), id, res);
                                        if (cacheArticles) {
                                            cacheStoryArticleSnapshot(id, res, articleFailures, () -> onCacheStoryFinished(remaining, articleFailures));
                                        } else {
                                            onCacheStoryFinished(remaining, articleFailures);
                                        }
                                    }, error -> onCacheStoryFinished(remaining, articleFailures));
                            r.setTag(requestTag);
                            queue.add(r);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        finishCacheProgress(CACHE_PROGRESS_STATUS_FAILED);
                    }
                }, error -> finishCacheProgress(CACHE_PROGRESS_STATUS_FAILED));

        request.setTag(requestTag);
        queue.add(request);
    }

    private void onCacheStoryFinished(int[] remaining, int[] articleFailures) {
        incrementCacheProgress();
        remaining[0]--;
        if (remaining[0] > 0) {
            return;
        }

        finishCacheProgress();
    }

    private void cacheStoryArticleSnapshot(int id, String storyJson, int[] articleFailures, Runnable onComplete) {
        try {
            JSONObject storyObject = new JSONObject(storyJson);
            if (!storyObject.has("url") || storyObject.isNull("url")) {
                onComplete.run();
                return;
            }

            String articleUrl = storyObject.optString("url", "");
            if (TextUtils.isEmpty(articleUrl) || !(articleUrl.startsWith("http://") || articleUrl.startsWith("https://"))) {
                onComplete.run();
                return;
            }

            StringRequest articleRequest = new StringRequest(Request.Method.GET, articleUrl,
                    html -> {
                        Utils.cacheArticleSnapshot(getContext(), id, articleUrl, html);
                        onComplete.run();
                    },
                    error -> {
                        articleFailures[0]++;
                        onComplete.run();
                    });
            articleRequest.setTag(requestTag);
            queue.add(articleRequest);
        } catch (JSONException e) {
            e.printStackTrace();
            articleFailures[0]++;
            onComplete.run();
        }
    }

    private void showCachedStories() {
        showingCached = true;
        swipeRefreshLayout.setRefreshing(false);

        replaceStories(Utils.loadCachedStories(getContext()));
        loadedTo = stories.size() - 1;
        loadingFailed = false;
        loadingFailedServerError = false;
        updateHeader();
    }

    private void hideUpdateButton() {
        updateButtonShowing = false;
        Context ctx = getContext();
        updateLastUpdatedHeader(ctx);
        applyHeaderPadding(ctx);

        if (updateFab != null && updateFab.getVisibility() == View.VISIBLE) {
            updateFab.hide();
        }
    }

    private void showUpdateButton() {
        updateButtonShowing = true;
        Context ctx = getContext();
        updateLastUpdatedHeader(ctx);
        applyHeaderPadding(ctx);

        if (updateFab != null && updateFab.getVisibility() != View.VISIBLE) {
            updateFab.show();
        }
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
