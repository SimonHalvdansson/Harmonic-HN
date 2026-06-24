package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceReaderModeFontSizeBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class ReaderModeFontSizePreference extends Preference {

    public ReaderModeFontSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_reader_mode_font_size);
        setSelectable(false);
    }

    public ReaderModeFontSizePreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        Slider slider = PreferenceReaderModeFontSizeBinding.bind(holder.itemView).readerModeFontSizeSlider;
        TextView valueText = (TextView) holder.findViewById(R.id.text_size_value);
        int persistedFontSize = SettingsUtils.clampReaderModeFontSize(getPersistedInt(SettingsUtils.DEFAULT_READER_MODE_FONT_SIZE));

        slider.clearOnChangeListeners();
        slider.setValueFrom(SettingsUtils.MIN_READER_MODE_FONT_SIZE);
        slider.setValueTo(SettingsUtils.MAX_READER_MODE_FONT_SIZE);
        slider.setStepSize(1);
        slider.setLabelFormatter(value -> formatFontSize(Math.round(value)));
        slider.setValue(persistedFontSize);
        updateValueText(valueText, persistedFontSize);
        slider.addOnChangeListener((changedSlider, value, fromUser) -> {
            if (!fromUser) {
                updateValueText(valueText, Math.round(value));
                return;
            }

            int fontSize = SettingsUtils.clampReaderModeFontSize(Math.round(value));
            updateValueText(valueText, fontSize);
            if (fontSize == getPersistedInt(SettingsUtils.DEFAULT_READER_MODE_FONT_SIZE)) {
                return;
            }

            if (!callChangeListener(fontSize)) {
                int persistedSize = SettingsUtils.clampReaderModeFontSize(getPersistedInt(SettingsUtils.DEFAULT_READER_MODE_FONT_SIZE));
                changedSlider.setValue(persistedSize);
                updateValueText(valueText, persistedSize);
                return;
            }

            persistInt(fontSize);
        });
    }

    private String formatFontSize(int fontSize) {
        String label = fontSize + "px";
        if (fontSize == SettingsUtils.DEFAULT_READER_MODE_FONT_SIZE) {
            return label + " (default)";
        }
        return label;
    }

    private void updateValueText(TextView valueText, int fontSize) {
        if (valueText != null) {
            valueText.setText(formatFontSize(fontSize));
        }
    }
}
