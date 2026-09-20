package com.simon.harmonichackernews.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import com.simon.harmonichackernews.settings.GlassParameter
import com.simon.harmonichackernews.settings.GlassSwitch
import com.simon.harmonichackernews.settings.SurfaceEffectMode
import com.simon.harmonichackernews.settings.SurfaceEffectPreferences
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/** Provides the shared backdrop source used by floating translucent controls. */
private val LocalSharedHazeState = compositionLocalOf<HazeState?> { null }

/** Whether the selected appearance uses glass, supplied by the observable settings environment. */
internal val LocalHazeGlassEnabled = compositionLocalOf { false }

/** Null preserves standalone previews that do not provide an appearance environment. */
internal val LocalHazePreferences = compositionLocalOf<SurfaceEffectPreferences?> { null }

internal enum class HazeGlassAppearance {
    Subtle,
    FloatingButton,
    Dialog,
}

/** The selected surface material also follows the dialog's container transform. */
@Composable
internal fun Modifier.sharedHazeDialogBackground(
    surfaceColor: Color,
    shape: RoundedCornerShape,
    revealProgress: Float = 1f,
): Modifier {
    val hazeState = currentSharedHazeState()
    val preferences = LocalHazePreferences.current
    return if (preferences != null && preferences.mode != SurfaceEffectMode.Solid && hazeState != null) {
        sharedHazeBackground(
            hazeState = hazeState,
            surfaceColor = surfaceColor,
            shape = shape,
            blurRadius = 16.dp,
            glassAppearance = HazeGlassAppearance.Dialog,
            revealProgress = revealProgress,
        )
    } else {
        clip(shape).background(surfaceColor)
    }
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
    revealProgress: Float = 1f,
): Modifier {
    val preferences = LocalHazePreferences.current ?: SurfaceEffectPreferences()
    val glass = preferences.glass
    val progress = revealProgress.coerceIn(0f, 1f)
    return clip(shape).then(if (preferences.mode == SurfaceEffectMode.Solid) {
        Modifier.background(surfaceColor.copy(alpha = 1f))
    } else if (hazeState == null) {
        Modifier.background(surfaceColor)
    } else if (preferences.mode == SurfaceEffectMode.Glass) {
        Modifier.hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = GlassStyle.regular.then {
                shape(shape)
                backgroundColor(surfaceColor.copy(alpha = glass[GlassParameter.BackgroundOpacity]))
                val tintAlpha = when (glassAppearance) {
                    HazeGlassAppearance.FloatingButton -> glass[GlassParameter.ButtonTint]
                    HazeGlassAppearance.Dialog -> surfaceColor.alpha *
                        (1f - (1f - glass[GlassParameter.DialogTint]) * progress)
                    HazeGlassAppearance.Subtle ->
                        (surfaceColor.alpha * glass[GlassParameter.SubtleTint] / 0.5f).coerceIn(0f, 1f)
                }
                tint(surfaceColor.copy(alpha = if (glass[GlassSwitch.TintEnabled]) tintAlpha else 0f))
                // Preserve the gentler lighting of back buttons/badges and large dialogs.
                val highlightScale = when (glassAppearance) {
                    HazeGlassAppearance.FloatingButton -> 1f
                    HazeGlassAppearance.Dialog -> 0.35f / 0.45f
                    HazeGlassAppearance.Subtle -> 0.4f
                }
                specularIntensity(glass[GlassParameter.SpecularIntensity] * highlightScale)
                ambientResponse(glass[GlassParameter.AmbientResponse] *
                    if (glassAppearance == HazeGlassAppearance.Subtle) 0.5f else 1f)
                lightPosition(BiasAlignment(glass[GlassParameter.LightX], glass[GlassParameter.LightY]))
                contentNormalBlend(glass[GlassParameter.ContentNormalBlend])
                edgeSoftness(glass[GlassParameter.EdgeSoftness].dp)
                specularExponent(glass[GlassParameter.SpecularExponent])
                fresnelExponent(glass[GlassParameter.FresnelExponent])
                alpha(glass[GlassParameter.MaterialOpacity])
                contrast(glass[GlassParameter.Contrast])
                whitePoint(glass[GlassParameter.WhitePoint])
                chromaMultiplier(glass[GlassParameter.ChromaMultiplier])
                surfaceProfile(SurfaceProfile.valueOf(glass.surfaceProfile.name))
                chromaticAberrationStrength(
                    if (glass[GlassSwitch.ChromaticAberration]) glass[GlassParameter.ChromaticAberration] else 0f,
                )
                chromaticAberrationMode(
                    if (glass[GlassSwitch.FullChromaticAberration]) ChromaticAberrationMode.Full
                    else ChromaticAberrationMode.Simple,
                )
                // Adaptive optics suppress sharp text fragments in the default frosted glass.
                if (!glass[GlassSwitch.AdaptiveOptics]) {
                    optics(GlassOptics.Fixed(
                        refractionStrength = glass[GlassParameter.RefractionStrength],
                        refractionHeightFraction = glass[GlassParameter.RefractionHeight],
                        refractionDisplacement = glass[GlassParameter.RefractionDisplacement].dp,
                        depth = glass[GlassParameter.Depth],
                        blurRadius = glass[GlassParameter.BlurRadius].dp,
                        refractionFoldStrength = glass[GlassParameter.RefractionFold],
                    ))
                }
            },
        )
    } else {
        val frostedColor = if (glassAppearance == HazeGlassAppearance.Dialog) {
            surfaceColor.copy(alpha = surfaceColor.alpha * (1f - 0.22f * progress))
        } else surfaceColor
        Modifier.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle {
                blurRadius(blurRadius)
                colorEffects(listOf(HazeColorEffect.tint(frostedColor)))
                noiseFactor(0f)
                fallbackColorEffect(HazeColorEffect.tint(frostedColor))
            },
        )
    })
}
