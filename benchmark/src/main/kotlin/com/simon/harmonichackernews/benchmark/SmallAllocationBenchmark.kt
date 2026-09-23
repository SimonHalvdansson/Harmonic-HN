package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.HistoryLedger
import com.simon.harmonichackernews.data.StoryCacheIndex
import com.simon.harmonichackernews.network.LinkPreviewParsers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** One production operation per iteration; fixtures and expected results are built outside timing. */
@RunWith(AndroidJUnit4::class)
class SmallAllocationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null

    private val model12Tags = model(12)
    private val model48Tags = model(48)
    private val history100 = history(100)
    private val history10000 = history(10_000)
    private val cache50 = cache(50)
    private val cache500 = cache(500)

    @Test fun parseModel12Tags() = measureModel(model12Tags)
    @Test fun parseModel48Tags() = measureModel(model48Tags)
    @Test fun serializeHistory100() = measureHistory(history100)
    @Test fun serializeHistory10000() = measureHistory(history10000)
    @Test fun recordCache50() = measureCache(cache50)
    @Test fun recordCache500() = measureCache(cache500)

    private fun measureModel(response: String) {
        check(LinkPreviewParsers.parseHuggingFace(response).quantization == "4-bit")
        benchmarkRule.measureRepeated { result = LinkPreviewParsers.parseHuggingFace(response) }
    }

    private fun measureHistory(ledger: HistoryLedger) {
        val expected = ledger.load().joinToString("-") { "${it.id}q${it.created}" }
        check(ledger.serialize() == expected)
        benchmarkRule.measureRepeated { result = ledger.serialize() }
    }

    private fun measureCache(entries: Set<String>) {
        check(StoryCacheIndex.record(entries, 50_000_000, 1_800_000_000_000L, entries.size)
            .evictedStoryIds == listOf(40_000_000))
        benchmarkRule.measureRepeated {
            result = StoryCacheIndex.record(entries, 50_000_000, 1_800_000_000_000L, entries.size)
        }
    }

    private fun history(size: Int) = HistoryLedger().apply {
        repeat(size) { record(40_000_000 + it, 1_700_000_000_000L + it) }
    }

    private fun cache(size: Int): Set<String> = (0 until size).mapTo(linkedSetOf()) {
        "${40_000_000 + it}-${1_700_000_000_000L + it}"
    }

    private fun model(tagCount: Int): String {
        val tags = listOf("transformers", "safetensors", "text-generation", "en", "license:apache-2.0") +
            List(tagCount - 6) { "dataset:example/corpus-$it" } + "4-bit"
        return """{"id":"example/model","tags":[${tags.joinToString(",") { "\"$it\"" }}],"likes":120,"downloads":25000}"""
    }
}
