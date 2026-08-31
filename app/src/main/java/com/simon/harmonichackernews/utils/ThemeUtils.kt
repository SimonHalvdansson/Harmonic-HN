package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.ThemePreferences

object ThemeUtils {
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
        val theme = getPreferredTheme(activity)
        when (theme) {
            ThemePreferences.DEFAULT -> setMaterialAutoTheme(activity, dynamic = true)
            ThemePreferences.MATERIAL_FIXED_AUTO -> setMaterialAutoTheme(activity, dynamic = false)

            "darklight_daynight" -> activity.setTheme(R.style.AppThemeDarkLightDayNight)
            "amoledwhite_daynight" -> activity.setTheme(R.style.AppThemeAmoledWhiteDayNight)
            "material_dark" -> activity.setTheme(materialTheme(dynamic = true, dark = true))
            ThemePreferences.MATERIAL_FIXED_DARK ->
                activity.setTheme(materialTheme(dynamic = false, dark = true))
            "amoled" -> activity.setTheme(R.style.AppThemeAmoledDark)
            "hacker" -> activity.setTheme(R.style.AppThemeHacker)
            "gray" -> activity.setTheme(R.style.AppThemeGray)
            "light" -> activity.setTheme(R.style.AppThemeLight)
            "hacker_news" -> activity.setTheme(R.style.AppThemeHackerNews)
            "material_light" -> activity.setTheme(materialTheme(dynamic = true, dark = false))
            ThemePreferences.MATERIAL_FIXED_LIGHT ->
                activity.setTheme(materialTheme(dynamic = false, dark = false))
            "white" -> activity.setTheme(R.style.AppThemeWhite)
            "dark" -> activity.setTheme(R.style.AppTheme)
        }

        val window = activity.getWindow()
        val insetsController = WindowCompat.getInsetsController(window, window.getDecorView())
        insetsController.setAppearanceLightStatusBars(!isDarkMode(activity))
        insetsController.setAppearanceLightNavigationBars(!isDarkMode(activity))

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // All themes have nav bar color set to transparent so on API 29+ the system will draw
            // translucent scrim for us. However on older versions we need to set correct nav bar
            // color manually.
            val navBarColor = if (isDarkMode(activity)) defaultDarkScrim else defaultLightScrim
            window.setNavigationBarColor(navBarColor)
        }

        if (activity.harmonicAppComposition.userSettings.general.transparentStatusBar) {
            window.setStatusBarColor(Color.TRANSPARENT)
        }
    }

    fun isDarkMode(ctx: Context, theme: String?): Boolean {
        return if (ThemePreferences.isAutomatic(theme)) uiModeNight(ctx)
        else ThemePreferences.isDark(theme)
    }

    fun isDarkMode(ctx: Context): Boolean = isDarkMode(ctx, getPreferredTheme(ctx))

    fun isLightMode(ctx: Context): Boolean = !isDarkMode(ctx)

    fun uiModeNight(ctx: Context): Boolean =
        (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun getBackgroundColorResource(ctx: Context): Int = when (getPreferredTheme(ctx)) {
        "amoled" -> android.R.color.black
        "hacker" -> R.color.hackerBackground
        "gray" -> R.color.grayBackground
        "light" -> R.color.lightBackground
        "hacker_news" -> R.color.hackerNewsBackground
        "white" -> R.color.whiteBackground
        ThemePreferences.MATERIAL_FIXED_DARK -> R.color.material_fixed_surface_dark
        ThemePreferences.MATERIAL_FIXED_LIGHT -> R.color.material_fixed_surface_light
        ThemePreferences.MATERIAL_FIXED_AUTO ->
            if (uiModeNight(ctx)) R.color.material_fixed_surface_dark
            else R.color.material_fixed_surface_light
        "material_dark" -> materialBackgroundColor(dynamic = true, dark = true)
        "material_light" -> materialBackgroundColor(dynamic = true, dark = false)
        "material_daynight" ->
            materialBackgroundColor(dynamic = true, dark = uiModeNight(ctx))
        "darklight_daynight" ->
            if (uiModeNight(ctx)) R.color.background else R.color.lightBackground
        "amoledwhite_daynight" ->
            if (uiModeNight(ctx)) android.R.color.black else R.color.whiteBackground
        else -> R.color.background
    }

    fun getPreferredTheme(ctx: Context): String {
        return ctx.harmonicAppComposition.appearance.selection().theme
    }

    private fun setMaterialAutoTheme(activity: ComponentActivity, dynamic: Boolean) {
        val useDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            activity.setTheme(
                if (useDynamic) R.style.AppThemeMaterialDayNight
                else R.style.AppThemeMaterialFixedDayNight,
            )
        } else {
            activity.setTheme(materialTheme(useDynamic, uiModeNight(activity)))
        }
    }

    private fun materialTheme(dynamic: Boolean, dark: Boolean): Int {
        val useDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return when {
            useDynamic && dark -> R.style.AppThemeMaterialDark
            useDynamic -> R.style.AppThemeMaterialLight
            dark -> R.style.AppThemeMaterialFixedDark
            else -> R.style.AppThemeMaterialFixedLight
        }
    }

    private fun materialBackgroundColor(dynamic: Boolean, dark: Boolean): Int {
        val useDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return when {
            useDynamic && dark -> R.color.material_you_neutral_900
            useDynamic -> R.color.material_you_neutral_50
            dark -> R.color.material_fixed_surface_dark
            else -> R.color.material_fixed_surface_light
        }
    }
}
