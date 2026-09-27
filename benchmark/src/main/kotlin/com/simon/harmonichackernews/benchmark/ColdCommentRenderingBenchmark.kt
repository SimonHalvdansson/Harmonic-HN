package com.simon.harmonichackernews.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Disk-cached discussion with empty process-local render caches on every opening. */
@RunWith(AndroidJUnit4::class)
class ColdCommentRenderingBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    @Before fun prepare() = prepareBenchmarkApp()
    @After fun finish() = finishBenchmarkApp()

    @Test fun coldRenderMedium() = measure(fromStories = false)

    /** Isolate the first Comments render from process/activity startup and its system animation. */
    @Test fun coldRenderMediumFromStories() = measure(fromStories = true)

    @OptIn(ExperimentalMetricApi::class)
    private fun measure(fromStories: Boolean) = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(
            FrameTimingMetric(),
            TraceSectionMetric("CommentsOpen.contentReady", TraceSectionMetric.Mode.First),
        ),
        compilationMode = CompilationMode.Full(),
        iterations = InstrumentationRegistry.getArguments().getString("comments.iterations")?.toInt() ?: 12,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            prepareDeterministicCommentsFixture(CommentsBenchmarkFixture.MEDIUM)
            killProcess()
            if (fromStories) {
                startActivityAndWait()
                device.waitForIdle()
            }
        },
        measureBlock = {
            openDeterministicCommentsFixture(CommentsBenchmarkFixture.MEDIUM)
            check(device.wait(Until.hasObject(By.textContains("It bothers me that so many of us developers")), 10_000)) {
                "The cold discussion must render its actual body, not just row placeholders"
            }
            device.waitForIdle()
        },
    )
}
