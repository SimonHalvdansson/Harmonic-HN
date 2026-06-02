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
import com.simon.harmonichackernews.network.BackgroundJSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

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
    private SwipeRefreshLayout swipeRefreshLayout;
    private View initialLoadingIndicator;
    private AppBarLayout appBarLayout;
    private TextView headerText;
    private MaterialButtonToggleGroup filterGroup;
    private MaterialButtonToggleGroup.OnButtonCheckedListener filterCheckedListener;
    private boolean initialLoadFinished = false;
    private boolean submissionsLoading = false;
    private int submissionsRequestGeneration = 0;
    private int submissionsHitsPerPage = ALGOLIA_HITS_INCREMENT;
    private boolean submissionsCanLoadMore = false;
    private int submissionFilter = SUBMISSION_FILTER_BOTH;
    private int topInset = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false);

        setContentView(R.layout.activity_submissions);
        swipeRefreshLayout = findViewById(R.id.submissions_swiperefreshlayout);
        initialLoadingIndicator = findViewById(R.id.submissions_initial_loading);
        appBarLayout = findViewById(R.id.submissions_appbar);
        headerText = findViewById(R.id.submissions_header_text);
        filterGroup = findViewById(R.id.submissions_header_filter_group);

        String userName = getIntent().getStringExtra(KEY_USER);
        headerText.setText(userName + "'s submissions");
        headerText.setContentDescription("Submissions by " + userName);
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

        RecyclerView recyclerView = findViewById(R.id.submissions_recyclerview);

        submissions = new ArrayList<>();

        queue = NetworkComponent.getRequestQueueInstance(this);

        linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);

        adapter = new StoryRecyclerViewAdapter(submissions,
                SettingsUtils.shouldShowPoints(this),
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
                SettingsUtils.shouldGrayOutClicked(this),
                SettingsUtils.getPreferredHotness(this),
                SettingsUtils.getPreferredFaviconProvider(this),
                SettingsUtils.getPreferredFont(this),
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
                Story story = submissions.get(position);

                Intent intent = new Intent(getApplicationContext(), CommentsActivity.class);
                intent.putExtra(CommentsFragment.EXTRA_ID, story.commentMasterId);
                intent.putExtra(CommentsFragment.EXTRA_TITLE, story.commentMasterTitle);
                intent.putExtra(CommentsFragment.EXTRA_URL, story.commentMasterUrl);

                startActivity(intent);

                if (!SettingsUtils.shouldDisableCommentsSwipeBack(getApplicationContext()) && !Utils.isTablet(getResources())) {
                    overridePendingTransition(R.anim.activity_in_animation, 0);
                }
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
        if (filterGroup != null && filterCheckedListener != null) {
            filterGroup.removeOnButtonCheckedListener(filterCheckedListener);
        }

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (Utils.isTablet(getResources())) {
            int sideMargin = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);
            swipeRefreshLayout.setPadding(sideMargin, 0, sideMargin, 0);
            appBarLayout.setPadding(sideMargin, 0, sideMargin, 0);
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
                return true;
            }
        });
    }

    private void setUpWindowInsets(RecyclerView recyclerView) {
        View root = findViewById(R.id.submissions_root);
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
        if (resetResultLimit) {
            submissionsHitsPerPage = ALGOLIA_HITS_INCREMENT;
        }
        int requestGeneration = ++submissionsRequestGeneration;
        submissionsLoading = true;
        boolean showInitialLoading = !initialLoadFinished;
        swipeRefreshLayout.setRefreshing(!showInitialLoading && resetResultLimit);
        initialLoadingIndicator.setVisibility(showInitialLoading ? View.VISIBLE : View.GONE);
        String url = Uri.parse("https://hn.algolia.com/api/v1/search_by_date")
                .buildUpon()
                .appendQueryParameter("tags", "author_" + getIntent().getStringExtra(KEY_USER))
                .appendQueryParameter("hitsPerPage", String.valueOf(submissionsHitsPerPage))
                .build()
                .toString();

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    // Parse JSON on background thread
                    BackgroundJSONParser.parseAlgoliaJson(response, new BackgroundJSONParser.AlgoliaParseCallback() {
                        @Override
                        public void onParseSuccess(List<Story> parsedStories) {
                            if (requestGeneration != submissionsRequestGeneration) {
                                return;
                            }

                            finishLoading(requestGeneration);

                            submissionsCanLoadMore = parsedStories.size() >= submissionsHitsPerPage;
                            allSubmissions.clear();
                            allSubmissions.addAll(parsedStories);
                            applySubmissionFilter();
                        }

                        @Override
                        public void onParseError(JSONException error) {
                            finishLoading(requestGeneration);
                            error.printStackTrace();
                        }
                    });

                }, error -> {
            error.printStackTrace();
            finishLoading(requestGeneration);
        });

        queue.add(stringRequest);
    }

    private void finishLoading(int requestGeneration) {
        if (requestGeneration != submissionsRequestGeneration) {
            return;
        }

        submissionsLoading = false;
        initialLoadFinished = true;
        swipeRefreshLayout.setRefreshing(false);
        initialLoadingIndicator.setVisibility(View.GONE);
    }
}
