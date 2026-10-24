package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.pageBackground
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Corner decorations stay in window coordinates, leaving the scrolling content's center quiet. */
@Composable
internal fun WelcomeBackground(time: State<Double>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val dark = HarmonicTheme.isDark
    val background = scheme.pageBackground
    val warm = lerp(scheme.primary, Color(0xFFFFAD85), .9f)
    val blush = lerp(scheme.tertiary, Color(0xFFFFB9A5), .8f)
    val glows = remember(warm, blush, dark) {
        listOf(warm, blush).map { color ->
            Brush.radialGradient(
                0f to color.copy(alpha = if (dark) .17f else .48f),
                .45f to color.copy(alpha = if (dark) .065f else .23f),
                1f to color.copy(alpha = 0f),
                center = Offset.Zero,
                radius = 1f,
            )
        }
    }
    Canvas(modifier) {
        drawRect(background)
        val millis = time.value
        val upper = WelcomeBackgroundMotion.field(0, millis)
        val lower = WelcomeBackgroundMotion.field(1, millis)
        val extentX = min(size.width * .92f, 480.dp.toPx())
        val extentY = min(size.height * .5f, 460.dp.toPx())
        val upperCenter = Offset(upper.x.dp.toPx(), upper.y.dp.toPx())
        val lowerCenter = Offset(size.width + lower.x.dp.toPx(), size.height + lower.y.dp.toPx())

        fun glow(center: Offset, radiusX: Float, radiusY: Float, brush: Brush, alpha: Float) {
            withTransform({
                translate(center.x, center.y)
                scale(radiusX, radiusY, Offset.Zero)
            }) { drawCircle(brush, radius = 1f, center = Offset.Zero, alpha = alpha) }
        }
        glow(upperCenter, extentX * 1.4f * upper.scale, extentY * 1.25f * upper.scale, glows[0], upper.alpha)
        glow(lowerCenter, extentX * 1.5f * lower.scale, extentY * 1.25f * lower.scale, glows[1], lower.alpha)
        glow(Offset(size.width * .22f + lower.x.dp.toPx(), size.height * .98f),
            extentX * 1.1f, extentY * .8f * lower.scale, glows[0], lower.alpha * .55f)

        // The grid is anchored to the window; only the glows use the drifting centers above.
        drawWelcomeCornerDots(Offset.Zero, extentX, extentY, false, warm, blush, millis, dark)
        drawWelcomeCornerDots(Offset(size.width, size.height), extentX, extentY, true, blush, warm, millis, dark)
    }
}

internal fun DrawScope.drawWelcomeCornerDots(
    center: Offset,
    extentX: Float,
    extentY: Float,
    mirrored: Boolean,
    color: Color,
    pulseColor: Color,
    millis: Double,
    dark: Boolean,
) {
    val spacing = 18.dp.toPx()
    val seconds = if (millis < 0) 0.0 else millis / 1000
    val direction = if (mirrored) -1 else 1
    val columns = ceil(extentX / spacing).toInt()
    val rows = ceil(extentY / spacing).toInt()
    for (row in -1..rows) {
        for (column in -1..columns) {
            val x = column * spacing + 6.dp.toPx()
            val y = row * spacing + 6.dp.toPx()
            val distance = sqrt((x / extentX) * (x / extentX) + (y / extentY) * (y / extentY))
            val falloff = (1f - distance).coerceIn(0f, 1f)
            if (falloff <= .025f) continue
            val pulse = (.5 + .5 * sin(seconds * .75 - row * .24 - column * .18)).toFloat()
            drawCircle(
                color = lerp(color, pulseColor, pulse),
                radius = 2.4.dp.toPx() * (.6f + .4f * falloff) * (.72f + .28f * pulse),
                center = Offset(center.x + direction * x, center.y + direction * y),
                alpha = (if (dark) .18f else .6f) * falloff * falloff * (.75f + .25f * pulse),
            )
        }
    }
}

internal object WelcomeBackgroundMotion {
    data class Field(val x: Float, val y: Float, val scale: Float, val alpha: Float)

    fun field(index: Int, millis: Double): Field {
        val seconds = if (millis < 0) 0.0 else millis / 1000
        val phase = index * PI * .7
        return Field(
            x = (10 * sin(seconds * 2 * PI / 19 + phase)).toFloat(),
            y = (14 * sin(seconds * 2 * PI / 23 + phase)).toFloat(),
            scale = (1 + .06 * sin(seconds * 2 * PI / 13 + phase)).toFloat(),
            alpha = (.85 + .15 * sin(seconds * 2 * PI / 11 + phase)).toFloat(),
        )
    }
}
