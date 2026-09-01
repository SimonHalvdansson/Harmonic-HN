package com.simon.harmonichackernews.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class HarmonicMacrobenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun prepareApp() {
        prepareBenchmarkApp()
    }

    @After
    fun finishApp() {
        finishBenchmarkApp()
    }

    @Test
    fun coldStartupWithoutCompilation() = measureStartup(CompilationMode.None())

    @Test
    fun coldStartupWithBaselineProfile() = measureStartup(
        CompilationMode.Partial(BaselineProfileMode.Require),
    )

    @Test
    fun storyListScroll() = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            awaitStoryContent()
        },
        measureBlock = {
            scrollStoryList(repetitions = 6)
        },
    )

    @Test
    @OptIn(ExperimentalMetricApi::class)
    fun commentsOpen() {
        rule.measureRepeated(
            packageName = BenchmarkPackageName,
            metrics = listOf(
                FrameTimingMetric(),
                TraceSectionMetric(
                    sectionName = "CommentsOpen.createCoordinator",
                    mode = TraceSectionMetric.Mode.First,
                ),
                TraceSectionMetric(
                    sectionName = "CommentsOpen.cacheRead",
                    mode = TraceSectionMetric.Mode.First,
                ),
                TraceSectionMetric(
                    sectionName = "CommentsOpen.parseCachedJson",
                    mode = TraceSectionMetric.Mode.First,
                ),
                TraceSectionMetric(
                    sectionName = "CommentsOpen.applyCachedThread",
                    mode = TraceSectionMetric.Mode.First,
                ),
                TraceSectionMetric(
                    sectionName = "CommentsOpen.prepareCachedThreadState",
                    mode = TraceSectionMetric.Mode.First,
                ),
                TraceSectionMetric(
                    sectionName = "CommentsOpen.contentReady",
                    mode = TraceSectionMetric.Mode.First,
                ),
            ),
            // Fail instead of silently falling back to run-from-apk: this benchmark is intended
            // to measure the Comments journey with the generated profile installed.
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            iterations = 40,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                prepareDeterministicCommentsFixture()
            },
            measureBlock = {
                openDeterministicCommentsFixture()
            },
        )
    }

    private fun measureStartup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 8,
        setupBlock = { pressHome() },
        measureBlock = { startActivityAndWait() },
    )
}
