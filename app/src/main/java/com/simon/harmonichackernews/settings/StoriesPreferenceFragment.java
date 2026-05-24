package com.simon.harmonichackernews.settings;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.widget.StoriesRemoteViewsFactory;
import com.simon.harmonichackernews.widget.StoriesWidgetProvider;

public class StoriesPreferenceFragment extends BaseSettingsFragment {
    private Preference grayOutClickedPreference;

    @Override
    protected String getToolbarTitle() {
        return "Stories";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_stories, rootKey);

        boolean compact = SettingsUtils.shouldUseCompactView(getContext());
        StoryContentPreviewPreference previewPreference = findPreference("pref_story_content_preview");
        grayOutClickedPreference = findPreference(SettingsUtils.PREF_GRAY_OUT_CLICKED);
        ListPreference defaultStoryTypePreference = findPreference("pref_default_story_type");

        changePrefStatus(findPreference("pref_show_points"), !compact);
        changePrefStatus(findPreference("pref_show_comments_count"), !compact);
        changePrefStatus(findPreference("pref_thumbnails"), !compact);
        updateGrayOutClickedPreference();

        if (SettingsUtils.shouldShowThumbnails(getContext())) {
            changePrefStatus(findPreference("pref_favicon_provider"), !compact);
        } else {
            changePrefStatus(findPreference("pref_favicon_provider"), false);
        }

        findPreference("pref_compact_view").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateCompact((boolean) newValue);
            }
            changePrefStatus(findPreference("pref_show_points"), !(boolean) newValue);
            changePrefStatus(findPreference("pref_show_comments_count"), !(boolean) newValue);
            changePrefStatus(findPreference("pref_thumbnails"), !(boolean) newValue);
            changePrefStatus(findPreference("pref_favicon_provider"), !(boolean) newValue && SettingsUtils.shouldShowThumbnails(getContext()));
            return true;
        });

        findPreference("pref_story_preview_image_mode").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updatePreviewImageMode((String) newValue);
            }
            return true;
        });

        findPreference("pref_thumbnails").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateThumbnails((boolean) newValue);
            }
            changePrefStatus(findPreference("pref_favicon_provider"), (boolean) newValue);
            return true;
        });

        findPreference("pref_show_points").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updatePoints((boolean) newValue);
            }
            return true;
        });

        findPreference("pref_show_comments_count").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateCommentsCount((boolean) newValue);
            }
            return true;
        });

        findPreference("pref_show_index").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateShowIndex((boolean) newValue);
            }
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

        findPreference("pref_left_align").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateLeftAlign((boolean) newValue);
            }
            return true;
        });

        findPreference("pref_hotness").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateHotness((String) newValue);
            }
            return true;
        });

        if (defaultStoryTypePreference != null) {
            String defaultStoryType = defaultStoryTypePreference.getValue();
            if ("Bookmarks".equals(defaultStoryType) || "History".equals(defaultStoryType)) {
                defaultStoryTypePreference.setValue("Top Stories");
            }

            defaultStoryTypePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                SettingsCallback callback = getSettingsCallback();
                if (callback != null) {
                    callback.onRequestRestart();
                }
                return true;
            });
        }

        findPreference(SettingsUtils.PREF_HIDE_CLICKED).setOnPreferenceChangeListener((preference, newValue) -> {
            updateGrayOutClickedPreference(!(boolean) newValue);
            SettingsCallback callback = getSettingsCallback();
            if (callback != null) {
                callback.onRequestRestart();
            }
            return true;
        });
    }

    private void updateGrayOutClickedPreference() {
        updateGrayOutClickedPreference(!SettingsUtils.shouldHideClicked(getContext()));
    }

    private void updateGrayOutClickedPreference(boolean enabled) {
        changePrefStatus(grayOutClickedPreference, enabled);
    }
}
