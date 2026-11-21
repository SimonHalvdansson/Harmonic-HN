@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.presentation.UserMessageDuration

@Composable
fun AndroidAiSummarySettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onArticleCategoriesRequested: (() -> Unit)? = null,
) {
    val appComposition = LocalHarmonicUiDependencies.current
    val repository = appComposition.aiSummarySettings
    val localModels = checkNotNull(appComposition.localModels) {
        "Android local-model service was not installed"
    }
    // The application preloads this cache off the main thread. Reading it here must not scan
    // model files or query dynamic-feature state while the settings navigation is animating.
    val localModelState by localModels.cachedState.collectAsStateWithLifecycle()
    var localRefresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val settingsRuntime = remember(appComposition, scope) {
        appComposition.createLocalSummarySettingsRuntime(scope)
    }
    val availabilityState by settingsRuntime.state.collectAsStateWithLifecycle()
    val aiSettings by repository.updates.collectAsStateWithLifecycle(initialValue = repository.snapshot())

    LaunchedEffect(settingsRuntime, localModelState, localRefresh, aiSettings.geminiNanoSummaryMode) {
        settingsRuntime.resolve()
    }
    DisposableEffect(settingsRuntime) {
        onDispose(settingsRuntime::dispose)
    }

    AiSummarySettingsRoute(
        repository = repository,
        modelDefaults = appComposition.aiModelDefaults,
        localSummarizationSupported = availabilityState.supported,
        localUnavailableReason = if (!android.os.Process.is64Bit())
            "On-device AI is unavailable on 32-bit devices. You can still use cloud AI with an API key."
        else null,
        localAvailabilityResolved = availabilityState.availabilityResolved,
        localConfigurationReady = availabilityState.configurationReady,
        localModeAvailable = availabilityState.available,
        selectedLocalModelId = localModelState.selectedModelId,
        geminiNanoAvailable = availabilityState.nanoAvailable,
        showNavigation = showNavigation,
        contentVersion = localRefresh + localModelState.hashCode() + availabilityState.revision,
        onBack = onBack,
        onArticleCategoriesRequested = onArticleCategoriesRequested,
        onLocalModeUnavailable = {
            appComposition.userMessages.show(
                "Local summarization is unavailable on this device",
                UserMessageDuration.LONG,
            )
        },
        localModelsContent = {
            LocalModelsRoute(
                nanoAvailabilityResolved = availabilityState.availabilityResolved,
                nanoAvailable = availabilityState.nanoAvailable,
                nanoBaseModelName = availabilityState.nanoBaseModelName,
                localModels = localModels,
                managerState = localModelState,
                onChanged = { localRefresh++ },
                onMessage = { message ->
                    appComposition.userMessages.show(message, UserMessageDuration.LONG)
                },
            )
        },
        onTextSettingSaved = { localRefresh++ },
    )
}
