package com.simon.harmonichackernews.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.presentation.ReaderModeSourceAssembler
import com.simon.harmonichackernews.presentation.ReaderModeTheme

data class ReaderModeFontData(
    val regularBase64: String,
    val boldBase64: String,
) {
    val fontFaceCss: String by lazy {
        ReaderModeSourceAssembler.fontFaceCss(
            ReaderModeSourceAssembler.fontDataUrl(regularBase64),
            ReaderModeSourceAssembler.fontDataUrl(boldBase64),
        )
    }
}

/** Converts shared UI tokens and host-loaded font bytes into the common reader-mode protocol. */
object ReaderModeThemeFactory {
    fun create(
        colors: ColorScheme,
        light: Boolean,
        font: String?,
        fontSizePx: Int,
        fontData: ReaderModeFontData? = null,
        lineHeight: Double = 1.68,
    ): ReaderModeTheme = ReaderModeTheme(
        light = light,
        backgroundColor = css(colors.pageBackground.toArgb()),
        textColor = css(colors.onSurface.toArgb()),
        headingColor = css(colors.onSurface.toArgb()),
        secondaryTextColor = css(colors.onSurfaceVariant.toArgb()),
        linkColor = css(colors.primary.toArgb()),
        dividerColor = css(colors.outlineVariant.toArgb()),
        codeBackgroundColor = css(colors.surfaceContainerHigh.toArgb()),
        fontFaceCss = fontData?.fontFaceCss.orEmpty(),
        font = font,
        fontSizePx = fontSizePx,
        lineHeight = lineHeight,
    )

    private fun css(argb: Int): String = ReaderModeSourceAssembler.cssColor(argb)
}
