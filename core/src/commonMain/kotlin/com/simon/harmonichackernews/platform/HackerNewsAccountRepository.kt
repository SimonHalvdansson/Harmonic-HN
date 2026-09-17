package com.simon.harmonichackernews.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
        storageScope.launch { initializeAccountState() }
    }

    private suspend fun initializeAccountState() {
        repeat(INITIAL_LOAD_ATTEMPTS) { attempt ->
            if (tryInitialLoad()) return
            if (attempt < INITIAL_LOAD_ATTEMPTS - 1) delay(retryDelayMillis(attempt))
        }

        // Repeated unreadable-storage failures are no longer treated as indefinitely loading.
        // Try to make the logout durable first, then always unblock account-state consumers.
        repeat(RECOVERY_CLEAR_ATTEMPTS) { attempt ->
            if (tryRecoveryClear()) return
            if (attempt < RECOVERY_CLEAR_ATTEMPTS - 1) delay(retryDelayMillis(attempt))
        }
        mutationMutex.withLock {
            if (mutableAccountState.value is HackerNewsAccountState.Loading) publish(null)
        }
    }

    private suspend fun tryInitialLoad(): Boolean = mutationMutex.withLock {
        if (mutableAccountState.value !is HackerNewsAccountState.Loading) return@withLock true
        try {
            publish(delegate.load()?.normalizedUsername())
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            false
        }
    }

    private suspend fun tryRecoveryClear(): Boolean = mutationMutex.withLock {
        if (mutableAccountState.value !is HackerNewsAccountState.Loading) return@withLock true
        try {
            delegate.clear().also { cleared -> if (cleared) publish(null) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            false
        }
    }

    override suspend fun saveAccount(account: HackerNewsAccount): Boolean =
        withContext(storageDispatcher) {
            mutationMutex.withLock { save(account) }
        }

    private fun save(account: HackerNewsAccount): Boolean {
        val normalizedAccount = account.normalizedUsername()
        return runCatching { delegate.save(normalizedAccount) }
            .getOrDefault(false)
            .also { saved ->
                if (saved) {
                    publish(normalizedAccount)
                } else {
                    reloadWithoutDiscardingPublishedState()
                }
            }
    }

    private fun reloadWithoutDiscardingPublishedState() {
        runCatching { delegate.load()?.normalizedUsername() }
            .onSuccess(::publish)
    }

    override suspend fun clearAccount(): Boolean = withContext(storageDispatcher) {
        mutationMutex.withLock {
            runCatching { delegate.clear() }
                .getOrDefault(false)
                .also { cleared ->
                    if (cleared) {
                        publish(null)
                    } else {
                        reloadWithoutDiscardingPublishedState()
                    }
                }
        }
    }

    override fun close() {
        storageScope.cancel()
    }

    private fun publish(account: HackerNewsAccount?) {
        mutableAccountState.value = account
            ?.let(HackerNewsAccountState::LoggedIn)
            ?: HackerNewsAccountState.LoggedOut
    }

    private companion object {
        const val INITIAL_LOAD_ATTEMPTS = 3
        const val RECOVERY_CLEAR_ATTEMPTS = 3
        const val RETRY_DELAY_MILLIS = 250L

        fun retryDelayMillis(attempt: Int): Long = RETRY_DELAY_MILLIS * (attempt + 1L)
    }
}

private fun HackerNewsAccount.normalizedUsername(): HackerNewsAccount =
    copy(username = username.trim())
