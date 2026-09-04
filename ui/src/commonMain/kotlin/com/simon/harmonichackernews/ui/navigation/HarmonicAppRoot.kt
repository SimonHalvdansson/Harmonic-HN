package com.simon.harmonichackernews.ui.navigation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.navigation.MainDestination
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot

/**
 * Common application-root compositor. A native shell reports adaptive/back-animation facts and
 * supplies platform screen hooks; destination retention, covering and accessibility are shared.
 */
@Composable
fun HarmonicAppRoot(
    navigation: MainNavigationSnapshot,
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
    completedStoryPredictiveBack: Boolean = false,
) {
    val current = navigation.currentDestination
    val settingsVisible = navigation.destinationStack.any {
        it.destination == MainDestination.SETTINGS
    }
    val submissionsVisible = navigation.destinationStack.any {
        it.destination == MainDestination.SUBMISSIONS
    }
    MainDestinationLayers(
        state = MainDestinationLayerState(
            settingsVisible = settingsVisible,
            settingsCoversBase = current == MainDestination.SETTINGS,
            settingsBehindStory = current == MainDestination.STORY && settingsVisible,
            settingsSemanticsHidden = settingsVisible && current != MainDestination.SETTINGS,
            submissionsVisible = submissionsVisible,
            submissionsCoversBase = current == MainDestination.SUBMISSIONS,
            submissionsBehindStory = current == MainDestination.STORY && submissionsVisible,
            submissionsSemanticsHidden = submissionsVisible &&
                current != MainDestination.SUBMISSIONS,
            editorVisible = current == MainDestination.EDITOR,
            editorSemanticsHidden = current == MainDestination.IMMERSIVE,
            immersiveVisible = current == MainDestination.IMMERSIVE,
        ),
        transitionOffsetPx = transitionOffsetPx,
        completedSettingsPredictiveBack = completedSettingsPredictiveBack,
        completedSubmissionsPredictiveBack = completedSubmissionsPredictiveBack,
        completedEditorPredictiveBack = completedEditorPredictiveBack,
        base = base,
        settings = settings,
        submissions = submissions,
        editor = editor,
        immersive = immersive,
        foreground = foreground,
        modifier = modifier,
        basePredictiveModifier = basePredictiveModifier,
        settingsPredictiveModifier = settingsPredictiveModifier,
        submissionsPredictiveModifier = submissionsPredictiveModifier,
        editorPredictiveModifier = editorPredictiveModifier,
        linkPreview = linkPreview,
        completedStoryPredictiveBack = completedStoryPredictiveBack,
    )
}
