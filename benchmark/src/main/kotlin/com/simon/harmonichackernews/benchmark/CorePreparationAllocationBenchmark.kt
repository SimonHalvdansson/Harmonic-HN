package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.categorization.ArticleEmbeddingInput
import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.data.HistoryLedger
import com.simon.harmonichackernews.data.SavedItemCodec
import com.simon.harmonichackernews.data.StoryCacheIndex
import com.simon.harmonichackernews.data.TimestampedItem
import com.simon.harmonichackernews.network.HtmlDescriptionExtractor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Paired algorithms in the same APK/device run; fixture creation is outside measurement. */
@RunWith(AndroidJUnit4::class)
class CorePreparationAllocationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null
    private val titles = List(30) { "  Kotlin\tand  memory performance: Article $it  " }
    private val descriptions = List(30) {
        "An article about  programming languages,\ncompilers and memory management. " +
            "Exploring practical  optimization techniques with\tmeasurement $it."
    }
    private val history10000 = SavedItemCodec.encode(List(10_000) {
        TimestampedItem(40_000_000 + it, 1_700_000_000_000L + it)
    })
    private val cache500 = (0 until 500).mapTo(linkedSetOf()) {
        "${40_000_000 + it}-${1_700_000_000_000L + it}"
    }

    @Test fun categoryInput30Before() {
        benchmarkRule.measureRepeated {
            for (index in titles.indices) result = categoryInputBefore(titles[index], descriptions[index])
        }
    }

    @Test fun categoryInput30After() {
        for (index in titles.indices) {
            check(ArticleEmbeddingInput.format(titles[index], descriptions[index]) ==
                categoryInputBefore(titles[index], descriptions[index]))
        }
        benchmarkRule.measureRepeated {
            for (index in titles.indices) result = ArticleEmbeddingInput.format(titles[index], descriptions[index])
        }
    }

    @Test fun historyDecode10000Before() {
        check(decodeHistoryBefore().size == 10_000)
        benchmarkRule.measureRepeated { result = decodeHistoryBefore() }
    }

    @Test fun historyDecode10000After() {
        check(HistoryLedger.decodeHistories(history10000, sorted = true) == decodeHistoryBefore())
        benchmarkRule.measureRepeated { result = HistoryLedger.decodeHistories(history10000, sorted = true) }
    }

    @Test fun cacheRemove500Before() {
        check(removeCacheBefore().size == 499)
        benchmarkRule.measureRepeated { result = removeCacheBefore() }
    }

    @Test fun cacheRemove500After() {
        check(StoryCacheIndex.remove(cache500, 40_000_250).toList() == removeCacheBefore().toList())
        benchmarkRule.measureRepeated { result = StoryCacheIndex.remove(cache500, 40_000_250) }
    }

    private fun categoryInputBefore(title: String, description: String?): String {
        val headline = title.replace(Regex("\\s+"), " ").trim().take(300)
        val details = description.orEmpty().replace(Regex("\\s+"), " ").trim().take(600)
            .takeIf { HtmlDescriptionExtractor.isMeaningful(it, headline, headline) }.orEmpty()
        return "title: $headline | text: ${details.ifBlank { headline }}"
    }

    // These are the production algorithms preceding this change. Parsing stays shared so both
    // measurements exercise exactly the same validation and numeric conversion code.
    private fun decodeHistoryBefore(): MutableList<History> =
        SavedItemCodec.decode(history10000, sortedByCreated = true)
            .mapTo(mutableListOf()) { History(it.id, it.created) }

    private fun removeCacheBefore(): Set<String> =
        StoryCacheIndex.entries(cache500)
            .filterNot { it.storyId == 40_000_250 }
            .mapTo(linkedSetOf()) { "${it.storyId}-${it.cachedAtMillis}" }
}
