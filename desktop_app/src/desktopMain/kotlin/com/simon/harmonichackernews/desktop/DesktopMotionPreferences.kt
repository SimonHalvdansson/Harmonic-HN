package com.simon.harmonichackernews.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.awaitApplication
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** The application context is inherited by ComposeWindow and all of its dialog compositions. */
internal fun desktopApplication(content: @Composable ApplicationScope.() -> Unit) {
    val motion = DesktopMotionDurationScale()
    runBlocking(motion) {
        val observer = launch(Dispatchers.IO) {
            while (isActive) {
                delay(1_000)
                motion.refresh()
            }
        }
        try {
            awaitApplication(content)
        } finally {
            observer.cancelAndJoin()
        }
    }
    // Match Compose's application() shutdown behavior after releasing the preference monitor.
    exitProcess(0)
}

@Stable
internal class DesktopMotionDurationScale(
    private val readScale: () -> Float? = DesktopMotionPreferences::readScale,
) : MotionDurationScale {
    private var currentScale by mutableFloatStateOf(readValidScale() ?: 1f)
    override val scaleFactor: Float get() = currentScale

    fun refresh() {
        // A transient native/settings failure must not silently reenable motion.
        readValidScale()?.let { currentScale = it }
    }

    private fun readValidScale(): Float? = runCatching { readScale() }.getOrNull()
        ?.takeIf { it.isFinite() && it >= 0f }
}

/** Read-only native preferences; reads run outside composition and never change OS settings. */
internal object DesktopMotionPreferences {
    fun readScale(): Float? = when {
        System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> MacMotionPreferences.readScale()
        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> WindowsMotionPreferences.readScale()
        else -> linuxScale()
    }

    private fun linuxScale(): Float? {
        val configRoot = System.getenv("XDG_CONFIG_HOME")?.takeIf(String::isNotBlank)?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home"), ".config")
        if (System.getenv("XDG_CURRENT_DESKTOP").orEmpty().contains("KDE", ignoreCase = true)) {
            return runCatching {
                val file = configRoot.resolve("kdeglobals")
                if (Files.isRegularFile(file)) kdeAnimationScale(Files.readString(file)) ?: 1f else 1f
            }.getOrNull()
        }
        return runCatching {
            val process = ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "enable-animations")
                .redirectErrorStream(true).start()
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching null
            }
            process.inputStream.bufferedReader().use { output ->
                if (process.exitValue() == 0) gnomeAnimationScale(output.readText()) else null
            }
        }.getOrNull()
    }
}

internal fun gnomeAnimationScale(value: String): Float? = when (value.trim()) {
    "true" -> 1f
    "false" -> 0f
    else -> null
}

internal fun kdeAnimationScale(config: String): Float? {
    var kde = false
    var scale: Float? = null
    config.lineSequence().forEach { raw ->
        val line = raw.trim()
        when {
            line.startsWith('[') -> kde = line == "[KDE]"
            kde && line.substringBefore('=').trim() == "AnimationDurationFactor" -> {
                scale = line.substringAfter('=', "").trim().toFloatOrNull()
                    ?.takeIf { it.isFinite() && it >= 0f }
            }
        }
    }
    return scale
}

private object MacMotionPreferences {
    private val appKit by lazy { NativeLibrary.getInstance("AppKit") }
    private val objc by lazy { NativeLibrary.getInstance("objc") }

    fun readScale(): Float? = runCatching {
        // NSWorkspace is the documented live preference, including changes made while open.
        appKit
        val workspaceClass = objc.getFunction("objc_getClass").invokePointer(arrayOf("NSWorkspace"))
            ?: return@runCatching null
        val register = objc.getFunction("sel_registerName")
        val send = objc.getFunction("objc_msgSend")
        val shared = register.invokePointer(arrayOf("sharedWorkspace"))
        val workspace = send.invokePointer(arrayOf(workspaceClass, shared)) ?: return@runCatching null
        val selector = register.invokePointer(arrayOf("accessibilityDisplayShouldReduceMotion"))
        val reduced = send.invoke(java.lang.Byte.TYPE, arrayOf(workspace, selector)) as Number
        if (reduced.toByte() != 0.toByte()) 0f else 1f
    }.getOrNull()
}

private object WindowsMotionPreferences {
    private interface UserMotionApi : StdCallLibrary {
        fun SystemParametersInfoW(action: Int, parameter: Int, value: IntByReference, flags: Int): Boolean
    }

    private val api by lazy { Native.load("User32", UserMotionApi::class.java) }

    fun readScale(): Float? = runCatching {
        val enabled = IntByReference()
        // SPI_GETCLIENTAREAANIMATION reads current UI animation effects, including temporary ones.
        if (!api.SystemParametersInfoW(0x1042, 0, enabled, 0)) null
        else if (enabled.value == 0) 0f else 1f
    }.getOrNull()
}
