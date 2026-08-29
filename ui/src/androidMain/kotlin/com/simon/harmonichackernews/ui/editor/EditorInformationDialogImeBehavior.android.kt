package com.simon.harmonichackernews.ui.editor

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
internal actual fun EditorInformationDialogImeBehavior() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window) {
        val flag = WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
        val flagWasSet = window.attributes.flags and flag != 0
        window.addFlags(flag)
        onDispose {
            if (!flagWasSet) window.clearFlags(flag)
        }
    }
}
