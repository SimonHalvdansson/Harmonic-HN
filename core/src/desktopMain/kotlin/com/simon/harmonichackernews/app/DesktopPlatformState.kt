package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.platform.ConnectivityService
import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.net.NetworkInterface
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

internal data object DesktopConnectivity : ConnectivityService {
    override fun isOnline(): Boolean = if (DesktopOperatingSystem.isWindows) {
        runCatching {
            WindowsInternetStatus.INSTANCE.InternetGetConnectedState(IntByReference(), 0)
        }.getOrDefault(false)
    } else {
        hasActiveNetworkInterface()
    }

    /**
     * The desktop host has no portable connection-cost API. Do not claim that a connection is
     * unmetered unless the host can prove it; none of the current desktop features consume this.
     */
    override fun isUnmetered(): Boolean = false

    private fun hasActiveNetworkInterface(): Boolean = runCatching {
        NetworkInterface.getNetworkInterfaces()?.asSequence()?.any { network ->
            network.isUp && !network.isLoopback && !network.isVirtual
        } == true
    }.getOrDefault(false)
}

private interface WindowsInternetStatus : StdCallLibrary {
    fun InternetGetConnectedState(flags: IntByReference, reserved: Int): Boolean

    companion object {
        val INSTANCE: WindowsInternetStatus = Native.load("Wininet", WindowsInternetStatus::class.java)
    }
}

internal data object DesktopSystemAppearance {
    /** Emits when either the Windows app theme or the local minute changes while Harmonic is open. */
    val changes: Flow<Unit> = flow {
        var dark = isDark()
        var minute = currentMinute()
        while (currentCoroutineContext().isActive) {
            delay(APPEARANCE_POLL_MILLIS)
            val nextDark = isDark()
            val nextMinute = currentMinute()
            if (nextDark != dark || nextMinute != minute) {
                dark = nextDark
                minute = nextMinute
                emit(Unit)
            }
        }
    }

    fun isDark(): Boolean = when {
        DesktopOperatingSystem.isWindows -> windowsAppsUseDarkTheme()
        DesktopOperatingSystem.isMac -> macOsUsesDarkTheme()
        else -> linuxUsesDarkTheme()
    }

    private fun windowsAppsUseDarkTheme(): Boolean = runCatching {
        Advapi32Util.registryGetIntValue(
            WinReg.HKEY_CURRENT_USER,
            WINDOWS_PERSONALIZE_KEY,
            "AppsUseLightTheme",
        ) == 0
    }.getOrDefault(false)

    private fun macOsUsesDarkTheme(): Boolean = runCatching {
        val process = ProcessBuilder("defaults", "read", "-g", "AppleInterfaceStyle")
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return@runCatching false
        }
        process.inputStream.bufferedReader().use {
            it.readText().trim().equals("Dark", ignoreCase = true)
        }
    }.getOrDefault(false)

    private fun linuxUsesDarkTheme(): Boolean = sequenceOf(
        System.getenv("GTK_THEME"),
        System.getenv("COLORFGBG"),
    ).filterNotNull().any { it.contains("dark", ignoreCase = true) }

    private fun currentMinute(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    private const val APPEARANCE_POLL_MILLIS = 1_000L
    private const val WINDOWS_PERSONALIZE_KEY =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize"
}

internal data object DesktopOperatingSystem {
    private val name = System.getProperty("os.name").lowercase()
    val isWindows: Boolean = name.contains("win")
    val isMac: Boolean = name.contains("mac")
}
