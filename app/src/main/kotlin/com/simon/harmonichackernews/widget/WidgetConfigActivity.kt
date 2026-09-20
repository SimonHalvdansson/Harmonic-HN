package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.ui.theme.harmonicColors
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.ThemeUtils.setupTheme
import com.simon.harmonichackernews.widget.WidgetConfigComposeHost.install

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val widgets by lazy { harmonicAppComposition.widgets }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupTheme(this)
        window.setBackgroundDrawable(ColorDrawable(harmonicColors(this).settingsPageBackground.toArgb()))

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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupTheme(this)
        harmonicAppComposition.appearance.refreshSelection()
        window.setBackgroundDrawable(ColorDrawable(harmonicColors(this).settingsPageBackground.toArgb()))
    }

    private fun setupComposeUi() {
        install(
            this,
            widgets.configuration(appWidgetId, WidgetConfiguration.fromStoryPreferences(harmonicAppComposition.userSettings.story)),
            WidgetConfigComposeHost.Listener(::confirmConfiguration)
        )
    }

    private fun confirmConfiguration(configuration: WidgetConfiguration) {
        widgets.save(appWidgetId, configuration)
        WidgetRefreshWorker.enqueue(this, appWidgetId)

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        // Launchers can supply a no-animation configuration launch. Explicitly animate the
        // successful close while leaving gesture-driven cancellation to predictive back.
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.widget_config_reveal, R.anim.widget_config_close)
        }
        finish()
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.widget_config_reveal, R.anim.widget_config_close)
        }
    }

}
