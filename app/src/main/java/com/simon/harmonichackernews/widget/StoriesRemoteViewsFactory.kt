package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.simon.harmonichackernews.CommentsContract
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.toBundle
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.network.WidgetFeedRequest
import com.simon.harmonichackernews.network.WidgetFeedResult
import com.simon.harmonichackernews.network.WidgetFeedUseCase
import com.simon.harmonichackernews.network.widgetStoryTypeForUrl
import com.simon.harmonichackernews.presentation.WidgetStoryFormatter
import com.simon.harmonichackernews.utils.HarmonicLog.debug as log
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class StoriesRemoteViewsFactory(private val context: Context, private val appWidgetId: Int) :
    RemoteViewsFactory {
    private val stories = ArrayList<Story>()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        log("WidgetFactory onCreate widgetId=$appWidgetId")
    }

    override fun onDataSetChanged() {
        val startedAt = TimeSource.Monotonic.markNow()
        var terminalStatePosted = false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val skipKey = KEY_SKIP_FETCH_PREFIX + appWidgetId
        val skipFetch = prefs.getBoolean(skipKey, false)
        val refreshing = isRefreshing(context, appWidgetId)

        log(
            "WidgetFactory onDataSetChanged start widgetId=$appWidgetId" +
                " skipFetch=$skipFetch refreshing=$refreshing inMemoryStories=${stories.size}"
        )

        try {
            if (skipFetch && stories.isNotEmpty()) {
                if (refreshing) {
                    log("WidgetFactory skip fetch and reconcile refresh widgetId=$appWidgetId")
                    postRefreshDone()
                    terminalStatePosted = true
                } else {
                    log("WidgetFactory skip fetch widgetId=$appWidgetId")
                }
                return
            }

            val visibleStoryCount = WidgetConfigActivity.getStoryCount(context, appWidgetId)
            val fetchStoryCount = WidgetConfigActivity.getFetchStoryCount(context, appWidgetId)
            val feedUrl = WidgetConfigActivity.getFeedUrl(context, appWidgetId)
            log("WidgetFactory fetch ids widgetId=$appWidgetId url=$feedUrl")
            val result = runBlocking(Dispatchers.IO) {
                WidgetFeedUseCase(
                    AndroidAppComposition.get(context).network.hackerNewsRepository,
                ).load(
                    WidgetFeedRequest(
                        storyType = widgetStoryTypeForUrl(feedUrl),
                        fetchCount = fetchStoryCount,
                        visibleCount = visibleStoryCount,
                    ),
                )
            }
            val loaded = result as? WidgetFeedResult.Loaded
            if (loaded == null) {
                log("WidgetFactory no stories fetched widgetId=$appWidgetId")
                postRefreshError()
                terminalStatePosted = true
                return
            }

            log(
                "WidgetFactory fetch complete widgetId=$appWidgetId" +
                    " stories=${loaded.stories.size}" +
                    " available=${loaded.availableStoryCount}" +
                    " storyErrors=${loaded.failedStoryCount}" +
                    " timedOut=${loaded.timedOut}" +
                    " elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
            )

            stories.clear()
            stories.addAll(loaded.stories)

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_UPDATED_PREFIX + appWidgetId, System.currentTimeMillis())
                .apply()

            setSkipFetch(context, appWidgetId, true)
            postRefreshDone()
            terminalStatePosted = true
        } catch (t: Throwable) {
            log("WidgetFactory onDataSetChanged failed widgetId=$appWidgetId error=$t")
            postRefreshError()
            terminalStatePosted = true
        } finally {
            val refreshingNow = isRefreshing(context, appWidgetId)
            log(
                "WidgetFactory onDataSetChanged end widgetId=$appWidgetId" +
                    " terminalPosted=$terminalStatePosted refreshingNow=$refreshingNow" +
                    " elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
            )
            if (!terminalStatePosted && refreshingNow) {
                log("WidgetFactory forcing refresh error widgetId=$appWidgetId")
                postRefreshError()
            }
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
        val storyPreferences = AndroidAppComposition.get(context).userSettings.story
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

    companion object {
        private const val PREFS_NAME = "widget_stories_cache"
        private const val KEY_LAST_UPDATED_PREFIX = "last_updated_"
        private const val KEY_SKIP_FETCH_PREFIX = "skip_fetch_"
        private const val KEY_REFRESHING_PREFIX = "refreshing_"
        fun setSkipFetch(context: Context, appWidgetId: Int, skip: Boolean) {
            log("WidgetFactory setSkipFetch widgetId=$appWidgetId skip=$skip")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_SKIP_FETCH_PREFIX + appWidgetId, skip).apply()
        }

        fun setSkipFetchAll(context: Context, skip: Boolean) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(
                ComponentName(context, StoriesWidgetProvider::class.java)
            )
            log("WidgetFactory setSkipFetchAll count=${ids.size} skip=$skip")
            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            for (id in ids) {
                editor.putBoolean(KEY_SKIP_FETCH_PREFIX + id, skip)
            }
            editor.apply()
        }

        fun setRefreshing(context: Context, appWidgetId: Int, refreshing: Boolean) {
            log("WidgetFactory setRefreshing widgetId=$appWidgetId refreshing=$refreshing")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_REFRESHING_PREFIX + appWidgetId, refreshing).apply()
        }

        fun isRefreshing(context: Context, appWidgetId: Int): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_REFRESHING_PREFIX + appWidgetId, false)

        fun getLastUpdated(context: Context, appWidgetId: Int): Long =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_UPDATED_PREFIX + appWidgetId, 0)

        fun clearPreferences(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(KEY_LAST_UPDATED_PREFIX + appWidgetId)
                .remove(KEY_SKIP_FETCH_PREFIX + appWidgetId)
                .remove(KEY_REFRESHING_PREFIX + appWidgetId)
                .apply()
        }
    }
}
