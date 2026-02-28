package com.simon.harmonichackernews.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.simon.harmonichackernews.CommentsFragment;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.JSONParser;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StoriesRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private static final int MAX_STORIES = 15;
    private static final String PREFS_NAME = "widget_stories_cache";
    private static final String KEY_LAST_UPDATED_PREFIX = "last_updated_";
    private static final String KEY_SKIP_FETCH_PREFIX = "skip_fetch_";

    private final Context context;
    private final int appWidgetId;
    private final List<Story> stories = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean fetchFailed = false;

    public StoriesRemoteViewsFactory(Context context, int appWidgetId) {
        this.context = context;
        this.appWidgetId = appWidgetId;
    }

    @Override
    public void onCreate() {
    }

    public static void setSkipFetch(Context context, int appWidgetId, boolean skip) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SKIP_FETCH_PREFIX + appWidgetId, skip).commit();
    }

    public static void setSkipFetchAll(Context context, boolean skip) {
        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        int[] ids = awm.getAppWidgetIds(
                new ComponentName(context, StoriesWidgetProvider.class));
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        for (int id : ids) {
            editor.putBoolean(KEY_SKIP_FETCH_PREFIX + id, skip);
        }
        editor.commit();
    }

    @Override
    public void onDataSetChanged() {
        // Skip fetch if flag is set and we already have data (e.g. after partiallyUpdateAppWidget
        // which triggers onDataSetChanged). The flag is NOT cleared here — it stays set until the
        // provider explicitly clears it before a deliberate refresh.
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String skipKey = KEY_SKIP_FETCH_PREFIX + appWidgetId;
        if (prefs.getBoolean(skipKey, false) && !stories.isEmpty()) {
            return;
        }

        fetchFailed = false;
        List<Story> freshStories = new ArrayList<>();

        try {
            OkHttpClient client = NetworkComponent.getOkHttpClientInstance();

            // Fetch story IDs using configured feed type
            String feedUrl = WidgetConfigActivity.getFeedUrl(context, appWidgetId);
            Request idsRequest = new Request.Builder()
                    .url(feedUrl)
                    .build();

            try (Response idsResponse = client.newCall(idsRequest).execute()) {
                if (!idsResponse.isSuccessful() || idsResponse.body() == null) {
                    fetchFailed = true;
                    postRefreshError();
                    return;
                }

                String idsBody = idsResponse.body().string();
                JSONArray idsArray = new JSONArray(idsBody);

                int count = Math.min(idsArray.length(), MAX_STORIES);

                // Fetch each story
                for (int i = 0; i < count; i++) {
                    int storyId = idsArray.getInt(i);
                    String storyUrl = "https://hacker-news.firebaseio.com/v0/item/" + storyId + ".json";

                    Request storyRequest = new Request.Builder()
                            .url(storyUrl)
                            .build();

                    try (Response storyResponse = client.newCall(storyRequest).execute()) {
                        if (storyResponse.isSuccessful() && storyResponse.body() != null) {
                            String storyBody = storyResponse.body().string();
                            Story story = new Story();
                            story.id = storyId;
                            if (JSONParser.updateStoryWithHNJson(storyBody, story, false)) {
                                freshStories.add(story);
                            }
                        }
                    } catch (Exception e) {
                        // Skip this story on error
                    }
                }
            }

            if (freshStories.isEmpty()) {
                fetchFailed = true;
                postRefreshError();
                return;
            }

            // Swap in new data only on success — keeps stale data visible during fetch
            stories.clear();
            stories.addAll(freshStories);

            // Save last updated time only on successful fetch
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_UPDATED_PREFIX + appWidgetId, System.currentTimeMillis()).apply();

        } catch (Exception e) {
            e.printStackTrace();
            fetchFailed = true;
            postRefreshError();
            return;
        }

        // Set skip_fetch BEFORE posting the UI update. partiallyUpdateAppWidget triggers the
        // framework to call onDataSetChanged again — the flag ensures it skips the fetch.
        setSkipFetch(context, appWidgetId, true);
        postRefreshDone();
    }

    private void postRefreshDone() {
        final int widgetId = appWidgetId;
        mainHandler.post(() -> StoriesWidgetProvider.updateRefreshDone(context, widgetId));
    }

    private void postRefreshError() {
        final int widgetId = appWidgetId;
        mainHandler.post(() -> StoriesWidgetProvider.updateRefreshError(context, widgetId));
    }

    @Override
    public void onDestroy() {
        stories.clear();
    }

    @Override
    public int getCount() {
        return stories.size();
    }

    @Override
    public RemoteViews getViewAt(int position) {
        if (position >= stories.size()) {
            return getLoadingView();
        }

        Story story = stories.get(position);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_story_item);

        // Index
        boolean showIndex = SettingsUtils.shouldShowIndex(context);
        views.setViewVisibility(R.id.widget_story_index, showIndex ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setTextViewText(R.id.widget_story_index, (position + 1) + ".");

        // Title
        views.setTextViewText(R.id.widget_story_title, story.title);

        // Meta: score + domain + time
        String meta = story.score + " pts";
        if (story.url != null && story.isLink) {
            try {
                String domain = Utils.getDomainName(story.url);
                meta += " · " + domain;
            } catch (Exception ignored) {
            }
        }
        meta += " · " + Utils.getTimeAgo(story.time);
        views.setTextViewText(R.id.widget_story_meta, meta);

        // Fill-in intent for item click -> CommentsActivity
        Intent fillInIntent = new Intent();
        fillInIntent.putExtras(story.toBundle());
        fillInIntent.putExtra(CommentsFragment.EXTRA_SHOW_WEBSITE, story.isLink);
        views.setOnClickFillInIntent(R.id.widget_story_item_container, fillInIntent);

        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return new RemoteViews(context.getPackageName(), R.layout.widget_story_item_loading);
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public long getItemId(int position) {
        if (position < stories.size()) {
            return stories.get(position).id;
        }
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    public static long getLastUpdated(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_UPDATED_PREFIX + appWidgetId, 0);
    }

    static void clearPreferences(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(KEY_LAST_UPDATED_PREFIX + appWidgetId)
                .remove(KEY_SKIP_FETCH_PREFIX + appWidgetId)
                .apply();
    }
}
