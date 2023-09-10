package com.simon.harmonichackernews;

import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
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
import com.android.volley.toolbox.Volley;
import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.VolleyOkHttp3StackInterceptors;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryUpdate;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class StoriesFragment extends Fragment {

    private StoryClickListener storyClickListener;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayout updateContainer;
    private RecyclerView recyclerView;

    private StoryRecyclerViewAdapter adapter;
    private List<Story> stories;
    private RequestQueue queue;
    private LinearLayoutManager linearLayoutManager;
    private Set<Integer> clickedIds;
    private ArrayList<String> filterWords;
    private boolean hideJobs, alwaysOpenComments, hideClicked;
    private String lastSearch;
    private boolean lastSearchRelevance;
    private String lastSearchAge;

    private int loadedTo = 0;

    public final static String[] hnUrls = new String[]{Utils.URL_TOP, Utils.URL_NEW, Utils.URL_BEST, Utils.URL_ASK, Utils.URL_SHOW, Utils.URL_JOBS};

    private int currentType = 0;

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
        clickedIds = SettingsUtils.readIntSetFromSharedPreferences(requireContext(), Utils.KEY_SHARED_PREFERENCES_CLICKED_IDS);

        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.stories_recyclerview);
        swipeRefreshLayout = view.findViewById(R.id.stories_swipe_refresh);
        updateContainer = view.findViewById(R.id.stories_update_container);
        Button updateButton = view.findViewById(R.id.stories_update_button);

        swipeRefreshLayout.setOnRefreshListener(this::attemptRefresh);
        ViewUtils.setUpSwipeRefreshWithStatusBarOffset(swipeRefreshLayout);

        linearLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(linearLayoutManager);

        stories = new ArrayList<>();
        setupAdapter();
        recyclerView.setAdapter(adapter);

        ViewCompat.setOnApplyWindowInsetsListener(view, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

                CoordinatorLayout.LayoutParams params = (CoordinatorLayout.LayoutParams) updateContainer.getLayoutParams();
                params.bottomMargin = insets.bottom + Utils.pxFromDpInt(getResources(), 8);
                updateContainer.setLayoutParams(params);

                topInset = insets.top;

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(view);

        updateButton.setOnClickListener((v) -> {
            attemptRefresh();
            recyclerView.smoothScrollToPosition(0);
        });

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            int lastVisibleItem;

            @Override
            public void onScrolled(@NotNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (!adapter.searching) {
                    lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();

                    int visibleThreshold = 17;
                    for (int i = loadedTo + 1; i < Math.min(lastVisibleItem + visibleThreshold, stories.size()); i++) {
                        loadedTo = i;

                        loadStory(stories.get(i), 0);
                    }
                }
            }
        });

        queue = Volley.newRequestQueue(requireContext(), new VolleyOkHttp3StackInterceptors());
        stories.add(new Story());
        attemptRefresh();

        StoryUpdate.setStoryUpdatedListener(new StoryUpdate.StoryUpdateListener() {
            @Override
            public void callback(Story story) {
                for (int i = 1; i < stories.size(); i++) {
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

    private void setupAdapter() {
        adapter = new StoryRecyclerViewAdapter(stories,
                SettingsUtils.shouldShowPoints(getContext()),
                SettingsUtils.shouldUseCompactView(getContext()),
                SettingsUtils.shouldShowThumbnails(getContext()),
                SettingsUtils.shouldShowIndex(getContext()),
                SettingsUtils.shouldHideJobs(getContext()),
                SettingsUtils.shouldUseCompactHeader(getContext()),
                SettingsUtils.shouldUseLeftAlign(getContext()),
                SettingsUtils.getPreferredHotness(getContext()),
                null);

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
                clickedIds.add(story.id);

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
        adapter.setOnRefreshListener(this::attemptRefresh);
        adapter.setOnMoreClickListener(this::moreClick);

        adapter.setOnTypeClickListener(index -> {
            if (index != currentType) {
                currentType = index;
                attemptRefresh();
            }
        });

        adapter.setSearchListener(new StoryRecyclerViewAdapter.SearchListener() {
            @Override
            public void onQueryTextSubmit(String query, boolean relevance, String age) {
                search(query, relevance, age);

            }

            @Override
            public void onSearchStatusChanged() {
                updateSearchStatus();
            }
        });

        adapter.setOnLongClickListener(new StoryRecyclerViewAdapter.LongClickCoordinateListener() {
            @Override
            public boolean onLongClick(View v, int position, int x, int y) {
                Context context = v.getContext();

                PopupMenu popupMenu = new PopupMenu(context, v);

                Story story = stories.get(position);
                boolean oldClicked = story.clicked;

                popupMenu.getMenu().add(oldClicked ? "Mark as unread" : "Mark as read").setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        story.clicked = !oldClicked;
                        if (oldClicked) {
                            clickedIds.remove(story.id);
                        } else {
                            clickedIds.add(story.id);
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

                    int targetX = x - Utils.pxFromDpInt(getResources(), 56);
                    int targetY = y - topInset - Utils.pxFromDpInt(getResources(), 20);

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
        hideJobs = SettingsUtils.shouldHideJobs(getContext());
        hideClicked = SettingsUtils.shouldHideClicked(getContext());
        alwaysOpenComments = SettingsUtils.shouldAlwaysOpenComments(getContext());

        long timeDiff = System.currentTimeMillis() - lastLoaded;

        // if more than 1 hr
        if (timeDiff > 1000*60*60 && !adapter.searching && currentType != getBookmarksIndex() && !currentTypeIsAlgolia()) {
            showUpdateButton();
        }

        if (adapter.showPoints != SettingsUtils.shouldShowPoints(getContext())) {
            adapter.showPoints = !adapter.showPoints;
            adapter.notifyItemRangeChanged(1, stories.size());
        }

        if (adapter.compactView != SettingsUtils.shouldUseCompactView(getContext())) {
            adapter.compactView = !adapter.compactView;
            adapter.notifyItemRangeChanged(1, stories.size());
        }

        if (adapter.thumbnails != SettingsUtils.shouldShowThumbnails(getContext())) {
            adapter.thumbnails = !adapter.thumbnails;
            adapter.notifyItemRangeChanged(1, stories.size());
        }

        if (adapter.showIndex != SettingsUtils.shouldShowIndex(getContext())) {
            adapter.showIndex = !adapter.showIndex;
            adapter.notifyItemRangeChanged(1, stories.size());
        }

        if (adapter.leftAlign != SettingsUtils.shouldUseLeftAlign(getContext())) {
            adapter.leftAlign = !adapter.leftAlign;
            setupAdapter();
            recyclerView.setAdapter(adapter);
        }

        if (TextUtils.isEmpty(FontUtils.font) || !FontUtils.font.equals(SettingsUtils.getPreferredFont(getContext()))) {
            FontUtils.init(getContext());
            adapter.notifyItemRangeChanged(0, stories.size());
        }

        if (adapter.compactHeader != SettingsUtils.shouldUseCompactHeader(getContext())) {
            adapter.compactHeader = SettingsUtils.shouldUseCompactHeader(getContext());
            adapter.notifyItemChanged(0);
        }

        if (adapter.hotness != SettingsUtils.getPreferredHotness(getContext())) {
            adapter.hotness = SettingsUtils.getPreferredHotness(getContext());
            adapter.notifyItemRangeChanged(1, stories.size());
        }

        if (adapter.hideJobs != SettingsUtils.shouldHideJobs(getContext())) {
            adapter.type = 0;
            currentType = 0;
            adapter.hideJobs = !adapter.hideJobs;
            adapter.notifyItemChanged(0);
            attemptRefresh();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        SettingsUtils.saveIntSetToSharedPreferences(getContext(), Utils.KEY_SHARED_PREFERENCES_CLICKED_IDS, clickedIds);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (queue != null) {
            queue.cancelAll(request -> true);
            queue.stop();
        }
    }

    private void clickedComments(int position) {
        //prevent double clicks
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
            clickedIds.add(story.id);

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
                    try {
                        int index = stories.indexOf(story);

                        if (!JSONParser.updateStoryWithHNJson(response, story)) {
                            stories.remove(story);
                            adapter.notifyItemRemoved(index);
                            loadedTo = Math.max(0, loadedTo - 1);
                            return;
                        }

                        //lets check if we should remove the post because of filter
                        for (String phrase : filterWords) {
                            if (story.title.toLowerCase().contains(phrase.toLowerCase())) {
                                stories.remove(story);
                                adapter.notifyItemRemoved(index);
                                loadedTo = Math.max(0, loadedTo - 1);
                                return;
                            }
                        }

                        //or because it's a job
                        if (hideJobs && (story.isJob || story.by.equals("whoishiring"))) {
                            stories.remove(story);
                            adapter.notifyItemRemoved(index);
                            loadedTo = Math.max(0, loadedTo - 1);
                            return;
                        }

                        adapter.notifyItemChanged(index);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Utils.log("Failed to load story with id: " + story.id);
                        adapter.notifyDataSetChanged();
                    }
                }, error -> {
            error.printStackTrace();
            story.loadingFailed = true;
            adapter.notifyItemChanged(stories.indexOf(story));
            loadStory(story, attempt + 1);
        });

        queue.add(stringRequest);
    }

    public void moreClick(View view) {
        PopupMenu popup = new PopupMenu(requireActivity(), view);
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.menu_settings) {
                    requireActivity().startActivity(new Intent(requireActivity(), SettingsActivity.class));
                } else if (item.getItemId() == R.id.menu_login) {
                    if (TextUtils.isEmpty(AccountUtils.getAccountUsername(requireActivity()))) {
                        AccountUtils.showLoginPrompt(requireActivity().getSupportFragmentManager());
                    } else {
                        AccountUtils.deleteAccountDetails(requireActivity());
                        Toast.makeText(getContext(), "Logged out", Toast.LENGTH_SHORT).show();
                    }
                } else if (item.getItemId() == R.id.menu_profile) {
                    UserDialogFragment.showUserDialog(requireActivity().getSupportFragmentManager(), AccountUtils.getAccountUsername(requireActivity()));
                }
                return true;
            }
        });
        popup.getMenuInflater().inflate(R.menu.main_menu, popup.getMenu());

        if (TextUtils.isEmpty(AccountUtils.getAccountUsername(requireActivity()))) {
            popup.getMenu().getItem(0).setTitle("Log in");
            popup.getMenu().getItem(1).setVisible(false);
        } else {
            popup.getMenu().getItem(0).setTitle("Log out");
            popup.getMenu().getItem(1).setVisible(true);
        }

        popup.show();
    }

    public void attemptRefresh() {
        hideUpdateButton();
        if (adapter.searching) {
            search(lastSearch, lastSearchRelevance, lastSearchAge);
            return;
        }

        swipeRefreshLayout.setRefreshing(true);

        //cancel all ongoing
        queue.cancelAll(request -> true);

        adapter.type = currentType;

        if (currentTypeIsAlgolia()) {
            //algoliaStuff
            int currentTime = (int) (System.currentTimeMillis() / 1000);
            int startTime = currentTime;
            if (currentType == 1) {
                startTime = currentTime - 60*60*24;
            } else if (currentType == 2) {
                startTime = currentTime - 60*60*48;
            } else if (currentType == 3) {
                startTime = currentTime - 60*60*24*7;
            }

            loadTopStoriesSince(startTime);

            return;
        }

        lastLoaded = System.currentTimeMillis();

        if (currentType == getBookmarksIndex()) {
            //lets load bookmarks instead - or rather add empty stories with correct id:s and start loading them
            adapter.notifyItemRangeRemoved(1, stories.size() + 1);
            loadedTo = 0;

            stories.clear();
            stories.add(new Story());

            ArrayList<Bookmark> bookmarks = Utils.loadBookmarks(getContext(), true);

            for (int i = 0; i < bookmarks.size(); i++) {
                Story s = new Story("Loading...", bookmarks.get(i).id, false, false);

                stories.add(s);
                adapter.notifyItemInserted(i + 1);
                if (i < 20) {
                    loadStory(stories.get(i + 1), 0);
                }
            }

            adapter.notifyItemChanged(0);
            swipeRefreshLayout.setRefreshing(false);

            return;
        }

        // if none of the above, do a normal loading
        StringRequest stringRequest = new StringRequest(Request.Method.GET, hnUrls[currentType == 0 ? 0 : currentType - 3],
                response -> {
                    swipeRefreshLayout.setRefreshing(false);
                    try {
                        JSONArray jsonArray = new JSONArray(response);

                        loadedTo = 0;

                        adapter.notifyItemRangeRemoved(1, stories.size() + 1);

                        stories.clear();
                        stories.add(new Story());

                        for (int i = 0; i < jsonArray.length(); i++) {
                            int id = Integer.parseInt(jsonArray.get(i).toString());
                            if (hideClicked && clickedIds.contains(id)) {
                                continue;
                            }

                            Story s = new Story("Loading...", id, false, clickedIds.contains(id));
                            //let's try to fill this with old information if possible

                            String cachedResponse = Utils.loadCachedStory(getContext(), id);
                            if (cachedResponse != null && !cachedResponse.equals(JSONParser.ALGOLIA_ERROR_STRING)) {
                                JSONParser.updateStoryWithAlgoliaResponse(s, cachedResponse);
                            }

                            stories.add(s);
                            adapter.notifyItemInserted(1 + i);
                        }

                        adapter.loadingFailed = false;
                        adapter.notifyItemChanged(0);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }, error -> {
            swipeRefreshLayout.setRefreshing(false);
            adapter.loadingFailed = true;
            adapter.notifyItemChanged(0);
        });

        queue.add(stringRequest);
    }

    private void updateSearchStatus() {
        hideUpdateButton();
        adapter.notifyItemChanged(0);

        if (getActivity() != null && getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).backPressedCallback.setEnabled(adapter.searching);
        }

        swipeRefreshLayout.setEnabled(!adapter.searching);

        if (adapter.searching) {
            //cancel all ongoing
            queue.cancelAll(request -> true);
            swipeRefreshLayout.setRefreshing(false);

            adapter.notifyItemRangeRemoved(1, stories.size() + 1);
            stories.clear();
            stories.add(new Story());
        } else {
            int size = stories.size();
            if (size > 1) {
                stories.subList(1, size).clear();
            }
            adapter.notifyItemRangeRemoved(1, size + 1);

            attemptRefresh();
        }
    }

    private void loadTopStoriesSince(int start_i) {
        loadAlgolia("https://hn.algolia.com/api/v1/search?tags=story&numericFilters=created_at_i>" + start_i + "&hitsPerPage=200", true);
    }

    private void search(String query, boolean relevance, String age) {
        lastSearch = query;
        adapter.lastSearch = query;
        lastSearchRelevance = relevance;
        lastSearchAge = age;

        loadAlgolia("https://hn.algolia.com/api/v1/" + (relevance ? "search" : "search_by_date") + "?query=" + query + "&tags=story&hitsPerPage=200", false);
    }

    private void loadAlgolia(String url, boolean markClicked) {
        swipeRefreshLayout.setEnabled(true);
        swipeRefreshLayout.setRefreshing(true);
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    swipeRefreshLayout.setRefreshing(false);
                    try {
                        int oldSize = stories.size();

                        stories.clear();
                        stories.add(new Story());

                        adapter.notifyItemRangeRemoved(1, oldSize + 1);

                        stories.addAll(JSONParser.algoliaJsonToStories(response));

                        Iterator<Story> iterator = stories.iterator();
                        while (iterator.hasNext()) {
                            Story story = iterator.next();
                            story.clicked = clickedIds.contains(story.id);

                            if (hideClicked && story.clicked) {
                                iterator.remove();
                            }
                        }

                        adapter.loadingFailed = false;

                        adapter.notifyItemRangeInserted(1, stories.size());
                        adapter.notifyItemChanged(0);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                }, error -> {
            error.printStackTrace();
            swipeRefreshLayout.setRefreshing(false);
            adapter.loadingFailed = true;
            adapter.notifyItemChanged(0);
        });

        queue.add(stringRequest);
    }

    public boolean currentTypeIsAlgolia() {
        return 0 < currentType && 4 > currentType;
    }

    public int getBookmarksIndex() {
        //works as long as bookmarks is last option
        return getResources().getStringArray(R.array.sorting_options).length - (hideJobs ? 2 : 1);
    }

    public boolean exitSearch() {
        if (adapter.searching) {
            adapter.searching = false;
            adapter.lastSearch = "";
            updateSearchStatus();
            return true;
        }
        return false;
    }

    private void hideUpdateButton() {
        if (updateContainer.getVisibility() == View.VISIBLE) {

            float endYPosition = getResources().getDisplayMetrics().heightPixels - updateContainer.getY() + updateContainer.getHeight() + ViewUtils.getNavigationBarHeight(getResources());
            PathInterpolator pathInterpolator = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

            ObjectAnimator yAnimator = ObjectAnimator.ofFloat(updateContainer, "translationY", endYPosition);
            yAnimator.setDuration(200);

            yAnimator.setInterpolator(pathInterpolator);

            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(updateContainer, "alpha", 1.0f, 0.0f);
            alphaAnimator.setDuration(300);
            alphaAnimator.setInterpolator(pathInterpolator);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(yAnimator, alphaAnimator);

            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    updateContainer.setVisibility(View.GONE);
                    updateContainer.setTranslationY(0);
                    updateContainer.setAlpha(1f);
                }
            });

            animatorSet.start();
        }
    }

    private void showUpdateButton() {
        if (updateContainer.getVisibility() != View.VISIBLE) {
            updateContainer.setVisibility(View.VISIBLE);

            AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
            anim.setDuration(300);
            anim.setRepeatMode(Animation.REVERSE);
            updateContainer.startAnimation(anim);
        }
    }

    private void openComments(Story story, int pos, boolean showWebsite) {
        storyClickListener.openStory(story, pos, showWebsite);
    }

    public interface StoryClickListener {
        void openStory(Story story, int pos, boolean showWebsite);
    }
}
