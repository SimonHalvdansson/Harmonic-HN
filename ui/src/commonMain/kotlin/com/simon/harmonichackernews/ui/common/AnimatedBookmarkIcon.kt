package com.simon.harmonichackernews.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_bookmark
import com.simon.harmonichackernews.resources.ic_bookmark_filled
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import org.jetbrains.compose.resources.painterResource
import kotlin.math.PI
import kotlin.math.sin

/** Animates confirmed membership changes, never initial composition or a switch to another item. */
@Composable
fun AnimatedBookmarkIcon(
    bookmarked: Boolean,
    itemId: Int,
    description: String? = if (bookmarked) "Remove bookmark" else "Bookmark",
    modifier: Modifier = Modifier,
    tint: Color = HarmonicTheme.colors.drawable,
) = key(itemId) {
    val transition = updateTransition(bookmarked, label = "bookmark")
    val fill by transition.animateFloat(
        transitionSpec = { tween(150) },
        label = "bookmark fill",
    ) { if (it) 1f else 0f }
    val progress by transition.animateFloat(
        transitionSpec = {
            tween(if (targetState) 360 else 220, easing = FastOutSlowInEasing)
        },
        label = "bookmark dip",
    ) { if (it) 1f else 0f }

    Box(
        modifier.size(24.dp)
            .semantics { description?.let { contentDescription = it } }
            .graphicsLayer {
                val strength = if (transition.targetState) 1f else 0.45f
                translationY = 3.dp.toPx() * sin(PI * progress).toFloat() * strength
                val bounce = sin(2 * PI * progress).toFloat() * strength
                scaleX = 1f + 0.06f * bounce
                scaleY = 1f - 0.08f * bounce
            },
    ) {
        Icon(
            painterResource(Res.drawable.ic_bookmark),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = 1f - fill },
        )
        Icon(
            painterResource(Res.drawable.ic_bookmark_filled),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = fill },
        )
    }
}
