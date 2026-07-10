package com.simon.harmonichackernews;

import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.simon.harmonichackernews.adapters.StoryRecyclerViewAdapter;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.databinding.ActivitySubmissionsBinding;
import com.simon.harmonichackernews.network.BackgroundJSONParser;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StatusBarProtectionUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class SubmissionsActivity extends AppCompatActivity {

    public final static String KEY_USER = "KEY_USER";
    private static final int SUBMISSION_FILTER_STORIES = 0;
    private static final int SUBMISSION_FILTER_BOTH = 1;
    private static final int SUBMISSION_FILTER_COMMENTS = 2;
    private static final int ALGOLIA_HITS_INCREMENT = 200;

    private StoryRecyclerViewAdapter adapter;
    private ArrayList<Story> submissions;
    private final ArrayList<Story> allSubmissions = new ArrayList<>();
    private LinearLayoutManager linearLayoutManager;
    private RequestQueue queue;
    private final Object requestTag = new Object();
    private Future<?> submissionsParseTask;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View initialLoadingIndicator;
    private AppBarLayout appBarLayout;
    private TextView headerText;
    private MaterialButtonToggleGroup filterGroup;
    private MaterialButtonToggleGroup.OnButtonCheckedListener filterCheckedListener;
    private ActivitySubmissionsBinding binding;
    private boolean initialLoadFinished = false;
    private boolean submissionsLoading = false;
    private boolean submissionsLoadedSuccessfully = false;
    private int submissionsRequestGeneration = 0;
    private int submissionsHitsPerPage = ALGOLIA_HITS_INCREMENT;
    private boolean submissionsCanLoadMore = false;
    private int submissionFilter = SUBMISSION_FILTER_BOTH;
    private int topInset = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false);

        binding = ActivitySubmissionsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        applyStatusBarProtection();
        swipeRefreshLayout = binding.submissionsSwiperefreshlayout;
        initialLoadingIndicator = binding.submissionsInitialLoading;
        appBarLayout = binding.submissionsAppbar;
        headerText = binding.submissionsHeaderContainer.submissionsHeaderText;
        filterGroup = binding.submissionsHeaderContainer.submissionsHeaderFilterGroup;

        String userName = getIntent().getStringExtra(KEY_USER);
        headerText.setText(userName + "'s submissions");
        headerText.setContentDescription("Submissions by " + userName);
        applyPreferredHeaderTypeface();
        ViewCompat.setAccessibilityHeading(headerText, true);

        appBarLayout.addOnOffsetChangedListener((appBar, verticalOffset) -> {
            float totalScrollRange = appBar.getTotalScrollRange();
            if (totalScrollRange > 0) {
                headerText.setAlpha(1f - (Math.abs(verticalOffset) / totalScrollRange));
            }
        });
        configureAppBarDragBehavior();

        filterCheckedListener = (group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            int newFilter = submissionFilterFromButtonId(checkedId);
            if (newFilter == submissionFilter) {
                return;
            }

            submissionFilter = newFilter;
            applySubmissionFilter();
        };
        filterGroup.addOnButtonCheckedListener(filterCheckedListener);

        swipeRefreshLayout.setOnRefreshListener(() -> loadSubmissions(true));
        ViewUtils.setUpSwipeRefreshWithStatusBarOffset(swipeRefreshLayout);

        RecyclerView recyclerView = binding.submissionsRecyclerview;

        submissions = new ArrayList<>();

        queue = NetworkComponent.getRequestQueueInstance(this);

        linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);

        adapter = new StoryRecyclerViewAdapter(submissions,
                SettingsUtils.shouldShowPoints(this),
                SettingsUtils.shouldUseCompactPoints(this),
                SettingsUtils.shouldIncludeTopLevelDomain(this),
                SettingsUtils.shouldShowCommentsCount(this),
                SettingsUtils.shouldUseCompactView(this),
                SettingsUtils.shouldShowThumbnails(this),
                SettingsUtils.getPreferredStoryPreviewImageMode(this),
                SettingsUtils.getPreferredStoryTextSize(this),
                false,
                SettingsUtils.shouldUseCompactHeader(this),
                SettingsUtils.shouldUseLeftAlign(this),
                SettingsUtils.shouldUseCardStoryDisplayStyle(this),
                SettingsUtils.shouldTintCardUsingPreview(this),
                SettingsUtils.getPreferredPaletteTintConfigKey(this),
                SettingsUtils.shouldGrayOutClicked(this),
                SettingsUtils.getPreferredHotness(this),
                SettingsUtils.getPreferredFaviconProvider(this),
                SettingsUtils.getPreferredFont(this),
                SettingsUtils.getPreferredCommentTextSize(this),
                userName,
                -1);

        adapter.setOnCommentClickListener(new StoryRecyclerViewAdapter.ClickListener() {
            @Override
            public void onItemClick(int position) {
                openComments(submissions.get(position), false);
            }
        });

        adapter.setOnLinkClickListener(new StoryRecyclerViewAdapter.ClickListener() {
            @Override
            public void onItemClick(int position) {
                Story story = submissions.get(position);

                if (story.isLink) {
                    if (SettingsUtils.shouldUseIntegratedWebView(getApplicationContext())) {
                        openComments(story, true);
                    } else {
                        Utils.launchCustomTab(SubmissionsActivity.this, story.url);
                    }
                } else {
                    openComments(story, false);
                }
            }
        });

        adapter.setOnCommentStoryClickListener(new StoryRecyclerViewAdapter.ClickListener() {
            @Override
            public void onItemClick(int position) {
                openCommentMasterStory(submissions.get(position));
            }
        });

        adapter.setOnCommentRepliesClickListener(new StoryRecyclerViewAdapter.ClickListener() {
            @Override
            public void onItemClick(int position) {
                openComments(submissions.get(position), false);
            }
        });

        adapter.setOnLoadMoreClickListener(v -> {
            if (submissionsLoading || !submissionsCanLoadMore) {
                return;
            }

            submissionsHitsPerPage += ALGOLIA_HITS_INCREMENT;
            loadSubmissions(false);
        });

        recyclerView.setAdapter(adapter);
        setUpWindowInsets(recyclerView);

        loadSubmissions(true);
    }

    @Override
    protected void onDestroy() {
        submissionsRequestGeneration++;
        cancelSubmissionsParseTask();
        if (queue != null) {
            queue.cancelAll(requestTag);
        }
        if (filterGroup != null && filterCheckedListener != null) {
            filterGroup.removeOnButtonCheckedListener(filterCheckedListener);
        }
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(null);
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyStatusBarProtection();
        syncCompactPointsPreference();
        syncPaletteTintPreference();
        syncFontPreference();
        syncCommentDisplayPreferences();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int sideMargin = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);
        swipeRefreshLayout.setPadding(sideMargin, 0, sideMargin, 0);
        appBarLayout.setPadding(sideMargin, 0, sideMargin, 0);
        applyHeaderPadding();
        ViewCompat.requestApplyInsets(binding.submissionsRoot);
    }

    private void syncCompactPointsPreference() {
        if (adapter == null) {
            return;
        }

        boolean compactPoints = SettingsUtils.shouldUseCompactPoints(this);
        if (adapter.compactPoints != compactPoints) {
            adapter.compactPoints = compactPoints;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }

        boolean includeTopLevelDomain = SettingsUtils.shouldIncludeTopLevelDomain(this);
        if (adapter.includeTopLevelDomain != includeTopLevelDomain) {
            adapter.includeTopLevelDomain = includeTopLevelDomain;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    private void applyStatusBarProtection() {
        StatusBarProtectionUtils.setTopProtection(
                binding.submissionsStatusBarProtection,
                StatusBarProtectionUtils.getPaneBackgroundColor(this));
    }

    private void syncPaletteTintPreference() {
        if (adapter == null) {
            return;
        }

        String paletteTintMode = SettingsUtils.getPreferredPaletteTintConfigKey(this);
        if (!paletteTintMode.equals(adapter.paletteTintMode)) {
            adapter.paletteTintMode = paletteTintMode;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    private void syncFontPreference() {
        String preferredFont = SettingsUtils.getPreferredFont(this);
        if (FontUtils.font == null || !FontUtils.font.equals(preferredFont)) {
            FontUtils.init(this);
        }
        applyPreferredHeaderTypeface();

        if (adapter == null) {
            return;
        }

        if (!preferredFont.equals(adapter.font)) {
            adapter.font = preferredFont;
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    private void syncCommentDisplayPreferences() {
        if (adapter == null) {
            return;
        }

        boolean changed = false;
        float commentTextSize = SettingsUtils.getPreferredCommentTextSize(this);
        if (Float.compare(adapter.commentTextSize, commentTextSize) != 0) {
            adapter.commentTextSize = SettingsUtils.clampCommentTextSize(commentTextSize);
            changed = true;
        }

        if (changed) {
            adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        }
    }

    private void applyPreferredHeaderTypeface() {
        String preferredFont = SettingsUtils.getPreferredFont(this);
        if (FontUtils.font == null || !FontUtils.font.equals(preferredFont)) {
            FontUtils.init(this);
        }
        FontUtils.setTypeface(headerText, true, 26);
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
                return true;
            }
        });
    }

    private void setUpWindowInsets(RecyclerView recyclerView) {
        View root = binding.submissionsRoot;
        final int rootPaddingLeft = root.getPaddingLeft();
        final int rootPaddingTop = root.getPaddingTop();
        final int rootPaddingRight = root.getPaddingRight();
        final int rootPaddingBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets cutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());

            v.setPadding(
                    rootPaddingLeft + Math.max(insets.left, cutoutInsets.left),
                    rootPaddingTop,
                    rootPaddingRight + Math.max(insets.right, cutoutInsets.right),
                    rootPaddingBottom
            );

            topInset = insets.top;
            applyHeaderPadding();

            recyclerView.setPadding(
                    recyclerView.getPaddingLeft(),
                    recyclerView.getPaddingTop(),
                    recyclerView.getPaddingRight(),
                    insets.bottom
            );

            return windowInsets;
        });
        ViewUtils.requestApplyInsetsWhenAttached(root);
    }

    private void applyHeaderPadding() {
        boolean compactHeader = SettingsUtils.shouldUseCompactHeader(this);
        int topPadding = topInset + Utils.pxFromDpInt(getResources(), compactHeader ? 20 : 40);
        int bottomPadding = Utils.pxFromDpInt(getResources(), compactHeader ? 8 : 16);
        headerText.setPaddingRelative(
                headerText.getPaddingStart(),
                topPadding,
                headerText.getPaddingEnd(),
                bottomPadding
        );
    }

    private int submissionFilterFromButtonId(int buttonId) {
        if (buttonId == R.id.submissions_header_filter_stories) {
            return SUBMISSION_FILTER_STORIES;
        }
        if (buttonId == R.id.submissions_header_filter_comments) {
            return SUBMISSION_FILTER_COMMENTS;
        }
        return SUBMISSION_FILTER_BOTH;
    }

    private void applySubmissionFilter() {
        int oldItemCount = adapter.getItemCount();
        submissions.clear();
        adapter.showLoadMoreButton = submissionsCanLoadMore;

        for (Story story : allSubmissions) {
            if (shouldShowStoryForSubmissionFilter(story)) {
                submissions.add(story);
            }
        }

        int newItemCount = adapter.getItemCount();
        if (oldItemCount == 0 && newItemCount > 0) {
            adapter.notifyItemRangeInserted(0, newItemCount);
        } else if (newItemCount == 0 && oldItemCount > 0) {
            adapter.notifyItemRangeRemoved(0, oldItemCount);
        } else {
            adapter.notifyDataSetChanged();
        }

        filterGroup.setVisibility(allSubmissions.isEmpty() ? View.GONE : View.VISIBLE);
        updateEmptyView();
    }

    private boolean shouldShowStoryForSubmissionFilter(Story story) {
        if (submissionFilter == SUBMISSION_FILTER_STORIES) {
            return !story.isComment;
        }
        if (submissionFilter == SUBMISSION_FILTER_COMMENTS) {
            return story.isComment;
        }
        return true;
    }

    private void openCommentMasterStory(Story story) {
        Story masterStory = story.toCommentMasterStory();
        if (masterStory == null) {
            openComments(story, false);
            return;
        }

        if (masterStory.loaded) {
            openComments(masterStory, false);
            return;
        }

        String url = "https://hacker-news.firebaseio.com/v0/item/" + masterStory.id + ".json";
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONParser.updateCommentMasterStoryWithHNJson(story, response);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }

                    int index = submissions.indexOf(story);
                    if (index >= 0) {
                        adapter.notifyItemChanged(index);
                    }

                    Story refreshedMasterStory = story.toCommentMasterStory();
                    openComments(refreshedMasterStory != null ? refreshedMasterStory : masterStory, false);
                }, error -> openComments(masterStory, false));

        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private void openComments(Story story, boolean showWebsite) {
        Bundle bundle = story.toBundle();
        bundle.putBoolean(CommentsFragment.EXTRA_SHOW_WEBSITE, showWebsite);

        Intent intent = new Intent(SubmissionsActivity.this, CommentsActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);

        if (!SettingsUtils.shouldDisableCommentsSwipeBack(getApplicationContext()) && !Utils.isTablet(getResources())) {
            overridePendingTransition(R.anim.activity_in_animation, 0);
        }
    }

    private void loadSubmissions(boolean resetResultLimit) {
        cancelSubmissionsParseTask();
        if (resetResultLimit) {
            submissionsHitsPerPage = ALGOLIA_HITS_INCREMENT;
        }
        int requestGeneration = ++submissionsRequestGeneration;
        submissionsLoading = true;
        boolean showInitialLoading = !initialLoadFinished;
        swipeRefreshLayout.setRefreshing(!showInitialLoading && resetResultLimit);
        initialLoadingIndicator.setVisibility(showInitialLoading ? View.VISIBLE : View.GONE);
        updateEmptyView();
        String url = Uri.parse("https://hn.algolia.com/api/v1/search_by_date")
                .buildUpon()
                .appendQueryParameter("tags", "author_" + getIntent().getStringExtra(KEY_USER))
                .appendQueryParameter("hitsPerPage", String.valueOf(submissionsHitsPerPage))
                .build()
                .toString();

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    // Parse JSON on background thread
                    submissionsParseTask = BackgroundJSONParser.parseAlgoliaJson(response, new BackgroundJSONParser.AlgoliaParseCallback() {
                        @Override
                        public void onParseSuccess(List<Story> parsedStories) {
                            if (requestGeneration != submissionsRequestGeneration) {
                                return;
                            }
                            submissionsParseTask = null;

                            finishLoading(requestGeneration);

                            submissionsCanLoadMore = parsedStories.size() >= submissionsHitsPerPage;
                            submissionsLoadedSuccessfully = true;
                            allSubmissions.clear();
                            allSubmissions.addAll(parsedStories);
                            applySubmissionFilter();
                        }

                        @Override
                        public void onParseError(JSONException error) {
                            if (requestGeneration != submissionsRequestGeneration) {
                                return;
                            }
                            submissionsParseTask = null;
                            finishLoading(requestGeneration);
                            error.printStackTrace();
                        }
                    });

                }, error -> {
            error.printStackTrace();
            finishLoading(requestGeneration);
        });

        stringRequest.setTag(requestTag);
        queue.add(stringRequest);
    }

    private void cancelSubmissionsParseTask() {
        if (submissionsParseTask != null) {
            submissionsParseTask.cancel(true);
            submissionsParseTask = null;
        }
    }

    private void finishLoading(int requestGeneration) {
        if (requestGeneration != submissionsRequestGeneration) {
            return;
        }

        submissionsLoading = false;
        initialLoadFinished = true;
        swipeRefreshLayout.setRefreshing(false);
        initialLoadingIndicator.setVisibility(View.GONE);
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (adapter == null) {
            return;
        }

        boolean showEmpty = submissionsLoadedSuccessfully
                && !submissionsLoading
                && adapter.getItemCount() == 0;

        if (showEmpty) {
            binding.submissionsEmptyText.setText(getEmptyViewText());
        }

        binding.submissionsEmpty.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
    }

    private String getEmptyViewText() {
        if (allSubmissions.isEmpty()) {
            return "No submissions";
        }
        if (submissionFilter == SUBMISSION_FILTER_STORIES) {
            return "No stories";
        }
        if (submissionFilter == SUBMISSION_FILTER_COMMENTS) {
            return "No comments";
        }
        return "No submissions";
    }
}
