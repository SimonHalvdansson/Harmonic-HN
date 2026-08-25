package com.simon.harmonichackernews.presentation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

enum class UserMessageDuration { SHORT, LONG }

data class UserMessage(
    val text: String,
    val duration: UserMessageDuration = UserMessageDuration.SHORT,
)

/** Platform-neutral queue for transient messages rendered by the active app host. */
class UserMessageStore {
    private val pendingMessages = Channel<UserMessage>(Channel.UNLIMITED)

    val messages: Flow<UserMessage> = pendingMessages.receiveAsFlow()

    fun show(
        text: String?,
        duration: UserMessageDuration = UserMessageDuration.SHORT,
    ) {
        val message = text?.takeIf(String::isNotBlank) ?: return
        pendingMessages.trySend(UserMessage(message, duration))
    }

    /** Stops delivery when the scene that owns this queue is permanently destroyed. */
    fun close() {
        pendingMessages.close()
    }
}
