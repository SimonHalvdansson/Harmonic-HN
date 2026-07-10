package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.TextView;

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
import com.simon.harmonichackernews.network.NetworkComponent;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

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
    private TextWatcher modelPricesTextWatcher;
    private boolean updatingModelOptionSelection;
    private Button modelPricesButton;
    private View modelPricesResult;
    private View modelPricesContent;
    private TextView modelPricesValue;
    private TextView modelPricesStatus;
    private Call modelPriceCall;
    private String lastPricedModelId;

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
        modelPricesButton = binding.aiSummaryModelPricesButton;
        modelPricesResult = binding.aiSummaryModelPricesResult;
        modelPricesContent = binding.aiSummaryModelPricesContent;
        modelPricesValue = binding.aiSummaryModelPricesValue;
        modelPricesStatus = binding.aiSummaryModelPricesStatus;

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
        lastPricedModelId = currentValue.trim();
        setInputText(currentValue);
        configureModelOptions(binding, currentValue);
        configureModelPrices();

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
            if (shouldShowModelPrices()
                    && buildOpenRouterModelUrl(getInputText()) != null) {
                checkOpenRouterModelPrices();
            }
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
        if (inputEditText != null && modelPricesTextWatcher != null) {
            inputEditText.removeTextChangedListener(modelPricesTextWatcher);
        }
        if (modelPricesContent != null) {
            modelPricesContent.animate().cancel();
        }
        if (modelPriceCall != null) {
            modelPriceCall.cancel();
            modelPriceCall = null;
        }
        if (modelOptionsGroup != null) {
            modelOptionsGroup.setOnCheckedStateChangeListener(null);
        }
        modelOptionsGroup = null;
        modelTextWatcher = null;
        modelPricesTextWatcher = null;
        modelPricesButton = null;
        modelPricesResult = null;
        modelPricesContent = null;
        modelPricesValue = null;
        modelPricesStatus = null;
        lastPricedModelId = null;
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
            chip.setOnClickListener(view -> {
                if (updatingModelOptionSelection) {
                    return;
                }
                inputLayout.setError(null);
                if (model.id.equals(getInputText())) {
                    checkOpenRouterModelPrices();
                } else {
                    setInputText(model.id);
                }
            });
            binding.aiSummaryModelOptions.addView(chip);
        }

        modelTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updateSelectedModelOption(String.valueOf(s))) {
                    checkOpenRouterModelPrices();
                }
            }
        };
        inputEditText.addTextChangedListener(modelTextWatcher);
        updateSelectedModelOption(currentValue);
    }

    private void configureModelPrices() {
        if (!shouldShowModelPrices()) {
            modelPricesButton.setVisibility(View.GONE);
            modelPricesResult.setVisibility(View.GONE);
            modelPricesStatus.setVisibility(View.GONE);
            return;
        }

        modelPricesButton.setVisibility(View.GONE);
        modelPricesButton.setOnClickListener(view -> checkOpenRouterModelPrices());
        modelPricesTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateModelPricesButtonVisibility();
                if (!String.valueOf(s).trim().equals(lastPricedModelId)) {
                    modelPricesStatus.setVisibility(View.GONE);
                }
            }
        };
        inputEditText.addTextChangedListener(modelPricesTextWatcher);
    }

    private void updateModelPricesButtonVisibility() {
        if (modelPricesButton == null || !shouldShowModelPrices()) {
            return;
        }
        boolean modelChanged = modelPriceCall == null
                && !getInputText().equals(lastPricedModelId);
        modelPricesButton.setVisibility(modelChanged ? View.VISIBLE : View.GONE);
    }

    private boolean updateSelectedModelOption(String modelId) {
        if (modelOptionsGroup == null) {
            return false;
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
        return matchingChipId != View.NO_ID;
    }

    private boolean shouldShowModelPrices() {
        return PREF_MODEL.equals(requireArguments().getString(ARG_KEY))
                && isOpenRouterProvider(getCurrentProvider());
    }

    private void checkOpenRouterModelPrices() {
        String modelId = getInputText();
        if (TextUtils.isEmpty(modelId)) {
            inputLayout.setError(null);
            setModelPricesError("Enter a model ID like openai/gpt-4.");
            return;
        }

        String requestUrl = buildOpenRouterModelUrl(modelId);
        if (TextUtils.isEmpty(requestUrl)) {
            inputLayout.setError(null);
            setModelPricesError("Enter an OpenRouter model ID like openai/gpt-4.");
            return;
        }

        inputLayout.setError(null);
        if (modelPriceCall != null) {
            modelPriceCall.cancel();
        }
        setModelPricesLoading();

        Request request = new Request.Builder()
                .url(requestUrl)
                .build();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        modelPriceCall = NetworkComponent.getOkHttpClientInstance().newCall(request);
        modelPriceCall.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) {
                    return;
                }
                mainHandler.post(() -> {
                    if (!isAdded() || call != modelPriceCall) {
                        return;
                    }
                    setModelPricesError(e.getMessage());
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful() || closeableResponse.body() == null) {
                        String message = "HTTP " + closeableResponse.code();
                        mainHandler.post(() -> {
                            if (!isAdded() || call != modelPriceCall) {
                                return;
                            }
                            setModelPricesError(message);
                        });
                        return;
                    }

                    String responseBody = closeableResponse.body().string();
                    String message = formatOpenRouterModelPrices(responseBody);
                    mainHandler.post(() -> {
                        if (!isAdded() || call != modelPriceCall) {
                            return;
                        }
                        setModelPricesResult(message);
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (!isAdded() || call != modelPriceCall) {
                            return;
                        }
                        setModelPricesError(e.getMessage());
                    });
                }
            }
        });
    }

    private void setModelPricesLoading() {
        modelPricesButton.setEnabled(false);
        modelPricesButton.setVisibility(View.GONE);
        inputEditText.setEnabled(false);
        if (!TextUtils.isEmpty(modelPricesValue.getText())) {
            modelPricesResult.setVisibility(View.VISIBLE);
            modelPricesContent.animate()
                    .alpha(0f)
                    .setDuration(120L)
                    .start();
        }
        modelPricesStatus.setVisibility(View.GONE);
    }

    private void setModelPricesIdle() {
        modelPricesButton.setEnabled(true);
        inputEditText.setEnabled(true);
        modelPriceCall = null;
    }

    private void setModelPricesResult(String message) {
        setModelPricesIdle();
        lastPricedModelId = getInputText();
        modelPricesContent.animate().cancel();
        modelPricesValue.setText(message);
        modelPricesContent.setAlpha(0f);
        modelPricesResult.setVisibility(View.VISIBLE);
        modelPricesStatus.setVisibility(View.GONE);
        modelPricesContent.animate()
                .alpha(1f)
                .setDuration(160L)
                .start();
        updateModelPricesButtonVisibility();
    }

    private void setModelPricesError(@Nullable String message) {
        setModelPricesIdle();
        lastPricedModelId = null;
        modelPricesContent.animate().cancel();
        modelPricesContent.setAlpha(1f);
        modelPricesResult.setVisibility(View.GONE);
        TypedValue errorColor = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.colorError, errorColor, true);
        modelPricesStatus.setTextColor(errorColor.data);
        modelPricesStatus.setText(TextUtils.isEmpty(message)
                ? "Couldn't check prices. OpenRouter did not return pricing."
                : "Couldn't check prices. " + message);
        modelPricesStatus.setVisibility(View.VISIBLE);
        updateModelPricesButtonVisibility();
    }

    @Nullable
    private String buildOpenRouterModelUrl(String modelId) {
        int separator = modelId.indexOf('/');
        if (separator <= 0 || separator >= modelId.length() - 1) {
            return null;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String baseUrl = AiSummaryProviders.normalizeUrl(
                prefs.getString(PREF_BASE_URL, AiSummaryProviders.getDefaultBaseUrl()));
        HttpUrl parsedBaseUrl = HttpUrl.parse(baseUrl);
        if (parsedBaseUrl == null) {
            return null;
        }

        return parsedBaseUrl.newBuilder()
                .addPathSegment("model")
                .addPathSegment(modelId.substring(0, separator))
                .addPathSegment(modelId.substring(separator + 1))
                .build()
                .toString();
    }

    private String formatOpenRouterModelPrices(String responseBody) throws Exception {
        JSONObject data = new JSONObject(responseBody).getJSONObject("data");
        JSONObject pricing = data.getJSONObject("pricing");
        String promptPrice = formatPricePerMillionTokens(pricing.optString("prompt", ""));
        String completionPrice = formatPricePerMillionTokens(pricing.optString("completion", ""));
        return promptPrice + " / " + completionPrice;
    }

    private static String formatPricePerMillionTokens(String pricePerToken) {
        double price = parseDouble(pricePerToken);
        if (price == 0d) {
            return "$0";
        }
        return String.format(Locale.US, "$%.2f", price * 1_000_000d);
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private static boolean isOpenRouterProvider(@Nullable AiSummaryProviders.Provider provider) {
        return provider != null && AiSummaryProviders.PROVIDER_OPENROUTER.equals(provider.id);
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
