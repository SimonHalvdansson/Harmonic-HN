package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.simon.harmonichackernews.ui.theme.contrastingColor
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily

internal data class StoryCategoryColors(val container: Color, val foreground: Color)

/** Keep chips in the card's own palette while retaining readable small text. */
internal fun tintedStoryCategoryColors(surface: Color, preferredForeground: Color): StoryCategoryColors {
    val ink = contrastingColor(preferredForeground, listOf(surface), 4.5f)
    val container = lerp(surface, ink, 0.12f)
    return StoryCategoryColors(container, contrastingColor(ink, listOf(container), 4.5f))
}

@Composable
internal fun StoryCategoryLabels(
    labels: List<String>,
    alpha: Float,
    tintedSurface: (() -> Color)? = null,
    animate: Boolean = true,
    topSpacing: Dp = 2.dp,
    bottomSpacing: Dp = 4.dp,
) {
    val scheme = MaterialTheme.colorScheme
    val colors = tintedSurface?.let { tintedStoryCategoryColors(it(), scheme.onSurface) }
        ?: StoryCategoryColors(scheme.secondaryContainer,
            contrastingColor(scheme.onSecondaryContainer, listOf(scheme.secondaryContainer), 4.5f))
    val content: @Composable (List<String>) -> Unit = { visibleLabels ->
        if (visibleLabels.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = topSpacing, bottom = bottomSpacing)
                    .graphicsLayer { this.alpha = alpha },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                visibleLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.background(colors.container, RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = colors.foreground,
                        fontFamily = ProductSansFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
    if (animate) AnimatedContent(
        targetState = labels,
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopStart,
        transitionSpec = {
            (fadeIn(tween(150, delayMillis = 20)) togetherWith fadeOut(tween(100))).using(
                SizeTransform(clip = false) { _, _ -> tween(240, easing = FastOutSlowInEasing) })
        },
        label = "story categories",
    ) { content(it) }
    else content(labels)
}
