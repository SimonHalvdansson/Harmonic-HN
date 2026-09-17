@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.ui.content.CommentContentLruEntries
import com.simon.harmonichackernews.ui.content.CommentHtmlTextCache
import com.simon.harmonichackernews.ui.content.CommentRenderModelCache
import com.simon.harmonichackernews.ui.content.collapsedCommentPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One cache hit for a recent 24-comment window, an 8-entry cache, or an oldest-to-newest traversal
 * through a full cache. Hit fixtures are parsed and filled outside measurement; miss benchmarks
 * include real parsing, insertion and eviction while keeping fixture construction untimed.
 * These timings cover production cache operations, not a whole frame.
 */
@RunWith(AndroidJUnit4::class)
class CommentCacheBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null
    private val parsePreview: (String) -> String = { Ksoup.parse(it).text() }

    @Test fun renderCacheHit() = measureRenderCacheHit(192, 24)
    @Test fun renderCacheHitSmall() = measureRenderCacheHit(8, 8)
    @Test fun renderCacheHitForward() = measureRenderCacheHit(192, 192)

    private fun measureRenderCacheHit(entryCount: Int, window: Int) {
        val sources = sources(entryCount)
        CommentRenderModelCache.clearForTest()
        sources.forEachIndexed { index, source -> CommentRenderModelCache.get(index, source, false) }
        var index = sources.size - window
        benchmarkRule.measureRepeated {
            sink = CommentRenderModelCache.get(index, sources[index], false)
            if (++index == sources.size) index = sources.size - window
        }
    }

    @Test fun htmlTextCacheHit() = measureHtmlTextCacheHit(384, 24)
    @Test fun htmlTextCacheHitSmall() = measureHtmlTextCacheHit(8, 8)
    @Test fun htmlTextCacheHitForward() = measureHtmlTextCacheHit(384, 384)

    private fun measureHtmlTextCacheHit(entryCount: Int, window: Int) {
        val sources = sources(entryCount)
        CommentHtmlTextCache.clearForTest()
        sources.forEach { CommentHtmlTextCache.get(it) }
        var index = sources.size - window
        benchmarkRule.measureRepeated {
            sink = CommentHtmlTextCache.get(sources[index])
            if (++index == sources.size) index = sources.size - window
        }
    }

    @Test fun collapsedPreviewCacheHit() = measureCollapsedPreviewCacheHit(192, 24)
    @Test fun collapsedPreviewCacheHitSmall() = measureCollapsedPreviewCacheHit(8, 8)
    @Test fun collapsedPreviewCacheHitForward() = measureCollapsedPreviewCacheHit(192, 192)

    private fun measureCollapsedPreviewCacheHit(entryCount: Int, window: Int) {
        val sources = sources(entryCount)
        clearProductionPreviewCache()
        sources.forEachIndexed { index, source -> collapsedCommentPreview(index, source, true, parsePreview) }
        var index = sources.size - window
        benchmarkRule.measureRepeated {
            sink = collapsedCommentPreview(index, sources[index], true, parsePreview)
            if (++index == sources.size) index = sources.size - window
        }
    }

    // Capacity + 1 distinct comments, revisited in order, guarantee a miss and eviction each time.
    // HTML strings are built before timing; each cache performs its real production parsing inside it.
    @Test fun renderCacheMiss() {
        val sources = sources(193)
        CommentRenderModelCache.clearForTest()
        repeat(192) { CommentRenderModelCache.get(it, sources[it], true) }
        var index = 192
        benchmarkRule.measureRepeated {
            sink = CommentRenderModelCache.get(index, sources[index], true)
            if (++index == sources.size) index = 0
        }
    }

    @Test fun htmlTextCacheMiss() {
        val sources = sources(385)
        CommentHtmlTextCache.clearForTest()
        repeat(384) { CommentHtmlTextCache.get(sources[it]) }
        var index = 384
        benchmarkRule.measureRepeated {
            sink = CommentHtmlTextCache.get(sources[index])
            if (++index == sources.size) index = 0
        }
    }

    @Test fun htmlTextCacheMixed() = measureHtmlTextCacheMixed()

    // One newly encountered comment plus six revisits to nearby comments in a full cache.
    // Each timed operation includes all seven lookups and the new comment's real parsing.
    private fun measureHtmlTextCacheMixed() {
        CommentHtmlTextCache.clearForTest()
        val sources = sources(385)
        repeat(384) { CommentHtmlTextCache.get(sources[it]) }
        var next = 384
        benchmarkRule.measureRepeated {
            repeat(6) { offset ->
                sink = CommentHtmlTextCache.get(sources[(next - 1 - offset + sources.size) % sources.size])
            }
            sink = CommentHtmlTextCache.get(sources[next])
            if (++next == sources.size) next = 0
        }
    }

    @Test fun collapsedPreviewCacheMiss() {
        val sources = sources(193)
        clearProductionPreviewCache()
        repeat(192) { collapsedCommentPreview(it, sources[it], true, parsePreview) }
        var index = 192
        benchmarkRule.measureRepeated {
            sink = collapsedCommentPreview(index, sources[index], true, parsePreview)
            if (++index == sources.size) index = 0
        }
    }

    private fun clearProductionPreviewCache() {
        // This cache has no test hook. Reflection is confined to untimed fixture setup and avoids
        // widening production visibility just to ensure the 8-entry benchmark starts empty.
        val cache = Class.forName("com.simon.harmonichackernews.ui.content.CommentCollapsedPreviewCache")
        val entries = cache.getDeclaredField("entries").apply { isAccessible = true }
        (entries.get(null) as CommentContentLruEntries<*, *>).clear()
        cache.getDeclaredField("totalKeyChars").apply { isAccessible = true }.setInt(null, 0)
    }

    private fun sources(count: Int): List<String> = List(count) { index ->
        "<p>Comment $index describes the same discussion with a unique source revision.</p>" +
            "<p>A paragraph with <b>emphasis</b> and enough body text for a normal comment.</p>".repeat(6)
    }
}
