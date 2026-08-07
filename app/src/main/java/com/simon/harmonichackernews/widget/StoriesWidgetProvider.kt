package com.simon.harmonichackernews.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.Utils
import java.util.Date

class StoriesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Utils.log("WidgetProvider onUpdate count=${appWidgetIds.size}")
        for (appWidgetId in appWidgetIds) {
            refreshWidgetSilently(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action
        Utils.log("WidgetProvider onReceive action=$action")

        if (ACTION_REFRESH == action) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetId = intent.getIntExtra(
                EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Utils.log("WidgetProvider refresh request widgetId=$appWidgetId")
                // Refresh only the tapped widget
                StoriesRemoteViewsFactory.setSkipFetch(context, appWidgetId, false)
                showRefreshing(context, appWidgetManager, appWidgetId)
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetId,
                    R.id.widget_stories_list
                )
            } else {
                Utils.log("WidgetProvider refresh request missing widget id, fallback to all widgets")
                // Fallback: refresh all widgets
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, StoriesWidgetProvider::class.java)
                )
                for (id in appWidgetIds) {
                    StoriesRemoteViewsFactory.setSkipFetch(context, id, false)
                    showRefreshing(context, appWidgetManager, id)
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetIds,
                    R.id.widget_stories_list
                )
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Utils.log("WidgetProvider onDeleted count=${appWidgetIds.size}")
        for (appWidgetId in appWidgetIds) {
            WidgetConfigActivity.clearPreferences(context, appWidgetId)
            StoriesRemoteViewsFactory.clearPreferences(context, appWidgetId)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.simon.harmonichackernews.widget.ACTION_REFRESH"
        private const val EXTRA_APPWIDGET_ID = "refresh_appwidget_id"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Utils.log("WidgetProvider updateWidget widgetId=$appWidgetId start")
            StoriesRemoteViewsFactory.setSkipFetch(context, appWidgetId, false)
            StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, true)
            val views = RemoteViews(context.packageName, R.layout.widget_stories)
            bindWidgetCommonViews(context, views, appWidgetId)

            // Show refreshing state initially
            views.setViewVisibility(R.id.widget_refresh_button, View.GONE)
            views.setViewVisibility(R.id.widget_refresh_progress, View.VISIBLE)
            views.setTextViewText(R.id.widget_updated_text, "")

            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Trigger data refresh — factory will call updateRefreshDone() when finished
            Utils.log("WidgetProvider updateWidget widgetId=$appWidgetId notify data changed")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stories_list)
        }

        private fun refreshWidgetSilently(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val refreshing = StoriesRemoteViewsFactory.isRefreshing(context, appWidgetId)
            Utils.log(
                "WidgetProvider refreshWidgetSilently widgetId=$appWidgetId refreshing=$refreshing"
            )
            StoriesRemoteViewsFactory.setSkipFetch(context, appWidgetId, false)

            val views = RemoteViews(context.packageName, R.layout.widget_stories)
            bindWidgetCommonViews(context, views, appWidgetId)
            views.setViewVisibility(
                R.id.widget_refresh_button,
                if (refreshing) View.GONE else View.VISIBLE
            )
            views.setViewVisibility(
                R.id.widget_refresh_progress,
                if (refreshing) View.VISIBLE else View.GONE
            )
            views.setTextViewText(
                R.id.widget_updated_text,
                formatUpdatedTime(context, appWidgetId)
            )
            if (refreshing) {
                views.setTextViewText(R.id.widget_empty_text, "Loading stories\u2026")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            Utils.log(
                "WidgetProvider refreshWidgetSilently widgetId=$appWidgetId notify data changed"
            )
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stories_list)
        }

        private fun showRefreshing(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            Utils.log("WidgetProvider showRefreshing widgetId=$appWidgetId")
            StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, true)
            val views = RemoteViews(context.packageName, R.layout.widget_stories)
            bindWidgetCommonViews(context, views, appWidgetId)
            views.setViewVisibility(R.id.widget_refresh_button, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_refresh_progress, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_updated_text, "")
            views.setTextViewText(R.id.widget_empty_text, "Loading stories\u2026")
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }

        fun updateRefreshDone(context: Context, appWidgetId: Int) {
            Utils.log("WidgetProvider updateRefreshDone widgetId=$appWidgetId")
            StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, false)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val views = RemoteViews(context.packageName, R.layout.widget_stories)
            bindWidgetCommonViews(context, views, appWidgetId)
            views.setViewVisibility(R.id.widget_refresh_button, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_refresh_progress, android.view.View.GONE)
            views.setTextViewText(
                R.id.widget_updated_text,
                formatUpdatedTime(context, appWidgetId)
            )
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }

        fun updateRefreshError(context: Context, appWidgetId: Int) {
            Utils.log("WidgetProvider updateRefreshError widgetId=$appWidgetId")
            StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, false)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val views = RemoteViews(context.packageName, R.layout.widget_stories)
            bindWidgetCommonViews(context, views, appWidgetId)
            views.setViewVisibility(R.id.widget_refresh_button, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_refresh_progress, android.view.View.GONE)
            views.setTextViewText(R.id.widget_empty_text, "Couldn\u2019t load stories")
            // Keep previous timestamp if any
            views.setTextViewText(
                R.id.widget_updated_text,
                formatUpdatedTime(context, appWidgetId)
            )
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }

        private fun bindWidgetCommonViews(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int
        ) {
            // Keep collection adapter bindings present on every update so launcher-side refresh
            // notifications always resolve to a known collection view.
            val serviceIntent = Intent(context, StoriesWidgetService::class.java)
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            serviceIntent.data = Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME))
            views.setRemoteAdapter(R.id.widget_stories_list, serviceIntent)
            views.setEmptyView(R.id.widget_stories_list, R.id.widget_empty_text)

            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val mainPendingIntent = PendingIntent.getActivity(
                context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, mainPendingIntent)

            val refreshIntent = Intent(context, StoriesWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

            val itemIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val itemPendingIntent = PendingIntent.getActivity(
                context, 1, itemIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_stories_list, itemPendingIntent)

            val feedName = WidgetConfigActivity.getFeedName(context, appWidgetId)
            views.setTextViewText(
                R.id.widget_title,
                feedName ?: "Top stories"
            )
        }

        fun formatUpdatedTime(context: Context, appWidgetId: Int): String {
            val lastUpdated = StoriesRemoteViewsFactory.getLastUpdated(context, appWidgetId)
            if (lastUpdated == 0L) {
                return ""
            }
            return DateFormat.getTimeFormat(context).format(Date(lastUpdated))
        }
    }
}
