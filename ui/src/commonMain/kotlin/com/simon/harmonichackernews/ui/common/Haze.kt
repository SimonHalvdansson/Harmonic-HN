package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Provides the shared backdrop source used by floating translucent controls. */
private val LocalSharedHazeState = compositionLocalOf<HazeState?> { null }

@Composable
fun HazeHost(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalSharedHazeState provides hazeState.takeIf { enabled }) {
        content()
    }
}

@Composable
internal fun currentSharedHazeState(): HazeState? = LocalSharedHazeState.current

internal fun Modifier.sharedHazeSource(hazeState: HazeState?): Modifier =
    if (hazeState == null) this else hazeSource(hazeState)
