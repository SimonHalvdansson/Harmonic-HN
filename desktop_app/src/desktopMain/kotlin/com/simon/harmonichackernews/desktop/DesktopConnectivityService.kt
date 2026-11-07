package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.app.DesktopConnectivity
import com.simon.harmonichackernews.platform.ConnectivityService
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Native cost checks run on a worker; preload decisions only read the latest cached result. */
internal class DesktopConnectivityService : ConnectivityService by DesktopConnectivity, AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val unmetered = MutableStateFlow(false)
    private val online = MutableStateFlow(DesktopConnectivity.isOnline())
    override val unmeteredChanges = unmetered.asStateFlow()
    override val onlineChanges = online.asStateFlow()

    init {
        scope.launch {
            val os = System.getProperty("os.name").lowercase()
            val macApi = if (os.startsWith("mac")) runCatching { MacWebViewLibrary.api }.getOrNull() else null
            val monitor = runCatching { macApi?.harmonic_network_monitor_create() }.getOrNull()
            try {
                var costCheckCountdown = 0
                while (isActive) {
                    val connected = runCatching {
                        if (macApi != null && monitor != null) macApi.harmonic_network_monitor_online(monitor) != 0
                        else DesktopConnectivity.isOnline()
                    }.getOrDefault(false)
                    if (costCheckCountdown-- <= 0 || connected != online.value || macApi != null) {
                        unmetered.value = runCatching {
                            when {
                                !connected -> false
                                macApi != null && monitor != null -> macApi.harmonic_network_monitor_unmetered(monitor) != 0
                                os.startsWith("windows") -> windowsConnectionUnmetered()
                                os.startsWith("linux") -> linuxConnectionUnmetered()
                                else -> false
                            }
                        }.getOrDefault(false)
                        costCheckCountdown = 4
                    }
                    online.value = connected
                    delay(1_000)
                }
            } finally {
                if (monitor != null) macApi?.harmonic_network_monitor_destroy(monitor)
            }
        }
    }

    override fun isOnline(): Boolean = online.value
    override fun isUnmetered(): Boolean = unmetered.value && isOnline()
    override fun close() = scope.cancel()
}

// INetworkCostManager::GetCost(NULL destination) describes the default Internet route.
private fun windowsConnectionUnmetered(): Boolean {
    val ole = Ole32.INSTANCE
    val initialized = ole.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED).toInt()
    if (initialized < 0) return false
    try {
        val result = PointerByReference()
        val status = ole.CoCreateInstance(
            Guid.CLSID("{DCB00C01-570F-4A9B-8D69-199FDBA5723B}"), null,
            WTypes.CLSCTX_ALL, Guid.IID("{DCB00008-570F-4A9B-8D69-199FDBA5723B}"), result,
        )
        if (status.toInt() < 0 || result.value == null) return false
        val manager = NetworkCostManager(result.value)
        return try {
            val cost = IntByReference()
            manager.getCost(cost).toInt() >= 0 && windowsCostIsUnmetered(cost.value)
        } finally {
            manager.Release()
        }
    } finally {
        ole.CoUninitialize()
    }
}

private class NetworkCostManager(pointer: Pointer) : Unknown(pointer) {
    fun getCost(cost: IntByReference): HRESULT =
        _invokeNativeObject(3, arrayOf(pointer, cost, null), HRESULT::class.java) as HRESULT
}

// Unknown, metered, roaming, over-limit and approaching-limit costs must not start preloads.
internal fun windowsCostIsUnmetered(cost: Int): Boolean = cost == 1

private fun linuxConnectionUnmetered(): Boolean {
    // NetworkManager's global Metered property follows the default route, including VPNs.
    // Missing NetworkManager/busctl or an unknown cost stays conservative.
    val process = ProcessBuilder(
        "busctl", "--system", "--timeout=1", "get-property", "org.freedesktop.NetworkManager",
        "/org/freedesktop/NetworkManager", "org.freedesktop.NetworkManager", "Metered",
    ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    return try {
        if (!process.waitFor(2, TimeUnit.SECONDS)) return false
        process.exitValue() == 0 && networkManagerCostIsUnmetered(process.inputStream.bufferedReader().use { it.readText() })
    } finally {
        process.destroyForcibly()
    }
}

internal fun networkManagerCostIsUnmetered(output: String): Boolean =
    output.trim() in setOf("u 2", "u 4") // NM_METERED_NO / NM_METERED_GUESS_NO
