package com.simon.harmonichackernews.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlPoliciesTest {
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
