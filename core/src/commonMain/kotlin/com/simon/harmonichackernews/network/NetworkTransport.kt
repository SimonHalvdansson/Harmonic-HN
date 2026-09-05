package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.withContext

/** Owns lazy transport construction and disposal, including disposal during construction. */
internal class NetworkTransport(
    private val dispatcher: CoroutineDispatcher,
    factory: () -> HttpClient,
) {
    constructor(client: HttpClient) : this(Dispatchers.Default, { client }) {
        get() // An already-created client is owned even if no request is ever made.
    }

    private sealed interface State {
        data object Empty : State
        data class Ready(val client: HttpClient) : State
        data object Closed : State
    }

    private val state = MutableStateFlow<State>(State.Empty)
    private val initialized by lazy {
        val client = factory()
        if (!state.compareAndSet(State.Empty, State.Ready(client))) {
            client.close()
            error("Network transport is closed")
        }
        client
    }

    suspend fun await(): HttpClient = when (val current = state.value) {
        is State.Ready -> current.client
        State.Closed -> error("Network transport is closed")
        State.Empty -> withContext(dispatcher) { get() }
    }

    /** Compatibility for native hosts that explicitly request the raw client. */
    fun get(): HttpClient {
        check(state.value != State.Closed) { "Network transport is closed" }
        return initialized
    }

    fun close() {
        (state.getAndUpdate { State.Closed } as? State.Ready)?.client?.close()
    }
}
