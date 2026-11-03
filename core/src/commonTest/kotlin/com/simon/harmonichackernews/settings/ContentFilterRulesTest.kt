package com.simon.harmonichackernews.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentFilterRulesTest {
    @Test
    fun wordsAndPhraseIsTheDefaultAndDoesNotMatchInsideUnicodeWords() {
        val rule = TitleFilterRule("AI")
        assertEquals(TitleFilterMatchMode.WORDS_PHRASE, rule.mode)
        for (title in listOf("AI tools", "New ai tools", "AI-powered research", "What is AI?", "chair, then AI")) {
            assertTrue(rule.matches(title), title)
        }
        for (title in listOf("chair", "AIs", "AItools", "x_AI", "AI_tools", "AI研究", "研究AI")) {
            assertFalse(rule.matches(title), title)
        }
    }

    @Test
    fun containsTextRetainsTheShippedSubstringAndWhitespaceBehavior() {
        val rule = TitleFilterRule(" AI ", TitleFilterMatchMode.CONTAINS_TEXT)
        assertTrue(rule.matches("chair"))
        assertTrue(rule.matches("AItools"))
        assertTrue(rule.matches("AI研究"))
        val phrase = TitleFilterRule("machine learning", TitleFilterMatchMode.CONTAINS_TEXT)
        assertTrue(phrase.matches("Machine Learning"))
        assertFalse(phrase.matches("machine  learning"))
        assertFalse(phrase.matches("machine-learning"))
    }

    @Test
    fun phrasesCollapseWhitespaceAndKeepTheirPunctuationLiteral() {
        val phrase = TitleFilterRule(" machine  learning ")
        assertTrue(phrase.matches("New machine\t\nlearning tools"))
        assertTrue(phrase.matches("Machine\u00a0learning: a guide"))
        assertFalse(phrase.matches("machine-learning"))
        assertFalse(phrase.matches("machine learningtools"))
        assertFalse(phrase.matches("premachine learning"))
        val cpp = TitleFilterRule("C++")
        assertTrue(cpp.matches("C++: a tiny compiler"))
        assertFalse(cpp.matches("XC++"))
        assertFalse(cpp.matches("C++Builder"))
        val dotNet = TitleFilterRule(".NET")
        assertTrue(dotNet.matches("Try .net today"))
        assertFalse(dotNet.matches("Try NET today"))
        assertFalse(dotNet.matches(".NETwork"))
    }

    @Test
    fun internalApostrophesContinueWordsButQuotationMarksDelimitThem() {
        assertFalse(TitleFilterRule("can").matches("can't"))
        assertFalse(TitleFilterRule("can").matches("can’t"))
        assertFalse(TitleFilterRule("AI").matches("AI's future"))
        assertFalse(TitleFilterRule("AI").matches("AI’s future"))
        assertTrue(TitleFilterRule("AI").matches("'AI'"))
        assertTrue(TitleFilterRule("AI").matches("‘AI’"))
        assertTrue(TitleFilterRule("can't").matches("Why can't this work?"))
        assertFalse(TitleFilterRule("can't").matches("Why can’t this work?"))
    }

    @Test
    fun combiningMarksAndConnectorPunctuationContinueWords() {
        assertFalse(TitleFilterRule("cafe").matches("cafe\u0301"))
        assertTrue(TitleFilterRule("cafe\u0301").matches("A cafe\u0301 opened"))
        assertFalse(TitleFilterRule("AI").matches("AI\u0301"))
        assertFalse(TitleFilterRule("AI").matches("AI\u203ftools"))
        assertFalse(TitleFilterRule("AI").matches("tools\u203fAI"))
        assertTrue(TitleFilterRule("café").matches("CAFÉ news"))
        // Exact diacritics remain meaningful; this rule performs no accent folding.
        assertFalse(TitleFilterRule("cafe").matches("café news"))
        assertFalse(TitleFilterRule("café").matches("cafe\u0301 news"))
    }

    @Test
    fun contiguousHanTextIsOneRunAndIsNotClaimedToBeNaturalLanguageSegmentation() {
        val phrase = TitleFilterRule("机器学习")
        assertTrue(phrase.matches("机器学习：入门"))
        assertTrue(phrase.matches("关于 机器学习 的文章"))
        assertFalse(phrase.matches("研究机器学习方法"))
        assertFalse(TitleFilterRule("AI").matches("AI研究"))
        assertTrue(TitleFilterRule("слово").matches("СЛОВО: обзор"))
        assertFalse(TitleFilterRule("слово").matches("подслово"))
    }

    @Test
    fun supplementaryLettersContinueWordsWhileEmojiSeparateThem() {
        val deseretCapital = "\uD801\uDC00"
        val deseretLowercase = "\uD801\uDC28"
        assertTrue(TitleFilterRule(deseretCapital).matches("$deseretLowercase!"))
        assertFalse(TitleFilterRule("AI").matches("${deseretCapital}AI"))
        assertFalse(TitleFilterRule("AI").matches("AI$deseretCapital"))
        assertTrue(TitleFilterRule("AI").matches("🚀AI🚀"))
        assertTrue(TitleFilterRule("AI").matches("AI🤖 tools"))
        assertTrue(TitleFilterRule("AI").matches("AI\uD800"))
        assertFalse(TitleFilterRule(" ").matches("Anything"))
        assertFalse(TitleFilterRule("").matches("Anything"))
    }

    @Test
    fun sharedUnicode17ClassificationIncludesSupplementaryScriptsOnEveryPlatform() {
        val planeThreeHan = "\uD880\uDC00" // U+30000 CJK IDEOGRAPH EXTENSION G, First; Lo.
        val tolongSiki = "\uD807\uDDB0" // U+11DB0 TOLONG SIKI LETTER I; Lo, added in Unicode 17.
        for (letter in listOf(planeThreeHan, tolongSiki)) {
            assertFalse(TitleFilterRule("AI").matches("AI$letter"))
            assertFalse(TitleFilterRule("AI").matches("${letter}AI"))
            assertTrue(TitleFilterRule(letter).matches("A $letter story"))
        }
        for (wordCodePoint in listOf(0x0041, 0x0030, 0x0301, 0x203F, 0x10400, 0x11DB0, 0x30000)) {
            assertTrue(ContentFilterWordCharacters.contains(wordCodePoint), "U+${wordCodePoint.toString(16)}")
        }
        for (separator in listOf(-1, 0x0020, 0x002D, 0xD800, 0x1F680, 0x10FFFF, 0x110000)) {
            assertFalse(ContentFilterWordCharacters.contains(separator), "U+${separator.toString(16)}")
        }
    }

    @Test
    fun domainInputExtractsAndNormalizesOnlyTheHost() {
        for (input in listOf(
            " Example.COM ",
            "https://Example.COM:443/news?sort=new#top",
            "HTTP://Example.COM:8080/path",
            "//Example.COM/path",
            "Example.COM:0/path",
            "Example.COM:65535/path",
            "example.com.",
        )) {
            assertEquals("example.com", DomainFilterHostPolicy.normalize(input), input)
        }
        assertEquals("www.example.com", DomainFilterHostPolicy.normalize("https://www.example.com/path"))
        assertEquals("example.com", DomainFilterHostPolicy.normalize("https://example.com/?email=a@b.test"))
    }

    @Test
    fun domainMatchingUsesHostLabelBoundariesAndAnExplicitSubdomainChoice() {
        val exact = DomainFilterRule("example.com", includeSubdomains = false)
        val subdomains = DomainFilterRule("example.com", includeSubdomains = true)
        assertTrue(exact.matches("https://example.com:8080/path"))
        assertFalse(exact.matches("https://www.example.com/path"))
        assertFalse(exact.matches("https://news.example.com/path"))
        assertTrue(subdomains.matches("https://www.example.com/path"))
        assertTrue(subdomains.matches("https://deep.news.example.com/path"))
        for (rule in listOf(exact, subdomains)) {
            for (url in listOf("https://notexample.com", "https://example.com.evil.test", "https://other.test/example.com")) {
                assertFalse(rule.matches(url), "$rule: $url")
            }
        }
        val www = DomainFilterRule("www.example.com", includeSubdomains = false)
        assertTrue(www.matches("https://www.example.com"))
        assertFalse(www.matches("https://example.com"))
    }

    @Test
    fun unicodeAndPunycodeShareOneNontransitionalIdnaRepresentation() {
        val expected = "xn--bcher-kva.example"
        for (input in listOf(
            "bücher.example",
            "https://BÜCHER.example:443/path",
            "https://b%C3%BCcher.example/path",
            "XN--BCHER-KVA.EXAMPLE",
            "bücher。example",
        )) {
            assertEquals(expected, DomainFilterHostPolicy.normalize(input), input)
        }
        assertTrue(DomainFilterRule("bücher.example").matches("https://xn--bcher-kva.example/path"))
        assertTrue(DomainFilterRule(expected).matches("https://bücher.example/path"))
        assertEquals("xn--fa-hia.de", DomainFilterHostPolicy.normalize("faß.de"))
        assertFalse(DomainFilterRule("faß.de").matches("https://fass.de"))
        assertEquals(
            DomainFilterHostPolicy.normalize("éxample.com"),
            DomainFilterHostPolicy.normalize("e\u0301xample.com"),
        )
    }

    @Test
    fun malformedOrAmbiguousHostsDoNotBecomeRules() {
        for (input in listOf(
            "", "https:///example.com", "ftp://example.com", "mailto:user@example.com",
            "https://user@example.com", "user:secret@example.com", "https://user%40example.com",
            "exa mple.com", "https://example.com/with space", "example.com\\path",
            "example.com:", "example.com:65536", "example.com:-1", "example.com:port",
            "[2001:db8::1]", "2001:db8::1", "https://[::1]:443",
            "example..com", "example.com..", ".example.com", "-example.com", "example-.com",
            "some_host.example", "a".repeat(64) + ".example", "xn--a.example", "xn--.example",
            "\u0301example.com", "exa\u0080mple.com", "www\u00a0.example.com",
        )) {
            assertNull(DomainFilterHostPolicy.normalize(input), input)
        }
    }

    @Test
    fun ipv4RulesMatchOnlyTheExactAddress() {
        val rule = DomainFilterRule("192.168.1.1", includeSubdomains = true)
        assertEquals("192.168.1.1", DomainFilterHostPolicy.normalize("http://192.168.1.1:8080/page"))
        assertTrue(rule.matches("http://192.168.1.1/page"))
        assertFalse(rule.matches("http://news.192.168.1.1/page"))
        assertFalse(rule.matches("http://192.168.1.10/page"))
        assertNull(DomainFilterHostPolicy.normalize("999.1.2.3"))
        assertNull(DomainFilterHostPolicy.normalize("192.168.001.1"))
        assertNull(DomainFilterHostPolicy.normalize("127.1"))
    }

    @Test
    fun legacyDomainRulesRetainDisplayDomainSubstringMatchingUntilEdited() {
        val legacy = DomainFilterRule("example", legacyContainsText = true)
        assertTrue(legacy.matches("https://notexample.com"))
        assertTrue(legacy.matches("https://news.example.com"))
        assertFalse(legacy.matches("https://other.test/path/example"))
        assertFalse(DomainFilterRule("www.", legacyContainsText = true).matches("https://www.example.com"))
        assertFalse(DomainFilterRule("example.com").matches("https://notexample.com"))
    }

    @Test
    fun typedStorageRoundTripsPunctuationAndRetainsLegacyModes() {
        val titles = listOf(
            TitleFilterRule("AI", TitleFilterMatchMode.CONTAINS_TEXT),
            TitleFilterRule("New \"quoted\", multiword phrase"),
            TitleFilterRule("机器学习"),
        )
        val domains = listOf(
            DomainFilterRule("example", legacyContainsText = true),
            DomainFilterRule("xn--bcher-kva.example", includeSubdomains = false),
        )
        assertEquals(titles, ContentFilterRuleCodec.decodeTitleRules(ContentFilterRuleCodec.encodeTitleRules(titles)))
        assertEquals(domains, ContentFilterRuleCodec.decodeDomainRules(ContentFilterRuleCodec.encodeDomainRules(domains)))
        assertEquals(emptyList(), ContentFilterRuleCodec.decodeTitleRules(ContentFilterRuleCodec.encodeTitleRules(emptyList())))
        assertEquals(emptyList(), ContentFilterRuleCodec.decodeDomainRules(ContentFilterRuleCodec.encodeDomainRules(emptyList())))
    }

    @Test
    fun savingLargeExistingListsDoesNotCreateAnUnreadableDocument() {
        val titles = (1..2100).map { index -> TitleFilterRule("Existing phrase $index " + "x".repeat(120)) }
        val encoded = ContentFilterRuleCodec.encodeTitleRules(titles)
        assertTrue(encoded.length > 256 * 1024)
        assertEquals(titles, ContentFilterRuleCodec.decodeTitleRules(encoded))
    }

    @Test
    fun unknownOrMalformedDocumentsDoNotSilentlyEraseOrReinterpretRules() {
        for (serialized in listOf(null, "", "not json", "[]", "{\"version\":2,\"rules\":[]}", "{\"version\":\"1\",\"rules\":[]}")) {
            assertNull(ContentFilterRuleCodec.decodeTitleRules(serialized), serialized)
            assertNull(ContentFilterRuleCodec.decodeDomainRules(serialized), serialized)
        }
        for (rule in listOf(
            "{\"text\":\"AI\",\"mode\":\"unknown\"}",
            "{\"text\":\"AI\"}",
            "{\"text\":12,\"mode\":\"words_phrase\"}",
            "{\"text\":\" \",\"mode\":\"words_phrase\"}",
        )) {
            assertNull(ContentFilterRuleCodec.decodeTitleRules("{\"version\":1,\"rules\":[$rule]}"))
        }
        for (rule in listOf(
            "{\"domain\":\"example.com\",\"includeSubdomains\":\"true\",\"legacyContainsText\":false}",
            "{\"domain\":\"example.com\",\"includeSubdomains\":true}",
            "{\"domain\":\"bad host\",\"includeSubdomains\":true,\"legacyContainsText\":false}",
        )) {
            assertNull(ContentFilterRuleCodec.decodeDomainRules("{\"version\":1,\"rules\":[$rule]}"))
        }
    }
}
