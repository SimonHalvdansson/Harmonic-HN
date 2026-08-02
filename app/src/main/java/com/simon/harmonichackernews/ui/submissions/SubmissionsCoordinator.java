package com.simon.harmonichackernews.ui.submissions;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.simon.harmonichackernews.MainActivity;
import com.simon.harmonichackernews.adapters.StoryDisplaySettings;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.BackgroundJSONParser;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** Networking and filtering state for a Compose submissions destination. */
public final class SubmissionsCoordinator {
    private static final int SUBMISSION_FILTER_STORIES = 0;
    private static final int SUBMISSION_FILTER_BOTH = 1;
    private static final int SUBMISSION_FILTER_COMMENTS = 2;
    private static final int ALGOLIA_HITS_INCREMENT = 200;

    public interface Navigator {
        void openStory(@NonNull Story story, boolean showWebsite);
    }

    private final MainActivity activity;
    private final String userName;
    private final Navigator navigator;
    private final ArrayList<Story> submissions = new ArrayList<>();
    private final ArrayList<Story> allSubmissions = new ArrayList<>();
    private final RequestQueue queue;
    private final Object requestTag = new Object();
    private final SubmissionsComposeController composeController;
    private Future<?> submissionsParseTask;
    private boolean initialLoadFinished;
    private boolean submissionsLoading;
    private boolean submissionsLoadedSuccessfully;
    private int submissionsRequestGeneration;
    private int submissionsHitsPerPage = ALGOLIA_HITS_INCREMENT;
    private boolean submissionsCanLoadMore;
    private int submissionFilter = SUBMISSION_FILTER_BOTH;

    public SubmissionsCoordinator(
            @NonNull MainActivity activity,
            @NonNull String userName,
            @NonNull Navigator navigator) {
        this.activity = activity;
        this.userName = userName;
        this.navigator = navigator;
        queue = NetworkComponent.getRequestQueueInstance(activity);
        composeController = SubmissionsComposeController.create(
                activity,
                userName,
                submissionFilter,
                new SubmissionsComposeController.Listener() {
                    @Override
                    public void onFilterSelected(int filter) {
                        if (submissionFilter == filter) return;
                        submissionFilter = filter;
                        applySubmissionFilter();
                    }

                    @Override
                    public void onRefresh() {
                        loadSubmissions(true);
                    }

                    @Override
                    public void onStoryLinkClick(@NonNull Story story) {
                        if (story.isLink) {
                            if (SettingsUtils.shouldUseIntegratedWebView(activity)) {
                                openComments(story, true);
                            } else {
                                Utils.launchCustomTab(activity, story.url);
                            }
                        } else {
                            openComments(story, false);
                        }
                    }

                    @Override
                    public void onStoryCommentsClick(@NonNull Story story) {
                        openComments(story, false);
                    }

                    @Override
                    public void onCommentStoryClick(@NonNull Story story) {
                        openCommentMasterStory(story);
                    }

                    @Override
                    public void onCommentRepliesClick(@NonNull Story story) {
                        openComments(story, false);
                    }

                    @Override
                    public void onLoadMore() {
                        if (submissionsLoading || !submissionsCanLoadMore) return;
                        submissionsHitsPerPage += ALGOLIA_HITS_INCREMENT;
                        loadSubmissions(false);
                    }
                });
        composeController.updateDisplaySettings(
                StoryDisplaySettings.from(activity).withShowIndex(false));
        loadSubmissions(true);
    }

    @NonNull
    public SubmissionsComposeController getComposeController() {
        return composeController;
    }

    public void close() {
        submissionsRequestGeneration++;
        cancelSubmissionsParseTask();
        queue.cancelAll(requestTag);
    }

    private void applySubmissionFilter() {
        submissions.clear();
        for (Story story : allSubmissions) {
            if (shouldShowStoryForSubmissionFilter(story)) submissions.add(story);
        }
        composeController.updateContent(
                new ArrayList<>(submissions),
                submissionFilter,
                !allSubmissions.isEmpty(),
                submissionsCanLoadMore,
                submissionsLoadedSuccessfully,
                getEmptyViewText());
    }

    private boolean shouldShowStoryForSubmissionFilter(Story story) {
        if (submissionFilter == SUBMISSION_FILTER_STORIES) return !story.isComment;
        if (submissionFilter == SUBMISSION_FILTER_COMMENTS) return story.isComment;
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
        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> {
                    try {
                        JSONParser.updateCommentMasterStoryWithHNJson(story, response);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if (submissions.contains(story)) composeController.refreshStoryRows();
                    Story refreshed = story.toCommentMasterStory();
                    openComments(refreshed != null ? refreshed : masterStory, false);
                }, error -> openComments(masterStory, false));
        request.setTag(requestTag);
        queue.add(request);
    }

    private void openComments(Story story, boolean showWebsite) {
        navigator.openStory(story, showWebsite);
    }

    private void loadSubmissions(boolean resetResultLimit) {
        cancelSubmissionsParseTask();
        if (resetResultLimit) submissionsHitsPerPage = ALGOLIA_HITS_INCREMENT;
        int requestGeneration = ++submissionsRequestGeneration;
        submissionsLoading = true;
        boolean showInitialLoading = !initialLoadFinished;
        composeController.updateLoading(
                true,
                showInitialLoading,
                !showInitialLoading && resetResultLimit);
        updateEmptyView();

        String url = Uri.parse("https://hn.algolia.com/api/v1/search_by_date")
                .buildUpon()
                .appendQueryParameter("tags", "author_" + userName)
                .appendQueryParameter("hitsPerPage", String.valueOf(submissionsHitsPerPage))
                .build()
                .toString();

        StringRequest request = new StringRequest(Request.Method.GET, url,
                response -> submissionsParseTask = BackgroundJSONParser.parseAlgoliaJson(
                        response,
                        new BackgroundJSONParser.AlgoliaParseCallback() {
                            @Override
                            public void onParseSuccess(List<Story> parsedStories) {
                                if (requestGeneration != submissionsRequestGeneration) return;
                                submissionsParseTask = null;
                                finishLoading(requestGeneration);
                                submissionsCanLoadMore =
                                        parsedStories.size() >= submissionsHitsPerPage;
                                submissionsLoadedSuccessfully = true;
                                allSubmissions.clear();
                                allSubmissions.addAll(parsedStories);
                                applySubmissionFilter();
                            }

                            @Override
                            public void onParseError(JSONException error) {
                                if (requestGeneration != submissionsRequestGeneration) return;
                                submissionsParseTask = null;
                                finishLoading(requestGeneration);
                                error.printStackTrace();
                            }
                        }),
                error -> {
                    error.printStackTrace();
                    finishLoading(requestGeneration);
                });
        request.setTag(requestTag);
        queue.add(request);
    }

    private void cancelSubmissionsParseTask() {
        if (submissionsParseTask == null) return;
        submissionsParseTask.cancel(true);
        submissionsParseTask = null;
    }

    private void finishLoading(int requestGeneration) {
        if (requestGeneration != submissionsRequestGeneration) return;
        submissionsLoading = false;
        initialLoadFinished = true;
        composeController.updateLoading(false, false, false);
        updateEmptyView();
    }

    private void updateEmptyView() {
        composeController.updateContent(
                new ArrayList<>(submissions),
                submissionFilter,
                !allSubmissions.isEmpty(),
                submissionsCanLoadMore,
                submissionsLoadedSuccessfully,
                getEmptyViewText());
    }

    private String getEmptyViewText() {
        if (allSubmissions.isEmpty()) return "No submissions";
        if (submissionFilter == SUBMISSION_FILTER_STORIES) return "No stories";
        if (submissionFilter == SUBMISSION_FILTER_COMMENTS) return "No comments";
        return "No submissions";
    }
}
