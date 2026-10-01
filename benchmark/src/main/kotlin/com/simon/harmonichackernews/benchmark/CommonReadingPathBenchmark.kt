package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.presentationSnapshot
import com.simon.harmonichackernews.data.toSnapshot
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import com.simon.harmonichackernews.presentation.StoryListStore
import com.simon.harmonichackernews.presentation.FrontPageDayState
import com.simon.harmonichackernews.presentation.StoriesInteractionStore
import com.simon.harmonichackernews.presentation.sameStoryIds
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Default feed operations and uncached discussion preparation, without network variability. */
@RunWith(AndroidJUnit4::class)
class CommonReadingPathBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null
    private val stories = List(500) { index ->
        Story("Story $index", 40_000_000 + index, true, false).apply {
            by = "reader"
            url = "https://example.com/story/$index"
            score = index + 20
            descendants = 8
            kids = IntArray(8) { id + it + 1 }
            isLink = true
        }
    }

    @Test fun presentationSnapshots30() = benchmarkRule.measureRepeated {
        for (index in 0 until 30) sink = stories[index].presentationSnapshot()
    }

    @Test fun unchangedFrontDateLabel() {
        val day = FrontPageDayState(-1L, 1_700_000_000_000L)
        benchmarkRule.measureRepeated { sink = day.requestParameter }
    }

    @Test fun snapshotStory64Children() {
        val story = Story().apply { id = 1; kids = IntArray(64) { 40_000_000 + it } }
        benchmarkRule.measureRepeated { sink = story.toSnapshot() }
    }

    @Test fun replaceFeed500() {
        val store = StoryListStore()
        benchmarkRule.measureRepeated {
            store.replace(stories)
            sink = store.state.value
        }
    }

    @Test fun updateOneStoryIn500() {
        val store = populatedStore()
        val story = stories[15]
        benchmarkRule.measureRepeated {
            story.score++
            store.contentChanged(story)
            sink = store.state.value
        }
    }

    @Test fun updateStoryAndReconcile500() {
        val store = populatedStore()
        val interactions = StoriesInteractionStore(100)
        interactions.updateContent(store.state.value.items, emptyList(), false, "")
        val story = stories[15]
        benchmarkRule.measureRepeated {
            val previous = store.state.value.items
            story.score++
            store.contentChanged(story)
            val next = store.state.value.items
            check(sameStoryIds(previous, next))
            interactions.updateContent(next, emptyList(), false, "")
            sink = interactions.state
        }
    }

    @Test fun freshCommentsSmall() = prepareComments("comments_benchmark_fixture.json")
    @Test fun freshCommentsMedium() = prepareComments("comments_benchmark_fixture_medium.json")
    @Test fun freshCommentsLarge() = prepareComments("comments_benchmark_fixture_large.json")

    private fun populatedStore() = StoryListStore().also { store ->
        store.replace(stories)
        check(store.state.value.items.size == 500)
    }

    private fun prepareComments(asset: String) {
        val context = InstrumentationRegistry.getInstrumentation().context
            .createPackageContext(BenchmarkPackageName, 0)
        val raw = context.assets.open(asset).bufferedReader().use { it.readText() }
        val parser = AlgoliaCommentsParser()
        benchmarkRule.measureRepeated {
            sink = runBlocking { parser.parseForDisplay(raw) }
        }
    }
}
