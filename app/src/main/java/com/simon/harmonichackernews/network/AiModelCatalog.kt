package com.simon.harmonichackernews.network

import android.content.Context
import androidx.preference.PreferenceManager
import kotlinx.coroutines.Job

/** Android preferences/callback adapter for the shared OpenRouter model catalog. */
object AiModelCatalog {
    const val PREF_BASE_URL: String = "pref_ai_summary_base_url"
    const val PREF_MODEL: String = "pref_ai_summary_model"

    private const val TWELVE_MONTHS_SECONDS = 365L * 24L * 60L * 60L

    fun fetchModels(
        provider: AiSummaryProviders.Provider,
        sort: Sort,
        callback: ModelsCallback,
    ): Job = NetworkComponent.launchCallbackRequest(
        request = {
            NetworkComponent.aiModelCatalogRepository.fetchModels(provider, sort.shared)
        },
        onSuccess = { callback.onSuccess(it.map(::Model)) },
        onFailure = { callback.onError(it.message ?: "Could not load models") },
    )

    fun resolveModel(
        provider: AiSummaryProviders.Provider,
        enteredModelId: String?,
        callback: ModelCallback,
    ): Job = NetworkComponent.launchCallbackRequest(
        request = {
            NetworkComponent.aiModelCatalogRepository.resolveModel(provider, enteredModelId)
        },
        onSuccess = { callback.onSuccess(Model(it)) },
        onFailure = { callback.onError(it.message ?: "Price unavailable") },
    )

    /** Selects the requested first-run default without baking a model slug into the app. */
    fun ensureInitialDefault(context: Context) {
        val appContext = context.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (prefs.contains(PREF_MODEL)) return

        fetchModels(AiSummaryProviders.OPENAI, Sort.PRICE_LOW_TO_HIGH, object : ModelsCallback {
            override fun onSuccess(models: List<Model>) {
                val latestPrefs = PreferenceManager.getDefaultSharedPreferences(appContext)
                val baseUrl = latestPrefs.getString(
                    PREF_BASE_URL,
                    AiSummaryProviders.defaultBaseUrl,
                )
                val selectedProvider = AiSummaryProviders.getProviderForBaseUrl(baseUrl)
                if (
                    latestPrefs.contains(PREF_MODEL) ||
                    selectedProvider?.id != AiSummaryProviders.PROVIDER_OPENROUTER
                ) {
                    return
                }
                val cutoff = System.currentTimeMillis() / 1_000L - TWELVE_MONTHS_SECONDS
                val selected = cheapestModel(models, cutoff)
                    ?: cheapestModel(models, Long.MIN_VALUE)
                selected?.let {
                    latestPrefs.edit()
                        .putString(PREF_BASE_URL, AiSummaryProviders.defaultBaseUrl)
                        .putString(PREF_MODEL, it.openRouterId)
                        .apply()
                }
            }

            override fun onError(message: String?) = Unit
        })
    }

    /** Chooses a dynamic low-cost model after switching direct providers. */
    fun ensureProviderDefault(context: Context, provider: AiSummaryProviders.Provider) {
        val appContext = context.applicationContext
        fetchModels(provider, Sort.PRICE_LOW_TO_HIGH, object : ModelsCallback {
            override fun onSuccess(models: List<Model>) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
                val currentProvider = AiSummaryProviders.getProviderForBaseUrl(
                    prefs.getString(PREF_BASE_URL, AiSummaryProviders.defaultBaseUrl),
                )
                if (prefs.contains(PREF_MODEL) || currentProvider?.id != provider.id) return
                cheapestModel(models, Long.MIN_VALUE)?.let {
                    prefs.edit().putString(PREF_MODEL, it.requestId).apply()
                }
            }

            override fun onError(message: String?) = Unit
        })
    }

    private fun cheapestModel(models: List<Model>, createdAfter: Long): Model? =
        AiModelCatalogSelection.cheapestModel(models.map(Model::shared), createdAfter)?.let(::Model)

    enum class Sort(internal val shared: AiModelCatalogSort) {
        POPULAR(AiModelCatalogSort.POPULAR),
        PRICE_LOW_TO_HIGH(AiModelCatalogSort.PRICE_LOW_TO_HIGH),
    }

    interface ModelsCallback {
        fun onSuccess(models: List<Model>)
        fun onError(message: String?)
    }

    interface ModelCallback {
        fun onSuccess(model: Model)
        fun onError(message: String?)
    }

    class Model internal constructor(internal val shared: AiModel) {
        val openRouterId: String get() = shared.openRouterId
        val requestId: String get() = shared.requestId
        val name: String get() = shared.name
        val created: Long get() = shared.created
        val inputPrice: Double get() = shared.inputPrice
        val outputPrice: Double get() = shared.outputPrice
        val contextLength: Long get() = shared.contextLength
        fun hasPrices(): Boolean = shared.hasPrices()
        val isFree: Boolean get() = shared.isFree
        fun displayName(): String = shared.displayName()
        fun providerSlug(): String = shared.providerSlug()
        fun totalTokenPrice(): Double = shared.totalTokenPrice()
        fun formattedInputPrice(): String = shared.formattedInputPrice()
        fun formattedOutputPrice(): String = shared.formattedOutputPrice()
    }
}
