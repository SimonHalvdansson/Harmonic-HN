package com.simon.harmonichackernews.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
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

    @Test fun coldRenderMedium() = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        iterations = InstrumentationRegistry.getArguments().getString("comments.iterations")?.toInt() ?: 12,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            prepareDeterministicCommentsFixture(CommentsBenchmarkFixture.MEDIUM)
            killProcess()
        },
        measureBlock = {
            openDeterministicCommentsFixture(CommentsBenchmarkFixture.MEDIUM)
            check(device.wait(Until.hasObject(By.textContains("Holy cow, I'm a very casual gamer")), 10_000)) {
                "The cold discussion must render its actual body, not just row placeholders"
            }
            device.waitForIdle()
        },
    )
}
