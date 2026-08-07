package com.simon.harmonichackernews.network

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import java.io.IOException
import java.util.Collections
import java.util.Locale
import kotlin.synchronized
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** OpenRouter-backed model discovery and pricing for every supported cloud provider.  */
object AiModelCatalog {
    const val PREF_BASE_URL: String = "pref_ai_summary_base_url"
    const val PREF_MODEL: String = "pref_ai_summary_model"

    private const val MODELS_URL = "https://openrouter.ai/api/v1/models"
    private const val MODEL_URL = "https://openrouter.ai/api/v1/model"
    private const val TWELVE_MONTHS_SECONDS = 365L * 24L * 60L * 60L
    private val MAIN_HANDLER = Handler(Looper.getMainLooper())
    private val CACHE: MutableMap<String, MutableList<Model>> = HashMap()

    fun fetchModels(
        provider: AiSummaryProviders.Provider, sort: Sort,
        callback: ModelsCallback
    ): Call? {
        val cacheKey = provider.id + ":" + sort.apiValue
        synchronized(CACHE) {
            val cached = CACHE[cacheKey]
            if (cached != null) {
                MAIN_HANDLER.post { callback.onSuccess(cached) }
                return null
            }
        }

        val urlBuilder = MODELS_URL.toHttpUrl().newBuilder()
            .addQueryParameter("output_modalities", "text")
            .addQueryParameter("sort", sort.apiValue)
        if (provider.catalogProvider != null) {
            urlBuilder.addQueryParameter("providers", provider.catalogProvider)
        }

        val request = Request.Builder().url(urlBuilder.build()).build()
        val call = NetworkComponent.okHttpClientInstance.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    MAIN_HANDLER.post { callback.onError(readableError(e)) }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { closeableResponse ->
                        if (!closeableResponse.isSuccessful || closeableResponse.body == null) {
                            postHttpError(callback, closeableResponse.code)
                            return
                        }
                        val data = JSONObject(closeableResponse.body.string())
                            .getJSONArray("data")
                        val models = parseModels(data, provider)
                        if (models.isEmpty()) {
                            MAIN_HANDLER.post { callback.onError("No compatible text models found") }
                            return
                        }
                        val immutableModels = Collections.unmodifiableList(models)
                        synchronized(CACHE) {
                            CACHE[cacheKey] = immutableModels
                        }
                        MAIN_HANDLER.post { callback.onSuccess(immutableModels) }
                    }
                } catch (e: Exception) {
                    MAIN_HANDLER.post { callback.onError("OpenRouter returned invalid model data") }
                }
            }
        })
        return call
    }

    fun resolveModel(
        provider: AiSummaryProviders.Provider, enteredModelId: String?,
        callback: ModelCallback
    ): Call? {
        val openRouterId = AiSummaryProviders.toOpenRouterModelId(provider, enteredModelId)
        if (provider.catalogAuthor != null && openRouterId.contains("/")
            && !openRouterId.startsWith(provider.catalogAuthor + "/")
        ) {
            MAIN_HANDLER.post {
                callback.onError(
                    "That OpenRouter ID belongs to a different provider"
                )
            }
            return null
        }
        val cached = findCachedModel(openRouterId)
        if (cached != null) {
            MAIN_HANDLER.post { callback.onSuccess(cached) }
            return null
        }

        val separator = openRouterId.indexOf('/')
        if (separator <= 0 || separator >= openRouterId.length - 1) {
            MAIN_HANDLER.post { callback.onError("Use an OpenRouter ID in provider/model-name format") }
            return null
        }

        val url = MODEL_URL.toHttpUrl().newBuilder()
            .addPathSegment(openRouterId.substring(0, separator))
            .addPathSegment(openRouterId.substring(separator + 1))
            .build()
        val request = Request.Builder().url(url).build()
        val call = NetworkComponent.okHttpClientInstance.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    MAIN_HANDLER.post { callback.onError(readableError(e)) }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { closeableResponse ->
                        if (!closeableResponse.isSuccessful || closeableResponse.body == null) {
                            MAIN_HANDLER.post {
                                callback.onError(
                                    if (closeableResponse.code == 404)
                                        "Price not found on OpenRouter"
                                    else
                                        "Price unavailable (HTTP " + closeableResponse.code + ")"
                                )
                            }
                            return
                        }
                        val data = JSONObject(closeableResponse.body.string())
                            .getJSONObject("data")
                        val model = parseModel(data, provider)
                        MAIN_HANDLER.post { callback.onSuccess(model) }
                    }
                } catch (e: Exception) {
                    MAIN_HANDLER.post { callback.onError("Price data could not be read") }
                }
            }
        })
        return call
    }

    /** Selects the requested first-run default without ever baking a model slug into the app.  */
    fun ensureInitialDefault(context: Context) {
        val appContext = context.applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
        if (prefs.contains(PREF_MODEL)) {
            return
        }

        fetchModels(AiSummaryProviders.OPENAI, Sort.PRICE_LOW_TO_HIGH, object : ModelsCallback {
            override fun onSuccess(models: MutableList<Model>) {
                val latestPrefs = PreferenceManager.getDefaultSharedPreferences(appContext)
                val baseUrl = latestPrefs.getString(
                    PREF_BASE_URL,
                    AiSummaryProviders.defaultBaseUrl
                )
                val selectedProvider =
                    AiSummaryProviders.getProviderForBaseUrl(baseUrl)
                if (latestPrefs.contains(PREF_MODEL) || selectedProvider == null || (AiSummaryProviders.PROVIDER_OPENROUTER != selectedProvider.id)) {
                    return
                }

                val cutoff = System.currentTimeMillis() / 1000L - TWELVE_MONTHS_SECONDS
                val selected = cheapestModel(models, cutoff)
                    ?: cheapestModel(models, Long.MIN_VALUE)
                if (selected != null) {
                    latestPrefs.edit()
                        .putString(PREF_BASE_URL, AiSummaryProviders.defaultBaseUrl)
                        .putString(PREF_MODEL, selected.openRouterId)
                        .apply()
                }
            }

            override fun onError(message: String?) {
                // The initializer retries on a future launch while the model preference is absent.
            }
        })
    }

    /** Chooses a dynamic low-cost model after switching direct providers.  */
    fun ensureProviderDefault(context: Context, provider: AiSummaryProviders.Provider) {
        val appContext = context.applicationContext
        fetchModels(provider, Sort.PRICE_LOW_TO_HIGH, object : ModelsCallback {
            override fun onSuccess(models: MutableList<Model>) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(appContext)
                val currentProvider = AiSummaryProviders.getProviderForBaseUrl(
                    prefs.getString(PREF_BASE_URL, AiSummaryProviders.defaultBaseUrl)
                )
                if (prefs.contains(PREF_MODEL) || currentProvider == null || (provider.id != currentProvider.id)) {
                    return
                }
                val selected = cheapestModel(models, Long.MIN_VALUE)
                if (selected != null) {
                    prefs.edit().putString(PREF_MODEL, selected.requestId).apply()
                }
            }

            override fun onError(message: String?) {
            }
        })
    }

    private fun parseModels(
        data: JSONArray,
        provider: AiSummaryProviders.Provider
    ): MutableList<Model> {
        val uniqueModels: MutableMap<String, Model> = LinkedHashMap()
        for (i in 0..<data.length()) {
            val item = data.optJSONObject(i)
            if (item == null) {
                continue
            }
            val model = parseModel(item, provider)
            if (provider.catalogAuthor != null
                && !model.openRouterId.startsWith(provider.catalogAuthor + "/")
            ) {
                continue
            }
            uniqueModels[model.requestId] = model
        }
        return ArrayList(uniqueModels.values)
    }

    private fun parseModel(item: JSONObject, provider: AiSummaryProviders.Provider): Model {
        val openRouterId = item.optString("id", "")
        val requestId = AiSummaryProviders.toProviderModelId(provider, openRouterId)
        val pricing = item.optJSONObject("pricing")
        val inputPrice = parsePrice(pricing, "prompt")
        val outputPrice = parsePrice(pricing, "completion")
        return Model(
            openRouterId,
            requestId,
            item.optString("name", requestId),
            item.optLong("created", 0L),
            inputPrice,
            outputPrice,
            item.optLong("context_length", 0L)
        )
    }

    private fun findCachedModel(openRouterId: String?): Model? {
        synchronized(CACHE) {
            for (models in CACHE.values) {
                for (model in models) {
                    if (model.openRouterId == openRouterId) {
                        return model
                    }
                }
            }
        }
        return null
    }

    private fun cheapestModel(models: List<Model>, createdAfter: Long): Model? {
        var cheapest: Model? = null
        for (model in models) {
            if (model.created < createdAfter || !model.hasPrices()) {
                continue
            }
            if (cheapest == null || model.totalTokenPrice() < cheapest.totalTokenPrice() || (model.totalTokenPrice() == cheapest.totalTokenPrice()
                        && model.created > cheapest.created)
            ) {
                cheapest = model
            }
        }
        return cheapest
    }

    private fun parsePrice(pricing: JSONObject?, key: String): Double {
        if (pricing == null || !pricing.has(key)) {
            return Double.NaN
        }
        try {
            return pricing.optString(key).toDouble()
        } catch (e: NumberFormatException) {
            return Double.NaN
        }
    }

    private fun postHttpError(callback: ModelsCallback, responseCode: Int) {
        MAIN_HANDLER.post {
            callback.onError(
                "Could not load models (HTTP " + responseCode + ")"
            )
        }
    }

    private fun readableError(error: IOException): String {
        val message = error.message
        return if (message?.trim { it <= ' ' }.isNullOrEmpty())
            "Could not reach OpenRouter"
        else
            "Could not reach OpenRouter: " + message
    }

    enum class Sort(val apiValue: String) {
        POPULAR("most-popular"),
        PRICE_LOW_TO_HIGH("pricing-low-to-high")
    }

    interface ModelsCallback {
        fun onSuccess(models: MutableList<Model>)

        fun onError(message: String?)
    }

    interface ModelCallback {
        fun onSuccess(model: Model)

        fun onError(message: String?)
    }

    class Model internal constructor(
        val openRouterId: String, val requestId: String, val name: String, val created: Long,
        val inputPrice: Double, val outputPrice: Double, val contextLength: Long
    ) {
        fun hasPrices(): Boolean = !inputPrice.isNaN() && !outputPrice.isNaN()

        val isFree: Boolean
            get() = hasPrices() && inputPrice == 0.0 && outputPrice == 0.0

        fun providerSlug(): String {
            val separator = openRouterId.indexOf('/')
            return if (separator > 0) openRouterId.substring(0, separator) else ""
        }

        fun totalTokenPrice(): Double = inputPrice + outputPrice

        fun formattedInputPrice(): String = formatPerMillion(inputPrice)

        fun formattedOutputPrice(): String = formatPerMillion(outputPrice)

        companion object {
            private fun formatPerMillion(perTokenPrice: Double): String {
                if (perTokenPrice.isNaN()) {
                    return "—"
                }
                val perMillion = perTokenPrice * 1000000.0
                if (perMillion == 0.0) {
                    return "$0"
                }
                var formatted = String.format(Locale.US, "%.4f", perMillion)
                val decimal = formatted.indexOf('.')
                while (formatted.length > decimal + 3 && formatted.endsWith("0")) {
                    formatted = formatted.substring(0, formatted.length - 1)
                }
                return "$" + formatted
            }
        }
    }
}
