package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.data.ItemTimeFormatter
import kotlin.time.Clock

/** Refresh display time when the host or navigation entry resumes on any Compose platform. */
@Composable
internal fun rememberTimeOnResume(
    vararg keys: Any?,
    nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentClock by rememberUpdatedState(nowMillis)
    val time = remember(lifecycleOwner, *keys) { mutableLongStateOf(nowMillis()) }
    DisposableEffect(lifecycleOwner, time) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) time.longValue = currentClock()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return time.longValue
}

@Composable
internal fun relativeTimeOnResume(createdAtEpochSeconds: Int): String =
    ItemTimeFormatter.format(createdAtEpochSeconds, rememberTimeOnResume(createdAtEpochSeconds))
