package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.network.*
import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class IosReplyNotificationChecksTest {
    private class Fixture {
        val store = TestKeyValueStore(mapOf(ReplyNotificationKeys.USERNAME to "alice", ReplyNotificationKeys.LAST_SEEN_ITEM_ID to "100"))
        var username: String? = "alice"
        var scans = 0
        var schedules = 0
        var cancellations = 0
        var published = 0
        var minute = 12 * 60
        var waitForScan: CompletableDeferred<Unit>? = null
        var failure = false
        val scanner = object : ReplyScanner {
            override suspend fun initialize(username: String) = ReplySubscriptionBaseline(username, 100)
            override suspend fun scan(username: String, previousLastSeenItemId: Int): ReplyScanResult {
                scans++
                waitForScan?.await()
                if (failure) error("Network unavailable")
                return ReplyScanResult(listOf(HackerNewsReply(150, 1, "bob", "A fixture reply")), 150)
            }
            override suspend fun findLatestReply(username: String) = LatestReplyResult(null, true)
        }
        val platform = object : ReplyNotificationPlatform {
            override fun prepareNotifications() = Unit
            override fun scheduleChecks(schedule: ReplyNotificationSchedule) { schedules++ }
            override fun cancelChecks() { cancellations++ }
            override suspend fun publish(batch: ReplyNotificationBatch) { published += batch.notifications.size }
        }
    }

    @Test fun checkWaitsForAccountThenPersistsAndDoesNotRepeatAlerts() = runTest {
        val f = Fixture()
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(f.scanner, f.store), f.platform,
            UnconfinedTestDispatcher(testScheduler))
        val loaded = CompletableDeferred<Unit>()
        val persisted = CompletableDeferred<Unit>()
        val completions = mutableListOf<Boolean>()
        val checks = IosReplyNotificationChecks(runtime, { loaded.await() }, { f.username }, { persisted.await() }, this)
        checks.start(true, true, completion = completions::add)
        assertEquals(0, f.scans)
        loaded.complete(Unit)
        runCurrent()
        assertEquals(1, f.published)
        assertTrue(completions.isEmpty())
        persisted.complete(Unit)
        runCurrent()
        assertEquals(listOf(true), completions)
        checks.start(true, true, completion = completions::add)
        runCurrent()
        assertEquals(1, f.published)
        assertEquals("150", f.store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
    }

    @Test fun accountChangeDisablesOldSubscriptionBeforeScanning() = runTest {
        val f = Fixture().apply { username = "another" }
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(f.scanner, f.store), f.platform)
        IosReplyNotificationChecks(runtime, {}, { f.username }, {}, this).start(true, true) { assertTrue(it) }
        runCurrent()
        assertFalse(runtime.isEnabled)
        assertEquals(0, f.scans)
        assertTrue(f.cancellations > 0)
    }

    @Test fun wifiAndCheckingHoursSkipScanningButRetainNextWakeup() = runTest {
        val f = Fixture()
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(f.scanner, f.store), f.platform,
            UnconfinedTestDispatcher(testScheduler), { f.minute })
        runtime.setAlertPreferences("alice", AccountAlertPreferences(wifiOnly = true))
        val checks = IosReplyNotificationChecks(runtime, {}, { f.username }, {}, this)
        checks.start(true, false) { assertTrue(it) }
        assertEquals(0, f.scans)
        runtime.setAlertPreferences("alice", AccountAlertPreferences(limitedHours = true, startMinute = 8 * 60, endMinute = 10 * 60))
        checks.start(true, true) { assertTrue(it) }
        assertEquals(0, f.scans)
        assertTrue(f.schedules >= 2)
    }

    @Test fun expirationCancelsScannerWithoutAdvancingCheckpointAndReportsOnce() = runTest {
        val f = Fixture().apply { waitForScan = CompletableDeferred() }
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(f.scanner, f.store), f.platform,
            UnconfinedTestDispatcher(testScheduler))
        val completions = mutableListOf<Boolean>()
        var persisted = false
        val task = IosReplyNotificationChecks(runtime, {}, { f.username }, { persisted = true }, this)
            .start(true, true, completion = completions::add)
        task.cancel()
        f.waitForScan!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(false), completions)
        assertTrue(persisted)
        assertEquals(0, f.published)
        assertEquals("100", f.store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
        assertEquals(1, f.schedules)
    }

    @Test fun networkFailureReportsFailureAndStillSchedulesNextCheck() = runTest {
        val f = Fixture().apply { failure = true }
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(f.scanner, f.store), f.platform)
        val result = mutableListOf<Boolean>()
        IosReplyNotificationChecks(runtime, {}, { f.username }, {}, this).start(true, true, completion = result::add)
        assertEquals(listOf(false), result)
        assertEquals(1, f.schedules)
        assertEquals("100", f.store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
    }

    @Test fun foregroundReschedulingDoesNotFetchReplies() = runTest {
        val f = Fixture()
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(f.scanner, f.store), f.platform)
        IosReplyNotificationChecks(runtime, {}, { f.username }, {}, this).start(false, true) { assertTrue(it) }
        assertEquals(1, f.schedules)
        assertEquals(0, f.scans)
    }

    @Test fun expiredShortScanCanFinishWithTheLongerProcessingBudget() = runTest {
        val f = Fixture().apply { waitForScan = CompletableDeferred() }
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(f.scanner, f.store), f.platform,
            UnconfinedTestDispatcher(testScheduler))
        val results = mutableListOf<Boolean>()
        val checks = IosReplyNotificationChecks(runtime, {}, { f.username }, {}, this)
        checks.start(true, true, 25_000, results::add)
        advanceTimeBy(25_000)
        runCurrent()
        assertEquals(listOf(false), results)
        assertEquals("100", f.store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
        checks.start(true, true, 240_000, results::add)
        advanceTimeBy(60_000)
        f.waitForScan!!.complete(Unit)
        runCurrent()
        assertEquals(listOf(false, true), results)
        assertEquals(1, f.published)
        assertEquals("150", f.store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
    }
}
