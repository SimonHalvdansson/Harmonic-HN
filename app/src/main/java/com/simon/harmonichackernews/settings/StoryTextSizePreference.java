package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.util.Locale;

public class StoryTextSizePreference extends Preference {

    public StoryTextSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_story_text_size);
        setSelectable(false);
    }

    public StoryTextSizePreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        Slider slider = (Slider) holder.findViewById(R.id.story_text_size_slider);
        TextView value = (TextView) holder.findViewById(R.id.story_text_size_value);
        if (slider == null) {
            return;
        }

        float textSize = getPersistedTextSize();
        slider.clearOnChangeListeners();
        slider.setValue(textSize);
        updateValueText(value, textSize);
        slider.addOnChangeListener((changedSlider, sliderValue, fromUser) -> {
            float clampedValue = SettingsUtils.clampStoryTextSize(sliderValue);
            updateValueText(value, clampedValue);
            if (!fromUser) {
                return;
            }

            String stringValue = formatTextSize(clampedValue);
            if (stringValue.equals(getPersistedString(formatTextSize(SettingsUtils.DEFAULT_STORY_TEXT_SIZE)))) {
                return;
            }

            if (!callChangeListener(stringValue)) {
                float previousValue = getPersistedTextSize();
                changedSlider.setValue(previousValue);
                updateValueText(value, previousValue);
                return;
            }

            persistString(stringValue);
        });
    }

    private float getPersistedTextSize() {
        try {
            return SettingsUtils.clampStoryTextSize(Float.parseFloat(
                    getPersistedString(formatTextSize(SettingsUtils.DEFAULT_STORY_TEXT_SIZE))));
        } catch (NumberFormatException e) {
            return SettingsUtils.DEFAULT_STORY_TEXT_SIZE;
        }
    }

    private void updateValueText(TextView value, float textSize) {
        if (value != null) {
            String suffix = Math.abs(textSize - SettingsUtils.DEFAULT_STORY_TEXT_SIZE) < 0.01f
                    ? " sp (default)"
                    : " sp";
            value.setText(formatTextSize(textSize) + suffix);
        }
    }

    private String formatTextSize(float textSize) {
        if (Math.abs(textSize - Math.round(textSize)) < 0.01f) {
            return String.valueOf(Math.round(textSize));
        }
        return String.format(Locale.US, "%.1f", textSize);
    }
}
