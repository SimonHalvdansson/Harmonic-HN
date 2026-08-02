package com.simon.harmonichackernews.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.utils.SettingsUtils.getSelectableNighttimeTheme
import com.simon.harmonichackernews.utils.SettingsUtils.shouldUseSpecialNighttimeTheme
import com.simon.harmonichackernews.utils.SettingsUtils.shouldUseTransparentStatusBar
import java.util.Arrays
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

    fun setupTheme(activity: ComponentActivity) {
        val theme = getPreferredTheme(activity)
        when (theme) {
            "material_daynight" -> if (Build.VERSION.SDK_INT < 30) {
                activity.setTheme(R.style.AppThemeMaterialDayNight)
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
        val dynamicThemes = mutableListOf<String?>(
            "material_daynight",
            "darklight_daynight",
            "amoledwhite_daynight"
        )
        val darkThemes = mutableListOf<String?>("amoled", "dark", "gray", "hacker", "material_dark")

        if (dynamicThemes.contains(theme)) {
            return uiModeNight(ctx)
        }
        return darkThemes.contains(theme)
    }

    fun isDarkMode(ctx: Context): Boolean {
        val theme = getPreferredTheme(ctx)
        return isDarkMode(ctx, theme)
    }

    fun isLightMode(ctx: Context): Boolean {
        return !isDarkMode(ctx)
    }

    fun uiModeNight(ctx: Context): Boolean {
        val currentNightMode =
            ctx.getResources().getConfiguration().uiMode and Configuration.UI_MODE_NIGHT_MASK
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES
    }

    fun getBackgroundColorResource(ctx: Context): Int {
        val theme = getPreferredTheme(ctx)
        when (theme) {
            "amoled" -> return android.R.color.black
            "hacker" -> return R.color.hackerBackground
            "gray" -> return R.color.grayBackground
            "light" -> return R.color.lightBackground
            "hacker_news" -> return R.color.hackerNewsBackground
            "white" -> return R.color.whiteBackground
            "material_dark" -> return R.color.material_you_neutral_900
            "material_light" -> return R.color.material_you_neutral_50
            "material_daynight" -> return if (uiModeNight(ctx)) R.color.material_you_neutral_900 else R.color.material_you_neutral_50
            "darklight_daynight" -> return if (uiModeNight(ctx)) R.color.background else R.color.lightBackground
            "amoledwhite_daynight" -> return if (uiModeNight(ctx)) android.R.color.black else R.color.whiteBackground
            "dark" -> return R.color.background
            else -> return R.color.background
        }
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

        return Utils.isTimeBetweenTwoTimes(startTime, endTime, currentTime)
    }
}
