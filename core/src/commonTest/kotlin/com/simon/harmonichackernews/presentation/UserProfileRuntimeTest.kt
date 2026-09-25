package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.HackerNewsAccountState
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
        assertFalse(runtime.state.value.ownProfile)
    }

    @Test
    fun blockOutcomesUpdatePortableState() = runTest {
        val blocks = FakeBlocks()
        val runtime = runtime(blocks = blocks)

        val blocked = runtime.toggleBlocked()
        assertEquals(true, blocked?.blocked)
        assertEquals(true, blocked?.dismissProfile)
        assertTrue(runtime.state.value.blocked)
        assertEquals(blocked, runtime.state.value.blockOutcome)

        val unblocked = runtime.toggleBlocked()
        assertEquals("Unblocked alice", unblocked?.message)
        assertFalse(runtime.state.value.blocked)
    }

    @Test
    fun publicContentDoesNotWaitForIdentityAndControlsFollowAccountChanges() = runTest {
        val accounts = object : ObservableHackerNewsAccountRepository {
            override val accountState = MutableStateFlow<HackerNewsAccountState>(HackerNewsAccountState.Loading)
            override suspend fun saveAccount(account: HackerNewsAccount): Boolean = error("Unused")
            override suspend fun clearAccount(): Boolean = error("Unused")
        }
        val runtime = UserProfileRuntime("Alice", MONTHS, UserProfileLoader { user(it) }, accounts, FakeBlocks())
        val session = UserProfileSession(backgroundScope, runtime)
        session.start()
        runCurrent()
        assertIs<UserProfileLoadState.Loaded>(runtime.state.value.loadState)
        assertFalse(runtime.state.value.identityResolved)
        assertEquals(null, runtime.toggleBlocked())
        accounts.accountState.value = HackerNewsAccountState.LoggedIn(HackerNewsAccount("Alice", "secret"))
        runCurrent()
        assertTrue(runtime.state.value.ownProfile)
        assertEquals(null, runtime.toggleBlocked())
        accounts.accountState.value = HackerNewsAccountState.LoggedIn(HackerNewsAccount("alice", "secret"))
        runCurrent()
        assertFalse(runtime.state.value.ownProfile)
        assertTrue(runtime.state.value.identityResolved)
        session.dispose()
    }

    @Test
    fun staleProfileRemainsVisibleWhenRefreshFails() = runTest {
        val cached = user("alice")
        val loader = object : UserProfileLoader {
            override fun cached(username: String) = cached
            override suspend fun load(username: String): UserProfileData = error("Offline")
        }
        val runtime = runtime(loader)
        assertIs<UserProfileLoadState.Loaded>(runtime.state.value.loadState)
        runtime.load()
        assertIs<UserProfileLoadState.Loaded>(runtime.state.value.loadState)
        assertTrue(runtime.state.value.refreshFailed)
        assertFalse(runtime.state.value.refreshing)
    }

    @Test
    fun acceptedDialogOwnsTheSameSessionAcrossHandoffAndCancelsOnDismiss() = runTest {
        var requests = 0
        var cancelled = false
        val dialogs = UserProfileDialogs(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)) { scope, name ->
            UserProfileSession(scope, runtime(UserProfileLoader {
                requests++
                try { kotlinx.coroutines.awaitCancellation() } finally { cancelled = true }
            }))
        }
        val accepted = dialogs.open("alice")
        assertEquals(1, requests) // Starts at acceptance, before a dialog collector exists.
        assertTrue(dialogs.open("alice") === accepted)
        accepted.start()
        assertEquals(1, requests)
        dialogs.release(accepted)
        runCurrent()
        assertTrue(cancelled)
        accepted.retry()
        accepted.start()
        runCurrent()
        assertEquals(1, requests)
        dialogs.close()
    }

    @Test
    fun rapidSwitchRejectsLateFailureAndHandoffDoesNotRestartAFailedLoad() = runTest {
        val old = kotlinx.coroutines.CompletableDeferred<Unit>()
        var requests = 0
        val dialogs = UserProfileDialogs(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)) { scope, name ->
            UserProfileSession(scope, UserProfileRuntime(name, MONTHS, UserProfileLoader {
                requests++
                if (name == "Old") kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { old.await() }
                error("Unavailable")
            }, FakeAccounts(null), FakeBlocks()))
        }
        val previous = dialogs.open("Old")
        val current = dialogs.open("New")
        runCurrent()
        assertIs<UserProfileLoadState.Error>(current.runtime.state.value.loadState)
        assertTrue(dialogs.open("New") === current)
        current.start()
        old.complete(Unit); runCurrent()
        assertIs<UserProfileLoadState.Loading>(previous.runtime.state.value.loadState)
        assertEquals(2, requests)
        current.retry(); runCurrent()
        assertEquals(3, requests)
        dialogs.close()
    }

    @Test
    fun localizedDatesCanBeAppliedToEarlySessionWithoutReloadingPublicData() = runTest {
        var requests = 0
        val runtime = runtime(UserProfileLoader { requests++; user(it) })
        runtime.load()
        val names = List(12) { "Localized month" }
        runtime.updateMonthNames(names)
        val loaded = assertIs<UserProfileLoadState.Loaded>(runtime.state.value.loadState)
        assertTrue(loaded.profile.meta.contains("Localized month"))
        assertEquals(1, requests)
    }

    private fun runtime(
        loader: UserProfileLoader = UserProfileLoader { user(it) },
        account: HackerNewsAccount? = null,
        blocks: FakeBlocks = FakeBlocks(),
    ) = UserProfileRuntime(
        username = "alice",
        monthNames = MONTHS,
        loader = loader,
        accounts = FakeAccounts(account),
        blocks = blocks,
    )

    private fun user(id: String) = UserProfileData.from(HackerNewsUserDto(
        id = id,
        created = 1_169_856_000L,
        karma = 10,
    ))

    private class FakeAccounts(private var account: HackerNewsAccount?) :
        ObservableHackerNewsAccountRepository {
        private val mutableState = MutableStateFlow<HackerNewsAccountState>(account.toState())
        override val accountState: StateFlow<HackerNewsAccountState> = mutableState

        override suspend fun saveAccount(account: HackerNewsAccount): Boolean {
            this.account = account
            mutableState.value = account.toState()
            return true
        }
        override suspend fun clearAccount(): Boolean {
            account = null
            mutableState.value = HackerNewsAccountState.LoggedOut
            return true
        }

        private fun HackerNewsAccount?.toState(): HackerNewsAccountState =
            this?.let(HackerNewsAccountState::LoggedIn) ?: HackerNewsAccountState.LoggedOut
    }

    private class FakeBlocks : UserProfileBlockPort {
        private val blocked = mutableSetOf<String>()
        override fun isBlocked(username: String): Boolean = username.lowercase() in blocked
        override fun setBlocked(username: String, blocked: Boolean): Boolean {
            if (blocked) this.blocked += username.lowercase() else this.blocked -= username.lowercase()
            return true
        }
    }

    private companion object {
        val MONTHS = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )
    }
}
