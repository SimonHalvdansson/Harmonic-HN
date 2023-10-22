package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

import com.simon.harmonichackernews.R;

public class FontUtils {

    public static Typeface activeRegular;
    public static Typeface activeBold;

    public static String font;

    public static void init(Context ctx) {
        font = SettingsUtils.getPreferredFont(ctx);

        switch (font) {
            case "productsans":
                activeRegular = ResourcesCompat.getFont(ctx, R.font.product_sans);
                activeBold = ResourcesCompat.getFont(ctx, R.font.product_sans_bold);
                break;
            case "devicedefault":
                activeRegular = Typeface.create("sans-serif", Typeface.NORMAL);
                activeBold = Typeface.create("sans-serif", Typeface.BOLD);
                break;
            case "verdana":
                activeRegular = ResourcesCompat.getFont(ctx, R.font.verdana);
                activeBold = ResourcesCompat.getFont(ctx, R.font.verdana_bold);
                break;
            case "jetbrainsmono":
                activeRegular = ResourcesCompat.getFont(ctx, R.font.jetbrains_mono);
                activeBold = ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_bold);
                break;
            case "georgia":
                activeRegular = ResourcesCompat.getFont(ctx, R.font.georgia);
                activeBold = ResourcesCompat.getFont(ctx, R.font.georgia_bold);
                break;
            case "robotoslab":
                activeRegular = ResourcesCompat.getFont(ctx, R.font.roboto_slab);
                activeBold = ResourcesCompat.getFont(ctx, R.font.roboto_slab_bold);
                break;
        }
    }

    public static void setTypeface(TextView textView, boolean bold, float size) {
        setTypeface(textView, bold, size, size, size, size, size, size);
    }

    public static void setMultipleTypefaces(boolean bold, float prodSize, float sansSize, float verdanaSize, float jetbrainsmonoSize, float georgiaSize, float robotoSlabSize, TextView... textViews) {
        for (TextView textView : textViews) {
            FontUtils.setTypeface(textView, bold, prodSize, sansSize, verdanaSize, jetbrainsmonoSize, georgiaSize, robotoSlabSize);
        }
    }

    public static void setTypeface(TextView textView, boolean bold, float prodSize, float sansSize, float verdanaSize, float jetbrainsmonoSize, float georgiaSize, float robotoSlabSize) {
        if (activeRegular == null) {
            init(textView.getContext());
        }

        textView.setTypeface(bold ? activeBold : activeRegular);

        switch (font) {
            case "productsans":
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, prodSize);
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

}
