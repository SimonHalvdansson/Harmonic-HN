package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.StoriesInteractionStore
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoriesInteractionStoreBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var store: StoriesInteractionStore

    @Before
    fun setUp() {
        val stories = (1..500).map(::story)
        store = StoriesInteractionStore(defaultStoryHeightPx = 100)
        store.updateContent(stories, emptyList(), searching = false, lastSearch = "")
        for (storyId in 1..500 step 5) {
            store.updateStoryItemHeight(storyId, 80 + storyId % 40)
        }
    }

    @Test
    fun pagingDistanceBetweenAdjacentStories() = benchmarkRule.measureRepeated {
        check(store.getAdjacentStoryPagingDistance(250) > 0)
    }

    private fun story(id: Int) = StoryListItemSnapshot(
        story = StorySnapshot(id = id, title = "Story $id"),
        presentation = StoryPresentationSnapshot(loaded = true),
    )
}
