package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceStoryTextSizeBinding;
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

        PreferenceStoryTextSizeBinding binding = PreferenceStoryTextSizeBinding.bind(holder.itemView);
        Slider slider = binding.storyTextSizeSlider;

        float textSize = getPersistedTextSize();
        int offset = SettingsUtils.getStoryTextSizeOffset(textSize);
        slider.clearOnChangeListeners();
        slider.setLabelFormatter(sliderValue -> formatOffset(Math.round(sliderValue)));
        slider.setValue(offset);
        slider.addOnChangeListener((changedSlider, sliderValue, fromUser) -> {
            int roundedOffset = Math.round(sliderValue);
            if (!fromUser) {
                return;
            }

            float textSizeValue = SettingsUtils.getStoryTextSizeForOffset(roundedOffset);
            String stringValue = formatTextSize(textSizeValue);
            if (stringValue.equals(formatTextSize(getPersistedTextSize()))) {
                return;
            }

            if (!callChangeListener(stringValue)) {
                changedSlider.setValue(SettingsUtils.getStoryTextSizeOffset(getPersistedTextSize()));
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

    private String formatOffset(int offset) {
        if (offset > 0) {
            return "+" + offset;
        }
        return String.valueOf(offset);
    }

    private String formatTextSize(float textSize) {
        if (Math.abs(textSize - Math.round(textSize)) < 0.01f) {
            return String.valueOf(Math.round(textSize));
        }
        return String.format(Locale.US, "%.1f", textSize);
    }
}
