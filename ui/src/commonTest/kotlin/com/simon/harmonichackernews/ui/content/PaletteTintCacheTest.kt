package com.simon.harmonichackernews.ui.content

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class PaletteTintCacheTest {
    @Test
    fun concurrentRequestsShareOneExtractionAndReceiveItsResult() = runTest {
        val cache = PaletteTintCache(maxEntries = 8)
        val key = key("https://icons.example/news.example.com.ico")
        val extractionStarted = CompletableDeferred<Unit>()
        val finishExtraction = CompletableDeferred<Unit>()
        var extractionCount = 0

        val first = async {
            cache.getOrExtract(key) {
                extractionCount += 1
                extractionStarted.complete(Unit)
                finishExtraction.await()
                0xff123456.toInt()
            }
        }
        extractionStarted.await()
        val second = async {
            cache.getOrExtract(key) {
                extractionCount += 1
                0xff654321.toInt()
            }
        }
        yield()

        assertEquals(1, extractionCount)
        finishExtraction.complete(Unit)
        assertEquals(0xff123456.toInt(), first.await())
        assertEquals(0xff123456.toInt(), second.await())
        assertEquals(1, extractionCount)
    }

    @Test
    fun completedResultIsReusedForLaterRequests() = runTest {
        val cache = PaletteTintCache(maxEntries = 8)
        val key = key("https://icons.example/news.example.com.ico")
        var extractionCount = 0

        val first = cache.getOrExtract(key) {
            extractionCount += 1
            0xff123456.toInt()
        }
        val second = cache.getOrExtract(key) {
            extractionCount += 1
            0xff654321.toInt()
        }

        assertEquals(0xff123456.toInt(), first)
        assertEquals(0xff123456.toInt(), second)
        assertEquals(1, extractionCount)
    }

    @Test
    fun waitingRequestTakesOverIfTheInitialExtractorIsCancelled() = runTest {
        val cache = PaletteTintCache(maxEntries = 8)
        val key = key("https://icons.example/news.example.com.ico")
        val extractionStarted = CompletableDeferred<Unit>()
        var extractionCount = 0

        val cancelled = async {
            cache.getOrExtract(key) {
                extractionCount += 1
                extractionStarted.complete(Unit)
                awaitCancellation()
            }
        }
        extractionStarted.await()
        val replacement = async {
            cache.getOrExtract(key) {
                extractionCount += 1
                0xff123456.toInt()
            }
        }

        cancelled.cancelAndJoin()

        assertEquals(0xff123456.toInt(), replacement.await())
        assertEquals(2, extractionCount)
    }

    @Test
    fun resourceAndPaletteInputsRemainDistinctCacheKeys() = runTest {
        val cache = PaletteTintCache(maxEntries = 8)
        var extractionCount = 0
        val keys = listOf(
            key("https://icons.example/one.example.com.ico"),
            key("https://icons.example/two.example.com.ico"),
            key("https://icons.example/one.example.com.ico", palette = "vibrant"),
        )

        val results = keys.mapIndexed { index, key ->
            cache.getOrExtract(key) {
                extractionCount += 1
                index
            }
        }

        assertEquals(listOf(0, 1, 2), results)
        assertEquals(3, extractionCount)
    }

    private fun key(
        resource: String,
        palette: String = "default",
    ) = PaletteTintCacheKey(
        resourceKey = resource,
        baseColorArgb = 0xff000000.toInt(),
        paletteTintConfigKey = palette,
    )
}
