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
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Compare identical APK code/fixtures with the old and refreshed packaged profiles. */
@RunWith(AndroidJUnit4::class)
class ReadingProfileBenchmark {
    @get:Rule(order = 0) val environment = ReadingProfileEnvironment()
    @get:Rule(order = 1) val rule = MacrobenchmarkRule()

    private val iterations get() = InstrumentationRegistry.getArguments()
        .getString("reading.iterations")?.toInt()?.also { require(it > 0) } ?: 10
    private val compilation get() = CompilationMode.Partial(BaselineProfileMode.Require)

    @Test fun startup() = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation,
        startupMode = StartupMode.COLD,
        iterations = iterations,
        setupBlock = {
            keepReadingProfileDeviceAwake()
            pressHome()
        },
        measureBlock = { startReadingProfileStories() },
    )

    @Test fun stories() = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        iterations = iterations,
        setupBlock = {
            keepReadingProfileDeviceAwake()
            killProcess()
            startReadingProfileStories()
        },
        measureBlock = { scrollStoryList() },
    )

    @Test fun settingsFirstOpen() = measureSettingsFirstOpen(compilation)

    @Test fun settingsFirstOpenWithoutCompilation() = measureSettingsFirstOpen(CompilationMode.None())

    private fun measureSettingsFirstOpen(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = iterations,
        setupBlock = {
            keepReadingProfileDeviceAwake()
            // A fresh process is essential: reopening Settings hides first-use loading and JIT.
            killProcess()
            startReadingProfileStories()
            prepareReadingProfileSettings()
        },
        measureBlock = { openReadingProfileSettings() },
    )

    @OptIn(ExperimentalMetricApi::class)
    @Test fun commentsOpen() = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric(),
            TraceSectionMetric("CommentsOpen.contentReady", TraceSectionMetric.Mode.First)),
        compilationMode = compilation,
        iterations = iterations,
        setupBlock = {
            keepReadingProfileDeviceAwake()
            killProcess()
            startReadingProfileStories()
            prepareReadingProfileComments()
        },
        measureBlock = { openReadingProfileComments() },
    )

    @Test fun commentsCollapse() = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        iterations = iterations,
        setupBlock = {
            keepReadingProfileDeviceAwake()
            killProcess()
            startReadingProfileStories()
            prepareReadingProfileComments()
            openReadingProfileComments()
            revealFixtureComment("sammy2255")
        },
        measureBlock = { collapseAndExpandProfileComments() },
    )

    @Test fun commentsScroll() = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        iterations = iterations,
        setupBlock = {
            keepReadingProfileDeviceAwake()
            killProcess()
            startReadingProfileStories()
            prepareReadingProfileComments()
            openReadingProfileComments()
        },
        measureBlock = { scrollComments(repetitions = 6) },
    )

    @Test fun reader() = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation,
        iterations = iterations,
        setupBlock = {
            keepReadingProfileDeviceAwake()
            killProcess()
            startReadingProfileStories()
            openProfileArticle()
        },
        measureBlock = { readProfileArticle() },
    )
}
