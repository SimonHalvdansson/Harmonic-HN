package com.simon.harmonichackernews.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed interface UserProfileSessionEffect {
    data class OpenSubmissions(val username: String) : UserProfileSessionEffect
    data class ComposeReportEmail(val username: String) : UserProfileSessionEffect
    data class Message(val text: String) : UserProfileSessionEffect
    data object Dismiss : UserProfileSessionEffect
}

/** Owns all profile actions except native email UI. */
class UserProfileSession(
    private val scope: CoroutineScope,
    val runtime: UserProfileRuntime,
) {
    private val mutableEffects = MutableSharedFlow<UserProfileSessionEffect>(extraBufferCapacity = 8)
    private var loadJob: Job? = null
    val effects: SharedFlow<UserProfileSessionEffect> = mutableEffects.asSharedFlow()

    fun start() {
        loadJob?.cancel()
        loadJob = scope.launch { runtime.load() }
    }

    fun retry() {
        loadJob?.cancel()
        loadJob = scope.launch { runtime.retry() }
    }

    fun openSubmissions(username: String) {
        mutableEffects.tryEmit(UserProfileSessionEffect.OpenSubmissions(username))
    }

    fun toggleBlocked() {
        runtime.toggleBlocked()?.let { result ->
            mutableEffects.tryEmit(UserProfileSessionEffect.Message(result.message))
            if (result.dismissProfile) mutableEffects.tryEmit(UserProfileSessionEffect.Dismiss)
        }
    }

    fun report(username: String) {
        mutableEffects.tryEmit(UserProfileSessionEffect.ComposeReportEmail(username))
    }

    fun dispose() = loadJob?.cancel()
}
