package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceStoryTextSizeBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class StoryTextSizePreference extends TextSizePreference {

    public StoryTextSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StoryTextSizePreference(Context context) {
        this(context, null);
    }

    @Override
    protected int getTextSizeLayoutResource() {
        return R.layout.preference_story_text_size;
    }

    @Override
    protected Slider getSlider(PreferenceViewHolder holder) {
        return PreferenceStoryTextSizeBinding.bind(holder.itemView).storyTextSizeSlider;
    }

    @Override
    protected float getDefaultTextSize() {
        return SettingsUtils.DEFAULT_STORY_TEXT_SIZE;
    }

    @Override
    protected float clampTextSize(float textSize) {
        return SettingsUtils.clampStoryTextSize(textSize);
    }

    @Override
    protected int getTextSizeOffset(float textSize) {
        return SettingsUtils.getStoryTextSizeOffset(textSize);
    }

    @Override
    protected float getTextSizeForOffset(int offset) {
        return SettingsUtils.getStoryTextSizeForOffset(offset);
    }
}
