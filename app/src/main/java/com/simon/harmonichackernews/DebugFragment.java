package com.simon.harmonichackernews;

import android.os.Build;
import android.os.Bundle;

import androidx.preference.Preference;

import com.simon.harmonichackernews.settings.BaseSettingsFragment;
import com.simon.harmonichackernews.settings.DebugHnIdPreference;
import com.simon.harmonichackernews.utils.Utils;

import java.util.Locale;

public class DebugFragment extends BaseSettingsFragment {

    private static final String PREF_BUILD_INFO = "pref_debug_build_info";
    private static final String PREF_LINK_POST = "pref_debug_link_post";
    private static final String PREF_REFERENCE_LINKS_POST = "pref_debug_reference_links_post";
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

        Preference buildInfo = findPreference(PREF_BUILD_INFO);
        if (buildInfo != null) {
            buildInfo.setSummary(String.format(Locale.US,
                    "Version %s · Build %d · %s\nAndroid %s (API %d)",
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                    BuildConfig.BUILD_TYPE,
                    Build.VERSION.RELEASE,
                    Build.VERSION.SDK_INT));
        }

        setLinkPreference(PREF_LINK_POST, "https://news.ycombinator.com/item?id=47938725");
        setLinkPreference(PREF_REFERENCE_LINKS_POST, "https://news.ycombinator.com/item?id=48352939");
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
}
