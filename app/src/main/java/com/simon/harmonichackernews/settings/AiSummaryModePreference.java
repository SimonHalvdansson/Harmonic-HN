package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.network.SummaryManager;

public class AiSummaryModePreference extends Preference {

    private static final float ENABLED_ALPHA = 1f;
    private static final float DISABLED_ALPHA = 0.38f;

    public static final String MODE_LOCAL = "local";
    public static final String MODE_CLOUD = "cloud";
    private int availabilityCheckGeneration = 0;
    private boolean controlsEnabled = true;
    private View boundItemView;
    private MaterialButtonToggleGroup boundGroup;
    private MaterialButton boundLocalButton;
    private MaterialButton boundCloudButton;
    private TextView boundTitle;
    private TextView boundStatusText;

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
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        MaterialButtonToggleGroup group = holder.itemView.findViewById(R.id.ai_summary_mode_group);
        MaterialButton localButton = holder.itemView.findViewById(R.id.ai_summary_mode_local);
        MaterialButton cloudButton = holder.itemView.findViewById(R.id.ai_summary_mode_cloud);
        TextView title = holder.itemView.findViewById(android.R.id.title);
        TextView statusText = holder.itemView.findViewById(R.id.ai_summary_mode_status);
        boundItemView = holder.itemView;
        boundGroup = group;
        boundLocalButton = localButton;
        boundCloudButton = cloudButton;
        boundTitle = title;
        boundStatusText = statusText;

        String currentMode = PreferenceManager.getDefaultSharedPreferences(getContext())
                .getString("pref_ai_summary_mode", MODE_CLOUD);
        boolean canAttemptLocal = SummaryManager.canAttemptLocalSummarization();
        boolean preferenceEnabled = controlsEnabled && isEnabled();

        group.clearOnButtonCheckedListeners();

        applyBoundControlsEnabled(preferenceEnabled);
        localButton.setEnabled(false);
        statusText.setVisibility(View.VISIBLE);
        if (canAttemptLocal) {
            statusText.setText("Checking Gemini Nano availability...");
        } else {
            statusText.setText("Gemini Nano requires Android 8.0 or newer");
        }

        int checkedId = MODE_LOCAL.equals(currentMode) && canAttemptLocal
                ? R.id.ai_summary_mode_local
                : R.id.ai_summary_mode_cloud;
        group.check(checkedId);

        if (!canAttemptLocal && MODE_LOCAL.equals(currentMode)) {
            new Handler(Looper.getMainLooper()).post(() ->
                PreferenceManager.getDefaultSharedPreferences(getContext())
                        .edit()
                        .putString("pref_ai_summary_mode", MODE_CLOUD)
                        .apply()
            );
        }

        if (!preferenceEnabled && !canAttemptLocal) {
            localButton.setEnabled(false);
            cloudButton.setEnabled(false);
            return;
        }

        if (preferenceEnabled) {
            addModeChangeListener(group);
        }

        if (canAttemptLocal) {
            int generation = ++availabilityCheckGeneration;
            SummaryManager.checkLocalSummaryAvailability(getContext(), (available, statusMessage) -> {
                if (generation != availabilityCheckGeneration) {
                    return;
                }

                boolean updatedPreferenceEnabled = controlsEnabled && isEnabled();
                applyBoundControlsEnabled(updatedPreferenceEnabled);
                localButton.setEnabled(updatedPreferenceEnabled && available);
                statusText.setVisibility(TextUtils.isEmpty(statusMessage) ? View.GONE : View.VISIBLE);
                statusText.setText(statusMessage);

                if (!updatedPreferenceEnabled) {
                    return;
                }

                String updatedMode = PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getString("pref_ai_summary_mode", MODE_CLOUD);

                group.clearOnButtonCheckedListeners();
                group.check(MODE_LOCAL.equals(updatedMode) && available
                        ? R.id.ai_summary_mode_local
                        : R.id.ai_summary_mode_cloud);
                addModeChangeListener(group);

                if (!available && MODE_LOCAL.equals(updatedMode)) {
                    PreferenceManager.getDefaultSharedPreferences(getContext())
                            .edit()
                            .putString("pref_ai_summary_mode", MODE_CLOUD)
                            .apply();
                }
            });
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
            boundLocalButton.setEnabled(false);
        }
        if (boundCloudButton != null) {
            boundCloudButton.setEnabled(enabled);
        }
        if (boundStatusText != null) {
            boundStatusText.setEnabled(enabled);
            boundStatusText.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
        }
    }

    private void addModeChangeListener(MaterialButtonToggleGroup group) {
        group.addOnButtonCheckedListener((buttonGroup, checkedId1, isChecked) -> {
            if (!isChecked) return;

            String mode = checkedId1 == R.id.ai_summary_mode_local ? MODE_LOCAL : MODE_CLOUD;
            String current = PreferenceManager.getDefaultSharedPreferences(getContext())
                    .getString("pref_ai_summary_mode", MODE_CLOUD);

            if (current.equals(mode)) return;

            if (!callChangeListener(mode)) {
                buttonGroup.check(MODE_LOCAL.equals(current)
                        ? R.id.ai_summary_mode_local
                        : R.id.ai_summary_mode_cloud);
                return;
            }

            PreferenceManager.getDefaultSharedPreferences(getContext())
                    .edit()
                    .putString("pref_ai_summary_mode", mode)
                    .apply();
        });
    }
}
