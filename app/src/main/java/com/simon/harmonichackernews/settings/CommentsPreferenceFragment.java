package com.simon.harmonichackernews.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.Preference;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class CommentsPreferenceFragment extends BaseSettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Preference threadDepthIndicatorsPreference;
    private CommentContentPreviewPreference previewPreference;
    private Preference enableHeaderTintPreference;
    private Preference enableHeaderPreviewImagePreference;

    @Override
    protected String getToolbarTitle() {
        return "Comments";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_comments, rootKey);

        previewPreference = findPreference("pref_comment_content_preview");
        threadDepthIndicatorsPreference = findPreference(SettingsUtils.PREF_COMMENT_DEPTH_INDICATORS);
        enableHeaderTintPreference = findPreference(SettingsUtils.PREF_ENABLE_COMMENTS_HEADER_TINT);
        enableHeaderPreviewImagePreference = findPreference(SettingsUtils.PREF_ENABLE_COMMENTS_HEADER_PREVIEW_IMAGE);
        if (threadDepthIndicatorsPreference != null) {
            updateThreadDepthIndicatorsSummary();
            threadDepthIndicatorsPreference.setOnPreferenceClickListener(preference -> {
                ThreadDepthIndicatorsDialogFragment.show(getParentFragmentManager());
                return true;
            });
        }

        findPreference(SettingsUtils.PREF_COMMENT_DISPLAY_STYLE).setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateDisplayStyle((String) newValue);
            }
            return true;
        });

        findPreference(SettingsUtils.PREF_COMMENT_TEXT_SIZE).setOnPreferenceChangeListener((preference, newValue) -> {
            if (previewPreference != null) {
                previewPreference.updateTextSize((String) newValue);
            }
            return true;
        });

        updateHeaderDisplayPreferenceStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateThreadDepthIndicatorsSummary();
        updateHeaderDisplayPreferenceStatus();
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SettingsUtils.PREF_COMMENT_DEPTH_INDICATORS.equals(key)
                || SettingsUtils.PREF_MONOCHROME_COMMENT_DEPTH.equals(key)) {
            updateThreadDepthIndicatorsSummary();
        } else if (SettingsUtils.PREF_TINT_CARD_USING_PREVIEW.equals(key)
                || SettingsUtils.PREF_STORY_PREVIEW_IMAGE_MODE.equals(key)) {
            updateHeaderDisplayPreferenceStatus();
        }
    }

    private void updateThreadDepthIndicatorsSummary() {
        if (threadDepthIndicatorsPreference == null || getContext() == null) {
            return;
        }

        threadDepthIndicatorsPreference.setSummary(CommentDepthIndicatorUtils.getModeLabel(
                SettingsUtils.getPreferredCommentDepthIndicatorMode(requireContext())));
    }

    private void updateHeaderDisplayPreferenceStatus() {
        if (getContext() == null) {
            return;
        }

        changePrefStatus(enableHeaderTintPreference, SettingsUtils.shouldTintCardUsingPreview(requireContext()));
        changePrefStatus(
                enableHeaderPreviewImagePreference,
                !SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(SettingsUtils.getPreferredStoryPreviewImageMode(requireContext())));
    }
}
