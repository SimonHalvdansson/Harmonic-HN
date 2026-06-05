package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceCommentDisplayStyleBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class CommentDisplayStylePreference extends StoryDisplayStylePreference {

    public CommentDisplayStylePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CommentDisplayStylePreference(Context context) {
        this(context, null);
    }

    @Override
    protected int getDisplayStyleLayoutResource() {
        return R.layout.preference_comment_display_style;
    }

    @Override
    protected int getToggleGroupId() {
        return R.id.comment_display_style_group;
    }

    @Override
    protected MaterialButtonToggleGroup getToggleGroup(PreferenceViewHolder holder) {
        return PreferenceCommentDisplayStyleBinding.bind(holder.itemView).commentDisplayStyleGroup;
    }

    @Override
    protected int getStandardButtonId() {
        return R.id.comment_display_style_standard;
    }

    @Override
    protected int getCardButtonId() {
        return R.id.comment_display_style_card;
    }

    @Override
    protected String getDefaultStyle() {
        return SettingsUtils.COMMENT_DISPLAY_STYLE_STANDARD;
    }

    @Override
    protected String getStandardStyle() {
        return SettingsUtils.COMMENT_DISPLAY_STYLE_STANDARD;
    }

    @Override
    protected String getCardStyle() {
        return SettingsUtils.COMMENT_DISPLAY_STYLE_CARD;
    }

}
