package com.simon.harmonichackernews;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.databinding.WelcomeDialogBinding;
import com.simon.harmonichackernews.databinding.StoryListItemBinding;
import com.simon.harmonichackernews.network.AiModelCatalog;
import com.simon.harmonichackernews.utils.DialogWindowUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

public class WelcomeDialogFragment extends AppCompatDialogFragment {

    private static final String TAG = "WelcomeDialogFragment";
    private static final String ARG_SHOW_VERSION_TITLE = "show_version_title";
    private static final String ARG_STYLE_CHOOSER = "style_chooser";
    private static final String FONT_EXPRESSIVE = "googlesansflexrounded";
    private static final String FONT_CLEAN = "productsans";
    private static final long PRESET_TRANSITION_DURATION_MS = 180L;
    private ValueAnimator previewTintAnimator;
    private Integer currentPreviewBackgroundColor;
    private int reservedPreviewWidth;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        boolean styleChooser = isStyleChooser();
        AiModelCatalog.ensureInitialDefault(requireContext());
        WelcomeDialogBinding binding = WelcomeDialogBinding.inflate(LayoutInflater.from(requireContext()));
        if (styleChooser) {
            binding.welcomeDialogLogo.setVisibility(View.GONE);
            binding.welcomeDialogTitle.setText("Style");
            binding.welcomeDialogBody.setText(
                    "Choose a general style for the app. This changes the font, story preview images and palette tint.");
            binding.welcomeDialogSettingsNote.setVisibility(View.GONE);
            binding.welcomeDialogGetStarted.setText("Apply");
        } else {
            binding.welcomeDialogTitle.setText(shouldShowVersionTitle()
                    ? "Welcome to Harmonic for Hacker News 3.0"
                    : "Welcome to Harmonic for Hacker News");
        }
        ViewCompat.setAccessibilityHeading(binding.welcomeDialogTitle, true);
        setupStoryPreview(binding);
        binding.welcomeDialogPresetGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            updatePresetPreview(binding, checkedId != R.id.welcome_dialog_preset_clean, true);
        });
        boolean expressive = !styleChooser || !isSimplePresetSelected();
        binding.welcomeDialogPresetGroup.check(expressive
                ? R.id.welcome_dialog_preset_expressive
                : R.id.welcome_dialog_preset_clean);
        updatePresetPreview(binding, expressive, false);
        binding.welcomeDialogStoryPreviewContainer.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                    int width = right - left;
                    if (width > 0 && width != reservedPreviewWidth) {
                        reservedPreviewWidth = width;
                        reservePreviewHeight(binding, width);
                    }
                });
        binding.welcomeDialogStoryPreviewContainer.post(() -> {
            int width = binding.welcomeDialogStoryPreviewContainer.getWidth();
            if (width > 0) {
                reservedPreviewWidth = width;
                reservePreviewHeight(binding, width);
            }
        });
        binding.welcomeDialogGetStarted.setOnClickListener(view -> {
            applySelectedPreset(binding);
            FontUtils.init(requireContext());
            MainActivity.applyWelcomePresetToActiveUi();
            if (!styleChooser) {
                Utils.markWelcomeDialogShown(requireContext());
            }
            dismiss();
        });
        setCancelable(styleChooser);

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(binding.getRoot())
                .create();
    }

    @Override
    public void onDestroyView() {
        cancelPreviewTintAnimator();
        super.onDestroyView();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() instanceof DialogHostActivity) {
            DialogWindowUtils.applyMaxWidth(getDialog());
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (getActivity() instanceof DismissListener) {
            ((DismissListener) getActivity()).onWelcomeDialogDismissed();
        }
    }

    private boolean shouldShowVersionTitle() {
        Bundle args = getArguments();
        return args != null && args.getBoolean(ARG_SHOW_VERSION_TITLE, false);
    }

    private boolean isStyleChooser() {
        Bundle args = getArguments();
        return args != null && args.getBoolean(ARG_STYLE_CHOOSER, false);
    }

    private boolean isSimplePresetSelected() {
        return FONT_CLEAN.equals(SettingsUtils.getPreferredFont(requireContext()))
                && !SettingsUtils.shouldTintCardUsingPreview(requireContext())
                && SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(
                        SettingsUtils.getPreferredStoryPreviewImageMode(requireContext()));
    }

    private void setupStoryPreview(WelcomeDialogBinding binding) {
        setupStoryPreview(binding.welcomeDialogStoryPreview);
    }

    private void setupStoryPreview(StoryListItemBinding story) {
        story.storyTitle.setText("Post title");
        story.storyMeta.setText("53 points \u2022 domain \u2022 2h");
        story.storyComments.setText("18");
        story.storyMetaFavicon.setImageResource(R.drawable.quanta);
        story.storyPreviewImageSmall.setImageResource(R.drawable.palette1);
        story.storyPreviewImageLarge.setVisibility(View.GONE);
        story.storyIndex.setVisibility(View.GONE);
    }

    private void updatePresetPreview(WelcomeDialogBinding binding, boolean expressive, boolean animate) {
        if (animate && ViewCompat.isLaidOut(binding.getRoot())) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(PRESET_TRANSITION_DURATION_MS);
            TransitionManager.beginDelayedTransition(binding.getRoot(), transition);
        }

        setPreviewFont(binding, expressive ? FONT_EXPRESSIVE : FONT_CLEAN);

        StoryListItemBinding story = binding.welcomeDialogStoryPreview;
        story.storyPreviewImageSmall.setVisibility(expressive ? View.VISIBLE : View.GONE);
        setPreviewBackgroundColor(
                binding,
                expressive ? getPalettedPreviewTintColor() : Color.TRANSPARENT,
                animate);
    }

    private void setPreviewFont(WelcomeDialogBinding binding, String font) {
        setPreviewFont(binding.welcomeDialogStoryPreview, font);
    }

    private void setPreviewFont(StoryListItemBinding story, String font) {
        setTextFont(story.storyTitle, font, true, 17.5f);
        setTextFont(story.storyMeta, font, false, 13);
        setTextFont(story.storyComments, font, true, 14);
        setTextFont(story.storyIndex, font, false, 16);
    }

    private int getPalettedPreviewTintColor() {
        Drawable drawable = ContextCompat.getDrawable(requireContext(), R.drawable.palette1);
        return drawable == null
                ? Color.TRANSPARENT
                : PreviewImageTintUtils.calculateCardTint(requireContext(), drawable);
    }

    private void setPreviewBackgroundColor(WelcomeDialogBinding binding, int color, boolean animate) {
        int startColor = currentPreviewBackgroundColor == null ? Color.TRANSPARENT : currentPreviewBackgroundColor;
        int endColor = color;
        cancelPreviewTintAnimator();

        if (Color.alpha(startColor) == 0 && Color.alpha(endColor) > 0) {
            startColor = withAlpha(endColor, 0);
        } else if (Color.alpha(startColor) > 0 && Color.alpha(endColor) == 0) {
            endColor = withAlpha(startColor, 0);
        }

        if (!animate || startColor == endColor) {
            setPreviewBackgroundColorNow(binding, endColor);
            return;
        }

        previewTintAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, endColor);
        previewTintAnimator.setDuration(PRESET_TRANSITION_DURATION_MS);
        previewTintAnimator.addUpdateListener(animation ->
                setPreviewBackgroundColorNow(binding, (int) animation.getAnimatedValue()));
        previewTintAnimator.start();
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void setPreviewBackgroundColorNow(WelcomeDialogBinding binding, int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(8));
        binding.welcomeDialogStoryPreviewContainer.setBackground(background);
        currentPreviewBackgroundColor = color;
    }

    private void cancelPreviewTintAnimator() {
        if (previewTintAnimator == null) {
            return;
        }

        previewTintAnimator.removeAllUpdateListeners();
        previewTintAnimator.cancel();
        previewTintAnimator = null;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void reservePreviewHeight(WelcomeDialogBinding binding, int width) {
        int expressiveHeight = measurePreviewHeight(true, width);
        int cleanHeight = measurePreviewHeight(false, width);
        binding.welcomeDialogStoryPreviewContainer.setMinimumHeight(Math.max(expressiveHeight, cleanHeight));
    }

    private int measurePreviewHeight(boolean expressive, int width) {
        StoryListItemBinding preview = StoryListItemBinding.inflate(getLayoutInflater());
        setupStoryPreview(preview);
        setPreviewFont(preview, expressive ? FONT_EXPRESSIVE : FONT_CLEAN);
        preview.storyPreviewImageSmall.setVisibility(expressive ? View.VISIBLE : View.GONE);
        preview.getRoot().measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return preview.getRoot().getMeasuredHeight();
    }

    private void setTextFont(TextView textView, String font, boolean bold, float size) {
        FontUtils.setTypefaceForFont(textView, font, bold, size);
    }

    private void applySelectedPreset(WelcomeDialogBinding binding) {
        boolean expressive = binding.welcomeDialogPresetGroup.getCheckedButtonId()
                != R.id.welcome_dialog_preset_clean;
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .edit();
        if (expressive) {
            editor.putBoolean(SettingsUtils.PREF_TINT_CARD_USING_PREVIEW, true)
                    .putString(SettingsUtils.PREF_FONT, FONT_EXPRESSIVE)
                    .putString(SettingsUtils.PREF_STORY_PREVIEW_IMAGE_MODE, SettingsUtils.STORY_PREVIEW_IMAGE_SMALL);
        } else {
            editor.putBoolean(SettingsUtils.PREF_TINT_CARD_USING_PREVIEW, false)
                    .putString(SettingsUtils.PREF_FONT, FONT_CLEAN)
                    .putString(SettingsUtils.PREF_STORY_PREVIEW_IMAGE_MODE, SettingsUtils.STORY_PREVIEW_IMAGE_OFF);
        }
        editor.apply();
    }

    public static void show(FragmentManager fm, boolean showVersionTitle) {
        if (fm.findFragmentByTag(TAG) != null) {
            return;
        }

        WelcomeDialogFragment fragment = new WelcomeDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_SHOW_VERSION_TITLE, showVersionTitle);
        fragment.setArguments(args);
        fragment.show(fm, TAG);
    }

    public static void showStyleChooser(FragmentManager fm) {
        if (fm.findFragmentByTag(TAG) != null) {
            return;
        }

        WelcomeDialogFragment fragment = new WelcomeDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_STYLE_CHOOSER, true);
        fragment.setArguments(args);
        fragment.show(fm, TAG);
    }

    public interface DismissListener {
        void onWelcomeDialogDismissed();
    }
}
