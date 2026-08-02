package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.simon.harmonichackernews.utils.ThemeUtils
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
        val intent = getIntent()
        val extras = intent.getExtras()
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

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
            WidgetConfigComposeHost.Listener { feedUrl: String, feedName: String, storyCount: Int ->
                this.confirmConfiguration(
                    feedUrl,
                    feedName,
                    storyCount
                )
            })
    }

    private fun confirmConfiguration(feedUrl: String?, feedName: String?, storyCount: Int) {
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
        private val DEFAULT_STORY_COUNT: Int = STORY_COUNT_MEDIUM

        fun getFeedUrl(context: Context, appWidgetId: Int): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(
                WidgetConfigActivity.Companion.KEY_FEED_TYPE_PREFIX + appWidgetId,
                com.simon.harmonichackernews.utils.Utils.URL_TOP
            )!!
        }

        fun getFeedName(context: Context, appWidgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_FEED_NAME_PREFIX + appWidgetId, null)
        }

        fun getStoryCount(context: Context, appWidgetId: Int): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val storyCount = prefs.getInt(KEY_STORY_COUNT_PREFIX + appWidgetId, DEFAULT_STORY_COUNT)
            if (storyCount == STORY_COUNT_SMALL || storyCount == STORY_COUNT_MEDIUM || storyCount == STORY_COUNT_LARGE) {
                return storyCount
            }
            if (storyCount == 10) {
                return STORY_COUNT_SMALL
            }
            if (storyCount == 20) {
                return STORY_COUNT_MEDIUM
            }
            if (storyCount == 30 || storyCount == 40) {
                return STORY_COUNT_LARGE
            }
            return DEFAULT_STORY_COUNT
        }

        fun getFetchStoryCount(context: Context, appWidgetId: Int): Int {
            val visibleStoryCount: Int = getStoryCount(context, appWidgetId)
            if (visibleStoryCount == STORY_COUNT_SMALL) {
                return 10
            }
            if (visibleStoryCount == STORY_COUNT_LARGE) {
                return 28
            }
            return 20
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
