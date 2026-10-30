package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.runAfterStartup
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.utils.AndroidActivityTheme.setupTheme
import com.simon.harmonichackernews.widget.AndroidWidgetConfigHost.install

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val widgets by lazy { harmonicAppComposition.widgets }
    private var preferencesReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        val app = application as HarmonicApplication
        runAfterStartup(
            prepare = { app.awaitWidgetPreferences() },
            retryStartup = app::retryStartup,
        ) {
            preferencesReady = true
            setupWidgetTheme()
            setupComposeUi()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!preferencesReady) return
        setupWidgetTheme()
        harmonicAppComposition.appearance.refreshSelection()
    }

    private fun setupWidgetTheme() {
        setupTheme(this)
        // Compose draws the app-owned preview backdrop beneath the status bar.
        window.statusBarColor = Color.TRANSPARENT
    }

    private fun setupComposeUi() {
        install(
            this,
            widgets.configuration(appWidgetId, WidgetConfiguration.fromStoryPreferences(harmonicAppComposition.userSettings.story)),
            AndroidWidgetConfigHost.Listener(::confirmConfiguration),
        )
    }

    private fun confirmConfiguration(configuration: WidgetConfiguration) {
        widgets.save(appWidgetId, configuration)
        WidgetRefreshWorker.enqueue(this, appWidgetId, source = "configuration")

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        // Leave activity closing and predictive back to Android.
        finish()
    }

}
