package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalSettingsDialogInsets = staticCompositionLocalOf<SettingsDialogInsetsState?> { null }

/** Keeps stacked dialogs moving together with the foremost dialog's keyboard animation. */
@Composable
fun SynchronizedSettingsDialogs(content: @Composable () -> Unit) {
    val state = remember { SettingsDialogInsetsState() }
    CompositionLocalProvider(LocalSettingsDialogInsets provides state, content = content)
}

@Stable
internal class SettingsDialogInsetsState {
    // Android's insets compare equal by inset type even when they belong to different windows.
    // Give each registration its own identity so windowInsetsPadding replaces its remembered
    // source when the foremost dialog changes.
    private class Window(insets: WindowInsets) : WindowInsets by insets

    private val windows = mutableStateListOf<Window>()

    val current: WindowInsets?
        get() = windows.lastOrNull()

    fun register(insets: WindowInsets): () -> Unit {
        val window = Window(insets)
        windows.add(window)
        return { windows.remove(window) }
    }
}

@Composable
internal fun synchronizedSettingsDialogInsets(): WindowInsets {
    // Read this inside each Dialog's composition. Inactive Android windows can receive the final
    // IME inset immediately; only the foremost window supplies the animated values we want.
    val windowInsets = WindowInsets.safeDrawing
    val state = LocalSettingsDialogInsets.current ?: return windowInsets
    DisposableEffect(state, windowInsets) {
        val unregister = state.register(windowInsets)
        onDispose(unregister)
    }
    // Share the live WindowInsets object, so padding reads each animated value during layout
    // rather than copying pixel values during composition and falling a frame behind.
    return state.current ?: windowInsets
}
