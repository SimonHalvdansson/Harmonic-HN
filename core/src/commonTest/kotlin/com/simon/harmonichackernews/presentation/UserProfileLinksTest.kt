package com.simon.harmonichackernews.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.random.Random

class UserProfileLinksTest {
    @Test
    fun detectsExplicitAndBareLinksWithOriginalTextRanges() {
        val text = "My work: https://example.com/a?x=1&y=2 and www.example.org, or blog.example.dev/about."
        val links = detectUserProfileLinks(text)

        assertEquals(
            listOf("https://example.com/a?x=1&y=2", "https://www.example.org", "https://blog.example.dev/about"),
            links.map { it.url },
        )
        assertEquals(
            listOf("https://example.com/a?x=1&y=2", "www.example.org", "blog.example.dev/about"),
            links.map { text.substring(it.start, it.end) },
        )
    }

    @Test
    fun preservesBalancedParenthesesAndTrimsSurroundingPunctuation() {
        val text = "(https://en.wikipedia.org/wiki/Function_(mathematics)). [example.com/about], https://example.org!"

        assertEquals(
            listOf(
                "https://en.wikipedia.org/wiki/Function_(mathematics)",
                "https://example.com/about",
                "https://example.org",
            ),
            detectUserProfileLinks(text).map { it.url },
        )
    }

    @Test
    fun punctuationTrimmingMatchesOriginalForMixedBracketBalances() {
        val random = Random(72183)
        val characters = "abc()[]{}.,;:!?"
        val paths = listOf(
            "article" + ")]}.".repeat(128),
            "((article)))]!}",
            "[{article}])",
            "article([)]",
        ) + List(1_000) {
            buildString {
                repeat(random.nextInt(1, 100)) { append(characters[random.nextInt(characters.length)]) }
            }
        }
        for (path in paths) {
            val candidate = "https://example.com/$path"
            val expected = legacyTrimPunctuation(candidate)
            val text = "My site: $candidate"
            val actual = detectUserProfileLinks(text).single()
            assertEquals(expected, actual.url, candidate)
            assertEquals(expected, text.substring(actual.start, actual.end), candidate)
        }
    }

    private fun legacyTrimPunctuation(value: String): String {
        var end = value.length
        while (end > 0) {
            val trim = when (value[end - 1]) {
                '.', ',', ';', ':', '!', '?' -> true
                ')' -> value.take(end).count { it == ')' } > value.take(end).count { it == '(' }
                ']' -> value.take(end).count { it == ']' } > value.take(end).count { it == '[' }
                '}' -> value.take(end).count { it == '}' } > value.take(end).count { it == '{' }
                else -> false
            }
            if (!trim) break
            end--
        }
        return value.substring(0, end)
    }

    @Test
    fun ignoresEmailCodeVersionsAndUnsupportedSchemes() {
        assertTrue(
            detectUserProfileLinks(
                "Email jane@example.com or me@www.example.org. I use Node.js, Main.kt and notes.txt. " +
                    "Version 1.2.3, ftp://example.com, mailto:example.org and javascript:example.net.",
            ).isEmpty(),
        )
    }

    @Test
    fun doesNotInventLinksInsideWordsOrMalformedDomains() {
        assertTrue(
            detectUserProfileLinks(
                "user_example.com -example.org a..example.com example.com_extra example.com123 " +
                    "https://example.com/%oops https://good.example@evil.example/ https://-example.com",
            ).isEmpty(),
        )
    }

    @Test
    fun allowsUncommonSuffixWhenUserExplicitlyWroteAWebUrl() {
        assertEquals(
            listOf("https://example.photography/me", "https://www.example.photography"),
            detectUserProfileLinks("https://example.photography/me www.example.photography").map { it.url },
        )
    }

    @Test
    fun handlesCaseNewlinesAndUnicodeTextBeforeLinks() {
        val text = "👋 Café\nHTTPS://Example.COM/Hello\nExample.NO"
        val links = detectUserProfileLinks(text)

        assertEquals(listOf("HTTPS://Example.COM/Hello", "https://Example.NO"), links.map { it.url })
        assertEquals(listOf("HTTPS://Example.COM/Hello", "Example.NO"), links.map { text.substring(it.start, it.end) })
    }

    @Test
    fun acceptsAHostWithAPortAndAvoidsPartialInvalidPorts() {
        assertEquals(
            listOf("https://example.com:8443/about"),
            detectUserProfileLinks("example.com:8443/about example.org:999999").map { it.url },
        )
    }

    @Test
    fun recognizesTypebookFromPlainBiographyText() {
        val text = "I am building typebook.lol."
        val link = detectUserProfileLinks(text).single()
        assertEquals("https://typebook.lol", link.url)
        assertEquals("typebook.lol", text.substring(link.start, link.end))
    }

    @Test
    fun emptyAndOrdinaryBiographiesHaveNoLinks() {
        assertTrue(detectUserProfileLinks("").isEmpty())
        assertTrue(detectUserProfileLinks("I write software and read Hacker News.").isEmpty())
    }
}
