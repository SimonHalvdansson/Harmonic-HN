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
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.JSONParser.updateStoryWithHNJson
import com.simon.harmonichackernews.network.HttpRequest
import com.simon.harmonichackernews.network.NetworkComponent.httpClientInstance
import com.simon.harmonichackernews.utils.SettingsUtils.shouldIncludeTopLevelDomain
import com.simon.harmonichackernews.utils.SettingsUtils.shouldShowIndex
import com.simon.harmonichackernews.utils.Utils.getTimeAgo
import com.simon.harmonichackernews.utils.Utils.log
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import com.simon.harmonichackernews.serialization.JsonArray as JSONArray

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

            var freshStories = ArrayList<Story>()
            var storyFetchErrors = 0
            val visibleStoryCount = WidgetConfigActivity.getStoryCount(context, appWidgetId)
            val fetchStoryCount = WidgetConfigActivity.getFetchStoryCount(context, appWidgetId)

            val client = checkNotNull(httpClientInstance) {
                "Network client is unavailable"
            }
                .newBuilder()
                .readTimeoutMillis(CALL_TIMEOUT_SECONDS * 1_000L)
                .build()

            val feedUrl = WidgetConfigActivity.getFeedUrl(context, appWidgetId)
            log("WidgetFactory fetch ids widgetId=$appWidgetId url=$feedUrl")
            val idsRequest = HttpRequest.Builder()
                .url(feedUrl)
                .build()

            client.newCall(idsRequest).execute().use { idsResponse ->
                val idsBody = idsResponse.takeIf { it.isSuccessful }?.body?.string()
                if (idsBody == null) {
                    log(
                        "WidgetFactory ids request failed widgetId=$appWidgetId" +
                            " code=${idsResponse.code}"
                    )
                    postRefreshError()
                    terminalStatePosted = true
                    return
                }
                val idsArray = JSONArray(idsBody)
                val count = min(idsArray.length(), fetchStoryCount)

                log(
                    "WidgetFactory ids fetched widgetId=$appWidgetId" +
                        " totalIds=${idsArray.length()} visibleCount=$visibleStoryCount" +
                        " fetchTarget=$fetchStoryCount fetchCount=$count"
                )
                for (i in 0..<count) {
                    val elapsed = startedAt.elapsedNow()
                    if (elapsed > TOTAL_FETCH_TIMEOUT) {
                        log(
                            "WidgetFactory total timeout reached widgetId=$appWidgetId" +
                                " elapsedMs=${elapsed.inWholeMilliseconds}"
                        )
                        break
                    }

                    val storyId = idsArray.getInt(i)
                    val storyUrl =
                        "https://hacker-news.firebaseio.com/v0/item/$storyId.json"

                    val storyRequest = HttpRequest.Builder()
                        .url(storyUrl)
                        .build()

                    try {
                        client.newCall(storyRequest).execute().use { storyResponse ->
                            val storyBody = storyResponse
                                .takeIf { it.isSuccessful }
                                ?.body
                                ?.string()
                            if (storyBody != null) {
                                val story = Story()
                                story.id = storyId
                                if (updateStoryWithHNJson(storyBody, story, false)) {
                                    freshStories.add(story)
                                } else {
                                    storyFetchErrors++
                                }
                            } else {
                                storyFetchErrors++
                            }
                        }
                    } catch (e: Exception) {
                        storyFetchErrors++
                    }
                }
            }
            if (freshStories.isEmpty()) {
                log("WidgetFactory no stories fetched widgetId=$appWidgetId")
                postRefreshError()
                terminalStatePosted = true
                return
            }

            log(
                "WidgetFactory fetch complete widgetId=$appWidgetId" +
                    " stories=${freshStories.size} storyErrors=$storyFetchErrors" +
                    " elapsedMs=${startedAt.elapsedNow().inWholeMilliseconds}"
            )

            if (freshStories.size > visibleStoryCount) {
                freshStories = ArrayList(freshStories.subList(0, visibleStoryCount))
            }

            stories.clear()
            stories.addAll(freshStories)

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
        val showIndex = shouldShowIndex(context)
        views.setViewVisibility(R.id.widget_story_index, if (showIndex) View.VISIBLE else View.GONE)
        views.setTextViewText(R.id.widget_story_index, "${position + 1}.")

        // Title
        views.setTextViewText(R.id.widget_story_title, story.title)

        // Meta: score + domain + time
        var meta = story.score.toString() + (if (story.score == 1) " pt" else " pts")
        if (story.url != null && story.isLink) {
            try {
                val domain = story.getDisplayDomain(
                    shouldIncludeTopLevelDomain(context)
                )
                meta += " \u00B7 " + domain
            } catch (ignored: Exception) {
            }
        }
        meta += " \u00B7 " + getTimeAgo(story.time.toLong())
        views.setTextViewText(R.id.widget_story_meta, meta)

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
        private const val CALL_TIMEOUT_SECONDS: Long = 15
        private val TOTAL_FETCH_TIMEOUT = 60.seconds

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
