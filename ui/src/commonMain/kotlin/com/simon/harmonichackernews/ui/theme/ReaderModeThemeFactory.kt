package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.toArgb
import com.simon.harmonichackernews.presentation.ReaderModeSourceAssembler
import com.simon.harmonichackernews.presentation.ReaderModeTheme

data class ReaderModeFontData(
    val regularBase64: String,
    val boldBase64: String,
) {
    internal val fontFaceCss: String by lazy {
        ReaderModeSourceAssembler.fontFaceCss(
            ReaderModeSourceAssembler.fontDataUrl(regularBase64),
            ReaderModeSourceAssembler.fontDataUrl(boldBase64),
        )
    }
}

/** Converts shared UI tokens and host-loaded font bytes into the common reader-mode protocol. */
object ReaderModeThemeFactory {
    fun create(
        colors: HarmonicColors,
        light: Boolean,
        font: String?,
        fontSizePx: Int,
        fontData: ReaderModeFontData? = null,
    ): ReaderModeTheme = ReaderModeTheme(
        light = light,
        backgroundColor = css(colors.readerModeBackground.toArgb()),
        textColor = css(colors.textPrimary.toArgb()),
        headingColor = css(colors.contentPrimary.toArgb()),
        secondaryTextColor = css(colors.textSecondary.toArgb()),
        linkColor = css(colors.link.toArgb()),
        dividerColor = css(colors.commentDivider.toArgb()),
        codeBackgroundColor = css(colors.surfaceContainerHigh.toArgb()),
        fontFaceCss = fontData?.fontFaceCss.orEmpty(),
        font = font,
        fontSizePx = fontSizePx,
    )

    private fun css(argb: Int): String = ReaderModeSourceAssembler.cssColor(argb)
}
