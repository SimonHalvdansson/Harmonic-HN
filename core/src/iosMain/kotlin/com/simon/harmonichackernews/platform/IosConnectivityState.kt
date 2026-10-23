package com.simon.harmonichackernews.platform

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bridges native path-monitor callbacks to the shared connectivity flow. */
class IosConnectivityState {
    private val mutableOnline = MutableStateFlow(true)
    val onlineChanges: Flow<Boolean> = mutableOnline.asStateFlow()

    fun updateOnline(online: Boolean) {
        mutableOnline.value = online
    }
}
