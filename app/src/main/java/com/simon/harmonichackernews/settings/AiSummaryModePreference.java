package com.simon.harmonichackernews.settings;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.TooltipCompat;
import androidx.core.view.ViewCompat;
import androidx.core.graphics.ColorUtils;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.network.SummaryManager;
import com.simon.harmonichackernews.summary.local.LocalModelManager;

import java.util.LinkedHashMap;
import java.util.Map;

public class AiSummaryModePreference extends Preference {

    private static final float ENABLED_ALPHA = 1f;
    private static final float DISABLED_ALPHA = 0.38f;
    private static final int ACTION_ICON_SWAP_OUT_DURATION_MS = 90;
    private static final int ACTION_ICON_SWAP_IN_DURATION_MS = 150;
    private static final float ACTION_ICON_SWAP_MIN_SCALE = 0.72f;

    public static final String MODE_LOCAL = "local";
    public static final String MODE_CLOUD = "cloud";

    private final Map<String, ModelRow> modelRows = new LinkedHashMap<>();
    private final LocalModelManager.StatusListener modelStatusListener = status -> {
        renderLocalPanel();
        notifyLocalConfigurationIfChanged();
    };
    private int availabilityCheckGeneration;
    private boolean controlsEnabled = true;
    private boolean localAvailable;
    private boolean nanoAvailabilityResolved;
    private boolean nanoAvailable;
    private String selectedMode = MODE_CLOUD;
    private Boolean lastLocalConfigurationReady;
    private Runnable localConfigurationChangedListener;
    private View boundItemView;
    private MaterialButtonToggleGroup boundGroup;
    private MaterialButton boundLocalButton;
    private MaterialButton boundCloudButton;
    private TextView boundTitle;
    private View boundModelPanel;
    private LinearLayout boundModelList;

    public AiSummaryModePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_ai_summary_mode);
        setSelectable(false);
    }

    public AiSummaryModePreference(Context context) {
        this(context, null);
    }

    public void setControlsEnabled(boolean enabled) {
        controlsEnabled = enabled;
        setEnabled(enabled);
        applyBoundControlsEnabled(enabled);
        renderLocalPanel();
    }

    public void setLocalConfigurationChangedListener(Runnable listener) {
        localConfigurationChangedListener = listener;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        LocalModelManager.addStatusListener(getContext(), modelStatusListener);
    }

    @Override
    public void onDetached() {
        availabilityCheckGeneration++;
        LocalModelManager.removeStatusListener(modelStatusListener);
        super.onDetached();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        boundItemView = holder.itemView;
        boundGroup = holder.itemView.findViewById(R.id.ai_summary_mode_group);
        boundLocalButton = holder.itemView.findViewById(R.id.ai_summary_mode_local);
        boundCloudButton = holder.itemView.findViewById(R.id.ai_summary_mode_cloud);
        boundTitle = holder.itemView.findViewById(android.R.id.title);
        boundModelPanel = holder.itemView.findViewById(R.id.ai_summary_local_model_panel);
        boundModelList = holder.itemView.findViewById(R.id.ai_summary_local_model_list);

        selectedMode = PreferenceManager.getDefaultSharedPreferences(getContext())
                .getString("pref_ai_summary_mode", MODE_CLOUD);
        boolean canAttemptLocal = SummaryManager.canAttemptLocalSummarization();
        boolean preferenceEnabled = controlsEnabled && isEnabled();
        localAvailable = canAttemptLocal;
        nanoAvailabilityResolved = false;
        nanoAvailable = false;

        boundGroup.clearOnButtonCheckedListeners();
        applyBoundControlsEnabled(preferenceEnabled);
        boundGroup.check(MODE_LOCAL.equals(selectedMode) && canAttemptLocal
                ? R.id.ai_summary_mode_local
                : R.id.ai_summary_mode_cloud);

        if (!canAttemptLocal && MODE_LOCAL.equals(selectedMode)) {
            selectedMode = MODE_CLOUD;
            new Handler(Looper.getMainLooper()).post(() ->
                    PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit()
                            .putString("pref_ai_summary_mode", MODE_CLOUD)
                            .apply());
        }

        buildModelRows();
        renderLocalPanel();

        if (preferenceEnabled) {
            addModeChangeListener(boundGroup);
        }

        if (canAttemptLocal) {
            int generation = ++availabilityCheckGeneration;
            SummaryManager.checkLocalSummaryAvailability(getContext(),
                    (available, fallbackRequired, statusMessage) -> {
                        if (generation != availabilityCheckGeneration) {
                            return;
                        }

                        localAvailable = available;
                        nanoAvailabilityResolved = true;
                        nanoAvailable = available && !fallbackRequired;
                        if (!nanoAvailable && LocalModelManager.MODEL_GEMINI_NANO.equals(
                                LocalModelManager.getSelectedModel(getContext()).id)) {
                            selectFirstDownloadedModelOrClear();
                        }

                        boolean updatedEnabled = controlsEnabled && isEnabled();
                        applyBoundControlsEnabled(updatedEnabled);
                        renderLocalPanel();
                        notifyLocalConfigurationIfChanged();

                        if (!updatedEnabled || boundGroup == null) {
                            return;
                        }
                        selectedMode = PreferenceManager.getDefaultSharedPreferences(getContext())
                                .getString("pref_ai_summary_mode", MODE_CLOUD);
                        if (!available && MODE_LOCAL.equals(selectedMode)) {
                            selectedMode = MODE_CLOUD;
                            PreferenceManager.getDefaultSharedPreferences(getContext())
                                    .edit()
                                    .putString("pref_ai_summary_mode", MODE_CLOUD)
                                    .apply();
                        }
                        boundGroup.clearOnButtonCheckedListeners();
                        boundGroup.check(MODE_LOCAL.equals(selectedMode) && available
                                ? R.id.ai_summary_mode_local
                                : R.id.ai_summary_mode_cloud);
                        addModeChangeListener(boundGroup);
                        renderLocalPanel();
                    });
        }
    }

    private void buildModelRows() {
        modelRows.clear();
        if (boundModelList == null) {
            return;
        }
        boundModelList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (LocalModelManager.ModelInfo model : LocalModelManager.getModels()) {
            View item = inflater.inflate(
                    R.layout.preference_ai_local_model_row, boundModelList, false);
            ModelRow row = new ModelRow(
                    item.findViewById(R.id.ai_summary_local_model_card),
                    item.findViewById(R.id.ai_summary_local_model_icon),
                    item.findViewById(R.id.ai_summary_local_model_name),
                    item.findViewById(R.id.ai_summary_local_model_availability),
                    item.findViewById(R.id.ai_summary_local_model_tags),
                    item.findViewById(R.id.ai_summary_local_model_quantization),
                    item.findViewById(R.id.ai_summary_local_model_size),
                    item.findViewById(R.id.ai_summary_local_model_framework),
                    item.findViewById(R.id.ai_summary_local_model_action),
                    item.findViewById(R.id.ai_summary_local_model_download_details),
                    item.findViewById(R.id.ai_summary_local_model_progress),
                    item.findViewById(R.id.ai_summary_local_model_status));
            row.icon.setImageResource(model.iconResId);
            row.name.setText(model.displayName);
            if (model.downloadable) {
                row.availability.setVisibility(View.GONE);
                row.tags.setVisibility(View.VISIBLE);
                row.quantization.setText(model.quantization);
                row.size.setText(LocalModelManager.formatBytes(model.sizeBytes));
                row.framework.setText(model.runtime == LocalModelManager.Runtime.LITERT_LM
                        ? "LiteRT-LM"
                        : "llama.cpp");
            } else {
                row.availability.setText("Checking availability…");
                row.availability.setVisibility(View.VISIBLE);
                row.tags.setVisibility(View.GONE);
            }
            row.card.setOnClickListener(view -> selectModel(model));
            row.action.setOnClickListener(view -> handleModelAction(model));
            modelRows.put(model.id, row);
            boundModelList.addView(item);
        }
    }

    private void selectModel(LocalModelManager.ModelInfo model) {
        if (LocalModelManager.MODEL_GEMINI_NANO.equals(model.id) && !nanoAvailable) {
            renderLocalPanel();
            return;
        }
        if (model.downloadable
                && !LocalModelManager.isModelDownloaded(getContext(), model)) {
            renderLocalPanel();
            return;
        }
        LocalModelManager.selectModel(getContext(), model.id);
        renderLocalPanel();
        notifyLocalConfigurationIfChanged();
    }

    private void renderLocalPanel() {
        if (boundModelPanel == null) {
            return;
        }
        boolean localMode = MODE_LOCAL.equals(selectedMode);
        boundModelPanel.setVisibility(localMode ? View.VISIBLE : View.GONE);
        if (!localMode) {
            return;
        }

        boolean enabled = controlsEnabled && isEnabled();
        LocalModelManager.ModelInfo selected =
                LocalModelManager.getSelectedModel(getContext());

        for (LocalModelManager.ModelInfo model : LocalModelManager.getModels()) {
            ModelRow row = modelRows.get(model.id);
            if (row == null) {
                continue;
            }
            boolean isNano = LocalModelManager.MODEL_GEMINI_NANO.equals(model.id);

            if (isNano) {
                boolean nanoEnabled = enabled && nanoAvailabilityResolved && nanoAvailable;
                row.availability.setText(nanoAvailabilityResolved
                        ? (nanoAvailable ? "Available" : "Not available")
                        : "Checking availability…");
                setModelRowEnabled(row, nanoEnabled, nanoEnabled);
                setModelRowSelected(row, nanoEnabled && model.id.equals(selected.id));
                row.action.setVisibility(View.INVISIBLE);
                row.progress.setVisibility(View.GONE);
                row.status.setVisibility(View.GONE);
                setModelRowDetailsVisible(row, false);
                continue;
            }

            LocalModelManager.Status status =
                    LocalModelManager.getStatus(getContext(), model);
            boolean downloaded = status.state == LocalModelManager.State.DOWNLOADED;
            boolean selectable = enabled && downloaded;
            setModelRowEnabled(row, enabled, selectable);
            setModelRowSelected(row, downloaded && model.id.equals(selected.id));
            boolean activeRow = status.state == LocalModelManager.State.WAITING
                    || status.state == LocalModelManager.State.DOWNLOADING;
            row.action.setVisibility(View.VISIBLE);
            row.actionEnabled = enabled;
            row.action.setEnabled(enabled && !row.actionIconAnimating);
            boolean showProgress = false;
            boolean showStatus = false;

            if (activeRow) {
                setModelActionIcon(row, R.drawable.ic_close,
                        "Cancel " + model.displayName + " download", "Cancel download");
                showProgress = true;
                showStatus = true;
                row.progress.setIndeterminate(
                        status.state == LocalModelManager.State.WAITING);
                if (status.state == LocalModelManager.State.DOWNLOADING) {
                    row.progress.setProgressCompat(status.getProgressPercent(), true);
                    row.status.setText(LocalModelManager.formatBytes(status.receivedBytes)
                            + " of " + LocalModelManager.formatBytes(model.sizeBytes)
                            + " · " + status.getProgressPercent() + "%");
                } else {
                    row.status.setText("Waiting for a network connection…");
                }
            } else if (status.state == LocalModelManager.State.DOWNLOADED) {
                setModelActionIcon(row, R.drawable.ic_delete,
                        "Delete " + model.displayName, "Delete model");
            } else {
                setModelActionIcon(row, R.drawable.ic_file_download,
                        "Download " + model.displayName, "Download model");
                if (status.state == LocalModelManager.State.PARTIALLY_DOWNLOADED) {
                    row.status.setText(LocalModelManager.formatBytes(status.receivedBytes)
                            + " downloaded · tap to resume");
                    showStatus = true;
                } else if (status.state == LocalModelManager.State.FAILED) {
                    row.status.setText(status.error + " · tap to retry");
                    showStatus = true;
                }
            }
            boolean showDetails = showProgress || showStatus;
            if (showDetails) {
                row.progress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
                row.status.setVisibility(showStatus ? View.VISIBLE : View.GONE);
            }
            setModelRowDetailsVisible(row, showDetails);
        }
    }

    private void handleModelAction(LocalModelManager.ModelInfo model) {
        LocalModelManager.Status status =
                LocalModelManager.getStatus(getContext(), model);
        if (status.state == LocalModelManager.State.DOWNLOADING
                || status.state == LocalModelManager.State.WAITING) {
            LocalModelManager.cancelDownload(getContext(), model.id);
        } else if (status.state == LocalModelManager.State.DOWNLOADED) {
            LocalModelManager.removeModel(getContext(), model.id);
        } else {
            String error = LocalModelManager.downloadModel(getContext(), model.id);
            if (!TextUtils.isEmpty(error)) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void applyBoundControlsEnabled(boolean enabled) {
        if (boundItemView != null) {
            boundItemView.setEnabled(enabled);
        }
        if (boundTitle != null) {
            boundTitle.setEnabled(enabled);
            boundTitle.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
        }
        if (boundGroup != null) {
            boundGroup.setEnabled(enabled);
        }
        if (boundLocalButton != null) {
            boundLocalButton.setEnabled(enabled && localAvailable);
        }
        if (boundCloudButton != null) {
            boundCloudButton.setEnabled(enabled);
        }
    }

    private void setModelRowEnabled(ModelRow row, boolean enabled, boolean selectable) {
        row.card.setEnabled(enabled);
        row.card.setClickable(selectable);
        row.card.setFocusable(selectable);
        row.icon.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
        row.name.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
        row.availability.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
        row.quantization.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
        row.size.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
        row.framework.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
    }

    private void setModelRowSelected(ModelRow row, boolean selected) {
        int normalBackground = MaterialColors.getColor(
                row.card,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT);
        int selectedBase = MaterialColors.getColor(
                row.card,
                com.google.android.material.R.attr.colorPrimaryContainer,
                normalBackground);
        int outline = MaterialColors.getColor(
                row.card,
                com.google.android.material.R.attr.colorOutlineVariant,
                Color.TRANSPARENT);
        int primary = MaterialColors.getColor(
                row.card,
                androidx.appcompat.R.attr.colorPrimary,
                outline);
        int targetBackground = selected
                ? ColorUtils.blendARGB(normalBackground, selectedBase, 0.12f)
                : normalBackground;
        int targetStroke = selected
                ? primary
                : outline;
        int targetStrokeWidth = dp(selected ? 2 : 1);
        row.card.setContentDescription(
                row.name.getText() + (selected ? ", selected" : ""));

        if (!row.selectionInitialized || !ViewCompat.isLaidOut(row.card)) {
            row.selectionInitialized = true;
            row.selected = selected;
            applySelectionAppearance(row, targetBackground, targetStroke, targetStrokeWidth);
            return;
        }
        if (row.selected == selected) {
            return;
        }

        row.selected = selected;
        if (row.selectionAnimator != null) {
            row.selectionAnimator.cancel();
        }
        int startBackground = row.backgroundColor;
        int startStroke = row.strokeColor;
        int startStrokeWidth = row.strokeWidth;
        ArgbEvaluator colorEvaluator = new ArgbEvaluator();
        row.selectionAnimator = ValueAnimator.ofFloat(0f, 1f);
        row.selectionAnimator.setDuration(200L);
        row.selectionAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        row.selectionAnimator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            int background = (int) colorEvaluator.evaluate(
                    fraction, startBackground, targetBackground);
            int stroke = (int) colorEvaluator.evaluate(fraction, startStroke, targetStroke);
            int strokeWidth = Math.round(startStrokeWidth
                    + (targetStrokeWidth - startStrokeWidth) * fraction);
            applySelectionAppearance(row, background, stroke, strokeWidth);
        });
        row.selectionAnimator.start();
    }

    private void applySelectionAppearance(ModelRow row, int background,
                                            int stroke, int strokeWidth) {
        row.backgroundColor = background;
        row.strokeColor = stroke;
        row.strokeWidth = strokeWidth;
        row.card.setCardBackgroundColor(background);
        row.card.setStrokeColor(stroke);
        row.card.setStrokeWidth(strokeWidth);
    }

    private void setModelRowDetailsVisible(ModelRow row, boolean visible) {
        if (!row.detailsInitialized || !ViewCompat.isLaidOut(row.card)) {
            row.detailsInitialized = true;
            row.detailsVisible = visible;
            row.details.setAlpha(1f);
            row.details.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            row.details.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (!visible) {
                hideModelRowDetailChildren(row);
            }
            return;
        }
        if (row.detailsVisible == visible) {
            return;
        }

        row.detailsVisible = visible;
        int generation = ++row.detailsAnimationGeneration;
        if (row.detailsHeightAnimator != null) {
            row.detailsHeightAnimator.cancel();
        }
        row.details.animate().cancel();

        if (visible) {
            animateModelRowDetailsIn(row, generation);
        } else {
            row.details.animate()
                    .alpha(0f)
                    .setDuration(100L)
                    .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                    .withEndAction(() -> {
                        if (generation == row.detailsAnimationGeneration
                                && !row.detailsVisible) {
                            animateModelRowDetailsHeight(row, row.details.getHeight(), 0,
                                    generation, false);
                        }
                    })
                    .start();
        }
    }

    private void animateModelRowDetailsIn(ModelRow row, int generation) {
        ViewGroup.LayoutParams params = row.details.getLayoutParams();
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        int availableWidth = Math.max(0, row.card.getWidth() - dp(16));
        row.details.measure(
                View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int targetHeight = row.details.getMeasuredHeight();
        int startHeight = row.details.getVisibility() == View.VISIBLE
                ? row.details.getHeight()
                : 0;
        row.details.setAlpha(0f);
        row.details.setVisibility(View.VISIBLE);
        animateModelRowDetailsHeight(row, startHeight, targetHeight, generation, true);
    }

    private void animateModelRowDetailsHeight(ModelRow row, int startHeight, int endHeight,
                                               int generation, boolean fadeInAfter) {
        ViewGroup.LayoutParams params = row.details.getLayoutParams();
        params.height = startHeight;
        row.details.setLayoutParams(params);
        row.detailsHeightAnimator = ValueAnimator.ofInt(startHeight, endHeight);
        row.detailsHeightAnimator.setDuration(140L);
        row.detailsHeightAnimator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        row.detailsHeightAnimator.addUpdateListener(animation -> {
            params.height = (int) animation.getAnimatedValue();
            row.details.setLayoutParams(params);
        });
        row.detailsHeightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (generation != row.detailsAnimationGeneration) {
                    return;
                }
                if (fadeInAfter && row.detailsVisible) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    row.details.setLayoutParams(params);
                    row.details.animate()
                            .alpha(1f)
                            .setDuration(100L)
                            .setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f))
                            .start();
                } else if (!fadeInAfter && !row.detailsVisible) {
                    row.details.setVisibility(View.GONE);
                    row.details.setAlpha(1f);
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    row.details.setLayoutParams(params);
                    hideModelRowDetailChildren(row);
                }
            }
        });
        row.detailsHeightAnimator.start();
    }

    private void hideModelRowDetailChildren(ModelRow row) {
        row.progress.setVisibility(View.GONE);
        row.status.setVisibility(View.GONE);
    }

    private void setModelActionIcon(ModelRow row, int iconRes,
                                    String contentDescription, String tooltip) {
        row.action.setContentDescription(contentDescription);
        TooltipCompat.setTooltipText(row.action, tooltip);
        if (!row.actionIconInitialized
                || !ViewCompat.isLaidOut(row.action)) {
            row.actionIconInitialized = true;
            row.actionIconRes = iconRes;
            row.actionIconAnimating = false;
            row.action.animate().cancel();
            row.action.setAlpha(1f);
            row.action.setScaleX(1f);
            row.action.setScaleY(1f);
            row.action.setImageResource(iconRes);
            row.action.setEnabled(row.actionEnabled);
            return;
        }
        if (row.actionIconRes == iconRes) {
            row.action.setEnabled(row.actionEnabled && !row.actionIconAnimating);
            return;
        }

        row.actionIconRes = iconRes;
        row.actionIconAnimating = true;
        row.action.setEnabled(false);
        int generation = ++row.actionIconAnimationGeneration;
        row.action.animate().cancel();
        row.action.animate()
                .alpha(0f)
                .scaleX(ACTION_ICON_SWAP_MIN_SCALE)
                .scaleY(ACTION_ICON_SWAP_MIN_SCALE)
                .setDuration(ACTION_ICON_SWAP_OUT_DURATION_MS)
                .withEndAction(() -> {
                    if (generation != row.actionIconAnimationGeneration) {
                        return;
                    }
                    row.action.setImageResource(iconRes);
                    row.action.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(ACTION_ICON_SWAP_IN_DURATION_MS)
                            .withEndAction(() -> {
                                if (generation != row.actionIconAnimationGeneration) {
                                    return;
                                }
                                row.actionIconAnimating = false;
                                row.action.setEnabled(row.actionEnabled);
                            })
                            .start();
                })
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private void notifyLocalConfigurationIfChanged() {
        LocalModelManager.ModelInfo model =
                LocalModelManager.getSelectedModel(getContext());
        boolean ready = LocalModelManager.MODEL_GEMINI_NANO.equals(model.id)
                ? nanoAvailable
                : LocalModelManager.isModelDownloaded(getContext(), model);
        if (lastLocalConfigurationReady != null
                && lastLocalConfigurationReady == ready) {
            return;
        }
        lastLocalConfigurationReady = ready;
        if (localConfigurationChangedListener != null) {
            localConfigurationChangedListener.run();
        }
    }

    private void addModeChangeListener(MaterialButtonToggleGroup group) {
        group.addOnButtonCheckedListener((buttonGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            String mode = checkedId == R.id.ai_summary_mode_local ? MODE_LOCAL : MODE_CLOUD;
            String current = PreferenceManager.getDefaultSharedPreferences(getContext())
                    .getString("pref_ai_summary_mode", MODE_CLOUD);
            selectedMode = mode;
            animateLocalPanelVisibility(MODE_LOCAL.equals(mode));
            renderLocalPanel();
            if (current.equals(mode)) {
                return;
            }
            if (!callChangeListener(mode)) {
                selectedMode = current;
                buttonGroup.check(MODE_LOCAL.equals(current)
                        ? R.id.ai_summary_mode_local
                        : R.id.ai_summary_mode_cloud);
                renderLocalPanel();
                return;
            }
            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .edit()
                    .putString("pref_ai_summary_mode", mode)
                    .apply();
        });
    }

    private void animateLocalPanelVisibility(boolean visible) {
        if (boundModelPanel == null || boundModelPanel.getVisibility() ==
                (visible ? View.VISIBLE : View.GONE)) {
            return;
        }
        View parent = (View) boundModelPanel.getParent();
        if (parent instanceof ViewGroup && ViewCompat.isLaidOut(parent)) {
            TransitionManager.endTransitions((ViewGroup) parent);
            TransitionSet transition = new TransitionSet()
                    .setOrdering(TransitionSet.ORDERING_TOGETHER)
                    .addTransition(new Fade(Fade.IN | Fade.OUT))
                    .addTransition(new ChangeBounds())
                    .setDuration(220L);
            transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
            TransitionManager.beginDelayedTransition((ViewGroup) parent, transition);
        }
        boundModelPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void selectFirstDownloadedModelOrClear() {
        for (LocalModelManager.ModelInfo model : LocalModelManager.getModels()) {
            if (model.downloadable
                    && LocalModelManager.isModelDownloaded(getContext(), model)) {
                LocalModelManager.selectModel(getContext(), model.id);
                return;
            }
        }
        LocalModelManager.clearSelectedModel(getContext());
    }

    private static final class ModelRow {
        final MaterialCardView card;
        final ImageView icon;
        final TextView name;
        final TextView availability;
        final View tags;
        final TextView quantization;
        final TextView size;
        final TextView framework;
        final ImageButton action;
        final View details;
        final LinearProgressIndicator progress;
        final TextView status;
        boolean selectionInitialized;
        boolean selected;
        int backgroundColor;
        int strokeColor;
        int strokeWidth;
        ValueAnimator selectionAnimator;
        boolean detailsInitialized;
        boolean detailsVisible;
        int detailsAnimationGeneration;
        ValueAnimator detailsHeightAnimator;
        boolean actionEnabled;
        boolean actionIconInitialized;
        boolean actionIconAnimating;
        int actionIconRes;
        int actionIconAnimationGeneration;

        ModelRow(MaterialCardView card,
                 ImageView icon,
                 TextView name,
                 TextView availability,
                 View tags,
                 TextView quantization,
                 TextView size,
                 TextView framework,
                 ImageButton action,
                 View details,
                 LinearProgressIndicator progress,
                 TextView status) {
            this.card = card;
            this.icon = icon;
            this.name = name;
            this.availability = availability;
            this.tags = tags;
            this.quantization = quantization;
            this.size = size;
            this.framework = framework;
            this.action = action;
            this.details = details;
            this.progress = progress;
            this.status = status;
        }
    }
}
