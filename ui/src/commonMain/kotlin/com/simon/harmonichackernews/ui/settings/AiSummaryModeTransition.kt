package com.simon.harmonichackernews.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import com.simon.harmonichackernews.settings.AiSummaryMode

@Composable
internal fun AiSummaryModeTransition(
    mode: AiSummaryMode,
    modifier: Modifier = Modifier,
    content: @Composable (AiSummaryMode) -> Unit,
) {
    AnimatedContent(
        targetState = mode,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            val enter = fadeIn(
                tween(
                    AiModeEnterFadeDurationMillis,
                    delayMillis = AiModeEnterFadeDelayMillis,
                    easing = LinearEasing,
                ),
            )
            val exit = fadeOut(tween(AiModeExitFadeDurationMillis, easing = LinearEasing))
            (enter togetherWith exit).using(
                SizeTransform(clip = true) { _, _ ->
                    tween(
                        AiModeTransitionDurationMillis,
                        easing = FastOutSlowInEasing,
                    )
                },
            )
        },
        label = "AI summary mode content",
    ) { contentMode ->
        val horizontalOffset = transition.animateFloat(
            transitionSpec = {
                tween(
                    if (targetState == EnterExitState.PostExit) {
                        AiModeExitDurationMillis
                    } else {
                        AiModeTransitionDurationMillis
                    },
                    easing = FastOutSlowInEasing,
                )
            },
            label = "AI summary mode horizontal offset",
        ) { visibility ->
            when (visibility) {
                EnterExitState.PreEnter ->
                    (if (contentMode == AiSummaryMode.CLOUD) 1f else -1f) / AiModeEnterOffsetDivisor
                EnterExitState.Visible -> 0f
                EnterExitState.PostExit ->
                    (if (contentMode == AiSummaryMode.CLOUD) 1f else -1f) / AiModeExitOffsetDivisor
            }
        }
        Box(
            Modifier.fillMaxWidth().drawWithContent {
                // Apply the slide without changing the child's layout coordinates.
                translate(left = size.width * horizontalOffset.value) {
                    this@drawWithContent.drawContent()
                }
            },
        ) {
            content(contentMode)
        }
    }
}

private const val AiModeTransitionDurationMillis = 240
private const val AiModeExitDurationMillis = 170
private const val AiModeEnterFadeDurationMillis = 175
private const val AiModeEnterFadeDelayMillis = 25
private const val AiModeExitFadeDurationMillis = 125
private const val AiModeEnterOffsetDivisor = 8
private const val AiModeExitOffsetDivisor = 10
