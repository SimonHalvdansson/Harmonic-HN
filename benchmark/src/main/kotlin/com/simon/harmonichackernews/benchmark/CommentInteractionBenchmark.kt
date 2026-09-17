@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.CommentThreadStore
import com.simon.harmonichackernews.utils.CommentSorter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Full comment-store operations using nested threads with collapsed and delayed replies. */
@RunWith(AndroidJUnit4::class)
class CommentInteractionBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null

    @Test fun toggleComment10() = toggle(10)
    @Test fun toggleComment500() = toggle(500)
    @Test fun toggleFilteredComment500() = toggle(500, filtered = true)

    private fun toggle(count: Int, filtered: Boolean = false) {
        val store = CommentThreadStore().also {
            val story = story()
            it.reset(story)
            it.appendLoadedComments(story, comments(count), CommentSorter.DEFAULT, false)
            it.setHideDelayedComments(filtered)
        }
        benchmarkRule.measureRepeated {
            store.toggleExpanded(FIRST_ID)
            result = store.state.value
        }
    }

    @Test fun hideShowDelayed500() {
        val store = preparedStore(comments(500), story())
        var hide = false
        benchmarkRule.measureRepeated {
            hide = !hide
            store.setHideDelayedComments(hide)
            result = store.state.value
        }
    }

    @Test fun openComments500() {
        val fixture = comments(500)
        val story = story()
        benchmarkRule.measureRepeated {
            result = CommentThreadStore().also {
                it.reset(story)
                it.appendLoadedComments(story, fixture, CommentSorter.DEFAULT, false)
            }.state.value
        }
    }

    @Test fun preparedOpen500() {
        val fixture = comments(500)
        val story = story()
        benchmarkRule.measureRepeated {
            result = preparedStore(fixture, story).state.value
        }
    }

    @Test fun firstToggleAfterPreparedOpen500() {
        val story = story()
        benchmarkRule.measureRepeated {
            // Include any deferred visibility work in the first interaction after preparation.
            val store = runWithMeasurementDisabled { preparedStore(comments(500), story) }
            store.toggleExpanded(FIRST_ID)
            result = store.state.value
        }
    }

    private fun preparedStore(fixture: List<Comment>, story: Story) =
        CommentThreadStore().also {
            it.reset(story)
            val prepared = it.prepareInitialParsedComments(story, fixture, CommentSorter.DEFAULT, false)
            it.commitPreparedInitialComments(story, prepared)
        }

    private fun story() = Story("Interaction benchmark", 99, true, false).also { it.by = "op" }

    private fun comments(count: Int): List<Comment> = List(count) { index ->
        val groupIndex = index % 10
        val depth = if (groupIndex == 0) 0 else (groupIndex - 1) % 3 + 1
        Comment().also {
            it.id = FIRST_ID + index
            it.parent = when (depth) {
                0 -> -1
                1 -> FIRST_ID + index - groupIndex
                else -> FIRST_ID + index - 1
            }
            it.depth = depth
            it.expanded = groupIndex != 4
            it.by = if (index % 19 == 0) "op" else "reader"
            it.text = if (groupIndex == 1) " [delayed] " else "Comment $index with nested replies"
        }
    }

    private companion object { const val FIRST_ID = 1_000_000 }
}
