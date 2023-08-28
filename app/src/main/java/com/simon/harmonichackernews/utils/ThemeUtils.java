package com.simon.harmonichackernews.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.Window;

import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.R;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

public class ThemeUtils {

    public static void setupTheme(Activity activity) {
        setupTheme(activity, false, true);
    }

    public static void setupTheme(Activity activity, boolean swipeBack) {
        setupTheme(activity, swipeBack, true);
    }

    public static void setupTheme(Activity activity, boolean swipeBack, boolean specialFlags) {
        String theme = getPreferredTheme(activity);
        switch (theme) {
            case "material_daynight":
                activity.setTheme(swipeBack ? R.style.ThemeSwipeBackNoActionBarMaterialDayNight : R.style.AppThemeMaterialDayNight);
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
        }

        Window window = activity.getWindow();
        WindowCompat.getInsetsController(window, window.getDecorView())
                .setAppearanceLightStatusBars(!isDarkMode(activity));

        if (specialFlags) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
        }

        if (SettingsUtils.shouldUseTransparentStatusBar(activity)) {
            window.setStatusBarColor(ContextCompat.getColor(activity, R.color.statusBarColorTransparent));
        }
    }

    public static boolean isDarkMode(Context ctx) {
        String theme = getPreferredTheme(ctx);
        if (theme.equals("material_daynight")) {
            return uiModeNight(ctx);
        }
        return theme.equals("amoled") || theme.equals("dark") || theme.equals("gray") || theme.equals("material_dark");
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
                return R.color.material_you_neutral_100;
            case "material_daynight":
                return uiModeNight(ctx) ? R.color.material_you_neutral_900 : R.color.material_you_neutral_100;
            default:
                return R.color.background;
        }
    }

    public static String getPreferredTheme(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (SettingsUtils.shouldUseSpecialNighttimeTheme(ctx)) {
            //check time
            Date currentTimeDate = Calendar.getInstance().getTime();
            int[] nighttimeHours = Utils.getNighttimeHours(ctx);

            String startTime = (nighttimeHours[0] < 10 ? "0" : "") + nighttimeHours[0] + ":" + (nighttimeHours[1] < 10 ? "0" : "") +  nighttimeHours[1];
            String endTime = (nighttimeHours[2] < 10 ? "0" : "") + nighttimeHours[2] + ":" + (nighttimeHours[3] < 10 ? "0" : "") +  nighttimeHours[3];
            String currentTime = (currentTimeDate.getHours() < 10 ? "0" : "") + currentTimeDate.getHours() + ":" + (currentTimeDate.getMinutes() < 10 ? "0" : "") +  currentTimeDate.getMinutes();

            try {
                if (Utils.isTimeBetweenTwoTimes(startTime, endTime, currentTime)) {
                    return prefs.getString("pref_theme_nighttime", "dark");
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        return prefs.getString("pref_theme", "dark");
    }

}
