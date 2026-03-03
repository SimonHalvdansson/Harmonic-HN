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
import com.simon.harmonichackernews.utils.Utils;

public class StoriesWidgetProvider extends AppWidgetProvider {

    private static final String ACTION_REFRESH = "com.simon.harmonichackernews.widget.ACTION_REFRESH";
    private static final String EXTRA_APPWIDGET_ID = "refresh_appwidget_id";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Utils.log("WidgetProvider onUpdate count=" + (appWidgetIds == null ? 0 : appWidgetIds.length));
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        Utils.log("WidgetProvider onReceive action=" + action);

        if (ACTION_REFRESH.equals(action)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Utils.log("WidgetProvider refresh request widgetId=" + appWidgetId);
                // Refresh only the tapped widget
                StoriesRemoteViewsFactory.setSkipFetch(context, appWidgetId, false);
                showRefreshing(context, appWidgetManager, appWidgetId);
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stories_list);
            } else {
                Utils.log("WidgetProvider refresh request missing widget id, fallback to all widgets");
                // Fallback: refresh all widgets
                int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                        new ComponentName(context, StoriesWidgetProvider.class));
                for (int id : appWidgetIds) {
                    StoriesRemoteViewsFactory.setSkipFetch(context, id, false);
                    showRefreshing(context, appWidgetManager, id);
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_stories_list);
            }
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        Utils.log("WidgetProvider onDeleted count=" + (appWidgetIds == null ? 0 : appWidgetIds.length));
        for (int appWidgetId : appWidgetIds) {
            WidgetConfigActivity.clearPreferences(context, appWidgetId);
            StoriesRemoteViewsFactory.clearPreferences(context, appWidgetId);
        }
    }

    static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Utils.log("WidgetProvider updateWidget widgetId=" + appWidgetId + " start");
        StoriesRemoteViewsFactory.setSkipFetch(context, appWidgetId, false);
        StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, true);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);
        bindWidgetCommonViews(context, views, appWidgetId);

        // Show refreshing state initially
        views.setViewVisibility(R.id.widget_refresh_button, View.GONE);
        views.setViewVisibility(R.id.widget_refresh_progress, View.VISIBLE);
        views.setTextViewText(R.id.widget_updated_text, "");

        appWidgetManager.updateAppWidget(appWidgetId, views);

        // Trigger data refresh — factory will call updateRefreshDone() when finished
        Utils.log("WidgetProvider updateWidget widgetId=" + appWidgetId + " notify data changed");
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stories_list);
    }

    private static void showRefreshing(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Utils.log("WidgetProvider showRefreshing widgetId=" + appWidgetId);
        StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, true);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);
        bindWidgetCommonViews(context, views, appWidgetId);
        views.setViewVisibility(R.id.widget_refresh_button, View.GONE);
        views.setViewVisibility(R.id.widget_refresh_progress, View.VISIBLE);
        views.setTextViewText(R.id.widget_updated_text, "");
        views.setTextViewText(R.id.widget_empty_text, "Loading stories\u2026");
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
    }

    static void updateRefreshDone(Context context, int appWidgetId) {
        Utils.log("WidgetProvider updateRefreshDone widgetId=" + appWidgetId);
        StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, false);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);
        bindWidgetCommonViews(context, views, appWidgetId);
        views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE);
        views.setViewVisibility(R.id.widget_refresh_progress, View.GONE);
        views.setTextViewText(R.id.widget_updated_text, formatUpdatedTime(context, appWidgetId));
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
    }

    static void updateRefreshError(Context context, int appWidgetId) {
        Utils.log("WidgetProvider updateRefreshError widgetId=" + appWidgetId);
        StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, false);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);
        bindWidgetCommonViews(context, views, appWidgetId);
        views.setViewVisibility(R.id.widget_refresh_button, View.VISIBLE);
        views.setViewVisibility(R.id.widget_refresh_progress, View.GONE);
        views.setTextViewText(R.id.widget_empty_text, "Couldn\u2019t load stories");
        // Keep previous timestamp if any
        views.setTextViewText(R.id.widget_updated_text, formatUpdatedTime(context, appWidgetId));
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
    }

    private static void applyThemeColors(Context context, AppWidgetManager appWidgetManager,
                                         int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_stories);
        bindWidgetCommonViews(context, views, appWidgetId);
        appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views);
    }

    private static void bindWidgetCommonViews(Context context, RemoteViews views, int appWidgetId) {
        // Keep collection adapter bindings present on every update so launcher-side refresh
        // notifications always resolve to a known collection view.
        Intent serviceIntent = new Intent(context, StoriesWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_stories_list, serviceIntent);
        views.setEmptyView(R.id.widget_stories_list, R.id.widget_empty_text);

        Intent mainIntent = new Intent(context, MainActivity.class);
        mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(
                context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_header, mainPendingIntent);

        Intent refreshIntent = new Intent(context, StoriesWidgetProvider.class);
        refreshIntent.setAction(ACTION_REFRESH);
        refreshIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent);

        Intent itemIntent = new Intent(context, CommentsActivity.class);
        itemIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent itemPendingIntent = PendingIntent.getActivity(
                context, 1, itemIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.widget_stories_list, itemPendingIntent);

        String feedName = WidgetConfigActivity.getFeedName(context, appWidgetId);
        views.setTextViewText(R.id.widget_title, feedName != null ? feedName : "Top stories");
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
