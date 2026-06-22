package com.simon.harmonichackernews.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreferenceCompat;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.SummaryManager;
import com.simon.harmonichackernews.utils.Utils;

public class AiSummaryPreferenceFragment extends BaseSettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PREF_ENABLED = "pref_ai_summary_enabled";
    private static final String PREF_MODE = "pref_ai_summary_mode";
    private static final String PREF_BASE_URL = "pref_ai_summary_base_url";
    private static final String PREF_API_KEY = "pref_ai_summary_api_key";
    private static final String PREF_MODEL = "pref_ai_summary_model";
    private static final String PREF_SYSTEM_PROMPT = "pref_ai_summary_system_prompt";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    private static final String DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant that is an expert on summarizing articles into an information-dense, concise and brief bullet-point list. Focus on key takeaways and most important/note-worthy points in the article. Keep the summary under 500 characters where possible. Respond in markdown format. Respond with only the summarized content - nothing else before or after.";

    private SwitchPreferenceCompat enablePreference;
    private Preference baseUrlPreference;
    private Preference apiKeyPreference;
    private Preference modelPreference;
    private Preference systemPromptPreference;
    private AiSummaryModePreference modePreference;

    @Override
    protected String getToolbarTitle() {
        return "AI Summarization";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        boolean hadEnablePreference = prefs.contains(PREF_ENABLED);
        boolean defaultEnabled = SummaryManager.canAttemptLocalSummarization()
                || !prefs.getString(PREF_API_KEY, "").isEmpty();

        setPreferencesFromResource(R.xml.preferences_ai_summary, rootKey);

        enablePreference = findPreference(PREF_ENABLED);
        modePreference = findPreference(PREF_MODE);
        baseUrlPreference = findPreference(PREF_BASE_URL);
        apiKeyPreference = findPreference(PREF_API_KEY);
        modelPreference = findPreference(PREF_MODEL);
        systemPromptPreference = findPreference(PREF_SYSTEM_PROMPT);

        if (enablePreference != null) {
            enablePreference.setChecked(hadEnablePreference
                    ? prefs.getBoolean(PREF_ENABLED, defaultEnabled)
                    : defaultEnabled);
            enablePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                updateDependentPreferenceStates((Boolean) newValue);
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
                AiSummaryTextDialogFragment.show(
                        getParentFragmentManager(),
                        PREF_MODEL,
                        "Model",
                        "Model",
                        DEFAULT_MODEL,
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                        1,
                        1,
                        16,
                        true,
                        false,
                        false);
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
        String mode = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(PREF_MODE, AiSummaryModePreference.MODE_CLOUD);
        updateDependentPreferenceStates(Utils.isAiSummaryEnabled(requireContext()), mode);
        updateBaseUrlSummary();
        updateApiKeySummary();
        updateModelSummary();
        updateSystemPromptSummary();
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
            updateDependentPreferenceStates();
        } else if (PREF_BASE_URL.equals(key)) {
            updateBaseUrlSummary();
        } else if (PREF_API_KEY.equals(key)) {
            updateApiKeySummary();
            String apiKey = sharedPreferences.getString(PREF_API_KEY, "");
            if (!TextUtils.isEmpty(apiKey) && getContext() != null) {
                suggestModel();
            }
        } else if (PREF_MODEL.equals(key)) {
            updateModelSummary();
        } else if (PREF_MODE.equals(key)) {
            updateDependentPreferenceStates(sharedPreferences.getString(key, AiSummaryModePreference.MODE_CLOUD));
        } else if (PREF_SYSTEM_PROMPT.equals(key)) {
            updateSystemPromptSummary();
        }
    }

    private void updateDependentPreferenceStates() {
        String mode = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(PREF_MODE, AiSummaryModePreference.MODE_CLOUD);
        updateDependentPreferenceStates(Utils.isAiSummaryEnabled(requireContext()), mode);
    }

    private void updateDependentPreferenceStates(String mode) {
        updateDependentPreferenceStates(Utils.isAiSummaryEnabled(requireContext()), mode);
    }

    private void updateDependentPreferenceStates(boolean aiEnabled) {
        String mode = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(PREF_MODE, AiSummaryModePreference.MODE_CLOUD);
        updateDependentPreferenceStates(aiEnabled, mode);
    }

    private void updateDependentPreferenceStates(boolean aiEnabled, String mode) {
        boolean cloudSettingsEnabled = aiEnabled && AiSummaryModePreference.MODE_CLOUD.equals(mode);
        if (modePreference != null) modePreference.setControlsEnabled(aiEnabled);
        if (baseUrlPreference != null) changePrefStatus(baseUrlPreference, cloudSettingsEnabled);
        if (apiKeyPreference != null) changePrefStatus(apiKeyPreference, cloudSettingsEnabled);
        if (modelPreference != null) changePrefStatus(modelPreference, cloudSettingsEnabled);
        if (systemPromptPreference != null) changePrefStatus(systemPromptPreference, cloudSettingsEnabled);
    }

    private void suggestModel() {
        SummaryManager.fetchModels(requireContext(), NetworkComponent.getRequestQueueInstance(requireContext()),
                new SummaryManager.SummaryCallback() {
                    @Override
                    public void onSuccess(String models) {
                        if (getContext() == null || modelPreference == null) return;
                        String[] modelArray = models.split(",");
                        if (modelArray.length > 0) {
                            String currentModel = PreferenceManager.getDefaultSharedPreferences(requireContext())
                                    .getString(PREF_MODEL, "");
                            if (currentModel.isEmpty() || DEFAULT_MODEL.equals(currentModel)) {
                                PreferenceManager.getDefaultSharedPreferences(requireContext())
                                        .edit()
                                        .putString(PREF_MODEL, modelArray[0])
                                        .apply();
                                updateModelSummary();
                            }
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                    }
                });
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
            String key = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString(PREF_API_KEY, "");
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
                    .getString(PREF_MODEL, DEFAULT_MODEL);
            if (!model.isEmpty()) {
                modelPreference.setSummary(model);
            } else {
                modelPreference.setSummary("Not set");
            }
        }
    }
}
