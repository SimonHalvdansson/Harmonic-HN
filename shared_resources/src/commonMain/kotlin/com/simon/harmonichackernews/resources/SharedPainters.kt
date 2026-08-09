package com.simon.harmonichackernews.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Loads a shared drawable while preserving the theme-tint behavior of Android vectors. */
@Composable
fun tintedPainterResource(resource: DrawableResource, color: Color): Painter {
    val painter = painterResource(resource)
    return remember(painter, color) {
        ColorFilterPainter(painter, ColorFilter.tint(color))
    }
}

private class ColorFilterPainter(
    private val painter: Painter,
    private val colorFilter: ColorFilter,
) : Painter() {
    override val intrinsicSize: Size
        get() = painter.intrinsicSize

    override fun DrawScope.onDraw() {
        with(painter) {
            draw(size = size, colorFilter = colorFilter)
        }
    }
}
