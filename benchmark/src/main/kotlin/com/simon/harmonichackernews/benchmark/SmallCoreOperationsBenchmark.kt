package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.TimestampedItem
import com.simon.harmonichackernews.utils.CollectedReferenceLinks.ReferenceLink
import com.simon.harmonichackernews.utils.HtmlTextUtils
import com.simon.harmonichackernews.utils.referenceLinkFallbackLabel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One production operation per iteration, with fixture construction outside measurement. */
@RunWith(AndroidJUnit4::class)
class SmallCoreOperationsBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: String? = null

    private val savedItems = List(500) { TimestampedItem(40_000_000 + it, 1_700_000_000_000L + it) }
    private val historyItems = List(10_000) { TimestampedItem(40_000_000 + it, 1_700_000_000_000L + it) }
    private val plainLabel = ReferenceLink(null, "https://example.com/article", "An article about Kotlin")
    private val spacedLabel = ReferenceLink(
        number = null,
        url = "https://example.com/article",
        label = "  An\n article\t about  Kotlin \r\n and performance  ",
    )
    private val namedAnchors = "<p>See <a href='https://example.com/article?a=1&amp;b=2'>the article</a> " +
        "and <a href='https://example.org/source'>the source</a> for details.</p>"
    private val mixedAnchors = "<p>See <a href='https://example.com/article'>the article</a> " +
        "and <a href='https://example.org/source'>https://example.org/...</a> for details.</p>"
    private val shortenedAnchors = "<p>See <a href='https://example.com/article'>https://example.com/...</a> " +
        "and <a href='https://example.org/source'>https://example.org/...</a> for details.</p>"

    @Test fun encodeSavedItems500() = benchmarkRule.measureRepeated {
        sink = SavedItemCodec.encode(savedItems)
    }

    @Test fun encodeHistory10000() = benchmarkRule.measureRepeated {
        sink = SavedItemCodec.encode(historyItems)
    }

    @Test fun referenceLabelPlain() = benchmarkRule.measureRepeated {
        sink = referenceLinkFallbackLabel(plainLabel)
    }

    @Test fun referenceLabelWhitespace() = benchmarkRule.measureRepeated {
        sink = referenceLinkFallbackLabel(spacedLabel)
    }

    @Test fun expandNamedAnchors() = benchmarkRule.measureRepeated {
        sink = HtmlTextUtils.expandShortenedAnchorText(namedAnchors)
    }

    @Test fun expandMixedAnchors() = benchmarkRule.measureRepeated {
        sink = HtmlTextUtils.expandShortenedAnchorText(mixedAnchors)
    }

    @Test fun expandShortenedAnchors() = benchmarkRule.measureRepeated {
        sink = HtmlTextUtils.expandShortenedAnchorText(shortenedAnchors)
    }
}
