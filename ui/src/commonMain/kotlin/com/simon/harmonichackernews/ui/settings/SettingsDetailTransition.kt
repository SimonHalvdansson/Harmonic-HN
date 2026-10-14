package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.drawscope.scale

/** Fade through categories; shared Z-axis motion conveys depth within the fixed detail pane. */
@Composable
internal fun SettingsDetailTransition(
    navigationState: SettingsNavigationState,
    content: @Composable (SettingsSection) -> Unit,
) {
    val detailTransition = updateTransition(navigationState, label = "Settings detail navigation")
    val navigation = detailTransition.targetState.detailNavigation
    detailTransition.AnimatedContent(
        modifier = Modifier.fillMaxSize().clipToBounds(),
        contentKey = { it.selectedSection },
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(FadeInMillis, FadeOutMillis)),
                initialContentExit = fadeOut(tween(FadeOutMillis)),
                sizeTransform = null,
            )
        },
    ) { currentState ->
        val contentScale by transition.animateFloat(
            transitionSpec = {
                if (navigation == SettingsDetailNavigation.Replace) {
                    tween(FadeInMillis, FadeOutMillis, FastOutSlowInEasing)
                } else {
                    tween(FadeOutMillis + FadeInMillis, easing = FastOutSlowInEasing)
                }
            },
            label = "Settings detail scale",
        ) { state ->
            when (state) {
                EnterExitState.PreEnter -> when (navigation) {
                    SettingsDetailNavigation.Replace -> 0.92f
                    SettingsDetailNavigation.Push -> 0.8f
                    SettingsDetailNavigation.Pop -> 1.1f
                }
                EnterExitState.Visible -> 1f
                EnterExitState.PostExit -> when (navigation) {
                    SettingsDetailNavigation.Replace -> 1f
                    SettingsDetailNavigation.Push -> 1.1f
                    SettingsDetailNavigation.Pop -> 0.8f
                }
            }
        }
        Box(
            Modifier.fillMaxSize().drawWithContent {
                // Scaling layout coordinates changes recalculated safe-area padding. Transform
                // only the drawing so the pane stays centered and text keeps its line breaks.
                scale(contentScale) { this@drawWithContent.drawContent() }
            },
        ) {
            content(currentState.selectedSection)
        }
    }
}

private const val FadeOutMillis = 105
private const val FadeInMillis = 195
