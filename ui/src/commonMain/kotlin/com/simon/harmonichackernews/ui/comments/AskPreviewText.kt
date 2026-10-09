package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.constrainHeight
import kotlin.math.roundToInt

/** Keeps both text layouts available so collapsing never removes the outgoing lines early. */
@Composable
internal fun AskPreviewText(
    expanded: Boolean,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    content: @Composable (maxLines: Int) -> Unit,
) {
    val transition = updateTransition(expanded, label = "Ask preview")
    val expansion by transition.animateFloat(
        transitionSpec = { tween(300, easing = AskEasing) }, label = "Height",
    ) { if (it) 1f else 0f }
    val textAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(150, delayMillis = 50) else tween(100)
        }, label = "Extra text",
    ) { if (it) 1f else 0f }
    val lineHeightPx = with(LocalDensity.current) { lineHeight.toPx() }
    val bounds = remember { AskPreviewTextBounds() }
    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            Box(
                Modifier.then(if (expanded) Modifier.clearAndSetSemantics {} else Modifier)
                    .drawWithContent {
                        if (bounds.stableHeight >= size.height || (expansion == 0f && textAlpha == 0f)) {
                            this@drawWithContent.drawContent()
                            return@drawWithContent
                        }
                        // Only the ellipsized final line changes; the preceding lines stay opaque.
                        clipRect(top = bounds.stableHeight) {
                            val alpha = 1f - textAlpha
                            if (alpha > 0f) {
                                drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height),
                                    Paint().apply { this.alpha = alpha })
                                this@drawWithContent.drawContent()
                                drawContext.canvas.restore()
                            }
                        }
                    },
            ) { content(3) }
            Box(
                Modifier.then(if (!expanded) Modifier.clearAndSetSemantics {} else Modifier)
                    .drawWithContent {
                        clipRect(bottom = bounds.stableHeight) { this@drawWithContent.drawContent() }
                        if (textAlpha > 0f) clipRect(top = bounds.stableHeight) {
                            drawContext.canvas.saveLayer(Rect(0f, 0f, size.width, size.height),
                                Paint().apply { alpha = textAlpha })
                            this@drawWithContent.drawContent()
                            drawContext.canvas.restore()
                        }
                    },
            ) { content(Int.MAX_VALUE) }
        },
    ) { measurables, constraints ->
        val textConstraints = constraints.copy(minHeight = 0)
        val collapsed = measurables[0].measure(textConstraints)
        val full = measurables[1].measure(textConstraints)
        bounds.stableHeight = if (full.height <= collapsed.height) full.height.toFloat()
            else (collapsed.height - lineHeightPx).coerceAtLeast(0f)
        val height = (collapsed.height + (full.height - collapsed.height) * expansion).roundToInt()
        layout(maxOf(collapsed.width, full.width), constraints.constrainHeight(height)) {
            // The hidden layout is measured but not placed once the transition has settled.
            if (full.height <= collapsed.height) {
                if (expanded) full.placeRelative(0, 0) else collapsed.placeRelative(0, 0)
            } else if (expansion == 0f && textAlpha == 0f) {
                collapsed.placeRelative(0, 0)
            } else {
                if (full.height > collapsed.height && textAlpha < 1f) collapsed.placeRelative(0, 0)
                full.placeRelative(0, 0)
            }
        }
    }
}

private class AskPreviewTextBounds {
    var stableHeight = 0f
}
