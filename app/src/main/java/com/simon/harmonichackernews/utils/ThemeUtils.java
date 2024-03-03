package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.view.Window;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.R;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class ThemeUtils {
    /**
     * Default color for nav bar's light scrim.
     * <p>
     * Copied from {@link EdgeToEdge#DefaultLightScrim} which was copied from Android sources:
     * <a href="https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/com/android/internal/policy/DecorView.java;drc=6ef0f022c333385dba2c294e35b8de544455bf19;l=142">source</a>
     */
    private static final int defaultLightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF);

    /**
     * Default color for nav bar's dark scrim.
     * <p>
     * Copied from {@link EdgeToEdge#DefaultDarkScrim} which was copied from Android sources:
     * <a href="https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/res/res/color/system_bar_background_semi_transparent.xml">source 1</a>,
     * <a href="https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/res/remote_color_resources_res/values/colors.xml;l=67">source 2</a>
     */
    private static final int defaultDarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b);

    public static void setupTheme(ComponentActivity activity) {
        setupTheme(activity, false, true);
    }

    public static void setupTheme(ComponentActivity activity, boolean swipeBack) {
        setupTheme(activity, swipeBack, true);
    }

    public static void setupTheme(ComponentActivity activity, boolean swipeBack, boolean specialFlags) {
        String theme = getPreferredTheme(activity);
        switch (theme) {
            case "material_daynight":
                // below 30, default on comments is swipeBack so if we don't use it we need to change to a normal theme
                if (Build.VERSION.SDK_INT < 30) {
                    if (!swipeBack) {
                        activity.setTheme(R.style.AppThemeMaterialDayNight);
                    }
                } else {
                    // at and above 30, the default is AppTheme so if we use swipeBack we need to change
                    if (swipeBack) {
                        activity.setTheme(R.style.ThemeSwipeBackNoActionBarMaterialDayNight);
                    }
                }
                break;
            case "material_dark":
                activity.setTheme(swipeBack ? R.style.ThemeSwipeBackNoActionBarMaterialDark : R.style.AppThemeMaterialDark);
                break;
            case "amoled":
                activity.setTheme(swipeBack ? R.style.ThemeSwipeBackNoActionBarAmoledDark : R.style.AppThemeAmoledDark);
                break;
            case "gray":
                activity.setTheme(swipeBack ? R.style.ThemeSwipeBackNoActionBarGray : R.style.AppThemeGray);
                break;
            case "light":
                activity.setTheme(swipeBack ? R.style.ThemeSwipeBackNoActionBarLight : R.style.AppThemeLight);
                break;
            case "material_light":
                activity.setTheme(swipeBack ? R.style.ThemeSwipeBackNoActionBarMaterialLight : R.style.AppThemeMaterialLight);
                break;
            case "white":
                activity.setTheme(swipeBack ? R.style.ThemeSwipeBackNoActionBarWhite : R.style.AppThemeWhite);
                break;
            // needed because of comment activity where the default is AppTheme, now swipeBack
            case "dark":
                activity.setTheme(swipeBack ? R.style.Theme_Swipe_Back_NoActionBar : R.style.AppTheme);

                break;

        }

        Window window = activity.getWindow();
        WindowInsetsControllerCompat insetsController = WindowCompat.getInsetsController(window, window.getDecorView());
        insetsController.setAppearanceLightStatusBars(!isDarkMode(activity));
        insetsController.setAppearanceLightNavigationBars(!isDarkMode(activity));

        if (specialFlags) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // All themes have nav bar color set to transparent so on API 29+ the system will draw
            // translucent scrim for us. However on older versions we need to set correct nav bar
            // color manually. Also before API 26 Android doesn't support light nav bars, so we
            // need to always use dark background
            int navBarColor;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                navBarColor = defaultDarkScrim;
            } else {
                navBarColor = isDarkMode(activity) ? defaultDarkScrim : defaultLightScrim;
            }
            window.setNavigationBarColor(navBarColor);
        }

        if (SettingsUtils.shouldUseTransparentStatusBar(activity)) {
            window.setStatusBarColor(Color.TRANSPARENT);
        }
    }

    public static boolean isDarkMode(Context ctx, String theme) {
        if (theme.equals("material_daynight")) {
            return uiModeNight(ctx);
        }
        return theme.equals("amoled") || theme.equals("dark") || theme.equals("gray") || theme.equals("material_dark");
    }

    public static boolean isDarkMode(Context ctx) {
        String theme = getPreferredTheme(ctx);
        return isDarkMode(ctx, theme);
    }

    public static boolean uiModeNight(Context ctx) {
        int currentNightMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return currentNightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int getBackgroundColorResource(Context ctx) {
        String theme = getPreferredTheme(ctx);
        switch (theme) {
            case "amoled":
                return android.R.color.black;
            case "gray":
                return R.color.grayBackground;
            case "light":
                return R.color.lightBackground;
            case "white":
                return R.color.whiteBackground;
            case "material_dark":
                return R.color.material_you_neutral_900;
            case "material_light":
                return R.color.material_you_neutral_50;
            case "material_daynight":
                return uiModeNight(ctx) ? R.color.material_you_neutral_900 : R.color.material_you_neutral_50;
            case "dark":
                return R.color.background;
            default:
                return R.color.background;
        }
    }

    public static String getPreferredTheme(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (SettingsUtils.shouldUseSpecialNighttimeTheme(ctx)) {
            // check time
            Calendar currentCalendar = Calendar.getInstance();
            int[] nighttimeHours = Utils.getNighttimeHours(ctx);

            long startTime = TimeUnit.HOURS.toMinutes(nighttimeHours[0]) + nighttimeHours[1];
            long endTime = TimeUnit.HOURS.toMinutes(nighttimeHours[2]) + nighttimeHours[3];
            long currentTime = TimeUnit.HOURS.toMinutes(currentCalendar.get(Calendar.HOUR_OF_DAY)) + currentCalendar.get(Calendar.MINUTE);

            if (Utils.isTimeBetweenTwoTimes(startTime, endTime, currentTime)) {
                return prefs.getString("pref_theme_nighttime", "material_daynight");
            }
        }
        return prefs.getString("pref_theme", "material_daynight");
    }

}
