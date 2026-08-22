@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource as androidPainterResource
import com.simon.harmonichackernews.network.CloudSummaryDefaults
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelBrand
import com.simon.harmonichackernews.summary.LocalModelPresentationAction
import com.simon.harmonichackernews.summary.LocalModelService
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.settings.AiSummaryTextSetting
import com.simon.harmonichackernews.presentation.UserMessageDuration

@Composable
fun AiSummarySettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val appComposition = LocalHarmonicUiDependencies.current
    val repository = appComposition.aiSummarySettings
    val localModels = checkNotNull(appComposition.localModels) {
        "Android local-model service was not installed"
    }
    val localModelState by localModels.state.collectAsState()
    var localRefresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val settingsRuntime = remember(appComposition, scope) {
        appComposition.createLocalSummarySettingsRuntime(scope)
    }
    val availabilityState by settingsRuntime.state.collectAsState()

    LaunchedEffect(settingsRuntime) {
        settingsRuntime.resolve()
    }
    DisposableEffect(settingsRuntime) {
        onDispose(settingsRuntime::dispose)
    }

    SharedAiSummarySettingsRoute(
        repository = repository,
        modelDefaults = appComposition.aiModelDefaults,
        localSummarizationSupported = availabilityState.supported,
        localConfigurationReady = availabilityState.configurationReady,
        localModeAvailable = availabilityState.available,
        showNavigation = showNavigation,
        contentVersion = localRefresh + localModelState.hashCode() + availabilityState.revision,
        onBack = onBack,
        onLocalModeUnavailable = {
            appComposition.userMessages.show(
                "Local summarization is unavailable on this device",
                UserMessageDuration.LONG,
            )
        },
        localModelsContent = {
            LocalModelsPanel(
                nanoAvailabilityResolved = availabilityState.availabilityResolved,
                nanoAvailable = availabilityState.nanoAvailable,
                localModels = localModels,
                onRefresh = { localRefresh++ },
            )
        },
        dialogContent = { dialog, onDismiss ->
            when (dialog) {
                AiSummarySettingsDialog.BaseUrl -> AiSummaryBaseUrlDialog(onDismiss = onDismiss)
                AiSummarySettingsDialog.ApiKey -> AiSummaryTextDialog(
                    setting = AiSummaryTextSetting.API_KEY,
                    title = "API Key",
                    hint = "API Key",
                    defaultValue = "",
                    minLines = 1,
                    maxLines = 1,
                    textSizeSp = 16,
                    trimValue = true,
                    allowEmpty = true,
                    showReset = false,
                    onDismiss = onDismiss,
                    onSaved = { localRefresh++ },
                )
                AiSummarySettingsDialog.Model -> SharedAiModelSelectorRoute(onDismiss = onDismiss)
                AiSummarySettingsDialog.SystemPrompt -> AiSummaryTextDialog(
                    setting = AiSummaryTextSetting.SYSTEM_PROMPT,
                    title = "System prompt",
                    hint = "System prompt",
                    defaultValue = CloudSummaryDefaults.SYSTEM_PROMPT,
                    minLines = 5,
                    maxLines = 10,
                    textSizeSp = 15,
                    trimValue = false,
                    allowEmpty = true,
                    showReset = true,
                    onDismiss = onDismiss,
                    onSaved = { localRefresh++ },
                )
            }
        },
    )
}

@Composable
private fun LocalModelsPanel(
    nanoAvailabilityResolved: Boolean,
    nanoAvailable: Boolean,
    localModels: LocalModelService,
    onRefresh: () -> Unit,
) {
    val userMessages = LocalHarmonicUiDependencies.current.userMessages
    val rows = LocalModelCatalog.models.map { definition ->
        LocalModelRowUiState(
            model = definition,
            presentation = localModels.presentation(
                model = definition,
                nanoAvailabilityResolved = nanoAvailabilityResolved,
                nanoAvailable = nanoAvailable,
            ),
        )
    }

    SharedLocalModelsPanel(
        models = rows,
        modelIconPainter = { definition ->
            androidPainterResource(
                when (definition.brand) {
                    LocalModelBrand.GOOGLE -> R.drawable.model_logo_google
                    LocalModelBrand.PRISM -> R.drawable.model_logo_prism
                    LocalModelBrand.QWEN -> R.drawable.model_logo_qwen
                    LocalModelBrand.NVIDIA -> R.drawable.model_logo_nvidia
                    LocalModelBrand.MISTRAL -> R.drawable.model_logo_mistral
                    LocalModelBrand.LIQUID -> R.drawable.model_logo_liquid
                },
            )
        },
        onModelSelected = { modelId ->
            localModels.select(modelId)
            onRefresh()
        },
        onAction = { modelId, action ->
            when (action) {
                LocalModelPresentationAction.CANCEL_DOWNLOAD -> localModels.cancel(modelId)
                LocalModelPresentationAction.DELETE_MODEL ->
                    localModels.remove(modelId)
                LocalModelPresentationAction.DOWNLOAD_MODEL -> {
                    val error = localModels.requestRuntimeAndModelDownload(modelId)
                    if (!error.isNullOrBlank()) {
                        userMessages.show(error, UserMessageDuration.LONG)
                    }
                }
            }
            onRefresh()
        },
    )
}
