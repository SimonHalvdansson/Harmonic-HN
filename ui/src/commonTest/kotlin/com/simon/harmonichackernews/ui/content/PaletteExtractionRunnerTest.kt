package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PaletteExtractionRunnerTest {
    @Test
    fun painterSamplingHandlesUnspecifiedAndFractionalIntrinsicSizes() {
        assertEquals(IntSize(96, 96), painterPaletteSampleDimensions(Size.Unspecified))
        assertEquals(IntSize(96, 96), painterPaletteSampleDimensions(Size.Zero))
        assertEquals(IntSize(96, 64), painterPaletteSampleDimensions(Size(101f, 67f)))
        assertEquals(IntSize(96, 48), painterPaletteSampleDimensions(Size(100.5f, 50.25f)))
        assertEquals(IntSize(1, 96), painterPaletteSampleDimensions(Size(1f, 173f)))
    }

    @Test
    fun resourceSamplingRetainsItsDistinctAreaLimitAndDoesNotUpscale() {
        assertEquals(IntSize(1, 1), resourcePaletteSampleDimensions(1, 1))
        assertEquals(IntSize(112, 112), resourcePaletteSampleDimensions(112, 112))
        assertEquals(IntSize(112, 112), resourcePaletteSampleDimensions(224, 224))
        assertEquals(IntSize(150, 84), resourcePaletteSampleDimensions(1600, 900))
    }

    @Test
    fun limitsSimultaneousExtractions() = runTest {
        val runner = PaletteExtractionRunner(
            maxConcurrentExtractions = 2,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val release = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0

        val jobs = List(6) {
            async {
                runner.run {
                    active += 1
                    peak = maxOf(peak, active)
                    release.await()
                    active -= 1
                }
            }
        }

        testScheduler.runCurrent()
        assertEquals(2, peak)
        release.complete(Unit)
        jobs.awaitAll()
        assertEquals(0, active)
    }

    @Test
    fun sampleDimensionsPreserveAspectRatioWithinNinetySixPixels() {
        assertEquals(96 to 48, paletteSampleDimensions(200, 100))
        assertEquals(48 to 96, paletteSampleDimensions(100, 200))
        assertEquals(96 to 96, paletteSampleDimensions(32, 32))
    }

    @Test
    fun previewSamplesRoundLikeTheOriginalPainterRatherThanTruncating() {
        assertEquals(IntSize(96, 64), previewPaletteSampleDimensions(101, 67))
        assertEquals(IntSize(64, 96), previewPaletteSampleDimensions(67, 101))
        assertEquals(IntSize(96, 96), previewPaletteSampleDimensions(32, 32))
        assertEquals(IntSize(1, 96), previewPaletteSampleDimensions(1, 173))
    }
}
