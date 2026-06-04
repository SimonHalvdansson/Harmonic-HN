package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class StoriesToCachePreference extends Preference {

    public StoriesToCachePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_stories_to_cache);
        setSelectable(false);
    }

    public StoriesToCachePreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        Slider slider = (Slider) holder.findViewById(R.id.stories_to_cache_slider);
        TextView valueText = (TextView) holder.findViewById(R.id.stories_to_cache_value);
        if (slider == null) {
            return;
        }

        int storiesToCache = SettingsUtils.getStoriesToCache(getContext());
        slider.clearOnChangeListeners();
        slider.setValueFrom(SettingsUtils.MIN_STORIES_TO_CACHE);
        slider.setValueTo(SettingsUtils.MAX_STORIES_TO_CACHE);
        slider.setStepSize(SettingsUtils.STORIES_TO_CACHE_STEP);
        slider.setLabelFormatter(value -> String.valueOf(Math.round(value)));
        slider.setValue(storiesToCache);
        updateValueText(valueText, storiesToCache);
        slider.addOnChangeListener((changedSlider, value, fromUser) -> {
            int roundedValue = SettingsUtils.sanitizeStoriesToCache(Math.round(value));
            updateValueText(valueText, roundedValue);
            if (!fromUser) {
                return;
            }

            if (roundedValue == SettingsUtils.getStoriesToCache(getContext())) {
                return;
            }

            if (!callChangeListener(roundedValue)) {
                int persistedValue = SettingsUtils.getStoriesToCache(getContext());
                changedSlider.setValue(persistedValue);
                updateValueText(valueText, persistedValue);
                return;
            }

            persistInt(roundedValue);
        });
    }

    private void updateValueText(TextView valueText, int value) {
        if (valueText != null) {
            valueText.setText(String.valueOf(value));
        }
    }
}
