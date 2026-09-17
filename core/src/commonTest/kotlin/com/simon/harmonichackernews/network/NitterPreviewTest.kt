package com.simon.harmonichackernews.network

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NitterPreviewTest {
    @Test
    fun instanceValidationNormalizesServerOrigins() {
        assertEquals("https://nitter.example.org", NitterInstance.normalize("  HTTPS://NITTER.EXAMPLE.ORG/  "))
        assertEquals("http://localhost:8080", NitterInstance.normalize("http://localhost:8080/"))
        listOf("", "nitter.example.org", "ftp://example.org", "https://", "https://example.org/tweet",
            "https://user:password@example.org", "https://example.org?x=1", "https://example.org#fragment",
            "https://bad host.org", "https://example.org:99999").forEach {
            assertNull(NitterInstance.normalize(it), it)
        }
    }

    @Test
    fun conversionUsesConfiguredOriginAndPreservesStatusQueryAndFragment() {
        assertEquals(
            "http://localhost:8080/example/status/123/photo/1?lang=en#reply",
            NitterPreview.convertUrl("https://mobile.twitter.com/example/status/123/photo/1?lang=en#reply", "http://localhost:8080/"),
        )
        assertEquals("https://nitter.net/example/status/123", NitterPreview.convertUrl("http://x.com:8080/example/status/123"))
        assertTrue(NitterPreview.isNitterUrl("https://nitter.example.org/example/status/123", "https://nitter.example.org"))
        assertFalse(NitterPreview.isNitterUrl("https://nitter.example.org.evil.org/example/status/123", "https://nitter.example.org"))
        assertFalse(NitterPreview.isSameInstance("http://localhost:8081/example/status/123", "http://localhost:8080/example/status/123"))
    }

    @Test
    fun convertibleUrlRecognizesOnlySupportedStatusPaths() {
        assertTrue(NitterPreview.isConvertibleUrl("https://x.com/example/status/123"))
        assertTrue(
            NitterPreview.isConvertibleUrl(
                "https://mobile.twitter.com/i/web/status/456/photo/1",
            ),
        )

        assertFalse(NitterPreview.isConvertibleUrl("https://example.com/example/status/123"))
        assertFalse(NitterPreview.isConvertibleUrl("https://x.com/example/status/not-a-number"))
        assertFalse(NitterPreview.isConvertibleUrl("https://x.com/name_is_too_long/status/123"))
    }
}
