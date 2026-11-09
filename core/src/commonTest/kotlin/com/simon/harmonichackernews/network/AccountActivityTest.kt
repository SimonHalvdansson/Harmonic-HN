package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.TestKeyValueStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountActivityTest {
    private fun reply(id: Int, kind: AccountActivityKind = AccountActivityKind.Reply, story: Int = 10) =
        HackerNewsReply(id, 20, "alice", "A reply", time = 1_800_000_000, storyId = story, storyTitle = "A discussion", kind = kind)

    @Test fun historyIsBoundedDeduplicatedPersistentAndAccountScoped() {
        val store = TestKeyValueStore()
        val repository = AccountActivityRepository(store)
        repository.record("Simon", ReplyScanResult((1..120).map { reply(it) }, 200), 1000, historical = true)
        repository.record("Simon", ReplyScanResult(listOf(reply(120), reply(121)), 201), 2000, historical = false)
        val snapshot = AccountActivityRepository(store).snapshot("Simon")
        assertEquals(100, snapshot.entries.size)
        assertEquals(121, snapshot.entries.first().reply.id)
        assertFalse(snapshot.entries.first().read)
        assertTrue(snapshot.entries.single { it.reply.id == 120 }.read)
        assertEquals(2000, snapshot.lastCheckedMillis)
        assertEquals(201, snapshot.checkpoint)
        assertTrue(repository.snapshot("simon").entries.isEmpty())
    }

    @Test fun readAndMuteStateSurvivesRefreshAndRepositoryRecreation() {
        val store = TestKeyValueStore()
        val repository = AccountActivityRepository(store)
        repository.record("me", ReplyScanResult(listOf(reply(1), reply(2, story = 11)), 3), 1000, false)
        repository.markRead("me", 1)
        repository.setMuted("me", 10, "A discussion", true)
        repository.record("me", ReplyScanResult(listOf(reply(1), reply(3)), 4), 2000, false)
        val restored = AccountActivityRepository(store)
        assertTrue(restored.snapshot("me").entries.single { it.reply.id == 1 }.read)
        assertFalse(restored.shouldAlert("me", reply(3)))
        assertTrue(restored.shouldAlert("me", reply(4, story = 11)))
        assertEquals(3, restored.snapshot("me").entries.size)
        restored.setMuted("me", 10, "A discussion", false)
        assertTrue(restored.shouldAlert("me", reply(3)))
        restored.markRead("me")
        assertTrue(restored.snapshot("me").entries.all { it.read })
    }

    @Test fun alertTypesFilterIndependentlyWithoutRemovingHistory() {
        val repository = AccountActivityRepository(TestKeyValueStore())
        val comment = reply(1)
        val submission = reply(2, AccountActivityKind.SubmissionComment)
        repository.record("me", ReplyScanResult(listOf(comment, submission), 3), 1000, false)
        repository.setAlerts("me", AccountAlertPreferences(replies = false))
        assertFalse(repository.shouldAlert("me", comment))
        assertTrue(repository.shouldAlert("me", submission))
        repository.setAlerts("me", AccountAlertPreferences(submissionComments = false))
        assertTrue(repository.shouldAlert("me", comment))
        assertFalse(repository.shouldAlert("me", submission))
        assertEquals(2, repository.snapshot("me").entries.size)
    }

    @Test fun checkingHoursHandleDaytimeAndOvernightBoundaries() {
        val day = AccountAlertPreferences(limitedHours = true)
        assertFalse(day.allowsChecking(479))
        assertTrue(day.allowsChecking(480))
        assertTrue(day.allowsChecking(1319))
        assertFalse(day.allowsChecking(1320))
        val night = day.copy(startMinute = 22 * 60, endMinute = 8 * 60)
        assertTrue(night.allowsChecking(23 * 60))
        assertTrue(night.allowsChecking(7 * 60))
        assertFalse(night.allowsChecking(8 * 60))
        assertTrue(day.copy(limitedHours = false).allowsChecking(0))
    }

    @Test fun foregroundLoadsHistorySilentlyAndChecksWhenBackgroundIsOff() = runTest {
        val scanner = Scanner()
        val platform = Platform()
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, TestKeyValueStore()), platform)
        scanner.result = ReplyScanResult(listOf(reply(10)), 100)
        assertIs<ReplyCheckResult.Success>(runtime.refreshActivity("me"))
        assertTrue(runtime.activity.snapshot("me").entries.single().read)
        scanner.result = ReplyScanResult(listOf(reply(101)), 150)
        runtime.refreshActivity("me")
        assertEquals(listOf(100), scanner.checkpoints)
        assertFalse(runtime.activity.snapshot("me").entries.first().read)
        assertTrue(platform.published.isEmpty())
        assertFalse(runtime.isEnabled)
    }

    @Test fun foregroundActivityWorksWithoutAnyNativeNotificationImplementation() = runTest {
        val scanner = Scanner().apply { result = ReplyScanResult(listOf(reply(10)), 100) }
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, TestKeyValueStore()), ReplyNotificationPlatform.Unavailable)
        val settings = com.simon.harmonichackernews.presentation.NotificationsSettingsRuntime("me", runtime)
        assertTrue(settings.state.value.supported)
        assertFalse(settings.state.value.backgroundSupported)
        settings.refreshActivity()
        assertEquals(listOf(10), settings.state.value.activity.entries.map { it.reply.id })
        assertIs<ReplySubscriptionResult.Failed>(runtime.enable("me"))
        assertEquals(ReplyCheckResult.Disabled, runtime.checkInBackground())
    }

    @Test fun debugPreviewChangesTheActivityScopeWithoutChangingTheRealAccount() = runTest {
        val identity = com.simon.harmonichackernews.presentation.DebugAccountIdentity(TestKeyValueStore(), true)
        val realAccount = "me"
        val realSession = Any()
        val scanner = Scanner()
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, TestKeyValueStore()), Platform())
        runtime.bindAccountScope({ identity.state.value.takeIf { it.username != null } ?: realSession },
            { identity.state.value.username ?: realAccount })
        identity.setUsername("Alice")
        assertIs<ReplyCheckResult.Success>(runtime.refreshActivity("Alice"))
        assertEquals(ReplyCheckResult.Disabled, runtime.refreshActivity("me"))
        identity.setUsername(null)
        assertIs<ReplyCheckResult.Success>(runtime.refreshActivity("me"))
        assertEquals("me", realAccount)
    }

    @Test fun backgroundStoresAllActivityButOnlyPublishesAllowedAlerts() = runTest {
        val scanner = Scanner()
        val platform = Platform()
        val store = TestKeyValueStore(mapOf(ReplyNotificationKeys.USERNAME to "me", ReplyNotificationKeys.LAST_SEEN_ITEM_ID to "100"))
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, store), platform,
            presentationDispatcher = UnconfinedTestDispatcher(testScheduler), currentMinutesFromMidnight = { 12 * 60 })
        runtime.setAlertPreferences("me", AccountAlertPreferences(submissionComments = false, wifiOnly = true, limitedHours = true))
        runtime.activity.setMuted("me", 11, "Muted discussion", true)
        scanner.result = ReplyScanResult(listOf(reply(101), reply(102, AccountActivityKind.SubmissionComment), reply(103, story = 11)), 150)
        runtime.checkInBackground()
        assertEquals(listOf(101), platform.published.single().notifications.map { it.id })
        assertEquals(3, runtime.activity.snapshot("me").entries.size)
        assertTrue(platform.schedule!!.wifiOnly)
        runtime.disable()
        assertEquals(3, runtime.activity.snapshot("me").entries.size)
    }

    @Test fun outsideCheckingHoursDoesNotAdvanceCheckpointAndManualRefreshStillWorks() = runTest {
        val scanner = Scanner()
        val platform = Platform()
        val store = TestKeyValueStore(mapOf(ReplyNotificationKeys.USERNAME to "me", ReplyNotificationKeys.LAST_SEEN_ITEM_ID to "100"))
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, store), platform, currentMinutesFromMidnight = { 23 * 60 })
        runtime.setAlertPreferences("me", AccountAlertPreferences(limitedHours = true))
        assertEquals(9 * 60 * 60_000L, platform.schedule?.delayMillis)
        assertEquals(ReplyCheckResult.Disabled, runtime.checkInBackground())
        assertTrue(scanner.checkpoints.isEmpty())
        assertEquals("100", store.getString(ReplyNotificationKeys.LAST_SEEN_ITEM_ID))
        runtime.refreshActivity("me")
        assertTrue(runtime.activity.snapshot("me").initialized)
    }

    @Test fun dailyChecksOutsideTheWindowAreDeferredToTheNextOpening() = runTest {
        var minute = 23 * 60
        val platform = Platform()
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(Scanner(), TestKeyValueStore()), platform,
            currentMinutesFromMidnight = { minute })
        runtime.setCheckFrequency(com.simon.harmonichackernews.settings.ReplyNotificationFrequency.ONE_DAY)
        runtime.setAlertPreferences("me", AccountAlertPreferences(limitedHours = true))
        runtime.enable("me")
        assertEquals(9 * 60 * 60_000L, platform.schedule?.delayMillis)
        minute = 8 * 60
        runtime.rescheduleChecks()
        assertNull(platform.schedule?.delayMillis)
        assertEquals(24 * 60 * 60_000L, platform.schedule?.intervalMillis)
        minute = 23 * 60
        runtime.rescheduleChecks()
        assertEquals(9 * 60 * 60_000L, platform.schedule?.delayMillis)
    }

    @Test fun firstVisitBackfillsOlderRepliesAndKeepsNewBackgroundRepliesUnread() = runTest {
        val scanner = Scanner()
        val store = TestKeyValueStore(mapOf(ReplyNotificationKeys.USERNAME to "me", ReplyNotificationKeys.LAST_SEEN_ITEM_ID to "100"))
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, store), Platform())
        scanner.result = ReplyScanResult(listOf(reply(110)), 150)
        runtime.checkNow()
        assertFalse(runtime.activity.snapshot("me").historyLoaded)
        scanner.result = ReplyScanResult(listOf(reply(110), reply(90)), 150)
        runtime.refreshActivity("me")
        val snapshot = runtime.activity.snapshot("me")
        assertTrue(snapshot.historyLoaded)
        assertFalse(snapshot.entries.single { it.reply.id == 110 }.read)
        assertTrue(snapshot.entries.single { it.reply.id == 90 }.read)
    }

    @Test fun reenablingCatchesUpHistoryWithoutAlertingAboutTheDisabledPeriod() = runTest {
        val scanner = Scanner()
        val platform = Platform()
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, TestKeyValueStore()), platform)
        scanner.result = ReplyScanResult(listOf(reply(50)), 50)
        runtime.refreshActivity("me")
        runtime.enable("me") // The new alert baseline is 100; history still needs 51..100.
        scanner.result = ReplyScanResult(listOf(reply(70), reply(110)), 150)
        runtime.checkNow()
        assertEquals(listOf(50), scanner.checkpoints)
        assertEquals(listOf(110), platform.published.single().notifications.map { it.id })
        assertEquals(listOf(110, 70, 50), runtime.activity.snapshot("me").entries.map { it.reply.id })
    }

    @Test fun accountChangeDiscardsAnInflightForegroundScan() = runTest {
        var session: Any = Any()
        var username = "me"
        val scanner = Scanner()
        val pending = CompletableDeferred<ReplyScanResult>()
        scanner.pending = pending
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(scanner, TestKeyValueStore()), Platform())
        runtime.bindAccountScope({ session }, { username })
        val request = async(start = CoroutineStart.UNDISPATCHED) { runtime.refreshActivity("me") }
        session = Any()
        username = "other"
        pending.complete(ReplyScanResult(listOf(reply(1)), 100))
        assertEquals(ReplyCheckResult.Disabled, request.await())
        assertTrue(runtime.activity.snapshot("me").entries.isEmpty())
        assertTrue(runtime.activity.snapshot("other").entries.isEmpty())
    }

    @Test fun debugCanFollowAPublicUsernameWhileAccountActivityRemainsScoped() = runTest {
        val runtime = ReplyNotificationRuntime(ReplyNotificationUseCase(Scanner(), TestKeyValueStore()), Platform())
        val session = Any()
        runtime.bindAccountScope({ session }, { "me" })
        assertEquals(ReplySubscriptionResult.Superseded, runtime.enable("other"))
        assertIs<ReplySubscriptionResult.Enabled>(runtime.enable("other", accountScoped = false))
        assertFalse(runtime.accountScoped)
        assertIs<ReplyCheckResult.Success>(runtime.checkInBackground())
        assertEquals(ReplyCheckResult.Disabled, runtime.refreshActivity("other"))
    }

    private class Scanner : ReplyScanner {
        var result = ReplyScanResult(emptyList(), 100)
        var pending: CompletableDeferred<ReplyScanResult>? = null
        val checkpoints = mutableListOf<Int>()
        override suspend fun initialize(username: String) = ReplySubscriptionBaseline(username, 100)
        override suspend fun recentReplies(username: String) = pending?.await() ?: result
        override suspend fun scan(username: String, previousLastSeenItemId: Int): ReplyScanResult {
            checkpoints += previousLastSeenItemId
            return pending?.await() ?: result
        }
        override suspend fun findLatestReply(username: String) = LatestReplyResult(null, true)
    }

    private class Platform : ReplyNotificationPlatform {
        var schedule: ReplyNotificationSchedule? = null
        val published = mutableListOf<ReplyNotificationBatch>()
        override fun prepareNotifications() = Unit
        override fun scheduleChecks(schedule: ReplyNotificationSchedule) { this.schedule = schedule }
        override fun cancelChecks() = Unit
        override suspend fun publish(batch: ReplyNotificationBatch) { if (batch.notifications.isNotEmpty()) published += batch }
    }
}
