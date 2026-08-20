package com.simon.harmonichackernews.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlPoliciesTest {
    @Test
    fun domainNamePolicyExtractsCommonHttpHosts() {
        assertEquals("example.com", DomainNamePolicy.fromUrl("https://example.com/path?q=1#result"))
        assertEquals("example.com", DomainNamePolicy.fromUrl("http://www.example.com:8080/path"))
        assertEquals("foo-bar.example", DomainNamePolicy.fromUrl("https://foo-bar.example/story"))
        assertEquals("127.0.0.1", DomainNamePolicy.fromUrl("http://127.0.0.1:65535/health"))
    }

    @Test
    fun domainNamePolicyFallsBackForUnusualAuthorities() {
        assertEquals("WWW.Example.COM", DomainNamePolicy.fromUrl("HTTPS://WWW.Example.COM/Path"))
        assertEquals("example.com", DomainNamePolicy.fromUrl("https://user:pass@example.com:8443/path"))
        assertEquals("[2001:db8::1]", DomainNamePolicy.fromUrl("https://[2001:db8::1]:443/path"))
    }

    @Test
    fun testArchiveRedirectPolicyNormalizeDomain() {
        assertEquals("test.com", ArchiveRedirectPolicy.normalizeDomain("test.com"))
        assertEquals("test.com", ArchiveRedirectPolicy.normalizeDomain("https://test.com"))
        assertEquals("test.com", ArchiveRedirectPolicy.normalizeDomain("https://www.test.com/path"))
        assertEquals("test.com", ArchiveRedirectPolicy.normalizeDomain("www.test.com"))
        assertEquals("test.com", ArchiveRedirectPolicy.normalizeDomain("  test.com  "))
        assertEquals("sub.domain.co.uk", ArchiveRedirectPolicy.normalizeDomain("https://sub.domain.co.uk:8080/foo?bar=1"))
        assertEquals("example.com", ArchiveRedirectPolicy.normalizeDomain("//example.com"))
        assertEquals("nytimes.com", ArchiveRedirectPolicy.normalizeDomain("  NYTimes.com  "))
        assertEquals("", ArchiveRedirectPolicy.normalizeDomain("test"))
        assertEquals("", ArchiveRedirectPolicy.normalizeDomain(""))
        assertEquals("", ArchiveRedirectPolicy.normalizeDomain("http://"))
        assertEquals(listOf("test.com"), ArchiveRedirectPolicy.parseDomains("test.com"))
        assertEquals(listOf("test.com", "example.com"), ArchiveRedirectPolicy.parseDomains("test.com, example.com"))
    }
}
