/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.simon.harmonichackernews.palette

import com.simon.harmonichackernews.settings.PreviewTintPalette
import com.simon.harmonichackernews.settings.PreviewTintSwatch
import kotlin.math.abs
import kotlin.math.round

/** An owned result; none of its values refer to the extractor's reusable workspace. */
data class HarmonicPaletteSwatch(
    val rgb: Int,
    val population: Int,
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
)

data class HarmonicPalette(
    val swatches: List<HarmonicPaletteSwatch>,
    val dominant: HarmonicPaletteSwatch?,
    val lightVibrant: HarmonicPaletteSwatch?,
    val vibrant: HarmonicPaletteSwatch?,
    val darkVibrant: HarmonicPaletteSwatch?,
    val lightMuted: HarmonicPaletteSwatch?,
    val muted: HarmonicPaletteSwatch?,
    val darkMuted: HarmonicPaletteSwatch?,
) {
    fun toPreviewTintPalette() = PreviewTintPalette(
        vibrant = vibrant?.tintSwatch(), lightVibrant = lightVibrant?.tintSwatch(),
        darkVibrant = darkVibrant?.tintSwatch(), dominant = dominant?.tintSwatch(),
        muted = muted?.tintSwatch(), lightMuted = lightMuted?.tintSwatch(),
        darkMuted = darkMuted?.tintSwatch(),
    )

    private fun HarmonicPaletteSwatch.tintSwatch() = PreviewTintSwatch(hue, saturation)
}

/**
 * Specialized for already-sampled pixels, 16 colors, and KMPalette 4.0.0's default filter/targets.
 * Derived from its Apache-licensed ColorCutQuantizer, Palette, Target, and ColorUtils algorithms.
 * Alpha is intentionally ignored, just as in the reference quantizer; sampling handles compositing.
 *
 * Reuses histogram, sorting, and box storage. An instance must have only one caller at a time.
 * Input pixels are never modified. Returned results remain valid after the next extraction.
 */
class HarmonicPaletteExtractor(private val maximumPixelCount: Int = 96 * 96) {
    init { require(maximumPixelCount > 0) }

    private val histogram = IntArray(32768)
    private val colors = IntArray(minOf(maximumPixelCount, 32768))
    private val scratch = IntArray(colors.size)
    private val buckets = IntArray(32)
    private val boxes = Array(16) { Box() }
    private val heap = IntArray(16)
    private var heapSize = 0
    private val hsl = FloatArray(3)

    fun extract(pixels: IntArray, pixelCount: Int = pixels.size): HarmonicPalette {
        require(pixelCount in 0..minOf(pixels.size, maximumPixelCount))
        var colorCount = 0
        val allowed = DefaultPaletteColors.allowed
        try {
            for (i in 0 until pixelCount) {
                val pixel = pixels[i]
                val color = ((pixel ushr 9) and 0x7c00) or
                    ((pixel ushr 6) and 0x03e0) or ((pixel ushr 3) and 0x001f)
                if (allowed[color]) {
                    if (histogram[color] == 0) colors[colorCount++] = color
                    histogram[color]++
                }
            }

            val swatches = ArrayList<HarmonicPaletteSwatch>(16)
            if (colorCount <= 16) {
                // The reference enumerates histogram bins in ascending order. This determines ties.
                colors.sort(0, colorCount)
                for (i in 0 until colorCount) swatches.add(swatch(colors[i], histogram[colors[i]]))
            } else {
                // No initial sort is needed: the first split orders the entire color array.
                heapSize = 0
                fitBox(0, 0, colorCount - 1)
                offer(0)
                var nextBox = 1
                while (heapSize < 16) {
                    val boxIndex = poll()
                    val box = boxes[boxIndex]
                    // Preserve the reference's behavior of removing this unsplittable box.
                    if (box.lower == box.upper) break
                    sortBox(box)
                    var count = 0
                    var split = box.lower
                    for (i in box.lower..box.upper) {
                        count += histogram[colors[i]]
                        if (count >= box.population / 2) {
                            split = minOf(box.upper - 1, i)
                            break
                        }
                    }
                    fitBox(nextBox, split + 1, box.upper)
                    fitBox(boxIndex, box.lower, split)
                    offer(nextBox++)
                    offer(boxIndex)
                }
                // Heap-array order, rather than poll order, is part of the reference's tie behavior.
                for (i in 0 until heapSize) {
                    val box = boxes[heap[i]]
                    var red = 0
                    var green = 0
                    var blue = 0
                    for (j in box.lower..box.upper) {
                        val color = colors[j]
                        val population = histogram[color]
                        red += population * (color ushr 10)
                        green += population * ((color ushr 5) and 31)
                        blue += population * (color and 31)
                    }
                    // Kotlin round uses ties-to-even, matching KMPalette (not roundToInt).
                    val mean = (round(red / box.population.toFloat()).toInt() shl 10) or
                        (round(green / box.population.toFloat()).toInt() shl 5) or
                        round(blue / box.population.toFloat()).toInt()
                    if (allowed[mean]) swatches.add(swatch(mean, box.population))
                }
            }
            return selectTargets(swatches)
        } finally {
            // Sorting only permutes these keys, so every touched bin is cleared exactly once.
            for (i in 0 until colorCount) histogram[colors[i]] = 0
        }
    }

    private fun swatch(color: Int, population: Int): HarmonicPaletteSwatch {
        colorHsl(color, hsl)
        return HarmonicPaletteSwatch(expandRgb(color), population, hsl[0], hsl[1], hsl[2])
    }

    private class Box {
        var lower = 0
        var upper = 0
        var population = 0
        var volume = 0
        var dimension = 0 // 0 = red, 1 = green, 2 = blue
    }

    private fun fitBox(index: Int, lower: Int, upper: Int) {
        var minR = 31
        var minG = 31
        var minB = 31
        var maxR = 0
        var maxG = 0
        var maxB = 0
        var population = 0
        for (i in lower..upper) {
            val color = colors[i]
            val r = color ushr 10
            val g = (color ushr 5) and 31
            val b = color and 31
            minR = minOf(minR, r)
            minG = minOf(minG, g)
            minB = minOf(minB, b)
            maxR = maxOf(maxR, r)
            maxG = maxOf(maxG, g)
            maxB = maxOf(maxB, b)
            population += histogram[color]
        }
        val redRange = maxR - minR
        val greenRange = maxG - minG
        val blueRange = maxB - minB
        boxes[index].apply {
            this.lower = lower
            this.upper = upper
            this.population = population
            volume = (redRange + 1) * (greenRange + 1) * (blueRange + 1)
            dimension = when {
                redRange >= greenRange && redRange >= blueRange -> 0
                greenRange >= redRange && greenRange >= blueRange -> 1
                else -> 2
            }
        }
    }

    private fun sortBox(box: Box) {
        val start = box.lower
        val end = box.upper + 1
        if (end - start < 128) {
            if (box.dimension != 0) {
                for (i in start until end) colors[i] = swapChannel(colors[i], box.dimension)
            }
            colors.sort(start, end)
            if (box.dimension != 0) {
                for (i in start until end) colors[i] = swapChannel(colors[i], box.dimension)
            }
            return
        }
        // Stable radix sort of the complete RGB/GRB/BGR key, including secondary-channel ties.
        val lowShift = if (box.dimension == 2) 10 else 0
        val midShift = if (box.dimension == 1) 10 else 5
        val highShift = when (box.dimension) { 0 -> 10; 1 -> 5; else -> 0 }
        radixPass(colors, scratch, start, end, lowShift)
        radixPass(scratch, colors, start, end, midShift)
        radixPass(colors, scratch, start, end, highShift)
        scratch.copyInto(colors, start, start, end)
    }

    private fun radixPass(input: IntArray, output: IntArray, start: Int, end: Int, shift: Int) {
        buckets.fill(0)
        for (i in start until end) buckets[(input[i] ushr shift) and 31]++
        var offset = start
        for (i in buckets.indices) {
            val count = buckets[i]
            buckets[i] = offset
            offset += count
        }
        for (i in start until end) {
            val color = input[i]
            output[buckets[(color ushr shift) and 31]++] = color
        }
    }

    private fun swapChannel(color: Int, dimension: Int): Int = if (dimension == 1) {
        (((color ushr 5) and 31) shl 10) or ((color ushr 10) shl 5) or (color and 31)
    } else {
        ((color and 31) shl 10) or (color and 0x3e0) or (color ushr 10)
    }

    private fun offer(box: Int) {
        var index = heapSize++
        heap[index] = box
        while (index > 0) {
            val parent = (index - 1) / 2
            if (boxes[heap[parent]].volume >= boxes[heap[index]].volume) break
            val old = heap[parent]
            heap[parent] = heap[index]
            heap[index] = old
            index = parent
        }
    }

    private fun poll(): Int {
        val result = heap[0]
        heap[0] = heap[--heapSize]
        var index = 0
        while (index * 2 + 1 < heapSize) {
            val left = index * 2 + 1
            val right = left + 1
            // The reference chooses the right child when volumes tie.
            val child = if (right >= heapSize || boxes[heap[left]].volume > boxes[heap[right]].volume) left else right
            if (boxes[heap[child]].volume <= boxes[heap[index]].volume) break
            val old = heap[index]
            heap[index] = heap[child]
            heap[child] = old
            index = child
        }
        return result
    }
}

private fun selectTargets(swatches: List<HarmonicPaletteSwatch>): HarmonicPalette {
    val dominant = swatches.maxByOrNull { it.population }
    val selected = arrayOfNulls<HarmonicPaletteSwatch>(6)
    val maxPopulation = dominant?.population ?: 1
    for (target in selected.indices) {
        val vibrant = target < 3
        val luma = target % 3
        val minLuma = when (luma) { 0 -> 0.55f; 1 -> 0.3f; else -> 0f }
        val maxLuma = when (luma) { 0 -> 1f; 1 -> 0.7f; else -> 0.45f }
        val targetLuma = when (luma) { 0 -> 0.74f; 1 -> 0.5f; else -> 0.26f }
        val targetSaturation = if (vibrant) 1f else 0.3f
        var bestScore = Float.MIN_VALUE
        for (swatch in swatches) {
            if (swatch.saturation < (if (vibrant) 0.35f else 0f) ||
                swatch.saturation > (if (vibrant) 1f else 0.4f) ||
                swatch.lightness < minLuma || swatch.lightness > maxLuma) continue
            var used = false
            for (previous in 0 until target) {
                if (selected[previous]?.rgb == swatch.rgb) { used = true; break }
            }
            if (used) continue
            // Preserve operation order and Float division to keep score ties identical.
            val saturationScore = 0.24f * (1f - abs(swatch.saturation - targetSaturation))
            val lightnessScore = 0.52f * (1f - abs(swatch.lightness - targetLuma))
            val populationScore = 0.24f * (swatch.population / maxPopulation.toFloat())
            val score = saturationScore + lightnessScore + populationScore
            if (score > bestScore) {
                bestScore = score
                selected[target] = swatch
            }
        }
    }
    return HarmonicPalette(swatches, dominant, selected[0], selected[1], selected[2],
        selected[3], selected[4], selected[5])
}

/** Immutable after initialization; shared safely by all extraction workspaces. */
private object DefaultPaletteColors {
    val allowed: BooleanArray = BooleanArray(32768).also { table ->
        val hsl = FloatArray(3)
        for (color in table.indices) {
            colorHsl(color, hsl)
            table[color] = hsl[2] > 0.05f && hsl[2] < 0.95f &&
                !(hsl[0] in 10f..37f && hsl[1] <= 0.82f)
        }
    }
}

private fun expandRgb(color: Int): Int = 0xff000000.toInt() or
    ((color and 0x7c00) shl 9) or ((color and 0x03e0) shl 6) or ((color and 31) shl 3)

/** Same arithmetic as the reference ColorUtils, including its clamping and hue wrap. */
private fun colorHsl(color: Int, output: FloatArray) {
    val rf = ((color ushr 10) shl 3) / 255f
    val gf = (((color ushr 5) and 31) shl 3) / 255f
    val bf = ((color and 31) shl 3) / 255f
    val max = maxOf(rf, maxOf(gf, bf))
    val min = minOf(rf, minOf(gf, bf))
    val delta = max - min
    val lightness = (max + min) / 2f
    var hue = if (max == min) 0f else when (max) {
        rf -> (gf - bf) / delta % 6f
        gf -> (bf - rf) / delta + 2f
        else -> (rf - gf) / delta + 4f
    }
    val saturation = if (max == min) 0f else delta / (1f - abs(2f * lightness - 1f))
    hue = hue * 60f % 360f
    if (hue < 0f) hue += 360f
    output[0] = hue.coerceIn(0f, 360f)
    output[1] = saturation.coerceIn(0f, 1f)
    output[2] = lightness.coerceIn(0f, 1f)
}
