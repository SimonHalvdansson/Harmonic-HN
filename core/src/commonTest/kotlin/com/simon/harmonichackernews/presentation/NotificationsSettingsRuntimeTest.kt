package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.LatestReplyResult
import com.simon.harmonichackernews.network.ReplyNotificationBatch
import com.simon.harmonichackernews.network.ReplyNotificationKeys
import com.simon.harmonichackernews.network.ReplyNotificationPlatform
import com.simon.harmonichackernews.network.ReplyNotificationRuntime
import com.simon.harmonichackernews.network.ReplyNotificationSchedule
import com.simon.harmonichackernews.network.ReplyNotificationUseCase
import com.simon.harmonichackernews.network.ReplyScanResult
import com.simon.harmonichackernews.network.ReplyScanner
import com.simon.harmonichackernews.network.ReplySubscriptionBaseline
import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsSettingsRuntimeTest {
    @Test
    fun anotherCaseIdentityCannotReadOrDisableTheActiveSubscription() = runTest {
        val store = TestKeyValueStore(mapOf(ReplyNotificationKeys.USERNAME to "Alice"))
        var checks = 0
        var cancellations = 0
        val scanner = object : ReplyScanner {
            override suspend fun initialize(username: String): ReplySubscriptionBaseline? = error("Unused")
            override suspend fun scan(username: String, previousLastSeenItemId: Int): ReplyScanResult {
                checks++
                return ReplyScanResult(emptyList(), 100)
            }
            override suspend fun findLatestReply(username: String): LatestReplyResult = error("Unused")
        }
        val platform = object : ReplyNotificationPlatform {
            override fun prepareNotifications() = Unit
            override fun scheduleChecks(schedule: ReplyNotificationSchedule) = Unit
            override fun cancelChecks() { cancellations++ }
            override suspend fun publish(batch: ReplyNotificationBatch) = Unit
        }
        val notifications = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, store), platform)
        val exact = NotificationsSettingsRuntime("Alice", notifications)
        val different = NotificationsSettingsRuntime("alice", notifications)
        assertTrue(exact.state.value.enabled)
        assertFalse(different.state.value.enabled)
        different.checkNow()
        different.disable()
        assertEquals(0, checks)
        assertEquals(0, cancellations)
        assertEquals("Alice", notifications.configuredUsername)
        exact.disable()
        assertFalse(exact.state.value.enabled)
        assertEquals(1, cancellations)
        assertEquals("", notifications.configuredUsername)
    }
}
