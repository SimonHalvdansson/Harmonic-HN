package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.HackerNewsAccountRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class UserProfileRuntimeTest {
    @Test
    fun loadRetryAndAccountComparisonAreOwnedByRuntime() = runTest {
        var attempts = 0
        val runtime = runtime(
            loader = UserProfileLoader {
                attempts++
                if (attempts == 1) error("offline") else user("Alice")
            },
            account = HackerNewsAccount("alice", "secret"),
        )

        runtime.load()
        assertIs<UserProfileLoadState.Error>(runtime.state.value.loadState)

        runtime.retry()
        val loaded = assertIs<UserProfileLoadState.Loaded>(runtime.state.value.loadState)
        assertEquals("Alice", loaded.profile.id)
        assertTrue(runtime.state.value.ownProfile)
    }

    @Test
    fun blockAndNotificationOutcomesUpdatePortableState() = runTest {
        val blocks = FakeBlocks()
        val notifications = FakeNotifications()
        val runtime = runtime(blocks = blocks, notifications = notifications)

        val blocked = runtime.toggleBlocked()
        assertEquals(true, blocked?.blocked)
        assertEquals(true, blocked?.dismissProfile)
        assertTrue(runtime.state.value.blocked)
        assertEquals(blocked, runtime.state.value.blockOutcome)

        val unblocked = runtime.toggleBlocked()
        assertEquals("Unblocked alice", unblocked?.message)
        assertFalse(runtime.state.value.blocked)

        runtime.enableNotifications()
        assertTrue(runtime.state.value.notificationsActive)
        assertEquals("", runtime.state.value.notificationStatus)
        assertEquals(
            UserProfileNotificationOutcome.ENABLED,
            runtime.state.value.notificationOutcome,
        )

        runtime.disableNotifications()
        assertFalse(runtime.state.value.notificationsActive)
        assertTrue(notifications.disabled)
        assertEquals(
            UserProfileNotificationOutcome.DISABLED,
            runtime.state.value.notificationOutcome,
        )

        runtime.notificationPermissionDenied()
        assertEquals("Notification permission denied.", runtime.state.value.notificationStatus)
        assertEquals(
            UserProfileNotificationOutcome.PERMISSION_DENIED,
            runtime.state.value.notificationOutcome,
        )
    }

    @Test
    fun notificationFailureProducesStableOutcomeState() = runTest {
        val runtime = runtime(notifications = FakeNotifications(enableSucceeds = false))

        runtime.enableNotifications()

        assertFalse(runtime.state.value.notificationLoading)
        assertFalse(runtime.state.value.notificationsActive)
        assertEquals(
            "Could not activate reply notifications.",
            runtime.state.value.notificationStatus,
        )
        assertEquals(
            UserProfileNotificationOutcome.ENABLE_FAILED,
            runtime.state.value.notificationOutcome,
        )
    }

    private fun runtime(
        loader: UserProfileLoader = UserProfileLoader { user(it) },
        account: HackerNewsAccount? = null,
        blocks: FakeBlocks = FakeBlocks(),
        notifications: FakeNotifications = FakeNotifications(),
    ) = UserProfileRuntime(
        username = "alice",
        monthNames = MONTHS,
        loader = loader,
        accounts = FakeAccounts(account),
        blocks = blocks,
        notifications = notifications,
    )

    private fun user(id: String) = HackerNewsUserDto(
        id = id,
        created = 1_169_856_000L,
        karma = 10,
    )

    private class FakeAccounts(private var account: HackerNewsAccount?) :
        HackerNewsAccountRepository {
        override fun load(): HackerNewsAccount? = account
        override fun save(account: HackerNewsAccount): Boolean {
            this.account = account
            return true
        }
        override fun clear(): Boolean {
            account = null
            return true
        }
    }

    private class FakeBlocks : UserProfileBlockPort {
        private val blocked = mutableSetOf<String>()
        override fun isBlocked(username: String): Boolean = username.lowercase() in blocked
        override fun setBlocked(username: String, blocked: Boolean): Boolean {
            if (blocked) this.blocked += username.lowercase() else this.blocked -= username.lowercase()
            return true
        }
    }

    private class FakeNotifications(private val enableSucceeds: Boolean = true) :
        UserProfileNotificationPort {
        private var configured: String? = null
        var disabled = false
        override fun configuredUsername(): String? = configured
        override suspend fun enable(username: String): Boolean {
            if (enableSucceeds) configured = username
            return enableSucceeds
        }
        override fun disable() {
            disabled = true
            configured = null
        }
    }

    private companion object {
        val MONTHS = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
    }
}
