package com.simon.harmonichackernews.palette

import com.simon.harmonichackernews.settings.PreviewTintPolicy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Goldens recorded from KMPalette 4.0.0 after direct field-by-field equivalence checks.
 * Includes ordered swatches/populations, exact HSL bits, all targets and 18 tints per image.
 * Do not regenerate these expectations from the implementation under test.
 */
class HarmonicPaletteExtractorTest {
    @Test
    fun everyQuantizedColorMatchesRecordedFilterAndHsl() {
        val extractor = HarmonicPaletteExtractor()
        val fingerprint = PaletteFingerprint()
        for (start in 0 until 32768 step 16) {
            fingerprint.add(extractor.extract(IntArray(16) { rgb(start + it) }))
        }
        assertEquals("a7fd22cfc46c60ed", fingerprint.hex())
    }

    @Test
    fun seededImagesMatchRecordedSwatchesTargetsAndFinalTints() {
        val random = Random(0x50414c)
        val extractor = HarmonicPaletteExtractor()
        val fingerprint = PaletteFingerprint()
        repeat(1200) { iteration ->
            val distinct = listOf(1, 2, 8, 16, 17, 32, 127, 128, 129, 256, 2048, 9216)[iteration % 12]
            val values = IntArray(distinct) { random.nextInt() }
            val pixels = IntArray(random.nextInt(1, 9217)) { values[random.nextInt(distinct)] }
            fingerprint.add(extractor.extract(pixels))
        }
        assertEquals("6e2309f25f7d7285", fingerprint.hex())
    }

    @Test
    fun fullColorSpaceAndAdversarialPopulationTiesMatchRecordedResults() {
        val extractor = HarmonicPaletteExtractor(32768)
        val fingerprint = PaletteFingerprint()
        fingerprint.add(extractor.extract(IntArray(32768) { rgb(it) }))
        fingerprint.add(extractor.extract(IntArray(32768) { rgb(32767 - it) }))
        for (size in listOf(0, 1, 15, 16, 17, 31, 32, 63, 64, 127, 128, 129, 1024, 9216)) {
            fingerprint.add(extractor.extract(IntArray(size) { rgb((it * 977) and 32767) }))
            fingerprint.add(extractor.extract(IntArray(size) { rgb(if (it % 7 == 0) 2048 else it and 32767) }))
        }
        fingerprint.add(extractor.extract(IntArray(96 * 96) { 0x00ffffff }))
        fingerprint.add(extractor.extract(IntArray(96 * 96)))
        assertEquals("cafc90b457f91f73", fingerprint.hex())
    }

    @Test
    fun inputAndPreviousResultsSurviveWorkspaceReuse() {
        val extractor = HarmonicPaletteExtractor()
        val original = IntArray(256) { rgb(it * 97) }
        val pixels = original.copyOf()
        val first = extractor.extract(pixels)
        assertContentEquals(original, pixels)
        extractor.extract(IntArray(9216) { rgb((it * 53) and 32767) })
        assertEquals(first, extractor.extract(original))
        assertFailsWith<IllegalArgumentException> { extractor.extract(IntArray(9217)) }
        assertEquals(first, extractor.extract(original))
        val oversizedBuffer = original + IntArray(128) { 0xffff0000.toInt() }
        assertEquals(first, extractor.extract(oversizedBuffer, original.size))
    }

    private fun rgb(color: Int) = 0xff000000.toInt() or ((color and 0x7c00) shl 9) or
        ((color and 0x3e0) shl 6) or ((color and 31) shl 3)
}

/** Canonical FNV-1a fingerprint of all output fields and 18 final tint configurations. */
private class PaletteFingerprint {
    private var value = 0xcbf29ce484222325UL

    private fun add(value: Int) {
        for (shift in 0 until 32 step 8) {
            this.value = (this.value xor ((value ushr shift) and 255).toULong()) * 0x100000001b3UL
        }
    }

    fun add(palette: HarmonicPalette) {
        fun swatch(value: HarmonicPaletteSwatch?) {
            add(if (value == null) 0 else 1)
            if (value != null) {
                add(value.rgb); add(value.population)
                add(value.hue.toBits()); add(value.saturation.toBits()); add(value.lightness.toBits())
            }
        }
        add(palette.swatches.size)
        palette.swatches.forEach { swatch(it) }
        listOf(palette.dominant, palette.lightVibrant, palette.vibrant, palette.darkVibrant,
            palette.lightMuted, palette.muted, palette.darkMuted).forEach { swatch(it) }
        for (base in listOf(0xfff8f7f4.toInt(), 0xff151515.toInt())) {
            for (mode in listOf("default", "vibrant", "dominant")) {
                for (settings in listOf("100|110|0", "200|200|20", "0|0|-20")) {
                    add(PreviewTintPolicy.calculateCardTint(base, palette.toPreviewTintPalette(), "$mode|$settings"))
                }
            }
        }
    }

    fun hex() = value.toString(16).padStart(16, '0')
}
