package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun interface UserProfileLoader {
    suspend fun load(username: String): HackerNewsUserDto?
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
    private val monthNames: List<String>,
    private val loader: UserProfileLoader,
    private val accounts: ObservableHackerNewsAccountRepository,
    private val blocks: UserProfileBlockPort,
) {
    private val username = username.trim()
    private val mutableState = MutableStateFlow(
        UserProfileRuntimeState(
            blocked = blocks.isBlocked(this.username),
        ),
    )
    val state: StateFlow<UserProfileRuntimeState> = mutableState.asStateFlow()

    suspend fun load() {
        mutableState.value = mutableState.value.copy(loadState = UserProfileLoadState.Loading)
        mutableState.value = try {
            val user = loader.load(username) ?: error("Hacker News user not found")
            val profile = UserProfilePresenter.present(user, monthNames)
            mutableState.value.copy(
                loadState = UserProfileLoadState.Loaded(profile),
                ownProfile = matches(profile.id, accounts.awaitAccount()?.username),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            mutableState.value.copy(loadState = UserProfileLoadState.Error, ownProfile = false)
        }
    }

    suspend fun retry() = load()

    fun toggleBlocked(): UserProfileBlockOutcome? {
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
        !first.isNullOrBlank() && first.equals(second, ignoreCase = true)
}
