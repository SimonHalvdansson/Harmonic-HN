package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Provides the shared backdrop source used by floating translucent controls. */
private val LocalSharedHazeState = compositionLocalOf<HazeState?> { null }

/** Opt-in experiment supplied once by the host's observable settings environment. */
internal val LocalHazeGlassEnabled = compositionLocalOf { false }

internal enum class HazeGlassAppearance {
    Subtle,
    FloatingButton,
}

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

@Composable
@OptIn(ExperimentalHazeApi::class)
internal fun Modifier.sharedHazeBackground(
    hazeState: HazeState?,
    surfaceColor: Color,
    shape: RoundedCornerShape,
    blurRadius: Dp = 6.dp,
    glassAppearance: HazeGlassAppearance = HazeGlassAppearance.Subtle,
): Modifier = clip(shape).then(
    if (hazeState == null) {
        Modifier.background(surfaceColor)
    } else if (LocalHazeGlassEnabled.current) {
        Modifier.hazeGlass(
            input = HazeInput.Sources(hazeState),
            // Regular diffuses the backdrop without Fixed optics' sharp edge-detail layer.
            style = GlassStyle.regular.then {
                shape(shape)
                backgroundColor(surfaceColor.copy(alpha = 1f))
                // FABs' normal 80% tint masks the optics. Let more backdrop through and
                // give their rim a stronger highlight, without sharpening background text.
                val floatingButton = glassAppearance == HazeGlassAppearance.FloatingButton
                tint(if (floatingButton) surfaceColor.copy(alpha = 0.4f) else surfaceColor)
                specularIntensity(if (floatingButton) 0.45f else 0.18f)
                ambientResponse(if (floatingButton) 0.16f else 0.08f)
                lightPosition(Alignment.TopStart)
                contentNormalBlend(0f)
                edgeSoftness(1.dp)
                chromaticAberrationStrength(0f)
            },
        )
    } else {
        Modifier.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle {
                blurRadius(blurRadius)
                colorEffects(listOf(HazeColorEffect.tint(surfaceColor)))
                noiseFactor(0f)
                fallbackColorEffect(HazeColorEffect.tint(surfaceColor))
            },
        )
    },
)
