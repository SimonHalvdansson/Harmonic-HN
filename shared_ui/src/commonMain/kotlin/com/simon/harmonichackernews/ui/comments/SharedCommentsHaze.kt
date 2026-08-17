package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Provides the shared backdrop source used by the comments screen's floating controls. */
private val LocalCommentsHazeState = compositionLocalOf<HazeState?> { null }

@Composable
fun SharedCommentsHazeHost(content: @Composable () -> Unit) {
    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalCommentsHazeState provides hazeState) {
        content()
    }
}

@Composable
internal fun currentCommentsHazeState(): HazeState? = LocalCommentsHazeState.current

internal fun Modifier.commentsHazeSource(hazeState: HazeState?): Modifier =
    if (hazeState == null) this else hazeSource(hazeState)
