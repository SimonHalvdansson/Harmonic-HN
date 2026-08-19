package com.simon.harmonichackernews.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToLong

enum class AiModelCatalogSort(val apiValue: String) {
    POPULAR("most-popular"),
    PRICE_LOW_TO_HIGH("pricing-low-to-high"),
}

data class AiModel(
    val openRouterId: String,
    val requestId: String,
    val name: String,
    val created: Long,
    val inputPrice: Double,
    val outputPrice: Double,
    val contextLength: Long,
) {
    fun hasPrices(): Boolean = !inputPrice.isNaN() && !outputPrice.isNaN()

    val isFree: Boolean
        get() = hasPrices() && inputPrice == 0.0 && outputPrice == 0.0

    fun providerSlug(): String = openRouterId.substringBefore('/', "")

    fun totalTokenPrice(): Double = inputPrice + outputPrice

    fun displayName(): String = if (isFree) name.replace(freeTitleSuffix, "") else name

    fun formattedInputPrice(): String = formatPerMillion(inputPrice)

    fun formattedOutputPrice(): String = formatPerMillion(outputPrice)

    private fun formatPerMillion(perTokenPrice: Double): String {
        if (perTokenPrice.isNaN()) return "—"
        val perMillion = perTokenPrice * 1_000_000.0
        if (perMillion == 0.0) return "\$0"
        val rounded = (perMillion * 10_000.0).roundToLong() / 10_000.0
        val parts = rounded.toString().split('.', limit = 2)
        val whole = parts[0]
        var decimal = parts.getOrElse(1) { "" }.padEnd(2, '0')
        while (decimal.length > 2 && decimal.endsWith('0')) decimal = decimal.dropLast(1)
        return "\$$whole.$decimal"
    }

    private companion object {
        val freeTitleSuffix = Regex("\\s*\\(free\\)\\s*$", RegexOption.IGNORE_CASE)
    }
}

interface AiModelCatalogRepository {
    suspend fun fetchModels(
        provider: AiSummaryProviders.Provider,
        sort: AiModelCatalogSort,
    ): List<AiModel>

    suspend fun resolveModel(
        provider: AiSummaryProviders.Provider,
        enteredModelId: String?,
    ): AiModel
}

class KtorAiModelCatalogRepository(
    private val client: KtorHttpClient,
) : AiModelCatalogRepository {
    private val cache = mutableMapOf<String, List<AiModel>>()
    private val cacheMutex = Mutex()

    override suspend fun fetchModels(
        provider: AiSummaryProviders.Provider,
        sort: AiModelCatalogSort,
    ): List<AiModel> {
        val cacheKey = "${provider.id}:${sort.apiValue}"
        cacheMutex.withLock { cache[cacheKey] }?.let { return it }

        val url = MODELS_URL.toNetworkUrl().newBuilder()
            .addQueryParameter("output_modalities", "text")
            .addQueryParameter("sort", sort.apiValue)
            .apply {
                provider.catalogProvider?.let { addQueryParameter("providers", it) }
            }
            .build()
        val body = requestBody(HttpRequest.Builder().url(url).get().build()) { code ->
            "Could not load models (HTTP $code)"
        }
        val models = runCatching {
            val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
                ?: throw IllegalArgumentException("Missing model data")
            parseModels(data, provider)
        }.getOrElse {
            throw AiModelCatalogException("OpenRouter returned invalid model data", it)
        }
        if (models.isEmpty()) {
            throw AiModelCatalogException("No compatible text models found")
        }
        cacheMutex.withLock { cache[cacheKey] = models }
        return models
    }

    override suspend fun resolveModel(
        provider: AiSummaryProviders.Provider,
        enteredModelId: String?,
    ): AiModel {
        val openRouterId = AiSummaryProviders.toOpenRouterModelId(provider, enteredModelId)
        if (
            provider.catalogAuthor != null &&
            '/' in openRouterId &&
            !openRouterId.startsWith("${provider.catalogAuthor}/")
        ) {
            throw AiModelCatalogException("That OpenRouter ID belongs to a different provider")
        }
        findCachedModel(openRouterId)?.let { return it }

        val author = openRouterId.substringBefore('/', "")
        val modelName = openRouterId.substringAfter('/', "")
        if (author.isEmpty() || modelName.isEmpty()) {
            throw AiModelCatalogException("Use an OpenRouter ID in provider/model-name format")
        }
        val url = MODEL_URL.toNetworkUrl().newBuilder()
            .addPathSegment(author)
            .addPathSegment(modelName)
            .build()
        val body = requestBody(HttpRequest.Builder().url(url).get().build()) { code ->
            if (code == 404) "Price not found on OpenRouter" else "Price unavailable (HTTP $code)"
        }
        return runCatching {
            val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
                ?: throw IllegalArgumentException("Missing price data")
            parseModel(data, provider)
        }.getOrElse { throw AiModelCatalogException("Price data could not be read", it) }
    }

    private suspend fun requestBody(
        request: HttpRequest,
        httpError: (Int) -> String,
    ): String {
        val response = try {
            client.execute(request)
        } catch (error: Throwable) {
            throw AiModelCatalogException(
                error.message?.trim()?.takeIf(String::isNotEmpty)?.let {
                    "Could not reach OpenRouter: $it"
                } ?: "Could not reach OpenRouter",
                error,
            )
        }
        return try {
            if (!response.isSuccessful) throw AiModelCatalogException(httpError(response.code))
            response.body.readText()
        } finally {
            response.close()
        }
    }

    private fun parseModels(
        data: JsonArray,
        provider: AiSummaryProviders.Provider,
    ): List<AiModel> {
        val uniqueModels = linkedMapOf<String, AiModel>()
        data.forEach { element ->
            val item = element as? JsonObject ?: return@forEach
            val model = parseModel(item, provider)
            if (
                provider.catalogAuthor != null &&
                !model.openRouterId.startsWith("${provider.catalogAuthor}/")
            ) {
                return@forEach
            }
            uniqueModels[model.requestId] = model
        }
        return uniqueModels.values.toList()
    }

    private fun parseModel(
        item: JsonObject,
        provider: AiSummaryProviders.Provider,
    ): AiModel {
        val openRouterId = item.string("id").orEmpty()
        val requestId = AiSummaryProviders.toProviderModelId(provider, openRouterId)
        val pricing = item["pricing"] as? JsonObject
        return AiModel(
            openRouterId = openRouterId,
            requestId = requestId,
            name = item.string("name") ?: requestId,
            created = item.long("created"),
            inputPrice = pricing.price("prompt"),
            outputPrice = pricing.price("completion"),
            contextLength = item.long("context_length"),
        )
    }

    private suspend fun findCachedModel(openRouterId: String): AiModel? = cacheMutex.withLock {
        cache.values.asSequence().flatten().firstOrNull { it.openRouterId == openRouterId }
    }

    private companion object {
        const val MODELS_URL = "https://openrouter.ai/api/v1/models"
        const val MODEL_URL = "https://openrouter.ai/api/v1/model"
        val json = Json { ignoreUnknownKeys = true }
    }
}

object AiModelCatalogSelection {
    fun cheapestModel(models: List<AiModel>, createdAfter: Long): AiModel? =
        models.asSequence()
            .filter {
                it.created >= createdAfter && it.hasPrices() &&
                    !it.openRouterId.endsWith("-batch", ignoreCase = true) &&
                    !it.openRouterId.endsWith("-pro", ignoreCase = true)
            }
            .minWithOrNull(
                compareBy<AiModel>(AiModel::totalTokenPrice)
                    .thenByDescending(AiModel::created)
                    .thenBy { it.name.length },
            )
}

class AiModelCatalogException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

private fun JsonObject?.price(key: String): Double =
    this?.string(key)?.toDoubleOrNull() ?: Double.NaN
