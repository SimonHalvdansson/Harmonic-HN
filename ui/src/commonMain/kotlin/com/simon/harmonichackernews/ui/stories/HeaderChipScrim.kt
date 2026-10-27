package com.simon.harmonichackernews.ui.stories

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.ui.navigation.LocalSplitPaneLayout

/** Softens pane and inset boundaries without covering chips once the row reaches its end. */
@Composable
internal fun Modifier.headerChipEndScrim(listState: LazyListState): Modifier {
    val direction = LocalLayoutDirection.current
    val density = LocalDensity.current
    val rtl = direction == LayoutDirection.Rtl
    val insets = WindowInsets.safeDrawing
    val endInset = if (rtl) insets.getLeft(density, direction) else insets.getRight(density, direction)
    if (!LocalSplitPaneLayout.current.supportsTwoPane && endInset == 0) return this

    val background = MaterialTheme.colorScheme.pageBackground
    return drawWithCache {
        val width = 24.dp.toPx().coerceAtMost(size.width)
        val left = if (rtl) 0f else size.width - width
        val transparent = background.copy(alpha = 0f)
        val brush = Brush.horizontalGradient(
            colors = if (rtl) listOf(background, transparent) else listOf(transparent, background),
            startX = left,
            endX = left + width,
        )
        onDrawWithContent {
            drawContent()
            if (listState.canScrollForward) {
                drawRect(brush, topLeft = Offset(left, 0f), size = Size(width, size.height))
            }
        }
    }
}
