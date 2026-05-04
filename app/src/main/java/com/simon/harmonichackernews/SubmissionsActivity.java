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

    private StoryRecyclerViewAdapter adapter;
    private ArrayList<Story> submissions;
    private LinearLayoutManager linearLayoutManager;
    private RequestQueue queue;
    private SwipeRefreshLayout swipeRefreshLayout;
    private View initialLoadingIndicator;
    private AppBarLayout appBarLayout;
    private TextView headerText;
    private boolean initialLoadFinished = false;
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

        swipeRefreshLayout.setOnRefreshListener(this::loadSubmissions);
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
                false,
                SettingsUtils.shouldUseCompactHeader(this),
                SettingsUtils.shouldUseLeftAlign(this),
                SettingsUtils.getPreferredHotness(this),
                SettingsUtils.getPreferredFaviconProvider(this),
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

        recyclerView.setAdapter(adapter);
        setUpWindowInsets(recyclerView);

        loadSubmissions();
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
        int bottomPadding = Utils.pxFromDpInt(getResources(), compactHeader ? 10 : 26);
        headerText.setPaddingRelative(
                headerText.getPaddingStart(),
                topPadding,
                headerText.getPaddingEnd(),
                bottomPadding
        );
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

    private void loadSubmissions() {
        boolean showInitialLoading = !initialLoadFinished;
        swipeRefreshLayout.setRefreshing(!showInitialLoading);
        initialLoadingIndicator.setVisibility(showInitialLoading ? View.VISIBLE : View.GONE);
        String url = Uri.parse("https://hn.algolia.com/api/v1/search_by_date")
                .buildUpon()
                .appendQueryParameter("tags", "author_" + getIntent().getStringExtra(KEY_USER))
                .appendQueryParameter("hitsPerPage", "999")
                .build()
                .toString();

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    // Parse JSON on background thread
                    BackgroundJSONParser.parseAlgoliaJson(response, new BackgroundJSONParser.AlgoliaParseCallback() {
                        @Override
                        public void onParseSuccess(List<Story> parsedStories) {
                            finishLoading();

                            int oldSize = submissions.size();

                            submissions.clear();

                            if (oldSize > 0) {
                                adapter.notifyItemRangeRemoved(0, oldSize);
                            }

                            submissions.addAll(parsedStories);

                            adapter.notifyItemRangeInserted(0, submissions.size());
                        }

                        @Override
                        public void onParseError(JSONException error) {
                            finishLoading();
                            error.printStackTrace();
                        }
                    });

                }, error -> {
            error.printStackTrace();
            finishLoading();
        });

        queue.add(stringRequest);
    }

    private void finishLoading() {
        initialLoadFinished = true;
        swipeRefreshLayout.setRefreshing(false);
        initialLoadingIndicator.setVisibility(View.GONE);
    }
}
