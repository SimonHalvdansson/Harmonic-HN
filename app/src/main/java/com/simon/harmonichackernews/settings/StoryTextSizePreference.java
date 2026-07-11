package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import com.simon.harmonichackernews.utils.SettingsUtils;

public class StoryTextSizePreference extends TextSizePreference {

    public StoryTextSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StoryTextSizePreference(Context context) {
        this(context, null);
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
