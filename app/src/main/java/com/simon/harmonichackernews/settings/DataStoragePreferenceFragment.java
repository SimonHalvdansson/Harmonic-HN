package com.simon.harmonichackernews.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Bookmark;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.HistoriesUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.UtilsKt;

import java.util.ArrayList;
import java.util.Calendar;

public class DataStoragePreferenceFragment extends BaseSettingsFragment {

    @Override
    protected String getToolbarTitle() {
        return "Data";
    }

    private ActivityResultLauncher<Intent> exportLauncher;
    private ActivityResultLauncher<Intent> importLauncher;
    private Preference enableBookmarksPreference;
    private Preference addBookmarksToFavoritesPreference;
    private Preference exportBookmarksPreference;
    private Preference importBookmarksPreference;
    private Preference clearClickedStoriesPreference;
    private Preference clearPostCachePreference;

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
                                updateBookmarksPreferences();
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

        enableBookmarksPreference = findPreference(SettingsUtils.PREF_BOOKMARKS_ENABLED);
        addBookmarksToFavoritesPreference = findPreference("pref_add_bookmarks_to_favorites");
        exportBookmarksPreference = findPreference("pref_export_bookmarks");
        importBookmarksPreference = findPreference("pref_import_bookmarks");
        clearClickedStoriesPreference = findPreference("pref_clear_clicked_stories");
        clearPostCachePreference = findPreference("pref_clear_post_cache");

        if (enableBookmarksPreference != null) {
            enableBookmarksPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                requireView().post(this::updateBookmarksPreferences);
                return true;
            });
        }

        addBookmarksToFavoritesPreference.setOnPreferenceClickListener(preference -> {
            ArrayList<Bookmark> bookmarks = Utils.loadBookmarks(requireContext(), true);
            int[] ids = new int[bookmarks.size()];
            for (int i = 0; i < bookmarks.size(); i++) {
                ids[i] = bookmarks.get(i).id;
            }

            AddBookmarksToFavoritesDialogFragment.newInstance(ids)
                    .show(getParentFragmentManager(), "AddBookmarksToFavoritesDialogFragment");
            return true;
        });

        exportBookmarksPreference.setOnPreferenceClickListener(preference -> {
            String textToSave = SettingsUtils.readStringFromSharedPreferences(requireContext(), Utils.KEY_SHARED_PREFERENCES_BOOKMARKS);

            if (TextUtils.isEmpty(textToSave)) {
                Snackbar.make(requireView(), "No bookmarks to export", Snackbar.LENGTH_SHORT).show();
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

        importBookmarksPreference.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");

            importLauncher.launch(intent);
            return false;
        });

        clearClickedStoriesPreference.setOnPreferenceClickListener(preference -> {
            int oldCount = getClickedStoryCount();

            HistoriesUtils.INSTANCE.clearHistories(requireContext());
            updateDataSummaries();

            if (oldCount > 0) {
                Utils.toast(
                        "Cleared " + oldCount + (oldCount == 1 ? " entry" : " entries"),
                        requireContext());
            }

            return false;
        });

        clearPostCachePreference.setOnPreferenceClickListener(preference -> {
            int oldCount = Utils.clearPostCache(requireContext());
            updateDataSummaries();

            if (oldCount > 0) {
                Utils.toast(
                        "Cleared " + oldCount + " cached " + (oldCount == 1 ? "post" : "posts"),
                        requireContext());
            }

            return false;
        });

        findPreference("pref_open_hn_links_in_harmonic").setOnPreferenceClickListener(preference -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setMessage("Since Harmonic does not own the domain news.ycombinator.com, intercepting links needs to be enabled by the user manually.\n\nGo to \"Open by default\" → \"Add link\" in the linked app settings page.")
                    .setNeutralButton("Go to settings", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                        startActivity(intent);
                        Toast.makeText(requireContext(), "The option should be under \"Open by default\"", Toast.LENGTH_LONG).show();
                    })
                    .show();
            return true;
        });

        updateBookmarksPreferences();
        updateDataSummaries();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBookmarksPreferences();
        updateDataSummaries();
    }

    private void updateBookmarksPreferences() {
        if (getContext() == null
                || addBookmarksToFavoritesPreference == null
                || exportBookmarksPreference == null
                || importBookmarksPreference == null) {
            return;
        }

        boolean bookmarksEnabled = SettingsUtils.shouldUseBookmarks(requireContext());
        int bookmarkCount = Utils.loadBookmarks(requireContext(), false).size();
        boolean hasBookmarks = bookmarkCount > 0;
        boolean loggedIn = AccountUtils.hasAccountDetails(requireContext());

        changePrefStatus(exportBookmarksPreference, bookmarksEnabled);
        changePrefStatus(importBookmarksPreference, bookmarksEnabled);

        changePrefStatus(addBookmarksToFavoritesPreference, bookmarksEnabled
                && hasBookmarks
                && loggedIn);
        if (!hasBookmarks) {
            addBookmarksToFavoritesPreference.setSummary("No bookmarks");
        } else if (!loggedIn) {
            addBookmarksToFavoritesPreference.setSummary("Login needed");
        } else {
            addBookmarksToFavoritesPreference.setSummary(formatBookmarkCount(bookmarkCount));
        }
    }

    private static String formatBookmarkCount(int count) {
        return count == 1 ? "1 bookmark" : count + " bookmarks";
    }

    private void updateDataSummaries() {
        if (getContext() == null
                || clearClickedStoriesPreference == null
                || clearPostCachePreference == null) {
            return;
        }

        clearClickedStoriesPreference.setTitle("Clear clicked stories (" + getClickedStoryCount() + ")");
        clearClickedStoriesPreference.setSummary(null);
        clearPostCachePreference.setTitle("Clear post cache (" + Utils.getCachedPostCount(requireContext()) + ")");
        clearPostCachePreference.setSummary(null);
    }

    private int getClickedStoryCount() {
        return UtilsKt.INSTANCE.loadHistories(requireContext(), false).size();
    }

}
