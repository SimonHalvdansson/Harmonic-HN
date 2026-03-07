package com.simon.harmonichackernews.settings;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.widget.StoriesRemoteViewsFactory;
import com.simon.harmonichackernews.widget.StoriesWidgetProvider;

public class StoriesPreferenceFragment extends BaseSettingsFragment {

    @Override
    protected String getToolbarTitle() {
        return "Stories";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_stories, rootKey);

        boolean compact = SettingsUtils.shouldUseCompactView(getContext());

        changePrefStatus(findPreference("pref_show_points"), !compact);
        changePrefStatus(findPreference("pref_show_comments_count"), !compact);
        changePrefStatus(findPreference("pref_thumbnails"), !compact);

        if (SettingsUtils.shouldShowThumbnails(getContext())) {
            changePrefStatus(findPreference("pref_favicon_provider"), !compact);
        } else {
            changePrefStatus(findPreference("pref_favicon_provider"), false);
        }

        findPreference("pref_compact_view").setOnPreferenceChangeListener((preference, newValue) -> {
            changePrefStatus(findPreference("pref_show_points"), !(boolean) newValue);
            changePrefStatus(findPreference("pref_show_comments_count"), !(boolean) newValue);
            changePrefStatus(findPreference("pref_thumbnails"), !(boolean) newValue);
            changePrefStatus(findPreference("pref_favicon_provider"), !(boolean) newValue);
            return true;
        });

        findPreference("pref_thumbnails").setOnPreferenceChangeListener((preference, newValue) -> {
            changePrefStatus(findPreference("pref_favicon_provider"), (boolean) newValue);
            return true;
        });

        findPreference("pref_show_index").setOnPreferenceChangeListener((preference, newValue) -> {
            // Re-render widget list items to show/hide indices (no network fetch)
            AppWidgetManager awm = AppWidgetManager.getInstance(requireContext());
            int[] ids = awm.getAppWidgetIds(
                    new ComponentName(requireContext(), StoriesWidgetProvider.class));
            if (ids.length > 0) {
                StoriesRemoteViewsFactory.setSkipFetchAll(requireContext(), true);
                awm.notifyAppWidgetViewDataChanged(ids, R.id.widget_stories_list);
            }
            return true;
        });

        findPreference("pref_default_story_type").setOnPreferenceChangeListener((preference, newValue) -> {
            SettingsCallback callback = getSettingsCallback();
            if (callback != null) {
                callback.onRequestRestart();
            }
            return true;
        });

        findPreference("pref_foldable_support").setOnPreferenceChangeListener((preference, newValue) -> {
            SettingsCallback callback = getSettingsCallback();
            if (callback != null) {
                callback.onRequestFullRestart();
            }
            return true;
        });
    }
}
