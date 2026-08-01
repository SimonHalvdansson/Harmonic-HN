package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceSplitPaneRatioBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class SplitPaneRatioPreference extends Preference {

    private static final float ENABLED_ALPHA = 1f;
    private static final float DISABLED_ALPHA = 0.38f;

    public SplitPaneRatioPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_split_pane_ratio);
        setSelectable(false);
    }

    public SplitPaneRatioPreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        Slider slider = PreferenceSplitPaneRatioBinding.bind(holder.itemView).splitPaneRatioSlider;
        TextView title = (TextView) holder.findViewById(android.R.id.title);
        TextView valueText = (TextView) holder.findViewById(R.id.split_pane_ratio_value);
        int persistedRatio = getPersistedRatio();
        updateTextEnabledState(title);
        updateTextEnabledState(valueText);

        slider.clearOnChangeListeners();
        slider.setValueFrom(SettingsUtils.MIN_SPLIT_PANE_RATIO);
        slider.setValueTo(SettingsUtils.MAX_SPLIT_PANE_RATIO);
        slider.setStepSize(1);
        slider.setLabelFormatter(value -> Math.round(value) + "%");
        slider.setValue(persistedRatio);
        updateValueText(valueText, persistedRatio);
        slider.addOnChangeListener((changedSlider, value, fromUser) -> {
            if (!fromUser) {
                updateValueText(valueText, Math.round(value));
                return;
            }

            int ratio = SettingsUtils.clampSplitPaneRatio(Math.round(value));
            updateValueText(valueText, ratio);
            if (ratio == getPersistedRatio()) {
                return;
            }

            if (!callChangeListener(ratio)) {
                int persistedSize = getPersistedRatio();
                changedSlider.setValue(persistedSize);
                updateValueText(valueText, persistedSize);
                return;
            }

            persistInt(ratio);
        });
    }

    private int getPersistedRatio() {
        int persisted = getPersistedInt(SettingsUtils.SPLIT_PANE_RATIO_UNSET);
        if (persisted == SettingsUtils.SPLIT_PANE_RATIO_UNSET) {
            return SettingsUtils.getDefaultSplitPaneRatio(getContext());
        }
        return SettingsUtils.clampSplitPaneRatio(persisted);
    }

    private String formatRatio(int ratio) {
        return ratio + "% / " + (100 - ratio) + "%";
    }

    private void updateValueText(TextView valueText, int ratio) {
        if (valueText != null) {
            valueText.setText(formatRatio(ratio));
        }
    }

    private void updateTextEnabledState(TextView textView) {
        if (textView != null) {
            textView.setEnabled(isEnabled());
            textView.setAlpha(isEnabled() ? ENABLED_ALPHA : DISABLED_ALPHA);
        }
    }
}
