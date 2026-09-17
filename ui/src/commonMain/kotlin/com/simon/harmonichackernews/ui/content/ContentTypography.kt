package com.simon.harmonichackernews.ui.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font as ComposeFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.georgia_bold
import com.simon.harmonichackernews.resources.georgia_regular
import com.simon.harmonichackernews.resources.google_sans_bold
import com.simon.harmonichackernews.resources.google_sans_code_regular
import com.simon.harmonichackernews.resources.google_sans_flex_rounded_bold
import com.simon.harmonichackernews.resources.google_sans_flex_rounded_regular
import com.simon.harmonichackernews.resources.google_sans_flex_rounded_variable_latin
import com.simon.harmonichackernews.resources.google_sans_regular
import com.simon.harmonichackernews.resources.jetbrains_mono_bold
import com.simon.harmonichackernews.resources.jetbrains_mono_regular
import com.simon.harmonichackernews.resources.product_sans_bold
import com.simon.harmonichackernews.resources.product_sans_regular
import com.simon.harmonichackernews.resources.roboto_slab_bold
import com.simon.harmonichackernews.resources.roboto_slab_regular
import com.simon.harmonichackernews.resources.verdana_bold
import com.simon.harmonichackernews.resources.verdana_regular
import com.simon.harmonichackernews.settings.TextPreferences
import org.jetbrains.compose.resources.Font

private val CondensedStoryMetaVariationSettings = FontVariation.Settings(
    FontVariation.width(90f),
    FontVariation.weight(FontWeight.Normal.weight),
)

data class ContentTypography(
    val family: FontFamily,
    val storyMetaFamily: FontFamily,
    val storyTitleSize: Float,
    val storySummarySize: Float,
    val storyMetaSize: Float,
    val storyCommentCountSize: Float,
    val storiesDropdownSelectedSize: Float,
    val storiesDropdownCompactSelectedSize: Float,
    val storiesDropdownItemSize: Float,
    val commentTextSize: Float,
    val commentsHeaderMetaSize: Float,
    val commentsHeaderTitleSize: Float,
    val referenceMarkerSize: Float,
    val referenceLabelSize: Float,
)

@Composable
fun rememberContentTypography(
    preferredFont: String,
    storyTextSize: Float = TextPreferences.DEFAULT_STORY_TEXT_SIZE,
    commentTextSize: Float = TextPreferences.DEFAULT_COMMENT_TEXT_SIZE,
): ContentTypography {
    val font = TextPreferences.sanitizeFont(preferredFont)
    val clampedStorySize = TextPreferences.clampStoryTextSize(storyTextSize)
    val clampedCommentSize = TextPreferences.clampCommentTextSize(commentTextSize)
    val family = contentFontFamily(font)
    val storyMetaFamily = storyMetaFontFamily(font, family)
    return remember(font, family, storyMetaFamily, clampedStorySize, clampedCommentSize) {
        val metrics = FontMetrics.forFont(font)
        val storyDelta = clampedStorySize - TextPreferences.DEFAULT_STORY_TEXT_SIZE
        val storyScale = clampedStorySize / TextPreferences.DEFAULT_STORY_TEXT_SIZE
        val commentDelta = clampedCommentSize - TextPreferences.DEFAULT_COMMENT_TEXT_SIZE
        val explicitAdjustment = if (font == "googlesansflexrounded") -0.5f else 0f

        ContentTypography(
            family = family,
            storyMetaFamily = storyMetaFamily,
            storyTitleSize = metrics.storyTitle + storyDelta,
            storySummarySize = maxOf(12f, clampedStorySize - 3.5f),
            storyMetaSize = metrics.storyMeta * storyScale,
            storyCommentCountSize = metrics.storyCommentCount * storyScale,
            storiesDropdownSelectedSize = metrics.storiesDropdownSelected,
            storiesDropdownCompactSelectedSize = metrics.storiesDropdownCompactSelected,
            storiesDropdownItemSize = metrics.storiesDropdownItem,
            commentTextSize = metrics.commentText + commentDelta,
            commentsHeaderMetaSize = metrics.commentsHeaderMeta,
            commentsHeaderTitleSize = metrics.commentsHeaderTitle,
            referenceMarkerSize = 13f + explicitAdjustment,
            referenceLabelSize = maxOf(12f, clampedCommentSize - 2f) + explicitAdjustment,
        )
    }
}

@Composable
private fun storyMetaFontFamily(font: String, fallback: FontFamily): FontFamily {
    if (font != "googlesansflexrounded") return fallback

    val condensed = Font(
        resource = Res.font.google_sans_flex_rounded_variable_latin,
        weight = FontWeight.Normal,
        variationSettings = CondensedStoryMetaVariationSettings,
    )
    return remember(font) { FontFamily(condensed) }
}

internal data class FontMetrics(
    val storyTitle: Float,
    val storyMeta: Float,
    val storyCommentCount: Float,
    val storiesDropdownSelected: Float,
    val storiesDropdownCompactSelected: Float,
    val storiesDropdownItem: Float,
    val commentText: Float,
    val commentsHeaderMeta: Float,
    val commentsHeaderTitle: Float,
) {
    companion object {
        fun forFont(font: String): FontMetrics = when (font) {
            "productsans" -> FontMetrics(17.5f, 13f, 14f, 36f, 32.5f, 18f, 15f, 14f, 27f)
            "googlesans" -> FontMetrics(17.5f, 13f, 14f, 36f, 32.5f, 18f, 15f, 14f, 27f)
            "devicedefault" -> FontMetrics(18f, 13f, 13f, 36f, 32.5f, 18f, 15f, 13f, 26f)
            "verdana" -> FontMetrics(15f, 12f, 13f, 33f, 29.5f, 17f, 14f, 13f, 23f)
            "jetbrainsmono" -> FontMetrics(16f, 12f, 14f, 34f, 27.5f, 17f, 14f, 13f, 26f)
            "googlesanscode" -> FontMetrics(16f, 12f, 14f, 34f, 27.5f, 17f, 14f, 13f, 26f)
            "georgia" -> FontMetrics(17f, 13f, 14f, 35f, 31.5f, 18f, 15f, 13f, 24f)
            "robotoslab" -> FontMetrics(17.5f, 13f, 14f, 35f, 31.5f, 18f, 15f, 13f, 26f)
            else -> FontMetrics(16f, 13f, 13.5f, 34f, 30.5f, 17f, 13.75f, 13.5f, 26f)
        }
    }
}

@Composable
private fun contentFontFamily(font: String): FontFamily = when (font) {
    "productsans" -> rememberFontFamily(
        font,
        Font(Res.font.product_sans_regular, FontWeight.Normal),
        Font(Res.font.product_sans_bold, FontWeight.Bold),
    )
    "googlesans" -> rememberFontFamily(
        font,
        Font(Res.font.google_sans_regular, FontWeight.Normal),
        Font(Res.font.google_sans_bold, FontWeight.Bold),
    )
    "devicedefault" -> FontFamily.SansSerif
    "verdana" -> rememberFontFamily(
        font,
        Font(Res.font.verdana_regular, FontWeight.Normal),
        Font(Res.font.verdana_bold, FontWeight.Bold),
    )
    "jetbrainsmono" -> rememberFontFamily(
        font,
        Font(Res.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(Res.font.jetbrains_mono_bold, FontWeight.Bold),
    )
    "googlesanscode" -> rememberFontFamily(
        font,
        Font(Res.font.google_sans_code_regular, FontWeight.Normal),
        Font(Res.font.google_sans_code_regular, FontWeight.Bold),
    )
    "georgia" -> rememberFontFamily(
        font,
        Font(Res.font.georgia_regular, FontWeight.Normal),
        Font(Res.font.georgia_bold, FontWeight.Bold),
    )
    "robotoslab" -> rememberFontFamily(
        font,
        Font(Res.font.roboto_slab_regular, FontWeight.Normal),
        Font(Res.font.roboto_slab_bold, FontWeight.Bold),
    )
    else -> rememberFontFamily(
        font,
        Font(Res.font.google_sans_flex_rounded_regular, FontWeight.Normal),
        Font(Res.font.google_sans_flex_rounded_bold, FontWeight.Bold),
    )
}

@Composable
private fun rememberFontFamily(
    fontKey: String,
    regular: ComposeFont,
    bold: ComposeFont,
): FontFamily = remember(fontKey) { FontFamily(regular, bold) }
