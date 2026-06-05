package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

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
        slider.clearOnChangeListeners();
        slider.setValueFrom(SettingsUtils.MIN_READER_MODE_FONT_SIZE);
        slider.setValueTo(SettingsUtils.MAX_READER_MODE_FONT_SIZE);
        slider.setStepSize(1);
        slider.setLabelFormatter(value -> formatFontSize(Math.round(value)));
        slider.setValue(SettingsUtils.clampReaderModeFontSize(getPersistedInt(SettingsUtils.DEFAULT_READER_MODE_FONT_SIZE)));
        slider.addOnChangeListener((changedSlider, value, fromUser) -> {
            if (!fromUser) {
                return;
            }

            int fontSize = SettingsUtils.clampReaderModeFontSize(Math.round(value));
            if (fontSize == getPersistedInt(SettingsUtils.DEFAULT_READER_MODE_FONT_SIZE)) {
                return;
            }

            if (!callChangeListener(fontSize)) {
                changedSlider.setValue(SettingsUtils.clampReaderModeFontSize(getPersistedInt(SettingsUtils.DEFAULT_READER_MODE_FONT_SIZE)));
                return;
            }

            persistInt(fontSize);
        });
    }

    private String formatFontSize(int fontSize) {
        return fontSize + " px";
    }
}
