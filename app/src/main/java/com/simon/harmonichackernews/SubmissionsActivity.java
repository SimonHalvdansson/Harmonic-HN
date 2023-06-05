package com.simon.harmonichackernews;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.gw.swipeback.SwipeBackLayout;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.VolleyOkHttp3StackInterceptors;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;

import java.util.ArrayList;

public class SubmissionsActivity extends AppCompatActivity {

    public final static String KEY_USER = "KEY_USER";

    private StoryRecyclerViewAdapter adapter;
    private ArrayList<Story> submissions;
    private LinearLayoutManager linearLayoutManager;
    private RequestQueue queue;
    private SwipeRefreshLayout swipeRefreshLayout;

    private int loadedTo = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, true);

        if (Utils.shouldUseTransparentStatusBar(this)) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.statusBarColorTransparent));
        }

        setContentView(R.layout.activity_submissions);

        SwipeBackLayout swipeBackLayout = findViewById(R.id.swipeBackLayout);
        swipeBackLayout.setPadding(0, 0, 0, 0);

        swipeBackLayout.setSwipeBackListener(new SwipeBackLayout.OnSwipeBackListener() {
            @Override
            public void onViewPositionChanged(View mView, float swipeBackFraction, float swipeBackFactor) {
                mView.invalidate();
            }

            @Override
            public void onViewSwipeFinished(View mView, boolean isEnd) {
                if (isEnd) {
                    finish();
                    overridePendingTransition(0, 0);
                }
            }
        });

        swipeRefreshLayout = findViewById(R.id.submissions_swiperefreshlayout);
        swipeRefreshLayout.setBackgroundResource(ThemeUtils.getBackgroundColorResource(this));

        swipeRefreshLayout.setOnRefreshListener(this::loadSubmissions);

        if (Utils.shouldUseTransparentStatusBar(this)) {
            swipeRefreshLayout.setProgressViewOffset(
                    false,
                    swipeRefreshLayout.getProgressViewStartOffset() + Utils.getStatusBarHeight(getResources()),
                    swipeRefreshLayout.getProgressViewEndOffset() + Utils.getStatusBarHeight(getResources()));
        }

        RecyclerView recyclerView = findViewById(R.id.submissions_recyclerview);

        submissions = new ArrayList<>();
        //header
        submissions.add(new Story());

        queue = Volley.newRequestQueue(this, new VolleyOkHttp3StackInterceptors());

        linearLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(linearLayoutManager);

        adapter = new StoryRecyclerViewAdapter(submissions,
                Utils.shouldShowPoints(this),
                Utils.shouldUseCompactView(this),
                Utils.shouldShowThumbnails(this),
                Utils.shouldShowIndex(this),
                Utils.shouldHideJobs(this),
                Utils.shouldUseCompactHeader(this),
                Utils.shouldUseLeftAlign(this),
                Utils.getPreferredHotness(this),
                getIntent().getStringExtra(KEY_USER));

        adapter.setOnRefreshListener(this::loadSubmissions);
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
                    if (Utils.shouldUseIntegratedWebView(getApplicationContext())) {
                        openComments(story, true);
                    } else {
                        Utils.launchCustomTab(getApplicationContext(), story.url);
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

                overridePendingTransition(R.anim.activity_in_animation, 0);
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
        if (Utils.isTablet(this)) {
            int sideMargin = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);
            swipeRefreshLayout.setPadding(sideMargin, Utils.getStatusBarHeight(getResources()), sideMargin, 0);
        }
    }

    private void openComments(Story story, boolean showWebsite) {
        Bundle bundle = story.toBundle();
        bundle.putBoolean(CommentsFragment.EXTRA_SHOW_WEBSITE, showWebsite);

        Intent intent = new Intent(getApplicationContext(), CommentsActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);

        overridePendingTransition(R.anim.activity_in_animation, 0);
    }

    private void loadSubmissions() {
        swipeRefreshLayout.setRefreshing(true);
        String url = "https://hn.algolia.com/api/v1/search_by_date?tags=author_" + getIntent().getStringExtra(KEY_USER) + "&hitsPerPage=500";

        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                response -> {
                    swipeRefreshLayout.setRefreshing(false);
                    try {
                        int oldSize = submissions.size();

                        submissions.clear();
                        submissions.add(new Story());

                        adapter.notifyItemRangeRemoved(1, oldSize + 1);

                        submissions.addAll(JSONParser.algoliaJsonToStories(response));

                        adapter.loadingFailed = false;

                        adapter.notifyItemRangeInserted(1, submissions.size());
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

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, R.anim.activity_out_animation);
    }
}