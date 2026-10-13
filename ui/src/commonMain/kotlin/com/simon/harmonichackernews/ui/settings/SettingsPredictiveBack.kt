package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.ui.navigation.DefaultActivityPredictiveBackAnimation
import com.simon.harmonichackernews.ui.navigation.LocalPredictiveBackCompletion
import com.simon.harmonichackernews.ui.navigation.PredictiveNavigationBackHandler
import com.simon.harmonichackernews.ui.navigation.rememberNavigationBackInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsPredictiveBackState(
    val overlay: SettingsPredictiveBackOverlay?,
    val completed: Boolean,
)

@Composable
fun rememberSettingsPredictiveBack(
    navigation: SettingsNavigationStore,
    isTwoPane: Boolean,
    backHandlerEnabled: Boolean,
    onBackFromSettings: () -> Unit,
): SettingsPredictiveBackState {
    val navigationState by navigation.state.collectAsStateWithLifecycle()
    val backAnimationScope = rememberCoroutineScope()
    val backCompletion = LocalPredictiveBackCompletion.current
    val dispatcher = rememberNavigationBackInput()
    var activeBackAnimation by remember {
        mutableStateOf<DefaultActivityPredictiveBackAnimation?>(null)
    }
    var predictiveBackStack by remember { mutableStateOf<List<SettingsSection>?>(null) }
    var completedPredictiveBack by remember { mutableStateOf(false) }

    fun popSettingsBackStack() {
        if (!navigation.navigateBack()) onBackFromSettings()
    }

    PredictiveNavigationBackHandler(
        enabled = backHandlerEnabled && navigationState.canNavigateBackWithinSettings,
    ) { events ->
        if (backCompletion.handleFollowingBack(events, dispatcher)) return@PredictiveNavigationBackHandler
        if (isTwoPane) {
            try {
                events.collect {}
                popSettingsBackStack()
            } catch (_: CancellationException) {
                // A cancelled two-pane gesture leaves the selected detail unchanged.
            }
            return@PredictiveNavigationBackHandler
        }

        completedPredictiveBack = false
        var animation: DefaultActivityPredictiveBackAnimation? = null
        try {
            events.collect { event ->
                val current = animation ?: DefaultActivityPredictiveBackAnimation(event).also {
                    animation = it
                    predictiveBackStack = navigationState.detailStack
                    activeBackAnimation = it
                }
                backAnimationScope.launch { current.animate(event) }
            }
            val current = animation
            if (current == null) {
                popSettingsBackStack()
                return@PredictiveNavigationBackHandler
            }
            backCompletion.finish(
                scope = backAnimationScope,
                animation = current,
                frameHoldCount = 3,
                onCommit = {
                    completedPredictiveBack = true
                    popSettingsBackStack()
                },
                onFinished = {
                    if (activeBackAnimation === current) activeBackAnimation = null
                    predictiveBackStack = null
                    completedPredictiveBack = false
                },
            )
        } catch (_: CancellationException) {
            withContext(NonCancellable) {
                animation?.cancel()
                if (activeBackAnimation === animation) activeBackAnimation = null
                predictiveBackStack = null
                completedPredictiveBack = false
            }
        }
    }

    return SettingsPredictiveBackState(
        overlay = activeBackAnimation?.let {
            val gestureStack = predictiveBackStack ?: navigationState.detailStack
            gestureStack.lastOrNull()?.let { sourceSection ->
                SettingsPredictiveBackOverlay(
                    enterModifier = it.enterModifier,
                    exitModifier = it.exitModifier,
                    sourceSection = sourceSection,
                    parentSection = gestureStack.dropLast(1).lastOrNull(),
                )
            }
        },
        completed = completedPredictiveBack,
    )
}
