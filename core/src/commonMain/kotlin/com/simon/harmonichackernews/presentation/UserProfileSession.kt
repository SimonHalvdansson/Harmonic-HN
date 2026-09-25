package com.simon.harmonichackernews.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
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
    private var accountJob: Job? = null
    private var started = false
    private var disposed = false
    val effects: SharedFlow<UserProfileSessionEffect> = mutableEffects.asSharedFlow()

    fun start() {
        if (started || disposed) return
        started = true
        accountJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.observeAccount() }
        loadJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { runtime.load() }
    }

    fun retry() {
        if (disposed) return
        loadJob?.cancel()
        loadJob = scope.launch { runtime.retry() }
    }

    fun openSubmissions(username: String) {
        if (disposed) return
        mutableEffects.tryEmit(UserProfileSessionEffect.OpenSubmissions(username))
    }

    fun toggleBlocked() {
        if (disposed) return
        runtime.toggleBlocked()?.let { result ->
            mutableEffects.tryEmit(UserProfileSessionEffect.Message(result.message))
            if (result.dismissProfile) mutableEffects.tryEmit(UserProfileSessionEffect.Dismiss)
        }
    }

    fun report(username: String) {
        if (disposed) return
        if (runtime.canActOnProfile()) {
            mutableEffects.tryEmit(UserProfileSessionEffect.ComposeReportEmail(username))
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        runtime.cancelLoad()
        loadJob?.cancel()
        accountJob?.cancel()
    }
}
