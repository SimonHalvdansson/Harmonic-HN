package com.simon.harmonichackernews.widget

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.view.WindowManager
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.network.WidgetConfiguration
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.AndroidActivityTheme.setupTheme
import com.simon.harmonichackernews.widget.AndroidWidgetConfigHost.install

class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val widgets by lazy { harmonicAppComposition.widgets }
    private val wallpaperManager by lazy { WallpaperManager.getInstance(this) }
    private var wallpaperColorsListener: WallpaperManager.OnColorsChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setupTheme(this)
        showWallpaper()

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
        showWallpaper()
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
                if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                    updateWallpaperStatusBarIcons(colors)
                }
            }
            wallpaperColorsListener = listener
            wallpaperManager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        }
        refreshWallpaperStatusBarIcons()
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            wallpaperColorsListener?.let(wallpaperManager::removeOnColorsChangedListener)
            wallpaperColorsListener = null
        }
        super.onStop()
    }

    private fun showWallpaper() {
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        refreshWallpaperStatusBarIcons()
    }

    private fun refreshWallpaperStatusBarIcons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            updateWallpaperStatusBarIcons(wallpaperManager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM))
        } else {
            // Android 8.0 does not expose wallpaper colors. Use the usual light launcher icons.
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun updateWallpaperStatusBarIcons(colors: WallpaperColors?) {
        val useDarkIcons = when {
            colors == null -> false
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
            else -> ColorUtils.calculateLuminance(colors.primaryColor.toArgb()) > 0.5
        }
        // Only the status bar overlays wallpaper; navigation overlays the themed settings panel.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = useDarkIcons
    }

    private fun setupComposeUi() {
        install(
            this,
            widgets.configuration(appWidgetId, WidgetConfiguration.fromStoryPreferences(harmonicAppComposition.userSettings.story)),
            AndroidWidgetConfigHost.Listener(::confirmConfiguration)
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
