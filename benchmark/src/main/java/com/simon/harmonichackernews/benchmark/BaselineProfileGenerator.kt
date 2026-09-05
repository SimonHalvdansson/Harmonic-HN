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
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = BenchmarkPackageName,
        includeInStartupProfile = true,
        filterPredicate = { rule -> rule.contains("com/simon/harmonichackernews") },
    ) {
        pressHome()
        startActivityAndWait()
        // DEX layout should prioritize the first screen, without post-launch AI warm-up,
        // scrolling, or the comments fixture. The broader profile below covers those journeys.
    }

    @Test
    fun generate() = rule.collect(
        packageName = BenchmarkPackageName,
        filterPredicate = { rule -> rule.contains("com/simon/harmonichackernews") },
    ) {
        pressHome()
        startActivityAndWait()
        awaitStoryContent()
        scrollStoryList()
        prepareDeterministicCommentsFixture(CommentsBenchmarkFixture.LARGE)
        openDeterministicCommentsFixture(CommentsBenchmarkFixture.LARGE)
        scrollComments(repetitions = 6)
    }
}
