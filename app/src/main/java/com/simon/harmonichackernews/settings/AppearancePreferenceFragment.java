package com.simon.harmonichackernews.settings;

import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.SettingsActivity;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class AppearancePreferenceFragment extends BaseSettingsFragment {

    @Override
    protected String getToolbarTitle() {
        return "Appearance";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_appearance, rootKey);

        updateTimedRangeSummary();

        boolean specialNighttime = SettingsUtils.shouldUseSpecialNighttimeTheme(getContext());
        changePrefStatus(findPreference("pref_theme_timed_range"), specialNighttime);
        changePrefStatus(findPreference("pref_theme_nighttime"), specialNighttime);

        findPreference("pref_special_nighttime").setOnPreferenceChangeListener((preference, newValue) -> {
            changePrefStatus(findPreference("pref_theme_timed_range"), (boolean) newValue);
            changePrefStatus(findPreference("pref_theme_nighttime"), (boolean) newValue);
            return true;
        });

        findPreference("pref_theme").setOnPreferenceChangeListener((preference, newValue) -> {
            SettingsCallback callback = getSettingsCallback();
            if (callback != null) {
                callback.onRequestRestart();
            }
            Intent intent = new Intent(getContext(), SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_REQUEST_RESTART, true);
            requireContext().startActivity(intent);
            requireActivity().finish();
            return true;
        });

        findPreference("pref_transparent_status_bar").setOnPreferenceChangeListener((preference, newValue) -> {
            SettingsCallback callback = getSettingsCallback();
            if (callback != null) {
                callback.onRequestRestart();
            }
            Intent intent = new Intent(getContext(), SettingsActivity.class);
            intent.putExtra(SettingsActivity.EXTRA_REQUEST_RESTART, true);
            requireContext().startActivity(intent);
            requireActivity().finish();
            return true;
        });

        findPreference("pref_theme_timed_range").setOnPreferenceClickListener(preference -> {
            int[] nighttimeHours = Utils.getNighttimeHours(getContext());

            MaterialTimePicker fromTimePicker = new MaterialTimePicker.Builder()
                    .setTimeFormat(DateFormat.is24HourFormat(getContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                    .setHour(nighttimeHours[0])
                    .setMinute(nighttimeHours[1])
                    .setTitleText("From:")
                    .build();

            fromTimePicker.addOnPositiveButtonClickListener(view -> {
                MaterialTimePicker toTimePicker = new MaterialTimePicker.Builder()
                        .setTimeFormat(DateFormat.is24HourFormat(getContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                        .setHour(nighttimeHours[2])
                        .setMinute(nighttimeHours[3])
                        .setTitleText("To:")
                        .build();
                toTimePicker.addOnPositiveButtonClickListener(view2 -> {
                    Utils.setNighttimeHours(fromTimePicker.getHour(), fromTimePicker.getMinute(), toTimePicker.getHour(), toTimePicker.getMinute(), getContext());
                    updateTimedRangeSummary();
                });
                toTimePicker.show(getParentFragmentManager(), "to_picker_tag");
            });
            fromTimePicker.show(getParentFragmentManager(), "from_picker_tag");
            return false;
        });
    }

    private void updateTimedRangeSummary() {
        int[] nighttimeHours = Utils.getNighttimeHours(getContext());

        if (DateFormat.is24HourFormat(getContext())) {
            findPreference("pref_theme_timed_range").setSummary(
                    (nighttimeHours[0] < 10 ? "0" : "") + nighttimeHours[0] + ":" +
                    (nighttimeHours[1] < 10 ? "0" : "") + nighttimeHours[1] + " - " +
                    (nighttimeHours[2] < 10 ? "0" : "") + nighttimeHours[2] + ":" +
                    (nighttimeHours[3] < 10 ? "0" : "") + nighttimeHours[3]);
        } else {
            SimpleDateFormat df = new SimpleDateFormat("h:mm a");
            Date dateFrom = new Date(0, 0, 0, nighttimeHours[0], nighttimeHours[1]);
            Date dateTo = new Date(0, 0, 0, nighttimeHours[2], nighttimeHours[3]);
            findPreference("pref_theme_timed_range").setSummary(df.format(dateFrom) + " - " + df.format(dateTo));
        }
    }
}
