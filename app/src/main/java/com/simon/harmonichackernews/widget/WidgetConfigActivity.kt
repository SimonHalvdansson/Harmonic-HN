package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.simon.harmonichackernews.utils.ThemeUtils.setupTheme
import com.simon.harmonichackernews.utils.Utils
import com.simon.harmonichackernews.widget.WidgetConfigComposeHost.install

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTheme(this)

        // Set canceled result initially — if user backs out, widget won't be added
        setResult(RESULT_CANCELED)

        // Get widget ID from intent
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setupComposeUi()
    }

    private fun setupComposeUi() {
        install(
            this,
            getStoryCount(this, appWidgetId),
            WidgetConfigComposeHost.Listener(::confirmConfiguration)
        )
    }

    private fun confirmConfiguration(feedUrl: String, feedName: String, storyCount: Int) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_FEED_TYPE_PREFIX + appWidgetId, feedUrl)
            .putString(KEY_FEED_NAME_PREFIX + appWidgetId, feedName)
            .putInt(KEY_STORY_COUNT_PREFIX + appWidgetId, storyCount)
            .apply()

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

    companion object {
        private const val PREFS_NAME = "widget_config"
        private const val KEY_FEED_TYPE_PREFIX = "feed_type_"
        private const val KEY_FEED_NAME_PREFIX = "feed_name_"
        private const val KEY_STORY_COUNT_PREFIX = "story_count_"
        private const val STORY_COUNT_SMALL = 8
        private const val STORY_COUNT_MEDIUM = 16
        private const val STORY_COUNT_LARGE = 24
        private const val DEFAULT_STORY_COUNT = STORY_COUNT_MEDIUM

        fun getFeedUrl(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(
                KEY_FEED_TYPE_PREFIX + appWidgetId,
                Utils.URL_TOP
            ) ?: Utils.URL_TOP
        }

        fun getFeedName(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_FEED_NAME_PREFIX + appWidgetId, null)
        }

        fun getStoryCount(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val storyCount = prefs.getInt(KEY_STORY_COUNT_PREFIX + appWidgetId, DEFAULT_STORY_COUNT)
            return when (storyCount) {
                STORY_COUNT_SMALL, STORY_COUNT_MEDIUM, STORY_COUNT_LARGE -> storyCount
                10 -> STORY_COUNT_SMALL
                20 -> STORY_COUNT_MEDIUM
                30, 40 -> STORY_COUNT_LARGE
                else -> DEFAULT_STORY_COUNT
            }
        }

        fun getFetchStoryCount(context: Context, appWidgetId: Int): Int {
            return when (getStoryCount(context, appWidgetId)) {
                STORY_COUNT_SMALL -> 10
                STORY_COUNT_LARGE -> 28
                else -> 20
            }
        }

        fun clearPreferences(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit()
                .remove(KEY_FEED_TYPE_PREFIX + appWidgetId)
                .remove(KEY_FEED_NAME_PREFIX + appWidgetId)
                .remove(KEY_STORY_COUNT_PREFIX + appWidgetId)
                .apply()
        }
    }
}
