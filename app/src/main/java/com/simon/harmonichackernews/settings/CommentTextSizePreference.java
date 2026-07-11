package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import com.simon.harmonichackernews.utils.SettingsUtils;

public class CommentTextSizePreference extends TextSizePreference {

    public CommentTextSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CommentTextSizePreference(Context context) {
        this(context, null);
    }

    @Override
    protected float getDefaultTextSize() {
        return SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE;
    }

    @Override
    protected float clampTextSize(float textSize) {
        return SettingsUtils.clampCommentTextSize(textSize);
    }

    @Override
    protected int getTextSizeOffset(float textSize) {
        return SettingsUtils.getCommentTextSizeOffset(textSize);
    }

    @Override
    protected float getTextSizeForOffset(int offset) {
        return SettingsUtils.getCommentTextSizeForOffset(offset);
    }
}
