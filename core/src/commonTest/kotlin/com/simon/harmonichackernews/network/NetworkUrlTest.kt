package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NetworkUrlTest {
    @Test
    fun queryAndFragmentReferencesPreserveTheEntireDocumentPath() {
        val base = "https://example.com/releases/tag/v1?view=full#old".toNetworkUrl()
        val expected = mapOf(
            "#details" to "https://example.com/releases/tag/v1?view=full#details",
            "?view=compact" to "https://example.com/releases/tag/v1?view=compact",
            "?view=compact#details" to "https://example.com/releases/tag/v1?view=compact#details",
            "" to "https://example.com/releases/tag/v1?view=full",
        )
        for ((reference, url) in expected) {
            assertEquals(url, base.resolve(reference)?.toString(), reference)
            assertEquals(url, base.resolve(" $reference ")?.toString(), "padded $reference")
        }
    }

    @Test
    fun relativePathsAndAbsoluteUrlsStillResolveNormally() {
        val base = "https://example.com/releases/tag/v1".toNetworkUrl()
        assertEquals("https://example.com/releases/tag/v2", base.resolve("v2")?.toString())
        assertEquals("https://example.com/issues/1", base.resolve("/issues/1")?.toString())
        assertEquals("https://other.example/item", base.resolve("https://other.example/item")?.toString())
    }

    @Test
    fun destinationPathsAndAuthoritiesDoNotInheritBaseQueryOrFragment() {
        val base = "https://user:secret@example.com:8443/releases/tag/v1?edition=1#old".toNetworkUrl()
        val expected = mapOf(
            "v2" to "https://user:secret@example.com:8443/releases/tag/v2",
            "v2?edition=2#new" to "https://user:secret@example.com:8443/releases/tag/v2?edition=2#new",
            "/issues/1" to "https://user:secret@example.com:8443/issues/1",
            "https://news.ycombinator.com/item?id=42" to "https://news.ycombinator.com/item?id=42",
            "https://other.example/" to "https://other.example/",
            "//other.example/item?id=42" to "https://other.example/item?id=42",
        )
        for ((reference, url) in expected) {
            assertEquals(url, base.resolve(reference)?.toString(), reference)
            assertEquals(url, base.resolve(" $reference ")?.toString(), "padded $reference")
        }
    }

    @Test
    fun nullableParsingRejectsMalformedPercentEscapes() {
        for (url in listOf(
            "https://example.com/search?q=%s",
            "https://example.com/search?q=%",
            "https://example.com/search?q=%2",
            "https://example.com/search?q=%GG",
            "https://example.com/search?%s=value",
            "https://example.com/path/%s",
            "https://example.com/#%s",
        )) {
            assertNull(url.toNetworkUrlOrNull(), url)
        }
    }

    @Test
    fun relativeResolutionRejectsMalformedPercentEscapes() {
        val base = "https://example.com/directory/article".toNetworkUrl()
        for (relative in listOf("?q=%s", "next?q=%GG", "?%s=value", "/path/%s", "#%s")) {
            assertNull(base.resolve(relative), relative)
        }
    }

    @Test
    fun validEscapesStillDecodeNormally() {
        val url = assertNotNull(
            "https://example.com/read%20me?q=%25s&plus=%2B#part%202".toNetworkUrlOrNull(),
        )
        assertEquals("/read%20me", url.encodedPath)
        assertEquals(listOf("read me"), url.pathSegments)
        assertEquals("%s", url.queryParameter("q"))
        assertEquals("+", url.queryParameter("plus"))
        assertEquals("part 2", url.fragment)

        val base = "https://example.com/directory/article".toNetworkUrl()
        val resolved = assertNotNull(base.resolve("/next?q=%25s#part%202"))
        assertEquals("https://example.com/next?q=%25s#part%202", resolved.toString())
        assertEquals("%s", resolved.queryParameter("q"))
    }

    @Test
    fun nullableParsingRejectsNullAndInvalidPorts() {
        assertNull(NetworkUrl.parseOrNull(null))
        assertNull("https://example.com:99999/".toNetworkUrlOrNull())
        assertNull("https://example.com/".toNetworkUrl().resolve("https://example.com:99999/"))
    }
}
