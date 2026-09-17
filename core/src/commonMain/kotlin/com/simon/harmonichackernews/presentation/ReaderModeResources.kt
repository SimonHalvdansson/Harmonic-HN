package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.AppFont

/** Pure assembly helpers; hosts only load bytes/assets and resolve native theme colors. */
object ReaderModeSourceAssembler {
    fun script(readabilitySource: String, readerModeSource: String): String = buildString {
        append(readabilitySource.trimEnd()).append('\n')
        append(readerModeSource.trimEnd()).append('\n')
    }

    fun cssColor(argb: Int): String = "#" + (argb and 0x00ff_ffff)
        .toString(16)
        .uppercase()
        .padStart(6, '0')

    fun fontDataUrl(base64: String): String = "data:font/ttf;base64,$base64"

    fun fontFaceCss(regularDataUrl: String, boldDataUrl: String): String {
        if (regularDataUrl.isBlank() || boldDataUrl.isBlank()) return ""
        return "@font-face{font-family:'HarmonicReaderFont';font-style:normal;" +
            "font-weight:400;src:url($regularDataUrl) format('truetype');}" +
            "@font-face{font-family:'HarmonicReaderFont';font-style:normal;" +
            "font-weight:700;src:url($boldDataUrl) format('truetype');}"
    }
}

enum class ReaderModeFontResource {
    PRODUCT_SANS_REGULAR,
    PRODUCT_SANS_BOLD,
    GOOGLE_SANS_FLEX_ROUNDED_REGULAR,
    GOOGLE_SANS_FLEX_ROUNDED_BOLD,
    GOOGLE_SANS_REGULAR,
    GOOGLE_SANS_BOLD,
    VERDANA_REGULAR,
    VERDANA_BOLD,
    ROBOTO_SLAB_REGULAR,
    ROBOTO_SLAB_BOLD,
    GOOGLE_SANS_CODE_REGULAR,
    JETBRAINS_MONO_REGULAR,
    JETBRAINS_MONO_BOLD,
    GEORGIA_REGULAR,
    GEORGIA_BOLD,
}

data class ReaderModeFontResources(
    val regular: ReaderModeFontResource,
    val bold: ReaderModeFontResource,
)

/** Canonical reader-font pairing; native hosts only load bytes for the selected resources. */
object ReaderModeFontResourcePolicy {
    fun resolve(storedFont: String?): ReaderModeFontResources? = when (
        AppFont.fromStored(storedFont)
    ) {
        AppFont.PRODUCT_SANS -> ReaderModeFontResources(
            ReaderModeFontResource.PRODUCT_SANS_REGULAR,
            ReaderModeFontResource.PRODUCT_SANS_BOLD,
        )
        AppFont.GOOGLE_SANS_FLEX_ROUNDED -> ReaderModeFontResources(
            ReaderModeFontResource.GOOGLE_SANS_FLEX_ROUNDED_REGULAR,
            ReaderModeFontResource.GOOGLE_SANS_FLEX_ROUNDED_BOLD,
        )
        AppFont.GOOGLE_SANS -> ReaderModeFontResources(
            ReaderModeFontResource.GOOGLE_SANS_REGULAR,
            ReaderModeFontResource.GOOGLE_SANS_BOLD,
        )
        AppFont.VERDANA -> ReaderModeFontResources(
            ReaderModeFontResource.VERDANA_REGULAR,
            ReaderModeFontResource.VERDANA_BOLD,
        )
        AppFont.ROBOTO_SLAB -> ReaderModeFontResources(
            ReaderModeFontResource.ROBOTO_SLAB_REGULAR,
            ReaderModeFontResource.ROBOTO_SLAB_BOLD,
        )
        AppFont.GOOGLE_SANS_CODE -> ReaderModeFontResources(
            ReaderModeFontResource.GOOGLE_SANS_CODE_REGULAR,
            ReaderModeFontResource.GOOGLE_SANS_CODE_REGULAR,
        )
        AppFont.JETBRAINS_MONO -> ReaderModeFontResources(
            ReaderModeFontResource.JETBRAINS_MONO_REGULAR,
            ReaderModeFontResource.JETBRAINS_MONO_BOLD,
        )
        AppFont.GEORGIA -> ReaderModeFontResources(
            ReaderModeFontResource.GEORGIA_REGULAR,
            ReaderModeFontResource.GEORGIA_BOLD,
        )
        AppFont.DEVICE_DEFAULT -> null
    }
}
