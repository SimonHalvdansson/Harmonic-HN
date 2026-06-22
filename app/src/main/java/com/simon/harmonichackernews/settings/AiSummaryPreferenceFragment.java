package com.simon.harmonichackernews.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.SummaryManager;

public class AiSummaryPreferenceFragment extends BaseSettingsFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Preference baseUrlPreference;
    private EditTextPreference apiKeyPreference;
    private EditTextPreference modelPreference;
    private EditTextPreference systemPromptPreference;
    private AiSummaryModePreference modePreference;

    @Override
    protected String getToolbarTitle() {
        return "AI Summarization";
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_ai_summary, rootKey);

        modePreference = findPreference("pref_ai_summary_mode");
        baseUrlPreference = findPreference("pref_ai_summary_base_url");
        apiKeyPreference = findPreference("pref_ai_summary_api_key");
        modelPreference = findPreference("pref_ai_summary_model");
        systemPromptPreference = findPreference("pref_ai_summary_system_prompt");

        if (modePreference != null) {
            modePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                updateCloudOptionsVisibility((String) newValue);
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
            apiKeyPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String key = (String) newValue;
                if (!key.isEmpty() && getContext() != null) {
                    suggestModel();
                }
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
                .getString("pref_ai_summary_mode", AiSummaryModePreference.MODE_CLOUD);
        updateCloudOptionsVisibility(mode);
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
        if ("pref_ai_summary_base_url".equals(key)) {
            updateBaseUrlSummary();
        } else if ("pref_ai_summary_api_key".equals(key)) {
            updateApiKeySummary();
        } else if ("pref_ai_summary_model".equals(key)) {
            updateModelSummary();
        } else if ("pref_ai_summary_mode".equals(key)) {
            updateCloudOptionsVisibility(sharedPreferences.getString(key, AiSummaryModePreference.MODE_CLOUD));
        } else if ("pref_ai_summary_system_prompt".equals(key)) {
            updateSystemPromptSummary();
        }
    }

    private void updateCloudOptionsVisibility(String mode) {
        boolean isCloud = AiSummaryModePreference.MODE_CLOUD.equals(mode);
        if (baseUrlPreference != null) changePrefStatus(baseUrlPreference, isCloud);
        if (apiKeyPreference != null) changePrefStatus(apiKeyPreference, isCloud);
        if (modelPreference != null) changePrefStatus(modelPreference, isCloud);
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
                                    .getString("pref_ai_summary_model", "");
                            if (currentModel.isEmpty() || "gpt-3.5-turbo".equals(currentModel)) {
                                PreferenceManager.getDefaultSharedPreferences(requireContext())
                                        .edit()
                                        .putString("pref_ai_summary_model", modelArray[0])
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
                    .getString("pref_ai_summary_base_url", "https://api.openai.com/v1");
            baseUrlPreference.setSummary(url);
        }
    }

    private void updateApiKeySummary() {
        if (apiKeyPreference != null && getContext() != null) {
            String key = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString("pref_ai_summary_api_key", "");
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
                    .getString("pref_ai_summary_system_prompt", "");
            if (prompt.length() > 60) {
                systemPromptPreference.setSummary(prompt.substring(0, 60) + "...");
            } else {
                systemPromptPreference.setSummary(prompt);
            }
        }
    }

    private void updateModelSummary() {
        if (modelPreference != null && getContext() != null) {
            String model = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString("pref_ai_summary_model", "");
            if (!model.isEmpty()) {
                modelPreference.setSummary(model);
                modelPreference.setText(model);
            }
        }
    }
}
