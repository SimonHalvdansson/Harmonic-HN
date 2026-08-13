package com.simon.harmonichackernews.ui.navigation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.simon.harmonichackernews.navigation.MainNavigationSnapshot

/**
 * Common application-root compositor. A native shell reports adaptive/back-animation facts and
 * supplies platform screen hooks; destination retention, covering and accessibility are shared.
 */
@Composable
fun SharedHarmonicAppRoot(
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
    storyPreview: (@Composable () -> Unit)? = null,
    linkPreview: (@Composable () -> Unit)? = null,
) {
    val storyFromSettingsReady = navigation.storyOpenedFromSettings &&
        navigation.storyRequest != null
    SharedMainDestinationLayers(
        state = MainDestinationLayerState(
            settingsVisible = navigation.settingsRequest != null ||
                navigation.storyOpenedFromSettings,
            settingsCoversBase = navigation.settingsRequest != null &&
                !navigation.storyOpenedFromSettings,
            settingsBehindStory = storyFromSettingsReady,
            settingsSemanticsHidden = navigation.submissionsRequest != null ||
                navigation.editorRequest != null || navigation.coulombGasVisible,
            submissionsVisible = navigation.submissionsRequest != null,
            submissionsCoversBase = navigation.submissionsRequest != null &&
                !navigation.storyOpenedFromSubmissions,
            submissionsBehindStory = navigation.storyOpenedFromSubmissions,
            submissionsSemanticsHidden = navigation.editorRequest != null ||
                navigation.coulombGasVisible,
            editorVisible = navigation.editorRequest != null,
            editorSemanticsHidden = navigation.coulombGasVisible,
            immersiveVisible = navigation.coulombGasVisible,
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
        storyPreview = storyPreview,
        linkPreview = linkPreview,
    )
}
