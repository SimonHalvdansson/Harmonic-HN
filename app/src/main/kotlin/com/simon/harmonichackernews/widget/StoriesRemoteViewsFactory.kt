package com.simon.harmonichackernews.widget

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.simon.harmonichackernews.CommentsContract
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.toBundle
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.network.WidgetRefreshResult
import com.simon.harmonichackernews.presentation.WidgetStoryFormatter
import com.simon.harmonichackernews.utils.HarmonicLog.debug as log
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class StoriesRemoteViewsFactory(private val context: Context, private val appWidgetId: Int) :
    RemoteViewsFactory {
    private val stories = ArrayList<Story>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val app = context.harmonicAppComposition
    private val widgets = app.widgets

    override fun onCreate() {
        log("WidgetFactory onCreate widgetId=$appWidgetId")
    }

    override fun onDataSetChanged() {
        val startedAt = TimeSource.Monotonic.markNow()
        val runtime = widgets.runtime(appWidgetId)
        val skipFetch = runtime.skipFetch
        val refreshing = runtime.refreshing

        log(
            "WidgetFactory onDataSetChanged start widgetId=$appWidgetId" +
                " skipFetch=$skipFetch refreshing=$refreshing inMemoryStories=${stories.size}"
        )

        try {
            val configuration = widgets.configuration(appWidgetId)
            log("WidgetFactory fetch ids widgetId=$appWidgetId url=${configuration.feedUrl}")
            val result = runBlocking(Dispatchers.IO) {
                app.widgetRefresh.refresh(appWidgetId, stories.isNotEmpty())
            }
            when (result) {
                WidgetRefreshResult.UseExisting -> {
                    log("WidgetFactory reused existing stories widgetId=$appWidgetId")
                    postRefreshDone()
                }
                is WidgetRefreshResult.Loaded -> {
                    log(
                        "WidgetFactory fetch complete widgetId=$appWidgetId" +
                            " stories=${result.stories.size}" +
                            " available=${result.availableStoryCount}" +
                            " storyErrors=${result.failedStoryCount}" +
                            " timedOut=${result.timedOut}" +
                            " elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
                    )
                    stories.clear()
                    stories.addAll(result.stories)
                    postRefreshDone()
                }
                is WidgetRefreshResult.Failed -> {
                    log("WidgetFactory refresh failed widgetId=$appWidgetId error=${result.cause}")
                    postRefreshError()
                }
            }
        } catch (t: Throwable) {
            log("WidgetFactory host failure widgetId=$appWidgetId error=$t")
            postRefreshError()
        }
    }

    private fun postRefreshDone() {
        val widgetId = appWidgetId
        log("WidgetFactory postRefreshDone queued widgetId=$widgetId")
        mainHandler.post {
            log("WidgetFactory postRefreshDone run widgetId=$widgetId")
            StoriesWidgetProvider.updateRefreshDone(context, widgetId)
        }
    }

    private fun postRefreshError() {
        val widgetId = appWidgetId
        log("WidgetFactory postRefreshError queued widgetId=$widgetId")
        mainHandler.post {
            log("WidgetFactory postRefreshError run widgetId=$widgetId")
            StoriesWidgetProvider.updateRefreshError(context, widgetId)
        }
    }

    override fun onDestroy() {
        log("WidgetFactory onDestroy widgetId=$appWidgetId stories=${stories.size}")
        stories.clear()
    }

    override fun getCount(): Int {
        return stories.size
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= stories.size) {
            return getLoadingView()
        }

        val story = stories[position]

        val views = RemoteViews(context.packageName, R.layout.widget_story_item)

        // Index
        val storyPreferences = app.userSettings.story
        val showIndex = storyPreferences.showIndex
        views.setViewVisibility(R.id.widget_story_index, if (showIndex) View.VISIBLE else View.GONE)
        val text = WidgetStoryFormatter.format(
            story = story.toSnapshot(),
            position = position,
            includeTopLevelDomain = storyPreferences.includeTopLevelDomain,
            nowMillis = System.currentTimeMillis(),
        )
        views.setTextViewText(R.id.widget_story_index, text.index)
        views.setTextViewText(R.id.widget_story_title, text.title)
        views.setTextViewText(R.id.widget_story_meta, text.metadata)

        // Fill-in intent for item click -> MainActivity's Compose comments destination.
        val fillInIntent = Intent()
        fillInIntent.putExtras(story.toBundle())
        fillInIntent.putExtra(CommentsContract.EXTRA_SHOW_WEBSITE, story.isLink)
        views.setOnClickFillInIntent(R.id.widget_story_item_container, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews {
        return RemoteViews(context.packageName, R.layout.widget_story_item_loading)
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        if (position < stories.size) {
            return stories[position].id.toLong()
        }
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

}
