package com.simon.harmonichackernews.settings;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.HashMap;
import java.util.Map;

public class ThemeSelectionDialogFragment extends AppCompatDialogFragment {

    public static final String TAG = "tag_theme_selection_dialog";
    public static final String RESULT_KEY = "theme_selection_result";
    public static final String RESULT_PREF_KEY = "pref_key";
    public static final String RESULT_THEME = "theme";
    public static final String RESULT_PREVIOUS_THEME = "previous_theme";

    private static final String ARG_PREF_KEY = "pref_key";
    private static final String ARG_DEFAULT_THEME = "default_theme";
    private static final String ARG_DIALOG_TITLE = "dialog_title";
    private static final String ARG_SHOW_AUTO_OPTIONS = "show_auto_options";
    private static final String ARG_DARK_THEMES_ONLY = "dark_themes_only";

    private static final int OPTION_ROW_HEIGHT_DP = 104;
    private static final int VISIBLE_OPTION_ROWS = 6;
    private static final int MIN_VISIBLE_OPTION_ROWS = 3;
    private static final int DIALOG_VERTICAL_MARGIN_DP = 260;

    private static final ThemeOption[] THEMES = new ThemeOption[]{
            new ThemeOption("material_daynight", "Material You (auto)", "Follows the system theme", true, false),
            new ThemeOption("material_light", "Material You (light)", "Softer Material light palette", false, false),
            new ThemeOption("material_dark", "Material You (dark)", "Softer Material dark palette", false, true),
            new ThemeOption("darklight_daynight", "Dark/Light (auto)", "Classic Harmonic colors, automatic", true, false),
            new ThemeOption("light", "Light", "Warm classic light palette", false, false),
            new ThemeOption("dark", "Dark", "Classic dark palette", false, true),
            new ThemeOption("hacker_news", "HN", "Hacker News orange and paper tones", false, false),
            new ThemeOption("amoledwhite_daynight", "Black/White (auto)", "Pure contrast, automatic", true, false),
            new ThemeOption("amoled", "Black", "OLED-friendly black", false, true),
            new ThemeOption("white", "White", "Clean white background", false, false),
            new ThemeOption("gray", "Gray", "Low-contrast dark gray", false, true)
    };

    private final Map<String, MaterialRadioButton> radioButtons = new HashMap<>();
    private String prefKey;
    private String defaultTheme;
    private boolean showAutoOptions;
    private boolean darkThemesOnly;
    private String selectedTheme;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.theme_selection_dialog, null);
        View scrollView = rootView.findViewById(R.id.theme_options_scroll);
        LinearLayout container = rootView.findViewById(R.id.theme_options_container);

        Bundle args = getArguments();
        prefKey = args != null ? args.getString(ARG_PREF_KEY, SettingsUtils.PREF_THEME) : SettingsUtils.PREF_THEME;
        defaultTheme = args != null ? args.getString(ARG_DEFAULT_THEME, SettingsUtils.DEFAULT_THEME) : SettingsUtils.DEFAULT_THEME;
        showAutoOptions = args == null || args.getBoolean(ARG_SHOW_AUTO_OPTIONS, true);
        darkThemesOnly = args != null && args.getBoolean(ARG_DARK_THEMES_ONLY, false);
        String title = args != null ? args.getString(ARG_DIALOG_TITLE, "Theme") : "Theme";

        selectedTheme = getStoredTheme(requireContext());
        int optionCount = buildThemeOptions(container);
        updateSelection();
        setWholeRowDialogHeight(scrollView, optionCount);

        builder.setTitle(title);
        builder.setView(rootView);
        return builder.create();
    }

    private void setWholeRowDialogHeight(View scrollView, int optionCount) {
        int rowHeight = dp(OPTION_ROW_HEIGHT_DP);
        int availableHeight = getResources().getDisplayMetrics().heightPixels - dp(DIALOG_VERTICAL_MARGIN_DP);
        int availableRows = Math.max(1, availableHeight / rowHeight);
        int maxRows = Math.min(VISIBLE_OPTION_ROWS, availableRows);
        int rows = Math.min(Math.max(1, optionCount), maxRows);
        if (optionCount >= MIN_VISIBLE_OPTION_ROWS) {
            rows = Math.max(MIN_VISIBLE_OPTION_ROWS, rows);
        }
        int height = rowHeight * rows;
        ViewGroup.LayoutParams layoutParams = scrollView.getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        } else {
            layoutParams.height = height;
        }
        scrollView.setLayoutParams(layoutParams);
        scrollView.setMinimumHeight(height);
        scrollView.requestLayout();
    }

    public static void show(FragmentManager fm) {
        show(fm, SettingsUtils.PREF_THEME, SettingsUtils.DEFAULT_THEME, "Theme", true, false);
    }

    public static void showNighttimeTheme(FragmentManager fm) {
        show(fm, SettingsUtils.PREF_THEME_NIGHTTIME, SettingsUtils.DEFAULT_NIGHTTIME_THEME, "Nighttime theme", false, true);
    }

    private static void show(
            FragmentManager fm,
            String prefKey,
            String defaultTheme,
            String title,
            boolean showAutoOptions,
            boolean darkThemesOnly) {
        ThemeSelectionDialogFragment fragment = new ThemeSelectionDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PREF_KEY, prefKey);
        args.putString(ARG_DEFAULT_THEME, defaultTheme);
        args.putString(ARG_DIALOG_TITLE, title);
        args.putBoolean(ARG_SHOW_AUTO_OPTIONS, showAutoOptions);
        args.putBoolean(ARG_DARK_THEMES_ONLY, darkThemesOnly);
        fragment.setArguments(args);
        fragment.show(fm, TAG);
    }

    public static String getThemeLabel(Context context, String theme) {
        return getThemeLabel(context, theme, true, SettingsUtils.DEFAULT_THEME);
    }

    public static String getThemeLabel(Context context, String theme, boolean showAutoOptions, String fallbackTheme) {
        return getThemeLabel(context, theme, showAutoOptions, false, fallbackTheme);
    }

    public static String getThemeLabel(
            Context context,
            String theme,
            boolean showAutoOptions,
            boolean darkThemesOnly,
            String fallbackTheme) {
        String safeTheme = sanitizeTheme(theme, showAutoOptions, darkThemesOnly, fallbackTheme);
        for (ThemeOption option : THEMES) {
            if (option.value.equals(safeTheme)) {
                return option.title;
            }
        }
        return context.getString(android.R.string.untitled);
    }

    @Override
    public void onDestroyView() {
        radioButtons.clear();
        super.onDestroyView();
    }

    private int buildThemeOptions(LinearLayout container) {
        Context context = container.getContext();
        int textColor = MaterialColors.getColor(container, R.attr.storyColorNormal);
        int secondaryTextColor = MaterialColors.getColor(container, R.attr.secondaryTextColor);
        int optionCount = 0;

        for (ThemeOption theme : THEMES) {
            if (!shouldShowTheme(theme)) {
                continue;
            }
            container.addView(createOptionRow(context, theme, textColor, secondaryTextColor));
            optionCount++;
        }
        return optionCount;
    }

    private boolean shouldShowTheme(ThemeOption theme) {
        if (!showAutoOptions && theme.auto) {
            return false;
        }
        return !darkThemesOnly || theme.darkTheme;
    }

    private LinearLayout createOptionRow(
            Context context,
            ThemeOption theme,
            @ColorInt int textColor,
            @ColorInt int secondaryTextColor) {
        int horizontalPadding = dp(24);
        int verticalPadding = dp(10);
        int minHeight = dp(OPTION_ROW_HEIGHT_DP);
        int previewWidth = dp(104);
        int previewHeight = dp(72);
        int previewMarginEnd = dp(16);
        int radioMarginStart = dp(10);

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

        FrameLayout preview = createThemePreview(context, theme);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(previewWidth, previewHeight);
        previewParams.setMarginEnd(previewMarginEnd);
        row.addView(preview, previewParams);

        LinearLayout textContainer = new LinearLayout(context);
        textContainer.setGravity(Gravity.CENTER_VERTICAL);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        row.addView(textContainer, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        TextView title = new TextView(context);
        title.setText(theme.title);
        title.setTextColor(textColor);
        title.setSingleLine(false);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        textContainer.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(context);
        subtitle.setText(theme.description);
        subtitle.setTextColor(secondaryTextColor);
        subtitle.setSingleLine(false);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        textContainer.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialRadioButton radioButton = new MaterialRadioButton(context);
        radioButton.setClickable(false);
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        radioParams.setMarginStart(radioMarginStart);
        row.addView(radioButton, radioParams);
        radioButtons.put(theme.value, radioButton);

        row.setOnClickListener(view -> selectTheme(theme.value));
        return row;
    }

    private FrameLayout createThemePreview(Context context, ThemeOption theme) {
        switch (theme.value) {
            case "material_daynight":
                return createThemePreview(context, materialLightPalette(context), materialDarkPalette(context));
            case "darklight_daynight":
                return createThemePreview(context, lightPalette(context), darkPalette(context));
            case "amoledwhite_daynight":
                return createThemePreview(context, whitePalette(context), blackPalette(context));
            default:
                return createThemePreview(context, getPalette(context, theme.value), null);
        }
    }

    private FrameLayout createThemePreview(
            Context context,
            ThemePalette palette,
            @Nullable ThemePalette splitPalette) {
        if (splitPalette == null) {
            return createSingleThemePreview(context, palette);
        }

        FrameLayout preview = new FrameLayout(context);
        preview.addView(createSingleThemePreview(context, splitPalette), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        DiagonalMaskFrameLayout maskedPreview = new DiagonalMaskFrameLayout(context);
        maskedPreview.addView(createSingleThemePreview(context, palette), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        preview.addView(maskedPreview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        return preview;
    }

    private FrameLayout createSingleThemePreview(Context context, ThemePalette palette) {
        FrameLayout preview = new FrameLayout(context);
        preview.setBackground(createPreviewDrawable(
                palette.backgroundColor,
                dp(16),
                dp(1),
                withAlpha(palette.textColor, palette.dark ? 70 : 42)));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(7), dp(8), dp(7));
        card.setBackground(createPreviewDrawable(
                palette.surfaceColor,
                dp(12),
                dp(1),
                withAlpha(palette.textColor, palette.dark ? 44 : 28)));

        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        cardParams.setMargins(dp(7), dp(7), dp(7), dp(7));
        preview.addView(card, cardParams);

        View accent = colorPill(context, palette.accentColor, dp(8));
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(38), dp(5));
        accentParams.setMarginEnd(dp(26));
        accentParams.bottomMargin = dp(6);
        card.addView(accent, accentParams);

        View title = colorPill(
                context,
                withAlpha(palette.textColor, 220),
                dp(4));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(6));
        titleParams.bottomMargin = dp(4);
        card.addView(title, titleParams);

        LinearLayout metaRow = new LinearLayout(context);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams metaRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(4));
        metaRowParams.bottomMargin = dp(5);
        card.addView(metaRow, metaRowParams);

        View score = colorPill(
                context,
                withAlpha(palette.accentColor, 200),
                dp(4));
        LinearLayout.LayoutParams scoreParams = new LinearLayout.LayoutParams(dp(18), dp(4));
        scoreParams.setMarginEnd(dp(5));
        metaRow.addView(score, scoreParams);

        View meta = colorPill(
                context,
                withAlpha(palette.secondaryTextColor, 190),
                dp(4));
        metaRow.addView(meta, new LinearLayout.LayoutParams(dp(36), dp(4)));

        View divider = new View(context);
        divider.setBackground(createPreviewDrawable(
                withAlpha(palette.secondaryTextColor, palette.dark ? 64 : 46),
                0,
                0,
                Color.TRANSPARENT));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1));
        dividerParams.bottomMargin = dp(5);
        card.addView(divider, dividerParams);

        LinearLayout commentRow = new LinearLayout(context);
        commentRow.setGravity(Gravity.CENTER_VERTICAL);
        commentRow.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(commentRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(14)));

        View indicator = colorPill(context, palette.accentColor, dp(2));
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(dp(3), dp(14));
        indicatorParams.setMarginEnd(dp(6));
        commentRow.addView(indicator, indicatorParams);

        LinearLayout commentLines = new LinearLayout(context);
        commentLines.setOrientation(LinearLayout.VERTICAL);
        commentRow.addView(commentLines, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1));

        View commentLineTop = colorPill(
                context,
                withAlpha(palette.textColor, 190),
                dp(4));
        LinearLayout.LayoutParams commentLineTopParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(4));
        commentLineTopParams.bottomMargin = dp(3);
        commentLines.addView(commentLineTop, commentLineTopParams);

        View commentLineBottom = colorPill(
                context,
                withAlpha(palette.secondaryTextColor, 160),
                dp(4));
        commentLines.addView(commentLineBottom, new LinearLayout.LayoutParams(dp(44), dp(4)));

        return preview;
    }

    private void selectTheme(String theme) {
        String safeTheme = sanitizeTheme(theme, showAutoOptions, darkThemesOnly, defaultTheme);
        if (safeTheme.equals(selectedTheme)) {
            dismiss();
            return;
        }

        String previousTheme = selectedTheme;
        selectedTheme = safeTheme;
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(requireContext())
                .edit();
        editor.putString(prefKey, selectedTheme);
        editor.apply();
        updateSelection();

        Bundle result = new Bundle();
        result.putString(RESULT_PREF_KEY, prefKey);
        result.putString(RESULT_THEME, selectedTheme);
        result.putString(RESULT_PREVIOUS_THEME, previousTheme);
        dismiss();
        getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
    }

    private void updateSelection() {
        String safeSelectedTheme = sanitizeTheme(selectedTheme, showAutoOptions, darkThemesOnly, defaultTheme);
        for (Map.Entry<String, MaterialRadioButton> entry : radioButtons.entrySet()) {
            entry.getValue().setChecked(entry.getKey().equals(safeSelectedTheme));
        }
    }

    private String getStoredTheme(Context context) {
        String theme = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(prefKey, defaultTheme);
        if (darkThemesOnly) {
            theme = SettingsUtils.getSelectableNighttimeTheme(theme);
        }
        return sanitizeTheme(theme, showAutoOptions, darkThemesOnly, defaultTheme);
    }

    private static String sanitizeTheme(String theme) {
        return sanitizeTheme(theme, true, false, SettingsUtils.DEFAULT_THEME);
    }

    private static String sanitizeTheme(String theme, boolean showAutoOptions, String fallbackTheme) {
        return sanitizeTheme(theme, showAutoOptions, false, fallbackTheme);
    }

    private static String sanitizeTheme(
            String theme,
            boolean showAutoOptions,
            boolean darkThemesOnly,
            String fallbackTheme) {
        ThemeOption themeOption = findThemeOption(theme);
        if (isThemeSelectable(themeOption, showAutoOptions, darkThemesOnly)) {
            return theme;
        }

        ThemeOption fallbackOption = findThemeOption(fallbackTheme);
        if (isThemeSelectable(fallbackOption, showAutoOptions, darkThemesOnly)) {
            return fallbackTheme;
        }

        return showAutoOptions ? SettingsUtils.DEFAULT_THEME : SettingsUtils.DEFAULT_NIGHTTIME_THEME;
    }

    private static boolean isThemeSelectable(
            @Nullable ThemeOption theme,
            boolean showAutoOptions,
            boolean darkThemesOnly) {
        return theme != null
                && (showAutoOptions || !theme.auto)
                && (!darkThemesOnly || theme.darkTheme);
    }

    @Nullable
    private static ThemeOption findThemeOption(String theme) {
        for (ThemeOption option : THEMES) {
            if (option.value.equals(theme)) {
                return option;
            }
        }
        return null;
    }

    private ThemePalette getPalette(Context context, String theme) {
        switch (sanitizeTheme(theme)) {
            case "material_daynight":
                return ThemeUtils.uiModeNight(context)
                        ? materialDarkPalette(context)
                        : materialLightPalette(context);
            case "darklight_daynight":
                return ThemeUtils.uiModeNight(context)
                        ? darkPalette(context)
                        : lightPalette(context);
            case "amoledwhite_daynight":
                return ThemeUtils.uiModeNight(context)
                        ? blackPalette(context)
                        : whitePalette(context);
            case "material_dark":
                return materialDarkPalette(context);
            case "gray":
                return grayPalette(context);
            case "amoled":
                return blackPalette(context);
            case "light":
                return lightPalette(context);
            case "hacker_news":
                return hackerNewsPalette(context);
            case "material_light":
                return materialLightPalette(context);
            case "white":
                return whitePalette(context);
            case "dark":
            default:
                return darkPalette(context);
        }
    }

    private ThemePalette darkPalette(Context context) {
        int background = color(context, R.color.background);
        return new ThemePalette(
                background,
                color(context, R.color.darkerBackground),
                color(context, R.color.colorPrimary),
                color(context, R.color.darkStoryColorNormal),
                color(context, R.color.darkTextColorDefault),
                true);
    }

    private ThemePalette materialDarkPalette(Context context) {
        return new ThemePalette(
                color(context, R.color.material_you_neutral_900),
                color(context, R.color.material_you_neutral_variant20),
                color(context, R.color.material_you_third_400),
                color(context, R.color.darkStoryColorNormal),
                color(context, R.color.material_you_secondary_70),
                true);
    }

    private ThemePalette grayPalette(Context context) {
        int background = color(context, R.color.grayBackground);
        int text = color(context, R.color.darkStoryColorNormal);
        return new ThemePalette(
                background,
                ColorUtils.blendARGB(background, text, 0.07f),
                color(context, R.color.colorPrimary),
                text,
                color(context, R.color.darkTextColorDefault),
                true);
    }

    private ThemePalette blackPalette(Context context) {
        return new ThemePalette(
                Color.BLACK,
                Color.BLACK,
                color(context, R.color.colorPrimary),
                color(context, R.color.darkStoryColorNormal),
                color(context, R.color.darkTextColorDefault),
                true);
    }

    private ThemePalette lightPalette(Context context) {
        int background = color(context, R.color.lightBackground);
        int text = color(context, R.color.lightStoryColorNormal);
        return new ThemePalette(
                background,
                ColorUtils.blendARGB(background, Color.WHITE, 0.56f),
                color(context, R.color.colorPrimaryGreen),
                text,
                color(context, R.color.lightTextColorDefault),
                false);
    }

    private ThemePalette hackerNewsPalette(Context context) {
        return new ThemePalette(
                color(context, R.color.hackerNewsBackground),
                color(context, R.color.hackerNewsSurfaceContainer),
                color(context, R.color.hackerNewsAccent),
                color(context, R.color.hackerNewsStoryColorNormal),
                color(context, R.color.hackerNewsTextColorDisabled),
                false);
    }

    private ThemePalette materialLightPalette(Context context) {
        return new ThemePalette(
                color(context, R.color.material_you_neutral_50),
                color(context, R.color.material_you_neutral_variant95),
                color(context, R.color.material_you_third_400),
                color(context, R.color.lightStoryColorNormal),
                color(context, R.color.material_you_secondary_40),
                false);
    }

    private ThemePalette whitePalette(Context context) {
        int background = Color.WHITE;
        int text = color(context, R.color.lightStoryColorNormal);
        return new ThemePalette(
                background,
                ColorUtils.blendARGB(background, text, 0.035f),
                color(context, R.color.colorPrimaryGreen),
                text,
                color(context, R.color.lightTextColorDefault),
                false);
    }

    private View colorPill(Context context, @ColorInt int color, int radius) {
        View view = new View(context);
        view.setBackground(createPreviewDrawable(color, radius, 0, Color.TRANSPARENT));
        return view;
    }

    private GradientDrawable createPreviewDrawable(
            @ColorInt int color,
            int radius,
            int strokeWidth,
            @ColorInt int strokeColor) {
        return createRoundedDrawable(color, radius, strokeWidth, strokeColor);
    }

    private GradientDrawable createRoundedDrawable(
            @ColorInt int color,
            int radius,
            int strokeWidth,
            @ColorInt int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) {
            drawable.setStroke(strokeWidth, strokeColor);
        }
        return drawable;
    }

    private int dp(int value) {
        return Utils.pxFromDpInt(getResources(), value);
    }

    @ColorInt
    private int color(Context context, int colorResource) {
        return ContextCompat.getColor(context, colorResource);
    }

    @ColorInt
    private int withAlpha(@ColorInt int color, int alpha) {
        return ColorUtils.setAlphaComponent(color, alpha);
    }

    private static class DiagonalMaskFrameLayout extends FrameLayout {
        private final Path maskPath = new Path();

        DiagonalMaskFrameLayout(@NonNull Context context) {
            super(context);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            maskPath.reset();
            maskPath.moveTo(0f, 0f);
            maskPath.lineTo(getWidth(), 0f);
            maskPath.lineTo(0f, getHeight());
            maskPath.close();

            int save = canvas.save();
            canvas.clipPath(maskPath);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(save);
        }
    }

    private static class ThemeOption {
        final String value;
        final String title;
        final String description;
        final boolean auto;
        final boolean darkTheme;

        ThemeOption(String value, String title, String description, boolean auto, boolean darkTheme) {
            this.value = value;
            this.title = title;
            this.description = description;
            this.auto = auto;
            this.darkTheme = darkTheme;
        }
    }

    private static class ThemePalette {
        final int backgroundColor;
        final int surfaceColor;
        final int accentColor;
        final int textColor;
        final int secondaryTextColor;
        final boolean dark;

        ThemePalette(
                int backgroundColor,
                int surfaceColor,
                int accentColor,
                int textColor,
                int secondaryTextColor,
                boolean dark) {
            this.backgroundColor = backgroundColor;
            this.surfaceColor = surfaceColor;
            this.accentColor = accentColor;
            this.textColor = textColor;
            this.secondaryTextColor = secondaryTextColor;
            this.dark = dark;
        }
    }
}
