package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.simon.harmonichackernews.databinding.ActivityWelcomeBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryMetaPreviewAnimator;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class WelcomeActivity extends AppCompatActivity {

    private static final long PREVIEW_ANIMATION_DURATION_MS = 180;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this);
        ActivityWelcomeBinding binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        final View root = binding.getRoot();
        setContentView(root);
        final int scrollPadLeft = binding.welcomeScroll.getPaddingLeft();
        final int scrollPadTop = binding.welcomeScroll.getPaddingTop();
        final int scrollPadRight = binding.welcomeScroll.getPaddingRight();
        final int scrollPadBottom = binding.welcomeScroll.getPaddingBottom();
        final ViewGroup.MarginLayoutParams fabParams =
                (ViewGroup.MarginLayoutParams) binding.welcomeGetStartedFab.getLayoutParams();
        final int fabBottomMargin = fabParams.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int leftInset = Math.max(bars.left, cutout.left);
            int rightInset = Math.max(bars.right, cutout.right);
            int bottomInset = Math.max(bars.bottom, ime.bottom);

            binding.welcomeScroll.setPadding(
                    scrollPadLeft + leftInset,
                    scrollPadTop + bars.top,
                    scrollPadRight + rightInset,
                    scrollPadBottom + bottomInset
            );
            ViewGroup.MarginLayoutParams updatedFabParams =
                    (ViewGroup.MarginLayoutParams) binding.welcomeGetStartedFab.getLayoutParams();
            updatedFabParams.setMargins(
                    updatedFabParams.leftMargin,
                    updatedFabParams.topMargin,
                    updatedFabParams.rightMargin,
                    fabBottomMargin + bottomInset
            );
            binding.welcomeGetStartedFab.setLayoutParams(updatedFabParams);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        ViewCompat.setAccessibilityHeading(binding.welcomeTitle, true);
        ViewCompat.setAccessibilityHeading(binding.welcomeThemeHeader, true);

        ImageView favicon = binding.storyListItem.storyMetaFavicon;
        favicon.setImageResource(R.drawable.quanta);
        TextView storyMeta = binding.storyListItem.storyMeta;
        storyMeta.setText("53 points \u2022 quantamagazine.org \u2022 2h");
        binding.storyListItem.storyIndex.setVisibility(View.VISIBLE);
        binding.storyListItem.storyPreviewImageSmall.setImageResource(R.drawable.web_preview);
        binding.storyListItem.storyPreviewImageLarge.setImageResource(R.drawable.web_preview);
        String initialPreviewImageMode = SettingsUtils.getPreferredStoryPreviewImageMode(this);
        updatePreviewImageMode(binding, initialPreviewImageMode);

        binding.welcomeStoryPreviewImageModeGroup.check(getButtonIdForPreviewImageMode(initialPreviewImageMode));
        binding.welcomeStoryPreviewImageModeGroup.addOnButtonCheckedListener((buttonGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            String previewImageMode = getPreviewImageModeForButtonId(checkedId);
            beginPreviewTransition(binding);
            updatePreviewImageMode(binding, previewImageMode);
            setSetting(buttonGroup.getContext(), SettingsUtils.PREF_STORY_PREVIEW_IMAGE_MODE, previewImageMode);
        });

        binding.welcomeSwitchThumbnails.setOnCheckedChangeListener((@NonNull CompoundButton compoundButton, boolean b) -> {
            beginPreviewTransition(binding);
            favicon.setVisibility(b ? View.VISIBLE : View.GONE);
            setBooleanSetting(compoundButton.getContext(), "pref_thumbnails", b);
        });

        binding.welcomeSwitchIndex.setOnCheckedChangeListener((@NonNull CompoundButton compoundButton, boolean b) -> {
            beginPreviewTransition(binding);
            binding.storyListItem.storyIndex.setVisibility(b ? View.VISIBLE : View.GONE);
            setBooleanSetting(compoundButton.getContext(), "pref_show_index", b);
        });

        binding.welcomeSwitchPoints.setOnCheckedChangeListener((@NonNull CompoundButton compoundButton, boolean b) -> {
            beginPreviewTransition(binding);
            StoryMetaPreviewAnimator.setPointsVisible(binding.storyListItem.storyMeta, b, true);
            setBooleanSetting(compoundButton.getContext(), "pref_show_points", b);
        });

        View.OnClickListener buttonClickListener = (View view) -> {
            setSetting(view.getContext(), "pref_theme", (String) view.getTag());
            restartActivity();
        };

        binding.welcomeButtonMaterialDaynight.setOnClickListener(buttonClickListener);
        binding.welcomeButtonMaterialDark.setOnClickListener(buttonClickListener);
        binding.welcomeButtonMaterialLight.setOnClickListener(buttonClickListener);
        binding.welcomeButtonDark.setOnClickListener(buttonClickListener);
        binding.welcomeButtonGray.setOnClickListener(buttonClickListener);
        binding.welcomeButtonBlack.setOnClickListener(buttonClickListener);
        binding.welcomeButtonLight.setOnClickListener(buttonClickListener);
        binding.welcomeButtonHackerNews.setOnClickListener(buttonClickListener);
        binding.welcomeButtonWhite.setOnClickListener(buttonClickListener);
    }

    private void beginPreviewTransition(ActivityWelcomeBinding binding) {
        ViewGroup previewRoot = (ViewGroup) binding.storyListItem.getRoot();
        ViewGroup transitionRoot = previewRoot.getParent() instanceof ViewGroup
                ? (ViewGroup) previewRoot.getParent()
                : previewRoot;
        if (!ViewCompat.isLaidOut(transitionRoot)) {
            return;
        }

        ChangeBounds transition = new ChangeBounds();
        transition.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        transition.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        TransitionManager.beginDelayedTransition(transitionRoot, transition);
    }

    private void updatePreviewImageMode(ActivityWelcomeBinding binding, String previewImageMode) {
        boolean showSmallPreview = SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(previewImageMode);
        boolean showLargePreview = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode);
        binding.storyListItem.storyPreviewImageSmall.setVisibility(showSmallPreview ? View.VISIBLE : View.GONE);
        binding.storyListItem.storyPreviewImageLarge.setVisibility(showLargePreview ? View.VISIBLE : View.GONE);
    }

    private String getPreviewImageModeForButtonId(int checkedId) {
        if (checkedId == R.id.welcome_story_preview_image_mode_small) {
            return SettingsUtils.STORY_PREVIEW_IMAGE_SMALL;
        }
        if (checkedId == R.id.welcome_story_preview_image_mode_large) {
            return SettingsUtils.STORY_PREVIEW_IMAGE_LARGE;
        }
        return SettingsUtils.STORY_PREVIEW_IMAGE_OFF;
    }

    private int getButtonIdForPreviewImageMode(String previewImageMode) {
        if (SettingsUtils.STORY_PREVIEW_IMAGE_SMALL.equals(previewImageMode)) {
            return R.id.welcome_story_preview_image_mode_small;
        }
        if (SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)) {
            return R.id.welcome_story_preview_image_mode_large;
        }
        return R.id.welcome_story_preview_image_mode_off;
    }

    @SuppressLint("ApplySharedPref")
    private void setSetting(Context ctx, String key, String newTheme) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.edit().putString(key, newTheme).commit();
    }

    @SuppressLint("ApplySharedPref")
    private void setBooleanSetting(Context ctx, String key, boolean newVal) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        prefs.edit().putBoolean(key, newVal).commit();
    }

    private void restartActivity() {
        Intent intent = new Intent(this, WelcomeActivity.class);
        startActivity(intent);
        finish();
    }

    public void done(View view) {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
