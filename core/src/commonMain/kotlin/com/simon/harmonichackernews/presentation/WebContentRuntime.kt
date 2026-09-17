package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.utils.AdHostBlocklist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application-scoped decoded ad-host data without a platform singleton. */
class AdBlocklistService {
    private val mutableHosts = MutableStateFlow(AdHostBlocklist.empty())
    val hosts: StateFlow<AdHostBlocklist> = mutableHosts.asStateFlow()

    fun install(encoded: ByteArray): Int {
        val decoded = AdHostBlocklist.decode(encoded)
        mutableHosts.value = decoded
        return decoded.size
    }

    fun contains(host: String?): Boolean = mutableHosts.value.contains(host)
}

/** Per-browser portable runtime; native hosts retain rendering and JavaScript evaluation. */
class WebContentRuntime internal constructor(
    val adBlocklist: AdBlocklistService,
) {
    val load = WebContentLoadStateMachine()
    val reader = ReaderModeStateMachine()
}

class WebContentService {
    val adBlocklist = AdBlocklistService()
    fun createRuntime(): WebContentRuntime = WebContentRuntime(adBlocklist)
}
