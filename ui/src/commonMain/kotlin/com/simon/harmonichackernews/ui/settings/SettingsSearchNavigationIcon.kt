package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Converted from the SVG motion tool's paired cubic paths. The 24dp endpoints follow the
 * Material Symbols Rounded search/back geometry, with the same 2dp weight and tint.
 * Read the retained search expansion while drawing: there is no separate animation clock to
 * restart during a predictive-back cancellation, resize, or reduced-motion transition.
 */
@Composable
internal fun SettingsSearchNavigationIcon(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    tint: (() -> Color)? = null,
) {
    val path = remember { Path() }
    val contentColor = LocalContentColor.current
    Canvas(modifier.size(24.dp).testTag("settings-search-navigation-icon")) {
        val fraction = progress().coerceIn(0f, 1f)
        path.reset()
        path.searchBack(fraction)
        scale(size.width / 24f, size.height / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            drawPath(path, tint?.invoke() ?: contentColor,
                style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun Path.searchBack(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    fun v(from: Float, to: Float) = from + (to - from) * p
    moveTo(v(13.389087f, 12f), v(13.389087f, 18.6f))
    cubicTo(
        v(11.241204f, 10.9f), v(15.536971f, 17.5f),
        v(7.758796f, 9.8f), v(15.536971f, 16.4f),
        v(5.610913f, 8.7f), v(13.389087f, 15.3f),
    )
    cubicTo(
        v(3.463029f, 7.6f), v(11.241204f, 14.2f),
        v(3.463029f, 6.5f), v(7.758796f, 13.1f),
        v(5.610913f, 5.4f), v(5.610913f, 12f),
    )
    cubicTo(
        v(7.758796f, 6.5f), v(3.463029f, 10.9f),
        v(11.241204f, 7.6f), v(3.463029f, 9.8f),
        v(13.389087f, 8.7f), v(5.610913f, 8.7f),
    )
    cubicTo(
        v(15.536971f, 9.8f), v(7.758796f, 7.6f),
        v(15.536971f, 10.9f), v(11.241204f, 6.5f),
        v(13.389087f, 12f), v(13.389087f, 5.4f),
    )
    moveTo(v(13.4f, 5.4f), v(13.4f, 12f))
    lineTo(v(19.6f, 19f), v(19.6f, 12f))
}
