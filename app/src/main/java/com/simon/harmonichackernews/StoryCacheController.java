package com.simon.harmonichackernews;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.simon.harmonichackernews.utils.ArticleSnapshotDownloader;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import okhttp3.Call;

class StoryCacheController {
    private static final long HEADER_LAYOUT_ANIMATION_DURATION_MS = 220;
    private static final long CACHE_PROGRESS_FINISHED_HOLD_MS = 1000;
    private static final long CACHE_PROGRESS_FADE_DURATION_MS = 140;
    private static final long CACHE_PROGRESS_STATUS_TEXT_FADE_DURATION_MS = 90;
    private static final String CACHE_PROGRESS_STATUS_CACHING = "Caching stories";
    private static final String CACHE_PROGRESS_STATUS_FINISHED = "Finished";
    private static final String CACHE_PROGRESS_STATUS_FAILED = "Caching failed";
    private static final String CACHE_PROGRESS_STATUS_EMPTY = "No stories to cache";
    private static final int MAX_CONCURRENT_ARTICLE_DOWNLOADS = 4;

    interface Callbacks {
        @Nullable
        Context getContext();

        @Nullable
        RequestQueue getRequestQueue();

        @NonNull
        Object getRequestTag();

        void beginHeaderTransition();
    }

    private final Callbacks callbacks;
    @Nullable
    private TextView statusText;
    @Nullable
    private LinearProgressIndicator progressIndicator;
    private boolean cachingStories = false;
    private boolean progressIndicatorVisible = false;
    private boolean progressHidePending = false;
    private int progressAnimationGeneration = 0;
    private int cacheStoriesTotal = 1;
    private int cacheStoriesCompleted = 0;
    private String progressStatus = CACHE_PROGRESS_STATUS_CACHING;
    private final ArrayDeque<ArticleDownload> pendingArticleDownloads = new ArrayDeque<>();
    private final Set<Call> activeArticleDownloads = new HashSet<>();
    @Nullable
    private ArticleSnapshotDownloader articleSnapshotDownloader;
    private int articleDownloadGeneration = 0;

    StoryCacheController(@NonNull Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    void bindViews(@Nullable LinearProgressIndicator progressIndicator, @Nullable TextView statusText) {
        this.progressIndicator = progressIndicator;
        this.statusText = statusText;
        updateProgressIndicator();
    }

    void clearViewReferences() {
        articleDownloadGeneration++;
        pendingArticleDownloads.clear();
        for (Call call : activeArticleDownloads) {
            call.cancel();
        }
        activeArticleDownloads.clear();
        articleSnapshotDownloader = null;
        progressAnimationGeneration++;
        if (progressIndicator != null) {
            progressIndicator.animate().cancel();
        }
        if (statusText != null) {
            statusText.animate().cancel();
        }
        statusText = null;
        progressIndicator = null;
        cachingStories = false;
        progressIndicatorVisible = false;
        progressHidePending = false;
        resetProgressState();
    }

    boolean isCachingStories() {
        return cachingStories;
    }

    void updateProgressIndicator() {
        if (progressIndicator == null || statusText == null) {
            return;
        }

        LinearProgressIndicator currentProgressIndicator = progressIndicator;
        TextView currentStatusText = statusText;
        String status = cachingStories ? getCachingStatus() : progressStatus;
        currentProgressIndicator.setMax(Math.max(cacheStoriesTotal, 1));
        currentProgressIndicator.setProgressCompat(cacheStoriesCompleted, true);
        updateProgressStatusText(currentStatusText, status);

        if (cachingStories) {
            showProgressIndicator(currentProgressIndicator, currentStatusText);
            return;
        }

        hideProgressIndicator(currentProgressIndicator, currentStatusText);
    }

    void cacheStories() {
        if (cachingStories) {
            return;
        }

        Context context = callbacks.getContext();
        RequestQueue queue = callbacks.getRequestQueue();
        if (context == null || queue == null) {
            return;
        }

        int storiesToCache = SettingsUtils.getStoriesToCache(context);
        startProgress(storiesToCache);
        boolean cacheArticles = SettingsUtils.shouldUseIntegratedWebView(context);
        articleSnapshotDownloader = cacheArticles
                ? new ArticleSnapshotDownloader(context)
                : null;
        StringRequest request = new StringRequest(Request.Method.GET, Utils.URL_TOP,
                response -> {
                    try {
                        JSONArray arr = new JSONArray(response);
                        int storyCount = storiesToCache;
                        if (storyCount == 0) {
                            finishProgress(CACHE_PROGRESS_STATUS_EMPTY);
                            return;
                        }

                        final int[] remaining = {storyCount};
                        final int[] articleFailures = {0};
                        for (int i = 0; i < storyCount; i++) {
                            int id = arr.getInt(i);
                            String url = "https://hn.algolia.com/api/v1/items/" + id;
                            StringRequest storyRequest = new StringRequest(Request.Method.GET, url,
                                    storyResponse -> {
                                        Utils.cacheStory(context, id, storyResponse);
                                        if (cacheArticles) {
                                            cacheStoryArticleSnapshot(id, storyResponse, articleFailures, () -> onCacheStoryFinished(remaining));
                                        } else {
                                            onCacheStoryFinished(remaining);
                                        }
                                    }, error -> onCacheStoryFinished(remaining));
                            storyRequest.setTag(callbacks.getRequestTag());
                            queue.add(storyRequest);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        finishProgress(CACHE_PROGRESS_STATUS_FAILED);
                    }
                }, error -> finishProgress(CACHE_PROGRESS_STATUS_FAILED));

        request.setTag(callbacks.getRequestTag());
        queue.add(request);
    }

    private void startProgress(int total) {
        cachingStories = true;
        cacheStoriesTotal = Math.max(total, 1);
        cacheStoriesCompleted = 0;
        progressStatus = CACHE_PROGRESS_STATUS_CACHING;
        updateProgressIndicator();
    }

    private void incrementProgress() {
        cacheStoriesCompleted = Math.min(cacheStoriesCompleted + 1, cacheStoriesTotal);
        updateProgressIndicator();
    }

    private void finishProgress() {
        finishProgress(CACHE_PROGRESS_STATUS_FINISHED);
    }

    private void finishProgress(@NonNull String status) {
        cachingStories = false;
        progressStatus = status;
        updateProgressIndicator();
    }

    private void updateProgressStatusText(@NonNull TextView currentStatusText,
                                          @NonNull String status) {
        if (TextUtils.equals(currentStatusText.getText(), status)) {
            return;
        }

        currentStatusText.animate().cancel();
        if (currentStatusText.getVisibility() != View.VISIBLE || !ViewCompat.isLaidOut(currentStatusText)) {
            currentStatusText.setAlpha(1f);
            currentStatusText.setText(status);
            return;
        }

        currentStatusText.animate()
                .alpha(0f)
                .setDuration(CACHE_PROGRESS_STATUS_TEXT_FADE_DURATION_MS)
                .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                .withEndAction(() -> {
                    currentStatusText.setText(status);
                    currentStatusText.animate()
                            .alpha(1f)
                            .setDuration(CACHE_PROGRESS_STATUS_TEXT_FADE_DURATION_MS)
                            .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                            .start();
                })
                .start();
    }

    private void showProgressIndicator(@NonNull LinearProgressIndicator currentProgressIndicator,
                                       @NonNull TextView currentStatusText) {
        if (progressIndicatorVisible
                && currentProgressIndicator.getVisibility() == View.VISIBLE
                && currentStatusText.getVisibility() == View.VISIBLE) {
            return;
        }

        progressAnimationGeneration++;
        progressHidePending = false;
        currentProgressIndicator.animate().cancel();
        currentStatusText.animate().cancel();
        progressIndicatorVisible = true;
        callbacks.beginHeaderTransition();
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

    private void hideProgressIndicator(@NonNull LinearProgressIndicator currentProgressIndicator,
                                       @NonNull TextView currentStatusText) {
        if (progressHidePending) {
            return;
        }

        if (!progressIndicatorVisible
                && currentProgressIndicator.getVisibility() != View.VISIBLE
                && currentStatusText.getVisibility() != View.VISIBLE) {
            resetProgressState();
            return;
        }

        progressIndicatorVisible = false;
        progressHidePending = true;
        int animationGeneration = ++progressAnimationGeneration;
        currentProgressIndicator.animate().cancel();
        currentProgressIndicator.postDelayed(() -> {
            if (progressAnimationGeneration != animationGeneration
                    || progressIndicator != currentProgressIndicator
                    || statusText != currentStatusText) {
                return;
            }

            callbacks.beginHeaderTransition();
            currentStatusText.animate().cancel();
            currentStatusText.setVisibility(View.GONE);
            currentProgressIndicator.setVisibility(View.GONE);
            currentProgressIndicator.postDelayed(() -> {
                if (progressAnimationGeneration != animationGeneration
                        || progressIndicator != currentProgressIndicator
                        || statusText != currentStatusText) {
                    return;
                }

                currentStatusText.setAlpha(1f);
                currentStatusText.setText(CACHE_PROGRESS_STATUS_CACHING);
                currentProgressIndicator.setAlpha(1f);
                currentProgressIndicator.setProgressCompat(0, false);
                resetProgressState();
                progressHidePending = false;
            }, HEADER_LAYOUT_ANIMATION_DURATION_MS);
        }, CACHE_PROGRESS_FINISHED_HOLD_MS);
    }

    private void resetProgressState() {
        cacheStoriesTotal = 1;
        cacheStoriesCompleted = 0;
        progressStatus = CACHE_PROGRESS_STATUS_CACHING;
    }

    @NonNull
    private String getCachingStatus() {
        return "Caching " + cacheStoriesTotal + (cacheStoriesTotal == 1 ? " story" : " stories");
    }

    private void onCacheStoryFinished(int[] remaining) {
        incrementProgress();
        remaining[0]--;
        if (remaining[0] > 0) {
            return;
        }

        finishProgress();
    }

    private void cacheStoryArticleSnapshot(int id,
                                           String storyJson,
                                           int[] articleFailures,
                                           Runnable onComplete) {
        if (articleSnapshotDownloader == null) {
            onComplete.run();
            return;
        }

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

            pendingArticleDownloads.add(new ArticleDownload(
                    id, articleUrl, articleFailures, onComplete, articleDownloadGeneration));
            startPendingArticleDownloads();
        } catch (JSONException e) {
            e.printStackTrace();
            articleFailures[0]++;
            onComplete.run();
        }
    }

    private void startPendingArticleDownloads() {
        ArticleSnapshotDownloader downloader = articleSnapshotDownloader;
        if (downloader == null) {
            return;
        }

        while (activeArticleDownloads.size() < MAX_CONCURRENT_ARTICLE_DOWNLOADS
                && !pendingArticleDownloads.isEmpty()) {
            ArticleDownload download = pendingArticleDownloads.remove();
            if (download.generation != articleDownloadGeneration) {
                continue;
            }

            Call call = downloader.download(
                    download.storyId,
                    download.articleUrl,
                    (completedCall, success) -> {
                        if (download.generation != articleDownloadGeneration) {
                            return;
                        }
                        activeArticleDownloads.remove(completedCall);
                        if (!success) {
                            download.articleFailures[0]++;
                        }
                        download.onComplete.run();
                        startPendingArticleDownloads();
                    });
            if (call == null) {
                download.articleFailures[0]++;
                download.onComplete.run();
                continue;
            }
            activeArticleDownloads.add(call);
        }
    }

    private static final class ArticleDownload {
        private final int storyId;
        @NonNull
        private final String articleUrl;
        @NonNull
        private final int[] articleFailures;
        @NonNull
        private final Runnable onComplete;
        private final int generation;

        private ArticleDownload(int storyId,
                                @NonNull String articleUrl,
                                @NonNull int[] articleFailures,
                                @NonNull Runnable onComplete,
                                int generation) {
            this.storyId = storyId;
            this.articleUrl = articleUrl;
            this.articleFailures = articleFailures;
            this.onComplete = onComplete;
            this.generation = generation;
        }
    }
}
