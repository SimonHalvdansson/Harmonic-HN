package com.simon.harmonichackernews.utils;

import android.content.Context;

import com.simon.harmonichackernews.R;

public class CommentDepthIndicatorUtils {

    public static final String MODE_THEME_DEFAULT = "theme_default";
    public static final String MODE_MATERIAL_YOU = "material_you";
    public static final String MODE_COLORS = "colors";
    public static final String MODE_MONOCHROME = "monochrome";

    public static final int COMMENT_DEPTH_COLOR_COUNT = 7;

    private static final int[] COMMENT_DEPTH_COLORS_DARK = new int[]{
            R.color.commentIndentIndicatorColor1,
            R.color.commentIndentIndicatorColor2,
            R.color.commentIndentIndicatorColor3,
            R.color.commentIndentIndicatorColor4,
            R.color.commentIndentIndicatorColor5,
            R.color.commentIndentIndicatorColor6,
            R.color.commentIndentIndicatorColor7
    };

    private static final int[] COMMENT_DEPTH_COLORS_MATERIAL = new int[]{
            R.color.material_you_thread_depth_1,
            R.color.material_you_thread_depth_2,
            R.color.material_you_thread_depth_3,
            R.color.material_you_thread_depth_4,
            R.color.material_you_thread_depth_5,
            R.color.material_you_thread_depth_6,
            R.color.material_you_thread_depth_7
    };

    private static final int[] COMMENT_DEPTH_COLORS_LIGHT = new int[]{
            R.color.commentIndentIndicatorColor1light,
            R.color.commentIndentIndicatorColor2light,
            R.color.commentIndentIndicatorColor3light,
            R.color.commentIndentIndicatorColor4light,
            R.color.commentIndentIndicatorColor5light,
            R.color.commentIndentIndicatorColor6light,
            R.color.commentIndentIndicatorColor7light
    };

    private CommentDepthIndicatorUtils() {
    }

    public static int getColorResource(Context ctx, String mode, String theme, int index) {
        int safeIndex = Math.abs(index) % COMMENT_DEPTH_COLOR_COUNT;
        String safeMode = sanitizeMode(mode);

        if (MODE_MONOCHROME.equals(safeMode)) {
            return R.color.commentIndentIndicatorColorMonochrome;
        }

        if (MODE_MATERIAL_YOU.equals(safeMode)) {
            return COMMENT_DEPTH_COLORS_MATERIAL[safeIndex];
        }

        if (MODE_COLORS.equals(safeMode)) {
            return getStandardColorResource(ctx, theme, safeIndex);
        }

        if (theme != null && theme.startsWith("material")) {
            return COMMENT_DEPTH_COLORS_MATERIAL[safeIndex];
        }
        return getStandardColorResource(ctx, theme, safeIndex);
    }

    public static String sanitizeMode(String mode) {
        if (MODE_MATERIAL_YOU.equals(mode)
                || MODE_COLORS.equals(mode)
                || MODE_MONOCHROME.equals(mode)) {
            return mode;
        }
        return MODE_THEME_DEFAULT;
    }

    public static String getModeLabel(String mode) {
        switch (sanitizeMode(mode)) {
            case MODE_MATERIAL_YOU:
                return "Material You";
            case MODE_COLORS:
                return "Standard";
            case MODE_MONOCHROME:
                return "Monochrome";
            case MODE_THEME_DEFAULT:
            default:
                return "Theme default";
        }
    }

    private static int getStandardColorResource(Context ctx, String theme, int index) {
        return ThemeUtils.isDarkMode(ctx, theme) ? COMMENT_DEPTH_COLORS_DARK[index] : COMMENT_DEPTH_COLORS_LIGHT[index];
    }
}
