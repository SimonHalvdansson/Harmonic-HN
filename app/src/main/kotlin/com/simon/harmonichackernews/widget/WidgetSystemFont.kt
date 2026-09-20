package com.simon.harmonichackernews.widget

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.text.TextRunShaper
import android.os.Build
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

internal const val ROUNDED_WIDGET_FONT_FAMILY = "variable-body-medium-emphasized"

/** Prefers the installed rounded Flex preset, then Flex and the public device headline style. */
internal fun widgetHeadlineFontFamily(context: Context): String? {
    // This named system preset retains ROND=100 through Glance's TypefaceSpan. Verify the actual
    // shaped font: OEMs may provide a different font under the same Material typography name.
    if (isRoundedGoogleSansFlex(ROUNDED_WIDGET_FONT_FAMILY)) {
        distinctWidgetFontFamily(ROUNDED_WIDGET_FONT_FAMILY)?.let { return it }
    }
    // Pixel's public headline appearance can still name older Google Sans even when Flex is
    // installed. Probe the installed family first; create() safely falls back when it is absent.
    distinctWidgetFontFamily("google-sans-flex")?.let { return it }
    // Headline references config_headlineFontFamily. The older public action-bar title style
    // references config_headlineFontFamilyMedium, like SystemUI's private notification title.
    val appearance = if (Build.VERSION.SDK_INT >= 33) android.R.style.TextAppearance_DeviceDefault_Headline
        else android.R.style.TextAppearance_DeviceDefault_Widget_ActionBar_Title
    val attributes = context.obtainStyledAttributes(appearance, intArrayOf(android.R.attr.fontFamily))
    val family = try { attributes.getString(0) } finally { attributes.recycle() }
    return distinctWidgetFontFamily(family)
}

internal fun isRoundedGoogleSansFlex(family: String): Boolean {
    if (Build.VERSION.SDK_INT < 31) return false
    return listOf(Typeface.NORMAL, Typeface.BOLD).all { style ->
        val paint = Paint().apply { typeface = Typeface.create(family, style); textSize = 32f }
        val glyphs = TextRunShaper.shapeTextRun("Aa", 0, 2, 0, 2, 0f, 0f, false, paint)
        if (glyphs.glyphCount() == 0 || glyphs.getFont(0).file?.name?.startsWith("GoogleSansFlex") != true) return@all false
        // Shaped Font.getAxes() can be empty even for a variable system preset. Compare the actual
        // outlines with explicit rounded/unrounded Flex instances instead. Body-medium-emphasized
        // uses 14pt optical size and weights 500/800 for normal/bold on Pixel.
        fun reference(roundness: Int) = Paint().apply {
            typeface = Typeface.create("google-sans-flex", Typeface.NORMAL)
            textSize = 32f
            fontVariationSettings = "'wght' ${if (style == Typeface.BOLD) 800 else 500}, 'wdth' 100, 'GRAD' 0, 'opsz' 14, 'slnt' 0, 'ROND' $roundness"
        }.widgetFontOutline()
        val rounded = reference(100)
        !rounded.contentEquals(reference(0)) && rounded.contentEquals(paint.widgetFontOutline())
    }
}

private fun Paint.widgetFontOutline(): FloatArray {
    val path = Path()
    val sample = "Hamburgefontsiv 0123456789 AaGgQq"
    getTextPath(sample, 0, sample.length, 0f, 0f, path)
    return path.approximate(0.01f)
}

/** Unknown names silently fall back to sans-serif; aliases must not create a redundant choice. */
internal fun distinctWidgetFontFamily(family: String?): String? {
    val name = family?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (name in setOf("sans-serif", "sans-serif-medium", "sans-serif-light", "sans-serif-thin", "sans-serif-black")) return null
    val candidate = Typeface.create(name, Typeface.NORMAL)
    if (candidate == Typeface.SANS_SERIF) return null

    // Typeface equality compares native identities, so two aliases can compare unequal while
    // rendering identically. Compare outlines at the same weights, not just family-name strings
    // or text width. This also works before getSystemFontFamilyName became public in API 34.
    val sameOutlines = listOf(Typeface.NORMAL, Typeface.BOLD).all { style ->
        fun outlines(base: Typeface): FloatArray {
            val face = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, if (style == Typeface.BOLD) 700 else 400, false)
                else Typeface.create(base, style)
            val paint = Paint().apply { typeface = face; textSize = 32f }
            val path = Path()
            val sample = "Hamburgefontsiv 0123456789 AaGgQq ЖΩ中"
            paint.getTextPath(sample, 0, sample.length, 0f, 0f, path)
            return path.approximate(0.01f)
        }
        outlines(candidate).contentEquals(outlines(Typeface.SANS_SERIF))
    }
    if (sameOutlines) return null
    // Report the resolved installed family, including when the requested name was an alias.
    return if (Build.VERSION.SDK_INT >= 34) candidate.systemFontFamilyName ?: name else name
}

internal fun widgetFontDisplayName(family: String): String = when (family) {
    ROUNDED_WIDGET_FONT_FAMILY -> "Google Sans Flex Rounded"
    "google-sans-flex" -> "Google Sans Flex"
    "google-sans", "google-sans-medium" -> "Google Sans"
    else -> family
}

/** Resolve each weight by name, just like the launcher's TextViews, instead of fixing one face. */
internal fun widgetPreviewFontFamily(family: String): FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map {
        Font(DeviceFontFamilyName(family), weight = it)
    },
)
