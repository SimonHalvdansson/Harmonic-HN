package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.SettingsUtils.getSelectableNighttimeTheme
import com.simon.harmonichackernews.utils.SettingsUtils.shouldUseSpecialNighttimeTheme
import com.simon.harmonichackernews.utils.SettingsUtils.shouldUseTransparentStatusBar
import java.util.Calendar
import java.util.concurrent.TimeUnit

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
    private val dynamicThemes = setOf(
        "material_daynight",
        "darklight_daynight",
        "amoledwhite_daynight",
    )
    private val darkThemes = setOf("amoled", "dark", "gray", "hacker", "material_dark")

    fun setupTheme(activity: ComponentActivity) {
        val theme = getPreferredTheme(activity)
        when (theme) {
            "material_daynight" -> if (Build.VERSION.SDK_INT < 30) {
                activity.setTheme(R.style.AppThemeMaterialDayNight)
            } else {
                // The v31 day/night theme keeps the dark base for WebView behavior. Resolve
                // the current system mode here so switching back to auto also updates the
                // existing activity immediately.
                activity.setTheme(
                    if (uiModeNight(activity)) {
                        R.style.AppThemeMaterialDark
                    } else {
                        R.style.AppThemeMaterialLight
                    },
                )
            }

            "darklight_daynight" -> activity.setTheme(R.style.AppThemeDarkLightDayNight)
            "amoledwhite_daynight" -> activity.setTheme(R.style.AppThemeAmoledWhiteDayNight)
            "material_dark" -> activity.setTheme(R.style.AppThemeMaterialDark)
            "amoled" -> activity.setTheme(R.style.AppThemeAmoledDark)
            "hacker" -> activity.setTheme(R.style.AppThemeHacker)
            "gray" -> activity.setTheme(R.style.AppThemeGray)
            "light" -> activity.setTheme(R.style.AppThemeLight)
            "hacker_news" -> activity.setTheme(R.style.AppThemeHackerNews)
            "material_light" -> activity.setTheme(R.style.AppThemeMaterialLight)
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

        if (shouldUseTransparentStatusBar(activity)) {
            window.setStatusBarColor(Color.TRANSPARENT)
        }
    }

    fun isDarkMode(ctx: Context, theme: String?): Boolean {
        return when (theme) {
            in dynamicThemes -> uiModeNight(ctx)
            in darkThemes -> true
            else -> false
        }
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
        "material_dark" -> R.color.material_you_neutral_900
        "material_light" -> R.color.material_you_neutral_50
        "material_daynight" ->
            if (uiModeNight(ctx)) R.color.material_you_neutral_900
            else R.color.material_you_neutral_50
        "darklight_daynight" ->
            if (uiModeNight(ctx)) R.color.background else R.color.lightBackground
        "amoledwhite_daynight" ->
            if (uiModeNight(ctx)) android.R.color.black else R.color.whiteBackground
        else -> R.color.background
    }

    fun getPreferredTheme(ctx: Context): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        return ThemeUtils.getPreferredTheme(
            ctx,
            shouldUseSpecialNighttimeTheme(ctx),
            prefs.getString(
                SettingsUtils.PREF_THEME_NIGHTTIME,
                SettingsUtils.DEFAULT_NIGHTTIME_THEME
            )!!
        )
    }

    fun getPreferredTheme(
        ctx: Context,
        useSpecialNighttimeTheme: Boolean,
        nighttimeTheme: String
    ): String {
        val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
        if (useSpecialNighttimeTheme && isNighttimeThemeTime(ctx)) {
            return getSelectableNighttimeTheme(nighttimeTheme)
        }
        return prefs.getString(SettingsUtils.PREF_THEME, SettingsUtils.DEFAULT_THEME)!!
    }

    private fun isNighttimeThemeTime(ctx: Context): Boolean {
        val currentCalendar = Calendar.getInstance()
        val nighttimeHours = Utils.getNighttimeHours(ctx)

        val startTime = TimeUnit.HOURS.toMinutes(nighttimeHours[0].toLong()) + nighttimeHours[1]
        val endTime = TimeUnit.HOURS.toMinutes(nighttimeHours[2].toLong()) + nighttimeHours[3]
        val currentTime = TimeUnit.HOURS.toMinutes(
            currentCalendar.get(Calendar.HOUR_OF_DAY).toLong()
        ) + currentCalendar.get(
            Calendar.MINUTE
        )

        return TimeWindowPolicy.containsMinutes(startTime, endTime, currentTime)
    }
}
