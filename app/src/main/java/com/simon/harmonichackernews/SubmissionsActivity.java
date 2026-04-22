package com.simon.harmonichackernews;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.adapters.StoryRecyclerViewAdapter;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.BackgroundJSONParser;
import com.simon.harmonichackernews.network.JSONParser;
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
    private boolean initialLoadFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false);

        setContentView(R.layout.activity_submissions);
        swipeRefreshLayout = findViewById(R.id.submissions_swiperefreshlayout);
        initialLoadingIndicator = findViewById(R.id.submissions_initial_loading);

        swipeRefreshLayout.setOnRefreshListener(this::loadSubmissions);
        ViewUtils.setUpSwipeRefreshWithStatusBarOffset(swipeRefreshLayout);

        RecyclerView recyclerView = findViewById(R.id.submissions_recyclerview);

        submissions = new ArrayList<>();
        // header
        submissions.add(new Story());

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
                getIntent().getStringExtra(KEY_USER),
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

        loadSubmissions();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (Utils.isTablet(getResources())) {
            int sideMargin = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);
            swipeRefreshLayout.setPadding(sideMargin, 0, sideMargin, 0);
        }
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
        String url = "https://hn.algolia.com/api/v1/search_by_date?tags=author_" + getIntent().getStringExtra(KEY_USER) + "&hitsPerPage=999";

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    // Parse JSON on background thread
                    BackgroundJSONParser.parseAlgoliaJson(response, new BackgroundJSONParser.AlgoliaParseCallback() {
                        @Override
                        public void onParseSuccess(List<Story> parsedStories) {
                            finishLoading();

                            int oldSize = submissions.size();

                            submissions.clear();
                            submissions.add(new Story());

                            adapter.notifyItemRangeRemoved(1, oldSize + 1);

                            submissions.addAll(parsedStories);

                            adapter.notifyItemRangeInserted(1, submissions.size());
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
