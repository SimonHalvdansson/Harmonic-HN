package com.simon.harmonichackernews;

import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
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
    private EditText searchEditText;
    private ImageButton searchButton;
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

    public static boolean showingCached = false;

    private int loadedTo = -1;
    private boolean paginationMode = false;
    private static final int PAGINATION_PAGE_SIZE = 30;

    public final static String[] hnUrls = new String[]{Utils.URL_TOP, Utils.URL_NEW, Utils.URL_BEST, Utils.URL_ASK, Utils.URL_SHOW, Utils.URL_JOBS};

    long lastLoaded = 0;
    long lastClick = 0;
    private final static long CLICK_INTERVAL = 350;

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

        // Bind header views
        headerContainer = view.findViewById(R.id.stories_header_container);
        typeSpinner = view.findViewById(R.id.stories_header_spinner);
        spinnerContainer = view.findViewById(R.id.stories_header_spinner_container);
        searchEditText = view.findViewById(R.id.stories_header_search_edittext);
        searchButton = view.findViewById(R.id.stories_header_search_button);
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

        swipeRefreshLayout.setOnRefreshListener(this::attemptRefresh);
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

                // Apply top inset to header container
                boolean compactHeader = SettingsUtils.shouldUseCompactHeader(getContext());
                int topPad = insets.top + Utils.pxFromDpInt(getResources(), compactHeader ? 20 : 40);
                int bottomPad = Utils.pxFromDpInt(getResources(), compactHeader ? 10 : 26);
                int sidePad = Utils.pxFromDpInt(getResources(), 16);
                headerContainer.setPadding(sidePad, topPad, sidePad, bottomPad);

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
                boolean isKeyboardSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH;
                boolean isHardwareEnterKey = keyEvent != null
                        && keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER
                        && keyEvent.getAction() == KeyEvent.ACTION_DOWN;

                if (!isKeyboardSearchAction && !isHardwareEnterKey) {
                    return false;
                }

                search(searchEditText.getText().toString());
                if (textView != null) {
                    InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                }
                return true;
            }
        });

        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                searching = !searching;
                updateSearchStatus();

                InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (searching) {
                    imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                } else {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    lastSearch = "";
                }
            }
        });

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
        if (headerContainer == null) return;

        Context ctx = getContext();
        if (ctx == null) return;

        boolean compactHeader = SettingsUtils.shouldUseCompactHeader(ctx);
        int topPad = topInset + Utils.pxFromDpInt(getResources(), compactHeader ? 20 : 40);
        int bottomPad = Utils.pxFromDpInt(getResources(), compactHeader ? 10 : 26);
        int sidePad = Utils.pxFromDpInt(getResources(), 16);
        headerContainer.setPadding(sidePad, topPad, sidePad, bottomPad);

        moreButton.setVisibility(searching ? View.GONE : View.VISIBLE);
        spinnerContainer.setVisibility(searching ? View.GONE : View.VISIBLE);

        searchButton.setImageResource(searching ? R.drawable.ic_action_cancel : R.drawable.ic_action_search);

        searchEditText.setVisibility(searching ? View.VISIBLE : View.GONE);

        if (searching) {
            loadingIndicator.setVisibility(View.GONE);
            searchEditText.requestFocus();
            searchEditText.setText(lastSearch);
            searchEditText.setSelection(lastSearch.length());

            searchEmptyContainer.setVisibility(stories.isEmpty() ? View.VISIBLE : View.GONE);
            noBookmarksLayout.setVisibility(View.GONE);
        } else {
            noBookmarksLayout.setVisibility((stories.isEmpty() && adapter.type == SettingsUtils.getBookmarksIndex(ctx.getResources())) ? View.VISIBLE : View.GONE);
            searchEmptyContainer.setVisibility(View.GONE);

            loadingIndicator.setVisibility(stories.isEmpty() && !loadingFailed && !loadingFailedServerError && (adapter.type != SettingsUtils.getBookmarksIndex(ctx.getResources())) ? View.VISIBLE : View.GONE);
        }

        showingCachedText.setVisibility(showingCached && !searching ? View.VISIBLE : View.GONE);

        typeSpinner.setSelection(adapter.type);

        TooltipCompat.setTooltipText(searchButton, searching ? "Close" : "Search");
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
        hideUpdateButton();
        if (searching) {
            search(lastSearch);
            return;
        }

        swipeRefreshLayout.setRefreshing(true);

        // cancel all ongoing
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

        if (getActivity() != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).backPressedCallback.setEnabled(searching);
        }

        swipeRefreshLayout.setEnabled(!searching);

        if (searching) {
            // cancel all ongoing
            queue.cancelAll(requestTag);
            swipeRefreshLayout.setRefreshing(false);
            clearStories();
            appBarLayout.setExpanded(true, false);
        } else {
            clearStories();
        }

        updateHeader();

        if (!searching) {
            attemptRefresh();
        }
    }

    private void loadTopStoriesSince(int start_i) {
        loadAlgolia("https://hn.algolia.com/api/v1/search?tags=story&numericFilters=created_at_i>" + start_i + "&hitsPerPage=200");
    }

    private void search(String query) {
        lastSearch = query;

        loadAlgolia("https://hn.algolia.com/api/v1/search_by_date?query=" + query + "&tags=story&hitsPerPage=200&typoTolerance=min");
    }

    private void loadAlgolia(String url) {
        swipeRefreshLayout.setEnabled(!searching);
        swipeRefreshLayout.setRefreshing(true);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    // Parse JSON on background thread
                    BackgroundJSONParser.parseAlgoliaJson(response, new BackgroundJSONParser.AlgoliaParseCallback() {
                        @Override
                        public void onParseSuccess(List<Story> parsedStories) {
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
                            swipeRefreshLayout.setRefreshing(false);
                            error.printStackTrace();
                        }
                    });

                }, error -> {
            if (error.networkResponse != null && error.networkResponse.statusCode == 404) {
                loadingFailedServerError = true;
            }

            error.printStackTrace();
            swipeRefreshLayout.setRefreshing(false);
            loadingFailed = true;
            updateHeader();
        });

        updateHeader();

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
            searching = false;
            lastSearch = "";
            updateSearchStatus();
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

    public interface StoryClickListener {
        void openStory(Story story, int pos, boolean showWebsite);
    }
}
