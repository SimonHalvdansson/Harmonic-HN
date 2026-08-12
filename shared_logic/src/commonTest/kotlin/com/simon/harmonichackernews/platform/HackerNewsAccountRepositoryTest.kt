package com.simon.harmonichackernews.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class HackerNewsAccountRepositoryTest {
    @Test
    fun accountRoundTripsThroughLegacyCredentialStore() {
        val credentials = MemoryCredentialStore()
        val repository = CredentialBackedHackerNewsAccountRepository(credentials)
        val account = HackerNewsAccount("alice", "correct horse")

        assertTrue(repository.save(account))
        assertEquals(account, repository.load())
        assertTrue(repository.clear())
        assertNull(repository.load())
    }

    @Test
    fun failedPartialSaveClearsBothCredentialFields() {
        val credentials = MemoryCredentialStore(failingWriteId = CredentialIds.HACKER_NEWS_PASSWORD)
        val repository = CredentialBackedHackerNewsAccountRepository(credentials)

        assertFalse(repository.save(HackerNewsAccount("alice", "secret")))
        assertNull(credentials.read(CredentialIds.HACKER_NEWS_USERNAME))
        assertNull(credentials.read(CredentialIds.HACKER_NEWS_PASSWORD))
    }

    @Test
    fun accountStringDoesNotExposePassword() {
        val account = HackerNewsAccount("alice", "super-secret")

        assertFalse(account.toString().contains("super-secret"))
        assertTrue(account.toString().contains("alice"))
    }

    @Test
    fun legacyAdapterOffersObservableSuspendMutations() = runTest {
        val repository = CredentialBackedHackerNewsAccountRepository(MemoryCredentialStore())
        val account = HackerNewsAccount("alice", "secret")

        assertTrue(repository.saveAccount(account))
        assertEquals(account, repository.accountState.value)

        assertTrue(repository.clearAccount())
        assertNull(repository.accountState.value)
    }

    private class MemoryCredentialStore(
        private val failingWriteId: String? = null,
    ) : CredentialStore {
        private val values = mutableMapOf<String, String>()

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
