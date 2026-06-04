package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.google.android.material.slider.Slider;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaletteTintDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "tag_palette_tint_dialog";

    private static final PaletteTintOption[] OPTIONS = new PaletteTintOption[]{
            new PaletteTintOption(
                    SettingsUtils.PALETTE_TINT_DEFAULT,
                    "Muted"),
            new PaletteTintOption(
                    SettingsUtils.PALETTE_TINT_VIBRANT,
                    "Vibrant"),
            new PaletteTintOption(
                    SettingsUtils.PALETTE_TINT_DOMINANT,
                    "Dominant")
    };

    private static final PreviewSample[] PREVIEW_SAMPLES = new PreviewSample[]{
            new PreviewSample(R.drawable.palette1, "Compiler release", "143 points"),
            new PreviewSample(R.drawable.palette2, "Design notes", "89 points"),
            new PreviewSample(R.drawable.palette3, "Database internals", "311 points"),
            new PreviewSample(R.drawable.palette4, "Ask HN", "54 comments"),
            new PreviewSample(R.drawable.palette5, "Launch write-up", "217 points"),
            new PreviewSample(R.drawable.web_preview, "Website preview", "example.com")
    };

    private final Map<String, MaterialRadioButton> radioButtons = new HashMap<>();
    private final List<PreviewCard> previewCards = new ArrayList<>();
    private String selectedMode;
    private int tintStrength;
    private int tintColorfulness;
    private int tintTone;
    private Slider strengthSlider;
    private Slider colorfulnessSlider;
    private Slider toneSlider;
    private TextView strengthValue;
    private TextView colorfulnessValue;
    private TextView toneValue;
    private boolean updatingSliderValues;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.palette_tint_dialog, null);
        LinearLayout previewContainer = rootView.findViewById(R.id.palette_tint_preview_container);
        LinearLayout optionsContainer = rootView.findViewById(R.id.palette_tint_options_container);
        LinearLayout slidersContainer = rootView.findViewById(R.id.palette_tint_sliders_container);

        selectedMode = SettingsUtils.getPreferredPaletteTintMode(requireContext());
        tintStrength = SettingsUtils.getPreferredPaletteTintStrength(requireContext());
        tintColorfulness = SettingsUtils.getPreferredPaletteTintColorfulness(requireContext());
        tintTone = SettingsUtils.getPreferredPaletteTintTone(requireContext());
        buildPreviewCards(previewContainer);
        buildOptions(optionsContainer);
        buildAdjustmentControls(slidersContainer);
        updateSelection();
        updatePreviewCards();

        builder.setTitle("Configure palette tint");
        builder.setView(rootView);
        builder.setNeutralButton("Reset", null);
        builder.setPositiveButton("Done", null);
        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof AlertDialog) {
            ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> resetToDefault());
        }
    }

    public static void show(FragmentManager fm) {
        new PaletteTintDialogFragment().show(fm, TAG);
    }

    @Override
    public void onDestroyView() {
        radioButtons.clear();
        previewCards.clear();
        strengthSlider = null;
        colorfulnessSlider = null;
        toneSlider = null;
        strengthValue = null;
        colorfulnessValue = null;
        toneValue = null;
        super.onDestroyView();
    }

    private void buildPreviewCards(LinearLayout container) {
        Context context = container.getContext();
        int gap = Utils.pxFromDpInt(getResources(), 10);
        for (int i = 0; i < PREVIEW_SAMPLES.length; i++) {
            PreviewSample sample = PREVIEW_SAMPLES[i];
            PreviewCard previewCard = createPreviewCard(context, sample);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Utils.pxFromDpInt(getResources(), 136),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i < PREVIEW_SAMPLES.length - 1) {
                params.setMarginEnd(gap);
            }
            container.addView(previewCard.card, params);
            previewCards.add(previewCard);
        }
    }

    private PreviewCard createPreviewCard(Context context, PreviewSample sample) {
        int padding = Utils.pxFromDpInt(getResources(), 8);
        int imageHeight = Utils.pxFromDpInt(getResources(), 72);
        MaterialCardView card = new MaterialCardView(context);
        int strokeColor = ColorUtils.setAlphaComponent(
                MaterialColors.getColor(card, R.attr.storyColorNormal, Color.WHITE),
                36);
        card.setRadius(Utils.pxFromDpInt(getResources(), 8));
        card.setStrokeWidth(Utils.pxFromDpInt(getResources(), 1));
        card.setStrokeColor(strokeColor);
        card.setCardElevation(0f);
        card.setMaxCardElevation(0f);
        card.setUseCompatPadding(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            card.setStateListAnimator(null);
        }
        card.setClickable(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        card.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView image = new ImageView(context);
        image.setImageResource(sample.drawableRes);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        content.addView(image, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                imageHeight));

        TextView title = new TextView(context);
        title.setText(sample.title);
        title.setTextColor(MaterialColors.getColor(card, R.attr.storyColorNormal, Color.WHITE));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = Utils.pxFromDpInt(getResources(), 8);
        content.addView(title, titleParams);

        TextView meta = new TextView(context);
        meta.setText(sample.meta);
        meta.setTextColor(MaterialColors.getColor(card, R.attr.secondaryTextColor, Color.LTGRAY));
        meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        content.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        return new PreviewCard(card, sample.drawableRes);
    }

    private void buildOptions(LinearLayout container) {
        Context context = container.getContext();
        int textColor = MaterialColors.getColor(container, R.attr.storyColorNormal);
        for (PaletteTintOption option : OPTIONS) {
            container.addView(createOptionRow(context, option, textColor));
        }
    }

    private LinearLayout createOptionRow(
            Context context,
            PaletteTintOption option,
            int textColor) {
        int horizontalPadding = Utils.pxFromDpInt(getResources(), 24);
        int verticalPadding = Utils.pxFromDpInt(getResources(), 2);
        int minHeight = Utils.pxFromDpInt(getResources(), 40);
        int radioMarginEnd = Utils.pxFromDpInt(getResources(), 8);

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(minHeight);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        row.setClickable(true);
        row.setFocusable(true);
        TypedValue selectableItemBackground = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectableItemBackground, true);
        row.setBackgroundResource(selectableItemBackground.resourceId);

        MaterialRadioButton radioButton = new MaterialRadioButton(context);
        radioButton.setClickable(false);
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        radioParams.setMarginEnd(radioMarginEnd);
        row.addView(radioButton, radioParams);
        radioButtons.put(option.value, radioButton);

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(textContainer, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        TextView title = new TextView(context);
        title.setText(option.title);
        title.setTextColor(textColor);
        title.setSingleLine(false);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        textContainer.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(view -> setSelectedMode(option.value));
        return row;
    }

    private void buildAdjustmentControls(LinearLayout container) {
        Context context = container.getContext();
        int textColor = MaterialColors.getColor(container, R.attr.storyColorNormal);
        int secondaryTextColor = MaterialColors.getColor(container, R.attr.secondaryTextColor);

        SliderControl strengthControl = createSliderControl(
                context,
                "Tint strength",
                formatPercent(tintStrength),
                SettingsUtils.MIN_PALETTE_TINT_STRENGTH,
                SettingsUtils.MAX_PALETTE_TINT_STRENGTH,
                5);
        strengthSlider = strengthControl.slider;
        strengthValue = strengthControl.value;
        configureSliderText(strengthControl, textColor, secondaryTextColor);
        strengthSlider.setLabelFormatter(value -> formatPercent(Math.round(value)));
        strengthSlider.setValue(tintStrength);
        strengthSlider.addOnChangeListener((slider, value, fromUser) -> {
            tintStrength = SettingsUtils.clampPaletteTintStrength(Math.round(value));
            updateSliderLabels();
            if (!updatingSliderValues) {
                persistPaletteTintSettings();
            }
        });
        container.addView(strengthControl.root);

        SliderControl colorfulnessControl = createSliderControl(
                context,
                "Colorfulness",
                formatPercent(tintColorfulness),
                SettingsUtils.MIN_PALETTE_TINT_COLORFULNESS,
                SettingsUtils.MAX_PALETTE_TINT_COLORFULNESS,
                5);
        colorfulnessSlider = colorfulnessControl.slider;
        colorfulnessValue = colorfulnessControl.value;
        configureSliderText(colorfulnessControl, textColor, secondaryTextColor);
        colorfulnessSlider.setLabelFormatter(value -> formatPercent(Math.round(value)));
        colorfulnessSlider.setValue(tintColorfulness);
        colorfulnessSlider.addOnChangeListener((slider, value, fromUser) -> {
            tintColorfulness = SettingsUtils.clampPaletteTintColorfulness(Math.round(value));
            updateSliderLabels();
            if (!updatingSliderValues) {
                persistPaletteTintSettings();
            }
        });
        container.addView(colorfulnessControl.root);

        SliderControl toneControl = createSliderControl(
                context,
                "Brightness",
                formatTone(tintTone),
                SettingsUtils.MIN_PALETTE_TINT_TONE,
                SettingsUtils.MAX_PALETTE_TINT_TONE,
                1);
        toneSlider = toneControl.slider;
        toneValue = toneControl.value;
        configureSliderText(toneControl, textColor, secondaryTextColor);
        toneSlider.setLabelFormatter(value -> formatTone(Math.round(value)));
        toneSlider.setValue(tintTone);
        toneSlider.addOnChangeListener((slider, value, fromUser) -> {
            tintTone = SettingsUtils.clampPaletteTintTone(Math.round(value));
            updateSliderLabels();
            if (!updatingSliderValues) {
                persistPaletteTintSettings();
            }
        });
        container.addView(toneControl.root);

        updateSliderLabels();
    }

    private SliderControl createSliderControl(
            Context context,
            String labelText,
            String valueText,
            int valueFrom,
            int valueTo,
            int stepSize) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, 0, 0, Utils.pxFromDpInt(getResources(), 4));

        LinearLayout labelRow = new LinearLayout(context);
        labelRow.setGravity(Gravity.CENTER_VERTICAL);
        labelRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(labelRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(context);
        label.setText(labelText);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTypeface(label.getTypeface(), Typeface.BOLD);
        labelRow.addView(label, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        TextView value = new TextView(context);
        value.setText(valueText);
        value.setGravity(Gravity.END);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        labelRow.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Slider slider = new Slider(context);
        slider.setValueFrom(valueFrom);
        slider.setValueTo(valueTo);
        slider.setStepSize(stepSize);
        slider.setTickVisible(false);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sliderParams.topMargin = Utils.pxFromDpInt(getResources(), 2);
        root.addView(slider, sliderParams);

        return new SliderControl(root, label, value, slider);
    }

    private void configureSliderText(SliderControl control, int textColor, int secondaryTextColor) {
        control.label.setTextColor(textColor);
        control.value.setTextColor(secondaryTextColor);
    }

    private void updateSliderValues() {
        updatingSliderValues = true;
        if (strengthSlider != null) {
            strengthSlider.setValue(tintStrength);
        }
        if (colorfulnessSlider != null) {
            colorfulnessSlider.setValue(tintColorfulness);
        }
        if (toneSlider != null) {
            toneSlider.setValue(tintTone);
        }
        updateSliderLabels();
        updatingSliderValues = false;
    }

    private void updateSliderLabels() {
        if (strengthValue != null) {
            strengthValue.setText(formatPercent(tintStrength));
        }
        if (colorfulnessValue != null) {
            colorfulnessValue.setText(formatPercent(tintColorfulness));
        }
        if (toneValue != null) {
            toneValue.setText(formatTone(tintTone));
        }
    }

    private String formatPercent(int value) {
        return value + "%";
    }

    private String formatTone(int value) {
        if (value > 0) {
            return "+" + value;
        }
        return String.valueOf(value);
    }

    private void setSelectedMode(String mode) {
        selectedMode = SettingsUtils.sanitizePaletteTintMode(mode);
        persistPaletteTintSettings();
        updateSelection();
    }

    private void resetToDefault() {
        selectedMode = SettingsUtils.PALETTE_TINT_DEFAULT;
        tintStrength = SettingsUtils.DEFAULT_PALETTE_TINT_STRENGTH;
        tintColorfulness = SettingsUtils.DEFAULT_PALETTE_TINT_COLORFULNESS;
        tintTone = SettingsUtils.DEFAULT_PALETTE_TINT_TONE;
        SettingsUtils.clearPreferredPaletteTintMode(requireContext());
        updateSelection();
        updateSliderValues();
        updatePreviewCards();
    }

    private void persistPaletteTintSettings() {
        SettingsUtils.setPreferredPaletteTintSettings(
                requireContext(),
                selectedMode,
                tintStrength,
                tintColorfulness,
                tintTone);
        updatePreviewCards();
    }

    private void updateSelection() {
        String safeSelectedMode = SettingsUtils.sanitizePaletteTintMode(selectedMode);
        for (Map.Entry<String, MaterialRadioButton> entry : radioButtons.entrySet()) {
            entry.getValue().setChecked(entry.getKey().equals(safeSelectedMode));
        }
    }

    private void updatePreviewCards() {
        Context context = getContext();
        if (context == null) {
            return;
        }

        for (PreviewCard previewCard : previewCards) {
            int baseColor = MaterialColors.getColor(
                    previewCard.card,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    Color.TRANSPARENT);
            int targetColor = baseColor;
            Drawable drawable = ContextCompat.getDrawable(context, previewCard.drawableRes);
            if (drawable != null) {
                try {
                    targetColor = PreviewImageTintUtils.calculateCardTint(
                            baseColor,
                            drawable,
                            SettingsUtils.buildPaletteTintConfigKey(
                                    selectedMode,
                                    tintStrength,
                                    tintColorfulness,
                                    tintTone));
                } catch (RuntimeException ignored) {
                    targetColor = baseColor;
                }
            }
            previewCard.card.setCardBackgroundColor(targetColor);
        }
    }

    private static class PaletteTintOption {
        final String value;
        final String title;

        PaletteTintOption(String value, String title) {
            this.value = value;
            this.title = title;
        }
    }

    private static class SliderControl {
        final LinearLayout root;
        final TextView label;
        final TextView value;
        final Slider slider;

        SliderControl(LinearLayout root, TextView label, TextView value, Slider slider) {
            this.root = root;
            this.label = label;
            this.value = value;
            this.slider = slider;
        }
    }

    private static class PreviewSample {
        final int drawableRes;
        final String title;
        final String meta;

        PreviewSample(int drawableRes, String title, String meta) {
            this.drawableRes = drawableRes;
            this.title = title;
            this.meta = meta;
        }
    }

    private static class PreviewCard {
        final MaterialCardView card;
        final int drawableRes;

        PreviewCard(MaterialCardView card, int drawableRes) {
            this.card = card;
            this.drawableRes = drawableRes;
        }
    }
}
