package com.simon.harmonichackernews.ui.stories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class StoryPreviewScrollWheelPagingTest {
    @Test
    fun continuousTrackpadEventsKeepAdvancingWithoutAnIdleGap() = runTest {
        val paging = StoryPreviewScrollWheelPagingState(this)
        var currentPage = 0
        repeat(41) {
            paging.page {
                delay(300)
                currentPage++
            }
            runCurrent()
            advanceTimeBy(50)
            runCurrent()
        }
        assertEquals(5, currentPage)
    }

    @Test
    fun burstEventsDoNotQueueOrOverlapAnimations() = runTest {
        val paging = StoryPreviewScrollWheelPagingState(this)
        val targets = mutableListOf<Int>()
        repeat(10) { target ->
            paging.page {
                targets += target
                delay(300)
            }
        }
        runCurrent()
        assertEquals(listOf(0), targets)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(listOf(0), targets)
    }

    @Test
    fun ignoredEventsDoNotExtendTheCooldownAfterAnimation() = runTest {
        val paging = StoryPreviewScrollWheelPagingState(this)
        val targets = mutableListOf<Int>()
        paging.page {
            targets += 1
            delay(300)
        }
        runCurrent()
        for (time in listOf(300L, 350L, 399L)) {
            advanceTimeBy(time - testScheduler.currentTime)
            runCurrent()
            paging.page { targets += 2 }
            runCurrent()
            assertEquals(listOf(1), targets)
        }
        advanceTimeBy(1)
        runCurrent()
        paging.page { targets += 2 }
        runCurrent()
        assertEquals(listOf(1, 2), targets)
    }

    @Test
    fun interruptedAnimationDoesNotLeavePagingLocked() = runTest {
        val paging = StoryPreviewScrollWheelPagingState(this)
        val targets = mutableListOf<Int>()
        paging.page {
            targets += 1
            throw CancellationException("Interrupted page animation")
        }
        runCurrent()
        paging.page { targets += 0 }
        runCurrent()
        assertEquals(listOf(1, 0), targets)
    }

    @Test
    fun disposingTheScopeCancelsAnimationAndPreventsFurtherPaging() = runTest {
        val sessionJob = Job(coroutineContext[Job])
        val scope = CoroutineScope(coroutineContext + sessionJob)
        val paging = StoryPreviewScrollWheelPagingState(scope)
        var completedPages = 0
        paging.page {
            delay(300)
            completedPages++
        }
        runCurrent()
        scope.cancel()
        advanceTimeBy(500)
        paging.page { completedPages++ }
        runCurrent()
        assertEquals(0, completedPages)
    }
}
