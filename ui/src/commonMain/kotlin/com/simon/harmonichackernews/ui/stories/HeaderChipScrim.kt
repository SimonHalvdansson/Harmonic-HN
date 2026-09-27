package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.ui.navigation.LocalSplitPaneLayout
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

/** Softens the internal pane boundary without covering chips once the row reaches its end. */
@Composable
internal fun Modifier.headerChipEndScrim(listState: LazyListState): Modifier {
    if (!LocalSplitPaneLayout.current.supportsTwoPane) return this

    val background = HarmonicTheme.colors.background
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
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
