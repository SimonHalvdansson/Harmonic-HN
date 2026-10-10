package com.simon.harmonichackernews.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_back
import org.jetbrains.compose.resources.painterResource
import dev.chrisbanes.haze.HazeSourceSelection

/** A fixed, host-positioned back control shared by full-screen feature destinations. */
@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
fun TranslucentBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    modalScrimAlpha: Float = 0f,
    modalScrimActive: Boolean = modalScrimAlpha > 0f,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Read in composition so each frame supplies a new immutable shape. Shadow renderers cache
    // by shape identity and can retain a stale outline if CornerSize reads animation state itself.
    val pressProgress = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "back button shape",
    ).value
    val pressedCorner = (ButtonDefaults.pressedShape as RoundedCornerShape).topStart
    // Share the Material button morph with the backdrop, shadow, ripple and modal scrim.
    val shape = RoundedCornerShape(object : CornerSize {
        override fun toPx(shapeSize: Size, density: Density): Float {
            val restingRadius = shapeSize.minDimension / 2f
            return restingRadius + (pressedCorner.toPx(shapeSize, density) - restingRadius) * pressProgress
        }
    })
    val hazeState = currentSharedHazeState()
    val surfaceColor = colors.surfaceContainerHigh.copy(alpha = 0.5f)
    val glassEnabled = LocalHazeGlassEnabled.current
    val backdropSelection = remember { HazeSourceSelection.Behind.where { it.zIndex <= 0f } }

    val immediateTooltipState = rememberTooltipState()
    val tooltipState = remember(immediateTooltipState) {
        HoverDelayedTooltipState(immediateTooltipState)
    }
    val hapticFeedback = LocalHapticFeedback.current
    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    // TooltipBox applies its modifier to an inner anchor, below its own layout wrapper.
    // Keep host positioning and z-order on the actual sibling of modal transition layers.
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.Below,
            ),
            tooltip = { PlainTooltip { Text("Back") } },
            state = tooltipState,
        ) {
            Box {
                // Native elevation shadows have a polygonal cutout which shows through transparent
                // surfaces over the WebView. Draw a soft shadow strictly outside the button instead.
                Spacer(
                    Modifier.matchParentSize()
                        .drawWithCache {
                            val outline = Path().apply {
                                addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
                            }
                            onDrawWithContent {
                                clipPath(outline, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
                            }
                        }
                        .dropShadow(shape, Shadow(
                            radius = if (glassEnabled) 2.dp else 8.dp,
                            color = Color.Black.copy(alpha = 0.24f),
                            offset = DpOffset(0.dp, 2.dp),
                        )),
                )
                Surface(
                    // Multiply only the control's pixels. A black overlay dims the already-dimmed
                    // backdrop again through the translucent material. Keep the shadow outside
                    // this offscreen layer so its bounds cannot clip the shadow during a modal.
                    modifier = Modifier.graphicsLayer {
                        val brightness = 1f - modalScrimAlpha.coerceIn(0f, 1f)
                        colorFilter = if (brightness < 1f) {
                            ColorFilter.tint(Color(brightness, brightness, brightness), BlendMode.Modulate)
                        } else null
                    },
                    shape = shape,
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = colors.onSurface,
                    shadowElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier
                            .sharedHazeBackground(
                                hazeState, surfaceColor, shape,
                                sourceSelection = backdropSelection,
                            )
                            .then(
                                // Preserve the original back button's extra tint when glass is disabled.
                                if (!glassEnabled && hazeState != null) {
                                    Modifier.background(surfaceColor)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Box(
                            // Draw the ripple above the backdrop, inside the shared shape clip.
                            modifier = Modifier.size(48.dp).clickable(
                                interactionSource = interactionSource,
                                indication = ripple(),
                                role = Role.Button,
                                onClick = onClick,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(Res.drawable.ic_arrow_back),
                                contentDescription = "Back",
                                modifier = Modifier.size(20.dp),
                                colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
                            )
                        }
                        ModalControlScrim(0f, shape, modalScrimActive)
                    }
                }
            }
        }
    }
}
