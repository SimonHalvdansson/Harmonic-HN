package com.simon.harmonichackernews.settings;

import static com.simon.harmonichackernews.utils.UtilsKt.KEY_SHARED_PREFERENCES_HISTORIES;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.utils.HistoriesUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.ArrayList;
import java.util.Calendar;

public class DataStoragePreferenceFragment extends BaseSettingsFragment {

    @Override
    protected String getToolbarTitle() {
        return "Data & Storage";
    }

    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null && getContext() != null) {
                        try {
                            Utils.writeInFile(
                                    getContext(),
                                    result.getData().getData(),
                                    SettingsUtils.readStringFromSharedPreferences(getContext(), Utils.KEY_SHARED_PREFERENCES_BOOKMARKS));
                        } catch (Exception e) {
                            Toast.makeText(getContext(), "Write error", Toast.LENGTH_SHORT).show();
                            e.printStackTrace();
                        }
                    }
                });

        importLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null && getContext() != null) {
                        try {
                            String content = Utils.readFileContent(getContext(), result.getData().getData());
                            ArrayList<Bookmark> bookmarks = Utils.loadBookmarks(true, content);
                            if (!bookmarks.isEmpty()) {
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
                });
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_data_storage, rootKey);

        findPreference("pref_export_bookmarks").setOnPreferenceClickListener(preference -> {
            String textToSave = SettingsUtils.readStringFromSharedPreferences(requireContext(), Utils.KEY_SHARED_PREFERENCES_BOOKMARKS);

            if (TextUtils.isEmpty(textToSave)) {
                Snackbar.make(getView(), "No bookmarks to export", Snackbar.LENGTH_SHORT).show();
                return false;
            }

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");

            Calendar cal = Calendar.getInstance();
            intent.putExtra(Intent.EXTRA_TITLE, "HarmonicBookmarks" + cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH) + ".txt");

            exportLauncher.launch(intent);
            return false;
        });

        findPreference("pref_import_bookmarks").setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");

            importLauncher.launch(intent);
            return false;
        });

        findPreference("pref_clear_clicked_stories").setOnPreferenceClickListener(preference -> {
            int oldCount = HistoriesUtils.INSTANCE.size();

            SettingsUtils.saveStringToSharedPreferences(getContext(), KEY_SHARED_PREFERENCES_HISTORIES, "");

            SettingsCallback callback = getSettingsCallback();
            if (callback != null) {
                callback.onRequestRestart();
            }

            Snackbar.make(
                    requireView(),
                    "Cleared " + oldCount + (oldCount == 1 ? " entry" : " entries"),
                    Snackbar.LENGTH_SHORT).show();

            return false;
        });

        findPreference("pref_open_hn_links_in_harmonic").setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage("Since Harmonic does not own the domain news.ycombinator.com intercepting links needs to be enabled by the user manually.")
                    .setNeutralButton("Go to settings", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                        startActivity(intent);
                        Toast.makeText(requireContext(), "The option should be under \"Open by default\"", Toast.LENGTH_LONG).show();
                    })
                    .show();
            return true;
        });
    }
}
