package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.network.AiModelCatalogRepository
import com.simon.harmonichackernews.network.AiModelCatalogSelection
import com.simon.harmonichackernews.network.AiModelCatalogSort
import com.simon.harmonichackernews.network.AiSummaryProviders
import kotlin.time.Clock

/** Shared model-selection policy used by every UI host. */
class AiModelDefaultsUseCase(
    private val settings: AiSummarySettingsRepository,
    private val catalog: AiModelCatalogRepository,
    private val nowEpochSeconds: () -> Long = {
        Clock.System.now().toEpochMilliseconds() / 1_000L
    },
) {
    suspend fun ensureInitialDefault(): Boolean = runCatching {
        if (settings.hasModelSelection()) return false
        val models = catalog.fetchModels(
            AiSummaryProviders.OPENAI,
            AiModelCatalogSort.PRICE_LOW_TO_HIGH,
        )
        val latest = settings.snapshot()
        val provider = AiSummaryProviders.getProviderForBaseUrl(latest.baseUrl)
        if (settings.hasModelSelection() || provider?.id != AiSummaryProviders.PROVIDER_OPENROUTER) {
            return false
        }
        val cutoff = nowEpochSeconds() - TWELVE_MONTHS_SECONDS
        val selected = AiModelCatalogSelection.cheapestModel(models, cutoff)
            ?: AiModelCatalogSelection.cheapestModel(models, Long.MIN_VALUE)
            ?: return false
        settings.setBaseUrl(AiSummaryProviders.defaultBaseUrl)
        settings.setModel(selected.openRouterId)
        true
    }.getOrDefault(false)

    suspend fun ensureProviderDefault(provider: AiSummaryProviders.Provider): Boolean =
        runCatching {
            val models = catalog.fetchModels(provider, AiModelCatalogSort.PRICE_LOW_TO_HIGH)
            val currentProvider = AiSummaryProviders.getProviderForBaseUrl(
                settings.snapshot().baseUrl,
            )
            if (settings.hasModelSelection() || currentProvider?.id != provider.id) return false
            val selected = AiModelCatalogSelection.cheapestModel(models, Long.MIN_VALUE)
                ?: return false
            settings.setModel(selected.requestId)
            true
        }.getOrDefault(false)

    private companion object {
        const val TWELVE_MONTHS_SECONDS = 365L * 24L * 60L * 60L
    }
}
