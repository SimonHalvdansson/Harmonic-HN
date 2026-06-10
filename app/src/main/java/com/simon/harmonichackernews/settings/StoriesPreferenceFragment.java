package com.simon.harmonichackernews.settings;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.StoryType;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.widget.StoriesRemoteViewsFactory;
import com.simon.harmonichackernews.widget.StoriesWidgetProvider;

import java.util.ArrayList;
import java.util.Set;

public class StoriesPreferenceFragment extends BaseSettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Preference grayOutClickedPreference;
    private Preference tintCardUsingPreviewPreference;
    private Preference compactPointsPreference;
    private Preference includeTopLevelDomainPreference;
    private Preference faviconProviderPreference;
    private ListPreference startingPagePreference;
    private MultiSelectListPreference additionalFrontpagesPreference;

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
        tintCardUsingPreviewPreference = findPreference(SettingsUtils.PREF_TINT_CARD_USING_PREVIEW);
        compactPointsPreference = findPreference(SettingsUtils.PREF_COMPACT_POINTS);
        includeTopLevelDomainPreference = findPreference(SettingsUtils.PREF_INCLUDE_TOP_LEVEL_DOMAIN);
        faviconProviderPreference = findPreference(SettingsUtils.PREF_FAVICON_PROVIDER);
        startingPagePreference = findPreference("pref_default_story_type");
        additionalFrontpagesPreference = findPreference(SettingsUtils.PREF_ADDITIONAL_FRONTPAGES);

        changePrefStatus(findPreference("pref_show_points"), !compact);
        updateCompactPointsPreference(!compact && SettingsUtils.shouldShowPoints(getContext()));
        changePrefStatus(includeTopLevelDomainPreference, !compact);
        changePrefStatus(findPreference("pref_show_comments_count"), !compact);
        changePrefStatus(findPreference("pref_thumbnails"), !compact);
        updateFaviconProviderPreference();
        updateGrayOutClickedPreference();
        updateTintCardUsingPreviewPreference();

        if (SettingsUtils.shouldShowThumbnails(getContext())) {
            changePrefStatus(faviconProviderPreference, !compact);
        } else {
            changePrefStatus(faviconProviderPreference, false);
        }

        if (faviconProviderPreference != null) {
            faviconProviderPreference.setOnPreferenceClickListener(preference -> {
                FaviconProviderDialogFragment.show(getParentFragmentManager());
                return true;
            });
        }

        findPreference("pref_compact_view").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateCompact((boolean) newValue);
            }
            changePrefStatus(findPreference("pref_show_points"), !(boolean) newValue);
            updateCompactPointsPreference(!(boolean) newValue && SettingsUtils.shouldShowPoints(getContext()));
            changePrefStatus(includeTopLevelDomainPreference, !(boolean) newValue);
            changePrefStatus(findPreference("pref_show_comments_count"), !(boolean) newValue);
            changePrefStatus(findPreference("pref_thumbnails"), !(boolean) newValue);
            changePrefStatus(faviconProviderPreference, !(boolean) newValue && SettingsUtils.shouldShowThumbnails(getContext()));
            return true;
        });

        findPreference("pref_story_preview_image_mode").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updatePreviewImageMode((String) newValue);
            }
            return true;
        });

        findPreference(SettingsUtils.PREF_STORY_DISPLAY_STYLE).setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateDisplayStyle((String) newValue);
            }
            return true;
        });

        findPreference(SettingsUtils.PREF_STORY_TEXT_SIZE).setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateTextSize((String) newValue);
            }
            return true;
        });

        findPreference("pref_thumbnails").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateThumbnails((boolean) newValue);
            }
            changePrefStatus(faviconProviderPreference, (boolean) newValue);
            return true;
        });

        findPreference("pref_show_points").setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updatePoints((boolean) newValue);
            }
            updateCompactPointsPreference((boolean) newValue && !SettingsUtils.shouldUseCompactView(getContext()));
            return true;
        });

        findPreference(SettingsUtils.PREF_COMPACT_POINTS).setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateCompactPoints((boolean) newValue);
            }
            return true;
        });

        findPreference(SettingsUtils.PREF_INCLUDE_TOP_LEVEL_DOMAIN).setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateIncludeTopLevelDomain((boolean) newValue);
            }
            refreshStoryWidgets();
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
            refreshStoryWidgets();
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

        if (startingPagePreference != null) {
            updateStartingPagePreference();

            startingPagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                SettingsCallback callback = getSettingsCallback();
                if (callback != null) {
                    callback.onRequestRestart();
                }
                return true;
            });
        }

        if (additionalFrontpagesPreference != null) {
            updateAdditionalFrontpagesPreference();
            additionalFrontpagesPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                Set<String> enabledFrontpages = SettingsUtils.sanitizeAdditionalFrontpages((Set<String>) newValue);
                additionalFrontpagesPreference.setSummary(SettingsUtils.summarizeAdditionalFrontpages(enabledFrontpages));
                updateStartingPagePreference(enabledFrontpages);
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

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateFaviconProviderPreference();
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SettingsUtils.PREF_FAVICON_PROVIDER.equals(key)) {
            updateFaviconProviderPreference();
        } else if (SettingsUtils.PREF_ADDITIONAL_FRONTPAGES.equals(key)) {
            updateAdditionalFrontpagesPreference();
            updateStartingPagePreference();
        }
    }

    private void updateStartingPagePreference() {
        updateStartingPagePreference(SettingsUtils.getEnabledAdditionalFrontpages(requireContext()));
    }

    private void updateStartingPagePreference(Set<String> enabledFrontpages) {
        if (startingPagePreference == null || getContext() == null) {
            return;
        }

        ArrayList<CharSequence> options = StoryType.buildStartingPageLabels(getResources(), enabledFrontpages);
        CharSequence[] optionArray = options.toArray(new CharSequence[0]);
        startingPagePreference.setEntries(optionArray);
        startingPagePreference.setEntryValues(optionArray);

        String startingPage = startingPagePreference.getValue();
        if (!options.contains(startingPage)
                || "Bookmarks".equals(startingPage)
                || "History".equals(startingPage)) {
            startingPagePreference.setValue("Top Stories");
        }
    }

    private void updateAdditionalFrontpagesPreference() {
        if (additionalFrontpagesPreference != null && getContext() != null) {
            Set<String> enabledFrontpages = SettingsUtils.sanitizeAdditionalFrontpages(additionalFrontpagesPreference.getValues());
            if (!enabledFrontpages.equals(additionalFrontpagesPreference.getValues())) {
                additionalFrontpagesPreference.setValues(enabledFrontpages);
            }
            additionalFrontpagesPreference.setSummary(SettingsUtils.summarizeAdditionalFrontpages(enabledFrontpages));
        }
    }

    private void updateGrayOutClickedPreference() {
        updateGrayOutClickedPreference(!SettingsUtils.shouldHideClicked(getContext()));
    }

    private void updateGrayOutClickedPreference(boolean enabled) {
        changePrefStatus(grayOutClickedPreference, enabled);
    }

    private void updateTintCardUsingPreviewPreference() {
        changePrefStatus(tintCardUsingPreviewPreference, true);
    }

    private void updateCompactPointsPreference(boolean enabled) {
        changePrefStatus(compactPointsPreference, enabled);
    }

    private void refreshStoryWidgets() {
        AppWidgetManager awm = AppWidgetManager.getInstance(requireContext());
        int[] ids = awm.getAppWidgetIds(
                new ComponentName(requireContext(), StoriesWidgetProvider.class));
        if (ids.length > 0) {
            StoriesRemoteViewsFactory.setSkipFetchAll(requireContext(), true);
            awm.notifyAppWidgetViewDataChanged(ids, R.id.widget_stories_list);
        }
    }

    private void updateFaviconProviderPreference() {
        if (faviconProviderPreference != null && getContext() != null) {
            String provider = SettingsUtils.getPreferredFaviconProvider(requireContext());
            faviconProviderPreference.setSummary(provider);
            faviconProviderPreference.setIcon(SettingsUtils.getFaviconProviderIconResource(provider));
        }
    }
}
