package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.AiSummaryTextDialogBinding;
import com.simon.harmonichackernews.network.AiSummaryProviders;

public class AiSummaryTextDialogFragment extends AppCompatDialogFragment {

    private static final String TAG = "ai_summary_text_dialog";
    private static final String PREF_BASE_URL = "pref_ai_summary_base_url";
    private static final String PREF_MODEL = "pref_ai_summary_model";
    private static final String ARG_KEY = "key";
    private static final String ARG_TITLE = "title";
    private static final String ARG_HINT = "hint";
    private static final String ARG_DEFAULT_VALUE = "default_value";
    private static final String ARG_INPUT_TYPE = "input_type";
    private static final String ARG_MIN_LINES = "min_lines";
    private static final String ARG_MAX_LINES = "max_lines";
    private static final String ARG_TEXT_SIZE_SP = "text_size_sp";
    private static final String ARG_TRIM_VALUE = "trim_value";
    private static final String ARG_ALLOW_EMPTY = "allow_empty";
    private static final String ARG_SHOW_RESET = "show_reset";
    private static final String STATE_VALUE = "value";

    private TextInputLayout inputLayout;
    private TextInputEditText inputEditText;
    private ChipGroup modelOptionsGroup;
    private TextWatcher modelTextWatcher;
    private boolean updatingModelOptionSelection;

    public static void show(
            androidx.fragment.app.FragmentManager fm,
            String key,
            String title,
            String hint,
            String defaultValue,
            int inputType,
            int minLines,
            int maxLines,
            int textSizeSp,
            boolean trimValue,
            boolean allowEmpty,
            boolean showReset) {
        AiSummaryTextDialogFragment fragment = new AiSummaryTextDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_HINT, hint);
        args.putString(ARG_DEFAULT_VALUE, defaultValue);
        args.putInt(ARG_INPUT_TYPE, inputType);
        args.putInt(ARG_MIN_LINES, minLines);
        args.putInt(ARG_MAX_LINES, maxLines);
        args.putInt(ARG_TEXT_SIZE_SP, textSizeSp);
        args.putBoolean(ARG_TRIM_VALUE, trimValue);
        args.putBoolean(ARG_ALLOW_EMPTY, allowEmpty);
        args.putBoolean(ARG_SHOW_RESET, showReset);
        fragment.setArguments(args);
        fragment.show(fm, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        Bundle args = requireArguments();
        AiSummaryTextDialogBinding binding =
                AiSummaryTextDialogBinding.inflate(getLayoutInflater());

        inputLayout = binding.aiSummaryTextInputLayout;
        inputEditText = binding.aiSummaryTextInput;
        modelOptionsGroup = binding.aiSummaryModelOptions;

        String title = args.getString(ARG_TITLE, "");
        String hint = args.getString(ARG_HINT, title);
        String defaultValue = args.getString(ARG_DEFAULT_VALUE, "");
        int inputType = args.getInt(ARG_INPUT_TYPE, InputType.TYPE_CLASS_TEXT);
        int minLines = args.getInt(ARG_MIN_LINES, 1);
        int maxLines = args.getInt(ARG_MAX_LINES, 1);
        int textSizeSp = args.getInt(ARG_TEXT_SIZE_SP, 16);

        inputLayout.setHint(hint);
        inputEditText.setInputType(inputType);
        inputEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);
        inputEditText.setMinLines(minLines);
        inputEditText.setMaxLines(maxLines);
        inputEditText.setSingleLine(maxLines <= 1);
        inputEditText.setImeOptions(maxLines <= 1
                ? EditorInfo.IME_ACTION_DONE
                : EditorInfo.IME_ACTION_NONE);

        String currentValue = savedInstanceState != null
                ? savedInstanceState.getString(STATE_VALUE, "")
                : getSavedValue(context, args.getString(ARG_KEY), defaultValue);
        setInputText(currentValue);
        configureModelOptions(binding, currentValue);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(binding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        if (args.getBoolean(ARG_SHOW_RESET, false)) {
            dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Reset", (dialogInterface, which) -> {
            });
        }

        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> saveAndDismiss());
            if (args.getBoolean(ARG_SHOW_RESET, false)) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> {
                    inputLayout.setError(null);
                    setInputText(defaultValue);
                });
            }
            inputEditText.requestFocus();
            inputEditText.setSelection(inputEditText.length());
        });
        return dialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_VALUE, getRawInputText());
    }

    @Override
    public void onDestroyView() {
        if (inputEditText != null && modelTextWatcher != null) {
            inputEditText.removeTextChangedListener(modelTextWatcher);
        }
        if (modelOptionsGroup != null) {
            modelOptionsGroup.setOnCheckedStateChangeListener(null);
        }
        modelOptionsGroup = null;
        modelTextWatcher = null;
        inputLayout = null;
        inputEditText = null;
        super.onDestroyView();
    }

    private void saveAndDismiss() {
        Bundle args = requireArguments();
        String value = getInputText();
        if (!args.getBoolean(ARG_ALLOW_EMPTY, true) && TextUtils.isEmpty(value)) {
            inputLayout.setError("Required");
            return;
        }

        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putString(args.getString(ARG_KEY), value)
                .apply();
        dismiss();
    }

    private void setInputText(String value) {
        inputEditText.setText(value);
        inputEditText.setSelection(inputEditText.length());
    }

    private void configureModelOptions(AiSummaryTextDialogBinding binding, String currentValue) {
        if (!PREF_MODEL.equals(requireArguments().getString(ARG_KEY))) {
            binding.aiSummaryModelOptionsLabel.setVisibility(View.GONE);
            binding.aiSummaryModelOptions.setVisibility(View.GONE);
            return;
        }

        AiSummaryProviders.Provider provider = getCurrentProvider();
        if (provider == null || provider.models.length == 0) {
            binding.aiSummaryModelOptionsLabel.setVisibility(View.GONE);
            binding.aiSummaryModelOptions.setVisibility(View.GONE);
            return;
        }

        binding.aiSummaryModelOptionsLabel.setText(provider.label + " options");
        binding.aiSummaryModelOptionsLabel.setVisibility(View.VISIBLE);
        binding.aiSummaryModelOptions.setVisibility(View.VISIBLE);
        binding.aiSummaryModelOptions.removeAllViews();

        for (AiSummaryProviders.Model model : provider.models) {
            Chip chip = new Chip(requireContext());
            chip.setId(View.generateViewId());
            chip.setText(model.label);
            chip.setTag(model.id);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setCheckedIconResource(R.drawable.ic_check_control_normal);
            chip.setCheckedIconVisible(true);
            chip.setChecked(model.id.equals(currentValue));
            binding.aiSummaryModelOptions.addView(chip);
        }

        binding.aiSummaryModelOptions.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (updatingModelOptionSelection || checkedIds.isEmpty()) {
                return;
            }
            View checkedView = group.findViewById(checkedIds.get(0));
            if (checkedView != null && checkedView.getTag() instanceof String) {
                inputLayout.setError(null);
                setInputText((String) checkedView.getTag());
            }
        });

        modelTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSelectedModelOption(String.valueOf(s));
            }
        };
        inputEditText.addTextChangedListener(modelTextWatcher);
        updateSelectedModelOption(currentValue);
    }

    private void updateSelectedModelOption(String modelId) {
        if (modelOptionsGroup == null) {
            return;
        }

        String normalizedModelId = modelId == null ? "" : modelId.trim();
        updatingModelOptionSelection = true;
        int matchingChipId = View.NO_ID;
        for (int i = 0; i < modelOptionsGroup.getChildCount(); i++) {
            View child = modelOptionsGroup.getChildAt(i);
            if (child.getTag() instanceof String
                    && ((String) child.getTag()).equals(normalizedModelId)) {
                matchingChipId = child.getId();
                break;
            }
        }

        if (matchingChipId == View.NO_ID) {
            modelOptionsGroup.clearCheck();
        } else {
            modelOptionsGroup.check(matchingChipId);
        }
        updatingModelOptionSelection = false;
    }

    @Nullable
    private AiSummaryProviders.Provider getCurrentProvider() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String baseUrl = prefs.getString(PREF_BASE_URL, AiSummaryProviders.getDefaultBaseUrl());
        return AiSummaryProviders.getProviderForBaseUrl(baseUrl);
    }

    private String getInputText() {
        String value = getRawInputText();
        if (requireArguments().getBoolean(ARG_TRIM_VALUE, true)) {
            return value.trim();
        }
        return value;
    }

    private String getRawInputText() {
        return String.valueOf(inputEditText.getText());
    }

    private static String getSavedValue(Context context, @Nullable String key, String defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(key, defaultValue);
    }
}
