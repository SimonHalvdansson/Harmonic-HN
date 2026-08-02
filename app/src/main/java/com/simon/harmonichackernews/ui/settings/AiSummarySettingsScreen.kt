package com.simon.harmonichackernews.ui.settings

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.R
import com.simon.harmonichackernews.network.AiModelCatalog
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.network.SummaryManager
import com.simon.harmonichackernews.summary.local.LocalAiRuntimeManager
import com.simon.harmonichackernews.summary.local.LocalModelManager
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
        mutableStateOf(SummaryManager.canAttemptLocalSummarization())
    }
    var nanoAvailabilityResolved by remember { mutableStateOf(false) }
    var nanoAvailable by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = preferenceRefresh + localRefresh

    LaunchedEffect(Unit) {
        AiModelCatalog.ensureInitialDefault(context)
        if (!SummaryManager.canAttemptLocalSummarization()) {
            prefs.edit()
                .putString(KeyAiMode, AiModeCloud)
                .apply()
        }
    }

    DisposableEffect(activity) {
        if (activity == null) {
            onDispose {}
        } else {
            val modelListener = LocalModelManager.StatusListener {
                localRefresh++
            }
            val runtimeListener = LocalAiRuntimeManager.StatusListener {
                localRefresh++
            }
            LocalModelManager.addStatusListener(context, modelListener)
            LocalAiRuntimeManager.addStatusListener(context, runtimeListener)

            var disposed = false
            if (SummaryManager.canAttemptLocalSummarization()) {
                SummaryManager.checkLocalSummaryAvailability(context) {
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
        SummaryManager.isLocalSummaryReady(context)
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

    SettingsPage(
        title = "AI summarization",
        showNavigation = showNavigation,
        onBack = onBack,
        contentVersion = observedRefresh,
    ) {
        item {
            SettingsMainToggle(
                title = "Use AI summarization",
                checked = enabled && configurationComplete,
                enabled = configurationComplete,
                onCheckedChange = {
                    prefs.edit().putBoolean(KeyAiEnabled, it).apply()
                },
            )
        }

        item {
            SettingsCard {
                if (SummaryManager.canAttemptLocalSummarization()) {
                    SegmentedSetting(
                        title = "Summarization mode",
                        options = listOf(
                            AiModeLocal to "Local",
                            AiModeCloud to "Cloud",
                        ),
                        selected = mode,
                        onSelected = { selected ->
                            if (
                                selected == AiModeLocal &&
                                !localAvailable
                            ) {
                                Toast.makeText(
                                    context,
                                    "Local summarization is unavailable on this device",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                prefs.edit().putString(KeyAiMode, selected).apply()
                            }
                        },
                    )

                    AnimatedVisibility(
                        visible = mode == AiModeLocal,
                    ) {
                        LocalModelsPanel(
                            nanoAvailabilityResolved = nanoAvailabilityResolved,
                            nanoAvailable = nanoAvailable,
                            refresh = localRefresh,
                            onRefresh = { localRefresh++ },
                        )
                    }
                    SettingsDivider()
                }
                SettingRow(
                    title = "Base URL",
                    summary = baseUrl,
                    icon = R.drawable.ic_link,
                    enabled = cloudMode,
                    onClick = { dialog = "base_url" },
                )
                SettingsDivider()
                SettingRow(
                    title = "API Key",
                    summary = if (apiKey.isBlank()) {
                        "Not set"
                    } else {
                        apiKey.take(8) + "…"
                    },
                    icon = R.drawable.ic_key,
                    enabled = cloudMode,
                    onClick = { dialog = "api_key" },
                )
                SettingsDivider()
                SettingRow(
                    title = "Model",
                    summary = model.ifBlank { "Finding a recommended model…" },
                    icon = R.drawable.ic_hard_drive,
                    enabled = cloudMode,
                    onClick = { dialog = "model" },
                )
                SettingsDivider()
                SettingRow(
                    title = "System prompt",
                    summary = systemPrompt,
                    icon = R.drawable.ic_subject,
                    summaryFontSizeSp = 13f,
                    summaryLineHeightSp = 17f,
                    summaryMaxLines = 10,
                    enabled = cloudMode,
                    onClick = { dialog = "system_prompt" },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = "Stream responses",
                    icon = R.drawable.ic_stream,
                    checked = prefs.getBoolean(KeyAiStream, true),
                    enabled = cloudMode,
                    onCheckedChange = {
                        prefs.edit().putBoolean(KeyAiStream, it).apply()
                    },
                )
            }
        }
    }

    when (dialog) {
        "base_url" -> AiSummaryBaseUrlDialog(
            onDismiss = { dialog = null },
        )
        "api_key" -> AiSummaryTextDialog(
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
        "model" -> AiModelSelectorDialog(
            onDismiss = { dialog = null },
        )
        "system_prompt" -> AiSummaryTextDialog(
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
    val selectedModel = LocalModelManager.getSelectedModel(context)

    @Suppress("UNUSED_VARIABLE")
    val observedRefresh = refresh

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) {
        LocalModelManager.models.forEachIndexed { index, model ->
            if (index > 0) {
                SettingsDivider()
            }
            val isNano = model.id == LocalModelManager.MODEL_GEMINI_NANO
            val supported = LocalModelManager.isModelSupported(model)
            val modelStatus = LocalModelManager.getStatus(context, model)
            val runtimeStatus = LocalAiRuntimeManager.getStatus(context, model.runtime)
            val downloaded = modelStatus.state == LocalModelManager.State.DOWNLOADED
            val runtimeInstalled =
                LocalAiRuntimeManager.isRuntimeInstalled(context, model.runtime)
            val selectable = if (isNano) {
                nanoAvailabilityResolved && nanoAvailable
            } else {
                supported && downloaded && runtimeInstalled
            }
            val summary = localModelSummary(
                model = model,
                nanoAvailabilityResolved = nanoAvailabilityResolved,
                nanoAvailable = nanoAvailable,
                modelStatus = modelStatus,
                runtimeStatus = runtimeStatus,
                runtimeInstalled = runtimeInstalled,
            )

            SettingRow(
                title = model.displayName,
                summary = summary,
                icon = model.iconResId,
                enabled = supported && (!isNano || nanoAvailabilityResolved),
                onClick = {
                    if (selectable) {
                        LocalModelManager.selectModel(context, model.id)
                        onRefresh()
                    }
                },
                trailing = {
                    if (isNano) {
                        if (selectable && selectedModel.id == model.id) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = "Selected",
                            )
                        }
                    } else {
                        LocalModelAction(
                            model = model,
                            modelStatus = modelStatus,
                            runtimeStatus = runtimeStatus,
                            runtimeInstalled = runtimeInstalled,
                            selected = selectable && selectedModel.id == model.id,
                            onRefresh = onRefresh,
                        )
                    }
                },
            )

            val modelProgress = when {
                runtimeStatus.isActive &&
                    runtimeStatus.pendingModelId == model.id &&
                    runtimeStatus.totalBytes > 0L ->
                    runtimeStatus.progressPercent / 100f

                modelStatus.state == LocalModelManager.State.DOWNLOADING ->
                    modelStatus.progressPercent / 100f

                else -> null
            }
            if (modelProgress != null) {
                LinearProgressIndicator(
                    progress = { modelProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LocalModelAction(
    model: LocalModelManager.ModelInfo,
    modelStatus: LocalModelManager.Status,
    runtimeStatus: LocalAiRuntimeManager.Status,
    runtimeInstalled: Boolean,
    selected: Boolean,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val runtimeActiveForModel =
        runtimeStatus.isActive && runtimeStatus.pendingModelId == model.id
    val modelDownloadActive =
        modelStatus.state == LocalModelManager.State.DOWNLOADING ||
            modelStatus.state == LocalModelManager.State.WAITING
    val icon = when {
        runtimeActiveForModel || modelDownloadActive -> R.drawable.ic_close
        modelStatus.state == LocalModelManager.State.DOWNLOADED &&
            runtimeInstalled -> R.drawable.ic_delete

        else -> R.drawable.ic_file_download
    }
    val description = when (icon) {
        R.drawable.ic_close -> "Cancel download"
        R.drawable.ic_delete -> "Delete model"
        else -> "Download model"
    }

    Column {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = "Selected",
            )
        }
        IconButton(
            onClick = {
                when {
                    runtimeActiveForModel -> {
                        LocalAiRuntimeManager.cancelRuntimeInstall(
                            context,
                            model.runtime,
                        )
                    }

                    modelDownloadActive -> {
                        LocalModelManager.cancelDownload(context, model.id)
                    }

                    modelStatus.state == LocalModelManager.State.DOWNLOADED &&
                        runtimeInstalled -> {
                        LocalModelManager.removeModel(context, model.id)
                    }

                    else -> {
                        val error = LocalAiRuntimeManager
                            .requestRuntimeAndModelDownload(context, model.id)
                        if (!error.isNullOrBlank()) {
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                onRefresh()
            },
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
            )
        }
    }
}

private fun localModelSummary(
    model: LocalModelManager.ModelInfo,
    nanoAvailabilityResolved: Boolean,
    nanoAvailable: Boolean,
    modelStatus: LocalModelManager.Status,
    runtimeStatus: LocalAiRuntimeManager.Status,
    runtimeInstalled: Boolean,
): String {
    if (model.id == LocalModelManager.MODEL_GEMINI_NANO) {
        return when {
            !nanoAvailabilityResolved -> "Checking availability…"
            nanoAvailable -> "Available · system managed"
            else -> "Not available"
        }
    }
    if (!LocalModelManager.isModelSupported(model)) {
        return LocalModelManager.getModelUnsupportedReason(model)
    }

    val description = listOf(
        model.parameterSize,
        model.quantization,
        LocalModelManager.formatBytes(model.sizeBytes),
        if (model.runtime == LocalModelManager.Runtime.LITERT_LM) {
            "LiteRT-LM"
        } else {
            "llama.cpp"
        },
    ).filter(String::isNotBlank).joinToString(" · ")

    val state = when {
        runtimeStatus.isActive && runtimeStatus.pendingModelId == model.id -> {
            if (
                runtimeStatus.state == LocalAiRuntimeManager.State.DOWNLOADING &&
                runtimeStatus.totalBytes > 0L
            ) {
                "Installing runtime · ${runtimeStatus.progressPercent}%"
            } else {
                "Preparing local AI runtime…"
            }
        }

        modelStatus.state == LocalModelManager.State.DOWNLOADING ->
            "${modelStatus.progressPercent}% downloaded"

        modelStatus.state == LocalModelManager.State.WAITING ->
            "Waiting for a network connection…"

        modelStatus.state == LocalModelManager.State.PARTIALLY_DOWNLOADED ->
            "${LocalModelManager.formatBytes(modelStatus.receivedBytes)} downloaded · tap to resume"

        modelStatus.state == LocalModelManager.State.FAILED ->
            modelStatus.error.ifBlank { "Download failed · tap to retry" }

        modelStatus.state == LocalModelManager.State.DOWNLOADED && !runtimeInstalled ->
            "${LocalAiRuntimeManager.getRuntimeLabel(model.runtime)} required"

        modelStatus.state == LocalModelManager.State.DOWNLOADED -> "Downloaded"
        else -> "Not downloaded"
    }
    return "$description\n$state"
}

private fun cloudConfigurationComplete(context: android.content.Context): Boolean {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    return !prefs.getString(
        KeyAiBaseUrl,
        AiSummaryProviders.defaultBaseUrl,
    ).isNullOrBlank() &&
        AiSummaryApiKeyStore.getApiKey(context).isNotBlank() &&
        !prefs.getString(KeyAiModel, "").isNullOrBlank() &&
        !prefs.getString(KeyAiSystemPrompt, DefaultSystemPrompt).isNullOrBlank()
}

private fun selectFirstReadyLocalModelOrClear(context: android.content.Context) {
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
