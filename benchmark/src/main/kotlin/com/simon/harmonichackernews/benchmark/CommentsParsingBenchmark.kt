package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import com.simon.harmonichackernews.network.JSONParser
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Measures CPU/allocation cost with fixture I/O excluded and production dispatcher behavior. */
@RunWith(AndroidJUnit4::class)
class CommentsParsingBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val parser = AlgoliaCommentsParser()

    @Test fun parseMedium() = measure("medium", compact = false, parse = true)
    @Test fun parseLarge() = measure("large", compact = false, parse = true)
    @Test fun compactMedium() = measure("medium", compact = true, parse = false)
    @Test fun compactLarge() = measure("large", compact = true, parse = false)
    @Test fun parseAndCompactMedium() = measure("medium", compact = true, parse = true)
    @Test fun parseAndCompactLarge() = measure("large", compact = true, parse = true)

    private fun measure(size: String, compact: Boolean, parse: Boolean) {
        val response = InstrumentationRegistry.getInstrumentation().context
            .createPackageContext(BenchmarkPackageName, 0).assets
            .open("comments_benchmark_fixture_$size.json").bufferedReader().use { it.readText() }
        benchmarkRule.measureRepeated {
            if (parse) {
                val parsed = runBlocking { parser.parse(response) }
                check(parsed.comments.isNotEmpty())
                if (compact) check(parsed.cacheSummary?.encode(1) != null)
            }
            if (compact && !parse) check(JSONParser.compactAlgoliaStoryResponse(response, 1) != null)
        }
    }
}
