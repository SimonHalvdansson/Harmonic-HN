package com.simon.harmonichackernews.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class WebLinksPreferenceFragment extends BaseSettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private PreloadWebViewPreference preloadWebViewPreference;
    private Preference archiveRedirectPreference;

    @Override
    protected String getToolbarTitle() {
        return "Web & Links";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_web_links, rootKey);

        boolean integratedWebview = SettingsUtils.shouldUseIntegratedWebView(getContext());
        boolean readerModeEnabled = SettingsUtils.shouldUseReaderMode(getContext());

        preloadWebViewPreference = findPreference(SettingsUtils.PREF_PRELOAD_WEBVIEW);
        if (preloadWebViewPreference != null) {
            preloadWebViewPreference.setOnPreferenceClickListener(preference -> {
                PreloadWebViewDialogFragment.show(getParentFragmentManager());
                return true;
            });
        }
        archiveRedirectPreference = findPreference(SettingsUtils.PREF_ARCHIVE_REDIRECT_DOMAINS);
        if (archiveRedirectPreference != null) {
            archiveRedirectPreference.setOnPreferenceClickListener(preference -> {
                ArchiveRedirectDomainsDialogFragment.show(getParentFragmentManager());
                return true;
            });
        }

        changePrefStatus(preloadWebViewPreference, integratedWebview);
        changePrefStatus(findPreference("pref_webview_match_theme"), integratedWebview);
        changePrefStatus(findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_ENABLED), integratedWebview);
        changePrefStatus(findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_DEFAULT), integratedWebview && readerModeEnabled);
        changePrefStatus(findPreference("pref_webview_adblock"), integratedWebview);
        changePrefStatus(findPreference("pref_close_webview_on_back"), integratedWebview);

        boolean redirectNitter = SettingsUtils.shouldRedirectNitter(getContext());

        findPreference("pref_link_preview_x").setSummary(redirectNitter ? "" : "Requires Nitter redirect to be active");
        changePrefStatus(findPreference("pref_link_preview_x"), redirectNitter);

        findPreference("pref_webview").setOnPreferenceChangeListener((preference, newValue) -> {
            boolean webViewEnabled = (boolean) newValue;
            boolean currentReaderModeEnabled = SettingsUtils.shouldUseReaderMode(getContext());
            changePrefStatus(preloadWebViewPreference, webViewEnabled);
            changePrefStatus(findPreference("pref_webview_match_theme"), webViewEnabled);
            changePrefStatus(findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_ENABLED), webViewEnabled);
            changePrefStatus(findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_DEFAULT), webViewEnabled && currentReaderModeEnabled);
            changePrefStatus(findPreference("pref_webview_adblock"), webViewEnabled);
            changePrefStatus(findPreference("pref_close_webview_on_back"), webViewEnabled);
            return true;
        });

        findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_ENABLED).setOnPreferenceChangeListener((preference, newValue) -> {
            changePrefStatus(findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_DEFAULT),
                    SettingsUtils.shouldUseIntegratedWebView(getContext()) && (boolean) newValue);
            return true;
        });

        findPreference("pref_redirect_nitter").setOnPreferenceChangeListener((preference, newValue) -> {
            changePrefStatus(findPreference("pref_link_preview_x"), (boolean) newValue);
            findPreference("pref_link_preview_x").setSummary((boolean) newValue ? "" : "Requires Nitter redirect to be active");
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(this);
        updatePreloadWebViewSummary();
        updateArchiveRedirectSummary();
    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SettingsUtils.PREF_PRELOAD_WEBVIEW.equals(key)
                || SettingsUtils.PREF_PRELOAD_WEBVIEW_MINIMUM_BATTERY.equals(key)) {
            updatePreloadWebViewSummary();
        } else if (SettingsUtils.PREF_ARCHIVE_REDIRECT_DOMAINS.equals(key)) {
            updateArchiveRedirectSummary();
        }
    }

    private void updatePreloadWebViewSummary() {
        if (preloadWebViewPreference != null) {
            preloadWebViewPreference.updateSummary();
        }
    }

    private void updateArchiveRedirectSummary() {
        if (archiveRedirectPreference == null || getContext() == null) {
            return;
        }

        int domainCount = SettingsUtils.getArchiveRedirectDomains(getContext()).size();
        if (domainCount == 0) {
            archiveRedirectPreference.setSummary("No domains");
        } else if (domainCount == 1) {
            archiveRedirectPreference.setSummary("1 domain");
        } else {
            archiveRedirectPreference.setSummary(domainCount + " domains");
        }
    }
}
