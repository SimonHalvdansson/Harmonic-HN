package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.simon.harmonichackernews.R;

public class FontUtils {

    private static final float GOOGLE_SANS_FLEX_ROUNDED_SIZE_ADJUSTMENT = -0.5f;

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
        setTypeface(textView, bold, size, adjustedGoogleSansFlexRoundedSize(size), size, size, size, size, size);
    }

    public static void setMultipleTypefaces(boolean bold, float prodSize, float googleSansFlexRoundedSize, float sansSize, float verdanaSize, float jetbrainsmonoSize, float georgiaSize, float robotoSlabSize, TextView... textViews) {
        for (TextView textView : textViews) {
            FontUtils.setTypeface(textView, bold, prodSize, googleSansFlexRoundedSize, sansSize, verdanaSize, jetbrainsmonoSize, georgiaSize, robotoSlabSize);
        }
    }

    public static void setTypeface(TextView textView, boolean bold, float prodSize, float googleSansFlexRoundedSize, float sansSize, float verdanaSize, float jetbrainsmonoSize, float georgiaSize, float robotoSlabSize) {
        if (activeRegular == null) {
            init(textView.getContext());
        }

        textView.setTypeface(bold ? activeBold : activeRegular);

        switch (font) {
            case "productsans":
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, prodSize);
                break;
            case "googlesansflexrounded":
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, googleSansFlexRoundedSize);
                break;
            case "devicedefault":
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sansSize);
                break;
            case "verdana":
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, verdanaSize);
                break;
            case "jetbrainsmono":
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, jetbrainsmonoSize);
                break;
            case "georgia":
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, georgiaSize);
                break;
            case "robotoslab":
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, robotoSlabSize);
                break;
        }
    }

    private static float adjustedGoogleSansFlexRoundedSize(float size) {
        return size + GOOGLE_SANS_FLEX_ROUNDED_SIZE_ADJUSTMENT;
    }

    public static float getAdjustedTextSize(String font, float size) {
        if ("googlesansflexrounded".equals(SettingsUtils.sanitizeFont(font))) {
            return adjustedGoogleSansFlexRoundedSize(size);
        }
        return size;
    }

}
