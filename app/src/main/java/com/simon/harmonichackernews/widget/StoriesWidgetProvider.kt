package com.simon.harmonichackernews.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.Utils

class StoriesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: android.content.Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray?
    ) {
        com.simon.harmonichackernews.utils.Utils.log("WidgetProvider onUpdate count=" + (if (appWidgetIds == null) 0 else appWidgetIds.size))
        for (appWidgetId in appWidgetIds!!) {
            StoriesWidgetProvider.Companion.refreshWidgetSilently(
                context,
                appWidgetManager,
                appWidgetId
            )
        }
    }

    override fun onReceive(context: android.content.Context, intent: Intent) {
        super.onReceive(context, intent)

        val action: kotlin.String? = intent.getAction()
        com.simon.harmonichackernews.utils.Utils.log("WidgetProvider onReceive action=" + action)

        if (StoriesWidgetProvider.Companion.ACTION_REFRESH == action) {
            val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetId: Int = intent.getIntExtra(
                StoriesWidgetProvider.Companion.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                com.simon.harmonichackernews.utils.Utils.log("WidgetProvider refresh request widgetId=" + appWidgetId)
                // Refresh only the tapped widget
                StoriesRemoteViewsFactory.setSkipFetch(context, appWidgetId, false)
                StoriesWidgetProvider.Companion.showRefreshing(
                    context,
                    appWidgetManager,
                    appWidgetId
                )
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetId,
                    R.id.widget_stories_list
                )
            } else {
                com.simon.harmonichackernews.utils.Utils.log("WidgetProvider refresh request missing widget id, fallback to all widgets")
                // Fallback: refresh all widgets
                val appWidgetIds: IntArray = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, StoriesWidgetProvider::class.java)
                )
                for (id in appWidgetIds) {
                    StoriesRemoteViewsFactory.setSkipFetch(context, id, false)
                    StoriesWidgetProvider.Companion.showRefreshing(context, appWidgetManager, id)
                }
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetIds,
                    R.id.widget_stories_list
                )
            }
        }
    }

    override fun onDeleted(context: android.content.Context, appWidgetIds: IntArray?) {
        com.simon.harmonichackernews.utils.Utils.log("WidgetProvider onDeleted count=" + (if (appWidgetIds == null) 0 else appWidgetIds.size))
        for (appWidgetId in appWidgetIds!!) {
            WidgetConfigActivity.clearPreferences(context, appWidgetId)
            StoriesRemoteViewsFactory.clearPreferences(context, appWidgetId)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.simon.harmonichackernews.widget.ACTION_REFRESH"
        private const val EXTRA_APPWIDGET_ID = "refresh_appwidget_id"

        fun updateWidget(
            context: android.content.Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            com.simon.harmonichackernews.utils.Utils.log("WidgetProvider updateWidget widgetId=" + appWidgetId + " start")
            StoriesRemoteViewsFactory.setSkipFetch(context, appWidgetId, false)
            StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, true)
            val views: RemoteViews = RemoteViews(context.getPackageName(), R.layout.widget_stories)
            StoriesWidgetProvider.Companion.bindWidgetCommonViews(context, views, appWidgetId)

            // Show refreshing state initially
            views.setViewVisibility(R.id.widget_refresh_button, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_refresh_progress, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_updated_text, "")

            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Trigger data refresh — factory will call updateRefreshDone() when finished
            com.simon.harmonichackernews.utils.Utils.log("WidgetProvider updateWidget widgetId=" + appWidgetId + " notify data changed")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stories_list)
        }

        private fun refreshWidgetSilently(
            context: android.content.Context, appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val refreshing = StoriesRemoteViewsFactory.isRefreshing(context, appWidgetId)
            com.simon.harmonichackernews.utils.Utils.log(
                ("WidgetProvider refreshWidgetSilently widgetId=" + appWidgetId
                        + " refreshing=" + refreshing)
            )
            StoriesRemoteViewsFactory.setSkipFetch(context, appWidgetId, false)

            val views: RemoteViews = RemoteViews(context.getPackageName(), R.layout.widget_stories)
            StoriesWidgetProvider.Companion.bindWidgetCommonViews(context, views, appWidgetId)
            views.setViewVisibility(
                R.id.widget_refresh_button,
                if (refreshing) android.view.View.GONE else android.view.View.VISIBLE
            )
            views.setViewVisibility(
                R.id.widget_refresh_progress,
                if (refreshing) android.view.View.VISIBLE else android.view.View.GONE
            )
            views.setTextViewText(
                R.id.widget_updated_text,
                StoriesWidgetProvider.Companion.formatUpdatedTime(context, appWidgetId)
            )
            if (refreshing) {
                views.setTextViewText(R.id.widget_empty_text, "Loading stories\u2026")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            com.simon.harmonichackernews.utils.Utils.log("WidgetProvider refreshWidgetSilently widgetId=" + appWidgetId + " notify data changed")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stories_list)
        }

        private fun showRefreshing(
            context: android.content.Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            com.simon.harmonichackernews.utils.Utils.log("WidgetProvider showRefreshing widgetId=" + appWidgetId)
            StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, true)
            val views: RemoteViews = RemoteViews(context.getPackageName(), R.layout.widget_stories)
            StoriesWidgetProvider.Companion.bindWidgetCommonViews(context, views, appWidgetId)
            views.setViewVisibility(R.id.widget_refresh_button, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_refresh_progress, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_updated_text, "")
            views.setTextViewText(R.id.widget_empty_text, "Loading stories\u2026")
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }

        fun updateRefreshDone(context: android.content.Context, appWidgetId: Int) {
            com.simon.harmonichackernews.utils.Utils.log("WidgetProvider updateRefreshDone widgetId=" + appWidgetId)
            StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, false)
            val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
            val views: RemoteViews = RemoteViews(context.getPackageName(), R.layout.widget_stories)
            StoriesWidgetProvider.Companion.bindWidgetCommonViews(context, views, appWidgetId)
            views.setViewVisibility(R.id.widget_refresh_button, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_refresh_progress, android.view.View.GONE)
            views.setTextViewText(
                R.id.widget_updated_text,
                StoriesWidgetProvider.Companion.formatUpdatedTime(context, appWidgetId)
            )
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }

        fun updateRefreshError(context: android.content.Context, appWidgetId: Int) {
            com.simon.harmonichackernews.utils.Utils.log("WidgetProvider updateRefreshError widgetId=" + appWidgetId)
            StoriesRemoteViewsFactory.setRefreshing(context, appWidgetId, false)
            val appWidgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)
            val views: RemoteViews = RemoteViews(context.getPackageName(), R.layout.widget_stories)
            StoriesWidgetProvider.Companion.bindWidgetCommonViews(context, views, appWidgetId)
            views.setViewVisibility(R.id.widget_refresh_button, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_refresh_progress, android.view.View.GONE)
            views.setTextViewText(R.id.widget_empty_text, "Couldn\u2019t load stories")
            // Keep previous timestamp if any
            views.setTextViewText(
                R.id.widget_updated_text,
                StoriesWidgetProvider.Companion.formatUpdatedTime(context, appWidgetId)
            )
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }

        private fun bindWidgetCommonViews(
            context: android.content.Context,
            views: RemoteViews,
            appWidgetId: Int
        ) {
            // Keep collection adapter bindings present on every update so launcher-side refresh
            // notifications always resolve to a known collection view.
            val serviceIntent: Intent = Intent(context, StoriesWidgetService::class.java)
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            serviceIntent.setData(android.net.Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)))
            views.setRemoteAdapter(R.id.widget_stories_list, serviceIntent)
            views.setEmptyView(R.id.widget_stories_list, R.id.widget_empty_text)

            val mainIntent: Intent = Intent(context, MainActivity::class.java)
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val mainPendingIntent: PendingIntent? = PendingIntent.getActivity(
                context, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, mainPendingIntent)

            val refreshIntent: Intent = Intent(context, StoriesWidgetProvider::class.java)
            refreshIntent.setAction(StoriesWidgetProvider.Companion.ACTION_REFRESH)
            refreshIntent.putExtra(StoriesWidgetProvider.Companion.EXTRA_APPWIDGET_ID, appWidgetId)
            val refreshPendingIntent: PendingIntent? = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh_button, refreshPendingIntent)

            val itemIntent: Intent = Intent(context, MainActivity::class.java)
            itemIntent.setFlags(
                (Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            val itemPendingIntent: PendingIntent? = PendingIntent.getActivity(
                context, 1, itemIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_stories_list, itemPendingIntent)

            val feedName = WidgetConfigActivity.getFeedName(context, appWidgetId)
            views.setTextViewText(
                R.id.widget_title,
                if (feedName != null) feedName else "Top stories"
            )
        }

        fun formatUpdatedTime(context: android.content.Context?, appWidgetId: Int): kotlin.String {
            val lastUpdated = StoriesRemoteViewsFactory.getLastUpdated(context!!, appWidgetId)
            if (lastUpdated == 0L) {
                return ""
            }
            return android.text.format.DateFormat.getTimeFormat(context)
                .format(java.util.Date(lastUpdated))
        }
    }
}
