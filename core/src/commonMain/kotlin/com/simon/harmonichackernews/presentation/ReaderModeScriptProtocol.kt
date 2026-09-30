package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.TextPreferences

data class ReaderModeTheme(
    val light: Boolean,
    val backgroundColor: String,
    val textColor: String,
    val headingColor: String,
    val secondaryTextColor: String,
    val linkColor: String,
    val dividerColor: String,
    val codeBackgroundColor: String,
    val fontFaceCss: String = "",
    val font: String? = null,
    val fontSizePx: Int,
)

/** Shared reader-mode JavaScript protocol and theme serialization. */
object ReaderModeScriptProtocol {
    fun applyCommand(
        script: String,
        theme: ReaderModeTheme,
        enabled: Boolean,
        fontToken: String? = null,
    ): String =
        script + "\nHarmonicReaderMode.setTheme(${themeJson(theme)},${fontToken?.let(::json) ?: "null"});" +
            "\nHarmonicReaderMode.${if (enabled) "enable" else "disable"}();"

    /** The page confirms its installation; a document replacement requests a full retry. */
    internal fun updateCommand(theme: ReaderModeTheme, enabled: Boolean, fontToken: String): String =
        "(function(){if(!window.HarmonicReaderMode||!HarmonicReaderMode.updateTheme||" +
            "!HarmonicReaderMode.updateTheme(${themeJson(theme, includeFont = false)},${json(fontToken)}))" +
            "return 'needs_install';return HarmonicReaderMode.${if (enabled) "enable" else "disable"}();})()"

    internal fun needsInstallation(result: String?): Boolean = normalize(result) == "needs_install"

    fun availabilityCommand(script: String): String =
        script + "\nHarmonicReaderMode.isAvailable();"

    fun parseStatus(result: String?): ReaderModeScriptStatus = when (normalize(result)) {
        "enabled" -> ReaderModeScriptStatus.ENABLED
        "disabled" -> ReaderModeScriptStatus.DISABLED
        "no_article" -> ReaderModeScriptStatus.NO_ARTICLE
        "unavailable" -> ReaderModeScriptStatus.UNAVAILABLE
        else -> ReaderModeScriptStatus.FAILED
    }

    fun isAvailable(result: String?): Boolean = normalize(result) == "available"

    fun fontFamily(font: String?): String = when (TextPreferences.sanitizeFont(font)) {
        "robotoslab", "georgia" -> "Georgia, 'Times New Roman', serif"
        "jetbrainsmono", "googlesanscode" ->
            "ui-monospace, SFMono-Regular, Consolas, 'Liberation Mono', monospace"
        else -> "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
    }

    private fun themeJson(theme: ReaderModeTheme, includeFont: Boolean = true): String {
        val fallback = fontFamily(theme.font)
        val family = if (theme.fontFaceCss.isBlank()) fallback else "'HarmonicReaderFont', $fallback"
        return buildString {
            append("{\"isLight\":").append(theme.light)
            append(",\"backgroundColor\":").append(json(theme.backgroundColor))
            append(",\"textColor\":").append(json(theme.textColor))
            append(",\"headingColor\":").append(json(theme.headingColor))
            append(",\"secondaryTextColor\":").append(json(theme.secondaryTextColor))
            append(",\"linkColor\":").append(json(theme.linkColor))
            append(",\"dividerColor\":").append(json(theme.dividerColor))
            append(",\"codeBackgroundColor\":").append(json(theme.codeBackgroundColor))
            if (includeFont) append(",\"fontFaceCss\":").append(json(theme.fontFaceCss))
            append(",\"fontFamily\":").append(json(family))
            append(",\"headingFontFamily\":").append(json(family))
            append(",\"fontSizePx\":").append(theme.fontSizePx).append('}')
        }
    }

    private fun normalize(result: String?): String = result.orEmpty().trim()
        .let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.lastIndex) else it }

    private fun json(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\', '"' -> append('\\').append(character)
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}
