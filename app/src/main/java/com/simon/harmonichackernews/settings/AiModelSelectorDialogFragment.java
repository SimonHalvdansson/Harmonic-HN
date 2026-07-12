package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.AiModelSelectorSheetBinding;
import com.simon.harmonichackernews.network.AiModelCatalog;
import com.simon.harmonichackernews.network.AiSummaryProviders;
import com.simon.harmonichackernews.network.OpenRouterProviderIconLoader;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;

public final class AiModelSelectorDialogFragment extends BottomSheetDialogFragment {
    private static final String TAG = "ai_model_selector";
    private static final String STATE_MODEL = "model";
    private static final String STATE_LIST_SCROLL = "list_scroll";
    private static final String STATE_SORT = "sort";
    private static final String STATE_FREE_ONLY = "free_only";
    private static final long PRICE_DEBOUNCE_MS = 400L;
    private static final long CATALOG_LOADING_DELAY_MS = 300L;
    private static final long STATE_FADE_OUT_MS = 80L;
    private static final long STATE_FADE_IN_MS = 130L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private AiModelSelectorSheetBinding binding;
    private AiModelAdapter adapter;
    private AiSummaryProviders.Provider provider;
    private AiModelCatalog.Sort selectedSort = AiModelCatalog.Sort.POPULAR;
    private boolean freeOnly;
    private boolean updatingInput;
    private TextWatcher modelWatcher;
    private Runnable pendingPriceLookup;
    private Runnable pendingCatalogLoading;
    private Call catalogCall;
    private Call priceCall;
    private int catalogRequestGeneration;
    private int priceAnimationGeneration;
    private View currentPriceState;
    private Parcelable pendingListScrollState;

    public static void show(androidx.fragment.app.FragmentManager fragmentManager) {
        new AiModelSelectorDialogFragment().show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(dialogInterface -> expandSheet(dialog));
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = AiModelSelectorSheetBinding.inflate(inflater, container, false);
        currentPriceState = binding.aiModelPriceMessage;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String baseUrl = prefs.getString(AiModelCatalog.PREF_BASE_URL,
                AiSummaryProviders.getDefaultBaseUrl());
        provider = AiSummaryProviders.getProviderForBaseUrl(baseUrl);
        if (provider == null) {
            provider = AiSummaryProviders.OPENROUTER;
        }

        if (savedInstanceState != null) {
            pendingListScrollState = savedInstanceState.getParcelable(STATE_LIST_SCROLL);
            freeOnly = savedInstanceState.getBoolean(STATE_FREE_ONLY, false);
            String savedSort = savedInstanceState.getString(
                    STATE_SORT, AiModelCatalog.Sort.POPULAR.name());
            try {
                selectedSort = AiModelCatalog.Sort.valueOf(savedSort);
            } catch (IllegalArgumentException ignored) {
                selectedSort = AiModelCatalog.Sort.POPULAR;
            }
        }

        String currentModel = savedInstanceState == null
                ? prefs.getString(AiModelCatalog.PREF_MODEL, "")
                : savedInstanceState.getString(STATE_MODEL, "");
        currentModel = AiSummaryProviders.getModelForRequest(baseUrl, currentModel);

        adapter = new AiModelAdapter(this::selectModel);
        adapter.setSelectedModelId(currentModel);
        binding.aiModelList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.aiModelList.setAdapter(adapter);
        binding.aiModelList.setHasFixedSize(true);

        setInputText(currentModel);
        configureInput();
        configureSortControls();
        binding.aiModelRetry.setOnClickListener(view -> loadModels());
        binding.aiModelCancel.setOnClickListener(view -> dismiss());
        binding.aiModelSave.setOnClickListener(view -> saveModel());

        loadModels();
        schedulePriceLookup(currentModel);
        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            expandSheet((BottomSheetDialog) dialog);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (binding != null) {
            outState.putString(STATE_MODEL, getModelInput());
            outState.putString(STATE_SORT, selectedSort.name());
            outState.putBoolean(STATE_FREE_ONLY, freeOnly);
            if (binding.aiModelList.getLayoutManager() != null) {
                outState.putParcelable(STATE_LIST_SCROLL,
                        binding.aiModelList.getLayoutManager().onSaveInstanceState());
            }
        }
    }

    @Override
    public void onDestroyView() {
        if (catalogCall != null) {
            catalogCall.cancel();
        }
        if (priceCall != null) {
            priceCall.cancel();
        }
        if (pendingPriceLookup != null) {
            handler.removeCallbacks(pendingPriceLookup);
        }
        if (pendingCatalogLoading != null) {
            handler.removeCallbacks(pendingCatalogLoading);
        }
        if (binding != null) {
            binding.aiModelPriceContent.animate().cancel();
            binding.aiModelPriceMessage.animate().cancel();
            binding.aiModelPriceLoading.animate().cancel();
        }
        if (binding != null && modelWatcher != null) {
            binding.aiModelInput.removeTextChangedListener(modelWatcher);
        }
        catalogCall = null;
        priceCall = null;
        pendingPriceLookup = null;
        pendingCatalogLoading = null;
        modelWatcher = null;
        currentPriceState = null;
        adapter = null;
        binding = null;
        super.onDestroyView();
    }

    private void configureInput() {
        modelWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (updatingInput) {
                    return;
                }
                String modelId = String.valueOf(s).trim();
                binding.aiModelInputLayout.setError(null);
                adapter.setSelectedModelId(modelId);
                schedulePriceLookup(modelId);
            }
        };
        binding.aiModelInput.addTextChangedListener(modelWatcher);
        binding.aiModelInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveModel();
                return true;
            }
            return false;
        });
    }

    private void configureSortControls() {
        int selectedControl = freeOnly
                ? R.id.ai_model_sort_free
                : selectedSort == AiModelCatalog.Sort.PRICE_LOW_TO_HIGH
                ? R.id.ai_model_sort_price
                : R.id.ai_model_sort_popular;
        binding.aiModelSortGroup.check(selectedControl);
        binding.aiModelSortGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            int checkedId = checkedIds.get(0);
            freeOnly = checkedId == R.id.ai_model_sort_free;
            if (checkedId == R.id.ai_model_sort_price) {
                selectedSort = AiModelCatalog.Sort.PRICE_LOW_TO_HIGH;
            } else {
                selectedSort = AiModelCatalog.Sort.POPULAR;
            }
            loadModels();
        });
    }

    private void loadModels() {
        int requestGeneration = ++catalogRequestGeneration;
        if (catalogCall != null) {
            catalogCall.cancel();
        }
        if (pendingCatalogLoading != null) {
            handler.removeCallbacks(pendingCatalogLoading);
        }
        pendingCatalogLoading = () -> {
            if (binding != null && requestGeneration == catalogRequestGeneration) {
                showCatalogLoading();
            }
        };
        handler.postDelayed(pendingCatalogLoading, CATALOG_LOADING_DELAY_MS);
        catalogCall = AiModelCatalog.fetchModels(provider, selectedSort,
                new AiModelCatalog.ModelsCallback() {
                    @Override
                    public void onSuccess(List<AiModelCatalog.Model> models) {
                        if (binding == null || requestGeneration != catalogRequestGeneration) {
                            return;
                        }
                        catalogCall = null;
                        cancelPendingCatalogLoading();
                        List<AiModelCatalog.Model> displayedModels = models;
                        if (freeOnly) {
                            displayedModels = new ArrayList<>();
                            for (AiModelCatalog.Model model : models) {
                                if (model.isFree()) {
                                    displayedModels.add(model);
                                }
                            }
                        }
                        adapter.setModels(displayedModels);
                        restorePendingListScrollState();
                        loadProviderIcons(models);
                        binding.aiModelCatalogState.setVisibility(
                                displayedModels.isEmpty() ? View.VISIBLE : View.GONE);
                        binding.aiModelCatalogProgress.setVisibility(View.GONE);
                        binding.aiModelRetry.setVisibility(View.GONE);
                        binding.aiModelCatalogMessage.setText(displayedModels.isEmpty()
                                ? getString(R.string.ai_model_no_free)
                                : "");
                        binding.aiModelListTitle.setText(getResources().getQuantityString(
                                R.plurals.ai_model_count,
                                displayedModels.size(),
                                displayedModels.size()));
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null || requestGeneration != catalogRequestGeneration) {
                            return;
                        }
                        catalogCall = null;
                        cancelPendingCatalogLoading();
                        adapter.setModels(new ArrayList<>());
                        binding.aiModelCatalogState.setVisibility(View.VISIBLE);
                        binding.aiModelCatalogProgress.setVisibility(View.GONE);
                        binding.aiModelCatalogMessage.setText(message);
                        binding.aiModelRetry.setVisibility(View.VISIBLE);
                        binding.aiModelListTitle.setText(R.string.ai_model_suggestions);
                    }
                });
    }

    private void restorePendingListScrollState() {
        if (pendingListScrollState == null
                || binding.aiModelList.getLayoutManager() == null) {
            return;
        }
        binding.aiModelList.getLayoutManager().onRestoreInstanceState(pendingListScrollState);
        pendingListScrollState = null;
    }

    private void loadProviderIcons(List<AiModelCatalog.Model> models) {
        AiModelAdapter currentAdapter = adapter;
        Set<String> providerSlugs = new LinkedHashSet<>();
        for (AiModelCatalog.Model model : models) {
            if (!model.providerSlug().isEmpty()) {
                providerSlugs.add(model.providerSlug());
            }
        }
        for (String providerSlug : providerSlugs) {
            OpenRouterProviderIconLoader.resolve(providerSlug, (resolvedSlug, iconData) -> {
                if (binding != null && adapter == currentAdapter) {
                    currentAdapter.setProviderIcon(resolvedSlug, iconData);
                }
            });
        }
    }

    private void showCatalogLoading() {
        pendingCatalogLoading = null;
        binding.aiModelCatalogState.setVisibility(View.VISIBLE);
        binding.aiModelCatalogProgress.setVisibility(View.VISIBLE);
        binding.aiModelCatalogMessage.setText(R.string.ai_model_loading);
        binding.aiModelRetry.setVisibility(View.GONE);
        binding.aiModelListTitle.setText(R.string.ai_model_suggestions);
    }

    private void selectModel(AiModelCatalog.Model model) {
        setInputText(model.requestId);
        adapter.setSelectedModelId(model.requestId);
        showResolvedPrice(model);
    }

    private void setInputText(String modelId) {
        updatingInput = true;
        binding.aiModelInput.setText(modelId);
        binding.aiModelInput.setSelection(binding.aiModelInput.length());
        updatingInput = false;
    }

    private void schedulePriceLookup(String modelId) {
        if (pendingPriceLookup != null) {
            handler.removeCallbacks(pendingPriceLookup);
        }
        if (priceCall != null) {
            priceCall.cancel();
            priceCall = null;
        }
        if (TextUtils.isEmpty(modelId)) {
            showPriceMessage(getString(R.string.ai_model_price_enter), false);
            return;
        }
        showPriceLoading();
        pendingPriceLookup = () -> resolvePrice(modelId);
        handler.postDelayed(pendingPriceLookup, PRICE_DEBOUNCE_MS);
    }

    private void resolvePrice(String modelId) {
        pendingPriceLookup = null;
        priceCall = AiModelCatalog.resolveModel(provider, modelId,
                new AiModelCatalog.ModelCallback() {
                    @Override
                    public void onSuccess(AiModelCatalog.Model model) {
                        if (binding == null || !modelId.equals(getModelInput())) {
                            return;
                        }
                        priceCall = null;
                        showResolvedPrice(model);
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null || !modelId.equals(getModelInput())) {
                            return;
                        }
                        priceCall = null;
                        showPriceMessage(message, true);
                    }
                });
    }

    private void showResolvedPrice(AiModelCatalog.Model model) {
        if (pendingPriceLookup != null) {
            handler.removeCallbacks(pendingPriceLookup);
            pendingPriceLookup = null;
        }
        if (priceCall != null) {
            priceCall.cancel();
            priceCall = null;
        }
        showPriceContent(model);
    }

    private void showPriceLoading() {
        transitionPriceState(binding.aiModelPriceLoading, null);
    }

    private void showPriceContent(AiModelCatalog.Model model) {
        transitionPriceState(binding.aiModelPriceContent, () ->
                binding.aiModelPriceStatus.setText(getString(
                        R.string.ai_model_price_pair_format,
                        model.formattedInputPrice(),
                        model.formattedOutputPrice())));
    }

    private void showPriceMessage(CharSequence text, boolean error) {
        transitionPriceState(binding.aiModelPriceMessage, () -> {
            binding.aiModelPriceMessage.setText(text);
            setPriceStatusError(error);
        });
    }

    private void transitionPriceState(View target, @Nullable Runnable prepareTarget) {
        int animationGeneration = ++priceAnimationGeneration;
        View previous = currentPriceState;
        currentPriceState = target;
        cancelPriceStateAnimations();

        if (previous == target && target.getVisibility() == View.VISIBLE) {
            target.animate()
                    .alpha(0f)
                    .setStartDelay(0L)
                    .setDuration(STATE_FADE_OUT_MS)
                    .withEndAction(() -> {
                        if (binding == null || animationGeneration != priceAnimationGeneration) {
                            return;
                        }
                        if (prepareTarget != null) {
                            prepareTarget.run();
                        }
                        target.setAlpha(0f);
                        target.setVisibility(View.VISIBLE);
                        target.animate()
                                .alpha(1f)
                                .setStartDelay(0L)
                                .setDuration(STATE_FADE_IN_MS)
                                .start();
                    })
                    .start();
            return;
        }

        hideUnusedPriceStates(target, previous);
        if (prepareTarget != null) {
            prepareTarget.run();
        }
        target.setAlpha(0f);
        target.setVisibility(View.VISIBLE);
        target.animate()
                .alpha(1f)
                .setStartDelay(40L)
                .setDuration(STATE_FADE_IN_MS)
                .start();

        if (previous != null && previous.getVisibility() == View.VISIBLE) {
            previous.animate()
                    .alpha(0f)
                    .setStartDelay(0L)
                    .setDuration(STATE_FADE_OUT_MS)
                    .withEndAction(() -> {
                        if (binding != null && animationGeneration == priceAnimationGeneration) {
                            previous.setVisibility(View.INVISIBLE);
                        }
                    })
                    .start();
        }
    }

    private void cancelPriceStateAnimations() {
        binding.aiModelPriceContent.animate().cancel();
        binding.aiModelPriceMessage.animate().cancel();
        binding.aiModelPriceLoading.animate().cancel();
    }

    private void hideUnusedPriceStates(View target, @Nullable View previous) {
        View[] states = new View[]{
                binding.aiModelPriceContent,
                binding.aiModelPriceMessage,
                binding.aiModelPriceLoading
        };
        for (View state : states) {
            if (state != target && state != previous) {
                state.setAlpha(0f);
                state.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void cancelPendingCatalogLoading() {
        if (pendingCatalogLoading != null) {
            handler.removeCallbacks(pendingCatalogLoading);
            pendingCatalogLoading = null;
        }
    }

    private void setPriceStatusError(boolean error) {
        int attribute = error
                ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorOnSurfaceVariant;
        binding.aiModelPriceMessage.setTextColor(MaterialColors.getColor(
                binding.aiModelPriceMessage, attribute, Color.GRAY));
    }

    private void saveModel() {
        String modelId = getModelInput();
        if (TextUtils.isEmpty(modelId)) {
            binding.aiModelInputLayout.setError(getString(R.string.ai_model_required));
            return;
        }
        String savedModelId = AiSummaryProviders.toProviderModelId(provider, modelId);
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putString(AiModelCatalog.PREF_MODEL, savedModelId)
                .apply();
        dismiss();
    }

    private String getModelInput() {
        return String.valueOf(binding.aiModelInput.getText()).trim();
    }

    private void expandSheet(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) {
            return;
        }
        int height = Math.round(getResources().getDisplayMetrics().heightPixels * 0.92f);
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = height;
        sheet.setLayoutParams(params);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
}
