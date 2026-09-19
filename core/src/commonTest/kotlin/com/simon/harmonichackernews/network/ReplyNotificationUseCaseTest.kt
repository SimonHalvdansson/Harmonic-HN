package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReplyNotificationUseCaseTest {
    @Test
    fun enablingNormalizesTheUsernameAndPersistsTheServerBaseline() = runTest {
        val scanner = FakeReplyScanner().apply {
            baseline = ReplySubscriptionBaseline("Simon", 123)
        }
        val store = TestKeyValueStore()
        val useCase = ReplyNotificationUseCase(scanner, store)

        val result = useCase.enable("  Simon  ")

        assertEquals(ReplySubscriptionResult.Enabled("Simon"), result)
        assertEquals("Simon", useCase.configuredUsername)
        assertEquals("123", store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
        assertEquals("Simon", scanner.initializedUsername)
        assertTrue(useCase.isEnabled)
    }

    @Test
    fun successfulChecksAdvanceTheCheckpointEvenWhenThereAreNoReplies() = runTest {
        val store = TestKeyValueStore(
            mapOf(
                ReplyNotificationKeys.USERNAME to "simon",
                ReplyNotificationKeys.LAST_SEEN_ITEM_ID to "100",
            ),
        )
        val scanner = FakeReplyScanner().apply {
            scanResult = ReplyScanResult(emptyList(), 150)
        }
        val useCase = ReplyNotificationUseCase(scanner, store)

        val result = useCase.check()

        assertIs<ReplyCheckResult.Success>(result)
        assertEquals(100, scanner.previousLastSeenItemId)
        assertEquals("150", store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
    }

    @Test
    fun missingUsersDoNotOverwriteTheExistingCheckpoint() = runTest {
        val store = TestKeyValueStore(
            mapOf(
                ReplyNotificationKeys.USERNAME to "missing",
                ReplyNotificationKeys.LAST_SEEN_ITEM_ID to "100",
            ),
        )
        val scanner = FakeReplyScanner().apply {
            scanResult = ReplyScanResult(emptyList(), 999, userFound = false)
        }
        val useCase = ReplyNotificationUseCase(scanner, store)

        assertEquals(ReplyCheckResult.UserNotFound, useCase.check())
        assertEquals("100", store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
    }

    @Test
    fun disablingClearsTheSubscriptionAndCheckpoint() {
        val store = TestKeyValueStore(
            mapOf(
                ReplyNotificationKeys.USERNAME to "simon",
                ReplyNotificationKeys.LAST_SEEN_ITEM_ID to "100",
            ),
        )
        val useCase = ReplyNotificationUseCase(FakeReplyScanner(), store)

        useCase.disable()

        assertFalse(useCase.isEnabled)
        assertEquals("0", store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
    }

    @Test
    fun disablingWhileCheckingDiscardsTheResultAndDoesNotPublish() = runTest {
        val completedScan = CompletableDeferred<ReplyScanResult>()
        val scanner = FakeReplyScanner().apply { pendingScan = completedScan }
        val store = subscribedStore()
        val platform = RecordingNotificationPlatform()
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, store), platform)
        val checking = async(start = CoroutineStart.UNDISPATCHED) { runtime.checkNow() }

        runtime.disable()
        completedScan.complete(ReplyScanResult(listOf(HackerNewsReply(150, 1, "alice", "Reply")), 150))

        assertEquals(ReplyCheckResult.Disabled, checking.await())
        assertEquals("0", store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
        assertTrue(platform.published.isEmpty())
    }

    @Test
    fun reenablingTheSameUsernameDuringACheckPreservesTheNewBaseline() = runTest {
        val completedScan = CompletableDeferred<ReplyScanResult>()
        val scanner = FakeReplyScanner().apply {
            pendingScan = completedScan
            baseline = ReplySubscriptionBaseline("simon", 200)
        }
        val store = subscribedStore()
        val useCase = ReplyNotificationUseCase(scanner, store)
        val checking = async(start = CoroutineStart.UNDISPATCHED) { useCase.check() }

        useCase.disable()
        assertIs<ReplySubscriptionResult.Enabled>(useCase.enable("simon"))
        completedScan.complete(ReplyScanResult(listOf(HackerNewsReply(150, 1, "alice", "Reply")), 150))

        assertEquals(ReplyCheckResult.Disabled, checking.await())
        assertEquals("200", store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
    }

    @Test
    fun concurrentChecksUseTheCheckpointFromThePreviousCompletedScan() = runTest {
        val completedScan = CompletableDeferred<ReplyScanResult>()
        val scanner = FakeReplyScanner().apply { pendingScan = completedScan }
        val store = subscribedStore()
        val useCase = ReplyNotificationUseCase(scanner, store)
        val first = async(start = CoroutineStart.UNDISPATCHED) { useCase.check() }
        val second = async(start = CoroutineStart.UNDISPATCHED) { useCase.check() }

        assertEquals(listOf(100), scanner.checkpoints)
        scanner.pendingScan = null
        scanner.scanResult = ReplyScanResult(emptyList(), 200)
        completedScan.complete(ReplyScanResult(emptyList(), 150))

        assertIs<ReplyCheckResult.Success>(first.await())
        assertIs<ReplyCheckResult.Success>(second.await())
        assertEquals(listOf(100, 150), scanner.checkpoints)
        assertEquals("200", store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
    }

    @Test
    fun disablingWhileEnablingDoesNotRestoreOrScheduleTheSubscription() = runTest {
        val initialized = CompletableDeferred<ReplySubscriptionBaseline?>()
        val scanner = FakeReplyScanner().apply { pendingInitialization = initialized }
        val store = TestKeyValueStore()
        val platform = RecordingNotificationPlatform()
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, store), platform)
        val enabling = async(start = CoroutineStart.UNDISPATCHED) { runtime.enable("simon") }

        runtime.disable()
        initialized.complete(ReplySubscriptionBaseline("simon", 200))

        assertEquals(ReplySubscriptionResult.Superseded, enabling.await())
        assertFalse(runtime.isEnabled)
        assertEquals("0", store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
        assertEquals(0, platform.scheduledChecks)
    }

    @Test
    fun olderEnableResultCannotReplaceANewerSubscription() = runTest {
        val initialized = CompletableDeferred<ReplySubscriptionBaseline?>()
        val scanner = FakeReplyScanner().apply { pendingInitialization = initialized }
        val store = TestKeyValueStore()
        val useCase = ReplyNotificationUseCase(scanner, store)
        val first = async(start = CoroutineStart.UNDISPATCHED) { useCase.enable("simon") }

        scanner.pendingInitialization = null
        scanner.baseline = ReplySubscriptionBaseline("alice", 300)
        assertIs<ReplySubscriptionResult.Enabled>(useCase.enable("alice"))
        initialized.complete(ReplySubscriptionBaseline("simon", 200))

        assertEquals(ReplySubscriptionResult.Superseded, first.await())
        assertEquals("alice", useCase.configuredUsername)
        assertEquals("300", store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
    }

    private fun subscribedStore() = TestKeyValueStore(
        mapOf(
            ReplyNotificationKeys.USERNAME to "simon",
            ReplyNotificationKeys.LAST_SEEN_ITEM_ID to "100",
        ),
    )

    private class RecordingNotificationPlatform : ReplyNotificationPlatform {
        val published = mutableListOf<ReplyNotificationBatch>()
        var scheduledChecks = 0
        override fun prepareNotifications() = Unit
        override fun scheduleChecks(schedule: ReplyNotificationSchedule) { scheduledChecks++ }
        override fun cancelChecks() = Unit
        override fun publish(batch: ReplyNotificationBatch) { published += batch }
    }

    private class FakeReplyScanner : ReplyScanner {
        var baseline: ReplySubscriptionBaseline? = null
        var scanResult = ReplyScanResult(emptyList(), 0)
        var latestResult = LatestReplyResult(null, true)
        var initializedUsername: String? = null
        var previousLastSeenItemId: Int? = null
        var pendingScan: CompletableDeferred<ReplyScanResult>? = null
        var pendingInitialization: CompletableDeferred<ReplySubscriptionBaseline?>? = null
        val checkpoints = mutableListOf<Int>()

        override suspend fun initialize(username: String): ReplySubscriptionBaseline? {
            initializedUsername = username
            return pendingInitialization?.await() ?: baseline
        }

        override suspend fun scan(
            username: String,
            previousLastSeenItemId: Int,
        ): ReplyScanResult {
            this.previousLastSeenItemId = previousLastSeenItemId
            checkpoints += previousLastSeenItemId
            return pendingScan?.await() ?: scanResult
        }

        override suspend fun findLatestReply(username: String): LatestReplyResult = latestResult
    }
}
