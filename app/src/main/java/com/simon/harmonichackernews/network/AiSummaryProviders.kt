package com.simon.harmonichackernews.network

import androidx.annotation.Nullable
import java.util.Locale

object AiSummaryProviders {
    const val PROVIDER_OPENAI: String = "openai"
    const val PROVIDER_ANTHROPIC: String = "anthropic"
    const val PROVIDER_OPENROUTER: String = "openrouter"
    const val PROVIDER_GOOGLE: String = "google"

    val OPENAI: Provider = Provider(
        PROVIDER_OPENAI,
        "OpenAI",
        "https://api.openai.com/v1",
        "OpenAI",
        "openai"
    )

    val ANTHROPIC: Provider = Provider(
        PROVIDER_ANTHROPIC,
        "Anthropic",
        "https://api.anthropic.com/v1",
        "Anthropic",
        "anthropic"
    )

    val defaultProvider: Provider = Provider(
        PROVIDER_OPENROUTER,
        "OpenRouter",
        "https://openrouter.ai/api/v1",
        null,
        null
    )

    val GOOGLE: Provider = Provider(
        PROVIDER_GOOGLE,
        "Google",
        "https://generativelanguage.googleapis.com/v1beta/openai",
        "Google AI Studio",
        "google"
    )

    val PROVIDERS: Array<Provider> = arrayOf<Provider>(
        OPENAI,
        ANTHROPIC,
        defaultProvider,
        GOOGLE
    )

    val defaultBaseUrl: String
        get() = defaultProvider.baseUrl

    fun getProviderForBaseUrl(baseUrl: String?): Provider? {
        val normalizedUrl = normalizeUrl(baseUrl)
        if (normalizedUrl.isEmpty()) {
            return defaultProvider
        }

        for (provider in PROVIDERS) {
            if (normalizeUrl(provider.baseUrl) == normalizedUrl) {
                return provider
            }
        }

        val lowerUrl = normalizedUrl.lowercase()
        if (lowerUrl.contains("api.openai.com")) {
            return OPENAI
        } else if (lowerUrl.contains("api.anthropic.com")) {
            return ANTHROPIC
        } else if (lowerUrl.contains("openrouter.ai")) {
            return defaultProvider
        } else if (lowerUrl.contains("generativelanguage.googleapis.com")) {
            return GOOGLE
        }
        return null
    }

    fun getModelForRequest(baseUrl: String?, model: String?): String {
        val provider = getProviderForBaseUrl(baseUrl)
        val requestModel = if (model == null) "" else model.trim { it <= ' ' }
        if (provider == null || PROVIDER_OPENROUTER == provider.id) {
            return requestModel
        }
        return toProviderModelId(provider, requestModel)
    }

    fun toProviderModelId(provider: Provider, openRouterModelId: String?): String {
        var modelId = if (openRouterModelId == null) "" else openRouterModelId.trim { it <= ' ' }
        if (PROVIDER_OPENROUTER == provider.id || provider.catalogAuthor == null) {
            return modelId
        }

        val prefix = provider.catalogAuthor + "/"
        if (modelId.startsWith(prefix)) {
            modelId = modelId.substring(prefix.length)
        }

        // OpenRouter routing variants are not part of direct-provider model IDs. Match only
        // known suffixes so direct IDs such as OpenAI fine-tunes beginning with "ft:" survive.
        val routingVariants: Array<String> = arrayOf(
            ":free", ":floor", ":nitro", ":online", ":extended", ":exacto"
        )
        for (routingVariant in routingVariants) {
            if (modelId.endsWith(routingVariant)) {
                modelId = modelId.substring(0, modelId.length - routingVariant.length)
                break
            }
        }
        return modelId
    }

    fun toOpenRouterModelId(provider: Provider, providerModelId: String?): String {
        val modelId = if (providerModelId == null) "" else providerModelId.trim { it <= ' ' }
        if (modelId.isEmpty() || PROVIDER_OPENROUTER == provider.id
            || provider.catalogAuthor == null || modelId.contains("/")
        ) {
            return modelId
        }
        return provider.catalogAuthor + "/" + modelId
    }

    fun translateModelId(
        oldProvider: Provider, newProvider: Provider,
        modelId: String?
    ): String {
        val openRouterId = toOpenRouterModelId(oldProvider, modelId)
        if (openRouterId.isEmpty()) {
            return ""
        }
        if (PROVIDER_OPENROUTER == newProvider.id) {
            return openRouterId
        }
        if (newProvider.catalogAuthor != null
            && openRouterId.startsWith(newProvider.catalogAuthor + "/")
        ) {
            return toProviderModelId(newProvider, openRouterId)
        }
        return ""
    }

    fun isAnthropicBaseUrl(baseUrl: String?): Boolean {
        val provider = getProviderForBaseUrl(baseUrl)
        return provider != null && PROVIDER_ANTHROPIC == provider.id
    }

    fun normalizeUrl(url: String?): String {
        var normalized = if (url == null) "" else url.trim { it <= ' ' }
        while (normalized.endsWith("/") && normalized.length > 1) {
            normalized = normalized.substring(0, normalized.length - 1)
        }
        return normalized
    }

    class Provider internal constructor(
        val id: String, val label: String, val baseUrl: String,
        val catalogProvider: String?, val catalogAuthor: String?
    )
}
