@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.simon.harmonichackernews.ui.settings


import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource as androidPainterResource
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.network.LocalSummaryManager
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelPresentationAction
import com.simon.harmonichackernews.summary.LocalModelPresentationInput
import com.simon.harmonichackernews.summary.LocalModelPresentationPolicy
import com.simon.harmonichackernews.summary.LocalModelTransferState
import com.simon.harmonichackernews.summary.LocalModelTransferStatus
import com.simon.harmonichackernews.summary.LocalRuntimeInstallState
import com.simon.harmonichackernews.summary.LocalRuntimeInstallStatus
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager
import com.simon.harmonichackernews.summary.local.LocalModelManager
import com.simon.harmonichackernews.settings.AndroidAiModelDefaults
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore

private const val KeyAiEnabled = "pref_ai_summary_enabled"
private const val KeyAiMode = "pref_ai_summary_mode"
private const val KeyAiBaseUrl = "pref_ai_summary_base_url"
private const val KeyAiModel = "pref_ai_summary_model"
private const val KeyAiSystemPrompt = "pref_ai_summary_system_prompt"
private const val KeyAiStream = "pref_ai_summary_stream_responses"
private const val AiModeLocal = "local"
private const val AiModeCloud = "cloud"

private const val DefaultSystemPrompt =
    "You are a helpful assistant that is an expert on summarizing articles into an " +
        "information-dense, concise and brief bullet-point list. Focus on key takeaways " +
        "and most important/note-worthy points in the article. Keep the summary under 500 " +
        "characters where possible. Respond in markdown format. Respond with only the " +
        "summarized content - nothing else before or after."

@Composable
fun AiSummarySettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val preferenceRefresh = rememberPreferenceRefresh()
    var localRefresh by remember { mutableIntStateOf(0) }
    var localAvailable by remember {
        mutableStateOf(LocalSummaryManager.canAttemptLocalSummarization())
    }
    var nanoAvailabilityResolved by remember { mutableStateOf(false) }
    var nanoAvailable by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<AiSummarySettingsDialog?>(null) }

    LaunchedEffect(Unit) {
        AndroidAiModelDefaults.ensureInitialDefault(context)
        if (!LocalSummaryManager.canAttemptLocalSummarization()) {
            prefs.edit()
                .putString(KeyAiMode, AiModeCloud)
                .apply()
        }
    }

    if (activity != null) {
        DisposableEffect(activity) {
            val modelListener = LocalModelManager.StatusListener {
                localRefresh++
            }
            val runtimeListener = LocalAiRuntimeManager.StatusListener {
                localRefresh++
            }
            LocalModelManager.addStatusListener(context, modelListener)
            LocalAiRuntimeManager.addStatusListener(context, runtimeListener)

            var disposed = false
            if (LocalSummaryManager.canAttemptLocalSummarization()) {
                LocalSummaryManager.checkLocalSummaryAvailability(context) {
                        available,
                        fallbackRequired,
                        _,
                    ->
                    if (!disposed) {
                        localAvailable = available
                        nanoAvailabilityResolved = true
                        nanoAvailable = available && !fallbackRequired
                        if (
                            !nanoAvailable &&
                            LocalModelManager.getSelectedModel(context).id ==
                            LocalModelManager.MODEL_GEMINI_NANO
                        ) {
                            selectFirstReadyLocalModelOrClear(context)
                        }
                        if (!available) {
                            prefs.edit()
                                .putString(KeyAiMode, AiModeCloud)
                                .apply()
                        }
                        localRefresh++
                    }
                }
            } else {
                nanoAvailabilityResolved = true
            }

            onDispose {
                disposed = true
                LocalModelManager.removeStatusListener(modelListener)
                LocalAiRuntimeManager.removeStatusListener(runtimeListener)
            }
        }
    }

    val mode = prefs.getString(
        KeyAiMode,
        AiModeCloud,
    ) ?: AiModeCloud
    val cloudMode = mode == AiModeCloud
    val configurationComplete = if (cloudMode) {
        cloudConfigurationComplete(context)
    } else {
        LocalSummaryManager.isLocalSummaryReady(context)
    }
    val enabled = prefs.getBoolean(KeyAiEnabled, configurationComplete)
    val baseUrl = prefs.getString(
        KeyAiBaseUrl,
        AiSummaryProviders.defaultBaseUrl,
    ) ?: AiSummaryProviders.defaultBaseUrl
    val apiKey = AiSummaryApiKeyStore.getApiKey(context)
    val model = prefs.getString(KeyAiModel, "") ?: ""
    val systemPrompt = prefs.getString(
        KeyAiSystemPrompt,
        DefaultSystemPrompt,
    ) ?: DefaultSystemPrompt

    LaunchedEffect(configurationComplete, enabled) {
        if (
            !configurationComplete &&
            prefs.contains(KeyAiEnabled) &&
            prefs.getBoolean(KeyAiEnabled, false)
        ) {
            prefs.edit().putBoolean(KeyAiEnabled, false).apply()
        }
    }

    SharedAiSummarySettingsScreen(
        state = AiSummarySettingsUiState(
            enabled = enabled,
            configurationComplete = configurationComplete,
            localSummarizationSupported = LocalSummaryManager.canAttemptLocalSummarization(),
            mode = mode,
            localModeValue = AiModeLocal,
            cloudModeValue = AiModeCloud,
            baseUrl = baseUrl,
            apiKeyPreview = if (apiKey.isBlank()) "Not set" else apiKey.take(8) + "…",
            model = model,
            systemPrompt = systemPrompt,
            streamResponses = prefs.getBoolean(KeyAiStream, true),
        ),
        showNavigation = showNavigation,
        contentVersion = preferenceRefresh + localRefresh,
        onBack = onBack,
        onEnabledChanged = { prefs.edit().putBoolean(KeyAiEnabled, it).apply() },
        onModeSelected = { selected ->
            if (selected == AiModeLocal && !localAvailable) {
                Toast.makeText(
                    context,
                    "Local summarization is unavailable on this device",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                prefs.edit().putString(KeyAiMode, selected).apply()
            }
        },
        onStreamChanged = { prefs.edit().putBoolean(KeyAiStream, it).apply() },
        onDialogRequested = { dialog = it },
        localModelsContent = {
            LocalModelsPanel(
                nanoAvailabilityResolved = nanoAvailabilityResolved,
                nanoAvailable = nanoAvailable,
                refresh = localRefresh,
                onRefresh = { localRefresh++ },
            )
        },
    )

    when (dialog) {
        AiSummarySettingsDialog.BaseUrl -> AiSummaryBaseUrlDialog(
            onDismiss = { dialog = null },
        )
        AiSummarySettingsDialog.ApiKey -> AiSummaryTextDialog(
            preferenceKey = AiSummaryApiKeyStore.PREF_API_KEY,
            title = "API Key",
            hint = "API Key",
            defaultValue = "",
            minLines = 1,
            maxLines = 1,
            textSizeSp = 16,
            trimValue = true,
            allowEmpty = true,
            showReset = false,
            onDismiss = { dialog = null },
            onSaved = { localRefresh++ },
        )
        AiSummarySettingsDialog.Model -> AiModelSelectorDialog(
            onDismiss = { dialog = null },
        )
        AiSummarySettingsDialog.SystemPrompt -> AiSummaryTextDialog(
            preferenceKey = KeyAiSystemPrompt,
            title = "System prompt",
            hint = "System prompt",
            defaultValue = DefaultSystemPrompt,
            minLines = 5,
            maxLines = 10,
            textSizeSp = 15,
            trimValue = false,
            allowEmpty = true,
            showReset = true,
            onDismiss = { dialog = null },
            onSaved = { localRefresh++ },
        )
        null -> Unit
    }
}

@Composable
private fun LocalModelsPanel(
    nanoAvailabilityResolved: Boolean,
    nanoAvailable: Boolean,
    refresh: Int,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val selectedModelId = remember(context, refresh) {
        LocalModelManager.getSelectedModel(context).id
    }
    val rows = LocalModelCatalog.models.map { definition ->
        val model = LocalModelManager.getModel(definition.id)
        val supported = LocalModelManager.isModelSupported(model)
        val runtimeInstalled = LocalAiRuntimeManager.isRuntimeInstalled(context, model.runtime)
        LocalModelRowUiState(
            model = definition,
            presentation = LocalModelPresentationPolicy.present(
                LocalModelPresentationInput(
                    model = definition,
                    supported = supported,
                    unsupportedReason = LocalModelManager.getModelUnsupportedReason(model),
                    selected = selectedModelId == model.id,
                    nanoAvailabilityResolved = nanoAvailabilityResolved,
                    nanoAvailable = nanoAvailable,
                    transferStatus = LocalModelManager.getStatus(context, model).toShared(),
                    runtimeStatus = LocalAiRuntimeManager.getStatus(
                        context,
                        model.runtime,
                    ).toShared(),
                    runtimeInstalled = runtimeInstalled,
                ),
            ),
        )
    }

    SharedLocalModelsPanel(
        models = rows,
        modelIconPainter = { definition ->
            androidPainterResource(LocalModelManager.getModel(definition.id).iconResId)
        },
        onModelSelected = { modelId ->
            LocalModelManager.selectModel(context, modelId)
            onRefresh()
        },
        onAction = { modelId, action ->
            val model = LocalModelManager.getModel(modelId)
            when (action) {
                LocalModelPresentationAction.CANCEL_DOWNLOAD -> {
                    val runtimeStatus = LocalAiRuntimeManager.getStatus(context, model.runtime)
                    if (runtimeStatus.isActive && runtimeStatus.pendingModelId == model.id) {
                        LocalAiRuntimeManager.cancelRuntimeInstall(context, model.runtime)
                    } else {
                        LocalModelManager.cancelDownload(context, model.id)
                    }
                }
                LocalModelPresentationAction.DELETE_MODEL ->
                    LocalModelManager.removeModel(context, model.id)
                LocalModelPresentationAction.DOWNLOAD_MODEL -> {
                    val error = LocalAiRuntimeManager.requestRuntimeAndModelDownload(
                        context,
                        model.id,
                    )
                    if (!error.isNullOrBlank()) {
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                }
            }
            onRefresh()
        },
    )
}

private fun LocalModelManager.Status.toShared(): LocalModelTransferStatus =
    LocalModelTransferStatus(
        state = when (state) {
            LocalModelManager.State.NOT_DOWNLOADED -> LocalModelTransferState.NOT_DOWNLOADED
            LocalModelManager.State.PARTIALLY_DOWNLOADED ->
                LocalModelTransferState.PARTIALLY_DOWNLOADED
            LocalModelManager.State.WAITING -> LocalModelTransferState.WAITING
            LocalModelManager.State.DOWNLOADING -> LocalModelTransferState.DOWNLOADING
            LocalModelManager.State.DOWNLOADED -> LocalModelTransferState.DOWNLOADED
            LocalModelManager.State.FAILED -> LocalModelTransferState.FAILED
        },
        receivedBytes = receivedBytes,
        error = error,
    )

private fun LocalAiRuntimeManager.Status.toShared(): LocalRuntimeInstallStatus =
    LocalRuntimeInstallStatus(
        state = when (state) {
            LocalAiRuntimeManager.State.NOT_INSTALLED -> LocalRuntimeInstallState.NOT_INSTALLED
            LocalAiRuntimeManager.State.PENDING -> LocalRuntimeInstallState.PENDING
            LocalAiRuntimeManager.State.DOWNLOADING -> LocalRuntimeInstallState.DOWNLOADING
            LocalAiRuntimeManager.State.INSTALLING -> LocalRuntimeInstallState.INSTALLING
            LocalAiRuntimeManager.State.INSTALLED -> LocalRuntimeInstallState.INSTALLED
            LocalAiRuntimeManager.State.FAILED -> LocalRuntimeInstallState.FAILED
            LocalAiRuntimeManager.State.CANCELED -> LocalRuntimeInstallState.CANCELED
        },
        pendingModelId = pendingModelId,
        downloadedBytes = bytesDownloaded,
        totalBytes = totalBytes,
    )

private fun cloudConfigurationComplete(context: Context): Boolean {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    return !prefs.getString(
        KeyAiBaseUrl,
        AiSummaryProviders.defaultBaseUrl,
    ).isNullOrBlank() &&
        AiSummaryApiKeyStore.getApiKey(context).isNotBlank() &&
        !prefs.getString(KeyAiModel, "").isNullOrBlank() &&
        !prefs.getString(KeyAiSystemPrompt, DefaultSystemPrompt).isNullOrBlank()
}

private fun selectFirstReadyLocalModelOrClear(context: Context) {
    val readyModel = LocalModelManager.models.firstOrNull { model ->
        model.downloadable &&
            LocalModelManager.isModelSupported(model) &&
            LocalModelManager.isModelDownloaded(context, model) &&
            LocalAiRuntimeManager.isRuntimeInstalled(context, model.runtime)
    }
    if (readyModel == null) {
        LocalModelManager.clearSelectedModel(context)
    } else {
        LocalModelManager.selectModel(context, readyModel.id)
    }
}
