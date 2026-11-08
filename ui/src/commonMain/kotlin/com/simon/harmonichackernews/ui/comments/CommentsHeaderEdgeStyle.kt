package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.settings.CommentsHeaderEdge

/** Shared by the scrolling story surface and its compact overlay. Read in layout/draw. */
internal class CommentsHeaderEdgeStyle(
    radius: State<Dp>, padding: State<Dp>, shadow: State<Dp>, spacing: State<Dp>, fade: State<Float>,
) {
    val bottomRadius by radius
    val bottomPadding by padding
    val shadowElevation by shadow
    val edgeHeight by spacing
    val fadeAlpha by fade
}

@Composable
internal fun rememberCommentsHeaderEdgeStyle(tinted: Boolean, edge: CommentsHeaderEdge): CommentsHeaderEdgeStyle {
    val radius = animateDpAsState(if (tinted && edge == CommentsHeaderEdge.ROUNDED) 24.dp else 0.dp, tween(240))
    val padding = animateDpAsState(if (tinted && edge != CommentsHeaderEdge.FADE) 12.dp else 0.dp, tween(240))
    val shadow = animateDpAsState(if (tinted && edge == CommentsHeaderEdge.SHADOW) 1.5.dp else 0.dp, tween(240))
    val spacing = animateDpAsState(when {
        !tinted -> 8.dp
        edge == CommentsHeaderEdge.FADE -> 32.dp
        else -> 16.dp
    }, tween(240))
    val fade = animateFloatAsState(if (tinted && edge == CommentsHeaderEdge.FADE) 1f else 0f, tween(240))
    return remember(radius, padding, shadow, spacing, fade) {
        CommentsHeaderEdgeStyle(radius, padding, shadow, spacing, fade)
    }
}

internal fun Modifier.commentsHeaderSurface(edge: CommentsHeaderEdgeStyle, color: () -> Color): Modifier =
    graphicsLayer {
        // Retain one layer through zero elevation so changing styles cannot flash an old outline.
        shadowElevation = edge.shadowElevation.toPx()
        shape = RoundedCornerShape(bottomStart = edge.bottomRadius, bottomEnd = edge.bottomRadius)
        clip = true
    }.drawBehind { drawRect(color()) }

internal fun Modifier.commentsHeaderFade(edge: CommentsHeaderEdgeStyle, color: () -> Color): Modifier =
    graphicsLayer { alpha = edge.fadeAlpha }.drawWithCache {
        val tint = color()
        val brush = Brush.verticalGradient(
            0f to tint,
            0.25f to tint.copy(alpha = tint.alpha * 0.84f),
            0.55f to tint.copy(alpha = tint.alpha * 0.42f),
            1f to tint.copy(alpha = 0f),
        )
        onDrawBehind { drawRect(brush) }
    }
