package com.simon.harmonichackernews.utils

import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HackerNewsCaptchaWebProtocolTest {
    @Test
    fun onlyTheTrustedHttpsOriginCanNavigateTheMainFrame() {
        for (url in listOf(
            "https://news.ycombinator.com/",
            "https://NEWS.YCOMBINATOR.COM:443/login?goto=news",
            "about:blank",
        )) assertTrue(HackerNewsCaptchaWebProtocol.allowsNavigation(url, isMainFrame = true), url)
        for (url in listOf(
            null, "", "http://news.ycombinator.com/", "https://news.ycombinator.com:8443/",
            "https://news.ycombinator.com./", "https://news.ycombinator.com.example/",
            "https://example.com/news.ycombinator.com", "https://user:pass@news.ycombinator.com/",
            "https://news.ycombinator.com@evil.example/", "javascript:alert(1)",
            "file:///tmp/challenge.html", "data:text/html,hello", "about:srcdoc",
            "https://www.google.com/recaptcha/api2/anchor",
            "https://www.recaptcha.net/recaptcha/api2/bframe",
        )) assertFalse(HackerNewsCaptchaWebProtocol.allowsNavigation(url, isMainFrame = true), url)
    }

    @Test
    fun captchaFramesAllowOnlyTheExpectedHttpsHosts() {
        for (host in listOf("google.com", "www.google.com", "recaptcha.google.com", "recaptcha.net", "www.recaptcha.net")) {
            assertTrue(HackerNewsCaptchaWebProtocol.allowsNavigation("https://$host/recaptcha/api2/anchor", false), host)
        }
        for (url in listOf(
            "https://google.com.evil.example/recaptcha", "https://other.google.com/recaptcha",
            "http://www.google.com/recaptcha", "https://www.google.com:8443/recaptcha",
            "https://www.google.com./recaptcha", "https://user@www.google.com/recaptcha",
            "https://evil.example/", "https://www.gstatic.com/unrelated",
        )) assertFalse(HackerNewsCaptchaWebProtocol.allowsNavigation(url, false), url)
    }

    @Test
    fun responseDecodingPreservesQuotesUnicodeAndEmptyOrMalformedResults() {
        assertEquals("token \"quoted\" ☃", HackerNewsCaptchaWebProtocol.decodeResponse("\"token \\\"quoted\\\" \\u2603\""))
        for (value in listOf(null, "", "null", "\"\"", "{broken")) {
            assertEquals("", HackerNewsCaptchaWebProtocol.decodeResponse(value))
        }
    }

    @Test
    fun generatedWidgetReportsLifecycleStatesAndEscapesTheSiteKey() {
        val challenge = HackerNewsCaptchaChallenge(
            "https://news.ycombinator.com/login", "fixture\"'<&>", emptyList(), true,
        )
        val html = challenge.captchaHtml
        assertTrue(html.contains("data-sitekey=\"fixture&quot;&#39;&lt;&amp;&gt;\""))
        assertFalse(html.contains("data-sitekey=\"fixture\"'"))
        for (status in listOf("loading", "ready", "solved", "error", "expired")) {
            assertTrue(html.contains("window.__harmonicCaptchaStatus='$status'"), status)
        }
        assertTrue(html.contains("onerror=\"harmonicCaptchaError()\""))
        assertTrue(html.contains("data-error-callback=\"harmonicCaptchaError\""))
        assertTrue(html.contains("data-expired-callback=\"harmonicCaptchaExpired\""))
        assertTrue(HackerNewsCaptchaWebProtocol.STATUS_SCRIPT.contains("__harmonicCaptchaStatus"))
        assertTrue(HackerNewsCaptchaWebProtocol.RESPONSE_SCRIPT.contains(HackerNewsCaptchaWebProtocol.RESPONSE_FIELD))
        assertTrue(HackerNewsCaptchaWebProtocol.TRUSTED_STATUS_SCRIPT.contains("location.origin==='https://news.ycombinator.com'"))
        assertTrue(HackerNewsCaptchaWebProtocol.TRUSTED_RESPONSE_SCRIPT.contains("location.origin==='https://news.ycombinator.com'"))
    }
}
