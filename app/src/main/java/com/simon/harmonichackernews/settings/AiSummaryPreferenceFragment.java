package com.simon.harmonichackernews.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.network.AiModelCatalog;
import com.simon.harmonichackernews.network.AiSummaryProviders;
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore;
import com.simon.harmonichackernews.utils.Utils;

public class AiSummaryPreferenceFragment extends BaseSettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PREF_ENABLED = "pref_ai_summary_enabled";
    private static final String PREF_MODE = "pref_ai_summary_mode";
    private static final String PREF_BASE_URL = "pref_ai_summary_base_url";
    private static final String PREF_API_KEY = AiSummaryApiKeyStore.PREF_API_KEY;
    private static final String PREF_MODEL = "pref_ai_summary_model";
    private static final String PREF_SYSTEM_PROMPT = "pref_ai_summary_system_prompt";
    private static final String DEFAULT_BASE_URL = AiSummaryProviders.getDefaultBaseUrl();
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant that is an expert on summarizing articles into an information-dense, concise and brief bullet-point list. Focus on key takeaways and most important/note-worthy points in the article. Keep the summary under 500 characters where possible. Respond in markdown format. Respond with only the summarized content - nothing else before or after.";

    private SwitchPreferenceCompat enablePreference;
    private Preference baseUrlPreference;
    private Preference apiKeyPreference;
    private Preference modelPreference;
    private Preference systemPromptPreference;
    private AiSummaryModePreference modePreference;

    @Override
    protected String getToolbarTitle() {
        return "AI summarization";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        AiModelCatalog.ensureInitialDefault(requireContext());
        boolean hadEnablePreference = prefs.contains(PREF_ENABLED);
        boolean defaultEnabled = isConfigurationComplete();

        setPreferencesFromResource(R.xml.preferences_ai_summary, rootKey);

        enablePreference = findPreference(PREF_ENABLED);
        modePreference = findPreference(PREF_MODE);
        baseUrlPreference = findPreference(PREF_BASE_URL);
        apiKeyPreference = findPreference(PREF_API_KEY);
        modelPreference = findPreference(PREF_MODEL);
        systemPromptPreference = findPreference(PREF_SYSTEM_PROMPT);

        getParentFragmentManager().setFragmentResultListener(
                AiSummaryTextDialogFragment.RESULT_KEY,
                this,
                (requestKey, result) -> {
                    String changedKey = result.getString(
                            AiSummaryTextDialogFragment.RESULT_PREFERENCE_KEY);
                    if (PREF_API_KEY.equals(changedKey)) {
                        updateApiKeySummary();
                        updateEnablePreferenceAvailability();
                        updateDependentPreferenceStates();
                    } else if (PREF_SYSTEM_PROMPT.equals(changedKey)) {
                        updateSystemPromptSummary();
                        updateEnablePreferenceAvailability();
                    }
                });

        if (enablePreference != null) {
            enablePreference.setChecked(hadEnablePreference
                    ? prefs.getBoolean(PREF_ENABLED, defaultEnabled)
                    : defaultEnabled);
            enablePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                if ((Boolean) newValue && !isConfigurationComplete()) {
                    return false;
                }
                updateDependentPreferenceStates();
                return true;
            });
        }

        if (modePreference != null) {
            modePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                updateDependentPreferenceStates((String) newValue);
                return true;
            });
        }

        if (baseUrlPreference != null) {
            baseUrlPreference.setOnPreferenceClickListener(preference -> {
                AiSummaryBaseUrlDialogFragment.show(getParentFragmentManager());
                return true;
            });
        }

        if (apiKeyPreference != null) {
            apiKeyPreference.setOnPreferenceClickListener(preference -> {
                AiSummaryTextDialogFragment.show(
                        getParentFragmentManager(),
                        PREF_API_KEY,
                        "API Key",
                        "API Key",
                        "",
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        1,
                        1,
                        16,
                        true,
                        true,
                        false);
                return true;
            });
        }

        if (modelPreference != null) {
            modelPreference.setOnPreferenceClickListener(preference -> {
                AiModelSelectorDialogFragment.show(getParentFragmentManager());
                return true;
            });
        }

        if (systemPromptPreference != null) {
            systemPromptPreference.setOnPreferenceClickListener(preference -> {
                AiSummaryTextDialogFragment.show(
                        getParentFragmentManager(),
                        PREF_SYSTEM_PROMPT,
                        "System prompt",
                        "System prompt",
                        DEFAULT_SYSTEM_PROMPT,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
                        5,
                        10,
                        15,
                        false,
                        true,
                        true);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .registerOnSharedPreferenceChangeListener(this);
        normalizeModelForCurrentProvider();
        String mode = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(PREF_MODE, AiSummaryModePreference.MODE_CLOUD);
        updateBaseUrlSummary();
        updateApiKeySummary();
        updateModelSummary();
        updateSystemPromptSummary();
        updateEnablePreferenceAvailability();
        updateDependentPreferenceStates(mode);
    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_ENABLED.equals(key)) {
            if (enablePreference != null) {
                enablePreference.setChecked(Utils.isAiSummaryEnabled(requireContext()));
            }
            updateEnablePreferenceAvailability();
            updateDependentPreferenceStates();
        } else if (PREF_BASE_URL.equals(key)) {
            updateBaseUrlSummary();
            updateEnablePreferenceAvailability();
        } else if (PREF_API_KEY.equals(key)) {
            updateApiKeySummary();
            updateEnablePreferenceAvailability();
        } else if (PREF_MODEL.equals(key)) {
            updateModelSummary();
            updateEnablePreferenceAvailability();
        } else if (PREF_MODE.equals(key)) {
            updateDependentPreferenceStates(sharedPreferences.getString(key, AiSummaryModePreference.MODE_CLOUD));
        } else if (PREF_SYSTEM_PROMPT.equals(key)) {
            updateSystemPromptSummary();
            updateEnablePreferenceAvailability();
        }
    }

    private void updateDependentPreferenceStates() {
        String mode = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(PREF_MODE, AiSummaryModePreference.MODE_CLOUD);
        updateDependentPreferenceStates(mode);
    }

    private void updateDependentPreferenceStates(String mode) {
        boolean cloudSettingsEnabled = AiSummaryModePreference.MODE_CLOUD.equals(mode);
        if (modePreference != null) modePreference.setControlsEnabled(true);
        if (baseUrlPreference != null) changePrefStatus(baseUrlPreference, cloudSettingsEnabled);
        if (apiKeyPreference != null) changePrefStatus(apiKeyPreference, cloudSettingsEnabled);
        if (modelPreference != null) changePrefStatus(modelPreference, cloudSettingsEnabled);
        if (systemPromptPreference != null) changePrefStatus(systemPromptPreference, cloudSettingsEnabled);
    }

    private void updateEnablePreferenceAvailability() {
        if (enablePreference == null || getContext() == null) {
            return;
        }

        boolean configurationComplete = isConfigurationComplete();
        if (!configurationComplete && enablePreference.isChecked()) {
            enablePreference.setChecked(false);
        }
        enablePreference.setEnabled(configurationComplete);
    }

    private boolean isConfigurationComplete() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String baseUrl = prefs.getString(PREF_BASE_URL, DEFAULT_BASE_URL);
        String apiKey = AiSummaryApiKeyStore.getApiKey(requireContext());
        String model = prefs.getString(PREF_MODEL, "");
        String systemPrompt = prefs.getString(PREF_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
        return !TextUtils.isEmpty(baseUrl == null ? "" : baseUrl.trim())
                && !TextUtils.isEmpty(apiKey.trim())
                && !TextUtils.isEmpty(model == null ? "" : model.trim())
                && !TextUtils.isEmpty(systemPrompt == null ? "" : systemPrompt.trim());
    }

    private void updateBaseUrlSummary() {
        if (baseUrlPreference != null && getContext() != null) {
            String url = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(PREF_BASE_URL, DEFAULT_BASE_URL);
            baseUrlPreference.setSummary(url);
        }
    }

    private void updateApiKeySummary() {
        if (apiKeyPreference != null && getContext() != null) {
            String key = AiSummaryApiKeyStore.getApiKey(requireContext());
            if (key.isEmpty()) {
                apiKeyPreference.setSummary("Not set");
            } else {
                apiKeyPreference.setSummary(key.substring(0, Math.min(8, key.length())) + "...");
            }
        }
    }

    private void updateSystemPromptSummary() {
        if (systemPromptPreference != null && getContext() != null) {
            String prompt = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(PREF_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
            systemPromptPreference.setSummary(prompt);
        }
    }

    private void updateModelSummary() {
        if (modelPreference != null && getContext() != null) {
            String model = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(PREF_MODEL, "");
            if (!model.isEmpty()) {
                modelPreference.setSummary(model);
            } else {
                modelPreference.setSummary("Finding a recommended model…");
            }
        }
    }

    private void normalizeModelForCurrentProvider() {
        if (getContext() == null) {
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String baseUrl = prefs.getString(PREF_BASE_URL, DEFAULT_BASE_URL);
        String model = prefs.getString(PREF_MODEL, "");
        String normalizedModel = AiSummaryProviders.getModelForRequest(baseUrl, model);
        if (!TextUtils.isEmpty(model) && !model.equals(normalizedModel)) {
            prefs.edit()
                    .putString(PREF_MODEL, normalizedModel)
                    .apply();
        }
    }
}
