package com.simon.harmonichackernews.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import com.simon.harmonichackernews.CommentsActivity;
import com.simon.harmonichackernews.MainActivity;
import com.simon.harmonichackernews.R;

public class StoriesWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_REFRESH = "com.simon.harmonichackernews.widget.ACTION_REFRESH";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, StoriesWidgetProvider.class));

            // Show loading spinner on all widgets
            for (int appWidgetId : appWidgetIds) {
                showRefreshing(context, appWidgetManager, appWidgetId);
            }

            // Notify data changed to trigger onDataSetChanged in factory
            // Factory will call updateRefreshDone() when finished
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_stories_list);
        }
    }

    static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
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

        // Refresh button click -> send ACTION_REFRESH broadcast
        Intent refreshIntent = new Intent(context, StoriesWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context, 0, refreshIntent,
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
        if (feedName != null) {
            views.setTextViewText(R.id.widget_title, "Harmonic \u00b7 " + feedName);
        } else {
            views.setTextViewText(R.id.widget_title, "Harmonic");
        }

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
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
    }

    static void updateRefreshDone(Context context, int appWidgetId) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);
        views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE);
        views.setViewVisibility(R.id.widget_refresh_progress, View.GONE);
        views.setTextViewText(R.id.widget_updated_text, formatUpdatedTime(context));
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
    }

    static String formatUpdatedTime(Context context) {
        long lastUpdated = StoriesRemoteViewsFactory.getLastUpdated(context);
        if (lastUpdated == 0) {
            return "";
        }
        long minutesAgo = (System.currentTimeMillis() - lastUpdated) / 60000;
        if (minutesAgo < 1) {
            return "now";
        } else if (minutesAgo < 60) {
            return minutesAgo + "m";
        } else {
            long hoursAgo = minutesAgo / 60;
            return hoursAgo + "h";
        }
    }
}
