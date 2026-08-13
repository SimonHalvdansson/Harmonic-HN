package com.simon.harmonichackernews.utils

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.TextPreferences

object FontUtils {
    fun getRegularTypeface(ctx: Context, font: String?): Typeface? {
        when (TextPreferences.sanitizeFont(font)) {
            "productsans" -> return ResourcesCompat.getFont(ctx, R.font.product_sans)
            "googlesansflexrounded" -> return ResourcesCompat.getFont(
                ctx,
                R.font.google_sans_flex_rounded
            )

            "googlesans" -> return ResourcesCompat.getFont(ctx, R.font.google_sans)
            "devicedefault" -> return Typeface.create("sans-serif", Typeface.NORMAL)
            "verdana" -> return ResourcesCompat.getFont(ctx, R.font.verdana)
            "jetbrainsmono" -> return ResourcesCompat.getFont(ctx, R.font.jetbrains_mono)
            "googlesanscode" -> return ResourcesCompat.getFont(ctx, R.font.google_sans_code)
            "georgia" -> return ResourcesCompat.getFont(ctx, R.font.georgia)
            "robotoslab" -> return ResourcesCompat.getFont(ctx, R.font.roboto_slab)
        }
        return ResourcesCompat.getFont(ctx, R.font.google_sans_flex_rounded)
    }

    fun getBoldTypeface(ctx: Context, font: String?): Typeface? {
        when (TextPreferences.sanitizeFont(font)) {
            "productsans" -> return ResourcesCompat.getFont(ctx, R.font.product_sans_bold)
            "googlesansflexrounded" -> return ResourcesCompat.getFont(
                ctx,
                R.font.google_sans_flex_rounded_bold
            )

            "googlesans" -> return ResourcesCompat.getFont(ctx, R.font.google_sans_bold)
            "devicedefault" -> return Typeface.create("sans-serif", Typeface.BOLD)
            "verdana" -> return ResourcesCompat.getFont(ctx, R.font.verdana_bold)
            "jetbrainsmono" -> return ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_bold)
            "googlesanscode" -> return ResourcesCompat.getFont(ctx, R.font.google_sans_code_bold)
            "georgia" -> return ResourcesCompat.getFont(ctx, R.font.georgia_bold)
            "robotoslab" -> return ResourcesCompat.getFont(ctx, R.font.roboto_slab_bold)
        }
        return ResourcesCompat.getFont(ctx, R.font.google_sans_flex_rounded_bold)
    }

}
