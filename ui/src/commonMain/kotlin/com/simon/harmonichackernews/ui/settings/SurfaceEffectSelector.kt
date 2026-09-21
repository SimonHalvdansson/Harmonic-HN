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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import com.simon.harmonichackernews.settings.SurfaceEffectMode
import com.simon.harmonichackernews.settings.SurfaceEffectPreferences
import com.simon.harmonichackernews.ui.common.HazeHost
import com.simon.harmonichackernews.ui.common.LocalHazePreferences
import com.simon.harmonichackernews.ui.common.currentSharedHazeState
import com.simon.harmonichackernews.ui.common.sharedHazeDialogBackground
import com.simon.harmonichackernews.ui.common.sharedHazeSource
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

@Composable
internal fun SurfaceEffectSelector(mode: SurfaceEffectMode, onSelected: (SurfaceEffectMode) -> Unit) {
    SegmentedSetting(
        title = "Surface effect",
        options = SurfaceEffectMode.entries.map { it to it.displayLabel },
        selected = mode,
        buttonHeight = 76.dp,
        optionContent = { value, selected ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SurfaceEffectIcon(value, selected)
                Text(
                    text = value.displayLabel,
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

private val SurfaceEffectMode.displayLabel: String
    get() = if (this == SurfaceEffectMode.Frosted) "Blur" else name

/** Miniature dialog surfaces use a neutral tint so the backdrop stays distinct in any theme. */
@Composable
private fun SurfaceEffectIcon(mode: SurfaceEffectMode, selected: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    val dark = HarmonicTheme.colors.settingsPageBackground.luminance() < 0.5f
    val surface = if (dark) Color(0xFF252525) else Color(0xFFF5F5F5)
    val backdrop = if (selected) HarmonicTheme.colors.secondaryContainer
        else HarmonicTheme.colors.settingsItemBackground
    val outline = HarmonicTheme.colors.outlineVariant
    val sample = SurfaceEffectPreferences(mode = mode)
    CompositionLocalProvider(
        LocalHazePreferences provides sample,
        // The blur kernel extends beyond this tiny source. Fill that sampled area like
        // an opaque page, so transparent edges cannot reveal the sharp original bars.
        LocalHazeBlurStyle provides HazeBlurStyle { backgroundColor(backdrop) },
    ) {
        HazeHost {
            val hazeState = currentSharedHazeState()
            Box(Modifier.size(width = 56.dp, height = 36.dp), contentAlignment = Alignment.Center) {
                if (mode != SurfaceEffectMode.Solid) {
                    Canvas(Modifier.fillMaxSize().sharedHazeSource(hazeState)) {
                        drawRect(backdrop)
                        rotate(-28f) {
                            drawRoundRect(
                                if (dark) Color(0xFF38C7EF) else Color(0xFF006C9C),
                                Offset(size.width * 0.2f, size.height * 0.04f),
                                Size(size.width * 0.23f, size.height * 0.92f),
                                CornerRadius(3.dp.toPx()),
                            )
                            drawRoundRect(
                                if (dark) Color(0xFFFFB36A) else Color(0xFFBA4400),
                                Offset(size.width * 0.57f, size.height * 0.04f),
                                Size(size.width * 0.23f, size.height * 0.92f),
                                CornerRadius(3.dp.toPx()),
                            )
                        }
                    }
                }
                Box(
                    Modifier.size(width = 44.dp, height = 28.dp)
                        .shadow(1.dp, shape)
                        .then(
                            if (mode == SurfaceEffectMode.Solid) Modifier.background(surface, shape)
                            else Modifier.sharedHazeDialogBackground(
                                surfaceColor = surface,
                                shape = shape,
                            ),
                        )
                        .border(0.5.dp, outline.copy(alpha = 0.7f), shape),
                )
            }
        }
    }
}
