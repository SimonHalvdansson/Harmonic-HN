package com.simon.harmonichackernews.benchmark

import android.os.SystemClock
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

/** Opens linked discussions with two retained comments screens beneath the destination. */
@RunWith(AndroidJUnit4::class)
class NestedCommentsOpeningBenchmark {
    @get:Rule(order = 0) val environment = ReadingProfileEnvironment()
    @get:Rule(order = 1) val rule = MacrobenchmarkRule()

    @Test fun thirdDiscussionOpen() {
        rule.measureRepeated(
            packageName = BenchmarkPackageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            iterations = InstrumentationRegistry.getArguments().getString("reading.iterations")?.toInt() ?: 10,
            setupBlock = {
                keepReadingProfileDeviceAwake()
                killProcess()
                startReadingProfileStories()
                for (fixture in CommentsBenchmarkFixture.entries) {
                    prepareDeterministicCommentsFixture(fixture, activityName = ReadingProfileActivity)
                }
                openDeterministicCommentsFixture(CommentsBenchmarkFixture.SMALL, ReadingProfileActivity)
                openLinkedDiscussion(26296339, CommentsBenchmarkFixture.MEDIUM.title)
                // Macrobenchmark closes the app after measurement. Validate nested history in
                // setup, outside frame timing, then restore the two retained source screens.
                openLinkedDiscussion(41002195, CommentsBenchmarkFixture.LARGE.title)
                device.pressBack()
                check(device.wait(Until.hasObject(By.text(CommentsBenchmarkFixture.MEDIUM.title)), 10_000))
                device.waitForIdle()
                device.pressBack()
                check(device.wait(Until.hasObject(By.text(CommentsBenchmarkFixture.SMALL.title)), 10_000))
                device.waitForIdle()
                openLinkedDiscussion(26296339, CommentsBenchmarkFixture.MEDIUM.title)
            },
            measureBlock = {
                openLinkedDiscussion(41002195, CommentsBenchmarkFixture.LARGE.title)
            },
        )
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.openLinkedDiscussion(id: Int, title: String) {
        device.executeShellCommand(
            "am start -W --activity-single-top " +
                "-a com.simon.harmonichackernews.action.BENCHMARK_OPEN_LINKED_COMMENTS " +
                "--ei benchmark_story_id $id -n $BenchmarkPackageName/$ReadingProfileActivity",
        )
        check(device.wait(Until.hasObject(By.text(title)), 10_000))
        check(device.wait(Until.hasObject(By.res("comment-row")), 10_000))
        SystemClock.sleep(550)
        device.waitForIdle()
    }
}
