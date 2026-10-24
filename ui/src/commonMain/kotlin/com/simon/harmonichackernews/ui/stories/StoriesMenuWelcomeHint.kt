@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import com.simon.harmonichackernews.ui.common.TextButton
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationTransitionDurationMillis
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.delay

@Composable
internal fun StoriesMenuWelcomeHint(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable (highlighted: Boolean) -> Unit,
) {
    val state = rememberTooltipState(isPersistent = true)
    LaunchedEffect(visible) {
        if (visible) {
            // The retained Stories page must finish returning from Welcome before opening a popup.
            delay(ActivityNavigationTransitionDurationMillis + 150L)
            state.show()
        } else {
            state.dismiss()
        }
    }
    val dismiss = {
        state.dismiss()
        onDismiss()
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val insets = WindowInsets.safeDrawing
    val left = insets.getLeft(density, layoutDirection)
    val right = insets.getRight(density, layoutDirection)
    val top = insets.getTop(density)
    val bottom = insets.getBottom(density)
    val margin = with(density) { 16.dp.roundToPx() }
    val gap = with(density) { 8.dp.roundToPx() }
    val position = remember(left, right, top, bottom, margin, gap) {
        StoriesMenuHintPositionProvider(left + margin, right + margin, top + margin, bottom + margin, gap)
    }
    val maxWidth = with(density) {
        (LocalWindowInfo.current.containerSize.width - left - right - 2 * margin).coerceAtLeast(1).toDp()
    }.coerceAtMost(320.dp)
    val colors = TooltipDefaults.richTooltipColors()
    TooltipBox(
        positionProvider = position,
        state = state,
        enableUserInput = false,
        hasAction = true,
        focusable = true,
        onDismissRequest = dismiss,
        tooltip = {
            Surface(
                modifier = Modifier.testTag("stories-welcome-hint"),
                shape = remember(position) { StoriesMenuHintShape(position) },
                color = colors.containerColor,
                contentColor = colors.contentColor,
                shadowElevation = 3.dp,
            ) {
                Column(Modifier.width(maxWidth).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                    Text("Make it yours", fontFamily = ProductSansFontFamily,
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium,
                        color = colors.titleContentColor, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(4.dp))
                    Text("Don't forget to sign in and explore the extensive customization options from the menu",
                        fontFamily = ProductSansFontFamily, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = dismiss,
                            colors = ButtonDefaults.textButtonColors(contentColor = colors.actionContentColor),
                        ) {
                            Text("Got it", fontFamily = ProductSansFontFamily)
                        }
                    }
                }
            }
        },
    ) {
        StoriesTooltip("More options", enabled = !visible) { content(state.isVisible) }
    }
}

/** Position the container and its caret together, including edge and cutout corrections. */
private class StoriesMenuHintPositionProvider(
    private val left: Int,
    private val right: Int,
    private val top: Int,
    private val bottom: Int,
    private val gap: Int,
) : PopupPositionProvider {
    var caretX by mutableFloatStateOf(0f)
        private set
    var below by mutableStateOf(true)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - right - popupContentSize.width).coerceAtLeast(left)
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(left, maxX)
        val maxY = (windowSize.height - bottom - popupContentSize.height).coerceAtLeast(top)
        val preferredY = anchorBounds.bottom + gap
        below = preferredY <= maxY
        val y = (if (below) preferredY else anchorBounds.top - gap - popupContentSize.height).coerceIn(top, maxY)
        caretX = (anchorBounds.center.x - x).toFloat()
        return IntOffset(x, y)
    }
}

private class StoriesMenuHintShape(private val position: StoriesMenuHintPositionProvider) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = with(density) { 12.dp.toPx() }
        val halfCaret = with(density) { 8.dp.toPx() }
        val x = position.caretX.coerceIn(radius + halfCaret, size.width - radius - halfCaret)
        val y = if (position.below) 0f else size.height
        val tip = y + if (position.below) -halfCaret else halfCaret
        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius))) }
        val caret = Path().apply {
            moveTo(x - halfCaret, y)
            lineTo(x, tip)
            lineTo(x + halfCaret, y)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, caret))
    }
}
