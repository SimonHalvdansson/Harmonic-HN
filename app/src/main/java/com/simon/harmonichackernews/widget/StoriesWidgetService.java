package com.simon.harmonichackernews.widget;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.widget.RemoteViewsService;

import com.simon.harmonichackernews.utils.Utils;

public class StoriesWidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        Utils.log("WidgetService onGetViewFactory widgetId=" + appWidgetId);
        return new StoriesRemoteViewsFactory(getApplicationContext(), appWidgetId);
    }
}
