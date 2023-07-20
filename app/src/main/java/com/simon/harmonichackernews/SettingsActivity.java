package com.simon.harmonichackernews;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.gw.swipeback.SwipeBackLayout;
import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private static boolean requestRestart = false;
    private static boolean requestFullRestart = false;
    public final static String EXTRA_REQUEST_RESTART = "EXTRA_REQUEST_RESTART";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestRestart = false;
        requestFullRestart = false;
        if (getIntent() != null) {
            if (getIntent().getBooleanExtra(EXTRA_REQUEST_RESTART, false)) {
                requestRestart = true;
            }
        }

        ThemeUtils.setupTheme(this, true);

        setContentView(R.layout.activity_settings);
        SwipeBackLayout swipeBackLayout = findViewById(R.id.swipeBackLayout);

        swipeBackLayout.setSwipeBackListener(new SwipeBackLayout.OnSwipeBackListener() {
            @Override
            public void onViewPositionChanged(View mView, float swipeBackFraction, float swipeBackFactor) {
                mView.invalidate();
            }

            @Override
            public void onViewSwipeFinished(View mView, boolean isEnd) {
                if (isEnd) {
                    if (requestRestart) {
                        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    } else {
                        finish();
                        overridePendingTransition(0, 0);
                    }
                }
            }
        });

        LinearLayout linearLayout = findViewById(R.id.settings_linear_layout);
        linearLayout.setBackgroundResource(ThemeUtils.getBackgroundColorResource(this));

        updatePadding();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
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

            findPreference("pref_special_nighttime").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    findPreference("pref_theme_nighttime").setEnabled((boolean) newValue);
                    findPreference("pref_theme_nighttime").getIcon().setAlpha((boolean) newValue ? 255 : 120);

                    findPreference("pref_theme_timed_range").setEnabled((boolean) newValue);
                    findPreference("pref_theme_timed_range").getIcon().setAlpha((boolean) newValue ? 255 : 120);

                    return true;
                }
            });

            findPreference("pref_compact_view").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    findPreference("pref_show_points").setEnabled(!(boolean) newValue);
                    findPreference("pref_show_points").getIcon().setAlpha((boolean) newValue ? 120 : 255);

                    findPreference("pref_thumbnails").setEnabled(!(boolean) newValue);
                    findPreference("pref_thumbnails").getIcon().setAlpha((boolean) newValue ? 120 : 255);

                    return true;
                }
            });

            findPreference("pref_foldable_support").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    requestRestart = true;
                    requestFullRestart = true;
                    return true;
                }
            });

            boolean specialNighttimeTheme = Utils.shouldUseSpecialNighttimeTheme(getContext());
            findPreference("pref_theme_nighttime").setEnabled((boolean) specialNighttimeTheme);
            findPreference("pref_theme_nighttime").getIcon().setAlpha((boolean) specialNighttimeTheme ? 255 : 120);

            findPreference("pref_theme_timed_range").setEnabled((boolean) specialNighttimeTheme);
            findPreference("pref_theme_timed_range").getIcon().setAlpha((boolean) specialNighttimeTheme ? 255 : 120);


            boolean compact = Utils.shouldUseCompactView(getContext());

            findPreference("pref_show_points").setEnabled(!compact);
            findPreference("pref_show_points").getIcon().setAlpha(compact ? 120 : 255);

            findPreference("pref_thumbnails").setEnabled(!compact);
            findPreference("pref_thumbnails").getIcon().setAlpha(compact ? 120 : 255);

            findPreference("pref_transparent_status_bar").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Intent intent = new Intent(getContext(), SettingsActivity.class);
                    intent.putExtra(EXTRA_REQUEST_RESTART, true);
                    requireContext().startActivity(intent);
                    requireActivity().finish();
                    return true;
                }
            });

            boolean integratedWebview = Utils.shouldUseIntegratedWebView(getContext());
            findPreference("pref_preload_webview").setEnabled(integratedWebview);
            findPreference("pref_preload_webview").getIcon().setAlpha(integratedWebview ? 255 : 120);

            findPreference("pref_webview_match_theme").setEnabled(integratedWebview);
            findPreference("pref_webview_match_theme").getIcon().setAlpha(integratedWebview ? 255 : 120);

            findPreference("pref_webview_adblock").setEnabled(integratedWebview);
            findPreference("pref_webview_adblock").getIcon().setAlpha(integratedWebview ? 255 : 120);

            findPreference("pref_webview_disable_swipeback").setEnabled(integratedWebview);
            findPreference("pref_webview_disable_swipeback").getIcon().setAlpha(integratedWebview ? 255 : 120);

            findPreference("pref_webview_show_expand").setEnabled(integratedWebview);
            findPreference("pref_webview_show_expand").getIcon().setAlpha(integratedWebview ? 255 : 120);

            findPreference("pref_about").setSummary("Version " + BuildConfig.VERSION_NAME);

            final Fragment f = this;

            findPreference("pref_webview").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    findPreference("pref_preload_webview").setEnabled((boolean) newValue);
                    findPreference("pref_preload_webview").getIcon().setAlpha((boolean) newValue ? 255 : 120);

                    findPreference("pref_webview_match_theme").setEnabled((boolean) newValue);
                    findPreference("pref_webview_match_theme").getIcon().setAlpha((boolean) newValue ? 255 : 120);

                    findPreference("pref_webview_adblock").setEnabled((boolean) newValue);
                    findPreference("pref_webview_adblock").getIcon().setAlpha((boolean) newValue ? 255 : 120);

                    findPreference("pref_webview_disable_swipeback").setEnabled((boolean) newValue);
                    findPreference("pref_webview_disable_swipeback").getIcon().setAlpha((boolean) newValue ? 255 : 120);

                    findPreference("pref_webview_show_expand").setEnabled((boolean) newValue);
                    findPreference("pref_webview_show_expand").getIcon().setAlpha((boolean) newValue ? 255 : 120);

                    return true;
                }
            });

            findPreference("pref_theme").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    Intent intent = new Intent(getContext(), SettingsActivity.class);
                    intent.putExtra(EXTRA_REQUEST_RESTART, true);
                    requireContext().startActivity(intent);
                    requireActivity().finish();
                    return true;
                }
            });

            findPreference("pref_theme_timed_range").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
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
                public boolean onPreferenceClick(Preference preference) {

                    String textToSave = Utils.readStringFromSharedPreferences(requireContext(), Utils.KEY_SHARED_PREFERENCES_BOOKMARKS);

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
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("text/plain");

                    startActivityForResult(intent, READ_REQUEST_CODE);

                    return false;
                }
            });

            findPreference("pref_clear_clicked_stories").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Set<Integer> set = Utils.readIntSetFromSharedPreferences(requireContext(), Utils.KEY_SHARED_PREFERENCES_CLICKED_IDS);
                    int oldCount = 0;

                    if (set != null) {
                        oldCount = set.size();
                    }

                    Utils.saveIntSetToSharedPreferences(getContext(), Utils.KEY_SHARED_PREFERENCES_CLICKED_IDS, new HashSet<>(0));
                    requestRestart = true;

                    Snackbar snackbar = Snackbar.make(
                            requireView(),
                            "Cleared " + oldCount + (oldCount == 1 ? " entry" : " entries"),
                            Snackbar.LENGTH_SHORT);

                    snackbar.show();

                    return false;
                }
            });

            findPreference("pref_feedback").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("mailto:"));
                    intent.putExtra(Intent.EXTRA_EMAIL, new String[] { "swesnowme@gmail.com" });
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Harmonic for Hacker News feedback");
                    startActivity(Intent.createChooser(intent, "Send feedback"));
                    return false;
                }
            });

            findPreference("pref_about").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(getContext(), AboutActivity.class));

                    return false;
                }
            });

            findPreference("pref_open_source").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    String url = "https://github.com/SimonHalvdansson/Harmonic-HN";
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    startActivity(intent);

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
                                Utils.readStringFromSharedPreferences(getContext(), Utils.KEY_SHARED_PREFERENCES_BOOKMARKS));
                    } catch (Exception e) {
                        Toast.makeText(getContext(), "Write error", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                } else if (requestCode == READ_REQUEST_CODE) {
                    try {
                        String content = Utils.readFileContent(getContext(), data.getData());
                        ArrayList<Bookmark> bookmarks = Utils.loadBookmarks(true, content);
                        if (bookmarks.size() > 0) {
                            //save the new bookmarks
                            Utils.saveStringToSharedPreferences(getContext(), Utils.KEY_SHARED_PREFERENCES_BOOKMARKS, content);
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
            getListView().setPadding(0, Utils.getStatusBarHeight(getResources()), 0, Utils.getNavigationBarHeight(getResources()));
        }

        private void updateTimedRangeSummary() {
            int[] nighttimeHours = Utils.getNighttimeHours(getContext());

            if (DateFormat.is24HourFormat(getContext())) {
                findPreference("pref_theme_timed_range").setSummary((nighttimeHours[0] < 10 ? "0" : "") + nighttimeHours[0] + ":" + (nighttimeHours[1] < 10 ? "0" : "") +  nighttimeHours[1] + " - " + (nighttimeHours[2] < 10 ? "0" : "") + nighttimeHours[2] + ":" + (nighttimeHours[3] < 10 ? "0" : "") + nighttimeHours[3]);

            } else {
                SimpleDateFormat df = new SimpleDateFormat("h:mm a");
                Date dateFrom = new Date(0, 0, 0, nighttimeHours[0], nighttimeHours[1]);
                Date dateTo = new Date(0, 0, 0, nighttimeHours[2], nighttimeHours[3]);

                findPreference("pref_theme_timed_range").setSummary(df.format(dateFrom) + " - " + df.format(dateTo));
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

    @Override
    public void onBackPressed() {
        if (requestRestart) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (requestFullRestart) {
                Runtime.getRuntime().exit(0);
            }
        } else {
            super.onBackPressed();
            overridePendingTransition(0, R.anim.activity_out_animation);
        }
    }
}