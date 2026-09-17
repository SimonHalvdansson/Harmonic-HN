package com.simon.harmonichackernews.presentation

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Lifecycle-independent feature boundary used by every platform host. */
interface Feature<Intent, State, Effect> {
    val state: StateFlow<State>
    val effects: SharedFlow<Effect>
    fun dispatch(intent: Intent)
}
