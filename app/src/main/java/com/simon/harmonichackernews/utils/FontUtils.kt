package com.simon.harmonichackernews.utils

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.simon.harmonichackernews.R
import kotlin.math.max

object FontUtils {
    private val GOOGLE_SANS_FLEX_ROUNDED_SIZE_ADJUSTMENT = -0.5f
    private const val EXTRA_COMPACT_STORIES_DROPDOWN_SELECTED_SCALE = 0.8f
    private val STORY_TITLE_SIZES = FontSizes(17.5f, 16f, 17.5f, 18f, 15f, 16f, 16f, 17f, 17.5f)
    private val STORY_META_SIZES = FontSizes(13f, 13f, 13f, 13f, 12f, 12f, 12f, 13f, 13f)
    private val STORY_COMMENT_COUNT_SIZES = FontSizes(14f, 13.5f, 14f, 13f, 13f, 14f, 14f, 14f, 14f)
    private val STORIES_DROPDOWN_SELECTED_SIZES =
        FontSizes(36f, 34f, 36f, 36f, 33f, 34f, 34f, 35f, 35f)
    private val COMPACT_STORIES_DROPDOWN_SELECTED_SIZES = FontSizes(
        32.5f, 30.5f, 32.5f, 32.5f, 29.5f, 27.5f, 27.5f, 31.5f, 31.5f
    )
    private val STORIES_DROPDOWN_ITEM_SIZES = FontSizes(18f, 17f, 18f, 18f, 17f, 17f, 17f, 18f, 18f)
    private val COMMENTS_HEADER_META_SIZES =
        FontSizes(14f, 13.5f, 14f, 13f, 13f, 13f, 13f, 13f, 13f)
    private val COMMENTS_HEADER_TITLE_SIZES = FontSizes(27f, 26f, 27f, 26f, 23f, 26f, 26f, 24f, 26f)
    private val COMMENT_TEXT_SIZES = FontSizes(
        15f,
        14f,
        15f,
        15f,
        14f,
        14f,
        14f,
        15f,
        15f
    )
    private val LINK_SUMMARY_STORY_TITLE_SIZES: FontSizes = STORY_TITLE_SIZES.plus(2.5f)
    private val LINK_SUMMARY_REFERENCE_TITLE_SIZES: FontSizes = STORY_TITLE_SIZES.plus(0.5f)
    private val LINK_SUMMARY_META_SIZES: FontSizes = STORY_META_SIZES
    private val LINK_SUMMARY_BODY_SIZES: FontSizes = COMMENT_TEXT_SIZES
    private val LINK_SUMMARY_ERROR_SIZES: FontSizes = COMMENT_TEXT_SIZES.plus(-1f)
    var activeRegular: Typeface? = null
    var activeBold: Typeface? = null

    var font: String? = null

    fun init(ctx: Context) {
        font = SettingsUtils.getPreferredFont(ctx)

        activeRegular = getRegularTypeface(ctx, font)
        activeBold = getBoldTypeface(ctx, font)
    }

    fun resolveTypography(
        context: Context,
        preferredFont: String?,
        storyTextSize: Float,
        commentTextSize: Float
    ): Typography {
        val resolvedFont = SettingsUtils.sanitizeFont(preferredFont)
        val clampedStoryTextSize = SettingsUtils.clampStoryTextSize(storyTextSize)
        val clampedCommentTextSize = SettingsUtils.clampCommentTextSize(commentTextSize)
        val storyTextDelta =
            clampedStoryTextSize - SettingsUtils.DEFAULT_STORY_TEXT_SIZE
        val storyTextScale =
            clampedStoryTextSize / SettingsUtils.DEFAULT_STORY_TEXT_SIZE
        val commentTextDelta =
            clampedCommentTextSize - SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE
        val adjustExplicitSizes = "googlesansflexrounded" == resolvedFont

        return FontUtils.Typography(
            getRegularTypeface(context, resolvedFont),
            getBoldTypeface(context, resolvedFont),
            adjustExplicitSizes,
            STORY_TITLE_SIZES.get(resolvedFont) + storyTextDelta,
            if (adjustExplicitSizes)
                adjustedGoogleSansFlexRoundedSize(
                    max(12f, clampedStoryTextSize - 3.5f)
                )
            else max(12f, clampedStoryTextSize - 3.5f),
            STORY_META_SIZES.get(resolvedFont) * storyTextScale,
            STORY_COMMENT_COUNT_SIZES.get(resolvedFont) * storyTextScale,
            COMMENT_TEXT_SIZES.get(resolvedFont) + commentTextDelta,
            COMMENTS_HEADER_META_SIZES.get(resolvedFont),
            COMMENTS_HEADER_TITLE_SIZES.get(resolvedFont)
        )
    }

    fun getRegularTypeface(ctx: Context, font: String?): Typeface? {
        when (SettingsUtils.sanitizeFont(font)) {
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
        when (SettingsUtils.sanitizeFont(font)) {
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

    fun setTypefaceForFont(textView: TextView, font: String?, bold: Boolean, size: Float) {
        textView.setTypeface(
            if (bold)
                getBoldTypeface(textView.getContext(), font)
            else
                getRegularTypeface(textView.getContext(), font)
        )
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, getAdjustedTextSize(font, size))
    }

    fun setTypeface(textView: TextView, bold: Boolean, size: Float) {
        setTypeface(
            textView, bold, FontSizes.uniform(size).withGoogleSansFlexRoundedSize(
                adjustedGoogleSansFlexRoundedSize(size)
            )
        )
    }

    fun setStoryTitleTypeface(textView: TextView, storyTextSize: Float) {
        val titleDelta =
            SettingsUtils.clampStoryTextSize(storyTextSize) - SettingsUtils.DEFAULT_STORY_TEXT_SIZE
        setTypeface(textView, true, STORY_TITLE_SIZES.plus(titleDelta))
    }

    fun setStoryMetaTypeface(textView: TextView, storyTextSize: Float) {
        val metaScale =
            SettingsUtils.clampStoryTextSize(storyTextSize) / SettingsUtils.DEFAULT_STORY_TEXT_SIZE
        setTypeface(textView, false, STORY_META_SIZES.times(metaScale))
    }

    fun setStoryCommentCountTypeface(textView: TextView, storyTextSize: Float) {
        val countScale =
            SettingsUtils.clampStoryTextSize(storyTextSize) / SettingsUtils.DEFAULT_STORY_TEXT_SIZE
        setTypeface(textView, true, STORY_COMMENT_COUNT_SIZES.times(countScale))
    }

    fun setCommentTextTypeface(textView: TextView, commentTextSize: Float) {
        setTypeface(textView, false, getCommentTextSizes(commentTextSize))
    }

    fun setCommentTextTypefaceForFont(textView: TextView, font: String?, commentTextSize: Float) {
        textView.setTypeface(getRegularTypeface(textView.getContext(), font))
        textView.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            getCommentTextSizes(commentTextSize).get(font)
        )
    }

    fun getCommentTextSize(commentTextSize: Float): Float {
        return getCommentTextSizes(commentTextSize).get(font)
    }

    fun setStoriesDropdownSelectedTypeface(textView: TextView) {
        var sizes = STORIES_DROPDOWN_SELECTED_SIZES
        if (textView.getResources()
                .getBoolean(R.bool.extra_compact_stories_dropdown_selected_text)
        ) {
            sizes = sizes.times(EXTRA_COMPACT_STORIES_DROPDOWN_SELECTED_SCALE)
        } else if (textView.getResources()
                .getBoolean(R.bool.compact_stories_dropdown_selected_text)
        ) {
            sizes = COMPACT_STORIES_DROPDOWN_SELECTED_SIZES
        }
        setTypeface(textView, true, sizes, TypedValue.COMPLEX_UNIT_DIP)
    }

    fun setStoriesDropdownItemTypeface(textView: TextView) {
        setTypeface(textView, true, STORIES_DROPDOWN_ITEM_SIZES)
    }

    fun setCommentsHeaderMetaTypefaces(vararg textViews: TextView) {
        setMultipleTypefaces(false, COMMENTS_HEADER_META_SIZES, *textViews)
    }

    fun setCommentsHeaderTitleTypeface(textView: TextView) {
        setTypeface(textView, true, COMMENTS_HEADER_TITLE_SIZES)
    }

    fun setLinkSummaryStoryTitleTypeface(textView: TextView) {
        setTypeface(textView, true, LINK_SUMMARY_STORY_TITLE_SIZES)
    }

    fun setLinkSummaryReferenceTitleTypeface(textView: TextView) {
        setTypeface(textView, true, LINK_SUMMARY_REFERENCE_TITLE_SIZES)
    }

    fun setLinkSummaryMetaTypeface(textView: TextView) {
        setTypeface(textView, false, LINK_SUMMARY_META_SIZES)
    }

    fun setLinkSummaryBodyTypeface(textView: TextView) {
        setTypeface(textView, false, LINK_SUMMARY_BODY_SIZES)
    }

    fun setLinkSummaryErrorTypeface(textView: TextView) {
        setTypeface(textView, false, LINK_SUMMARY_ERROR_SIZES)
    }

    private fun setMultipleTypefaces(bold: Boolean, sizes: FontSizes, vararg textViews: TextView) {
        for (textView in textViews) {
            setTypeface(textView, bold, sizes)
        }
    }

    private fun setTypeface(textView: TextView, bold: Boolean, sizes: FontSizes) {
        setTypeface(textView, bold, sizes, TypedValue.COMPLEX_UNIT_SP)
    }

    private fun setTypeface(textView: TextView, bold: Boolean, sizes: FontSizes, unit: Int) {
        val preferredFont = SettingsUtils.getPreferredFont(textView.getContext())
        if (activeRegular == null || activeBold == null || TextUtils.isEmpty(font) || (font != preferredFont)) {
            init(textView.getContext())
        }

        textView.setTypeface(if (bold) activeBold else activeRegular)

        textView.setTextSize(unit, sizes.get(font))
    }

    private fun adjustedGoogleSansFlexRoundedSize(size: Float): Float {
        return size + GOOGLE_SANS_FLEX_ROUNDED_SIZE_ADJUSTMENT
    }

    private fun getCommentTextSizes(commentTextSize: Float): FontSizes {
        val textDelta = (SettingsUtils.clampCommentTextSize(commentTextSize)
                - SettingsUtils.DEFAULT_COMMENT_TEXT_SIZE)
        return COMMENT_TEXT_SIZES.plus(textDelta)
    }

    fun getAdjustedTextSize(font: String?, size: Float): Float {
        if ("googlesansflexrounded" == SettingsUtils.sanitizeFont(font)) {
            return adjustedGoogleSansFlexRoundedSize(size)
        }
        return size
    }

    class Typography internal constructor(
        private val regular: Typeface?,
        private val bold: Typeface?,
        private val adjustExplicitSizes: Boolean,
        private val storyTitleSize: Float,
        private val storySummarySize: Float,
        private val storyMetaSize: Float,
        private val storyCommentCountSize: Float,
        private val commentTextSize: Float,
        private val commentsHeaderMetaSize: Float,
        private val commentsHeaderTitleSize: Float
    ) {
        fun applyStoryTitle(textView: TextView) {
            apply(textView, bold, storyTitleSize)
        }

        fun applyStorySummary(textView: TextView) {
            apply(textView, regular, storySummarySize)
        }

        fun applyStoryMeta(textView: TextView) {
            apply(textView, regular, storyMetaSize)
        }

        fun applyStoryCommentCount(textView: TextView) {
            apply(textView, bold, storyCommentCountSize)
        }

        fun applyCommentText(textView: TextView) {
            apply(textView, regular, commentTextSize)
        }

        fun applyCommentsHeaderMeta(vararg textViews: TextView) {
            for (textView in textViews) {
                apply(textView, regular, commentsHeaderMetaSize)
            }
        }

        fun applyCommentsHeaderTitle(textView: TextView) {
            apply(textView, bold, commentsHeaderTitleSize)
        }

        fun applyRegular(textView: TextView) {
            textView.setTypeface(regular)
        }

        fun applyBold(textView: TextView) {
            textView.setTypeface(bold)
        }

        fun applyRegular(textView: TextView, size: Float) {
            apply(textView, regular, adjustExplicitSize(size))
        }

        fun applyBold(textView: TextView, size: Float) {
            apply(textView, bold, adjustExplicitSize(size))
        }

        private fun adjustExplicitSize(size: Float): Float {
            return if (adjustExplicitSizes)
                adjustedGoogleSansFlexRoundedSize(size)
            else
                size
        }

        companion object {
            private fun apply(textView: TextView, typeface: Typeface?, size: Float) {
                textView.setTypeface(typeface)
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
            }
        }
    }

    private class FontSizes(
        private val productSans: Float,
        private val googleSansFlexRounded: Float,
        private val googleSans: Float,
        private val deviceDefault: Float,
        private val verdana: Float,
        private val jetbrainsMono: Float,
        private val googleSansCode: Float,
        private val georgia: Float,
        private val robotoSlab: Float
    ) {
        fun plus(delta: Float): FontSizes {
            return FontSizes(
                productSans + delta,
                googleSansFlexRounded + delta,
                googleSans + delta,
                deviceDefault + delta,
                verdana + delta,
                jetbrainsMono + delta,
                googleSansCode + delta,
                georgia + delta,
                robotoSlab + delta
            )
        }

        fun times(scale: Float): FontSizes {
            return FontSizes(
                productSans * scale,
                googleSansFlexRounded * scale,
                googleSans * scale,
                deviceDefault * scale,
                verdana * scale,
                jetbrainsMono * scale,
                googleSansCode * scale,
                georgia * scale,
                robotoSlab * scale
            )
        }

        fun withGoogleSansFlexRoundedSize(size: Float): FontSizes {
            return FontSizes(
                productSans,
                size,
                googleSans,
                deviceDefault,
                verdana,
                jetbrainsMono,
                googleSansCode,
                georgia,
                robotoSlab
            )
        }

        fun get(font: String?): Float {
            when (SettingsUtils.sanitizeFont(font)) {
                "googlesansflexrounded" -> return googleSansFlexRounded
                "googlesans" -> return googleSans
                "devicedefault" -> return deviceDefault
                "verdana" -> return verdana
                "jetbrainsmono" -> return jetbrainsMono
                "googlesanscode" -> return googleSansCode
                "georgia" -> return georgia
                "robotoslab" -> return robotoSlab
                "productsans" -> return productSans
                else -> return productSans
            }
        }

        companion object {
            fun uniform(size: Float): FontSizes {
                return FontSizes(size, size, size, size, size, size, size, size, size)
            }
        }
    }
}
