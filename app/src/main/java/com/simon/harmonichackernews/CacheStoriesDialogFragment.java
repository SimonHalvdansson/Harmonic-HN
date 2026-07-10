package com.simon.harmonichackernews;

import android.app.Dialog;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.databinding.CacheStoriesDialogBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

public class CacheStoriesDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "tag_cache_stories_dialog";
    public static final String RESULT_KEY = "cache_stories_result";

    private static final String STATE_STORIES_TO_CACHE = "stories_to_cache";

    @Nullable
    private Slider storiesSlider;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        CacheStoriesDialogBinding binding = CacheStoriesDialogBinding.inflate(getLayoutInflater());
        Slider slider = binding.cacheStoriesSlider;
        TextView valueText = binding.cacheStoriesValue;
        storiesSlider = slider;

        int storiesToCache = savedInstanceState == null
                ? SettingsUtils.getStoriesToCache(requireContext())
                : SettingsUtils.sanitizeStoriesToCache(savedInstanceState.getInt(
                        STATE_STORIES_TO_CACHE,
                        SettingsUtils.DEFAULT_STORIES_TO_CACHE));

        slider.setValueFrom(SettingsUtils.MIN_STORIES_TO_CACHE);
        slider.setValueTo(SettingsUtils.MAX_STORIES_TO_CACHE);
        slider.setStepSize(SettingsUtils.STORIES_TO_CACHE_STEP);
        slider.setLabelFormatter(value -> String.valueOf(Math.round(value)));
        slider.setValue(storiesToCache);
        updateValueText(valueText, storiesToCache);
        slider.addOnChangeListener((changedSlider, value, fromUser) ->
                updateValueText(valueText, SettingsUtils.sanitizeStoriesToCache(Math.round(value))));

        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.cache_stories_title)
                .setView(binding.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.cache_stories_action, (dialog, which) -> {
                    int selectedStoryCount = SettingsUtils.sanitizeStoriesToCache(
                            Math.round(slider.getValue()));
                    SettingsUtils.setStoriesToCache(requireContext(), selectedStoryCount);
                    getParentFragmentManager().setFragmentResult(RESULT_KEY, new Bundle());
                })
                .create();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        Slider slider = storiesSlider;
        if (slider != null) {
            outState.putInt(STATE_STORIES_TO_CACHE,
                    SettingsUtils.sanitizeStoriesToCache(Math.round(slider.getValue())));
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        storiesSlider = null;
        super.onDestroyView();
    }

    public static void show(@NonNull FragmentManager fragmentManager) {
        new CacheStoriesDialogFragment().show(fragmentManager, TAG);
    }

    private void updateValueText(@NonNull TextView valueText, int value) {
        valueText.setText(String.valueOf(value));
    }
}
