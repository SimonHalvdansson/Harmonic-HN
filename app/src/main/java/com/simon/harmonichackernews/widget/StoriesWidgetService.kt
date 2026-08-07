package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.widget.RemoteViewsService
import com.simon.harmonichackernews.utils.Utils.log

class StoriesWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        log("WidgetService onGetViewFactory widgetId=$appWidgetId")
        return StoriesRemoteViewsFactory(applicationContext, appWidgetId)
    }
}
