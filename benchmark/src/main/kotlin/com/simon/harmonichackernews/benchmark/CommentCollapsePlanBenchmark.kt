@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.CommentPresentationSnapshot
import com.simon.harmonichackernews.data.CommentSnapshot
import com.simon.harmonichackernews.presentation.PortableCommentItem
import com.simon.harmonichackernews.presentation.PortableVisibleComment
import com.simon.harmonichackernews.ui.comments.commentCollapsePlan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentCollapsePlanBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null

    @Test fun collapse700() = collapse(700)
    @Test fun collapse4000() = collapse(4_000)

    private fun collapse(count: Int) {
        val before = List(count) { index ->
            PortableVisibleComment(index + 1, PortableCommentItem(
                CommentSnapshot(index + 1),
                CommentPresentationSnapshot(expanded = true, depth = if (index % 20 == 0) 0 else 1),
            ), if (index % 20 == 0) 19 else 0)
        }
        val after = listOf(before.first().let {
            it.copy(comment = it.comment.copy(presentation = it.comment.presentation.copy(expanded = false)))
        }) + before.drop(20)
        val visible = (1..8).toSet()
        benchmarkRule.measureRepeated { result = commentCollapsePlan(before, after, visible) }
    }
}
