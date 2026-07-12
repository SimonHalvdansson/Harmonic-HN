package com.simon.harmonichackernews.settings;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.AiModelItemBinding;
import com.simon.harmonichackernews.network.AiModelCatalog;
import com.simon.harmonichackernews.network.NetworkComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import coil.Coil;
import coil.decode.SvgDecoder;
import coil.request.ImageRequest;
import coil.target.ImageViewTarget;
import coil.util.CoilUtils;

final class AiModelAdapter extends RecyclerView.Adapter<AiModelAdapter.ModelViewHolder> {
    private static final long SELECTION_ANIMATION_DURATION_MS = 100L;
    private static final Pattern FREE_TITLE_SUFFIX = Pattern.compile(
            "\\s*\\(free\\)\\s*$", Pattern.CASE_INSENSITIVE);

    interface Listener {
        void onModelSelected(AiModelCatalog.Model model);
    }

    private final List<AiModelCatalog.Model> models = new ArrayList<>();
    private final Map<String, Object> providerIcons = new HashMap<>();
    private final Listener listener;
    private String selectedModelId = "";
    private RecyclerView recyclerView;

    AiModelAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    void setModels(List<AiModelCatalog.Model> updatedModels) {
        List<AiModelCatalog.Model> oldModels = new ArrayList<>(models);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldModels.size();
            }

            @Override
            public int getNewListSize() {
                return updatedModels.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldModels.get(oldItemPosition).openRouterId.equals(
                        updatedModels.get(newItemPosition).openRouterId);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return areItemsTheSame(oldItemPosition, newItemPosition);
            }
        });
        models.clear();
        models.addAll(updatedModels);
        diff.dispatchUpdatesTo(this);
    }

    void setSelectedModelId(String modelId) {
        String normalizedId = modelId == null ? "" : modelId.trim();
        if (selectedModelId.equals(normalizedId)) {
            return;
        }
        int oldPosition = findModelPosition(selectedModelId);
        selectedModelId = normalizedId;
        int newPosition = findModelPosition(selectedModelId);
        animateVisibleSelection(oldPosition, false);
        if (newPosition != oldPosition) {
            animateVisibleSelection(newPosition, true);
        }
    }

    void setProviderIcon(String providerSlug, Object iconData) {
        providerIcons.put(providerSlug, iconData);
        if (recyclerView == null) {
            return;
        }
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(
                    recyclerView.getChildAt(i));
            if (holder instanceof ModelViewHolder) {
                ModelViewHolder modelHolder = (ModelViewHolder) holder;
                if (providerSlug.equals(modelHolder.boundProviderSlug)) {
                    modelHolder.setProviderIcon(iconData);
                }
            }
        }
    }

    private void animateVisibleSelection(int position, boolean selected) {
        if (position < 0 || recyclerView == null) {
            return;
        }
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (holder instanceof ModelViewHolder) {
            ((ModelViewHolder) holder).setSelectionState(selected, true);
        }
    }

    private int findModelPosition(String modelId) {
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).requestId.equals(modelId)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public long getItemId(int position) {
        return models.get(position).openRouterId.hashCode();
    }

    @NonNull
    @Override
    public ModelViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AiModelItemBinding binding = AiModelItemBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ModelViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ModelViewHolder holder, int position) {
        AiModelCatalog.Model model = models.get(position);
        holder.bind(model, selectedModelId, providerIcons.get(model.providerSlug()), listener);
    }

    @Override
    public void onViewRecycled(@NonNull ModelViewHolder holder) {
        holder.cancelSelectionAnimation();
        holder.clearProviderIcon();
        super.onViewRecycled(holder);
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        if (this.recyclerView == recyclerView) {
            this.recyclerView = null;
        }
        super.onDetachedFromRecyclerView(recyclerView);
    }

    @Override
    public int getItemCount() {
        return models.size();
    }

    static final class ModelViewHolder extends RecyclerView.ViewHolder {
        private final AiModelItemBinding binding;
        private ValueAnimator selectionAnimator;
        private AiModelCatalog.Model boundModel;
        private String displayName = "";
        private String boundProviderSlug = "";

        ModelViewHolder(AiModelItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(AiModelCatalog.Model model, String selectedModelId, Object providerIconData,
                  Listener listener) {
            boolean selected = model.requestId.equals(selectedModelId);
            boundModel = model;
            boundProviderSlug = model.providerSlug();
            displayName = model.isFree()
                    ? FREE_TITLE_SUFFIX.matcher(model.name).replaceFirst("")
                    : model.name;
            binding.aiModelItemName.setText(displayName);
            binding.aiModelItemProviderFallback.setText(providerInitial(boundProviderSlug));
            setProviderIcon(providerIconData);
            binding.aiModelItemId.setText(model.requestId);
            if (model.isFree()) {
                binding.aiModelItemPrice.setVisibility(View.GONE);
                binding.aiModelItemFree.setVisibility(View.VISIBLE);
            } else {
                binding.aiModelItemPrice.setText(itemView.getContext().getString(
                        R.string.ai_model_row_price_format,
                        model.formattedInputPrice(),
                        model.formattedOutputPrice()));
                binding.aiModelItemPrice.setVisibility(View.VISIBLE);
                binding.aiModelItemFree.setVisibility(View.GONE);
            }
            setSelectionState(selected, false);
            binding.aiModelItemCard.setOnClickListener(view -> listener.onModelSelected(model));
        }

        void setProviderIcon(Object iconData) {
            clearProviderIcon();
            binding.aiModelItemProviderFallback.setVisibility(View.VISIBLE);
            binding.aiModelItemProviderIcon.setVisibility(View.INVISIBLE);
            if (iconData == null || (iconData instanceof String
                    && ((String) iconData).trim().isEmpty())) {
                return;
            }

            binding.aiModelItemProviderIcon.setTag(iconData);
            ImageRequest.Builder requestBuilder = new ImageRequest.Builder(itemView.getContext())
                    .data(iconData)
                    .setHeader("User-Agent", NetworkComponent.USER_AGENT)
                    .crossfade(100)
                    .target(new ImageViewTarget(binding.aiModelItemProviderIcon) {
                        @Override
                        public void onStart(Drawable placeholder) {
                            super.onStart((Drawable) null);
                        }

                        @Override
                        public void onSuccess(Drawable result) {
                            if (!iconData.equals(binding.aiModelItemProviderIcon.getTag())) {
                                return;
                            }
                            super.onSuccess(result);
                            binding.aiModelItemProviderIcon.setVisibility(View.VISIBLE);
                            binding.aiModelItemProviderFallback.setVisibility(View.INVISIBLE);
                        }

                        @Override
                        public void onError(Drawable error) {
                            if (iconData.equals(binding.aiModelItemProviderIcon.getTag())) {
                                super.onError(null);
                                binding.aiModelItemProviderIcon.setVisibility(View.INVISIBLE);
                                binding.aiModelItemProviderFallback.setVisibility(View.VISIBLE);
                            }
                        }
                    });
            if (iconData instanceof byte[] || (iconData instanceof String
                    && ((String) iconData).toLowerCase(Locale.ROOT).contains(".svg"))) {
                requestBuilder.decoderFactory(new SvgDecoder.Factory());
            }
            ImageRequest request = requestBuilder.build();
            Coil.imageLoader(itemView.getContext()).enqueue(request);
        }

        void clearProviderIcon() {
            CoilUtils.dispose(binding.aiModelItemProviderIcon);
            binding.aiModelItemProviderIcon.setTag(null);
            binding.aiModelItemProviderIcon.setImageDrawable(null);
        }

        private String providerInitial(String providerSlug) {
            return providerSlug.isEmpty()
                    ? "?"
                    : providerSlug.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        void setSelectionState(boolean selected, boolean animate) {
            cancelSelectionAnimation();
            int normalBackground = MaterialColors.getColor(
                    binding.aiModelItemCard,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    Color.TRANSPARENT);
            int selectedBase = MaterialColors.getColor(
                    binding.aiModelItemCard,
                    com.google.android.material.R.attr.colorPrimaryContainer,
                    normalBackground);
            int targetBackground = selected
                    ? ColorUtils.blendARGB(normalBackground, selectedBase, 0.04f)
                    : normalBackground;
            int targetStrokeWidth = selected ? dp(2) : 0;
            binding.aiModelItemCard.setStrokeColor(MaterialColors.getColor(
                    binding.aiModelItemCard,
                    androidx.appcompat.R.attr.colorPrimary,
                    Color.TRANSPARENT));
            binding.aiModelItemSelected.setChecked(selected);

            if (!animate) {
                binding.aiModelItemSelected.jumpDrawablesToCurrentState();
                binding.aiModelItemCard.setCardBackgroundColor(targetBackground);
                binding.aiModelItemCard.setStrokeWidth(targetStrokeWidth);
                updateContentDescription(selected);
                return;
            }

            int startBackground = binding.aiModelItemCard.getCardBackgroundColor()
                    .getDefaultColor();
            int startStrokeWidth = binding.aiModelItemCard.getStrokeWidth();
            selectionAnimator = ValueAnimator.ofFloat(0f, 1f);
            selectionAnimator.setDuration(SELECTION_ANIMATION_DURATION_MS);
            selectionAnimator.addUpdateListener(animator -> {
                float progress = (float) animator.getAnimatedValue();
                binding.aiModelItemCard.setCardBackgroundColor(ColorUtils.blendARGB(
                        startBackground, targetBackground, progress));
                binding.aiModelItemCard.setStrokeWidth(Math.round(
                        startStrokeWidth + (targetStrokeWidth - startStrokeWidth) * progress));
            });
            selectionAnimator.start();
            updateContentDescription(selected);
        }

        void cancelSelectionAnimation() {
            if (selectionAnimator != null) {
                selectionAnimator.cancel();
                selectionAnimator = null;
            }
        }

        private void updateContentDescription(boolean selected) {
            if (boundModel == null) {
                return;
            }
            binding.aiModelItemCard.setContentDescription(itemView.getContext().getString(
                    R.string.ai_model_item_description,
                    displayName,
                    boundModel.formattedInputPrice(),
                    boundModel.formattedOutputPrice(),
                    selected ? itemView.getContext().getString(
                            R.string.ai_model_selected_description) : ""));
        }

        private int dp(int value) {
            return Math.round(value * itemView.getResources().getDisplayMetrics().density);
        }
    }
}
