package com.simon.harmonichackernews.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
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
        startupMode = StartupMode.COLD,
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
