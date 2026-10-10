package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.settings.FaviconPreferences
import com.simon.harmonichackernews.settings.FaviconProviderCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

class FaviconUrlBuilderTest {
    @Test
    fun dialogTemplatesAndRequestsUseTheSavedProviderValues() {
        val expectedTemplates = mapOf(
            FaviconPreferences.GOOGLE to "https://www.google.com/s2/favicons?domain={host}&sz=128",
            FaviconPreferences.DUCK_DUCK_GO to "https://icons.duckduckgo.com/ip3/{host}.ico",
            FaviconPreferences.TWENTY to "https://twenty-icons.com/{host}",
        )
        assertEquals(expectedTemplates, FaviconProviderCatalog.options.associate { it.value to it.urlTemplate })
        expectedTemplates.forEach { (provider, template) ->
            assertEquals(
                template.replace("{host}", "example.com"),
                FaviconUrlBuilder.faviconUrl("https://www.example.com/article", provider),
            )
        }
    }

    @Test
    fun unknownProvidersFallBackToGoogle() {
        listOf(null, "", "unknown").forEach { provider ->
            assertEquals(
                "https://www.google.com/s2/favicons?domain=example.com&sz=128",
                FaviconUrlBuilder.faviconUrlForHost("example.com", provider),
            )
        }
    }

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
