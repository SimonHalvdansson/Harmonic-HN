package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;

import java.util.Locale;

abstract class TextSizePreference extends Preference {

    TextSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_text_size);
        setSelectable(false);
    }

    TextSizePreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        Slider slider = (Slider) holder.findViewById(R.id.text_size_slider);
        TextView valueText = (TextView) holder.findViewById(R.id.text_size_value);
        float textSize = getPersistedTextSize();
        int offset = getTextSizeOffset(textSize);

        slider.clearOnChangeListeners();
        slider.setLabelFormatter(sliderValue -> formatOffset(Math.round(sliderValue)));
        slider.setValue(offset);
        updateValueText(valueText, offset);
        slider.addOnChangeListener((changedSlider, sliderValue, fromUser) -> {
            int roundedOffset = Math.round(sliderValue);
            updateValueText(valueText, roundedOffset);
            if (!fromUser) {
                return;
            }

            float textSizeValue = getTextSizeForOffset(roundedOffset);
            String stringValue = formatTextSize(textSizeValue);
            if (stringValue.equals(formatTextSize(getPersistedTextSize()))) {
                return;
            }

            if (!callChangeListener(stringValue)) {
                int persistedOffset = getTextSizeOffset(getPersistedTextSize());
                changedSlider.setValue(persistedOffset);
                updateValueText(valueText, persistedOffset);
                return;
            }

            persistString(stringValue);
        });
    }

    protected abstract float getDefaultTextSize();

    protected abstract float clampTextSize(float textSize);

    protected abstract int getTextSizeOffset(float textSize);

    protected abstract float getTextSizeForOffset(int offset);

    private float getPersistedTextSize() {
        try {
            return clampTextSize(Float.parseFloat(
                    getPersistedString(formatTextSize(getDefaultTextSize()))));
        } catch (NumberFormatException e) {
            return getDefaultTextSize();
        }
    }

    private String formatOffset(int offset) {
        if (offset >= 0) {
            return "+" + offset;
        }
        return String.valueOf(offset);
    }

    private void updateValueText(TextView valueText, int offset) {
        if (valueText != null) {
            valueText.setText(formatOffset(offset));
        }
    }

    private String formatTextSize(float textSize) {
        if (Math.abs(textSize - Math.round(textSize)) < 0.01f) {
            return String.valueOf(Math.round(textSize));
        }
        return String.format(Locale.US, "%.1f", textSize);
    }
}
