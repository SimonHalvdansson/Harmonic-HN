package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.simon.harmonichackernews.databinding.AiSummaryTextDialogBinding;

public class AiSummaryTextDialogFragment extends AppCompatDialogFragment {
    private static final String TAG = "ai_summary_text_dialog";
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

        String title = args.getString(ARG_TITLE, "");
        String hint = args.getString(ARG_HINT, title);
        String defaultValue = args.getString(ARG_DEFAULT_VALUE, "");
        int maxLines = args.getInt(ARG_MAX_LINES, 1);
        inputLayout.setHint(hint);
        inputEditText.setInputType(args.getInt(ARG_INPUT_TYPE, InputType.TYPE_CLASS_TEXT));
        inputEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                args.getInt(ARG_TEXT_SIZE_SP, 16));
        inputEditText.setMinLines(args.getInt(ARG_MIN_LINES, 1));
        inputEditText.setMaxLines(maxLines);
        inputEditText.setSingleLine(maxLines <= 1);
        inputEditText.setImeOptions(maxLines <= 1
                ? EditorInfo.IME_ACTION_DONE
                : EditorInfo.IME_ACTION_NONE);

        String currentValue = savedInstanceState != null
                ? savedInstanceState.getString(STATE_VALUE, "")
                : getSavedValue(context, args.getString(ARG_KEY), defaultValue);
        setInputText(currentValue);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(binding.getRoot())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null);
        if (args.getBoolean(ARG_SHOW_RESET, false)) {
            builder.setNeutralButton("Reset", null);
        }
        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(dialogInterface -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> saveAndDismiss());
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

    private String getInputText() {
        String value = getRawInputText();
        return requireArguments().getBoolean(ARG_TRIM_VALUE, true) ? value.trim() : value;
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
