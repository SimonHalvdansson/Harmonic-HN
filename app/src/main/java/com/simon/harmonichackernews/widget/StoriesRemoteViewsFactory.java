package com.simon.harmonichackernews.widget;

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
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class StoriesRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    private static final int MAX_STORIES = 15;
    private static final String PREFS_NAME = "widget_stories_cache";
    private static final String KEY_LAST_UPDATED_PREFIX = "last_updated_";

    private final Context context;
    private final int appWidgetId;
    private final List<Story> stories = new ArrayList<>();
    private boolean fetchFailed = false;

    public StoriesRemoteViewsFactory(Context context, int appWidgetId) {
        this.context = context;
        this.appWidgetId = appWidgetId;
    }

    @Override
    public void onCreate() {
    }

    @Override
    public void onDataSetChanged() {
        stories.clear();
        fetchFailed = false;

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
                    StoriesWidgetProvider.updateRefreshError(context, appWidgetId);
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
                                stories.add(story);
                            }
                        }
                    } catch (Exception e) {
                        // Skip this story on error
                    }
                }
            }

            if (stories.isEmpty()) {
                fetchFailed = true;
                StoriesWidgetProvider.updateRefreshError(context, appWidgetId);
                return;
            }

            // Save last updated time only on successful fetch
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putLong(KEY_LAST_UPDATED_PREFIX + appWidgetId, System.currentTimeMillis()).apply();

        } catch (Exception e) {
            e.printStackTrace();
            fetchFailed = true;
            StoriesWidgetProvider.updateRefreshError(context, appWidgetId);
            return;
        }

        // Notify widget to stop spinner and show updated time
        StoriesWidgetProvider.updateRefreshDone(context, appWidgetId);
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
            return null;
        }

        Story story = stories.get(position);
        boolean darkMode = ThemeUtils.isDarkMode(context);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_story_item);

        // Index
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

        // Apply theme colors
        if (darkMode) {
            views.setTextColor(R.id.widget_story_index, 0xFF777777);
            views.setTextColor(R.id.widget_story_title, 0xFFdfdfdf);
            views.setTextColor(R.id.widget_story_meta, 0xFFbbbbcc);
        } else {
            views.setTextColor(R.id.widget_story_index, 0xFF999999);
            views.setTextColor(R.id.widget_story_title, 0xFF2f2f2f);
            views.setTextColor(R.id.widget_story_meta, 0xFF4a4a4a);
        }

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
        prefs.edit().remove(KEY_LAST_UPDATED_PREFIX + appWidgetId).apply();
    }
}
