package com.simon.harmonichackernews.ui.content

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PaletteExtractionRunnerTest {
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
}
