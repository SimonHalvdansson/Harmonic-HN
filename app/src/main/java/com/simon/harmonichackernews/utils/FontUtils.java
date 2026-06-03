package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.simon.harmonichackernews.R;

public class FontUtils {

    private static final float GOOGLE_SANS_FLEX_ROUNDED_SIZE_ADJUSTMENT = -0.5f;
    private static final FontSizes STORY_TITLE_SIZES = new FontSizes(17.5f, 16, 18, 15, 16, 16, 17, 17.5f);
    private static final FontSizes STORY_META_SIZES = new FontSizes(13, 12.5f, 13, 12, 12, 12, 13, 13);
    private static final FontSizes STORY_COMMENT_COUNT_SIZES = new FontSizes(14, 13.5f, 13, 13, 14, 14, 14, 14);
    private static final FontSizes STORIES_DROPDOWN_SELECTED_SIZES = new FontSizes(36, 34, 36, 33, 34, 34, 35, 35);
    private static final FontSizes COMMENTS_HEADER_META_SIZES = new FontSizes(14, 13.5f, 13, 13, 13, 13, 13, 13);
    private static final FontSizes COMMENTS_HEADER_TITLE_SIZES = new FontSizes(27, 26, 26, 23, 26, 26, 24, 26);
    private static final FontSizes COMMENT_TEXT_SIZES = new FontSizes(
            15,
            14,
            15,
            14,
            14,
            14,
            15,
            15);

    public static Typeface activeRegular;
    public static Typeface activeBold;

    public static String font;

    public static void init(Context ctx) {
        font = SettingsUtils.getPreferredFont(ctx);

        activeRegular = getRegularTypeface(ctx, font);
        activeBold = getBoldTypeface(ctx, font);
    }

    public static Typeface getRegularTypeface(Context ctx, String font) {
        switch (SettingsUtils.sanitizeFont(font)) {
            case "productsans":
                return ResourcesCompat.getFont(ctx, R.font.product_sans);
            case "googlesansflexrounded":
                return ResourcesCompat.getFont(ctx, R.font.google_sans_flex_rounded);
            case "devicedefault":
                return Typeface.create("sans-serif", Typeface.NORMAL);
            case "verdana":
                return ResourcesCompat.getFont(ctx, R.font.verdana);
            case "jetbrainsmono":
                return ResourcesCompat.getFont(ctx, R.font.jetbrains_mono);
            case "googlesanscode":
                return ResourcesCompat.getFont(ctx, R.font.google_sans_code);
            case "georgia":
                return ResourcesCompat.getFont(ctx, R.font.georgia);
            case "robotoslab":
                return ResourcesCompat.getFont(ctx, R.font.roboto_slab);
        }
        return ResourcesCompat.getFont(ctx, R.font.product_sans);
    }

    public static Typeface getBoldTypeface(Context ctx, String font) {
        switch (SettingsUtils.sanitizeFont(font)) {
            case "productsans":
                return ResourcesCompat.getFont(ctx, R.font.product_sans_bold);
            case "googlesansflexrounded":
                return ResourcesCompat.getFont(ctx, R.font.google_sans_flex_rounded_bold);
            case "devicedefault":
                return Typeface.create("sans-serif", Typeface.BOLD);
            case "verdana":
                return ResourcesCompat.getFont(ctx, R.font.verdana_bold);
            case "jetbrainsmono":
                return ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_bold);
            case "googlesanscode":
                return ResourcesCompat.getFont(ctx, R.font.google_sans_code_bold);
            case "georgia":
                return ResourcesCompat.getFont(ctx, R.font.georgia_bold);
            case "robotoslab":
                return ResourcesCompat.getFont(ctx, R.font.roboto_slab_bold);
        }
        return ResourcesCompat.getFont(ctx, R.font.product_sans_bold);
    }

    public static void setTypefaceForFont(TextView textView, String font, boolean bold, float size) {
        textView.setTypeface(bold
                ? getBoldTypeface(textView.getContext(), font)
                : getRegularTypeface(textView.getContext(), font));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, getAdjustedTextSize(font, size));
    }

    public static void setTypeface(TextView textView, boolean bold, float size) {
        setTypeface(textView, bold, FontSizes.uniform(size).withGoogleSansFlexRoundedSize(adjustedGoogleSansFlexRoundedSize(size)));
    }

    public static void setStoryTitleTypeface(TextView textView, float storyTextSize) {
        float titleDelta = SettingsUtils.clampStoryTextSize(storyTextSize) - SettingsUtils.DEFAULT_STORY_TEXT_SIZE;
        setTypeface(textView, true, STORY_TITLE_SIZES.plus(titleDelta));
    }

    public static void setStoryMetaTypeface(TextView textView, float storyTextSize) {
        float metaScale = SettingsUtils.clampStoryTextSize(storyTextSize) / SettingsUtils.DEFAULT_STORY_TEXT_SIZE;
        setTypeface(textView, false, STORY_META_SIZES.times(metaScale));
    }

    public static void setStoryCommentCountTypeface(TextView textView) {
        setTypeface(textView, true, STORY_COMMENT_COUNT_SIZES);
    }

    public static void setCommentTextTypeface(TextView textView, float commentTextSize) {
        setTypeface(textView, false, getCommentTextSizes(commentTextSize));
    }

    public static float getCommentTextSize(float commentTextSize) {
        return getCommentTextSizes(commentTextSize).get(font);
    }

    public static void setStoriesDropdownSelectedTypeface(TextView textView) {
        setTypeface(textView, true, STORIES_DROPDOWN_SELECTED_SIZES, TypedValue.COMPLEX_UNIT_DIP);
    }

    public static void setCommentsHeaderMetaTypefaces(TextView... textViews) {
        setMultipleTypefaces(false, COMMENTS_HEADER_META_SIZES, textViews);
    }

    public static void setCommentsHeaderTitleTypeface(TextView textView) {
        setTypeface(textView, true, COMMENTS_HEADER_TITLE_SIZES);
    }

    private static void setMultipleTypefaces(boolean bold, FontSizes sizes, TextView... textViews) {
        for (TextView textView : textViews) {
            FontUtils.setTypeface(textView, bold, sizes);
        }
    }

    private static void setTypeface(TextView textView, boolean bold, FontSizes sizes) {
        setTypeface(textView, bold, sizes, TypedValue.COMPLEX_UNIT_SP);
    }

    private static void setTypeface(TextView textView, boolean bold, FontSizes sizes, int unit) {
        if (activeRegular == null) {
            init(textView.getContext());
        }

        textView.setTypeface(bold ? activeBold : activeRegular);

        textView.setTextSize(unit, sizes.get(font));
    }

    private static float adjustedGoogleSansFlexRoundedSize(float size) {
        return size + GOOGLE_SANS_FLEX_ROUNDED_SIZE_ADJUSTMENT;
    }

    private static FontSizes getCommentTextSizes(float commentTextSize) {
        float textDelta = SettingsUtils.clampCommentTextSize(commentTextSize)
                - SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE;
        return COMMENT_TEXT_SIZES.plus(textDelta);
    }

    public static float getAdjustedTextSize(String font, float size) {
        if ("googlesansflexrounded".equals(SettingsUtils.sanitizeFont(font))) {
            return adjustedGoogleSansFlexRoundedSize(size);
        }
        return size;
    }

    private static class FontSizes {

        private final float productSans;
        private final float googleSansFlexRounded;
        private final float deviceDefault;
        private final float verdana;
        private final float jetbrainsMono;
        private final float googleSansCode;
        private final float georgia;
        private final float robotoSlab;

        FontSizes(float productSans,
                  float googleSansFlexRounded,
                  float deviceDefault,
                  float verdana,
                  float jetbrainsMono,
                  float googleSansCode,
                  float georgia,
                  float robotoSlab) {
            this.productSans = productSans;
            this.googleSansFlexRounded = googleSansFlexRounded;
            this.deviceDefault = deviceDefault;
            this.verdana = verdana;
            this.jetbrainsMono = jetbrainsMono;
            this.googleSansCode = googleSansCode;
            this.georgia = georgia;
            this.robotoSlab = robotoSlab;
        }

        static FontSizes uniform(float size) {
            return new FontSizes(size, size, size, size, size, size, size, size);
        }

        FontSizes plus(float delta) {
            return new FontSizes(
                    productSans + delta,
                    googleSansFlexRounded + delta,
                    deviceDefault + delta,
                    verdana + delta,
                    jetbrainsMono + delta,
                    googleSansCode + delta,
                    georgia + delta,
                    robotoSlab + delta);
        }

        FontSizes times(float scale) {
            return new FontSizes(
                    productSans * scale,
                    googleSansFlexRounded * scale,
                    deviceDefault * scale,
                    verdana * scale,
                    jetbrainsMono * scale,
                    googleSansCode * scale,
                    georgia * scale,
                    robotoSlab * scale);
        }

        FontSizes withGoogleSansFlexRoundedSize(float size) {
            return new FontSizes(
                    productSans,
                    size,
                    deviceDefault,
                    verdana,
                    jetbrainsMono,
                    googleSansCode,
                    georgia,
                    robotoSlab);
        }

        float get(String font) {
            switch (SettingsUtils.sanitizeFont(font)) {
                case "googlesansflexrounded":
                    return googleSansFlexRounded;
                case "devicedefault":
                    return deviceDefault;
                case "verdana":
                    return verdana;
                case "jetbrainsmono":
                    return jetbrainsMono;
                case "googlesanscode":
                    return googleSansCode;
                case "georgia":
                    return georgia;
                case "robotoslab":
                    return robotoSlab;
                case "productsans":
                default:
                    return productSans;
            }
        }
    }

}
