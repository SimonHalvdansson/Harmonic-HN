package com.simon.harmonichackernews.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** One-way feature boundary shared by Compose, SwiftUI, and desktop hosts. */
interface FeatureStore<Intent, State, Effect> {
    val state: StateFlow<State>
    val effects: Flow<Effect>
    fun accept(intent: Intent)
    fun close()
}
