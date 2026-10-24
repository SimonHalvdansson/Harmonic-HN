package com.simon.harmonichackernews.ui.common

import androidx.compose.ui.graphics.Path

/** The splash logo's two continuous cubic strokes, in its unpadded 512-unit disk. */
internal fun Path.setHarmonicLogoStroke(index: Int, bend: Float = 0f) {
    reset()
    moveTo(83f, 298f)
    when (index) {
        0 -> {
            cubicTo(120f, 233f + bend / 2, 142f, 168f + bend, 205f, 168f + bend)
            cubicTo(269f, 168f + bend, 281f, 304f - bend, 350f, 304f - bend)
            cubicTo(390f, 304f - bend, 410f, 248f - bend / 2, 429f, 214f)
        }
        1 -> {
            cubicTo(113f, 245f + bend / 2, 129f, 207f + bend, 159f, 207f + bend)
            cubicTo(223f, 207f + bend, 215f, 344f - bend, 305f, 344f - bend)
            cubicTo(370f, 344f - bend, 401f, 263f - bend / 2, 429f, 214f)
        }
        else -> error("Unknown Harmonic logo stroke: $index")
    }
}
