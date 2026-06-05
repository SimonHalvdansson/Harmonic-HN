package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;

import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceCommentTextSizeBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class CommentTextSizePreference extends TextSizePreference {

    public CommentTextSizePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CommentTextSizePreference(Context context) {
        this(context, null);
    }

    @Override
    protected int getTextSizeLayoutResource() {
        return R.layout.preference_comment_text_size;
    }

    @Override
    protected Slider getSlider(PreferenceViewHolder holder) {
        return PreferenceCommentTextSizeBinding.bind(holder.itemView).commentTextSizeSlider;
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
