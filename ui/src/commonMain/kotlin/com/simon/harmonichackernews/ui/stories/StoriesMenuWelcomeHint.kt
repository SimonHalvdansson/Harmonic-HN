@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    contentInsets: WindowInsets = WindowInsets.safeDrawing,
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
    fun WindowInsets.snapshot() = StoriesMenuHintInsets(
        getLeft(density, layoutDirection), getTop(density),
        getRight(density, layoutDirection), getBottom(density),
    )
    val insets = contentInsets.snapshot()
    val systemInsets = WindowInsets.systemBars.snapshot()
    val containerSize = LocalWindowInfo.current.containerSize
    val margin = with(density) { 16.dp.roundToPx() }
    val gap = with(density) { 8.dp.roundToPx() }
    val position = remember(insets, systemInsets, containerSize, margin, gap) {
        StoriesMenuHintPositionProvider(insets, systemInsets, containerSize, margin, gap)
    }
    val maxWidth = with(density) {
        (containerSize.width - insets.left - insets.right - 2 * margin).coerceAtLeast(1).toDp()
    }.coerceAtMost(320.dp)
    val maxHeight = with(density) {
        (containerSize.height - insets.top - insets.bottom - 2 * margin).coerceAtLeast(1).toDp()
    }
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
                Column(Modifier.width(maxWidth).heightIn(max = maxHeight)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Text("Make it yours", fontFamily = ProductSansFontFamily,
                            fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium,
                            color = colors.titleContentColor, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(4.dp))
                    Text("Login and extensive customization options are available from here when you feel like it",
                            fontFamily = ProductSansFontFamily, style = MaterialTheme.typography.bodyMedium)
                    }
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

internal data class StoriesMenuHintInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

internal enum class StoriesMenuHintPlacement { Above, Below, Left, Right }

/** Position the container and its caret together, including edge and cutout corrections. */
internal class StoriesMenuHintPositionProvider(
    private val insets: StoriesMenuHintInsets,
    private val platformInsets: StoriesMenuHintInsets,
    private val containerSize: IntSize,
    private val margin: Int,
    private val gap: Int,
) : PopupPositionProvider {
    var caretOffset by mutableFloatStateOf(0f)
        private set
    var placement by mutableStateOf(StoriesMenuHintPlacement.Below)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        // Some popup hosts (including iOS) remove system bars before calling the provider.
        // Work in that same coordinate space rather than subtracting those insets twice.
        val horizontalInsetsRemoved = containerSize.width - windowSize.width ==
            platformInsets.left + platformInsets.right
        val verticalInsetsRemoved = containerSize.height - windowSize.height ==
            platformInsets.top + platformInsets.bottom
        val left = margin + (insets.left - if (horizontalInsetsRemoved) platformInsets.left else 0).coerceAtLeast(0)
        val right = margin + (insets.right - if (horizontalInsetsRemoved) platformInsets.right else 0).coerceAtLeast(0)
        val top = margin + (insets.top - if (verticalInsetsRemoved) platformInsets.top else 0).coerceAtLeast(0)
        val bottom = margin + (insets.bottom - if (verticalInsetsRemoved) platformInsets.bottom else 0).coerceAtLeast(0)
        val maxX = (windowSize.width - right - popupContentSize.width).coerceAtLeast(left)
        val maxY = (windowSize.height - bottom - popupContentSize.height).coerceAtLeast(top)
        // A menu outside the safe content area belongs to a side rail. Point sideways at it,
        // independent of device name, screen proportions, or the rail's physical width.
        val besideRightRail = anchorBounds.center.x > windowSize.width - right &&
            anchorBounds.left - gap - popupContentSize.width >= left
        val besideLeftRail = anchorBounds.center.x < left &&
            anchorBounds.right + gap + popupContentSize.width <= windowSize.width - right
        if (besideRightRail || besideLeftRail) {
            placement = if (besideRightRail) StoriesMenuHintPlacement.Left else StoriesMenuHintPlacement.Right
            val x = (if (besideRightRail) anchorBounds.left - gap - popupContentSize.width
                else anchorBounds.right + gap).coerceIn(left, maxX)
            val y = (anchorBounds.center.y - popupContentSize.height / 2).coerceIn(top, maxY)
            caretOffset = (anchorBounds.center.y - y).toFloat()
            return IntOffset(x, y)
        }
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(left, maxX)
        val preferredY = anchorBounds.bottom + gap
        val below = preferredY <= maxY
        placement = if (below) StoriesMenuHintPlacement.Below else StoriesMenuHintPlacement.Above
        val y = (if (below) preferredY else anchorBounds.top - gap - popupContentSize.height).coerceIn(top, maxY)
        caretOffset = (anchorBounds.center.x - x).toFloat()
        return IntOffset(x, y)
    }
}

private class StoriesMenuHintShape(private val position: StoriesMenuHintPositionProvider) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val horizontal = position.placement == StoriesMenuHintPlacement.Left ||
            position.placement == StoriesMenuHintPlacement.Right
        val edgeLength = if (horizontal) size.height else size.width
        val radius = minOf(with(density) { 12.dp.toPx() }, size.minDimension / 2f)
        val halfCaret = minOf(with(density) { 8.dp.toPx() }, (edgeLength / 2f - radius).coerceAtLeast(0f))
        val offset = position.caretOffset.coerceIn(radius + halfCaret, edgeLength - radius - halfCaret)
        val body = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius))) }
        if (halfCaret == 0f) return Outline.Generic(body)
        val caret = Path().apply {
            when (position.placement) {
                StoriesMenuHintPlacement.Left, StoriesMenuHintPlacement.Right -> {
                    val x = if (position.placement == StoriesMenuHintPlacement.Left) size.width else 0f
                    val tip = x + if (position.placement == StoriesMenuHintPlacement.Left) halfCaret else -halfCaret
                    moveTo(x, offset - halfCaret)
                    lineTo(tip, offset)
                    lineTo(x, offset + halfCaret)
                }
                StoriesMenuHintPlacement.Above, StoriesMenuHintPlacement.Below -> {
                    val y = if (position.placement == StoriesMenuHintPlacement.Below) 0f else size.height
                    val tip = y + if (position.placement == StoriesMenuHintPlacement.Below) -halfCaret else halfCaret
                    moveTo(offset - halfCaret, y)
                    lineTo(offset, tip)
                    lineTo(offset + halfCaret, y)
                }
            }
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, body, caret))
    }
}
