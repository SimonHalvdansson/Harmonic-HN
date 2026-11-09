package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DebugAccountIdentityState(val username: String? = null, val revision: Long = 0)

/** Public Account-screen identity only. Never writes credentials or authenticates HN actions. */
class DebugAccountIdentity(private val store: KeyValueStore, private val enabled: Boolean) {
    private val mutableState = MutableStateFlow(DebugAccountIdentityState(
        username = if (enabled) store.getString(KEY)?.trim()?.takeIf { it.isNotEmpty() } else null,
    ))
    val state = mutableState.asStateFlow()

    fun setUsername(username: String?) {
        if (!enabled) return
        val normalized = username?.trim()?.takeIf { it.isNotEmpty() }
        require(normalized == null || normalized.none(Char::isWhitespace)) { "Enter a username without spaces" }
        if (normalized == mutableState.value.username) return
        store.putString(KEY, normalized)
        mutableState.value = DebugAccountIdentityState(normalized, mutableState.value.revision + 1)
    }

    companion object { private const val KEY = "debug_account_preview_username" }
}
