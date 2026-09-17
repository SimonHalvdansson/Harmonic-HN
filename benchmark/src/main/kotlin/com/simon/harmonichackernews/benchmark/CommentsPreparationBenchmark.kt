package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import com.simon.harmonichackernews.presentation.CommentThreadStore
import com.simon.harmonichackernews.utils.CommentSorter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Measures initial snapshot preparation: fresh comments on every iteration preserve cold text caches. */
@RunWith(AndroidJUnit4::class)
class CommentsPreparationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    private val parser = AlgoliaCommentsParser()
    private fun fixture() = InstrumentationRegistry.getInstrumentation().context
        .createPackageContext(BenchmarkPackageName, 0).assets
        .open("comments_benchmark_fixture_large.json").bufferedReader().use { it.readText() }

    @Test fun parseAndPrepareLarge() {
        val response = fixture()
        benchmarkRule.measureRepeated {
            runBlocking(Dispatchers.Default) {
                val parsed = parser.parse(response)
                val story = Story()
                parsed.updateStoryInformation(story, 0)
                val store = CommentThreadStore()
                store.reset(story)
                store.replaceParsedComments(story, parsed.comments, CommentSorter.DEFAULT, false)
                check(store.state.value.allComments.size == 3768)
            }
        }
    }

    @Test fun prepareLarge() {
        val response = fixture()
        benchmarkRule.measureRepeated {
            val parsed = runWithTimingDisabled { runBlocking { parser.parse(response) } }
            runBlocking(Dispatchers.Default) {
                val story = Story()
                parsed.updateStoryInformation(story, 0)
                val store = CommentThreadStore()
                store.reset(story)
                store.replaceParsedComments(story, parsed.comments, CommentSorter.DEFAULT, false)
                check(store.state.value.allComments.size == 3768)
            }
        }
    }
}

