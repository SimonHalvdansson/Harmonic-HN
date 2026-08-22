package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.ui.common.SharedHazeHost
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import dev.chrisbanes.haze.HazeState

/** Provides the shared backdrop source used by the comments screen's floating controls. */
@Composable
fun SharedCommentsHazeHost(content: @Composable () -> Unit) = SharedHazeHost(content)

@Composable
internal fun currentCommentsHazeState() = currentSharedHazeState()

internal fun Modifier.commentsHazeSource(hazeState: HazeState?): Modifier =
    sharedHazeSource(hazeState)
