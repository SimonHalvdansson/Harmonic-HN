package com.simon.harmonichackernews;

import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
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
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
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
import com.simon.harmonichackernews.utils.StoryUpdate;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.UtilsKt;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class StoriesFragment extends Fragment {

    private StoryClickListener storyClickListener;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ExtendedFloatingActionButton updateFab;
    private RecyclerView recyclerView;
    private AppBarLayout appBarLayout;

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
    private ImageButton searchButton;
    private ImageButton closeSearchButton;
    private ImageButton moreButton;
    private RelativeLayout loadingIndicator;
    private LinearLayout loadingFailedLayout;
    private TextView loadingFailedText;
    private TextView loadingFailedAlgoliaLayout;
    private LinearLayout noBookmarksLayout;
    private TextView showingCachedText;
    private LinearLayout searchEmptyContainer;
    private Button retryButton;
    private Button showCachedButton;

    private StoryRecyclerViewAdapter adapter;
    private List<Story> stories;
    private RequestQueue queue;
    private final Object requestTag = new Object();
    private LinearLayoutManager linearLayoutManager;
    private ArrayList<String> filterWords;
    private ArrayList<String> filterDomains;
    private boolean hideJobs, alwaysOpenComments, hideClicked;
    private boolean searching = false;
    private boolean loadingFailed = false;
    private boolean loadingFailedServerError = false;
    private String lastSearch = "";
    private int searchSortIndex = 0;
    private int searchDateRangeIndex = 0;
    private int searchMinimumPointsIndex = 0;
    private int searchMinimumCommentsIndex = 0;
    private int algoliaRequestGeneration = 0;
    private boolean algoliaLoading = false;
    private String activeAlgoliaUrl = null;
    private List<Story> storiesBeforeSearch = null;
    private int loadedToBeforeSearch = -1;
    private int visibleStoryCountBeforeSearch = Integer.MAX_VALUE;
    private boolean showingCachedBeforeSearch = false;
    private boolean loadingFailedBeforeSearch = false;
    private boolean loadingFailedServerErrorBeforeSearch = false;
    private boolean loadPendingBeforeSearch = false;

    public static boolean showingCached = false;

    private int loadedTo = -1;
    private boolean paginationMode = false;
    private static final int PAGINATION_PAGE_SIZE = 30;

    public final static String[] hnUrls = new String[]{Utils.URL_TOP, Utils.URL_NEW, Utils.URL_BEST, Utils.URL_ASK, Utils.URL_SHOW, Utils.URL_JOBS};

    long lastLoaded = 0;
    long lastClick = 0;
    private final static long CLICK_INTERVAL = 350;
    private static final long SEARCH_HEADER_ANIMATION_DURATION_MS = 180;
    private static final long HEADER_LAYOUT_ANIMATION_DURATION_MS = 220;
    private static final String[] SEARCH_SORT_LABELS = new String[]{"Relevance", "Newest"};
    private static final String[] SEARCH_DATE_RANGE_LABELS = new String[]{"All time", "Past day", "Past week", "Past month", "Past year"};
    private static final int[] SEARCH_DATE_RANGE_DAYS = new int[]{0, 1, 7, 30, 365};
    private static final String[] SEARCH_MINIMUM_POINTS_LABELS = new String[]{"Any points", "5+ points", "25+ points", "100+ points"};
    private static final int[] SEARCH_MINIMUM_POINTS = new int[]{0, 5, 25, 100};
    private static final String[] SEARCH_MINIMUM_COMMENTS_LABELS = new String[]{"Any comments", "5+ comments", "25+ comments", "100+ comments"};
    private static final int[] SEARCH_MINIMUM_COMMENTS = new int[]{0, 5, 25, 100};

    private int topInset = 0;

    public StoriesFragment() {
        super(R.layout.fragment_stories);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        HistoriesUtils.INSTANCE.init(requireContext());

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.stories_recyclerview);
        swipeRefreshLayout = view.findViewById(R.id.stories_swipe_refresh);
        updateFab = view.findViewById(R.id.stories_update_fab);
        appBarLayout = view.findViewById(R.id.stories_appbar);
        appBarLayout.addOnOffsetChangedListener((appBar, verticalOffset) -> {
            float totalScrollRange = appBar.getTotalScrollRange();
            if (totalScrollRange > 0) {
                headerContainer.setAlpha(1f - (Math.abs(verticalOffset) / totalScrollRange));
            }
        });
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
        searchBar.setElevation(0f);
        searchEditText.bringToFront();
        searchButton = view.findViewById(R.id.stories_header_search_button);
        closeSearchButton = view.findViewById(R.id.stories_header_close_search_button);
        moreButton = view.findViewById(R.id.stories_header_more);
        loadingIndicator = view.findViewById(R.id.stories_header_loading_indicator);
        loadingFailedLayout = view.findViewById(R.id.stories_header_loading_failed);
        loadingFailedText = view.findViewById(R.id.stories_header_loading_failed_text);
        loadingFailedAlgoliaLayout = view.findViewById(R.id.stories_header_loading_failed_algolia);
        noBookmarksLayout = view.findViewById(R.id.stories_header_no_bookmarks);
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
        recyclerView.setLayoutManager(linearLayoutManager);

        stories = new ArrayList<>();
        setupAdapter();
        recyclerView.setAdapter(adapter);

        // Setup header after adapter so spinner callback can safely access adapter.type
        setupHeader();

        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

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

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            int lastVisibleItem;

            @Override
            public void onScrolled(@NotNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // Only enable infinite scroll if pagination mode is OFF
                if (!searching && !paginationMode) {
                    lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();

                    int visibleThreshold = 17;
                    for (int i = loadedTo + 1; i < Math.min(lastVisibleItem + visibleThreshold, stories.size()); i++) {
                        loadedTo = i;

                        loadStory(stories.get(i), 0);
                    }
                }
            }
        });

        queue = NetworkComponent.getRequestQueueInstance(requireContext());
        attemptRefresh();

        StoryUpdate.setStoryUpdatedListener(new StoryUpdate.StoryUpdateListener() {
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
        });
    }

    private int getPreferredTypeIndex() {
        String[] sortingOptions = getResources().getStringArray(R.array.sorting_options);
        ArrayList<CharSequence> typeAdapterList = new ArrayList<>(Arrays.asList(sortingOptions));
        return typeAdapterList.indexOf(SettingsUtils.getPreferredStoryType(getContext()));
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
        updateSearchOptionChips();

        searchButton.setOnClickListener(view -> openSearch());
        closeSearchButton.setOnClickListener(view -> closeSearch(view));

        // Set up spinner
        String[] sortingOptions = ctx.getResources().getStringArray(R.array.sorting_options);
        ArrayList<CharSequence> typeAdapterList = new ArrayList<>(Arrays.asList(sortingOptions));
        ArrayAdapter<CharSequence> spinnerAdapter = new ArrayAdapter<>(ctx, R.layout.spinner_top_layout, R.id.selection_dropdown_item_textview, typeAdapterList);
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item_layout);

        typeSpinner.setAdapter(spinnerAdapter);
        typeSpinner.setSelection(getPreferredTypeIndex());
        typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i != adapter.type) {
                    adapter.type = i;
                    attemptRefresh();
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
        if (ctx == null || headerContainer == null) return;

        boolean compactHeader = SettingsUtils.shouldUseCompactHeader(ctx);
        int topPad = topInset + Utils.pxFromDpInt(getResources(), compactHeader ? 20 : 40);
        int bottomPad = Utils.pxFromDpInt(getResources(), compactHeader
                ? (searching ? 6 : 10)
                : (searching ? 18 : 26));
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

        applyHeaderPadding(ctx);

        beginHeaderTransition(animateSearchTransition);

        moreButton.setVisibility(searching ? View.GONE : View.VISIBLE);
        spinnerContainer.setVisibility(searching ? View.GONE : View.VISIBLE);
        searchButton.setVisibility(searching ? View.GONE : View.VISIBLE);
        closeSearchButton.setVisibility(searching ? View.VISIBLE : View.GONE);

        searchContainer.setVisibility(searching ? View.VISIBLE : View.GONE);
        searchOptionsScroll.setVisibility(searching ? View.VISIBLE : View.GONE);

        if (searching) {
            loadingIndicator.setVisibility(algoliaLoading ? View.VISIBLE : View.GONE);
            searchEditText.requestFocus();
            searchEditText.setText(lastSearch);
            searchEditText.setSelection(lastSearch.length());

            searchEmptyContainer.setVisibility(stories.isEmpty() && !algoliaLoading ? View.VISIBLE : View.GONE);
            noBookmarksLayout.setVisibility(View.GONE);
        } else {
            noBookmarksLayout.setVisibility((stories.isEmpty() && adapter.type == SettingsUtils.getBookmarksIndex(ctx.getResources())) ? View.VISIBLE : View.GONE);
            searchEmptyContainer.setVisibility(View.GONE);

            loadingIndicator.setVisibility(stories.isEmpty() && !loadingFailed && !loadingFailedServerError && (adapter.type != SettingsUtils.getBookmarksIndex(ctx.getResources())) ? View.VISIBLE : View.GONE);
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

        showCachedButton.setVisibility(loadingFailed && Utils.hasCachedStories(ctx) ? View.VISIBLE : View.GONE);

        loadingFailedAlgoliaLayout.setVisibility(loadingFailedServerError ? View.VISIBLE : View.GONE);
        updateRecyclerScrollState();
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
        beginSearchOptionsTransition();

        searchSortChip.setText(SEARCH_SORT_LABELS[searchSortIndex]);
        searchDateChip.setText(SEARCH_DATE_RANGE_LABELS[searchDateRangeIndex]);
        searchPointsChip.setText(SEARCH_MINIMUM_POINTS_LABELS[searchMinimumPointsIndex]);
        searchCommentsChip.setText(SEARCH_MINIMUM_COMMENTS_LABELS[searchMinimumCommentsIndex]);

        searchSortChip.setContentDescription("Search sort: " + SEARCH_SORT_LABELS[searchSortIndex]);
        searchDateChip.setContentDescription("Search date range: " + SEARCH_DATE_RANGE_LABELS[searchDateRangeIndex]);
        searchPointsChip.setContentDescription("Search minimum points: " + SEARCH_MINIMUM_POINTS_LABELS[searchMinimumPointsIndex]);
        searchCommentsChip.setContentDescription("Search minimum comments: " + SEARCH_MINIMUM_COMMENTS_LABELS[searchMinimumCommentsIndex]);
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
        updateSearchStatus();
        resetSearchOptionsScroll();

        focusSearchInput();
    }

    private void closeSearch(@Nullable View view) {
        searching = false;
        lastSearch = "";
        updateSearchStatus();

        View tokenView = view != null ? view : searchEditText;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(tokenView.getWindowToken(), 0);
        searchEditText.clearFocus();
        resetSearchOptionsScroll();
    }

    private void focusSearchInput() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        searchEditText.post(() -> {
            searchEditText.requestFocus();
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void resetSearchOptionsScroll() {
        searchOptionsScroll.post(() -> searchOptionsScroll.scrollTo(0, 0));
    }

    private void resetPaginationState() {
        loadedTo = -1;
        adapter.visibleStoryCount = paginationMode ? PAGINATION_PAGE_SIZE : Integer.MAX_VALUE;
    }

    private int getInitialLoadCount() {
        return paginationMode ? PAGINATION_PAGE_SIZE : 20;
    }

    private void clearStories() {
        int oldItemCount = adapter.getItemCount();
        stories.clear();
        resetPaginationState();

        if (oldItemCount > 0) {
            adapter.notifyItemRangeRemoved(0, oldItemCount);
        }
    }

    private void replaceStories(List<Story> newStories) {
        clearStories();
        stories.addAll(newStories);

        int newItemCount = adapter.getItemCount();
        if (newItemCount > 0) {
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
        loadPendingBeforeSearch = stories.isEmpty()
                && !loadingFailed
                && !loadingFailedServerError
                && adapter.type != SettingsUtils.getBookmarksIndex(getResources());
    }

    private boolean restoreStoriesBeforeSearch() {
        if (storiesBeforeSearch == null) {
            return false;
        }

        int oldItemCount = adapter.getItemCount();
        stories.clear();
        if (oldItemCount > 0) {
            adapter.notifyItemRangeRemoved(0, oldItemCount);
        }

        stories.addAll(storiesBeforeSearch);
        loadedTo = loadedToBeforeSearch;
        adapter.visibleStoryCount = visibleStoryCountBeforeSearch;
        showingCached = showingCachedBeforeSearch;
        loadingFailed = loadingFailedBeforeSearch;
        loadingFailedServerError = loadingFailedServerErrorBeforeSearch;

        int newItemCount = adapter.getItemCount();
        if (newItemCount > 0) {
            adapter.notifyItemRangeInserted(0, newItemCount);
        }

        storiesBeforeSearch = null;
        loadedToBeforeSearch = -1;
        visibleStoryCountBeforeSearch = Integer.MAX_VALUE;
        showingCachedBeforeSearch = false;
        loadingFailedBeforeSearch = false;
        loadingFailedServerErrorBeforeSearch = false;

        return true;
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
                SettingsUtils.shouldShowIndex(getContext()),
                SettingsUtils.shouldUseCompactHeader(getContext()),
                SettingsUtils.shouldUseLeftAlign(getContext()),
                SettingsUtils.getPreferredHotness(getContext()),
                SettingsUtils.getPreferredFaviconProvider(getContext()),
                null,
                getPreferredTypeIndex()
        );

        adapter.paginationMode = paginationMode;
        adapter.visibleStoryCount = paginationMode ? PAGINATION_PAGE_SIZE : Integer.MAX_VALUE;

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
                story.clicked = true;
                HistoriesUtils.INSTANCE.addHistory(requireContext(), story.id);

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

        // Set up pagination "Load More" button click listener
        adapter.setOnLoadMoreClickListener(v -> {
            if (paginationMode) {
                // Load next batch of stories
                int oldLoadedTo = loadedTo;
                int newLoadedTo = Math.min(
                        loadedTo + PAGINATION_PAGE_SIZE,
                        stories.size() - 1
                );

                // Load the next batch of stories
                for (int i = oldLoadedTo + 1; i <= newLoadedTo; i++) {
                    loadedTo = i;
                    loadStory(stories.get(i), 0);
                }

                // Update adapter to show more items
                adapter.loadNextPage();
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
                boolean oldBookmarked = Utils.isBookmarked(ctx, story.id);
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

                popupMenu.getMenu().add(oldBookmarked ? "Remove bookmark" : "Bookmark").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(@NonNull MenuItem item) {
                        if (oldBookmarked) {
                            Utils.removeBookmark(ctx, story.id);
                            if (adapter.type == SettingsUtils.getBookmarksIndex(ctx.getResources())) {
                                stories.remove(story);
                                adapter.notifyItemRemoved(position);
                                return true;
                            }
                        } else {
                            Utils.addBookmark(ctx, story.id);
                        }

                        adapter.notifyItemChanged(position);
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
    public void onResume() {
        super.onResume();

        filterWords = Utils.getFilterWords(getContext());
        filterDomains = Utils.getFilterDomains(getContext());
        hideJobs = SettingsUtils.shouldHideJobs(getContext());
        hideClicked = SettingsUtils.shouldHideClicked(getContext());
        alwaysOpenComments = SettingsUtils.shouldAlwaysOpenComments(getContext());

        long timeDiff = System.currentTimeMillis() - lastLoaded;

        // if more than 1 hr
        if (timeDiff > 1000 * 60 * 60 && !searching && adapter.type != SettingsUtils.getBookmarksIndex(getResources()) && !currentTypeIsAlgolia()) {
            showUpdateButton();
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

        if (adapter.showIndex != SettingsUtils.shouldShowIndex(getContext())) {
            adapter.showIndex = !adapter.showIndex;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        if (adapter.leftAlign != SettingsUtils.shouldUseLeftAlign(getContext())) {
            adapter.leftAlign = !adapter.leftAlign;
            setupAdapter();
            recyclerView.setAdapter(adapter);
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

        if (hideJobs != SettingsUtils.shouldHideJobs(getContext())) {
            hideJobs = !hideJobs;
            attemptRefresh();
        }

        if (adapter.faviconProvider != SettingsUtils.getPreferredFaviconProvider(getContext())) {
            adapter.faviconProvider = SettingsUtils.getPreferredFaviconProvider(getContext());
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (queue != null) {
            queue.cancelAll(requestTag);
        }
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
            story.clicked = true;
            HistoriesUtils.INSTANCE.addHistory(requireContext(), story.id);

            openComments(story, position, false);

            adapter.notifyItemChanged(position);
        }
    }

    private void loadStory(Story story, final int attempt) {
        if (story.loaded || attempt >= 3) {
            return;
        }

        String url = "https://hacker-news.firebaseio.com/v0/item/" + story.id + ".json";

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    int index = stories.indexOf(story);
                    try {
                        if (!JSONParser.updateStoryWithHNJson(response, story, adapter.type == SettingsUtils.getHistoryIndex(getResources()))) {
                            stories.remove(story);
                            adapter.notifyItemRemoved(index);
                            loadedTo = Math.max(-1, loadedTo - 1);
                            return;
                        }

                        // lets check if we should remove the post because of filter
                        for (String phrase : filterWords) {
                            if (story.title.toLowerCase().contains(phrase.toLowerCase())) {
                                stories.remove(story);
                                adapter.notifyItemRemoved(index);
                                loadedTo = Math.max(-1, loadedTo - 1);
                                return;
                            }
                        }

                        // or domain name
                        for (String phrase : filterDomains) {
                            try {
                                String domain = Utils.getDomainName(story.url);
                                if (domain.toLowerCase().contains(phrase.toLowerCase())) {

                                    stories.remove(story);
                                    adapter.notifyItemRemoved(index);
                                    loadedTo = Math.max(-1, loadedTo - 1);
                                    return;
                                }
                            } catch (Exception e) {
                                //nothing
                            }
                        }

                        // or because it's a job
                        if (hideJobs && adapter.type != SettingsUtils.getJobsIndex(getResources()) && (story.isJob || story.by.equals("whoishiring"))) {
                            stories.remove(story);
                            adapter.notifyItemRemoved(index);
                            loadedTo = Math.max(-1, loadedTo - 1);
                            return;
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
            error.printStackTrace();
            story.loadingFailed = true;
            adapter.notifyItemChanged(stories.indexOf(story));
            loadStory(story, attempt + 1);
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
        menu.findItem(R.id.menu_cache).setVisible(!showingCached);
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

    private void invalidateAlgoliaLoad() {
        algoliaRequestGeneration++;
        algoliaLoading = false;
        activeAlgoliaUrl = null;
    }

    private void attemptRefresh(boolean showSwipeRefreshIndicator) {
        hideUpdateButton();
        if (searching) {
            search(lastSearch);
            return;
        }

        swipeRefreshLayout.setRefreshing(showSwipeRefreshIndicator);

        // cancel all ongoing
        invalidateAlgoliaLoad();
        queue.cancelAll(requestTag);

        if (currentTypeIsAlgolia()) {
            // algoliaStuff
            int currentTime = (int) (System.currentTimeMillis() / 1000);
            int startTime = currentTime;
            if (adapter.type == 1) {
                startTime = currentTime - 60 * 60 * 24;
            } else if (adapter.type == 2) {
                startTime = currentTime - 60 * 60 * 48;
            } else if (adapter.type == 3) {
                startTime = currentTime - 60 * 60 * 24 * 7;
            }

            loadTopStoriesSince(startTime);

            return;
        }

        lastLoaded = System.currentTimeMillis();

        if (adapter.type == SettingsUtils.getBookmarksIndex(getResources())) {
            // lets load bookmarks instead - or rather add empty stories with correct id:s and start loading them
            ArrayList<Story> refreshedStories = new ArrayList<>();
            showingCached = false;

            ArrayList<Bookmark> bookmarks = Utils.loadBookmarks(getContext(), true);

            for (int i = 0; i < bookmarks.size(); i++) {
                Story s = new Story("Loading...", bookmarks.get(i).id, false, false);
                refreshedStories.add(s);
            }

            replaceStories(refreshedStories);

            int initialLoadCount = Math.min(getInitialLoadCount(), stories.size());
            for (int i = 0; i < initialLoadCount; i++) {
                loadStory(stories.get(i), 0);
                loadedTo = i;
            }

            updateHeader();
            swipeRefreshLayout.setRefreshing(false);

            return;
        } else if (adapter.type == SettingsUtils.getHistoryIndex(getResources())) {
            ArrayList<Story> refreshedStories = new ArrayList<>();
            showingCached = false;
            List<History> histories = UtilsKt.INSTANCE.loadHistories(requireContext(), true);

            for (int i = 0; i < histories.size(); i++) {
                Story s = new Story("Loading...", histories.get(i).getId(), false, false, histories.get(i).getCreated());
                refreshedStories.add(s);
            }

            replaceStories(refreshedStories);

            int initialLoadCount = Math.min(getInitialLoadCount(), stories.size());
            for (int i = 0; i < initialLoadCount; i++) {
                loadStory(stories.get(i), 0);
                loadedTo = i;
            }

            updateHeader();
            swipeRefreshLayout.setRefreshing(false);

            return;
        }

        // if none of the above, do a normal loading
        StringRequest stringRequest = new StringRequest(Request.Method.GET, hnUrls[adapter.type == 0 ? 0 : adapter.type - 3],
                response -> {
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
                            }

                            refreshedStories.add(s);
                        }

                        replaceStories(refreshedStories);

                        if (loadingFailed) {
                            loadingFailed = false;
                            loadingFailedServerError = false;
                        }

                        updateHeader();

                        // Load initial batch of stories
                        int storiesToLoad = Math.min(getInitialLoadCount(), stories.size());
                        for (int i = 0; i < storiesToLoad; i++) {
                            loadedTo = i;
                            loadStory(stories.get(i), 0);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, error -> {
            swipeRefreshLayout.setRefreshing(false);
            loadingFailed = true;
            updateHeader();
        });

        updateHeader();
        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
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
            invalidateAlgoliaLoad();
            queue.cancelAll(requestTag);
            swipeRefreshLayout.setRefreshing(false);
            clearStories();
            appBarLayout.setExpanded(true, false);
        } else {
            shouldRefreshAfterRestore = loadPendingBeforeSearch
                    && storiesBeforeSearch != null
                    && storiesBeforeSearch.isEmpty();
            loadPendingBeforeSearch = false;

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

    private void loadTopStoriesSince(int start_i) {
        Uri uri = Uri.parse("https://hn.algolia.com/api/v1/search")
                .buildUpon()
                .appendQueryParameter("tags", "story")
                .appendQueryParameter("numericFilters", "created_at_i>" + start_i)
                .appendQueryParameter("hitsPerPage", "200")
                .build();
        loadAlgolia(uri.toString());
    }

    private void search(String query) {
        lastSearch = query;

        String endpoint = searchSortIndex == 0
                ? "https://hn.algolia.com/api/v1/search"
                : "https://hn.algolia.com/api/v1/search_by_date";
        Uri.Builder builder = Uri.parse(endpoint).buildUpon()
                .appendQueryParameter("query", query)
                .appendQueryParameter("tags", "story")
                .appendQueryParameter("hitsPerPage", "200")
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

    private void loadAlgolia(String url) {
        if (algoliaLoading && TextUtils.equals(activeAlgoliaUrl, url)) {
            return;
        }

        invalidateAlgoliaLoad();
        final int requestGeneration = algoliaRequestGeneration;
        algoliaLoading = true;
        activeAlgoliaUrl = url;
        queue.cancelAll(requestTag);

        swipeRefreshLayout.setEnabled(!searching);
        swipeRefreshLayout.setRefreshing(!searching);
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

                            Iterator<Story> iterator = parsedStories.iterator();
                            while (iterator.hasNext()) {
                                Story story = iterator.next();
                                story.clicked = HistoriesUtils.INSTANCE.isHistoryExist(story.id);

                                if (story.title != null) {
                                    for (String phrase : filterWords) {
                                        if (story.title.toLowerCase().contains(phrase.toLowerCase())) {
                                            iterator.remove();
                                            break;
                                        }
                                    }
                                    // or domain name
                                    for (String phrase : filterDomains) {
                                        try {
                                            String domain = Utils.getDomainName(story.url);
                                            if (domain.toLowerCase().contains(phrase.toLowerCase())) {
                                                iterator.remove();
                                                break;
                                            }
                                        } catch (Exception e) {
                                            //nothing
                                        }
                                    }
                                }

                                if (hideClicked && story.clicked) {
                                    iterator.remove();
                                }
                            }

                            loadingFailed = false;
                            loadingFailedServerError = false;
                            showingCached = false;

                            replaceStories(parsedStories);
                            updateHeader();

                            // Set loadedTo for Algolia stories (they're already fully loaded)
                            loadedTo = stories.size() - 1;
                        }

                        @Override
                        public void onParseError(JSONException error) {
                            if (requestGeneration != algoliaRequestGeneration) {
                                return;
                            }

                            algoliaLoading = false;
                            activeAlgoliaUrl = null;
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
        return searching && stories != null && stories.isEmpty();
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

    public boolean currentTypeIsAlgolia() {
        return 0 < adapter.type && 4 > adapter.type;
    }

    public boolean exitSearch() {
        if (searching) {
            closeSearch(null);
            return true;
        }
        return false;
    }

    private void cacheStories() {
        Toast.makeText(getContext(), "Caching stories...", Toast.LENGTH_SHORT).show();
        StringRequest request = new StringRequest(Request.Method.GET, Utils.URL_TOP,
                response -> {
                    try {
                        JSONArray arr = new JSONArray(response);
                        for (int i = 0; i < Math.min(20, arr.length()); i++) {
                            int id = arr.getInt(i);
                            String url = "https://hn.algolia.com/api/v1/items/" + id;
                            StringRequest r = new StringRequest(Request.Method.GET, url,
                                    res -> Utils.cacheStory(getContext(), id, res), error -> {});
                            queue.add(r);
                        }
                        Toast.makeText(getContext(), "Stories cached", Toast.LENGTH_SHORT).show();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(getContext(), "Caching failed", Toast.LENGTH_SHORT).show();
                    }
                }, error -> Toast.makeText(getContext(), "Caching failed", Toast.LENGTH_SHORT).show());

        queue.add(request);
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
        if (updateFab.getVisibility() == View.VISIBLE) {

            float endYPosition = getResources().getDisplayMetrics().heightPixels - updateFab.getY() + updateFab.getHeight() + ViewUtils.getNavigationBarHeight(getResources());
            PathInterpolator pathInterpolator = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

            ObjectAnimator yAnimator = ObjectAnimator.ofFloat(updateFab, "translationY", endYPosition);
            yAnimator.setDuration(200);

            yAnimator.setInterpolator(pathInterpolator);

            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(updateFab, "alpha", 1.0f, 0.0f);
            alphaAnimator.setDuration(300);
            alphaAnimator.setInterpolator(pathInterpolator);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(yAnimator, alphaAnimator);

            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    updateFab.setVisibility(View.GONE);
                    updateFab.setTranslationY(0);
                    updateFab.setAlpha(1f);
                }
            });

            animatorSet.start();
        }
    }

    private void showUpdateButton() {
        if (updateFab.getVisibility() != View.VISIBLE) {
            updateFab.setVisibility(View.VISIBLE);

            AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
            anim.setDuration(300);
            anim.setRepeatMode(Animation.REVERSE);
            updateFab.startAnimation(anim);
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
