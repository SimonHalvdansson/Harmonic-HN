package com.simon.harmonichackernews.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule(order = 0)
    val environment = ReadingProfileEnvironment()

    @get:Rule(order = 1)
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = BenchmarkPackageName,
        includeInStartupProfile = true,
        filterPredicate = ::isProductionProfileRule,
    ) {
        keepReadingProfileDeviceAwake()
        pressHome()
        startReadingProfileStories()
        // DEX layout prioritizes the rendered first feed. Scrolling, comments and the browser
        // belong only to the broader baseline; no deliberate post-launch warm-up is added.
    }

    @Test
    fun stories() = rule.collect(
        packageName = BenchmarkPackageName,
        filterPredicate = ::isProductionProfileRule,
    ) {
        keepReadingProfileDeviceAwake()
        pressHome()
        startReadingProfileStories()
        scrollStoryList()
    }

    @Test
    fun comments() = rule.collect(
        packageName = BenchmarkPackageName,
        filterPredicate = ::isProductionProfileRule,
    ) {
        keepReadingProfileDeviceAwake()
        pressHome()
        startReadingProfileStories()
        prepareReadingProfileComments()
        openReadingProfileComments()
        collapseAndExpandProfileComments()
        scrollComments(repetitions = 6)
        returnToReadingProfileStories()
    }

    @Test
    fun reader() = rule.collect(
        packageName = BenchmarkPackageName,
        filterPredicate = ::isProductionProfileRule,
    ) {
        keepReadingProfileDeviceAwake()
        pressHome()
        startReadingProfileStories()
        openProfileArticle()
        readProfileArticle()
        returnToReadingProfileStories()
    }
}

private fun isProductionProfileRule(rule: String): Boolean =
    rule.contains("com/simon/harmonichackernews") && !rule.contains("StoriesScrollBenchmarkActivity")
