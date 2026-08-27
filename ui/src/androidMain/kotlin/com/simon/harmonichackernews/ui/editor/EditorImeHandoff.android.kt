package com.simon.harmonichackernews.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.PlatformTextInputSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val ImeFieldHandoffTimeoutMillis = 100L

// Compose normally stops the old platform input session before the newly focused field starts
// its session. Keep the upstream session alive across that short gap so Android can attach the
// replacement input connection without hiding the IME. The timeout still closes it when focus
// actually leaves the editor.
private class EditorImeHandoffInterceptor(
    private val scope: CoroutineScope,
) : PlatformTextInputInterceptor {
    private var activeSession: Job? = null
    private var pendingStop: Job? = null

    override suspend fun interceptStartInputMethod(
        request: PlatformTextInputMethodRequest,
        nextHandler: PlatformTextInputSession,
    ): Nothing {
        pendingStop?.cancel()
        val session = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            nextHandler.startInputMethod(request)
        }
        activeSession = session

        try {
            awaitCancellation()
        } finally {
            pendingStop?.cancel()
            pendingStop = scope.launch {
                delay(ImeFieldHandoffTimeoutMillis)
                if (activeSession === session) {
                    activeSession = null
                    session.cancel()
                }
            }
        }
    }

    fun dispose() {
        pendingStop?.cancel()
        activeSession?.cancel()
        pendingStop = null
        activeSession = null
    }
}

@Composable
internal actual fun KeepImeOpenDuringFieldHandoff(content: @Composable () -> Unit) {
    val scope = rememberCoroutineScope()
    val interceptor = remember(scope) { EditorImeHandoffInterceptor(scope) }
    DisposableEffect(interceptor) {
        onDispose(interceptor::dispose)
    }
    InterceptPlatformTextInput(interceptor, content)
}
