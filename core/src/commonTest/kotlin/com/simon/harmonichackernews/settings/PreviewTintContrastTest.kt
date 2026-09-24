package com.simon.harmonichackernews.settings

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PreviewTintContrastTest {
    @Test
    fun matchingAndNearbyTintsSeparateFromLightDarkAndColoredBackgrounds() {
        for (background in listOf(0xff000000, 0xff151515, 0xff34353a, 0xff808080, 0xfff3edf7, 0xffffffff, 0xff408050)) {
            for (tint in listOf(background, 0xfff4eeee, 0xff191921, 0xff777788)) {
                val result = PreviewTintPolicy.ensureCardTintContrast(tint.toInt(), background.toInt(), null)
                assertTrue(contrast(result, background.toInt()) >= 1.2, "Tint $tint against $background")
                assertEquals(255, result ushr 24)
                assertEquals(result, PreviewTintPolicy.ensureCardTintContrast(result, background.toInt(), null))
            }
        }
    }

    @Test
    fun alreadyDistinctTintIsUnchanged() {
        val tint = 0xffaaccdd.toInt()
        assertEquals(tint, PreviewTintPolicy.ensureCardTintContrast(tint, 0xff151515.toInt(), null))
    }

    @Test
    fun strengthCanFadeToZeroWithoutAnAbruptMinimum() {
        val background = 0xff151515.toInt()
        var previous = 1.0
        for (strength in listOf(0, 1, 10, 25, 50, 75, 100, 200)) {
            val config = PaletteTintPreferences.configKey(null, strength, 110, 0)
            val result = PreviewTintPolicy.ensureCardTintContrast(background, background, config)
            val ratio = contrast(result, background)
            assertTrue(ratio >= 1.0 + 0.2 * minOf(strength, 100) / 100.0)
            assertTrue(ratio >= previous)
            previous = ratio
            if (strength == 0) assertEquals(background, result)
            if (strength == 1) assertTrue(ratio < 1.05)
        }
    }

    @Test
    fun liftsDarkSurfacesAndDarkensLightSurfacesWithOnlySmallAdjustment() {
        for (background in listOf(0xff151515.toInt(), 0xfff3edf7.toInt())) {
            val result = PreviewTintPolicy.ensureCardTintContrast(background, background, null)
            assertTrue(contrast(result, background) in 1.2..1.23)
            assertEquals(luminance(background) < 0.5, luminance(result) > luminance(background))
        }
    }

    private fun contrast(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 255) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
