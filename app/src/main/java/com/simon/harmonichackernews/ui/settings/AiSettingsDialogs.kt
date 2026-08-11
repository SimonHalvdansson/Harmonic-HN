package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.OpenRouterProviderIconLoader
import com.simon.harmonichackernews.network.networkHeader
import com.simon.harmonichackernews.settings.AndroidAiModelDefaults
import com.simon.harmonichackernews.ui.theme.ProductSansFontFamily
import com.simon.harmonichackernews.utils.AiSummaryApiKeyStore

@Composable
fun AiSummaryTextDialog(
    preferenceKey: String,
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
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val initialValue = remember(preferenceKey, defaultValue) {
        if (preferenceKey == AiSummaryApiKeyStore.PREF_API_KEY) {
            AiSummaryApiKeyStore.getApiKey(context)
        } else {
            prefs.getString(preferenceKey, defaultValue) ?: defaultValue
        }
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
        asciiInput = preferenceKey == AiSummaryApiKeyStore.PREF_API_KEY,
        onSave = { savedValue ->
            if (preferenceKey == AiSummaryApiKeyStore.PREF_API_KEY) {
                if (!AiSummaryApiKeyStore.setApiKey(context, savedValue)) {
                    return@SharedAiSummaryTextDialog "Couldn't securely save API key"
                }
            } else {
                prefs.edit().putString(preferenceKey, savedValue).apply()
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
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val initialUrl = prefs.getString(
        AndroidAiModelDefaults.PREF_BASE_URL,
        AiSummaryProviders.defaultBaseUrl,
    ) ?: AiSummaryProviders.defaultBaseUrl
    SharedAiSummaryBaseUrlDialog(
        initialUrl = initialUrl,
        presets = AiSummaryProviders.PROVIDERS.map { provider ->
            AiBaseUrlPreset(provider.id, provider.label, provider.baseUrl)
        },
        onSave = { savedUrl ->
            val oldProvider = AiSummaryProviders.getProviderForBaseUrl(
                prefs.getString(
                    AndroidAiModelDefaults.PREF_BASE_URL,
                    AiSummaryProviders.defaultBaseUrl,
                ),
            )
            val newProvider = AiSummaryProviders.getProviderForBaseUrl(savedUrl)
            val editor = prefs.edit().putString(AndroidAiModelDefaults.PREF_BASE_URL, savedUrl)
            if (newProvider != null && newProvider.id != oldProvider?.id) {
                val translated = if (oldProvider == null) {
                    ""
                } else {
                    AiSummaryProviders.translateModelId(
                        oldProvider,
                        newProvider,
                        prefs.getString(AndroidAiModelDefaults.PREF_MODEL, ""),
                    )
                }
                if (translated.isNullOrEmpty()) {
                    editor.remove(AndroidAiModelDefaults.PREF_MODEL)
                } else {
                    editor.putString(AndroidAiModelDefaults.PREF_MODEL, translated)
                }
            }
            editor.apply()
            if (newProvider != null && !prefs.contains(AndroidAiModelDefaults.PREF_MODEL)) {
                AndroidAiModelDefaults.ensureProviderDefault(context, newProvider)
            }
        },
        onDismiss = onDismiss,
    )
}

@Composable
fun AiModelSelectorDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val baseUrl = prefs.getString(
        AndroidAiModelDefaults.PREF_BASE_URL,
        AiSummaryProviders.defaultBaseUrl,
    ) ?: AiSummaryProviders.defaultBaseUrl
    val provider = AiSummaryProviders.getProviderForBaseUrl(baseUrl)
        ?: AiSummaryProviders.defaultProvider
    val initialModel = AiSummaryProviders.getModelForRequest(
        baseUrl,
        prefs.getString(AndroidAiModelDefaults.PREF_MODEL, ""),
    )
    SharedAiModelSelectorDialog(
        initialModel = initialModel,
        provider = provider,
        catalogRepository = NetworkComponent.aiModelCatalogRepository,
        onSave = { selected ->
            prefs.edit()
                .putString(
                    AndroidAiModelDefaults.PREF_MODEL,
                    AiSummaryProviders.toProviderModelId(provider, selected),
                )
                .apply()
        },
        onDismiss = onDismiss,
        providerIcon = { providerSlug -> AiModelProviderIcon(providerSlug) },
    )
}

@Composable
private fun AiModelProviderIcon(providerSlug: String) {
    val context = LocalContext.current
    var iconData by remember(providerSlug) { mutableStateOf<Any?>(null) }

    DisposableEffect(providerSlug) {
        var active = true
        OpenRouterProviderIconLoader.resolve(providerSlug) { resolvedSlug, resolvedIcon ->
            if (active && resolvedSlug.equals(providerSlug, ignoreCase = true)) {
                iconData = resolvedIcon
            }
        }
        onDispose { active = false }
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
