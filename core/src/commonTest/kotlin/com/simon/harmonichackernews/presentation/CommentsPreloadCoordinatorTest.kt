package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.network.CommentThreadSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsPreloadCoordinatorTest {
    @Test
    fun disablingCancelsRunningAndQueuedPreloadsWithImmediateCallbacks() =
        assertCancelsRunningAndQueuedPreloads { it.setEnabled(false) }

    @Test
    fun disposingCancelsRunningAndQueuedPreloadsWithImmediateCallbacks() =
        assertCancelsRunningAndQueuedPreloads { it.dispose() }

    private fun assertCancelsRunningAndQueuedPreloads(stop: (CommentsPreloadCoordinator) -> Unit) = runTest {
        val release = CompletableDeferred<Unit>()
        val requested = mutableListOf<Int>()
        val cancelled = mutableListOf<Int>()
        val scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        val coordinator = CommentsPreloadCoordinator(
            scope = scope,
            loadFilteredUsers = { emptySet() },
            isPrepared = { _, _, _, _ -> false },
            preload = { _, id, _, _ ->
                requested += id
                try {
                    release.await()
                } finally {
                    cancelled += id
                }
            },
            maxConcurrentPreloads = 2,
            scrollSettleDelayMillis = 0,
            // Workers dispatch separately; semaphore waiters still cancel immediately on the UI dispatcher.
            preloadDispatcher = StandardTestDispatcher(testScheduler),
        )
        coordinator.setEnabled(true)
        coordinator.updateVisibleStories((1..5).map { story(it) })
        runCurrent()
        assertEquals(listOf(1, 2), requested)
        val jobs = scope.coroutineContext.job.children.toList()
        assertEquals(5, jobs.size)

        stop(coordinator)
        runCurrent()

        assertTrue(jobs.all { it.isCancelled && it.isCompleted })
        assertEquals(setOf(1, 2), cancelled.toSet())
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 2), requested)
        coordinator.dispose()
    }

    @Test
    fun latestViewportDropsQueuedWorkButRetainsAnAlreadyStartedTransfer() = runTest {
        val release = CompletableDeferred<Unit>()
        val requested = mutableListOf<Int>()
        val coordinator = CommentsPreloadCoordinator(
            scope = backgroundScope,
            loadFilteredUsers = { emptySet() },
            isPrepared = { _, _, _, _ -> false },
            preload = { _, id, _, _ ->
                requested += id
                if (id == 1) release.await()
            },
            maxConcurrentPreloads = 1,
            scrollSettleDelayMillis = 0,
            preloadDispatcher = StandardTestDispatcher(testScheduler),
        )
        coordinator.setEnabled(true)
        coordinator.updateVisibleStories(listOf(story(1), story(2), story(3)))
        runCurrent()
        assertEquals(listOf(1), requested)

        coordinator.updateVisibleStories(listOf(story(4)))
        runCurrent()
        assertEquals(listOf(1), requested)
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(1, 4), requested)
    }

    @Test
    fun queuedWorkRechecksVisibilityEvenBeforeTheNewViewportSettles() = runTest {
        val release = CompletableDeferred<Unit>()
        val requested = mutableListOf<Int>()
        val coordinator = CommentsPreloadCoordinator(
            scope = backgroundScope,
            loadFilteredUsers = { emptySet() },
            isPrepared = { _, _, _, _ -> false },
            preload = { _, id, _, _ ->
                requested += id
                if (id == 1) release.await()
            },
            maxConcurrentPreloads = 1,
            scrollSettleDelayMillis = 300,
            preloadDispatcher = StandardTestDispatcher(testScheduler),
        )
        coordinator.setEnabled(true)
        coordinator.updateVisibleStories(listOf(story(1), story(2)))
        advanceTimeBy(300.milliseconds)
        runCurrent()
        coordinator.updateVisibleStories(emptyList())
        release.complete(Unit)
        runCurrent()
        assertEquals(listOf(1), requested)
    }

    @Test
    fun enabledCoordinatorPreloadsEligibleVisibleDiscussionsAfterScrollingSettles() = runTest {
        val requested = mutableListOf<Triple<Int, List<Int>, Set<String>>>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val coordinator = CommentsPreloadCoordinator(
            scope = this,
            loadFilteredUsers = { setOf("blocked-user") },
            isPrepared = { _, storyId, _, _ -> storyId == 2 },
            preload = { _, storyId, kids, filteredUsers ->
                requested += Triple(storyId, kids, filteredUsers)
            },
            scrollSettleDelayMillis = 300L,
            preloadDispatcher = dispatcher,
        )

        coordinator.setEnabled(true)
        coordinator.updateVisibleStories(
            listOf(
                story(id = 1, kids = listOf(11, 12)),
                story(id = 2, kids = listOf(21)),
                story(id = 3, descendants = 0),
                story(id = 4, loaded = false),
                story(id = 5, isComment = true),
                story(id = 1, kids = listOf(11, 12)),
            ),
        )

        advanceTimeBy(299L.milliseconds)
        runCurrent()
        assertEquals(emptyList(), requested)

        advanceTimeBy(1L.milliseconds)
        runCurrent()
        assertEquals(
            listOf(Triple(1, listOf(11, 12), setOf("blocked-user"))),
            requested,
        )
    }

    @Test
    fun disablingCoordinatorCancelsPendingPreloads() = runTest {
        val requested = mutableListOf<Int>()
        val coordinator = CommentsPreloadCoordinator(
            scope = this,
            loadFilteredUsers = { emptySet() },
            isPrepared = { _, _, _, _ -> false },
            preload = { _, storyId, _, _ -> requested += storyId },
            scrollSettleDelayMillis = 300L,
            preloadDispatcher = StandardTestDispatcher(testScheduler),
        )

        coordinator.setEnabled(true)
        coordinator.updateVisibleStories(listOf(story(1)))
        coordinator.setEnabled(false)
        advanceTimeBy(300L.milliseconds)
        runCurrent()

        assertEquals(emptyList(), requested)
    }

    @Test
    fun policyIsRecheckedWhenTheSettledViewportIsSubmitted() = runTest {
        val requested = mutableListOf<Int>()
        var allowed = false
        val coordinator = CommentsPreloadCoordinator(
            scope = this,
            loadFilteredUsers = { emptySet() },
            preloadAllowed = { allowed },
            isPrepared = { _, _, _, _ -> false },
            preload = { _, storyId, _, _ -> requested += storyId },
            scrollSettleDelayMillis = 100L,
            preloadDispatcher = StandardTestDispatcher(testScheduler),
        )
        coordinator.setEnabled(true)

        coordinator.updateVisibleStories(listOf(story(1)))
        advanceTimeBy(100L.milliseconds)
        runCurrent()
        assertEquals(emptyList(), requested)

        allowed = true
        coordinator.updateVisibleStories(listOf(story(1)))
        advanceTimeBy(100L.milliseconds)
        runCurrent()
        assertEquals(listOf(1), requested)
    }

    @Test
    fun policyIsRecheckedWhenAQueuedPreloadGetsAConcurrencySlot() = runTest {
        val firstPreloadStarted = CompletableDeferred<Unit>()
        val releaseFirstPreload = CompletableDeferred<Unit>()
        val requested = mutableListOf<Int>()
        var allowed = true
        val coordinator = CommentsPreloadCoordinator(
            scope = this,
            loadFilteredUsers = { emptySet() },
            preloadAllowed = { allowed },
            isPrepared = { _, _, _, _ -> false },
            preload = { _, storyId, _, _ ->
                requested += storyId
                if (storyId == 1) {
                    firstPreloadStarted.complete(Unit)
                    releaseFirstPreload.await()
                }
            },
            maxConcurrentPreloads = 1,
            scrollSettleDelayMillis = 0L,
            preloadDispatcher = StandardTestDispatcher(testScheduler),
        )
        coordinator.setEnabled(true)
        coordinator.updateVisibleStories(listOf(story(1), story(2)))
        runCurrent()
        firstPreloadStarted.await()

        allowed = false
        releaseFirstPreload.complete(Unit)
        runCurrent()

        assertEquals(listOf(1), requested)
    }

    @Test
    fun selectedCommentsProviderIsUsedForPreparedChecksAndPreloading() = runTest {
        val checkedSources = mutableListOf<CommentThreadSource>()
        val requestedSources = mutableListOf<CommentThreadSource>()
        val coordinator = CommentsPreloadCoordinator(
            scope = this,
            loadFilteredUsers = { emptySet() },
            preloadSource = { CommentThreadSource.OFFICIAL },
            isPrepared = { source, _, _, _ ->
                checkedSources += source
                false
            },
            preload = { source, _, _, _ -> requestedSources += source },
            scrollSettleDelayMillis = 0L,
            preloadDispatcher = StandardTestDispatcher(testScheduler),
        )

        coordinator.setEnabled(true)
        coordinator.updateVisibleStories(listOf(story(1)))
        runCurrent()

        assertEquals(listOf(CommentThreadSource.OFFICIAL), checkedSources)
        assertEquals(listOf(CommentThreadSource.OFFICIAL), requestedSources)
    }

    private fun story(
        id: Int,
        kids: List<Int> = listOf(id * 10),
        descendants: Int = kids.size,
        loaded: Boolean = true,
        isComment: Boolean = false,
    ) = StoryListItemSnapshot(
        story = StorySnapshot(
            id = id,
            childIds = kids,
            descendantCount = descendants,
            isComment = isComment,
        ),
        presentation = StoryPresentationSnapshot(loaded = loaded),
    )
}
