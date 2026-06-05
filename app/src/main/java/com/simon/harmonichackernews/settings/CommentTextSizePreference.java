package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceCommentTextSizeBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.util.Locale;

public class CommentTextSizePreference extends Preference {

    public CommentTextSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_comment_text_size);
        setSelectable(false);
    }

    public CommentTextSizePreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        PreferenceCommentTextSizeBinding binding = PreferenceCommentTextSizeBinding.bind(holder.itemView);
        Slider slider = binding.commentTextSizeSlider;

        float textSize = getPersistedTextSize();
        int offset = SettingsUtils.getCommentTextSizeOffset(textSize);
        slider.clearOnChangeListeners();
        slider.setLabelFormatter(sliderValue -> formatOffset(Math.round(sliderValue)));
        slider.setValue(offset);
        slider.addOnChangeListener((changedSlider, sliderValue, fromUser) -> {
            int roundedOffset = Math.round(sliderValue);
            if (!fromUser) {
                return;
            }

            float textSizeValue = SettingsUtils.getCommentTextSizeForOffset(roundedOffset);
            String stringValue = formatTextSize(textSizeValue);
            if (stringValue.equals(formatTextSize(getPersistedTextSize()))) {
                return;
            }

            if (!callChangeListener(stringValue)) {
                changedSlider.setValue(SettingsUtils.getCommentTextSizeOffset(getPersistedTextSize()));
                return;
            }

            persistString(stringValue);
        });
    }

    private float getPersistedTextSize() {
        try {
            return SettingsUtils.clampCommentTextSize(Float.parseFloat(
                    getPersistedString(String.valueOf(SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE))));
        } catch (NumberFormatException e) {
            return SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE;
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
