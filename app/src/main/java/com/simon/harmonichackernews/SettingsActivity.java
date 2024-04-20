package com.simon.harmonichackernews;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private static boolean requestFullRestart = false;
    public final static String EXTRA_REQUEST_RESTART = "EXTRA_REQUEST_RESTART";

    private OnBackPressedCallback backPressedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestFullRestart = false;

        ThemeUtils.setupTheme(this, false);

        setContentView(R.layout.activity_settings);

        updatePadding();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleExit();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
        backPressedCallback.setEnabled(false);

        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_REQUEST_RESTART, false)) {
            backPressedCallback.setEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private static final int WRITE_REQUEST_CODE = 101;
        private static final int READ_REQUEST_CODE = 102;

        public SettingsFragment() {

        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            updateTimedRangeSummary();

            findPreference("pref_default_story_type").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    if (getActivity() != null && getActivity() instanceof SettingsActivity) {
                        ((SettingsActivity) getActivity()).backPressedCallback.setEnabled(true);
                    }
                    return true;
                }
            });

            findPreference("pref_compact_view").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    changePrefStatus(findPreference("pref_show_points"), !(boolean) newValue);
                    changePrefStatus(findPreference("pref_show_comments_count"), !(boolean) newValue);
                    changePrefStatus(findPreference("pref_thumbnails"), !(boolean) newValue);

                    return true;
                }
            });

            findPreference("pref_foldable_support").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    if (getActivity() != null && getActivity() instanceof SettingsActivity) {
                        ((SettingsActivity) getActivity()).backPressedCallback.setEnabled(true);
                    }
                    requestFullRestart = true;
                    return true;
                }
            });

            boolean compact = SettingsUtils.shouldUseCompactView(getContext());

            changePrefStatus(findPreference("pref_show_points"), !compact);
            changePrefStatus(findPreference("pref_show_comments_count"), !compact);
            changePrefStatus(findPreference("pref_thumbnails"), !compact);

            findPreference("pref_transparent_status_bar").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    Intent intent = new Intent(getContext(), SettingsActivity.class);
                    intent.putExtra(EXTRA_REQUEST_RESTART, true);
                    requireContext().startActivity(intent);
                    requireActivity().finish();
                    return true;
                }
            });

            findPreference("pref_about").setSummary("Version " + BuildConfig.VERSION_NAME);

            final Fragment f = this;

            findPreference("pref_theme").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
                    Intent intent = new Intent(getContext(), SettingsActivity.class);
                    intent.putExtra(EXTRA_REQUEST_RESTART, true);
                    requireContext().startActivity(intent);
                    requireActivity().finish();
                    return true;
                }
            });

            findPreference("pref_theme_timed_range").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    int[] nighttimeHours = Utils.getNighttimeHours(getContext());

                    MaterialTimePicker fromTimePicker = new MaterialTimePicker.Builder()
                            .setTimeFormat(DateFormat.is24HourFormat(getContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                            .setHour(nighttimeHours[0])
                            .setMinute(nighttimeHours[1])
                            .setTitleText("From:")
                            .build();

                    fromTimePicker.addOnPositiveButtonClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            MaterialTimePicker toTimePicker = new MaterialTimePicker.Builder()
                                    .setTimeFormat(DateFormat.is24HourFormat(getContext()) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                                    .setHour(nighttimeHours[2])
                                    .setMinute(nighttimeHours[3])
                                    .setTitleText("To:")
                                    .build();
                            toTimePicker.addOnPositiveButtonClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    Utils.setNighttimeHours(fromTimePicker.getHour(), fromTimePicker.getMinute(), toTimePicker.getHour(), toTimePicker.getMinute(), getContext());
                                    updateTimedRangeSummary();
                                }
                            });
                            toTimePicker.show(getParentFragmentManager(), "to_picker_tag");
                        }
                    });
                    fromTimePicker.show(getParentFragmentManager(), "from_picker_tag");
                    return false;
                }
            });

            findPreference("pref_export_bookmarks").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {

                    String textToSave = SettingsUtils.readStringFromSharedPreferences(requireContext(), Utils.KEY_SHARED_PREFERENCES_BOOKMARKS);

                    if (TextUtils.isEmpty(textToSave)) {
                        Snackbar.make(
                                getView(),
                                "No bookmarks to export",
                                Snackbar.LENGTH_SHORT).show();
                        return false;
                    }

                    // when you create document, you need to add Intent.ACTION_CREATE_DOCUMENT
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);

                    intent.setType("text/plain");

                    Calendar cal = Calendar.getInstance();
                    intent.putExtra(Intent.EXTRA_TITLE, "HarmonicBookmarks" + cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH) + ".txt");

                    startActivityForResult(intent, WRITE_REQUEST_CODE);
                    return false;
                }
            });

            findPreference("pref_import_bookmarks").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/plain");

                    startActivityForResult(intent, READ_REQUEST_CODE);

                    return false;
                }
            });

            findPreference("pref_clear_clicked_stories").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    Set<Integer> set = SettingsUtils.readIntSetFromSharedPreferences(requireContext(), Utils.KEY_SHARED_PREFERENCES_CLICKED_IDS);
                    int oldCount = set.size();

                    SettingsUtils.saveIntSetToSharedPreferences(getContext(), Utils.KEY_SHARED_PREFERENCES_CLICKED_IDS, new HashSet<>(0));
                    if (getActivity() != null && getActivity() instanceof SettingsActivity) {
                        ((SettingsActivity) getActivity()).backPressedCallback.setEnabled(true);
                    }

                    Snackbar snackbar = Snackbar.make(
                            requireView(),
                            "Cleared " + oldCount + (oldCount == 1 ? " entry" : " entries"),
                            Snackbar.LENGTH_SHORT);

                    snackbar.show();

                    return false;
                }
            });

            findPreference("pref_about").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(@NonNull Preference preference) {
                    startActivity(new Intent(getContext(), AboutActivity.class));

                    return false;
                }
            });
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

            if (resultCode == Activity.RESULT_OK && getContext() != null && data != null && data.getData() != null) {
                if (requestCode == WRITE_REQUEST_CODE) {
                    try {
                        Utils.writeInFile(
                                getContext(),
                                data.getData(),
                                SettingsUtils.readStringFromSharedPreferences(getContext(), Utils.KEY_SHARED_PREFERENCES_BOOKMARKS));
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Write error", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                } else if (requestCode == READ_REQUEST_CODE) {
                    try {
                        String content = Utils.readFileContent(getContext(), data.getData());
                        ArrayList<Bookmark> bookmarks = Utils.loadBookmarks(true, content);
                        if (!bookmarks.isEmpty()) {
                            // save the new bookmarks
                            SettingsUtils.saveStringToSharedPreferences(getContext(), Utils.KEY_SHARED_PREFERENCES_BOOKMARKS, content);
                            Toast.makeText(getContext(), "Loaded " + bookmarks.size() + " bookmarks", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "File contained no bookmarks", Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Read error", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                }
            }
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            setDivider(null);
        }

        @NonNull
        @Override
        public RecyclerView onCreateRecyclerView(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent, @Nullable Bundle savedInstanceState) {
            RecyclerView recycler = super.onCreateRecyclerView(inflater, parent, savedInstanceState);
            recycler.setFitsSystemWindows(true);
            return recycler;
        }

        private void updateTimedRangeSummary() {
            int[] nighttimeHours = Utils.getNighttimeHours(getContext());

            if (DateFormat.is24HourFormat(getContext())) {
                findPreference("pref_theme_timed_range").setSummary((nighttimeHours[0] < 10 ? "0" : "") + nighttimeHours[0] + ":" + (nighttimeHours[1] < 10 ? "0" : "") + nighttimeHours[1] + " - " + (nighttimeHours[2] < 10 ? "0" : "") + nighttimeHours[2] + ":" + (nighttimeHours[3] < 10 ? "0" : "") + nighttimeHours[3]);

            } else {
                SimpleDateFormat df = new SimpleDateFormat("h:mm a");
                Date dateFrom = new Date(0, 0, 0, nighttimeHours[0], nighttimeHours[1]);
                Date dateTo = new Date(0, 0, 0, nighttimeHours[2], nighttimeHours[3]);

                findPreference("pref_theme_timed_range").setSummary(df.format(dateFrom) + " - " + df.format(dateTo));
            }
        }

        private void changePrefStatus(Preference pref, boolean newStatus) {
            if (pref != null) {
                pref.setEnabled(newStatus);
                pref.getIcon().setAlpha(newStatus ? 255 : 120);
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updatePadding();
    }

    private void updatePadding() {
        int extraPadding = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);
        findViewById(R.id.settings_linear_layout).setPadding(extraPadding, 0, extraPadding, 0);
    }

    private void handleExit() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (requestFullRestart) {
            Runtime.getRuntime().exit(0);
        }
    }
}