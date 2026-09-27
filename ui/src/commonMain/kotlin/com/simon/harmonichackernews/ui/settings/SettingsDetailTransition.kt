package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale

/** Fade through peer categories inside a fixed detail pane. */
@Composable
internal fun SettingsDetailTransition(
    section: SettingsSection,
    content: @Composable (SettingsSection) -> Unit,
) {
    AnimatedContent(
        targetState = section,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(FadeInMillis, FadeOutMillis)),
                initialContentExit = fadeOut(tween(FadeOutMillis)),
                sizeTransform = null,
            )
        },
        label = "Settings detail fade through",
    ) { currentSection ->
        val contentScale by transition.animateFloat(
            transitionSpec = {
                tween(FadeInMillis, FadeOutMillis, FastOutSlowInEasing)
            },
            label = "Settings detail incoming scale",
        ) { state ->
            if (state == EnterExitState.PreEnter) 0.92f else 1f
        }
        Box(
            Modifier.fillMaxSize().drawWithContent {
                // Scaling layout coordinates changes recalculated safe-area padding. Transform
                // only the drawing so the pane stays centered and text keeps its line breaks.
                scale(contentScale) { this@drawWithContent.drawContent() }
            },
        ) {
            content(currentSection)
        }
    }
}

private const val FadeOutMillis = 105
private const val FadeInMillis = 195
