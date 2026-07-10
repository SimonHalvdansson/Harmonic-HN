package com.simon.harmonichackernews.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class WebLinksPreferenceFragment extends BaseSettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private PreloadWebViewPreference preloadWebViewPreference;
    private Preference archiveRedirectPreference;
    private Preference readerModeFontPreference;

    @Override
    protected String getToolbarTitle() {
        return "Web and links";
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
        readerModeFontPreference = findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_FONT);
        if (readerModeFontPreference != null) {
            readerModeFontPreference.setOnPreferenceClickListener(preference -> {
                FontSelectionDialogFragment.showReaderMode(getParentFragmentManager());
                return true;
            });
        }

        updateWebViewDependentPreferenceStatus(integratedWebview, readerModeEnabled);

        SwitchPreferenceCompat redirectNitterPreference = findPreference("pref_redirect_nitter");
        findPreference("pref_link_preview_x").setOnPreferenceChangeListener((preference, newValue) -> {
            if ((boolean) newValue && !SettingsUtils.shouldRedirectNitter(requireContext())) {
                redirectNitterPreference.setChecked(true);
            }
            return true;
        });

        findPreference("pref_webview").setOnPreferenceChangeListener((preference, newValue) -> {
            boolean webViewEnabled = (boolean) newValue;
            boolean currentReaderModeEnabled = SettingsUtils.shouldUseReaderMode(getContext());
            updateWebViewDependentPreferenceStatus(webViewEnabled, currentReaderModeEnabled);
            return true;
        });

        findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_ENABLED).setOnPreferenceChangeListener((preference, newValue) -> {
            updateReaderModePreferenceStatus(SettingsUtils.shouldUseIntegratedWebView(getContext()) && (boolean) newValue);
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
        updateReaderModeFontSummary();
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
        } else if (SettingsUtils.PREF_WEBVIEW_READER_MODE_FONT.equals(key)) {
            updateReaderModeFontSummary();
        }
    }

    private void updateWebViewDependentPreferenceStatus(boolean integratedWebview, boolean readerModeEnabled) {
        changePrefStatus(preloadWebViewPreference, integratedWebview);
        changePrefStatus(findPreference("pref_webview_match_theme"), integratedWebview);
        changePrefStatus(findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_ENABLED), integratedWebview);
        updateReaderModePreferenceStatus(integratedWebview && readerModeEnabled);
        changePrefStatus(findPreference("pref_webview_adblock"), integratedWebview);
        changePrefStatus(findPreference("pref_close_webview_on_back"), integratedWebview);
    }

    private void updateReaderModePreferenceStatus(boolean enabled) {
        changePrefStatus(findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_DEFAULT), enabled);
        changePrefStatus(readerModeFontPreference, enabled);
        changePrefStatus(findPreference(SettingsUtils.PREF_WEBVIEW_READER_MODE_FONT_SIZE), enabled);
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

    private void updateReaderModeFontSummary() {
        if (readerModeFontPreference != null && getContext() != null) {
            readerModeFontPreference.setSummary(SettingsUtils.getPreferredReaderModeFontLabel(requireContext()));
        }
    }
}
