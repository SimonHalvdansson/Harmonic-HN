package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.StoryCacheIndex
import com.simon.harmonichackernews.network.LinkPreviewParsers
import com.simon.harmonichackernews.network.StackExchangeRequest
import com.simon.harmonichackernews.settings.UserAvatarOptions
import com.simon.harmonichackernews.settings.UserAvatarStyle
import com.simon.harmonichackernews.utils.CommentSorter
import com.simon.harmonichackernews.utils.StorySearchRanking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** Production operations; fixtures, list resets and correctness checks are outside measurement. */
@RunWith(AndroidJUnit4::class)
class ReadingAllocationBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null
    private val search30 = stories(30)
    private val search500 = stories(500)
    private val comments = List(1001) { index ->
        Comment().apply {
            id = 40_000_000 + index
            depth = if (index == 0) -1 else if ((index - 1) % 10 == 0) 0 else 1
            time = 1_700_000_000 + (index * 719) % 10_000
            sortOrder = index
        }
    }
    private val authors = List(30) { "reader_$it" }
    private val defaultAvatars = UserAvatarOptions()
    private val mixedAvatars = UserAvatarOptions(setOf(
        UserAvatarStyle.MOSAIC, UserAvatarStyle.ORBITAL, UserAvatarStyle.ROBOT,
    ))
    private val cache500 = (0 until 500).mapTo(linkedSetOf()) {
        "${40_000_000 + it}-${1_700_000_000_000L + it}"
    }
    private val question = """{"items":[{"title":"Kotlin collections &amp; sorting","body":"<p>How can I sort  a collection?</p><pre>val items = listOf(1, 2, 3)</pre><p>Preserve the order.<br>Thanks!</p>","score":42,"answer_count":3,"view_count":12000,"is_answered":true,"owner":{"display_name":"A reader"},"tags":["kotlin","sorting"]}]}"""
    private val questionRequest = StackExchangeRequest("stackoverflow", "123", false)

    @Test fun search30() = measureSearch(search30)
    @Test fun search500() = measureSearch(search500)

    private fun measureSearch(input: List<Story>) {
        val working = input.toMutableList()
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                input.forEachIndexed { index, story -> working[index] = story }
            }
            StorySearchRanking.sortStoriesByRelevance(working, "kotlin")
            sink = working
        }
    }

    @Test fun newestComments1000() {
        val working = comments.toMutableList()
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                comments.forEachIndexed { index, comment -> working[index] = comment }
            }
            CommentSorter.sort(working, CommentSorter.NEWEST_FIRST)
            sink = working
        }
        check(working.first() === comments.first())
        check(working.size == comments.size)
    }

    @Test fun defaultAvatarStyles30() = measureAvatars(defaultAvatars)
    @Test fun mixedAvatarStyles30() = measureAvatars(mixedAvatars)

    private fun measureAvatars(options: UserAvatarOptions) = benchmarkRule.measureRepeated {
        for (author in authors) sink = options.styleFor(author)
    }

    @Test fun cacheStoryIds500() {
        check(StoryCacheIndex.storyIds(cache500).size == 500)
        benchmarkRule.measureRepeated { sink = StoryCacheIndex.storyIds(cache500) }
    }

    @Test fun stackExchangePreview() {
        check(LinkPreviewParsers.parseStackExchange(question, questionRequest)?.title ==
            "Kotlin collections & sorting")
        benchmarkRule.measureRepeated {
            sink = LinkPreviewParsers.parseStackExchange(question, questionRequest)
        }
    }

    private fun stories(count: Int): List<Story> = List(count) { index ->
        Story().apply {
            id = 40_000_000 + index
            title = when (index % 5) {
                0 -> "Kotlin"
                1 -> "Kotlin memory and performance"
                2 -> "Learning Kotlin with examples"
                3 -> "A Kotlin-inspired language"
                else -> "A different programming language"
            }
            score = 100 + (index * 719) % 500
            descendants = (index * 31) % 300
            createdAtEpochSeconds = 1_700_000_000 + index
        }
    }.shuffled(Random(82))
}
