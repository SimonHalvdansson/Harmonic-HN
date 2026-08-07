package com.simon.harmonichackernews.network

object AiSummaryProviders {
    const val PROVIDER_OPENAI: String = "openai"
    const val PROVIDER_ANTHROPIC: String = "anthropic"
    const val PROVIDER_OPENROUTER: String = "openrouter"
    const val PROVIDER_GOOGLE: String = "google"
    private val ROUTING_VARIANTS = listOf(
        ":free", ":floor", ":nitro", ":online", ":extended", ":exacto"
    )

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

    val PROVIDERS: List<Provider> = listOf(
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

        PROVIDERS.firstOrNull { normalizeUrl(it.baseUrl) == normalizedUrl }?.let { return it }

        val lowerUrl = normalizedUrl.lowercase()
        return when {
            "api.openai.com" in lowerUrl -> OPENAI
            "api.anthropic.com" in lowerUrl -> ANTHROPIC
            "openrouter.ai" in lowerUrl -> defaultProvider
            "generativelanguage.googleapis.com" in lowerUrl -> GOOGLE
            else -> null
        }
    }

    fun getModelForRequest(baseUrl: String?, model: String?): String {
        val provider = getProviderForBaseUrl(baseUrl)
        val requestModel = model.orEmpty().trim { it <= ' ' }
        if (provider == null || PROVIDER_OPENROUTER == provider.id) {
            return requestModel
        }
        return toProviderModelId(provider, requestModel)
    }

    fun toProviderModelId(provider: Provider, openRouterModelId: String?): String {
        var modelId = openRouterModelId.orEmpty().trim { it <= ' ' }
        if (PROVIDER_OPENROUTER == provider.id || provider.catalogAuthor == null) {
            return modelId
        }

        val prefix = provider.catalogAuthor + "/"
        if (modelId.startsWith(prefix)) {
            modelId = modelId.substring(prefix.length)
        }

        // OpenRouter routing variants are not part of direct-provider model IDs. Match only
        // known suffixes so direct IDs such as OpenAI fine-tunes beginning with "ft:" survive.
        for (routingVariant in ROUTING_VARIANTS) {
            if (modelId.endsWith(routingVariant)) {
                modelId = modelId.substring(0, modelId.length - routingVariant.length)
                break
            }
        }
        return modelId
    }

    fun toOpenRouterModelId(provider: Provider, providerModelId: String?): String {
        val modelId = providerModelId.orEmpty().trim { it <= ' ' }
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
        return getProviderForBaseUrl(baseUrl)?.id == PROVIDER_ANTHROPIC
    }

    fun normalizeUrl(url: String?): String {
        var normalized = url.orEmpty().trim { it <= ' ' }
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
