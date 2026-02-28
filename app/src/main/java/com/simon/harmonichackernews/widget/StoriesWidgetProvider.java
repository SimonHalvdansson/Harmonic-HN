package com.simon.harmonichackernews.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import com.simon.harmonichackernews.CommentsActivity;
import com.simon.harmonichackernews.MainActivity;
import com.simon.harmonichackernews.R;

public class StoriesWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_REFRESH = "com.simon.harmonichackernews.widget.ACTION_REFRESH";
    private static final String EXTRA_APPWIDGET_ID = "refresh_appwidget_id";
    private static int lastNightMode = -1;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        lastNightMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        // Initialize lastNightMode on first receive after process restart
        // to avoid spurious config-change re-renders
        if (lastNightMode == -1) {
            lastNightMode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
        }

        String action = intent.getAction();

        if (ACTION_REFRESH.equals(action)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Refresh only the tapped widget
                StoriesRemoteViewsFactory.setSkipFetch(context, appWidgetId, false);
                showRefreshing(context, appWidgetManager, appWidgetId);
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stories_list);
            } else {
                // Fallback: refresh all widgets
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                        new ComponentName(context, StoriesWidgetProvider.class));
                for (int id : appWidgetIds) {
                    StoriesRemoteViewsFactory.setSkipFetch(context, id, false);
                    showRefreshing(context, appWidgetManager, id);
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_stories_list);
            }
        } else if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
            int currentNightMode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            if (currentNightMode != lastNightMode) {
                lastNightMode = currentNightMode;
                boolean night = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                        new ComponentName(context, StoriesWidgetProvider.class));
                // Update header/background colors without resetting the adapter
                for (int id : appWidgetIds) {
                    applyThemeColors(context, appWidgetManager, id, night);
                }
                // Re-render list items with new colors (skip network fetch)
                StoriesRemoteViewsFactory.setSkipFetchAll(context, true);
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_stories_list);
            }
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            WidgetConfigActivity.clearPreferences(context, appWidgetId);
            StoriesRemoteViewsFactory.clearPreferences(context, appWidgetId);
        }
    }

    static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        StoriesRemoteViewsFactory.setSkipFetch(context, appWidgetId, false);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);

        // Set up RemoteViewsService adapter for the ListView
        Intent serviceIntent = new Intent(context, StoriesWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_stories_list, serviceIntent);
        views.setEmptyView(R.id.widget_stories_list, R.id.widget_empty_text);

        // Header click -> open MainActivity
        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(
                context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_header, mainPendingIntent);

        // Refresh button click -> send ACTION_REFRESH broadcast with widget ID
        Intent refreshIntent = new Intent(context, StoriesWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent);

        // Item click template -> open CommentsActivity
        Intent itemIntent = new Intent(context, CommentsActivity.class);
        itemIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent itemPendingIntent = PendingIntent.getActivity(
                context, 1, itemIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.widget_stories_list, itemPendingIntent);

        // Set header title based on configured feed
        String feedName = WidgetConfigActivity.getFeedName(context, appWidgetId);
        views.setTextViewText(R.id.widget_title, feedName != null ? feedName : "Top stories");

        // Show refreshing state initially
        views.setViewVisibility(R.id.widget_refresh_button, View.GONE);
        views.setViewVisibility(R.id.widget_refresh_progress, View.VISIBLE);
        views.setTextViewText(R.id.widget_updated_text, "");

        appWidgetManager.updateAppWidget(appWidgetId, views);

        // Trigger data refresh — factory will call updateRefreshDone() when finished
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stories_list);
    }

    private static void showRefreshing(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);
        views.setViewVisibility(R.id.widget_refresh_button, View.GONE);
        views.setViewVisibility(R.id.widget_refresh_progress, View.VISIBLE);
        views.setTextViewText(R.id.widget_updated_text, "");
        views.setTextViewText(R.id.widget_empty_text, "Loading stories\u2026");
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
    }

    static void updateRefreshDone(Context context, int appWidgetId) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);
        views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE);
        views.setViewVisibility(R.id.widget_refresh_progress, View.GONE);
        views.setTextViewText(R.id.widget_updated_text, formatUpdatedTime(context, appWidgetId));
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
    }

    static void updateRefreshError(Context context, int appWidgetId) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);
        views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE);
        views.setViewVisibility(R.id.widget_refresh_progress, View.GONE);
        views.setTextViewText(R.id.widget_empty_text, "Couldn\u2019t load stories");
        // Keep previous timestamp if any
        views.setTextViewText(R.id.widget_updated_text, formatUpdatedTime(context, appWidgetId));
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
    }

    private static void applyThemeColors(Context context, AppWidgetManager appWidgetManager,
                                           int appWidgetId, boolean night) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);
        views.setInt(R.id.widget_root, "setBackgroundResource",
                night ? R.drawable.widget_background_dark : R.drawable.widget_background);
        // Rebind icon drawable so its resource-based tint is re-resolved for the new mode.
        views.setImageViewResource(R.id.widget_refresh_button, R.drawable.ic_action_refresh);
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
    }

    static String formatUpdatedTime(Context context, int appWidgetId) {
        long lastUpdated = StoriesRemoteViewsFactory.getLastUpdated(context, appWidgetId);
        if (lastUpdated == 0) {
            return "";
        }
        return android.text.format.DateFormat.getTimeFormat(context)
                .format(new java.util.Date(lastUpdated));
    }
}
