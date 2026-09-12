@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.ui.content.CommentContentLruEntries
import com.simon.harmonichackernews.ui.content.CommentHtmlTextCache
import com.simon.harmonichackernews.ui.content.CommentRenderModel
import com.simon.harmonichackernews.ui.content.CommentRenderModelCache
import com.simon.harmonichackernews.ui.content.collapsedCommentPreview
import com.simon.harmonichackernews.ui.content.prepareCommentHtml
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.min

/**
 * One cache hit for a recent 24-comment window, an 8-entry cache, or an oldest-to-newest traversal
 * through a full cache. Hit fixtures are parsed and filled outside measurement; miss benchmarks
 * include real parsing, insertion and eviction while keeping fixture construction untimed.
 * Before copies freeze the original deque-based LRU; after calls production caches. These timings
 * cover lookup/recency work, not a whole frame.
 */
@RunWith(AndroidJUnit4::class)
class CommentScrollMicroOptimizationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null
    private val parsePreview: (String) -> String = { Ksoup.parse(it).text() }

    @Test fun renderCacheHitBefore() = measureRenderCacheHitBefore(192, 24)
    @Test fun renderCacheHitSmallBefore() = measureRenderCacheHitBefore(8, 8)
    @Test fun renderCacheHitForwardBefore() = measureRenderCacheHitBefore(192, 192)

    private fun measureRenderCacheHitBefore(entryCount: Int, window: Int) {
        val cache = BeforeRenderCache()
        val sources = sources(entryCount)
        sources.forEachIndexed { index, source -> cache.get(index, source, false) }
        var index = sources.size - window
        benchmarkRule.measureRepeated {
            sink = cache.get(index, sources[index], false)
            if (++index == sources.size) index = sources.size - window
        }
    }

    @Test fun renderCacheHitAfter() = measureRenderCacheHitAfter(192, 24)
    @Test fun renderCacheHitSmallAfter() = measureRenderCacheHitAfter(8, 8)
    @Test fun renderCacheHitForwardAfter() = measureRenderCacheHitAfter(192, 192)

    private fun measureRenderCacheHitAfter(entryCount: Int, window: Int) {
        val sources = sources(entryCount)
        CommentRenderModelCache.clearForTest()
        sources.forEachIndexed { index, source -> CommentRenderModelCache.get(index, source, false) }
        var index = sources.size - window
        benchmarkRule.measureRepeated {
            sink = CommentRenderModelCache.get(index, sources[index], false)
            if (++index == sources.size) index = sources.size - window
        }
    }

    @Test fun htmlTextCacheHitBefore() = measureHtmlTextCacheHitBefore(384, 24)
    @Test fun htmlTextCacheHitSmallBefore() = measureHtmlTextCacheHitBefore(8, 8)
    @Test fun htmlTextCacheHitForwardBefore() = measureHtmlTextCacheHitBefore(384, 384)

    private fun measureHtmlTextCacheHitBefore(entryCount: Int, window: Int) {
        val cache = BeforeHtmlCache()
        val sources = sources(entryCount)
        sources.forEach { cache.get(it) }
        var index = sources.size - window
        benchmarkRule.measureRepeated {
            sink = cache.get(sources[index])
            if (++index == sources.size) index = sources.size - window
        }
    }

    @Test fun htmlTextCacheHitAfter() = measureHtmlTextCacheHitAfter(384, 24)
    @Test fun htmlTextCacheHitSmallAfter() = measureHtmlTextCacheHitAfter(8, 8)
    @Test fun htmlTextCacheHitForwardAfter() = measureHtmlTextCacheHitAfter(384, 384)

    private fun measureHtmlTextCacheHitAfter(entryCount: Int, window: Int) {
        val sources = sources(entryCount)
        CommentHtmlTextCache.clearForTest()
        sources.forEach { CommentHtmlTextCache.get(it) }
        var index = sources.size - window
        benchmarkRule.measureRepeated {
            sink = CommentHtmlTextCache.get(sources[index])
            if (++index == sources.size) index = sources.size - window
        }
    }

    @Test fun collapsedPreviewCacheHitBefore() = measureCollapsedPreviewCacheHitBefore(192, 24)
    @Test fun collapsedPreviewCacheHitSmallBefore() = measureCollapsedPreviewCacheHitBefore(8, 8)
    @Test fun collapsedPreviewCacheHitForwardBefore() = measureCollapsedPreviewCacheHitBefore(192, 192)

    private fun measureCollapsedPreviewCacheHitBefore(entryCount: Int, window: Int) {
        val cache = BeforePreviewCache()
        val sources = sources(entryCount)
        sources.forEachIndexed { index, source -> cache.get(index, source, parsePreview) }
        var index = sources.size - window
        benchmarkRule.measureRepeated {
            sink = cache.get(index, sources[index], parsePreview)
            if (++index == sources.size) index = sources.size - window
        }
    }

    @Test fun collapsedPreviewCacheHitAfter() = measureCollapsedPreviewCacheHitAfter(192, 24)
    @Test fun collapsedPreviewCacheHitSmallAfter() = measureCollapsedPreviewCacheHitAfter(8, 8)
    @Test fun collapsedPreviewCacheHitForwardAfter() = measureCollapsedPreviewCacheHitAfter(192, 192)

    private fun measureCollapsedPreviewCacheHitAfter(entryCount: Int, window: Int) {
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
    @Test fun renderCacheMissBefore() {
        val cache = BeforeRenderCache()
        val sources = sources(193)
        repeat(192) { cache.get(it, sources[it], true) }
        var index = 192
        benchmarkRule.measureRepeated {
            sink = cache.get(index, sources[index], true)
            if (++index == sources.size) index = 0
        }
    }

    @Test fun renderCacheMissAfter() {
        val sources = sources(193)
        CommentRenderModelCache.clearForTest()
        repeat(192) { CommentRenderModelCache.get(it, sources[it], true) }
        var index = 192
        benchmarkRule.measureRepeated {
            sink = CommentRenderModelCache.get(index, sources[index], true)
            if (++index == sources.size) index = 0
        }
    }

    @Test fun htmlTextCacheMissBefore() {
        val cache = BeforeHtmlCache()
        val sources = sources(385)
        repeat(384) { cache.get(sources[it]) }
        var index = 384
        benchmarkRule.measureRepeated {
            sink = cache.get(sources[index])
            if (++index == sources.size) index = 0
        }
    }

    @Test fun htmlTextCacheMissAfter() {
        val sources = sources(385)
        CommentHtmlTextCache.clearForTest()
        repeat(384) { CommentHtmlTextCache.get(sources[it]) }
        var index = 384
        benchmarkRule.measureRepeated {
            sink = CommentHtmlTextCache.get(sources[index])
            if (++index == sources.size) index = 0
        }
    }

    @Test fun htmlTextCacheMixedBefore() = measureHtmlTextCacheMixed(before = true)
    @Test fun htmlTextCacheMixedAfter() = measureHtmlTextCacheMixed(before = false)

    // One newly encountered comment plus six revisits to nearby comments in a full cache.
    // Each timed operation includes all seven lookups and the new comment's real parsing.
    private fun measureHtmlTextCacheMixed(before: Boolean) {
        val original = BeforeHtmlCache()
        CommentHtmlTextCache.clearForTest()
        val get: (String) -> AnnotatedString = if (before) {
            { original.get(it) }
        } else {
            { CommentHtmlTextCache.get(it) }
        }
        val sources = sources(385)
        repeat(384) { get(sources[it]) }
        var next = 384
        benchmarkRule.measureRepeated {
            repeat(6) { offset ->
                sink = get(sources[(next - 1 - offset + sources.size) % sources.size])
            }
            sink = get(sources[next])
            if (++next == sources.size) next = 0
        }
    }

    @Test fun collapsedPreviewCacheMissBefore() {
        val cache = BeforePreviewCache()
        val sources = sources(193)
        repeat(192) { cache.get(it, sources[it], parsePreview) }
        var index = 192
        benchmarkRule.measureRepeated {
            sink = cache.get(index, sources[index], parsePreview)
            if (++index == sources.size) index = 0
        }
    }

    @Test fun collapsedPreviewCacheMissAfter() {
        val sources = sources(193)
        clearProductionPreviewCache()
        repeat(192) { collapsedCommentPreview(it, sources[it], true, parsePreview) }
        var index = 192
        benchmarkRule.measureRepeated {
            sink = collapsedCommentPreview(index, sources[index], true, parsePreview)
            if (++index == sources.size) index = 0
        }
    }

    @Test fun cachedContentMatchesBeforeAcrossRevisionsAndEvictions() {
        val beforeRender = BeforeRenderCache()
        val beforeHtml = BeforeHtmlCache()
        val beforePreview = BeforePreviewCache()
        val sources = sources(420)
        CommentRenderModelCache.clearForTest()
        CommentHtmlTextCache.clearForTest()
        for (iteration in 0 until 1_200) {
            val id = (iteration * 73) % sources.size
            val source = sources[id] + if (iteration % 7 == 0) "<p>Revision</p>" else ""
            val before: CommentRenderModel = beforeRender.get(id, source, false)
            val after: CommentRenderModel = CommentRenderModelCache.get(id, source, false)
            assertEquals(before.contentBlocks.map { it.bodyHtml }, after.contentBlocks.map { it.bodyHtml })
            assertEquals(beforeHtml.get(source), CommentHtmlTextCache.get(source))
            assertEquals(beforePreview.get(id, source, parsePreview), collapsedCommentPreview(id, source, true, parsePreview))
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

    // Frozen pre-optimization hit paths. Parsing delegates to the unchanged production
    // preparation functions; keys, cache limits and eviction policies match the original caches.
    private class BeforeRenderCache {
        private val entries = mutableMapOf<Key, CommentRenderModel>()
        private val order = ArrayDeque<Key>()
        private var totalWeightedChars = 0

        fun get(commentId: Int, expandedHtml: String?, collectLinks: Boolean): CommentRenderModel {
            val source = expandedHtml.orEmpty()
            val key = Key(commentId, source, collectLinks)
            entries[key]?.let { cached ->
                order.remove(key)
                order.addLast(key)
                return cached
            }
            val model = CommentRenderModelCache.prepare(expandedHtml, collectLinks)
            install(commentId, source, collectLinks, model)
            return model
        }

        private fun install(commentId: Int, source: String, collectLinks: Boolean, model: CommentRenderModel) {
            val key = Key(commentId, source, collectLinks)
            if (key in entries) return
            entries.keys.filter { it.commentId == commentId && it.source != source }.forEach(::remove)
            if (source.length <= 64 * 1024) {
                val weight = key.source.length * 2
                while (order.isNotEmpty() && (order.size >= 192 || totalWeightedChars + weight > 1024 * 1024)) {
                    remove(order.first())
                }
                entries[key] = model
                order.addLast(key)
                totalWeightedChars += weight
            }
        }

        private fun remove(key: Key) {
            if (entries.remove(key) != null) totalWeightedChars -= key.source.length * 2
            order.remove(key)
        }

        private data class Key(val commentId: Int, val source: String, val collectLinks: Boolean)
    }

    private class BeforeHtmlCache {
        private val entries = mutableMapOf<String, AnnotatedString>()
        private val order = ArrayDeque<String>()
        private var totalWeightedChars = 0

        fun get(html: String): AnnotatedString {
            entries[html]?.let { cached ->
                order.remove(html)
                order.addLast(html)
                return cached
            }
            val text = prepareCommentHtml(html)
            install(html, text)
            return text
        }

        private fun install(html: String, text: AnnotatedString) {
            if (html in entries || html.length > 64 * 1024) return
            val weight = min(Int.MAX_VALUE / 2, html.length * 3)
            while (order.isNotEmpty() && (order.size >= 384 || totalWeightedChars + weight > 2 * 1024 * 1024)) {
                remove(order.first())
            }
            entries[html] = text
            order.addLast(html)
            totalWeightedChars += weight
        }

        private fun remove(html: String) {
            if (entries.remove(html) != null) totalWeightedChars -= min(Int.MAX_VALUE / 2, html.length * 3)
            order.remove(html)
        }
    }

    private class BeforePreviewCache {
        private val entries = mutableMapOf<PreviewKey, String>()
        private val order = ArrayDeque<PreviewKey>()
        private var totalKeyChars = 0

        fun get(commentId: Int, rawHtml: String, parse: (String) -> String): String {
            val sourcePrefix = rawHtml.take(240)
            val key = PreviewKey(commentId, sourcePrefix)
            entries[key]?.let { cached ->
                order.remove(key)
                order.addLast(key)
                return cached
            }
            entries.keys.filter { it.commentId == commentId }.forEach(::remove)
            val preview = parse(sourcePrefix).replace('\n', ' ').take(120)
            while (order.isNotEmpty() && (order.size >= 192 || totalKeyChars + sourcePrefix.length > 48 * 1024)) {
                remove(order.first())
            }
            entries[key] = preview
            order.addLast(key)
            totalKeyChars += sourcePrefix.length
            return preview
        }

        private fun remove(key: PreviewKey) {
            if (entries.remove(key) != null) totalKeyChars -= key.sourcePrefix.length
            order.remove(key)
        }

        private data class PreviewKey(val commentId: Int, val sourcePrefix: String)
    }
}
