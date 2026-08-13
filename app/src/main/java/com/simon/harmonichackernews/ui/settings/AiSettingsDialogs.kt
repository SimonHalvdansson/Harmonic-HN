package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.OpenRouterProviderIcon
import com.simon.harmonichackernews.network.networkHeader
import com.simon.harmonichackernews.settings.AiSummaryTextSetting
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val appComposition = LocalHarmonicUiDependencies.current
    val repository = appComposition.aiSummarySettings
    val initialValue = remember(setting, defaultValue) {
        repository.text(setting)
    }
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
    val context = LocalContext.current
    val appComposition = LocalHarmonicUiDependencies.current
    val repository = appComposition.aiSummarySettings
    val scope = rememberCoroutineScope()
    val initialUrl = repository.snapshot().baseUrl
    SharedAiSummaryBaseUrlDialog(
        initialUrl = initialUrl,
        presets = AiSummaryProviders.PROVIDERS.map { provider ->
            AiBaseUrlPreset(provider.id, provider.label, provider.baseUrl)
        },
        onSave = { savedUrl ->
            val update = repository.setBaseUrl(savedUrl)
            if (update.needsDefaultModel) {
                update.provider?.let { provider ->
                    scope.launch { appComposition.aiModelDefaults.ensureProviderDefault(provider) }
                }
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun AiModelSelectorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val appComposition = LocalHarmonicUiDependencies.current
    val repository = appComposition.aiSummarySettings
    val baseUrl = repository.snapshot().baseUrl
    val provider = AiSummaryProviders.getProviderForBaseUrl(baseUrl)
        ?: AiSummaryProviders.defaultProvider
    val initialModel = repository.modelForPicker()
    SharedAiModelSelectorDialog(
        initialModel = initialModel,
        provider = provider,
        catalogRepository = appComposition.network.aiModelCatalogRepository,
        onSave = repository::setModelForCurrentProvider,
        onDismiss = onDismiss,
        providerIcon = { providerSlug -> AiModelProviderIcon(providerSlug) },
    )
}

@Composable
private fun AiModelProviderIcon(providerSlug: String) {
    val context = LocalContext.current
    val appComposition = LocalHarmonicUiDependencies.current
    var iconData by remember(providerSlug) { mutableStateOf<Any?>(null) }

    LaunchedEffect(providerSlug, appComposition) {
        iconData = runCatching {
            when (
                val icon = appComposition.network.openRouterProviderIconRepository
                    .resolve(providerSlug).icon
            ) {
                is OpenRouterProviderIcon.RemoteUrl -> icon.url
                is OpenRouterProviderIcon.SvgBytes -> icon.bytes
                null -> null
            }
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = providerSlug.take(1).uppercase().ifEmpty { "?" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = ProductSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        iconData?.let { resolvedIcon ->
            val request = remember(context, resolvedIcon) {
                ImageRequest.Builder(context)
                    .data(resolvedIcon)
                    .networkHeader("User-Agent", NetworkComponent.USER_AGENT)
                    .crossfade(100)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(22.dp).clip(CircleShape),
            )
        }
    }
}
