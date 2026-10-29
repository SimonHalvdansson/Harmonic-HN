package com.simon.harmonichackernews.widget

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetWorkLockTest {
    @Test
    fun resizeCompletesWhileRefreshIsWaitingForNetwork() = runBlocking {
        val releaseRefresh = CompletableDeferred<Unit>()
        val refresh = launch(start = CoroutineStart.UNDISPATCHED) {
            withWidgetStorage(1) { releaseRefresh.await() }
        }
        try {
            assertEquals("resized", withTimeout(1_000) { withWidgetRender(1) { "resized" } })
            assertFalse(refresh.isCompleted)
        } finally {
            releaseRefresh.complete(Unit)
            refresh.join()
        }
    }

    @Test
    fun cleanupWaitsForBothRefreshAndResize() = runBlocking {
        val releaseRefresh = CompletableDeferred<Unit>()
        val releaseResize = CompletableDeferred<Unit>()
        val refresh = launch(start = CoroutineStart.UNDISPATCHED) {
            withWidgetStorage(2) { releaseRefresh.await() }
        }
        val resize = launch(start = CoroutineStart.UNDISPATCHED) {
            withWidgetRender(2) { releaseResize.await() }
        }
        val cleanup = async(start = CoroutineStart.UNDISPATCHED) {
            withWidgetStorage(2) { withWidgetRender(2) { "cleaned" } }
        }
        try {
            assertFalse(cleanup.isCompleted)
            releaseRefresh.complete(Unit)
            refresh.join()
            assertFalse(cleanup.isCompleted)
            releaseResize.complete(Unit)
            resize.join()
            assertEquals("cleaned", withTimeout(1_000) { cleanup.await() })
        } finally {
            releaseRefresh.complete(Unit)
            releaseResize.complete(Unit)
        }
    }
}
