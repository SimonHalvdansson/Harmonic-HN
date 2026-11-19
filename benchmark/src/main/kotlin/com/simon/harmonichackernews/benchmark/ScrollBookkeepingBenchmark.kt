@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.ScrolledPastStoriesTracker
import com.simon.harmonichackernews.ui.content.CommentHtmlTextCache
import com.simon.harmonichackernews.ui.content.CommentRenderModelCache
import com.simon.harmonichackernews.ui.content.prefetchCommentRenderModels
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Owner-thread bookkeeping only; excludes rendering, network, and fixture preparation. */
@RunWith(AndroidJUnit4::class)
class ScrollBookkeepingBenchmark {
    @get:Rule val rule = BenchmarkRule()
    @Volatile private var sink: Any? = null

    @Test fun markScrolledRead500() = markScrolledRead(500)
    @Test fun markScrolledRead5000() = markScrolledRead(5_000)

    private fun markScrolledRead(count: Int) {
        val ids = List(count) { 1_000_000 + it }
        val tracker = ScrolledPastStoriesTracker()
        var first = count / 2
        tracker.update(ids, first, false)
        rule.measureRepeated {
            // Alternate backward/forward movement near the middle of a retained feed.
            first = if (first == count / 2) first + 1 else first - 1
            sink = tracker.update(ids, first, true)
        }
    }

    @Test fun cachedCommentPrefetchWindow() = runBlocking {
        val comments = List(32) { index ->
            Comment().apply {
                id = 1_000_000 + index
                text = "<p>Comment $index with <b>formatted</b> text.</p>" +
                    "<p>Read <a href='https://example.com/$index'>this reference</a>.</p>" +
                    "<p>A reply after the reference.</p>"
            }.let { PortableCommentItem(it.toSnapshot(), it.presentationSnapshot()) }
        }
        CommentRenderModelCache.clearForTest()
        CommentHtmlTextCache.clearForTest()
        prefetchCommentRenderModels(comments, collectLinks = true)
        rule.measureRepeated {
            // Already cached windows are checked again as the viewport moves by one row.
            // Includes the same coroutine entry overhead in both baseline and changed builds.
            runBlocking {
                prefetchCommentRenderModels(comments, collectLinks = true) {
                    error("The cached window must not parse HTML again")
                }
            }
        }
    }
}
