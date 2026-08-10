package com.simon.harmonichackernews.settings

import android.content.Context
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.network.AiModelCatalogSelection
import com.simon.harmonichackernews.network.AiModelCatalogSort
import com.simon.harmonichackernews.network.AiSummaryProviders
import com.simon.harmonichackernews.network.NetworkComponent

/** Persists Android defaults selected by the shared model-catalog repository and policy. */
object AndroidAiModelDefaults {
    const val PREF_BASE_URL = "pref_ai_summary_base_url"
    const val PREF_MODEL = "pref_ai_summary_model"

    private const val TWELVE_MONTHS_SECONDS = 365L * 24L * 60L * 60L

    fun ensureInitialDefault(context: Context) {
        val appContext = context.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (prefs.contains(PREF_MODEL)) return
        NetworkComponent.launchCallbackRequest(
            request = {
                NetworkComponent.aiModelCatalogRepository.fetchModels(
                    AiSummaryProviders.OPENAI,
                    AiModelCatalogSort.PRICE_LOW_TO_HIGH,
                )
            },
            onSuccess = { models ->
                val latestPrefs = PreferenceManager.getDefaultSharedPreferences(appContext)
                val provider = AiSummaryProviders.getProviderForBaseUrl(
                    latestPrefs.getString(PREF_BASE_URL, AiSummaryProviders.defaultBaseUrl),
                )
                if (
                    latestPrefs.contains(PREF_MODEL) ||
                    provider?.id != AiSummaryProviders.PROVIDER_OPENROUTER
                ) return@launchCallbackRequest
                val cutoff = System.currentTimeMillis() / 1_000L - TWELVE_MONTHS_SECONDS
                val selected = AiModelCatalogSelection.cheapestModel(models, cutoff)
                    ?: AiModelCatalogSelection.cheapestModel(models, Long.MIN_VALUE)
                selected?.let {
                    latestPrefs.edit()
                        .putString(PREF_BASE_URL, AiSummaryProviders.defaultBaseUrl)
                        .putString(PREF_MODEL, it.openRouterId)
                        .apply()
                }
            },
            onFailure = {},
        )
    }

    fun ensureProviderDefault(context: Context, provider: AiSummaryProviders.Provider) {
        val appContext = context.applicationContext
        NetworkComponent.launchCallbackRequest(
            request = {
                NetworkComponent.aiModelCatalogRepository.fetchModels(
                    provider,
                    AiModelCatalogSort.PRICE_LOW_TO_HIGH,
                )
            },
            onSuccess = { models ->
                val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
                val currentProvider = AiSummaryProviders.getProviderForBaseUrl(
                    prefs.getString(PREF_BASE_URL, AiSummaryProviders.defaultBaseUrl),
                )
                if (prefs.contains(PREF_MODEL) || currentProvider?.id != provider.id) {
                    return@launchCallbackRequest
                }
                AiModelCatalogSelection.cheapestModel(models, Long.MIN_VALUE)?.let {
                    prefs.edit().putString(PREF_MODEL, it.requestId).apply()
                }
            },
            onFailure = {},
        )
    }
}
