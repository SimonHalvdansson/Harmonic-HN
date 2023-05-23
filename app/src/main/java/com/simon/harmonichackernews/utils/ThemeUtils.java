package com.simon.harmonichackernews.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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

        if (specialFlags) {
            if (isDarkMode(activity)) {
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            } else {
                activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
            }
        }
    }

    public static boolean isDarkMode(Context ctx) {
        String theme = getPreferredTheme(ctx);
        return theme.equals("amoled") || theme.equals("dark") || theme.equals("gray") || theme.equals("material_dark");
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
            default:
                return R.color.background;
        }
    }

    public static String getPreferredTheme(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (Utils.shouldUseSpecialNighttimeTheme(ctx)) {
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
