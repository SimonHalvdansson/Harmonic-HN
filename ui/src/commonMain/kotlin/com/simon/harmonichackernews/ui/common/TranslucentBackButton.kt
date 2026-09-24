package com.simon.harmonichackernews.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_back
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

/** A fixed, host-positioned back control shared by full-screen feature destinations. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun TranslucentBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    modalScrimAlpha: Float = 0f,
    modalScrimActive: Boolean = modalScrimAlpha > 0f,
) {
    val colors = HarmonicTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "back button shape",
    )
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

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = colors.onSurface,
        shadowElevation = if (glassEnabled) 2.dp else 8.dp,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier
                .sharedHazeBackground(hazeState, surfaceColor, shape)
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
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(colors.iconTint),
                )
            }
            ModalControlScrim(modalScrimAlpha, shape, modalScrimActive)
        }
    }
}
