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
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StoriesRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private static final String PREFS_NAME = "widget_stories_cache";
    private static final String KEY_LAST_UPDATED_PREFIX = "last_updated_";
    private static final String KEY_SKIP_FETCH_PREFIX = "skip_fetch_";
    private static final String KEY_REFRESHING_PREFIX = "refreshing_";
    private static final long CALL_TIMEOUT_SECONDS = 15;
    private static final long TOTAL_FETCH_TIMEOUT_MS = 60_000;

    private final Context context;
    private final int appWidgetId;
    private final List<Story> stories = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public StoriesRemoteViewsFactory(Context context, int appWidgetId) {
        this.context = context;
        this.appWidgetId = appWidgetId;
    }

    @Override
    public void onCreate() {
        Utils.log("WidgetFactory onCreate widgetId=" + appWidgetId);
    }

    public static void setSkipFetch(Context context, int appWidgetId, boolean skip) {
        Utils.log("WidgetFactory setSkipFetch widgetId=" + appWidgetId + " skip=" + skip);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SKIP_FETCH_PREFIX + appWidgetId, skip).commit();
    }

    public static void setSkipFetchAll(Context context, boolean skip) {
        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        int[] ids = awm.getAppWidgetIds(
                new ComponentName(context, StoriesWidgetProvider.class));
        Utils.log("WidgetFactory setSkipFetchAll count=" + ids.length + " skip=" + skip);
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        for (int id : ids) {
            editor.putBoolean(KEY_SKIP_FETCH_PREFIX + id, skip);
        }
        editor.commit();
    }

    static void setRefreshing(Context context, int appWidgetId, boolean refreshing) {
        Utils.log("WidgetFactory setRefreshing widgetId=" + appWidgetId + " refreshing=" + refreshing);
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_REFRESHING_PREFIX + appWidgetId, refreshing).apply();
    }

    static boolean isRefreshing(Context context, int appWidgetId) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_REFRESHING_PREFIX + appWidgetId, false);
    }

    @Override
    public void onDataSetChanged() {
        long startedAt = System.currentTimeMillis();
        boolean terminalStatePosted = false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String skipKey = KEY_SKIP_FETCH_PREFIX + appWidgetId;
        boolean skipFetch = prefs.getBoolean(skipKey, false);
        boolean refreshing = isRefreshing(context, appWidgetId);

        Utils.log("WidgetFactory onDataSetChanged start widgetId=" + appWidgetId
                + " skipFetch=" + skipFetch
                + " refreshing=" + refreshing
                + " inMemoryStories=" + stories.size());

        try {
            if (skipFetch && !stories.isEmpty()) {
                if (refreshing) {
                    Utils.log("WidgetFactory skip fetch and reconcile refresh widgetId=" + appWidgetId);
                    postRefreshDone();
                    terminalStatePosted = true;
                } else {
                    Utils.log("WidgetFactory skip fetch widgetId=" + appWidgetId);
                }
                return;
            }

            List<Story> freshStories = new ArrayList<>();
            int storyFetchErrors = 0;
            int visibleStoryCount = WidgetConfigActivity.getStoryCount(context, appWidgetId);
            int fetchStoryCount = WidgetConfigActivity.getFetchStoryCount(context, appWidgetId);

            OkHttpClient client = NetworkComponent.getOkHttpClientInstance()
                    .newBuilder()
                    .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build();

            String feedUrl = WidgetConfigActivity.getFeedUrl(context, appWidgetId);
            Utils.log("WidgetFactory fetch ids widgetId=" + appWidgetId + " url=" + feedUrl);
            Request idsRequest = new Request.Builder()
                    .url(feedUrl)
                    .build();

            try (Response idsResponse = client.newCall(idsRequest).execute()) {
                if (!idsResponse.isSuccessful() || idsResponse.body() == null) {
                    Utils.log("WidgetFactory ids request failed widgetId=" + appWidgetId
                            + " code=" + idsResponse.code());
                    postRefreshError();
                    terminalStatePosted = true;
                    return;
                }

                String idsBody = idsResponse.body().string();
                JSONArray idsArray = new JSONArray(idsBody);
                int count = Math.min(idsArray.length(), fetchStoryCount);

                Utils.log("WidgetFactory ids fetched widgetId=" + appWidgetId
                        + " totalIds=" + idsArray.length()
                        + " visibleCount=" + visibleStoryCount
                        + " fetchTarget=" + fetchStoryCount
                        + " fetchCount=" + count);

                for (int i = 0; i < count; i++) {
                    long elapsedMs = System.currentTimeMillis() - startedAt;
                    if (elapsedMs > TOTAL_FETCH_TIMEOUT_MS) {
                        Utils.log("WidgetFactory total timeout reached widgetId=" + appWidgetId
                                + " elapsedMs=" + elapsedMs);
                        break;
                    }

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
                            } else {
                                storyFetchErrors++;
                            }
                        } else {
                            storyFetchErrors++;
                        }
                    } catch (Exception e) {
                        storyFetchErrors++;
                    }
                }
            }

            if (freshStories.isEmpty()) {
                Utils.log("WidgetFactory no stories fetched widgetId=" + appWidgetId);
                postRefreshError();
                terminalStatePosted = true;
                return;
            }

            Utils.log("WidgetFactory fetch complete widgetId=" + appWidgetId
                    + " stories=" + freshStories.size()
                    + " storyErrors=" + storyFetchErrors
                    + " elapsedMs=" + (System.currentTimeMillis() - startedAt));

            if (freshStories.size() > visibleStoryCount) {
                freshStories = new ArrayList<>(freshStories.subList(0, visibleStoryCount));
            }

            stories.clear();
            stories.addAll(freshStories);

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putLong(KEY_LAST_UPDATED_PREFIX + appWidgetId, System.currentTimeMillis()).apply();

            setSkipFetch(context, appWidgetId, true);
            postRefreshDone();
            terminalStatePosted = true;
        } catch (Throwable t) {
            Utils.log("WidgetFactory onDataSetChanged failed widgetId=" + appWidgetId + " error=" + t);
            postRefreshError();
            terminalStatePosted = true;
        } finally {
            boolean refreshingNow = isRefreshing(context, appWidgetId);
            Utils.log("WidgetFactory onDataSetChanged end widgetId=" + appWidgetId
                    + " terminalPosted=" + terminalStatePosted
                    + " refreshingNow=" + refreshingNow
                    + " elapsedMs=" + (System.currentTimeMillis() - startedAt));
            if (!terminalStatePosted && refreshingNow) {
                Utils.log("WidgetFactory forcing refresh error widgetId=" + appWidgetId);
                postRefreshError();
            }
        }
    }

    private void postRefreshDone() {
        final int widgetId = appWidgetId;
        Utils.log("WidgetFactory postRefreshDone queued widgetId=" + widgetId);
        mainHandler.post(() -> {
            Utils.log("WidgetFactory postRefreshDone run widgetId=" + widgetId);
            StoriesWidgetProvider.updateRefreshDone(context, widgetId);
        });
    }

    private void postRefreshError() {
        final int widgetId = appWidgetId;
        Utils.log("WidgetFactory postRefreshError queued widgetId=" + widgetId);
        mainHandler.post(() -> {
            Utils.log("WidgetFactory postRefreshError run widgetId=" + widgetId);
            StoriesWidgetProvider.updateRefreshError(context, widgetId);
        });
    }

    @Override
    public void onDestroy() {
        Utils.log("WidgetFactory onDestroy widgetId=" + appWidgetId + " stories=" + stories.size());
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
        String meta = story.score + (story.score == 1 ? " pt" : " pts");
        if (story.url != null && story.isLink) {
            try {
                String domain = Utils.getDomainName(story.url);
                meta += " \u00B7 " + domain;
            } catch (Exception ignored) {
            }
        }
        meta += " \u00B7 " + Utils.getTimeAgo(story.time);
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
                .remove(KEY_REFRESHING_PREFIX + appWidgetId)
                .apply();
    }
}
