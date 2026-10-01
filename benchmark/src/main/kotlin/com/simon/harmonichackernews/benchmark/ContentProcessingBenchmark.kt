package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.StoryCacheIndex
import com.simon.harmonichackernews.network.HtmlDescriptionExtractor
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.utils.CommentSorter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** Local production operations; input construction and correctness checks are outside timing. */
@RunWith(AndroidJUnit4::class)
class ContentProcessingBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null
    private val paragraph = "A useful article describes the implementation, explains its tradeoffs, " +
        "and includes enough detail for readers to understand the results."
    private val document = Ksoup.parse("<main>" + "<p>$paragraph</p>".repeat(80) + "</main>")
    private val smallReferences = references(3)
    private val largeReferences = references(150)
    private val inlineProse = "<p>" + ("An ordinary sentence with a link to " +
        "<a href='https://example.com'>the source</a> and more prose. ").repeat(30) + "</p>"
    private val unorderedCache = (0 until 500).shuffled(Random(82)).mapTo(linkedSetOf()) {
        "${40_000_000 + it}-${1_700_000_000_000L + it}"
    }
    private val comments = MutableList(1001) { index ->
        Comment().apply {
            id = index
            depth = if (index == 0) -1 else (index - 1) % 10
            time = index
            sortOrder = index
        }
    }

    @Test fun descriptionMetadata() = benchmarkRule.measureRepeated {
        sink = HtmlDescriptionExtractor.chooseDescription(paragraph, document, "Article", "Fallback")
    }

    @Test fun descriptionFallback80() = benchmarkRule.measureRepeated {
        sink = HtmlDescriptionExtractor.chooseDescription(null, document, "Article", "Fallback")
    }

    @Test fun references3() = measureReferences(smallReferences, 3)
    @Test fun references150() = measureReferences(largeReferences, 150)

    @Test fun inlineLinkedProse() {
        check(!CollectedReferenceLinks.parse(inlineProse).hasLinks())
        benchmarkRule.measureRepeated { sink = CollectedReferenceLinks.parse(inlineProse) }
    }

    @Test fun allocateComments3767() = benchmarkRule.measureRepeated {
        sink = List(3767) { Comment() }
    }

    @Test fun recordUnorderedCache500() {
        check(StoryCacheIndex.record(unorderedCache, 50_000_000, 1_800_000_000_000L, 500)
            .evictedStoryIds == listOf(40_000_000))
        benchmarkRule.measureRepeated {
            sink = StoryCacheIndex.record(unorderedCache, 50_000_000, 1_800_000_000_000L, 500)
        }
    }

    @Test fun sortComments1000() = benchmarkRule.measureRepeated {
        // Repeated sorting is also exercised by changing the sort menu on a retained thread.
        CommentSorter.sort(comments, CommentSorter.REPLY_COUNT)
        sink = comments
    }

    private fun measureReferences(html: String, count: Int) {
        check(CollectedReferenceLinks.parse(html).links.size == count)
        benchmarkRule.measureRepeated { sink = CollectedReferenceLinks.parse(html) }
    }

    private fun references(count: Int) = buildString {
        append("<p>Useful sources for this discussion:</p>")
        repeat(count) {
            append("<p>[${it + 1}] <a href='https://example.com/$it'>Source $it</a></p>")
        }
    }
}
