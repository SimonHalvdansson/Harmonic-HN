package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.presentation.StoryVisibilityConfig
import com.simon.harmonichackernews.presentation.StoryVisibilityPolicy
import com.simon.harmonichackernews.settings.DomainFilterRule
import com.simon.harmonichackernews.settings.SettingsSearchEntry
import com.simon.harmonichackernews.settings.SettingsSearchIndex
import com.simon.harmonichackernews.settings.TitleFilterMatchMode
import com.simon.harmonichackernews.settings.TitleFilterRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real production APIs; fixture construction and result checks are outside measurement. */
@RunWith(AndroidJUnit4::class)
class CoreFilteringPerformanceBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var result: Any? = null
    @Volatile private var count = 0

    private val smallEntries = settingsEntries(30)
    private val fullEntries = settingsEntries(180)
    private val fullIndex = SettingsSearchIndex(fullEntries)
    private val domainRule = DomainFilterRule("example.com")
    private val smallHosts = hosts(30)
    private val largeHosts = hosts(500)
    private val smallStories = stories(30)
    private val largeStories = stories(500)
    private val smallRules = titleRules(3)
    private val largeRules = titleRules(20)
    private val smallPolicy = StoryVisibilityPolicy(StoryVisibilityConfig(titleRules = smallRules))
    private val largePolicy = StoryVisibilityPolicy(StoryVisibilityConfig(titleRules = largeRules))

    @Test fun settingsIndex30() = measureIndex(smallEntries)
    @Test fun settingsIndex180() = measureIndex(fullEntries)

    @Test fun settingsQuery180() {
        check(fullIndex.search("  COMMENTS--Text\nSIZE ") == fullIndex.search("comments text size"))
        check(fullIndex.search("comments text size").isNotEmpty())
        benchmarkRule.measureRepeated { result = fullIndex.search("  COMMENTS--Text\nSIZE ") }
    }

    @Test fun domainHosts30() = measureHosts(smallHosts)
    @Test fun domainHosts500() = measureHosts(largeHosts)
    @Test fun titleVisibility30Stories3Rules() = measureVisibility(smallStories, smallRules, smallPolicy)
    @Test fun titleVisibility500Stories20Rules() = measureVisibility(largeStories, largeRules, largePolicy)

    private fun measureIndex(entries: List<SettingsSearchEntry>) {
        check(SettingsSearchIndex(entries).search("comments text size").isNotEmpty())
        benchmarkRule.measureRepeated { result = SettingsSearchIndex(entries) }
    }

    private fun measureHosts(hosts: List<String>) {
        check(hosts.count(domainRule::matchesHost) == hosts.count { it == "example.com" || it.endsWith(".example.com") })
        benchmarkRule.measureRepeated {
            var matches = 0
            for (host in hosts) if (domainRule.matchesHost(host)) matches++
            count = matches
        }
    }

    private fun measureVisibility(stories: List<Story>, rules: List<TitleFilterRule>, policy: StoryVisibilityPolicy) {
        for (story in stories) {
            check(policy.shouldHide(story, StoryType.TOP_STORIES) == rules.any { it.matches(story.title.orEmpty()) })
        }
        benchmarkRule.measureRepeated {
            var hidden = 0
            for (story in stories) if (policy.shouldHide(story, StoryType.TOP_STORIES)) hidden++
            count = hidden
        }
    }

    private fun settingsEntries(size: Int): List<SettingsSearchEntry> {
        val titles = listOf("Text size", "Font", "Dark mode", "Vertical spacing", "Reader mode", "Link previews")
        return List(size) { index ->
            SettingsSearchEntry("setting-$index", "comments", titles[index % titles.size],
                listOf("Appearance", "Comments"), "Customize font typography layout display preference $index",
                aliases = listOf("Display ${titles[index % titles.size]}"))
        }
    }

    private fun hosts(size: Int): List<String> = List(size) {
        when (it % 5) {
            0 -> "example.com"
            1 -> "news.example.com"
            2 -> "deep.news.example.com"
            3 -> "notexample.com"
            else -> "example.com.other.test"
        }
    }

    private fun titleRules(size: Int): List<TitleFilterRule> = List(size) {
        TitleFilterRule(if (it == size - 1) "machine learning" else "excluded topic $it",
            if (it % 2 == 0) TitleFilterMatchMode.WORDS_PHRASE else TitleFilterMatchMode.CONTAINS_TEXT)
    }

    private fun stories(size: Int): List<Story> = List(size) {
        Story(when (it % 4) {
            0 -> "New Machine Learning Research and Practical Tools"
            1 -> "A Detailed Guide to Kotlin Coroutines and Structured Concurrency"
            2 -> "Building Efficient Software: Measurements, Memory, and Careful Verification"
            else -> "Café\u00a0News: A Small Compiler and Its Design"
        }, 40_000_000 + it, true, false)
    }
}
