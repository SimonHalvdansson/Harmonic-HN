package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.AiSummaryBaseUrlDialogBinding;
import com.simon.harmonichackernews.network.AiModelCatalog;
import com.simon.harmonichackernews.network.AiSummaryProviders;

public class AiSummaryBaseUrlDialogFragment extends AppCompatDialogFragment {

    private static final String TAG = "ai_summary_base_url_dialog";
    private static final String STATE_URL = "url";

    private static final Preset[] PRESETS = new Preset[]{
            new Preset(R.id.ai_summary_base_url_openai, AiSummaryProviders.OPENAI),
            new Preset(R.id.ai_summary_base_url_anthropic, AiSummaryProviders.ANTHROPIC),
            new Preset(R.id.ai_summary_base_url_openrouter, AiSummaryProviders.OPENROUTER),
            new Preset(R.id.ai_summary_base_url_google, AiSummaryProviders.GOOGLE)
    };

    private ChipGroup presetGroup;
    private TextInputLayout inputLayout;
    private TextInputEditText inputEditText;
    private TextWatcher textWatcher;
    private boolean updatingInput;

    public static void show(androidx.fragment.app.FragmentManager fm) {
        new AiSummaryBaseUrlDialogFragment().show(fm, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        AiSummaryBaseUrlDialogBinding binding =
                AiSummaryBaseUrlDialogBinding.inflate(getLayoutInflater());

        presetGroup = binding.aiSummaryBaseUrlPresets;
        inputLayout = binding.aiSummaryBaseUrlInputLayout;
        inputEditText = binding.aiSummaryBaseUrlInput;

        String currentUrl = savedInstanceState != null
                ? savedInstanceState.getString(STATE_URL, "")
                : getSavedBaseUrl(context);
        setInputUrl(currentUrl);
        updatePresetSelection(currentUrl);

        presetGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (updatingInput) {
                return;
            }
            if (checkedIds.isEmpty()) {
                updatePresetSelection(getInputUrl());
                return;
            }

            String presetUrl = getPresetUrl(checkedIds.get(0));
            if (presetUrl != null) {
                inputLayout.setError(null);
                setInputUrl(presetUrl);
            }
        });

        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!updatingInput) {
                    inputLayout.setError(null);
                    updatePresetSelection(String.valueOf(s));
                }
            }
        };
        inputEditText.addTextChangedListener(textWatcher);
        inputEditText.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveAndDismiss();
                return true;
            }
            return false;
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Base URL")
                .setView(binding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> saveAndDismiss());
            inputEditText.requestFocus();
            inputEditText.setSelection(inputEditText.length());
        });
        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_URL, getInputUrl());
    }

    @Override
    public void onDestroyView() {
        if (inputEditText != null) {
            if (textWatcher != null) {
                inputEditText.removeTextChangedListener(textWatcher);
            }
            inputEditText.setOnEditorActionListener(null);
        }
        if (presetGroup != null) {
            presetGroup.setOnCheckedStateChangeListener(null);
        }

        presetGroup = null;
        inputLayout = null;
        inputEditText = null;
        textWatcher = null;

        super.onDestroyView();
    }

    private void saveAndDismiss() {
        String url = getInputUrl();
        if (TextUtils.isEmpty(url)) {
            inputLayout.setError("Enter a base URL");
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        AiSummaryProviders.Provider oldProvider = AiSummaryProviders.getProviderForBaseUrl(
                prefs.getString("pref_ai_summary_base_url", AiSummaryProviders.getDefaultBaseUrl()));
        AiSummaryProviders.Provider newProvider = AiSummaryProviders.getProviderForBaseUrl(url);
        SharedPreferences.Editor editor = prefs.edit()
                .putString("pref_ai_summary_base_url", url);
        if (newProvider != null && (oldProvider == null || !newProvider.id.equals(oldProvider.id))) {
            String translatedModel = oldProvider == null ? "" : AiSummaryProviders.translateModelId(
                    oldProvider, newProvider, prefs.getString("pref_ai_summary_model", ""));
            if (translatedModel.isEmpty()) {
                editor.remove("pref_ai_summary_model");
            } else {
                editor.putString("pref_ai_summary_model", translatedModel);
            }
        }
        editor.apply();
        if (newProvider != null && !prefs.contains("pref_ai_summary_model")) {
            AiModelCatalog.ensureProviderDefault(requireContext(), newProvider);
        }
        dismiss();
    }

    private void setInputUrl(String url) {
        updatingInput = true;
        inputEditText.setText(url);
        inputEditText.setSelection(inputEditText.length());
        updatePresetSelection(url);
        updatingInput = false;
    }

    private String getInputUrl() {
        return String.valueOf(inputEditText.getText()).trim();
    }

    private void updatePresetSelection(String url) {
        int presetId = getPresetId(url);
        if (presetId == presetGroup.getCheckedChipId()) {
            return;
        }

        if (presetId == ViewId.NONE) {
            presetGroup.clearCheck();
        } else {
            presetGroup.check(presetId);
        }
    }

    private static String getSavedBaseUrl(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString("pref_ai_summary_base_url", AiSummaryProviders.getDefaultBaseUrl());
    }

    private static int getPresetId(String url) {
        String normalizedUrl = AiSummaryProviders.normalizeUrl(url);
        for (Preset preset : PRESETS) {
            if (AiSummaryProviders.normalizeUrl(preset.provider.baseUrl).equals(normalizedUrl)) {
                return preset.chipId;
            }
        }
        return ViewId.NONE;
    }

    @Nullable
    private static String getPresetUrl(int chipId) {
        for (Preset preset : PRESETS) {
            if (preset.chipId == chipId) {
                return preset.provider.baseUrl;
            }
        }
        return null;
    }

    private static class Preset {
        final int chipId;
        final AiSummaryProviders.Provider provider;

        Preset(int chipId, AiSummaryProviders.Provider provider) {
            this.chipId = chipId;
            this.provider = provider;
        }
    }

    private static class ViewId {
        static final int NONE = -1;
    }
}
