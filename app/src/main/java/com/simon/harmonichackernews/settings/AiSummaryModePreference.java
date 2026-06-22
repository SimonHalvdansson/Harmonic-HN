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

    public static final String MODE_LOCAL = "local";
    public static final String MODE_CLOUD = "cloud";
    private int availabilityCheckGeneration = 0;

    public AiSummaryModePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_ai_summary_mode);
        setSelectable(false);
    }

    public AiSummaryModePreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        MaterialButtonToggleGroup group = holder.itemView.findViewById(R.id.ai_summary_mode_group);
        MaterialButton localButton = holder.itemView.findViewById(R.id.ai_summary_mode_local);
        TextView statusText = holder.itemView.findViewById(R.id.ai_summary_mode_status);

        String currentMode = PreferenceManager.getDefaultSharedPreferences(getContext())
                .getString("pref_ai_summary_mode", MODE_CLOUD);
        boolean canAttemptLocal = SummaryManager.canAttemptLocalSummarization();

        group.clearOnButtonCheckedListeners();

        localButton.setEnabled(false);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(canAttemptLocal
                ? "Checking Gemini Nano availability..."
                : "Gemini Nano requires Android 8.0 or newer");

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

        addModeChangeListener(group);

        if (canAttemptLocal) {
            int generation = ++availabilityCheckGeneration;
            SummaryManager.checkLocalSummaryAvailability(getContext(), (available, statusMessage) -> {
                if (generation != availabilityCheckGeneration) {
                    return;
                }

                localButton.setEnabled(available);
                statusText.setVisibility(TextUtils.isEmpty(statusMessage) ? View.GONE : View.VISIBLE);
                statusText.setText(statusMessage);

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
