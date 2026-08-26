package com.simon.harmonichackernews.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class HackerNewsAccountRepositoryTest {
    @Test
    fun accountRoundTripsThroughLegacyCredentialStore() = runTest {
        val credentials = MemoryCredentialStore()
        val repository = CredentialBackedHackerNewsAccountRepository(
            credentials,
            StandardTestDispatcher(testScheduler),
        )
        val account = HackerNewsAccount("alice", "correct horse")

        runCurrent()
        assertNull(repository.currentAccount)
        assertTrue(repository.saveAccount(account))
        assertEquals(account, repository.currentAccount)
        assertTrue(repository.clearAccount())
        assertNull(repository.currentAccount)
        repository.close()
    }

    @Test
    fun trimsUsernameWithoutChangingPassword() = runTest {
        val credentials = MemoryCredentialStore()
        val repository = CredentialBackedHackerNewsAccountRepository(
            credentials,
            StandardTestDispatcher(testScheduler),
        )

        assertTrue(repository.saveAccount(HackerNewsAccount("  alice  ", " secret ")))
        assertEquals("alice", credentials.read(CredentialIds.HACKER_NEWS_USERNAME))
        assertEquals(" secret ", credentials.read(CredentialIds.HACKER_NEWS_PASSWORD))
        assertEquals(HackerNewsAccount("alice", " secret "), repository.currentAccount)
        repository.close()
    }

    @Test
    fun trimsUsernameAlreadyStoredByLegacyImplementation() = runTest {
        val credentials = MemoryCredentialStore(
            initialValues = mapOf(
                CredentialIds.HACKER_NEWS_USERNAME to "alice ",
                CredentialIds.HACKER_NEWS_PASSWORD to "secret",
            ),
        )
        val repository = CredentialBackedHackerNewsAccountRepository(
            credentials,
            StandardTestDispatcher(testScheduler),
        )

        runCurrent()
        assertEquals(HackerNewsAccount("alice", "secret"), repository.currentAccount)
        repository.close()
    }

    @Test
    fun failedPartialSaveClearsBothCredentialFields() = runTest {
        val credentials = MemoryCredentialStore(failingWriteId = CredentialIds.HACKER_NEWS_PASSWORD)
        val repository = CredentialBackedHackerNewsAccountRepository(
            credentials,
            StandardTestDispatcher(testScheduler),
        )

        assertFalse(repository.saveAccount(HackerNewsAccount("alice", "secret")))
        assertNull(credentials.read(CredentialIds.HACKER_NEWS_USERNAME))
        assertNull(credentials.read(CredentialIds.HACKER_NEWS_PASSWORD))
        repository.close()
    }

    @Test
    fun accountStringDoesNotExposePassword() {
        val account = HackerNewsAccount("alice", "super-secret")

        assertFalse(account.toString().contains("super-secret"))
        assertTrue(account.toString().contains("alice"))
    }

    @Test
    fun adapterOffersObservableSuspendMutations() = runTest {
        val repository = CredentialBackedHackerNewsAccountRepository(
            MemoryCredentialStore(),
            StandardTestDispatcher(testScheduler),
        )
        val account = HackerNewsAccount("alice", "secret")

        assertTrue(repository.saveAccount(account))
        assertEquals(account, repository.currentAccount)
        assertEquals(account, assertIs<HackerNewsAccountState.LoggedIn>(repository.accountState.value).account)

        assertTrue(repository.clearAccount())
        assertEquals(HackerNewsAccountState.LoggedOut, repository.accountState.value)
        repository.close()
    }

    @Test
    fun concurrentSuspendSavesNeverMixCredentialPairs() = runTest {
        val repository = CredentialBackedHackerNewsAccountRepository(
            MemoryCredentialStore(),
            StandardTestDispatcher(testScheduler),
        )

        coroutineScope {
            repeat(100) { index ->
                launch {
                    repository.saveAccount(HackerNewsAccount("user-$index", "password-$index"))
                }
            }
        }

        val account = requireNotNull(repository.currentAccount)
        assertEquals(account.username.substringAfter('-'), account.password.substringAfter('-'))
        repository.close()
    }

    @Test
    fun initializationIsDeferredAndSecureStorageLoadsOnlyOnce() = runTest {
        val account = HackerNewsAccount("alice", "secret")
        val storage = RecordingAccountStorage(account)
        val repository = ObservableAccountRepositoryAdapter(
            storage,
            StandardTestDispatcher(testScheduler),
        )

        assertEquals(HackerNewsAccountState.Loading, repository.accountState.value)
        assertEquals(0, storage.loadCount)

        runCurrent()

        assertEquals(account, repository.currentAccount)
        assertEquals(account, repository.awaitAccount())
        assertEquals(account, repository.currentAccount)
        assertEquals(1, storage.loadCount)
        repository.close()
    }

    private class RecordingAccountStorage(
        private var account: HackerNewsAccount?,
    ) : HackerNewsAccountRepository {
        var loadCount = 0
            private set

        override fun load(): HackerNewsAccount? {
            loadCount++
            return account
        }

        override fun save(account: HackerNewsAccount): Boolean {
            this.account = account
            return true
        }

        override fun clear(): Boolean {
            account = null
            return true
        }
    }

    private class MemoryCredentialStore(
        private val failingWriteId: String? = null,
        initialValues: Map<String, String> = emptyMap(),
    ) : CredentialStore {
        private val values = initialValues.toMutableMap()

        override fun read(id: String): String? = values[id]

        override fun write(id: String, value: String): Boolean {
            if (id == failingWriteId) return false
            values[id] = value
            return true
        }

        override fun remove(id: String): Boolean {
            values.remove(id)
            return true
        }
    }
}
