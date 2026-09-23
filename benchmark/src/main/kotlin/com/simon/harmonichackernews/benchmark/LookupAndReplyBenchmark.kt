package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.network.ReplyText
import com.simon.harmonichackernews.serialization.JsonObject
import com.simon.harmonichackernews.settings.ContentFilterKeys
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.UserTagCodec
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Old and production paths in the same non-debuggable APK, one lookup/preview per iteration.
 * Fixtures and parity assertions stay outside measurement. Baselines retain the pre-change code.
 * Run repeatedly with full AOT compilation; compare JSON timing and allocation medians.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class LookupAndReplyBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var textSink: String? = null
    @Volatile private var booleanSink = false

    private val filters = ContentFilterRepository(InMemoryKeyValueStore().apply {
        putString(ContentFilterKeys.WORDS, (0..<30).joinToString(",") { " keyword$it " })
        putString(ContentFilterKeys.DOMAINS, (0..<30).joinToString(",") { " domain$it.example " })
        putString(ContentFilterKeys.USERS, (0..<30).joinToString(",") { " User$it " })
    })
    private val tags10 = tags(10)
    private val tags100 = tags(100)
    private val shortReply = "<p>Thanks &amp; good point.</p>"
    private val longReply = "<p>Thanks for the <a href='https://example.com'>reference</a>.</p>" +
        "<p>The explanation contains enough detail to help clarify the original question.\n\t".repeat(5)

    @Before
    fun checkParity() {
        for (username in listOf(" USER0 ", "missing")) {
            check(legacyContainsUser(username) == filters.containsUser(username))
        }
        check(legacyTagFor(tags10, " USER9 ") == UserTagCodec.tagFor(tags10, " USER9 "))
        check(legacyTagFor(tags100, " USER99 ") == UserTagCodec.tagFor(tags100, " USER99 "))
        for (reply in listOf(shortReply, longReply)) {
            check(legacyReplyText(reply) == ReplyText.plainReplyText(reply))
        }
    }

    @Test fun blockedEarlyBefore() = benchmarkRule.measureRepeated {
        booleanSink = legacyContainsUser(" USER0 ")
    }

    @Test fun blockedEarlyOptimized() = benchmarkRule.measureRepeated {
        booleanSink = filters.containsUser(" USER0 ")
    }

    @Test fun blockedMissingBefore() = benchmarkRule.measureRepeated {
        booleanSink = legacyContainsUser("missing")
    }

    @Test fun blockedMissingOptimized() = benchmarkRule.measureRepeated {
        booleanSink = filters.containsUser("missing")
    }

    @Test fun tags10Before() = benchmarkRule.measureRepeated {
        textSink = legacyTagFor(tags10, " USER9 ")
    }

    @Test fun tags10Optimized() = benchmarkRule.measureRepeated {
        textSink = UserTagCodec.tagFor(tags10, " USER9 ")
    }

    @Test fun tags100Before() = benchmarkRule.measureRepeated {
        textSink = legacyTagFor(tags100, " USER99 ")
    }

    @Test fun tags100Optimized() = benchmarkRule.measureRepeated {
        textSink = UserTagCodec.tagFor(tags100, " USER99 ")
    }

    @Test fun replyShortBefore() = benchmarkRule.measureRepeated {
        textSink = legacyReplyText(shortReply)
    }

    @Test fun replyShortOptimized() = benchmarkRule.measureRepeated {
        textSink = ReplyText.plainReplyText(shortReply)
    }

    @Test fun replyLongBefore() = benchmarkRule.measureRepeated {
        textSink = legacyReplyText(longReply)
    }

    @Test fun replyLongOptimized() = benchmarkRule.measureRepeated {
        textSink = ReplyText.plainReplyText(longReply)
    }

    private fun legacyContainsUser(username: String?): Boolean {
        val normalized = username?.trim()?.takeIf(String::isNotEmpty)?.lowercase() ?: return false
        return normalized in filters.load().users
    }

    private fun legacyTagFor(serialized: String?, username: String?): String {
        val normalizedUsername = username?.trim()?.takeIf(String::isNotEmpty)?.lowercase() ?: return ""
        return UserTagCodec.decode(serialized, normalizeUsernames = true)[normalizedUsername].orEmpty()
    }

    private fun legacyReplyText(html: String?): String {
        if (html.isNullOrBlank()) return ReplyText.EMPTY_REPLY_TEXT
        val text = Ksoup.parse(html).text().replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return ReplyText.EMPTY_REPLY_TEXT
        return if (text.length > 240) text.take(237) + "..." else text
    }

    private fun tags(count: Int): String = JsonObject().apply {
        repeat(count) { put(" User$it ", "tag $it") }
    }.toString()
}
