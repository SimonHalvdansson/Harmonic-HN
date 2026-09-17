package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.StoryTextProcessor
import com.simon.harmonichackernews.presentation.StoryPlaceholderFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production HTML preprocessing and feed placeholder operations. */
@RunWith(AndroidJUnit4::class)
class StoryProcessingBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()

    private val thirtyIds = (1..30).toList()
    private val fiveHundredIds = (1..500).toList()
    private val clickedIds = (1..500 step 5).toSet()
    private val allClickedIds = fiveHundredIds.toSet()
    private val commentIds = (1..500 step 7).toSet()
    private val retainedStories = fiveHundredIds.map { Story("Retained $it", it, true, false) }
    private val cachedStories = (1..450).associateWith { Story("Cached $it", it, true, false) }
    private val bareUrls = "Read https://example.com/article and http://second.example/path. " +
        "See https:&#x2F;&#47;third.example/path for more."
    private val existingAnchors = """<a href="https://example.com/article">An article</a><A HREF="https://second.example/path">https://second.example/path</A>"""
    private val mixedAnchors = """https://before.example/path <a href="https://example.com/article">An article</a> https://after.example/path."""
    @Volatile private var sink: Any? = null

    @Test fun bareUrls() = benchmarkRule.measureRepeated {
        sink = StoryTextProcessor.preprocessHtml(bareUrls)
    }

    @Test fun existingAnchors() = benchmarkRule.measureRepeated {
        sink = StoryTextProcessor.preprocessHtml(existingAnchors)
    }

    @Test fun mixedAnchors() = benchmarkRule.measureRepeated {
        sink = StoryTextProcessor.preprocessHtml(mixedAnchors)
    }

    @Test fun thirtyPlaceholders() = benchmarkRule.measureRepeated {
        sink = create(thirtyIds)
    }

    @Test fun fiveHundredPlaceholders() = benchmarkRule.measureRepeated {
        sink = create(fiveHundredIds)
    }

    @Test fun mostlyCachedPlaceholders() = benchmarkRule.measureRepeated {
        sink = create(fiveHundredIds, cachedStories)
    }

    @Test fun hiddenPlaceholders() = benchmarkRule.measureRepeated {
        sink = create(fiveHundredIds, cachedStories, allClickedIds, true)
    }

    @Test fun initialReconcile() = benchmarkRule.measureRepeated {
        sink = reconcile(emptyList())
    }

    @Test fun retainedReconcile() = benchmarkRule.measureRepeated {
        sink = reconcile(retainedStories)
    }

    private fun create(
        ids: List<Int>,
        cache: Map<Int, Story> = emptyMap(),
        clicked: Set<Int> = clickedIds,
        hidden: Boolean = false,
    ): MutableList<Story> = StoryPlaceholderFactory.create(
        itemIds = ids,
        commentIds = commentIds,
        clickedIds = clicked,
        hideClicked = hidden,
        cachedStories = cache,
    )

    private fun reconcile(existing: List<Story>): MutableList<Story> =
        StoryPlaceholderFactory.reconcile(
            existingStories = existing,
            itemIds = fiveHundredIds,
            commentIds = commentIds,
            clickedIds = clickedIds,
        )

}
