package com.simon.harmonichackernews.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PreviewContentCoordinatorTest {
    @Test
    fun overflowEvictsOnlyLeastRecentlyUsedImagesAndSummaries() = runTest {
        for (summary in listOf(false, true)) {
            val coordinator = PreviewContentCoordinator(this, maxImageEntries = 3)
            val loads = mutableListOf<String>()
            suspend fun load(url: String) = coordinator.load(url, summary, false) {
                loads += url
                LinkSummary(description = url, imageUrl = "$url/image")
            }
            listOf("a", "b", "c", "a", "d", "a", "c", "d").forEach { load(it) }
            assertEquals(listOf("a", "b", "c", "d"), loads)
            load("b")
            assertEquals(listOf("a", "b", "c", "d", "b"), loads)
        }
    }

    @Test
    fun positiveCacheOverflowDoesNotDiscardUnexpiredMisses() = runTest {
        val coordinator = PreviewContentCoordinator(this, maxImageEntries = 1)
        coordinator.load("miss", false, false) { LinkSummary() }
        coordinator.load("a", false, false) { LinkSummary(imageUrl = "a/image") }
        coordinator.load("b", false, false) { LinkSummary(imageUrl = "b/image") }
        coordinator.load("miss", false, false) { error("Miss should survive unrelated eviction") }
    }

    @Test
    fun transientFailureDoesNotBecomeAnInMemoryMiss() = runTest {
        val coordinator = PreviewContentCoordinator(this)
        var attempts = 0

        val failed = coordinator.load("https://example.com", false, false) {
            attempts++
            error("temporary failure")
        }
        val retried = coordinator.load("https://example.com", false, false) {
            attempts++
            LinkSummary()
        }

        assertEquals(PreviewImageResult.TRANSIENT_FAILURE, failed.imageResult)
        assertEquals(PreviewImageResult.CONFIRMED, retried.imageResult)
        assertEquals(2, attempts)
    }

    @Test
    fun forceRefreshBypassesAConfirmedMiss() = runTest {
        val coordinator = PreviewContentCoordinator(this)
        var attempts = 0
        coordinator.load("https://example.com", false, false) {
            attempts++
            LinkSummary()
        }
        coordinator.load("https://example.com", false, false) {
            error("confirmed miss should have been reused")
        }

        val refreshed = coordinator.load("https://example.com", false, true) {
            attempts++
            LinkSummary(imageUrl = "https://example.com/image.png")
        }

        assertEquals("https://example.com/image.png", refreshed.imageUrl)
        assertEquals(2, attempts)
    }

    @Test
    fun confirmedInMemoryMissExpires() = runTest {
        var now = 1_000L
        val coordinator = PreviewContentCoordinator(
            scope = this,
            missTtlMillis = 50L,
            nowMillis = { now },
        )
        var attempts = 0
        coordinator.load("https://example.com", false, false) {
            attempts++
            LinkSummary()
        }
        coordinator.load("https://example.com", false, false) {
            error("unexpired miss should have been reused")
        }

        now = 1_051L
        coordinator.load("https://example.com", false, false) {
            attempts++
            LinkSummary(imageUrl = "https://example.com/new.png")
        }

        assertEquals(2, attempts)
    }

    @Test
    fun supersededRequestCannotOverwriteForceRefreshResult() = runTest {
        val coordinator = PreviewContentCoordinator(this)
        val oldResult = CompletableDeferred<LinkSummary>()
        val first = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.load("https://example.com", false, false) { oldResult.await() }
        }

        val refreshed = coordinator.load("https://example.com", false, true) {
            LinkSummary(imageUrl = "https://example.com/new.png")
        }
        oldResult.complete(LinkSummary(imageUrl = "https://example.com/old.png"))
        first.await()
        val cached = coordinator.load("https://example.com", false, false) {
            error("force-refreshed image should be cached")
        }

        assertEquals("https://example.com/new.png", refreshed.imageUrl)
        assertEquals("https://example.com/new.png", cached.imageUrl)
    }
}
