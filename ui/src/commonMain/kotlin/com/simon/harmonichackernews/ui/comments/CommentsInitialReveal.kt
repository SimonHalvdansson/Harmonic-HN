package com.simon.harmonichackernews.ui.comments

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.simon.harmonichackernews.ui.navigation.LocalActivityNavigationTransition
import kotlinx.coroutines.flow.first

/** Reveal once, after both the first comments and the page's opening animation are ready. */
@Composable
internal fun rememberInitialCommentsReveal(
    storyId: Int,
    initiallyVisible: Boolean,
    hasComments: Boolean,
    animateComments: Boolean,
): Animatable<Float, AnimationVector1D> {
    val reveal = remember(storyId) { Animatable(if (initiallyVisible) 1f else 0f) }
    val openingTransition = LocalActivityNavigationTransition.current
    LaunchedEffect(reveal, hasComments, animateComments, openingTransition) {
        if (!animateComments) {
            reveal.snapTo(1f)
        } else if (hasComments && reveal.value < 1f) {
            // Observe the real transition, including its duration scale. A slow request or a
            // restored page has no remaining opening animation and starts fading immediately.
            snapshotFlow { openingTransition?.currentState != EnterExitState.PreEnter }.first { it }
            reveal.animateTo(1f, tween(220))
        }
    }
    return reveal
}
