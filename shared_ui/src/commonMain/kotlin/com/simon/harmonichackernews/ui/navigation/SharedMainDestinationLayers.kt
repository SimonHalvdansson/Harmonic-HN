package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex

/** Platform-neutral visibility and accessibility state for the app's destination layers. */
data class MainDestinationLayerState(
    val settingsVisible: Boolean = false,
    val settingsCoversBase: Boolean = false,
    val settingsBehindStory: Boolean = false,
    val settingsSemanticsHidden: Boolean = false,
    val submissionsVisible: Boolean = false,
    val submissionsCoversBase: Boolean = false,
    val submissionsBehindStory: Boolean = false,
    val submissionsSemanticsHidden: Boolean = false,
    val editorVisible: Boolean = false,
    val editorSemanticsHidden: Boolean = false,
    val immersiveVisible: Boolean = false,
) {
    val baseSemanticsHidden: Boolean
        get() = settingsCoversBase || submissionsCoversBase ||
            editorVisible || immersiveVisible
}

/**
 * Shared destination compositor for every platform host. It owns layer retention, transitions,
 * accessibility isolation and z-order; hosts supply the actual screens and platform back effects.
 */
@Composable
fun SharedMainDestinationLayers(
    state: MainDestinationLayerState,
    transitionOffsetPx: Int,
    completedSettingsPredictiveBack: Boolean,
    completedSubmissionsPredictiveBack: Boolean,
    completedEditorPredictiveBack: Boolean,
    base: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    submissions: @Composable () -> Unit,
    editor: @Composable () -> Unit,
    immersive: @Composable () -> Unit,
    foreground: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    basePredictiveModifier: Modifier = Modifier,
    settingsPredictiveModifier: Modifier = Modifier,
    submissionsPredictiveModifier: Modifier = Modifier,
    editorPredictiveModifier: Modifier = Modifier,
    storyPreview: (@Composable () -> Unit)? = null,
    linkPreview: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (state.baseSemanticsHidden) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                )
                .then(basePredictiveModifier),
        ) {
            base()
        }

        storyPreview?.let { content ->
            Box(Modifier.fillMaxSize().zIndex(4f)) { content() }
        }
        linkPreview?.let { content ->
            Box(Modifier.fillMaxSize().zIndex(4.5f)) { content() }
        }

        AnimatedVisibility(
            visible = state.settingsVisible,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (state.settingsBehindStory) -1f else 5f)
                .then(
                    if (state.settingsSemanticsHidden || state.settingsBehindStory) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                )
                .then(settingsPredictiveModifier),
            enter = layeredDestinationEnter(transitionOffsetPx),
            exit = if (completedSettingsPredictiveBack) {
                ExitTransition.None
            } else {
                layeredDestinationExit(transitionOffsetPx)
            },
        ) {
            settings()
        }

        AnimatedVisibility(
            visible = state.submissionsVisible,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(if (state.submissionsBehindStory) -1f else 7f)
                .then(
                    if (state.submissionsSemanticsHidden || state.submissionsBehindStory) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                )
                .then(submissionsPredictiveModifier),
            enter = layeredDestinationEnter(transitionOffsetPx),
            exit = if (completedSubmissionsPredictiveBack) {
                ExitTransition.None
            } else {
                layeredDestinationExit(transitionOffsetPx)
            },
        ) {
            submissions()
        }

        AnimatedVisibility(
            visible = state.editorVisible,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .then(
                    if (state.editorSemanticsHidden) {
                        Modifier.clearAndSetSemantics { }
                    } else {
                        Modifier
                    },
                )
                .then(editorPredictiveModifier),
            enter = layeredDestinationEnter(transitionOffsetPx),
            exit = if (completedEditorPredictiveBack) {
                ExitTransition.None
            } else {
                layeredDestinationExit(transitionOffsetPx)
            },
        ) {
            editor()
        }

        AnimatedVisibility(
            visible = state.immersiveVisible,
            modifier = Modifier.fillMaxSize().zIndex(20f),
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
            immersive()
        }

        foreground()
    }
}

private fun layeredDestinationEnter(offsetPx: Int) = slideInHorizontally(
    animationSpec = tween(
        durationMillis = NavigationTransitionDurationMillis,
        easing = navigationEasing(),
    ),
    initialOffsetX = { offsetPx },
) + fadeIn(
    animationSpec = tween(
        durationMillis = NavigationFadeDurationMillis,
        delayMillis = 50,
        easing = LinearEasing,
    ),
)

private fun layeredDestinationExit(offsetPx: Int) = slideOutHorizontally(
    animationSpec = tween(
        durationMillis = NavigationTransitionDurationMillis,
        easing = navigationEasing(),
    ),
    targetOffsetX = { offsetPx },
) + fadeOut(
    animationSpec = tween(
        durationMillis = NavigationFadeDurationMillis,
        delayMillis = 35,
        easing = LinearEasing,
    ),
)

private fun navigationEasing(): Easing = PathEasing(
    Path().apply {
        moveTo(0f, 0f)
        cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
        cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
)

private const val NavigationTransitionDurationMillis = 450
private const val NavigationFadeDurationMillis = 90
