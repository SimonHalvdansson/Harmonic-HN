package com.simon.harmonichackernews;

import android.os.Build;
import android.os.Bundle;

import androidx.preference.Preference;

import com.simon.harmonichackernews.settings.BaseSettingsFragment;
import com.simon.harmonichackernews.settings.DebugHnIdPreference;
import com.simon.harmonichackernews.utils.Utils;

public class DebugFragment extends BaseSettingsFragment {

    private static final String PREF_APP_VERSION = "pref_debug_app_version";
    private static final String PREF_APP_BUILD = "pref_debug_app_build";
    private static final String PREF_BUILD_VERSION = "pref_debug_build_version";
    private static final String PREF_ANDROID_VERSION = "pref_debug_android_version";
    private static final String PREF_LINK_POST = "pref_debug_link_post";
    private static final String PREF_REFERENCE_LINKS_POST = "pref_debug_reference_links_post";
    private static final String PREF_YOUTUBE_COMMENT = "pref_debug_youtube_comment";
    private static final String PREF_POLL = "pref_debug_poll";
    private static final String PREF_INTERNAL_HN_LINK = "pref_debug_internal_hn_link";
    private static final String PREF_NITTER_VIDEO = "pref_debug_nitter_video";
    private static final String PREF_WELCOME_DIALOG = "pref_debug_welcome_dialog";
    private static final String PREF_NOTIFICATIONS = "pref_debug_notifications";
    private static final String PREF_OPEN_HN_ID = "pref_debug_open_hn_id";

    @Override
    protected String getToolbarTitle() {
        return "Debug";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_debug, rootKey);

        setSummary(PREF_APP_VERSION, BuildConfig.VERSION_NAME);
        setSummary(PREF_APP_BUILD, String.valueOf(BuildConfig.VERSION_CODE));
        setSummary(PREF_BUILD_VERSION, BuildConfig.BUILD_TYPE);
        setSummary(PREF_ANDROID_VERSION,
                Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");

        setLinkPreference(PREF_LINK_POST, "https://news.ycombinator.com/item?id=47938725");
        setLinkPreference(PREF_REFERENCE_LINKS_POST, "https://news.ycombinator.com/item?id=48352939");
        setLinkPreference(PREF_YOUTUBE_COMMENT, "https://news.ycombinator.com/item?id=34225887");
        setLinkPreference(PREF_POLL, "https://news.ycombinator.com/item?id=39572682");
        setLinkPreference(PREF_INTERNAL_HN_LINK, "https://news.ycombinator.com/item?id=30676384");
        setLinkPreference(PREF_NITTER_VIDEO, "https://news.ycombinator.com/item?id=48012735");

        Preference welcomeDialog = findPreference(PREF_WELCOME_DIALOG);
        if (welcomeDialog != null) {
            welcomeDialog.setOnPreferenceClickListener(preference -> {
                WelcomeDialogFragment.show(getParentFragmentManager(), false);
                return true;
            });
        }

        Preference notifications = findPreference(PREF_NOTIFICATIONS);
        if (notifications != null) {
            notifications.setOnPreferenceClickListener(preference -> {
                DebugNotificationsDialogFragment.show(getParentFragmentManager());
                return true;
            });
        }

        DebugHnIdPreference hnIdPreference = findPreference(PREF_OPEN_HN_ID);
        if (hnIdPreference != null) {
            hnIdPreference.setOnOpenIdListener(id ->
                    Utils.openCommentsActivity(id, -1, requireContext()));
        }
    }

    private void setLinkPreference(String key, String url) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setOnPreferenceClickListener(clickedPreference -> {
                Utils.openLinkMaybeHN(requireActivity(), url);
                return true;
            });
        }
    }

    private void setSummary(String key, String summary) {
        Preference preference = findPreference(key);
        if (preference != null) {
            preference.setSummary(summary);
        }
    }
}
