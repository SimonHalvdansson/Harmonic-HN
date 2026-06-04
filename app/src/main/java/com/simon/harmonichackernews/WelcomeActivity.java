package com.simon.harmonichackernews;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.databinding.ActivityWelcomeBinding;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryMetaPreviewAnimator;
import com.simon.harmonichackernews.utils.ThemeUtils;

public class WelcomeActivity extends AppCompatActivity {

    private static final long PREVIEW_ANIMATION_DURATION_MS = 180;
    private ValueAnimator cardTintAnimator;
    private Integer currentCardBackgroundColor;

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
        binding.welcomeSwitchTint.setChecked(SettingsUtils.shouldTintCardUsingPreview(this));
        updateTintCardUsingPreview(binding, binding.welcomeSwitchTint.isChecked(), initialPreviewImageMode, false);

        binding.welcomeStoryPreviewImageModeGroup.check(getButtonIdForPreviewImageMode(initialPreviewImageMode));
        binding.welcomeStoryPreviewImageModeGroup.addOnButtonCheckedListener((buttonGroup, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            String previewImageMode = getPreviewImageModeForButtonId(checkedId);
            beginPreviewTransition(binding);
            updatePreviewImageMode(binding, previewImageMode);
            updateTintCardUsingPreview(binding, binding.welcomeSwitchTint.isChecked(), previewImageMode, true);
            setSetting(buttonGroup.getContext(), SettingsUtils.PREF_STORY_PREVIEW_IMAGE_MODE, previewImageMode);
        });

        binding.welcomeSwitchThumbnails.setOnCheckedChangeListener((@NonNull CompoundButton compoundButton, boolean b) -> {
            beginPreviewTransition(binding);
            favicon.setVisibility(b ? View.VISIBLE : View.GONE);
            setBooleanSetting(compoundButton.getContext(), "pref_thumbnails", b);
        });

        binding.welcomeSwitchTint.setOnCheckedChangeListener((@NonNull CompoundButton compoundButton, boolean b) -> {
            String previewImageMode = getPreviewImageModeForButtonId(binding.welcomeStoryPreviewImageModeGroup.getCheckedButtonId());
            updateTintCardUsingPreview(binding, b, previewImageMode, true);
            setBooleanSetting(compoundButton.getContext(), SettingsUtils.PREF_TINT_CARD_USING_PREVIEW, b);
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

        markSelectedThemeButton(binding);
    }

    @Override
    protected void onDestroy() {
        cancelCardTintAnimator();
        super.onDestroy();
    }

    private void markSelectedThemeButton(ActivityWelcomeBinding binding) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String selectedTheme = prefs.getString("pref_theme", "material_daynight");
        Button[] themeButtons = {
                binding.welcomeButtonMaterialDaynight,
                binding.welcomeButtonMaterialDark,
                binding.welcomeButtonMaterialLight,
                binding.welcomeButtonDark,
                binding.welcomeButtonGray,
                binding.welcomeButtonBlack,
                binding.welcomeButtonLight,
                binding.welcomeButtonHackerNews,
                binding.welcomeButtonWhite
        };

        for (Button button : themeButtons) {
            if (selectedTheme.equals(button.getTag())) {
                button.setSelected(true);
                button.setBackgroundTintList(ColorStateList.valueOf(
                        MaterialColors.getColor(button, com.google.android.material.R.attr.colorSecondary)));
                button.setTextColor(MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSecondary));
                return;
            }
        }
    }

    private void beginPreviewTransition(ActivityWelcomeBinding binding) {
        ViewGroup transitionRoot = binding.welcomeStoryCard.getParent() instanceof ViewGroup
                ? (ViewGroup) binding.welcomeStoryCard.getParent()
                : (ViewGroup) binding.storyListItem.getRoot();
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

    private void updateTintCardUsingPreview(
            ActivityWelcomeBinding binding,
            boolean tintCardUsingPreview,
            String previewImageMode,
            boolean animate) {
        int targetColor = tintCardUsingPreview
                ? getPreviewCardTintColor(binding.welcomeStoryCard, previewImageMode)
                : getUntintedPreviewCardBackgroundColor(binding.welcomeStoryCard);
        setStoryCardBackgroundColor(binding.welcomeStoryCard, targetColor, animate);
    }

    private int getPreviewCardTintColor(View view, String previewImageMode) {
        Drawable tintDrawable = ContextCompat.getDrawable(
                this,
                SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)
                        ? R.drawable.quanta
                        : R.drawable.web_preview);
        if (tintDrawable == null) {
            return getDefaultCardBackgroundColor(view);
        }

        try {
            return PreviewImageTintUtils.calculateCardTint(this, tintDrawable);
        } catch (RuntimeException ignored) {
            return getDefaultCardBackgroundColor(view);
        }
    }

    private void setStoryCardBackgroundColor(MaterialCardView card, int targetColor, boolean animate) {
        if (card == null) {
            return;
        }

        cancelCardTintAnimator();
        int currentColor = currentCardBackgroundColor != null
                ? currentCardBackgroundColor
                : card.getCardBackgroundColor().getDefaultColor();

        if (!animate || currentColor == targetColor) {
            card.setCardBackgroundColor(targetColor);
            currentCardBackgroundColor = targetColor;
            return;
        }

        cardTintAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), currentColor, targetColor);
        cardTintAnimator.setDuration(PREVIEW_ANIMATION_DURATION_MS);
        cardTintAnimator.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            card.setCardBackgroundColor(color);
            currentCardBackgroundColor = color;
        });
        cardTintAnimator.start();
    }

    private int getDefaultCardBackgroundColor(View view) {
        return MaterialColors.getColor(
                view,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT);
    }

    private int getUntintedPreviewCardBackgroundColor(View view) {
        return MaterialColors.getColor(
                view,
                android.R.attr.colorBackground,
                Color.TRANSPARENT);
    }

    private void cancelCardTintAnimator() {
        if (cardTintAnimator == null) {
            return;
        }

        cardTintAnimator.removeAllUpdateListeners();
        cardTintAnimator.cancel();
        cardTintAnimator = null;
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
