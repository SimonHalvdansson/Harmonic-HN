package com.simon.harmonichackernews.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridges native path-monitor callbacks, including cost-only changes, to shared observers. */
class IosConnectivityState {
    private val mutableOnline = MutableStateFlow(true)
    private val mutableUnmetered = MutableStateFlow(true)
    val onlineChanges: Flow<Boolean> = mutableOnline.asStateFlow()
    val unmeteredChanges: Flow<Boolean> = mutableUnmetered.asStateFlow()

    fun update(online: Boolean, unmetered: Boolean) {
        mutableOnline.value = online
        mutableUnmetered.value = online && unmetered
    }
}
