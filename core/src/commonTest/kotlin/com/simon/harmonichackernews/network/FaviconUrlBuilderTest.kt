package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals

class FaviconUrlBuilderTest {
    @Test
    fun extractsCommonHostsWithoutChangingProviderUrls() {
        assertEquals(
            "https://www.google.com/s2/favicons?domain=example.com&sz=128",
            FaviconUrlBuilder.faviconUrl("https://www.example.com/path?q=1#result", "google"),
        )
        assertEquals(
            "https://icons.duckduckgo.com/ip3/example.com.ico",
            FaviconUrlBuilder.faviconUrl("http://example.com:8080/path", "duckduckgo"),
        )
        assertEquals(
            "https://twenty-icons.com/example.com",
            FaviconUrlBuilder.faviconUrl("https://example.com/#", "twenty"),
        )
    }

    @Test
    fun preservesFallbackParsingForUnusualAuthorities() {
        assertEquals(
            "https://www.google.com/s2/favicons?domain=example.com&sz=128",
            FaviconUrlBuilder.faviconUrl("https://user:pass@example.com:8443/path", null),
        )
        assertEquals(
            "https://www.google.com/s2/favicons?domain=WWW.Example.COM&sz=128",
            FaviconUrlBuilder.faviconUrl("HTTPS://WWW.Example.COM/Path", null),
        )
    }

    @Test
    fun preservesNetworkParserFallbacksForNonHttpInputs() {
        listOf("", "not a URL", "//example.com/path").forEach { url ->
            val legacyHost = requireNotNull(url.toNetworkUrlOrNull()).host.removePrefix("www.")
            assertEquals(
                FaviconUrlBuilder.faviconUrlForHost(legacyHost, null),
                FaviconUrlBuilder.faviconUrl(url, null),
            )
        }
    }
}
