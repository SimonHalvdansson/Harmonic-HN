package com.simon.harmonichackernews.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class WebLinksPreferenceFragment extends BaseSettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private PreloadWebViewPreference preloadWebViewPreference;

    @Override
    protected String getToolbarTitle() {
        return "Web & Links";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_web_links, rootKey);

        boolean integratedWebview = SettingsUtils.shouldUseIntegratedWebView(getContext());

        preloadWebViewPreference = findPreference(SettingsUtils.PREF_PRELOAD_WEBVIEW);
        if (preloadWebViewPreference != null) {
            preloadWebViewPreference.setOnPreferenceClickListener(preference -> {
                PreloadWebViewDialogFragment.show(getParentFragmentManager());
                return true;
            });
        }

        changePrefStatus(preloadWebViewPreference, integratedWebview);
        changePrefStatus(findPreference("pref_webview_match_theme"), integratedWebview);
        changePrefStatus(findPreference("pref_webview_adblock"), integratedWebview);
        changePrefStatus(findPreference("pref_close_webview_on_back"), integratedWebview);

        boolean redirectNitter = SettingsUtils.shouldRedirectNitter(getContext());

        findPreference("pref_link_preview_x").setSummary(redirectNitter ? "" : "Requires Nitter redirect to be active");
        changePrefStatus(findPreference("pref_link_preview_x"), redirectNitter);

        findPreference("pref_webview").setOnPreferenceChangeListener((preference, newValue) -> {
            changePrefStatus(preloadWebViewPreference, (boolean) newValue);
            changePrefStatus(findPreference("pref_webview_match_theme"), (boolean) newValue);
            changePrefStatus(findPreference("pref_webview_adblock"), (boolean) newValue);
            changePrefStatus(findPreference("pref_close_webview_on_back"), (boolean) newValue);
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
        }
    }

    private void updatePreloadWebViewSummary() {
        if (preloadWebViewPreference != null) {
            preloadWebViewPreference.updateSummary();
        }
    }
}
