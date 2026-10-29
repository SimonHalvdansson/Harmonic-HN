package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun HeaderChipScrollContainer(
    state: LazyListState,
    modifier: Modifier,
    contentPadding: PaddingValues,
    content: @Composable (Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val wheelStep = with(LocalDensity.current) { 48.dp.toPx() }
    val direction = LocalLayoutDirection.current
    val colors = MaterialTheme.colorScheme
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val dragging by interactions.collectIsDraggedAsState()
    Column(modifier.hoverable(interactions)) {
        content(
            Modifier.fillMaxWidth().onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                if (event.changes.any { it.isConsumed }) return@onPointerEvent
                // Leave horizontal/Shift-wheel gestures to LazyRow's native scrolling. Only
                // translate a vertical wheel over this row into movement through its options.
                if (event.changes.any { it.scrollDelta.x != 0f }) return@onPointerEvent
                val delta = event.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat() * wheelStep
                if (!delta.isFinite() || delta == 0f) return@onPointerEvent
                if (if (delta > 0f) !state.canScrollForward else !state.canScrollBackward) return@onPointerEvent
                event.changes.forEach { it.consume() }
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    state.scroll(MutatePriority.UserInput) { scrollBy(delta) }
                }
            },
        )
        // Reserve a small track even when everything fits, avoiding a header jump on resize.
        Box(
            Modifier.fillMaxWidth().height(8.dp).padding(
                start = contentPadding.calculateStartPadding(direction),
                end = contentPadding.calculateEndPadding(direction),
            ),
        ) {
            if (state.canScrollForward || state.canScrollBackward) {
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(state),
                    // Keep the thumb mounted during a drag, even if the pointer leaves the row.
                    modifier = Modifier.fillMaxWidth().alpha(if (hovered || dragging) 1f else 0f),
                    interactionSource = interactions,
                    style = LocalScrollbarStyle.current.copy(
                        thickness = 6.dp,
                        unhoverColor = colors.onSurface.copy(alpha = 0.24f),
                        hoverColor = colors.onSurface.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }
}
