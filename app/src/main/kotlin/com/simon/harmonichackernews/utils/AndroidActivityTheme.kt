package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.ThemeSelection
import com.simon.harmonichackernews.settings.ColorSchemePreferences
import com.simon.harmonichackernews.ui.theme.harmonicThemePalette

object AndroidActivityTheme {
    /**
     * Default color for nav bar's light scrim.
     * 
     * 
     * Copied from [EdgeToEdge.DefaultLightScrim] which was copied from Android sources:
     * [source](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/com/android/internal/policy/DecorView.java;drc=6ef0f022c333385dba2c294e35b8de544455bf19;l=142)
     */
    private val defaultLightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)

    /**
     * Default color for nav bar's dark scrim.
     * 
     * 
     * Copied from [EdgeToEdge.DefaultDarkScrim] which was copied from Android sources:
     * [source 1](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/res/res/color/system_bar_background_semi_transparent.xml),
     * [source 2](https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/res/remote_color_resources_res/values/colors.xml;l=67)
     */
    private val defaultDarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    fun setupTheme(activity: ComponentActivity) {
        val selection = activity.harmonicAppComposition.appearance.selection()
        activity.setTheme(themeResource(selection))

        val window = activity.getWindow()
        val background = harmonicThemePalette(activity, selection).colorScheme.surface.toArgb()
        window.setBackgroundDrawable(ColorDrawable(background))
        window.statusBarColor = background
        val insetsController = WindowCompat.getInsetsController(window, window.getDecorView())
        insetsController.setAppearanceLightStatusBars(!selection.dark)
        insetsController.setAppearanceLightNavigationBars(!selection.dark)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // All themes have nav bar color set to transparent so on API 29+ the system will draw
            // translucent scrim for us. However on older versions we need to set correct nav bar
            // color manually.
            val navBarColor = if (selection.dark) defaultDarkScrim else defaultLightScrim
            window.setNavigationBarColor(navBarColor)
        }

        if (activity.harmonicAppComposition.userSettings.general.transparentStatusBar) {
            window.setStatusBarColor(Color.TRANSPARENT)
        }
    }

    fun isDarkMode(ctx: Context): Boolean = ctx.harmonicAppComposition.appearance.selection().dark

    fun isLightMode(ctx: Context): Boolean = !isDarkMode(ctx)

    fun uiModeNight(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    @ColorInt
    fun getPageBackgroundColor(ctx: Context): Int {
        val selection = ctx.harmonicAppComposition.appearance.selection()
        return harmonicThemePalette(ctx, selection).colorScheme.surface.toArgb()
    }

    fun themeResource(selection: ThemeSelection): Int = materialTheme(
        dynamic = selection.colorScheme == ColorSchemePreferences.DYNAMIC,
        dark = selection.dark,
    )

    private fun materialTheme(dynamic: Boolean, dark: Boolean): Int {
        val useDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return when {
            useDynamic && dark -> R.style.AppThemeMaterialDark
            useDynamic -> R.style.AppThemeMaterialLight
            dark -> R.style.AppThemeMaterialFixedDark
            else -> R.style.AppThemeMaterialFixedLight
        }
    }
}
