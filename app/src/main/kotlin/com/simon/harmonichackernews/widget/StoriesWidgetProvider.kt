package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.work.WorkManager
import com.simon.harmonichackernews.harmonicAppComposition

/** Keep the provider component name so existing home-screen widgets migrate in place. */
class StoriesWidgetProvider : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = StoriesGlanceWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        appWidgetIds.forEach { WidgetRefreshWorker.enqueue(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) refreshStoryWidgets(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        // Recompose the one scalable layout at its new width without restarting network work.
        WidgetRefreshWorker.enqueueResize(context, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { id ->
            WorkManager.getInstance(context).cancelUniqueWork(WidgetRefreshWorker.workName(id))
            WorkManager.getInstance(context).cancelUniqueWork(WidgetRefreshWorker.resizeWorkName(id))
            context.harmonicAppComposition.widgets.clear(id)
            WidgetRefreshWorker.imageDirectory(context, id).deleteRecursively()
        }
    }
}

fun refreshStoryWidgets(context: Context, reloadStories: Boolean = true) {
    AppWidgetManager.getInstance(context).getAppWidgetIds(
        ComponentName(context, StoriesWidgetProvider::class.java),
    ).forEach {
        if (reloadStories) WidgetRefreshWorker.enqueue(context, it)
        else WidgetRefreshWorker.enqueueResize(context, it)
    }
}
