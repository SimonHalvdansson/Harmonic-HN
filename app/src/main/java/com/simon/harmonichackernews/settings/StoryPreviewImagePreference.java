package com.simon.harmonichackernews.settings;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.databinding.PreferenceStoryPreviewImageBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class StoryPreviewImagePreference extends Preference {

    public StoryPreviewImagePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_story_preview_image);
        setSelectable(false);
    }

    public StoryPreviewImagePreference(Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        holder.itemView.setFocusable(false);

        PreferenceStoryPreviewImageBinding binding = PreferenceStoryPreviewImageBinding.bind(holder.itemView);
        MaterialButtonToggleGroup group = binding.storyPreviewImageModeGroup;

        group.clearOnButtonCheckedListeners();
        group.check(getButtonIdForMode(getPersistedString(SettingsUtils.STORY_PREVIEW_IMAGE_SMALL)));
        group.addOnButtonCheckedListener((buttonGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            String mode = getModeForButtonId(checkedId);
            String currentMode = getPersistedString(SettingsUtils.STORY_PREVIEW_IMAGE_SMALL);
            if (currentMode.equals(mode)) {
                return;
            }

            if (!callChangeListener(mode)) {
                buttonGroup.check(getButtonIdForMode(currentMode));
                return;
            }

            persistString(mode);
        });
    }

    private int getButtonIdForMode(String mode) {
        if (SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(mode)) {
            return R.id.story_preview_image_mode_small;
        }
        if (SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(mode)) {
            return R.id.story_preview_image_mode_large;
        }
        return R.id.story_preview_image_mode_off;
    }

    private String getModeForButtonId(int checkedId) {
        if (checkedId == R.id.story_preview_image_mode_small) {
            return SettingsUtils.STORY_PREVIEW_IMAGE_SMALL;
        }
        if (checkedId == R.id.story_preview_image_mode_large) {
            return SettingsUtils.STORY_PREVIEW_IMAGE_LARGE;
        }
        return SettingsUtils.STORY_PREVIEW_IMAGE_OFF;
    }
}
