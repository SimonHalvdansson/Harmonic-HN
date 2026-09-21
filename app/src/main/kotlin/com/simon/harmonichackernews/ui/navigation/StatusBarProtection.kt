package com.simon.harmonichackernews.ui.navigation

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@Composable
internal fun StatusBarProtection(
    color: Color,
    statusBarHeight: Dp,
    modalScrimAlpha: Float = 0f,
) {
    // Above moving modal cards (100), below floating controls (101). Darken only the
    // gradient's color: a second black overlay would double-dim the content underneath.
    val protectedColor = Color.Black.copy(alpha = modalScrimAlpha.coerceIn(0f, 1f))
        .compositeOver(color)
    val height = statusBarHeight + 4.dp
    val insetFraction = statusBarHeight / height
    val brush = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberStatusBarBrush(protectedColor, insetFraction)
    } else {
        remember(protectedColor, insetFraction) {
            // Sample each segment separately so even a short tail stays smooth on
            // tall status bars. Include the join exactly; avoid duplicate stops at zero inset.
            val fractions = buildList {
                if (insetFraction > 0f) {
                    repeat(32) { add(insetFraction * it / 32f) }
                }
                repeat(33) { add(insetFraction + (1f - insetFraction) * it / 32f) }
            }
            Brush.verticalGradient(
                *fractions.map { t ->
                    t to protectedColor.copy(alpha = statusBarProtectionAlpha(t, insetFraction))
                }.toTypedArray(),
            )
        }
    }
    Spacer(
        Modifier
            .zIndex(100.5f)
            .fillMaxWidth()
            .height(height)
            .background(brush),
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun rememberStatusBarBrush(color: Color, insetFraction: Float): Brush {
    // Keep the compiled shader when the header tint or modal dim animates.
    val shader = remember { RuntimeShader(STATUS_BAR_SHADER) }
    return remember(shader, color, insetFraction) {
        shader.setColorUniform("scrimColor", color.copy(alpha = 1f).toArgb())
        shader.setFloatUniform("insetFraction", insetFraction)
        object : ShaderBrush() {
            override fun createShader(size: Size): Shader {
                shader.setFloatUniform("height", size.height.coerceAtLeast(1f))
                return shader
            }
        }
    }
}

/** Earlier fade with extra attenuation at low opacity, keeping the top at 92%. */
internal fun statusBarProtectionAlpha(fraction: Float, insetFraction: Float): Float {
    val t = fraction.coerceIn(0f, 1f)
    val s = insetFraction.coerceIn(0f, 1f)
    val opacity = if (t <= s) {
        1f - t * t
    } else {
        val u = (t - s) / (1f - s)
        val v = 1f - u
        val start = 1f - s * s
        // Match the quadratic's slope (-2 * s) at the inset boundary.
        val control = start - (2f * s * (1f - s)) / 3f
        v * v * (v * start + 3f * u * control)
    }
    // Perceptual tuning, not a color-space conversion: the actual contrast depends on
    // the content underneath. Squaring gives the tail zero slope, curvature, and third
    // derivative at its transparent end, without an abrupt low-alpha cutoff.
    return 0.92f * opacity * opacity
}

private const val STATUS_BAR_SHADER = """
    layout(color) uniform half4 scrimColor;
    uniform float height;
    uniform float insetFraction;

    half4 main(float2 position) {
        float t = clamp(position.y / height, 0.0, 1.0);
        float opacity = 1.0 - t * t;
        if (t > insetFraction) {
            float s = insetFraction;
            float u = (t - s) / (1.0 - s);
            float v = 1.0 - u;
            float start = 1.0 - s * s;
            float control = start - (2.0 * s * (1.0 - s)) / 3.0;
            opacity = v * v * (v * start + 3.0 * u * control);
        }
        half alpha = half(0.92 * opacity * opacity);
        return half4(scrimColor.rgb * alpha, alpha);
    }
"""
