package com.simon.harmonichackernews.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_arrow_back
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource

/** A fixed, host-positioned back control shared by full-screen feature destinations. */
@Composable
fun TranslucentBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    modalScrimAlpha: Float = 0f,
    modalScrimActive: Boolean = modalScrimAlpha > 0f,
) {
    val colors = HarmonicTheme.colors
    val shape = RoundedCornerShape(percent = 50)
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
            Row(
                modifier = Modifier.padding(
                    start = 16.dp,
                    top = 14.dp,
                    end = 18.dp,
                    bottom = 14.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(Res.drawable.ic_arrow_back),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(colors.drawable),
                )
                Text(
                    text = "Back",
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            ModalControlScrim(modalScrimAlpha, shape, modalScrimActive)
        }
    }
}
