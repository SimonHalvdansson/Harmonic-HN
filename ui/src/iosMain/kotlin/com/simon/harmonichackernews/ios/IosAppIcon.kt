package com.simon.harmonichackernews.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.harmonic_app_icon
import org.jetbrains.compose.resources.painterResource

/** Apply the iOS icon silhouette to the shared, unmasked brand artwork. */
@Composable
internal fun rememberIosAppIconPainter(): Painter {
    val artwork = painterResource(Res.drawable.harmonic_app_icon)
    return remember(artwork) {
        object : Painter() {
            override val intrinsicSize = artwork.intrinsicSize

            override fun DrawScope.onDraw() {
                val radius = size.minDimension * 0.2237f
                val mask = Path().apply {
                    addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
                }
                clipPath(mask) {
                    with(artwork) { draw(size = size) }
                }
            }
        }
    }
}
