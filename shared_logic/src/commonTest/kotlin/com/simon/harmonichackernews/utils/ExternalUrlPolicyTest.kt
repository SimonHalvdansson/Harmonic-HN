package com.simon.harmonichackernews.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalUrlPolicyTest {
    @Test
    fun fallbackPreservesWebSchemesAndAddsOneWhenMissing() {
        assertEquals("https://example.com", ExternalUrlPolicy.ensureHttpScheme("https://example.com"))
        assertEquals("HTTP://example.com", ExternalUrlPolicy.ensureHttpScheme("HTTP://example.com"))
        assertEquals("http://example.com", ExternalUrlPolicy.ensureHttpScheme("example.com"))
    }
}
