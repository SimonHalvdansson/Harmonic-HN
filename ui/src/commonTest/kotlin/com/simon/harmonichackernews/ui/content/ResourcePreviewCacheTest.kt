package com.simon.harmonichackernews.ui.content

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ResourcePreviewCacheTest {
    @Test
    fun simultaneousAndLaterRequestsReuseOneLoad() = runTest {
        val cache = ResourcePreviewCache<String, Int>(5)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var loads = 0
        val first = async {
            cache.getOrLoad("sample") {
                loads++
                started.complete(Unit)
                release.await()
                42
            }
        }
        started.await()
        val second = async { cache.getOrLoad("sample") { loads++; 99 } }
        yield()
        assertNull(cache["sample"])
        release.complete(Unit)
        assertEquals(42, first.await())
        assertEquals(42, second.await())
        assertEquals(42, cache["sample"])
        assertEquals(42, cache.getOrLoad("sample") { loads++; 100 })
        assertEquals(1, loads)
    }

    @Test
    fun distinctResourcesAndEnvironmentsAreBounded() = runTest {
        val cache = ResourcePreviewCache<Pair<String, String>, Int>(2)
        val first = "sample1" to "light"
        val second = "sample2" to "light"
        val dark = "sample1" to "dark"
        cache.getOrLoad(first) { 1 }
        cache.getOrLoad(second) { 2 }
        cache.getOrLoad(dark) { 3 }
        assertNull(cache[first])
        assertEquals(2, cache[second])
        assertEquals(3, cache[dark])
    }

    @Test
    fun cancellingOneLoadDoesNotBlockTheNextVisit() = runTest {
        val cache = ResourcePreviewCache<String, Int>(5)
        val started = CompletableDeferred<Unit>()
        val first = async {
            cache.getOrLoad("sample") {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        val second = async { cache.getOrLoad("sample") { 42 } }
        first.cancelAndJoin()
        assertEquals(42, second.await())
        assertEquals(42, cache["sample"])
    }

    @Test
    fun failedLoadsAreNotCached() = runTest {
        val cache = ResourcePreviewCache<String, Int>(5)
        assertFailsWith<IllegalStateException> {
            cache.getOrLoad("sample") { error("Decode failed") }
        }
        assertNull(cache["sample"])
        assertEquals(42, cache.getOrLoad("sample") { 42 })
    }
}
