package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.utils.ThemeUtils.setupTheme
import com.simon.harmonichackernews.widget.WidgetConfigComposeHost.install

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val widgets by lazy { harmonicAppComposition.widgets }

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
            widgets.configuration(appWidgetId),
            WidgetConfigComposeHost.Listener(::confirmConfiguration)
        )
    }

    private fun confirmConfiguration(configuration: WidgetConfiguration) {
        widgets.save(appWidgetId, configuration)

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
        finish()
    }

}
