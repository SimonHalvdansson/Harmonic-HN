package com.simon.harmonichackernews.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
private class CredentialBackedHackerNewsAccountStorage(
    private val credentials: CredentialStore,
) : HackerNewsAccountRepository {
    override fun load(): HackerNewsAccount? {
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
            return true
        }

        // Do not leave a partially updated login behind.
        clear()
        return false
    }

    override fun clear(): Boolean {
        val usernameRemoved = credentials.remove(CredentialIds.HACKER_NEWS_USERNAME)
        val passwordRemoved = credentials.remove(CredentialIds.HACKER_NEWS_PASSWORD)
        return usernameRemoved && passwordRemoved
    }
}

class CredentialBackedHackerNewsAccountRepository(
    credentials: CredentialStore,
    storageDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ObservableHackerNewsAccountRepository by ObservableAccountRepositoryAdapter(
    delegate = CredentialBackedHackerNewsAccountStorage(credentials),
    storageDispatcher = storageDispatcher,
)

/**
 * Adds observable/suspend semantics to an atomic platform account vault. Platforms only implement
 * secure atomic read/write/remove; shared code owns publication and mutation serialization.
 */
class ObservableAccountRepositoryAdapter(
    private val delegate: HackerNewsAccountRepository,
    private val storageDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ObservableHackerNewsAccountRepository {
    private val mutationMutex = Mutex()
    private val storageScope = CoroutineScope(SupervisorJob() + storageDispatcher)
    private val mutableAccountState = MutableStateFlow<HackerNewsAccountState>(
        HackerNewsAccountState.Loading,
    )
    override val accountState: StateFlow<HackerNewsAccountState> = mutableAccountState.asStateFlow()

    init {
        storageScope.launch {
            mutationMutex.withLock {
                if (mutableAccountState.value is HackerNewsAccountState.Loading) {
                    publish(delegate.load()?.normalizedUsername())
                }
            }
        }
    }

    override suspend fun saveAccount(account: HackerNewsAccount): Boolean =
        withContext(storageDispatcher) {
            mutationMutex.withLock { save(account) }
        }

    private fun save(account: HackerNewsAccount): Boolean {
        val normalizedAccount = account.normalizedUsername()
        return delegate.save(normalizedAccount).also { saved ->
            if (saved) publish(normalizedAccount) else publish(delegate.load()?.normalizedUsername())
        }
    }

    override suspend fun clearAccount(): Boolean = withContext(storageDispatcher) {
        mutationMutex.withLock { clear() }
    }

    private fun clear(): Boolean = delegate.clear().also { cleared ->
        if (cleared) publish(null) else publish(delegate.load()?.normalizedUsername())
    }

    override fun close() {
        storageScope.cancel()
    }

    private fun publish(account: HackerNewsAccount?) {
        mutableAccountState.value = account
            ?.let(HackerNewsAccountState::LoggedIn)
            ?: HackerNewsAccountState.LoggedOut
    }
}

private fun HackerNewsAccount.normalizedUsername(): HackerNewsAccount =
    copy(username = username.trim())
