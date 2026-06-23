package com.simon.harmonichackernews.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateFormat;

import androidx.preference.Preference;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;

public class AppearancePreferenceFragment extends BaseSettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Preference themePreference;
    private Preference nighttimeThemePreference;
    private Preference fontPreference;
    private Preference paletteTintPreference;

    @Override
    protected String getToolbarTitle() {
        return "Appearance";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_appearance, rootKey);

        themePreference = findPreference(SettingsUtils.PREF_THEME);
        updateThemeSummary();
        nighttimeThemePreference = findPreference(SettingsUtils.PREF_THEME_NIGHTTIME);
        updateNighttimeThemeSummary();
        updateTimedRangeSummary();
        fontPreference = findPreference(SettingsUtils.PREF_FONT);
        updateFontSummary();
        paletteTintPreference = findPreference(SettingsUtils.PREF_PALETTE_TINT_MODE);
        updatePaletteTintSummary();

        getParentFragmentManager().setFragmentResultListener(
                ThemeSelectionDialogFragment.RESULT_KEY,
                this,
                (requestKey, result) -> {
                    String prefKey = result.getString(ThemeSelectionDialogFragment.RESULT_PREF_KEY);
                    String selectedTheme = result.getString(ThemeSelectionDialogFragment.RESULT_THEME);
                    String previousTheme = result.getString(ThemeSelectionDialogFragment.RESULT_PREVIOUS_THEME);
                    if (SettingsUtils.PREF_THEME.equals(prefKey)) {
                        updateThemeSummary();
                        restartSettingsActivity();
                    } else if (SettingsUtils.PREF_THEME_NIGHTTIME.equals(prefKey)) {
                        updateNighttimeThemeSummary();
                        restartSettingsActivityIfThemeChanged(
                                ThemeUtils.getPreferredTheme(
                                        requireContext(),
                                        SettingsUtils.shouldUseSpecialNighttimeTheme(requireContext()),
                                        previousTheme),
                                ThemeUtils.getPreferredTheme(
                                        requireContext(),
                                        SettingsUtils.shouldUseSpecialNighttimeTheme(requireContext()),
                                        selectedTheme));
                    }
                });

        boolean specialNighttime = SettingsUtils.shouldUseSpecialNighttimeTheme(getContext());
        changePrefStatus(findPreference("pref_theme_timed_range"), specialNighttime);
        changePrefStatus(findPreference("pref_theme_nighttime"), specialNighttime);

        findPreference("pref_special_nighttime").setOnPreferenceChangeListener((preference, newValue) -> {
            boolean useSpecialNighttimeTheme = (boolean) newValue;
            changePrefStatus(findPreference("pref_theme_timed_range"), useSpecialNighttimeTheme);
            changePrefStatus(findPreference("pref_theme_nighttime"), useSpecialNighttimeTheme);
            restartSettingsActivityIfThemeChanged(ThemeUtils.getPreferredTheme(
                    requireContext(),
                    useSpecialNighttimeTheme,
                    getNighttimeTheme()));
            return true;
        });

        themePreference.setOnPreferenceClickListener(preference -> {
            ThemeSelectionDialogFragment.show(getParentFragmentManager());
            return true;
        });

        nighttimeThemePreference.setOnPreferenceClickListener(preference -> {
            ThemeSelectionDialogFragment.showNighttimeTheme(getParentFragmentManager());
            return true;
        });

        if (fontPreference != null) {
            fontPreference.setOnPreferenceClickListener(preference -> {
                FontSelectionDialogFragment.show(getParentFragmentManager());
                return true;
            });
        }

        if (paletteTintPreference != null) {
            paletteTintPreference.setOnPreferenceClickListener(preference -> {
                PaletteTintDialogFragment.show(getParentFragmentManager());
                return true;
            });
        }

        findPreference("pref_transparent_status_bar").setOnPreferenceChangeListener((preference, newValue) -> {
            restartSettingsActivity();
            return true;
        });

        findPreference(SettingsUtils.PREF_TRANSLUCENT_STATUS_BAR).setOnPreferenceChangeListener((preference, newValue) -> {
            restartSettingsActivity();
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
                    String previousTheme = ThemeUtils.getPreferredTheme(requireContext());
                    Utils.setNighttimeHours(fromTimePicker.getHour(), fromTimePicker.getMinute(), toTimePicker.getHour(), toTimePicker.getMinute(), getContext());
                    updateTimedRangeSummary();
                    restartSettingsActivityIfThemeChanged(previousTheme, ThemeUtils.getPreferredTheme(requireContext()));
                });
                toTimePicker.show(getParentFragmentManager(), "to_picker_tag");
            });
            fromTimePicker.show(getParentFragmentManager(), "from_picker_tag");
            return false;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateThemeSummary();
        updateNighttimeThemeSummary();
        updateFontSummary();
        updatePaletteTintSummary();
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SettingsUtils.PREF_FONT.equals(key)) {
            updateFontSummary();
        } else if (SettingsUtils.PREF_PALETTE_TINT_MODE.equals(key)
                || SettingsUtils.PREF_PALETTE_TINT_STRENGTH.equals(key)
                || SettingsUtils.PREF_PALETTE_TINT_COLORFULNESS.equals(key)
                || SettingsUtils.PREF_PALETTE_TINT_TONE.equals(key)) {
            updatePaletteTintSummary();
        } else if (SettingsUtils.PREF_THEME.equals(key)) {
            updateThemeSummary();
        } else if (SettingsUtils.PREF_THEME_NIGHTTIME.equals(key)) {
            updateNighttimeThemeSummary();
        }
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

    private String getNighttimeTheme() {
        return SettingsUtils.getSelectableNighttimeTheme(getPreferenceManager()
                .getSharedPreferences()
                .getString(SettingsUtils.PREF_THEME_NIGHTTIME, SettingsUtils.DEFAULT_NIGHTTIME_THEME));
    }

    private void restartSettingsActivityIfThemeChanged(String newTheme) {
        restartSettingsActivityIfThemeChanged(ThemeUtils.getPreferredTheme(requireContext()), newTheme);
    }

    private void restartSettingsActivityIfThemeChanged(String oldTheme, String newTheme) {
        if (!oldTheme.equals(newTheme)) {
            restartSettingsActivity();
        }
    }

    private void updateFontSummary() {
        if (fontPreference != null && getContext() != null) {
            fontPreference.setSummary(SettingsUtils.getPreferredFontLabel(requireContext()));
        }
    }

    private void updatePaletteTintSummary() {
        if (paletteTintPreference != null && getContext() != null) {
            paletteTintPreference.setSummary(SettingsUtils.getPreferredPaletteTintSummary(requireContext()));
        }
    }

    private void updateThemeSummary() {
        if (themePreference != null && getContext() != null) {
            String theme = getPreferenceManager()
                    .getSharedPreferences()
                    .getString(SettingsUtils.PREF_THEME, SettingsUtils.DEFAULT_THEME);
            themePreference.setSummary(ThemeSelectionDialogFragment.getThemeLabel(requireContext(), theme));
        }
    }

    private void updateNighttimeThemeSummary() {
        if (nighttimeThemePreference != null && getContext() != null) {
            nighttimeThemePreference.setSummary(ThemeSelectionDialogFragment.getThemeLabel(
                    requireContext(),
                    getNighttimeTheme(),
                    false,
                    true,
                    SettingsUtils.DEFAULT_NIGHTTIME_THEME));
        }
    }
}
