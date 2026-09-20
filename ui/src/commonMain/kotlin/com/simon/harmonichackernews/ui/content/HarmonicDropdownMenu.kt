package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

private const val MenuRevealDurationMillis = 220
private const val MenuDismissDurationMillis = 150
private val MenuRevealEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val MenuDismissEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** An anchored surface reveal, with independently fading, unscaled menu content. */
@Composable
fun HarmonicDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = MenuDefaults.ShadowElevation,
    containerColor: Color = HarmonicTheme.colors.popupMenuBackground,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    val transition = rememberTransition(expandedState, label = "menu visibility")
    val reveal by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(MenuRevealDurationMillis, easing = MenuRevealEasing)
            else tween(MenuDismissDurationMillis, easing = MenuDismissEasing)
        },
        label = "menu surface reveal",
    ) { if (it) 1f else 0f }
    val surfaceAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(105, easing = LinearEasing)
            else tween(MenuDismissDurationMillis - 60, delayMillis = 60, easing = LinearEasing)
        },
        label = "menu surface opacity",
    ) { if (it) 1f else 0f }
    val contentAlpha by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(130, delayMillis = 60, easing = LinearEasing)
            // Clear the text before fading the surface, reversing the opening sequence.
            else tween(60, easing = LinearEasing)
        },
        label = "menu content opacity",
    ) { if (it) 1f else 0f }

    if (expandedState.currentState || expandedState.targetState || !expandedState.isIdle) {
        val positionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(
            MenuAnchorPosition.Below,
        )
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismiss,
            properties = PopupProperties(focusable = true),
        ) {
            val focusManager = LocalFocusManager.current
            val inputModeManager = LocalInputModeManager.current
            // Measure the full menu throughout the reveal so popup placement, scrolling and
            // line wrapping remain stable. Only its rounded outline changes, including shadow.
            Surface(
                modifier = Modifier.graphicsLayer { alpha = surfaceAlpha },
                shape = MenuRevealShape(reveal, positionProvider.transformOrigin.pivotFractionY),
                color = containerColor,
                tonalElevation = 0.dp,
                shadowElevation = shadowElevation,
            ) {
                Column(
                    modifier = modifier
                        .padding(vertical = 8.dp)
                        .width(IntrinsicSize.Max)
                        .verticalScroll(scrollState)
                        .graphicsLayer { alpha = contentAlpha }
                        .onPreviewKeyEvent {
                            if (it.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (it.key) {
                                Key.DirectionDown, Key.DirectionUp -> {
                                    inputModeManager.requestInputMode(InputMode.Keyboard)
                                    focusManager.moveFocus(
                                        if (it.key == Key.DirectionDown) FocusDirection.Next
                                        else FocusDirection.Previous,
                                    )
                                    true
                                }
                                Key.Escape -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        },
                    content = content,
                )
            }
        }
    }
}

/** Reveal away from the anchor, including when the menu has to open above it. */
private data class MenuRevealShape(val progress: Float, val pivotY: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val initialHeight = with(density) { 24.dp.toPx() }.coerceAtMost(size.height)
        val height = initialHeight + (size.height - initialHeight) * progress.coerceIn(0f, 1f)
        val top = (size.height - height) * pivotY.coerceIn(0f, 1f)
        val radius = with(density) { 16.dp.toPx() }.coerceAtMost(minOf(size.width, height) / 2f)
        return Outline.Rounded(
            RoundRect(0f, top, size.width, top + height, CornerRadius(radius)),
        )
    }
}

@Composable
fun HarmonicMenuText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HarmonicTheme.colors.textPrimary,
    fontFamily: FontFamily = ProductSansFontFamily,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: TextUnit = 16.sp,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontSize = fontSize,
    )
}
