package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.settings.AiSummaryTextSetting
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import kotlinx.coroutines.launch

/** Shared settings-aware host for AI text preferences, including secure-save errors. */
@Composable
fun AiSummaryTextDialog(
    setting: AiSummaryTextSetting,
    title: String,
    hint: String,
    defaultValue: String,
    minLines: Int,
    maxLines: Int,
    textSizeSp: Int,
    trimValue: Boolean,
    allowEmpty: Boolean,
    showReset: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val repository = LocalHarmonicUiDependencies.current.aiSummarySettings
    val initialValue = remember(setting, defaultValue) { repository.text(setting) }
    SharedAiSummaryTextDialog(
        title = title,
        hint = hint,
        initialValue = initialValue,
        defaultValue = defaultValue,
        minLines = minLines,
        maxLines = maxLines,
        textSizeSp = textSizeSp,
        trimValue = trimValue,
        allowEmpty = allowEmpty,
        showReset = showReset,
        asciiInput = setting == AiSummaryTextSetting.API_KEY,
        onSave = { savedValue ->
            if (!repository.setText(setting, savedValue)) {
                return@SharedAiSummaryTextDialog "Couldn't securely save API key"
            }
            onSaved()
            null
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun AiSummaryBaseUrlDialog(onDismiss: () -> Unit) {
    val app = LocalHarmonicUiDependencies.current
    val repository = app.aiSummarySettings
    val scope = rememberCoroutineScope()
    SharedAiSummaryBaseUrlDialog(
        initialUrl = repository.snapshot().baseUrl,
        presets = AiSummaryProviders.PROVIDERS.map { provider ->
            AiBaseUrlPreset(provider.id, provider.label, provider.baseUrl)
        },
        onSave = { savedUrl ->
            val update = repository.setBaseUrl(savedUrl)
            if (update.needsDefaultModel) {
                update.provider?.let { provider ->
                    scope.launch { app.aiModelDefaults.ensureProviderDefault(provider) }
                }
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun SharedAiModelSelectorRoute(
    onDismiss: () -> Unit,
    providerIcon: @Composable (String) -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val repository = app.aiSummarySettings
    val provider = AiSummaryProviders.getProviderForBaseUrl(repository.snapshot().baseUrl)
        ?: AiSummaryProviders.defaultProvider
    SharedAiModelSelectorDialog(
        initialModel = repository.modelForPicker(),
        provider = provider,
        catalogRepository = app.network.aiModelCatalogRepository,
        onSave = repository::setModelForCurrentProvider,
        onDismiss = onDismiss,
        providerIcon = providerIcon,
    )
}
