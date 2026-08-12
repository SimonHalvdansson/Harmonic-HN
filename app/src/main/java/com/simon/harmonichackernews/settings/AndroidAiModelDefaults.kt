package com.simon.harmonichackernews.settings

import android.content.Context
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.network.AiModelCatalogSelection
import com.simon.harmonichackernews.network.AiModelCatalogSort
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.network.NetworkComponent

/** Persists Android defaults selected by the shared model-catalog repository and policy. */
object AndroidAiModelDefaults {
    const val PREF_BASE_URL = AiSummaryPreferenceKeys.BASE_URL
    const val PREF_MODEL = AiSummaryPreferenceKeys.MODEL

    private const val TWELVE_MONTHS_SECONDS = 365L * 24L * 60L * 60L

    fun ensureInitialDefault(context: Context) {
        val appContext = context.applicationContext
        val settings = AndroidAiSummarySettings.repository(appContext)
        val catalog = AndroidAppComposition.get(appContext).network.aiModelCatalogRepository
        if (settings.hasModelSelection()) return
        NetworkComponent.launchCallbackRequest(
            request = {
                catalog.fetchModels(
                    AiSummaryProviders.OPENAI,
                    AiModelCatalogSort.PRICE_LOW_TO_HIGH,
                )
            },
            onSuccess = { models ->
                val latest = settings.snapshot()
                val provider = AiSummaryProviders.getProviderForBaseUrl(
                    latest.baseUrl,
                )
                if (
                    settings.hasModelSelection() ||
                    provider?.id != AiSummaryProviders.PROVIDER_OPENROUTER
                ) return@launchCallbackRequest
                val cutoff = System.currentTimeMillis() / 1_000L - TWELVE_MONTHS_SECONDS
                val selected = AiModelCatalogSelection.cheapestModel(models, cutoff)
                    ?: AiModelCatalogSelection.cheapestModel(models, Long.MIN_VALUE)
                selected?.let {
                    settings.setBaseUrl(AiSummaryProviders.defaultBaseUrl)
                    settings.setModel(it.openRouterId)
                }
            },
            onFailure = {},
        )
    }

    fun ensureProviderDefault(context: Context, provider: AiSummaryProviders.Provider) {
        val appContext = context.applicationContext
        val settings = AndroidAiSummarySettings.repository(appContext)
        val catalog = AndroidAppComposition.get(appContext).network.aiModelCatalogRepository
        NetworkComponent.launchCallbackRequest(
            request = {
                catalog.fetchModels(
                    provider,
                    AiModelCatalogSort.PRICE_LOW_TO_HIGH,
                )
            },
            onSuccess = { models ->
                val currentProvider = AiSummaryProviders.getProviderForBaseUrl(
                    settings.snapshot().baseUrl,
                )
                if (settings.hasModelSelection() || currentProvider?.id != provider.id) {
                    return@launchCallbackRequest
                }
                AiModelCatalogSelection.cheapestModel(models, Long.MIN_VALUE)?.let {
                    settings.setModel(it.requestId)
                }
            },
            onFailure = {},
        )
    }
}
