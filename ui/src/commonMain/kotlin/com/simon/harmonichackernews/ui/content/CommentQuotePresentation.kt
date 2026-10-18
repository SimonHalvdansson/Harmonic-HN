package com.simon.harmonichackernews.ui.content

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Color
import com.simon.harmonichackernews.settings.QuoteHighlightColor
import com.simon.harmonichackernews.settings.QuoteHighlightStyle
import com.simon.harmonichackernews.settings.QuoteSpacing
import kotlinx.coroutines.flow.first

internal class CommentQuotePresentation(
    val decorate: Boolean,
    val background: State<Color>,
    val accent: State<Color>,
    val gapFraction: State<Float>,
)

/** Keep the decorated paragraphs until both the color fade and spacing animation have settled. */
@Composable
internal fun rememberCommentQuotePresentation(
    enabled: Boolean,
    spacing: QuoteSpacing,
    color: QuoteHighlightColor,
    style: QuoteHighlightStyle,
    primary: Color,
    onSurface: Color,
): CommentQuotePresentation {
    val base = if (color == QuoteHighlightColor.MATERIAL) primary else onSurface
    val background = animateColorAsState(
        targetValue = if (!enabled || style == QuoteHighlightStyle.SIDE_LINE) Color.Transparent
            else base.copy(alpha = if (color == QuoteHighlightColor.MATERIAL) 0.08f else 0.055f),
        animationSpec = tween(240),
        label = "quote background",
    )
    val accent = animateColorAsState(
        targetValue = if (!enabled || style == QuoteHighlightStyle.BACKGROUND) Color.Transparent
            else base.copy(alpha = 0.4f),
        animationSpec = tween(240),
        label = "quote side line",
    )
    val gap = animateFloatAsState(
        targetValue = if (enabled && spacing == QuoteSpacing.COMPACT) 0.5f else 1f,
        animationSpec = tween(280),
        label = "quote paragraph spacing",
    )
    var retainDecoration by remember { mutableStateOf(enabled) }
    LaunchedEffect(enabled) {
        if (enabled) {
            retainDecoration = true
        } else if (retainDecoration) {
            // Observe completion outside composition. A reversal cancels this wait, and ordinary
            // rows never collect animation frames just to decide whether to keep their layout.
            snapshotFlow { background.value.alpha == 0f && accent.value.alpha == 0f && gap.value == 1f }
                .first { it }
            retainDecoration = false
        }
    }
    return CommentQuotePresentation(
        decorate = enabled || retainDecoration,
        background = background,
        accent = accent,
        gapFraction = gap,
    )
}
