package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.CommentThreadLoadResult
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.LinkSummary
import com.simon.harmonichackernews.network.OfficialCommentThreadLoader
import com.simon.harmonichackernews.network.PreviewContentCache
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** CPU/allocation costs with deterministic storage and transport, excluding network latency. */
@RunWith(AndroidJUnit4::class)
class CacheAndCommentTreeBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null

    @Test fun previewImageHit1000() {
        val store = InMemoryKeyValueStore()
        val cache = PreviewContentCache()
        repeat(1000) { cache.savePreviewImage(store, "$it", "https://example.com/$it.png") }
        var index = 0
        benchmarkRule.measureRepeated {
            result = cache.loadPreviewImage(store, "${index++ % 1000}")
        }
    }

    @Test fun summaryWorkingSetAcrossEviction() {
        val store = InMemoryKeyValueStore()
        val cache = PreviewContentCache(stableHash = { it })
        repeat(600) { cache.saveLinkSummary(store, "page$it", LinkSummary(title = "Title $it")) }
        var cold = 0
        // A frequently revisited set, interrupted by new entries: clearing the entire cache
        // discards the hot set; individual eviction can retain it.
        benchmarkRule.measureRepeated {
            repeat(50) { result = cache.loadLinkSummary(store, "page${550 + it}") }
            result = cache.loadLinkSummary(store, "page${cold++ % 500}")
        }
    }

    @Test fun officialWide1000() = forest(width = 1000, depth = 1)
    @Test fun officialNested1000() = forest(width = 20, depth = 50)
    @Test fun officialDeep500() = forest(width = 1, depth = 500)

    private fun forest(width: Int, depth: Int) {
        val repository = object : HackerNewsRepository {
            override suspend fun getStoryIds(type: StoryType): List<Int> = error("Unused")
            override suspend fun getStory(id: Int) = Story().also {
                it.id = id
                it.kids = IntArray(width) { branch -> branch * depth + 1 }
            }
            override suspend fun getComment(id: Int) = Comment().also {
                it.id = id
                it.by = "reader"
                it.text = "Comment $id"
                it.kidsIds = if (id % depth == 0) intArrayOf() else intArrayOf(id + 1)
            }
        }
        val loader = OfficialCommentThreadLoader(repository)
        benchmarkRule.measureRepeated {
            result = runBlocking { loader.load(99999, emptySet(), false) }
        }
        check((result as CommentThreadLoadResult.Official).comments.size == width * depth)
    }
}
