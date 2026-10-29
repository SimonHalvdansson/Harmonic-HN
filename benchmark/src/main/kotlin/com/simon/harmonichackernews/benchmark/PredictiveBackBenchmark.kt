package com.simon.harmonichackernews.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires gesture navigation. Uses the same offline reading fixture for both APKs. */
@RunWith(AndroidJUnit4::class)
class PredictiveBackBenchmark {
    @get:Rule(order = 0) val environment = ReadingProfileEnvironment()
    @get:Rule(order = 1) val rule = MacrobenchmarkRule()

    @Test fun commentsBack() = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = InstrumentationRegistry.getArguments().getString("reading.iterations")?.toInt() ?: 5,
        setupBlock = {
            keepReadingProfileDeviceAwake()
            killProcess()
            startReadingProfileStories()
            prepareReadingProfileComments()
            openReadingProfileComments()
        },
        measureBlock = {
            device.swipe(1, device.displayHeight / 2,
                device.displayWidth * 2 / 3, device.displayHeight / 2, 80)
            check(device.wait(Until.hasObject(By.text("Top Stories")), 5_000)) {
                "Edge Back did not return to Stories; enable gesture navigation on the test device"
            }
            device.waitForIdle()
        },
    )
}
