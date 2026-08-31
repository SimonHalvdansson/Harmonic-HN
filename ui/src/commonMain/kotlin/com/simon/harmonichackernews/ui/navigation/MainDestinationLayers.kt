package com.simon.harmonichackernews.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.ui.common.consumeAllPointerGestures
import com.simon.harmonichackernews.ui.theme.HarmonicTheme

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
fun MainDestinationLayers(
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

        linkPreview?.let { content ->
            Box(Modifier.fillMaxSize().zIndex(4.5f)) { content() }
        }

        if (state.settingsCoversBase) {
            // An opaque destination is not automatically a pointer target. Keep a stationary
            // barrier behind Settings so taps on non-clickable areas (and transition gutters)
            // cannot reach controls in the retained base layer.
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(4.9f)
                    .consumeAllPointerGestures(),
            )
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
                ),
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            ActivityNavigationTransitionViewport(
                transition = transition,
                transitionOffsetPx = transitionOffsetPx,
                skipExitAnimation = completedSettingsPredictiveBack,
                modifier = Modifier.fillMaxSize(),
                contentModifier = settingsPredictiveModifier,
            ) {
                settings()
            }
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
                ),
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            ActivityNavigationTransitionViewport(
                transition = transition,
                transitionOffsetPx = transitionOffsetPx,
                skipExitAnimation = completedSubmissionsPredictiveBack,
                modifier = Modifier.fillMaxSize(),
                contentModifier = submissionsPredictiveModifier,
            ) {
                submissions()
            }
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
                ),
            enter = EnterTransition.None,
            exit = ExitTransition.None,
        ) {
            ActivityNavigationTransitionViewport(
                transition = transition,
                transitionOffsetPx = transitionOffsetPx,
                skipExitAnimation = completedEditorPredictiveBack,
                modifier = Modifier.fillMaxSize(),
                contentModifier = editorPredictiveModifier,
            ) {
                editor()
            }
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
