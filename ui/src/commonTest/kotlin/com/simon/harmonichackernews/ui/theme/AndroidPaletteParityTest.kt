package com.simon.harmonichackernews.ui.theme

import androidx.compose.ui.graphics.toArgb
import kotlin.test.Test
import kotlin.test.assertEquals

/** ARGB snapshots of Android themes before removing Material Components 1.14.0.
 * Keep each app role stable, including reader/page separation and translucent icon tints.
 */
class AndroidPaletteParityTest {
    @Test
    fun everyFixedThemePreservesItsAndroidColorRoles() {
        for ((theme, expected) in snapshots) {
            val colors = HarmonicThemeCatalog.resolve(theme, systemDark = false).colors
            val actual = listOf(
                "readerModeBackground" to colors.readerModeBackground,
                "accent" to colors.accent,
                "onSurface" to colors.onSurface,
                "textPrimary" to colors.textPrimary,
                "textSecondary" to colors.textSecondary,
                "link" to colors.link,
                "surfaceContainerHigh" to colors.surfaceContainerHigh,
                "surfaceContainerHighest" to colors.surfaceContainerHighest,
                "secondaryContainer" to colors.secondaryContainer,
                "onSecondaryContainer" to colors.onSecondaryContainer,
                "contentPrimary" to colors.contentPrimary,
                "mutedText" to colors.mutedText,
                "outlineVariant" to colors.outlineVariant,
                "commentDivider" to colors.commentDivider,
                "commentCountIndicator" to colors.commentCountIndicator,
                "iconTint" to colors.iconTint,
                "mutedSurface" to colors.mutedSurface,
                "settingsHeaderSelected" to colors.settingsHeaderSelected,
                "settingsMainToggle" to colors.settingsMainToggle,
                "settingsMainToggleText" to colors.settingsMainToggleText,
                "overlayButton" to colors.overlayButton,
                "overlayButtonContent" to colors.overlayButtonContent,
                "submissionsCommentTimeBackground" to colors.submissionsCommentTimeBackground,
                "submissionsCommentTimeOutline" to colors.submissionsCommentTimeOutline,
                "background" to colors.background,
                "itemBackground" to colors.itemBackground,
                "contentCardBackground" to colors.contentCardBackground,
                "popupMenuBackground" to colors.popupMenuBackground,
            )
            val expectedColors = expected.split(Regex("\\s+")).filter(String::isNotEmpty)
            assertEquals(actual.size, expectedColors.size, theme)
            for ((index, entry) in actual.withIndex()) {
                assertEquals(expectedColors[index].toLong(16).toInt(), entry.second.toArgb(), "$theme: ${entry.first}")
            }
        }
    }

    private val snapshots = mapOf(
        "dark" to """
            FF222431 FFFF959E FFDFDFDF FFDFDFDF FFBBBBCC FFFF959E FF14191E
            FF2B2E3A FF99595E FFFFD9DC FFDFDFDF FF9999AA 22EEEEFF 22EEEEFF
            FF99595E CCFFFFFF FF2B2E3A FF343744 FF99595E FFFFD9DC FF99595E
            FFFFFFFF FF2B2E3A 00000000 FF222431 FF2B2E3A FF2B2E3A FF14191E
        """.trimIndent(),
        "gray" to """
            FF292A2E FFFF959E FFDFDFDF FFDFDFDF FFBBBBCC FFFF959E FF292A2E
            FF34353A FF99595E FFFFD9DC FFDFDFDF FF9999AA 22EEEEFF 22EEEEFF
            FF99595E CCFFFFFF FF202124 FF292A2E FF34353A FFD4D5DA FF99595E
            FFFFFFFF FF34353A 00000000 FF292A2E FF202124 FF34353A FF14191E
        """.trimIndent(),
        "amoled" to """
            FF000000 FFFF959E FFDFDFDF FFDFDFDF FFBBBBCC FFFF959E FF000000
            FF000000 FF99595E FFFFD9DC FFDFDFDF FF9999AA 22EEEEFF 22EEEEFF
            FF99595E CCFFFFFF FF000000 FF0D0F11 FF0D0F11 FFDADCE2 FF000000
            FFDFDFDF FF000000 33FFFFFF FF000000 FF000000 FF000000 FF14191E
        """.trimIndent(),
        "hacker" to """
            FF000000 FF00FF00 FF00FF00 FF00FF00 9900FF00 FF00FF00 FF000000
            FF000000 FF003300 FF00FF00 FF00FF00 9900FF00 6600FF00 6600FF00
            FF00FF00 CC00FF00 FF000000 FF001A00 FF003300 FF00FF00 FF000000
            FF00FF00 FF000000 6600FF00 FF000000 FF000000 FF000000 FF000000
        """.trimIndent(),
        "light" to """
            FFF6F6EF FF4C9B7B FF2F2F2F FF2F2F2F FF4A4A4A FF4C9B7B FFF6F6EF
            FFF0EEDC FFC6E9D8 FF245B46 FF2F2F2F FF777777 FFAAAAAA FFAAAAAA
            FF99595E 96000000 FFF0EEDC FFEAE6D2 FFC6E9D8 FF245B46 FF4C9B7B
            FFFFFFFF FFF0EEDC 00000000 FFF6F5EC FFFCFCFA FFFCFCFA FFFCFCFA
        """.trimIndent(),
        "white" to """
            FFFFFFFF FF4C9B7B FF2F2F2F FF2F2F2F FF4A4A4A FF4C9B7B FFFFFFFF
            FFF2F4F7 FFC6E9D8 FF245B46 FF2F2F2F FF777777 FFAAAAAA FFAAAAAA
            FF99595E 96000000 FFF2F4F7 FFE1E3E5 FFC6E9D8 FF245B46 FF4C9B7B
            FFFFFFFF FFF2F4F7 00000000 FFF2F4F7 FFFFFFFF FFFFFFFF FFFFFFFF
        """.trimIndent(),
        "hacker_news" to """
            FFF6F6EF FFFF6600 FF222222 FF222222 FF828282 FFFF6600 FFEEEBD9
            FFF2EEDF FFFFD5B8 FF7A3100 FF222222 FF828282 FFD8D3BE FFD8D3BE
            FFB34700 A3000000 FFF2EEDF FFEEEBD9 FFFFD5B8 FF7A3100 FFBF5724
            FFFFFFFF FFF2EEDF 00000000 FFF7F5ED FFFCFCFA FFFCFCFA FFFCFCFA
        """.trimIndent(),
        "material_fixed_dark" to """
            FF141218 FFCCC2DC FFE6E0E9 FFE6E0E9 FFCAC4D0 FFD0BCFF FF2B2930
            FF36343B FF4A4458 FFE8DEF8 FFE6E0E9 FFCAC4D0 FF49454F FF49454F
            FF6750A4 CCE6E0E9 FF2B2930 FF211F26 FF4A4458 FFE8DEF8 FF4F378B
            FFE6E0E9 FF36343B 00000000 FF141218 FF2B2930 FF2B2930 FF2B2930
        """.trimIndent(),
        "material_fixed_light" to """
            FFFEF7FF FF625B71 FF1D1B20 FF1D1B20 FF49454F FF6750A4 FFECE6F0
            FFE6E0E9 FFE8DEF8 FF4A4458 FF1D1B20 FF49454F FFCAC4D0 FFCAC4D0
            FF6750A4 CC1D1B20 FFECE6F0 FFF3EDF7 FFE8DEF8 FF4A4458 FF6750A4
            FFFFFFFF FFE6E0E9 00000000 FFECE6F0 FFFEF7FF FFFEF7FF FFFEF7FF
        """.trimIndent(),
    )
}
