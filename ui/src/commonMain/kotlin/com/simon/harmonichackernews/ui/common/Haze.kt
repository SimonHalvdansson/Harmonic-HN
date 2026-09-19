package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Provides the shared backdrop source used by floating translucent controls. */
private val LocalSharedHazeState = compositionLocalOf<HazeState?> { null }

@Composable
fun HazeHost(content: @Composable () -> Unit) {
    val hazeState = rememberHazeState()
    CompositionLocalProvider(LocalSharedHazeState provides hazeState) {
        content()
    }
}

@Composable
internal fun currentSharedHazeState(): HazeState? = LocalSharedHazeState.current

internal fun Modifier.sharedHazeSource(
    hazeState: HazeState?,
    zIndex: Float = 0f,
): Modifier = if (hazeState == null) {
    this
} else {
    hazeSource(hazeState, zIndex = zIndex)
}

internal fun Modifier.sharedHazeBackground(
    hazeState: HazeState?,
    surfaceColor: Color,
    shape: Shape,
): Modifier = clip(shape).then(
    if (hazeState == null) {
        Modifier.background(surfaceColor)
    } else {
        Modifier.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle {
                blurRadius(6.dp)
                colorEffects(listOf(HazeColorEffect.tint(surfaceColor)))
                noiseFactor(0f)
                fallbackColorEffect(HazeColorEffect.tint(surfaceColor))
            },
        )
    },
)
