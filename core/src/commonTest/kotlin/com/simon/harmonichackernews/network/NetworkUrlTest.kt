package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NetworkUrlTest {
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
