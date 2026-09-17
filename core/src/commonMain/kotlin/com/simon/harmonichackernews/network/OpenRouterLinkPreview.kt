package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.OpenRouterModelInfo
import com.simon.harmonichackernews.serialization.JsonObject
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLPathPart

data class OpenRouterModel(val author: String, val slug: String)

internal object OpenRouterLinkPreview {
    fun isOpenRouterUrl(url: String?): Boolean = openRouterModel(url) != null

    fun openRouterModel(url: String?): OpenRouterModel? {
        val parsed = url?.toNetworkUrlOrNull() ?: return null
        if (parsed.host.lowercase().removePrefix("www.") != "openrouter.ai") return null
        val segments = parsed.pathSegments.filter(String::isNotEmpty)
        if (segments.size != 2 || segments.first().lowercase() in openRouterReservedPaths) {
            return null
        }
        return OpenRouterModel(segments[0], segments[1])
    }

    private val openRouterReservedPaths = setOf(
        "about", "activity", "api", "apps", "chat", "collections", "credits", "docs", "enterprise",
        "keys", "models", "privacy", "providers", "rankings", "settings", "terms",
    )

    fun parseOpenRouter(response: String): OpenRouterModelInfo {
        val json = JsonObject(response).optJSONObject("data")
            ?: throw LinkPreviewException("OpenRouter model data not found")
        val id = json.optString("id")
        val idParts = id.split('/')
        if (idParts.size != 2 || idParts.any(String::isBlank)) {
            throw LinkPreviewException("OpenRouter model data not found")
        }
        val apiName = json.nullableString("name")
        val provider = apiName
            ?.substringBefore(':')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: idParts[0].replaceFirstChar(Char::uppercase)
        val architecture = json.optJSONObject("architecture")
        val pricing = json.optJSONObject("pricing")
        val topProvider = json.optJSONObject("top_provider")
        return OpenRouterModelInfo(
            provider = provider,
            name = apiName
                ?.substringAfter(':', missingDelimiterValue = apiName)
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?: idParts[1],
            website = "https://openrouter.ai/$id",
            providerIconUrl = "https://openrouter.ai/images/icons/" +
                provider.encodeURLPathPart() + ".svg",
            description = json.nullableString("description"),
            promptPricePerToken = pricing?.nullableString("prompt"),
            completionPricePerToken = pricing?.nullableString("completion"),
            contextLength = json.optLong("context_length"),
            maxCompletionTokens = topProvider?.optLong("max_completion_tokens") ?: 0,
            inputModalities = architecture.stringList("input_modalities"),
            outputModalities = architecture.stringList("output_modalities"),
            knowledgeCutoff = json.nullableString("knowledge_cutoff"),
        )
    }

    private fun JsonObject?.stringList(key: String): List<String> =
        this?.optJSONArray(key)?.let { values ->
            (0..<values.length()).mapNotNull { index ->
                values.optString(index).takeUnless(String::isBlank)
            }
        }.orEmpty()

    private fun JsonObject.nullableString(key: String): String? =
        (opt(key) as? String)?.takeUnless(String::isEmpty)
}

internal suspend fun HttpClient.loadOpenRouterInfo(url: String): OpenRouterModelInfo {
    val model = OpenRouterLinkPreview.openRouterModel(url)
        ?: throw LinkPreviewException("Invalid OpenRouter model URL")
    val endpoint = "https://openrouter.ai/api/v1/model/" +
        model.author.encodeURLPathPart() + "/" + model.slug.encodeURLPathPart()
    return OpenRouterLinkPreview.parseOpenRouter(getTextOrThrow(endpoint))
}
