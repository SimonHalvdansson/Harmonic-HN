package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.navigation.MainNavigationSnapshot
import com.simon.harmonichackernews.navigation.MainUserRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Scene-owned dialog sessions start at action acceptance and survive the composition handoff. */
class UserProfileDialogs(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val createSession: (CoroutineScope, String) -> UserProfileSession,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var navigationRequest: MainUserRequest? = null
    private var current: Pair<String, UserProfileSession>? = null
    private var closed = false

    fun navigationChanged(snapshot: MainNavigationSnapshot) {
        if (closed || navigationRequest == snapshot.userRequest) return
        navigationRequest = snapshot.userRequest
        snapshot.userRequest?.let { open(it.userName) } ?: dismiss()
    }

    /** Also used by the settings-local profile action before it publishes its dialog state. */
    fun open(username: String): UserProfileSession {
        check(!closed)
        val key = username.trim()
        current?.takeIf { it.first == key }?.let { return it.second }
        dismiss()
        return createSession(scope, key).also {
            current = key to it
            it.start()
        }
    }

    fun release(session: UserProfileSession) {
        if (current?.second === session) dismiss() else session.dispose()
    }

    private fun dismiss() {
        current?.second?.dispose()
        current = null
    }

    override fun close() {
        if (closed) return
        closed = true
        dismiss()
        scope.cancel()
    }
}
