package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsDialogFocusTest {
    @Test
    fun autofocusWaitsForTheDialogWindowAndDoesNotRepeatAfterFocusReturns() = SwingUtilities.invokeAndWait {
        val window = TestWindowInfo()
        val requester = FocusRequester()
        val otherRequester = FocusRequester()
        var focused = false
        val scene = ImageComposeScene(400, 800) {
            CompositionLocalProvider(LocalWindowInfo provides window) {
                RequestSettingsDialogFocus(requester)
                BasicTextField(rememberTextFieldState(), Modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused })
                BasicTextField(rememberTextFieldState(), Modifier.focusRequester(otherRequester))
            }
        }
        var time = 0L
        fun frames(count: Int = 5) { repeat(count) { scene.render(time.also { time += 16_666_667L }).close() } }
        try {
            frames()
            assertFalse(focused, "A cold dialog must not open the keyboard before owning window focus")
            window.isWindowFocused = true
            frames()
            assertTrue(focused)
            otherRequester.requestFocus()
            frames()
            window.isWindowFocused = false
            frames()
            window.isWindowFocused = true
            frames()
            assertFalse(focused, "Autofocus must run once per opening and respect subsequent user focus changes")
        } finally { scene.close() }
    }

    @Test
    fun closingBeforeWindowFocusCancelsTheRequestAndReopeningStartsFresh() = SwingUtilities.invokeAndWait {
        val window = TestWindowInfo()
        var visible by mutableStateOf(true)
        var focused = false
        val scene = ImageComposeScene(400, 800) {
            CompositionLocalProvider(LocalWindowInfo provides window) {
                if (visible) {
                    val requester = remember { FocusRequester() }
                    RequestSettingsDialogFocus(requester)
                    BasicTextField(rememberTextFieldState(), Modifier.focusRequester(requester).onFocusChanged { focused = it.isFocused })
                }
            }
        }
        var time = 0L
        fun frames() { repeat(5) { scene.render(time.also { time += 16_666_667L }).close() } }
        try {
            frames()
            assertFalse(focused)
            visible = false
            frames()
            window.isWindowFocused = true
            frames()
            assertFalse(focused)
            visible = true
            frames()
            assertTrue(focused, "A new opening must autofocus even if the previous request was cancelled")
        } finally { scene.close() }
    }

    private class TestWindowInfo : WindowInfo {
        override var isWindowFocused: Boolean by mutableStateOf(false)
    }
}
