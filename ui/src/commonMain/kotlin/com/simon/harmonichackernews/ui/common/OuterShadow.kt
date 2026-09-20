package com.simon.harmonichackernews.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.setOutline
import androidx.compose.ui.unit.Dp

/** Keep the native elevation shadow outside a translucent surface, including its text layers. */
internal fun Modifier.outerShadow(elevation: Dp, shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val interior = Path().apply { addOutline(outline) }
    val shadow = obtainGraphicsLayer().apply {
        record { }
        setOutline(outline)
        shadowElevation = elevation.toPx()
    }
    onDrawWithContent {
        clipPath(interior, ClipOp.Difference) { drawLayer(shadow) }
        drawContent()
    }
}
