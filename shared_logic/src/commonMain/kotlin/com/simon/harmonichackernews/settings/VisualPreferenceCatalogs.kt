package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.network.FaviconUrlBuilder

data class FaviconProviderOption(
    val value: String,
    val label: String,
    val urlTemplate: String,
)

object FaviconProviderCatalog {
    val options: List<FaviconProviderOption> = listOf(
        option(FaviconPreferences.GOOGLE, "Google"),
        option(FaviconPreferences.DUCK_DUCK_GO, "DuckDuckGo"),
        option(FaviconPreferences.TWENTY, "Twenty icons"),
    )

    private fun option(value: String, label: String) = FaviconProviderOption(
        value = value,
        label = label,
        urlTemplate = FaviconUrlBuilder.faviconUrlTemplate(value),
    )
}
