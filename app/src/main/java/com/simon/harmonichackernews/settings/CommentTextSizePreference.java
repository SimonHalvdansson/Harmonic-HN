package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;

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

        Slider slider = (Slider) holder.findViewById(R.id.comment_text_size_slider);
        TextView value = (TextView) holder.findViewById(R.id.comment_text_size_value);
        if (slider == null) {
            return;
        }

        int textSize = getPersistedTextSize();
        slider.clearOnChangeListeners();
        slider.setValue(textSize);
        updateValueText(value, textSize);
        slider.addOnChangeListener((changedSlider, sliderValue, fromUser) -> {
            int roundedValue = SettingsUtils.clampCommentTextSize(Math.round(sliderValue));
            updateValueText(value, roundedValue);
            if (!fromUser) {
                return;
            }

            String stringValue = String.valueOf(roundedValue);
            if (stringValue.equals(getPersistedString(String.valueOf(SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE)))) {
                return;
            }

            if (!callChangeListener(stringValue)) {
                int previousValue = getPersistedTextSize();
                changedSlider.setValue(previousValue);
                updateValueText(value, previousValue);
                return;
            }

            persistString(stringValue);
        });
    }

    private int getPersistedTextSize() {
        try {
            return SettingsUtils.clampCommentTextSize(Integer.parseInt(
                    getPersistedString(String.valueOf(SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE))));
        } catch (NumberFormatException e) {
            return SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE;
        }
    }

    private void updateValueText(TextView value, int textSize) {
        if (value != null) {
            String suffix = textSize == SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE
                    ? " sp (default)"
                    : " sp";
            value.setText(textSize + suffix);
        }
    }
}
