package com.simon.harmonichackernews.network

object FaviconUrlBuilder {
    const val PROVIDER_GOOGLE = "google"
    const val PROVIDER_DUCKDUCKGO = "duckduckgo"
    const val PROVIDER_TWENTY = "twenty"

    fun sanitizeProvider(provider: String?): String = when (provider) {
        PROVIDER_DUCKDUCKGO, PROVIDER_TWENTY -> provider
        else -> PROVIDER_GOOGLE
    }

    fun faviconUrl(pageUrl: String, provider: String?): String {
        val parsed = pageUrl.toNetworkUrlOrNull()
            ?: throw IllegalArgumentException("Invalid page URL")
        return faviconUrlForHost(parsed.host.removePrefix("www."), provider)
    }

    fun faviconUrlTemplate(provider: String?): String = faviconUrlForHost("{host}", provider)

    fun faviconUrlForHost(host: String, provider: String?): String =
        when (sanitizeProvider(provider)) {
            PROVIDER_TWENTY -> "https://twenty-icons.com/$host"
            PROVIDER_DUCKDUCKGO -> "https://icons.duckduckgo.com/ip3/$host.ico"
            else -> "https://www.google.com/s2/favicons?domain=$host&sz=128"
        }
}
