package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.platform.HackerNewsAccountState
import com.simon.harmonichackernews.platform.accountOrNull
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun interface UserProfileLoader {
    suspend fun load(username: String): UserProfileData?
    fun cached(username: String): UserProfileData? = null
    suspend fun refresh(username: String): UserProfileData? = load(username)
}

interface UserProfileBlockPort {
    fun isBlocked(username: String): Boolean
    fun setBlocked(username: String, blocked: Boolean): Boolean
}

sealed interface UserProfileLoadState {
    data object Loading : UserProfileLoadState
    data class Loaded(val profile: UserProfilePresentation) : UserProfileLoadState
    data object Error : UserProfileLoadState
}

data class UserProfileRuntimeState(
    val loadState: UserProfileLoadState = UserProfileLoadState.Loading,
    val blocked: Boolean = false,
    val ownProfile: Boolean = false,
    val identityResolved: Boolean = false,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val blockOutcome: UserProfileBlockOutcome? = null,
)

data class UserProfileBlockOutcome(
    val blocked: Boolean,
    val message: String,
    val dismissProfile: Boolean,
)

/** Portable profile workflow; platform hosts retain navigation and intent side effects. */
class UserProfileRuntime(
    username: String,
    private var monthNames: List<String>,
    private val loader: UserProfileLoader,
    private val accounts: ObservableHackerNewsAccountRepository,
    private val blocks: UserProfileBlockPort,
) {
    private val username = username.trim()
    private var profile = loader.cached(this.username)
    private val mutableState = MutableStateFlow(
        UserProfileRuntimeState(
            loadState = profile?.let {
                UserProfileLoadState.Loaded(UserProfilePresenter.present(it, monthNames))
            } ?: UserProfileLoadState.Loading,
            blocked = blocks.isBlocked(this.username),
            ownProfile = matches(this.username, accounts.currentAccount?.username),
            identityResolved = accounts.accountState.value !is HackerNewsAccountState.Loading,
        ),
    )
    val state: StateFlow<UserProfileRuntimeState> = mutableState.asStateFlow()

    private var loadGeneration = 0

    suspend fun load(forceRefresh: Boolean = false) {
        val generation = ++loadGeneration
        val retained = mutableState.value.loadState as? UserProfileLoadState.Loaded
        mutableState.value = mutableState.value.copy(
            loadState = retained ?: UserProfileLoadState.Loading,
            refreshing = retained != null,
            refreshFailed = false,
        )
        try {
            val user = (if (forceRefresh) loader.refresh(username) else loader.load(username))
                ?: error("Hacker News user not found")
            currentCoroutineContext().ensureActive()
            if (generation != loadGeneration) return
            profile = user
            mutableState.value = mutableState.value.copy(
                loadState = UserProfileLoadState.Loaded(UserProfilePresenter.present(user, monthNames)),
                refreshing = false,
            )
            updateAccount(accounts.accountState.value)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (generation != loadGeneration) return
            mutableState.value = mutableState.value.copy(
                loadState = retained ?: UserProfileLoadState.Error,
                refreshing = false,
                refreshFailed = retained != null,
            )
        }
    }

    fun updateMonthNames(names: List<String>) {
        if (monthNames == names) return
        require(names.size >= 12)
        monthNames = names.toList()
        profile?.let { user ->
            mutableState.value = mutableState.value.copy(
                loadState = UserProfileLoadState.Loaded(UserProfilePresenter.present(user, monthNames)),
            )
        }
    }

    fun cancelLoad() { loadGeneration++ }

    fun canActOnProfile(): Boolean {
        updateAccount(accounts.accountState.value)
        return mutableState.value.identityResolved && !mutableState.value.ownProfile
    }

    suspend fun retry() = load(forceRefresh = true)

    suspend fun observeAccount() {
        accounts.accountState.collect(::updateAccount)
    }

    private fun updateAccount(account: HackerNewsAccountState) {
        val id = (mutableState.value.loadState as? UserProfileLoadState.Loaded)?.profile?.id ?: username
        mutableState.value = mutableState.value.copy(
            ownProfile = matches(id, account.accountOrNull?.username),
            identityResolved = account !is HackerNewsAccountState.Loading,
            blocked = blocks.isBlocked(username),
        )
    }

    fun toggleBlocked(): UserProfileBlockOutcome? {
        if (!canActOnProfile()) return null
        val nextBlocked = !mutableState.value.blocked
        if (!blocks.setBlocked(username, nextBlocked)) return null
        val outcome = UserProfileBlockOutcome(
            blocked = nextBlocked,
            message = if (nextBlocked) {
                "You will no longer see posts or comments from $username"
            } else {
                "Unblocked $username"
            },
            dismissProfile = nextBlocked,
        )
        mutableState.value = mutableState.value.copy(blocked = nextBlocked, blockOutcome = outcome)
        return outcome
    }

    private fun matches(first: String?, second: String?): Boolean =
        !first.isNullOrBlank() && first == second
}
