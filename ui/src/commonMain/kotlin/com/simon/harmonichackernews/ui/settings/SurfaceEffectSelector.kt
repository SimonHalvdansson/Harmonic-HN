package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.settings.GlassParameter
import com.simon.harmonichackernews.settings.GlassPreferences
import com.simon.harmonichackernews.settings.GlassSwitch
import com.simon.harmonichackernews.settings.SurfaceEffectMode
import com.simon.harmonichackernews.settings.SurfaceEffectPreferences
import com.simon.harmonichackernews.ui.common.HazeGlassAppearance
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.LocalHazePreferences
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.sharedHazeBackground
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

@Composable
internal fun SurfaceEffectSelector(mode: SurfaceEffectMode, onSelected: (SurfaceEffectMode) -> Unit) {
    SegmentedSetting(
        title = "Surface effect",
        options = SurfaceEffectMode.entries.map { it to it.name },
        selected = mode,
        buttonHeight = 100.dp,
        optionContent = { value, selected ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SurfaceEffectIcon(value)
                Text(
                    text = value.name,
                    color = if (selected) HarmonicTheme.colors.onSecondaryContainer
                        else HarmonicTheme.colors.textPrimary,
                    fontFamily = ProductSansFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        },
        onSelected = onSelected,
    )
}

/** Tiny material samples, intentionally independent of the user's experimental tuning. */
@Composable
private fun SurfaceEffectIcon(mode: SurfaceEffectMode) {
    val shape = RoundedCornerShape(percent = 50)
    val surface = HarmonicTheme.colors.surfaceContainerHigh
    val outline = HarmonicTheme.colors.outlineVariant
    val sample = SurfaceEffectPreferences(
        mode = mode,
        glass = GlassPreferences(
            parameters = mapOf(
                GlassParameter.ButtonTint to 0.24f,
                GlassParameter.SpecularIntensity to 0.7f,
                GlassParameter.BlurRadius to 1f,
                GlassParameter.RefractionDisplacement to 4f,
            ),
            switches = mapOf(GlassSwitch.AdaptiveOptics to false),
        ),
    )
    CompositionLocalProvider(LocalHazePreferences provides sample) {
        HazeHost {
            val hazeState = currentSharedHazeState()
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                if (mode != SurfaceEffectMode.Solid) {
                    Canvas(Modifier.fillMaxSize().sharedHazeSource(hazeState)) {
                        rotate(-28f) {
                            drawRoundRect(
                                Color(0xFF4D9BC5),
                                Offset(size.width * 0.2f, size.height * 0.04f),
                                Size(size.width * 0.23f, size.height * 0.92f),
                                CornerRadius(3.dp.toPx()),
                            )
                            drawRoundRect(
                                Color(0xFFF5A16A),
                                Offset(size.width * 0.57f, size.height * 0.04f),
                                Size(size.width * 0.23f, size.height * 0.92f),
                                CornerRadius(3.dp.toPx()),
                            )
                        }
                    }
                }
                Box(
                    Modifier.size(42.dp)
                        .shadow(1.dp, shape)
                        .then(
                            if (mode == SurfaceEffectMode.Solid) Modifier.background(surface, shape)
                            else Modifier.sharedHazeBackground(
                                hazeState = hazeState,
                                surfaceColor = surface.copy(alpha = 0.5f),
                                shape = shape,
                                blurRadius = 4.dp,
                                glassAppearance = HazeGlassAppearance.FloatingButton,
                            ),
                        )
                        .border(0.5.dp, outline.copy(alpha = 0.7f), shape),
                )
            }
        }
    }
}
