package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
    val initialValue by produceState<String?>(null, repository, setting, defaultValue) {
        value = repository.text(setting)
    }
    val loadedValue = initialValue ?: return
    AiSummaryTextDialog(
        title = title,
        hint = hint,
        initialValue = loadedValue,
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
                return@AiSummaryTextDialog "Couldn't securely save API key"
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
    AiSummaryBaseUrlDialog(
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
fun AiModelSelectorRoute(
    onDismiss: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val repository = app.aiSummarySettings
    val provider = AiSummaryProviders.getProviderForBaseUrl(repository.snapshot().baseUrl)
        ?: AiSummaryProviders.defaultProvider
    AiModelSelectorDialog(
        initialModel = repository.modelForPicker(),
        provider = provider,
        catalogRepository = app.network.aiModelCatalogRepository,
        onSave = repository::setModelForCurrentProvider,
        onDismiss = onDismiss,
        providerIcon = { providerSlug ->
            AiModelProviderIcon(
                providerSlug = providerSlug,
                repository = app.network.openRouterProviderIconRepository,
                userAgent = app.network.userAgent,
            )
        },
    )
}
