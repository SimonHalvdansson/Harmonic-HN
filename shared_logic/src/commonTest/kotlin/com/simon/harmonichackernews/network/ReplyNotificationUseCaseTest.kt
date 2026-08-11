package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.TestKeyValueStore
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

    private class FakeReplyScanner : ReplyScanner {
        var baseline: ReplySubscriptionBaseline? = null
        var scanResult = ReplyScanResult(emptyList(), 0)
        var latestResult = LatestReplyResult(null, true)
        var initializedUsername: String? = null
        var previousLastSeenItemId: Int? = null

        override suspend fun initialize(username: String): ReplySubscriptionBaseline? {
            initializedUsername = username
            return baseline
        }

        override suspend fun scan(
            username: String,
            previousLastSeenItemId: Int,
        ): ReplyScanResult {
            this.previousLastSeenItemId = previousLastSeenItemId
            return scanResult
        }

        override suspend fun findLatestReply(username: String): LatestReplyResult = latestResult
    }
}
