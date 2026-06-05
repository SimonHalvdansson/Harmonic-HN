package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceStoryDisplayStyleBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class StoryDisplayStylePreference extends Preference {

    public StoryDisplayStylePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(getDisplayStyleLayoutResource());
        setSelectable(false);
    }

    public StoryDisplayStylePreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        MaterialButtonToggleGroup group = getToggleGroup(holder);

        group.clearOnButtonCheckedListeners();
        group.check(getButtonIdForStyle(getPersistedString(getDefaultStyle())));
        group.addOnButtonCheckedListener((buttonGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            String style = getStyleForButtonId(checkedId);
            String currentStyle = getPersistedString(getDefaultStyle());
            if (currentStyle.equals(style)) {
                return;
            }

            if (!callChangeListener(style)) {
                buttonGroup.check(getButtonIdForStyle(currentStyle));
                return;
            }

            persistString(style);
        });
    }

    private int getButtonIdForStyle(String style) {
        if (getCardStyle().equals(style)) {
            return getCardButtonId();
        }
        return getStandardButtonId();
    }

    private String getStyleForButtonId(int checkedId) {
        if (checkedId == getCardButtonId()) {
            return getCardStyle();
        }
        return getStandardStyle();
    }

    protected int getDisplayStyleLayoutResource() {
        return R.layout.preference_story_display_style;
    }

    protected int getToggleGroupId() {
        return R.id.story_display_style_group;
    }

    protected MaterialButtonToggleGroup getToggleGroup(PreferenceViewHolder holder) {
        return PreferenceStoryDisplayStyleBinding.bind(holder.itemView).storyDisplayStyleGroup;
    }

    protected int getStandardButtonId() {
        return R.id.story_display_style_standard;
    }

    protected int getCardButtonId() {
        return R.id.story_display_style_card;
    }

    protected String getDefaultStyle() {
        return SettingsUtils.STORY_DISPLAY_STYLE_STANDARD;
    }

    protected String getStandardStyle() {
        return SettingsUtils.STORY_DISPLAY_STYLE_STANDARD;
    }

    protected String getCardStyle() {
        return SettingsUtils.STORY_DISPLAY_STYLE_CARD;
    }
}
