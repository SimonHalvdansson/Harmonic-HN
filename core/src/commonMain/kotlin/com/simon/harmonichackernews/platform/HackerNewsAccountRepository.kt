package com.simon.harmonichackernews.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The Hacker News login is one credential and must be saved or removed as a unit. */
data class HackerNewsAccount(
    val username: String,
    val password: String,
) {
    init {
        require(username.isNotBlank()) { "A Hacker News username is required" }
        require(password.isNotEmpty()) { "A Hacker News password is required" }
    }

    override fun toString(): String = "HackerNewsAccount(username=$username, password=***)"
}

/** Typed secure-storage boundary for the Hacker News account. */
interface HackerNewsAccountRepository {
    fun load(): HackerNewsAccount?
    fun save(account: HackerNewsAccount): Boolean
    fun clear(): Boolean
}

/**
 * Compatibility adapter for platform implementations that still expose individual credentials.
 * New platform shells should implement [HackerNewsAccountRepository] atomically instead.
 */
class CredentialBackedHackerNewsAccountRepository(
    private val credentials: CredentialStore,
) : ObservableHackerNewsAccountRepository {
    private val mutationMutex = Mutex()
    private val mutableAccountState = MutableStateFlow(readAccount())
    override val accountState: StateFlow<HackerNewsAccount?> = mutableAccountState.asStateFlow()

    override fun load(): HackerNewsAccount? {
        return readAccount().also { mutableAccountState.value = it }
    }

    private fun readAccount(): HackerNewsAccount? {
        val username = credentials.read(CredentialIds.HACKER_NEWS_USERNAME)?.trim()
        val password = credentials.read(CredentialIds.HACKER_NEWS_PASSWORD)
        if (username.isNullOrBlank() || password.isNullOrEmpty()) return null
        return HackerNewsAccount(username, password)
    }

    override fun save(account: HackerNewsAccount): Boolean {
        val normalizedAccount = account.normalizedUsername()
        val usernameSaved = credentials.write(
            CredentialIds.HACKER_NEWS_USERNAME,
            normalizedAccount.username,
        )
        val passwordSaved = credentials.write(
            CredentialIds.HACKER_NEWS_PASSWORD,
            normalizedAccount.password,
        )
        if (usernameSaved && passwordSaved) {
            mutableAccountState.value = normalizedAccount
            return true
        }

        // Do not leave a partially updated login behind.
        clear()
        return false
    }

    override fun clear(): Boolean {
        val usernameRemoved = credentials.remove(CredentialIds.HACKER_NEWS_USERNAME)
        val passwordRemoved = credentials.remove(CredentialIds.HACKER_NEWS_PASSWORD)
        return (usernameRemoved && passwordRemoved).also {
            mutableAccountState.value = readAccount()
        }
    }

    override suspend fun saveAccount(account: HackerNewsAccount): Boolean =
        mutationMutex.withLock { save(account) }

    override suspend fun clearAccount(): Boolean = mutationMutex.withLock { clear() }
}

/**
 * Adds observable/suspend semantics to an atomic platform account vault. Platforms only implement
 * secure atomic read/write/remove; shared code owns publication and mutation serialization.
 */
class ObservableAccountRepositoryAdapter(
    private val delegate: HackerNewsAccountRepository,
) : ObservableHackerNewsAccountRepository {
    private val mutationMutex = Mutex()
    private val mutableAccountState = MutableStateFlow(delegate.load()?.normalizedUsername())
    override val accountState: StateFlow<HackerNewsAccount?> = mutableAccountState.asStateFlow()

    override fun load(): HackerNewsAccount? = delegate.load()?.normalizedUsername().also {
        mutableAccountState.value = it
    }

    override fun save(account: HackerNewsAccount): Boolean {
        val normalizedAccount = account.normalizedUsername()
        return delegate.save(normalizedAccount).also { saved ->
            mutableAccountState.value = if (saved) normalizedAccount else delegate.load()?.normalizedUsername()
        }
    }

    override fun clear(): Boolean = delegate.clear().also {
        mutableAccountState.value = delegate.load()?.normalizedUsername()
    }

    override suspend fun saveAccount(account: HackerNewsAccount): Boolean =
        mutationMutex.withLock { save(account) }

    override suspend fun clearAccount(): Boolean = mutationMutex.withLock { clear() }
}

private fun HackerNewsAccount.normalizedUsername(): HackerNewsAccount =
    copy(username = username.trim())
