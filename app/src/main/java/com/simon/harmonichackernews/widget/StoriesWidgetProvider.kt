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
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.HarmonicLog

class StoriesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        HarmonicLog.debug("WidgetProvider onUpdate count=${appWidgetIds.size}")
        for (appWidgetId in appWidgetIds) {
            refreshWidgetSilently(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val action = intent.action
        HarmonicLog.debug("WidgetProvider onReceive action=$action")

        if (ACTION_REFRESH == action) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetId = intent.getIntExtra(
                EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                HarmonicLog.debug("WidgetProvider refresh request widgetId=$appWidgetId")
                // Refresh only the tapped widget
                widgetService(context).setSkipFetch(appWidgetId, false)
                showRefreshing(context, appWidgetManager, appWidgetId)
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetId,
                    R.id.widget_stories_list
                )
            } else {
                HarmonicLog.debug("WidgetProvider refresh request missing widget id, fallback to all widgets")
                // Fallback: refresh all widgets
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, StoriesWidgetProvider::class.java)
                )
                for (id in appWidgetIds) {
                    widgetService(context).setSkipFetch(id, false)
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
        HarmonicLog.debug("WidgetProvider onDeleted count=${appWidgetIds.size}")
        for (appWidgetId in appWidgetIds) {
            widgetService(context).clear(appWidgetId)
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
            HarmonicLog.debug("WidgetProvider updateWidget widgetId=$appWidgetId start")
            widgetService(context).setSkipFetch(appWidgetId, false)
            widgetService(context).setRefreshing(appWidgetId, true)
            val views = createWidgetViews(context, appWidgetId)

            // Show refreshing state initially
            views.setRefreshIndicator(refreshing = true)
            views.setTextViewText(R.id.widget_updated_text, "")

            appWidgetManager.updateAppWidget(appWidgetId, views)

            // Trigger data refresh — factory will call updateRefreshDone() when finished
            HarmonicLog.debug("WidgetProvider updateWidget widgetId=$appWidgetId notify data changed")
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stories_list)
        }

        private fun refreshWidgetSilently(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val refreshing = widgetService(context).runtime(appWidgetId).refreshing
            HarmonicLog.debug(
                "WidgetProvider refreshWidgetSilently widgetId=$appWidgetId refreshing=$refreshing"
            )
            widgetService(context).setSkipFetch(appWidgetId, false)

            val views = createWidgetViews(context, appWidgetId)
            views.setRefreshIndicator(refreshing)
            views.setTextViewText(
                R.id.widget_updated_text,
                formatUpdatedTime(context, appWidgetId)
            )
            if (refreshing) {
                views.setTextViewText(
                    R.id.widget_empty_text,
                    context.getString(R.string.widget_loading_stories),
                )
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
            HarmonicLog.debug(
                "WidgetProvider refreshWidgetSilently widgetId=$appWidgetId notify data changed"
            )
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_stories_list)
        }

        private fun showRefreshing(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            HarmonicLog.debug("WidgetProvider showRefreshing widgetId=$appWidgetId")
            widgetService(context).setRefreshing(appWidgetId, true)
            val views = createWidgetViews(context, appWidgetId)
            views.setRefreshIndicator(refreshing = true)
            views.setTextViewText(R.id.widget_updated_text, "")
            views.setTextViewText(
                R.id.widget_empty_text,
                context.getString(R.string.widget_loading_stories),
            )
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }

        fun updateRefreshDone(context: Context, appWidgetId: Int) {
            HarmonicLog.debug("WidgetProvider updateRefreshDone widgetId=$appWidgetId")
            widgetService(context).setRefreshing(appWidgetId, false)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val views = createWidgetViews(context, appWidgetId)
            views.setRefreshIndicator(refreshing = false)
            views.setTextViewText(
                R.id.widget_updated_text,
                formatUpdatedTime(context, appWidgetId)
            )
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }

        fun updateRefreshError(context: Context, appWidgetId: Int) {
            HarmonicLog.debug("WidgetProvider updateRefreshError widgetId=$appWidgetId")
            widgetService(context).setRefreshing(appWidgetId, false)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val views = createWidgetViews(context, appWidgetId)
            views.setRefreshIndicator(refreshing = false)
            views.setTextViewText(
                R.id.widget_empty_text,
                context.getString(R.string.widget_could_not_load_stories),
            )
            // Keep previous timestamp if any
            views.setTextViewText(
                R.id.widget_updated_text,
                formatUpdatedTime(context, appWidgetId)
            )
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
        }

        private fun createWidgetViews(context: Context, appWidgetId: Int): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_stories).also { views ->
                bindWidgetCommonViews(context, views, appWidgetId)
            }

        private fun RemoteViews.setRefreshIndicator(refreshing: Boolean) {
            setViewVisibility(
                R.id.widget_refresh_button,
                if (refreshing) View.GONE else View.VISIBLE,
            )
            setViewVisibility(
                R.id.widget_refresh_progress,
                if (refreshing) View.VISIBLE else View.GONE,
            )
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

            val feedName = widgetService(context).configuration(appWidgetId).feedName
            views.setTextViewText(
                R.id.widget_title,
                feedName ?: context.getString(R.string.widget_top_stories)
            )
        }

        fun formatUpdatedTime(context: Context, appWidgetId: Int): String {
            val lastUpdated = widgetService(context).runtime(appWidgetId).lastUpdatedMillis
            if (lastUpdated == 0L) {
                return ""
            }
            return context.harmonicAppComposition.platform.timeFormatting.time(lastUpdated)
        }
    }
}

private fun widgetService(context: Context) = context.harmonicAppComposition.widgets

fun setSkipFetchForAllWidgets(context: Context, skip: Boolean) {
    val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, StoriesWidgetProvider::class.java),
    )
    val widgets = widgetService(context)
    ids.forEach { widgets.setSkipFetch(it, skip) }
}
