package com.simon.harmonichackernews.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkErrorUtilsTest {
    @Test
    fun rateLimitTextAcceptsStatusAndServerWordingAcrossFields() {
        assertTrue(NetworkErrorUtils.isRateLimitedText("HTTP 429", null))
        assertTrue(NetworkErrorUtils.isRateLimitedText(null, "Too Many Requests"))
        assertFalse(NetworkErrorUtils.isRateLimitedText("Unavailable", "Try again"))
    }
}
