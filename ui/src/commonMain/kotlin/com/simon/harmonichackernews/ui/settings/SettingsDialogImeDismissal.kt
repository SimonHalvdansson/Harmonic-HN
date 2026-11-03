package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.first

/** Holds a closing editor's window until its keyboard has finished moving the stacked dialogs. */
@Stable
internal class SettingsDialogDismissState {
    var pendingAction: (() -> Unit)? by mutableStateOf(null)
        private set

    val isClosing: Boolean get() = pendingAction != null

    fun request(action: () -> Unit) {
        if (pendingAction == null) pendingAction = action
    }

    /** A later parent-close intent cancels an editor-to-child handoff still waiting for the IME. */
    fun cancel() {
        pendingAction = null
    }

    fun complete(action: () -> Unit) {
        if (pendingAction !== action) return
        pendingAction = null
        action()
    }
}

/** Must be composed inside the editor Dialog, whose insets animate with the IME. */
@Composable
internal fun CompleteSettingsDialogDismissal(state: SettingsDialogDismissState) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val pendingAction = state.pendingAction
    LaunchedEffect(pendingAction) {
        val action = pendingAction ?: return@LaunchedEffect
        keyboardController?.hide()
        // Keep both the window and its live inset registration until layout has consumed the
        // last hidden-keyboard frame. Disposing earlier makes the inactive profile's already-final
        // insets take over while the keyboard is still visibly on screen.
        do {
            withFrameNanos { }
            snapshotFlow { ime.getBottom(density) }.first { it == 0 }
            withFrameNanos { }
        } while (ime.getBottom(density) != 0)
        // Ending the input session while the keyboard is visible can change its key layout.
        // Keep the frozen editor focused during the hide, as the post editor does on discard.
        focusManager.clearFocus(force = true)
        state.complete(action)
    }
}
