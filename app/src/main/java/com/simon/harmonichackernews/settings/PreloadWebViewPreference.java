package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.simon.harmonichackernews.utils.SettingsUtils;

public class PreloadWebViewPreference extends Preference {

    public PreloadWebViewPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        updateSummary();
    }

    public PreloadWebViewPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        updateSummary();
    }

    public void updateSummary() {
        Context context = getContext();
        String mode = SettingsUtils.shouldPreloadWebView(context);
        int minimumBattery = SettingsUtils.getPreloadWebViewMinimumBattery(context);

        if (SettingsUtils.PRELOAD_WEBVIEW_ALWAYS.equals(mode)) {
            setSummary(getBatteryAwareSummary("Always", minimumBattery));
        } else if (SettingsUtils.PRELOAD_WEBVIEW_ONLY_WIFI.equals(mode)) {
            setSummary(getBatteryAwareSummary("Only on WiFi", minimumBattery));
        } else {
            setSummary("Never");
        }
    }

    private String getBatteryAwareSummary(String modeLabel, int minimumBattery) {
        if (minimumBattery <= SettingsUtils.DEFAULT_PRELOAD_WEBVIEW_MINIMUM_BATTERY) {
            return modeLabel + ", any battery level";
        }
        return modeLabel + ", battery at least " + minimumBattery + "%";
    }
}
