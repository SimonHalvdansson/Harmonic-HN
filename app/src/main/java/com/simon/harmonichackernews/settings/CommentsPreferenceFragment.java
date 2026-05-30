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

    @Override
    protected String getToolbarTitle() {
        return "Comments";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_comments, rootKey);

        previewPreference = findPreference("pref_comment_content_preview");
        threadDepthIndicatorsPreference = findPreference(SettingsUtils.PREF_COMMENT_DEPTH_INDICATORS);
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
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        updateThreadDepthIndicatorsSummary();
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
        }
    }

    private void updateThreadDepthIndicatorsSummary() {
        if (threadDepthIndicatorsPreference == null || getContext() == null) {
            return;
        }

        threadDepthIndicatorsPreference.setSummary(CommentDepthIndicatorUtils.getModeLabel(
                SettingsUtils.getPreferredCommentDepthIndicatorMode(requireContext())));
    }
}
