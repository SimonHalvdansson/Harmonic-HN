package com.simon.harmonichackernews.benchmark

import android.content.Intent
import android.os.SystemClock
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

/** Run offline to keep refreshes from replacing the fixed discussion during a measurement. */
@RunWith(AndroidJUnit4::class)
class CommentsScrollingBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()
    @Before fun prepare() = prepareBenchmarkApp()
    @After fun finish() = finishBenchmarkApp()

    @Test fun scrollMedium() = measure(CommentsBenchmarkFixture.MEDIUM, toggle = false)
    @Test fun scrollLarge() = measure(CommentsBenchmarkFixture.LARGE, toggle = false)
    @Test fun toggleLarge() = measure(CommentsBenchmarkFixture.LARGE, toggle = true)

    private fun measure(fixture: CommentsBenchmarkFixture, toggle: Boolean) = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        iterations = InstrumentationRegistry.getArguments().getString("comments.iterations")?.toInt() ?: 8,
        setupBlock = {
            pressHome()
            // Reset process-local reading positions and render caches between iterations.
            killProcess()
            startActivityAndWait(Intent().setClassName(BenchmarkPackageName,
                "com.simon.harmonichackernews.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            val firstAuthor = if (fixture == CommentsBenchmarkFixture.LARGE) "sammy2255" else "capableweb"
            prepareDeterministicCommentsFixture(fixture, firstCommentAuthor = firstAuthor, returnToStories = false)
            if (toggle) {
                // The first root's author is a stable tap target in either expanded state.
                check(device.wait(Until.hasObject(By.text("sammy2255")), 5_000))
                check(device.wait(Until.hasObject(By.text("VargaLand")), 5_000))
            }
        },
        measureBlock = {
            if (toggle) {
                repeat(6) { index ->
                    checkNotNull(device.wait(Until.findObject(By.text("sammy2255")), 5_000)).click()
                    val reply = By.text("VargaLand")
                    check(device.wait(if (index % 2 == 1) Until.hasObject(reply) else Until.gone(reply), 5_000)) {
                        "The first reply must disappear on collapse and return on expansion (tap $index)"
                    }
                    SystemClock.sleep(450)
                }
            } else {
                val x = device.displayWidth / 2
                val top = (device.displayHeight * 0.30f).toInt()
                val bottom = (device.displayHeight * 0.80f).toInt()
                repeat(6) { device.swipe(x, bottom, x, top, 12) }
                device.waitForIdle()
                repeat(6) { device.swipe(x, top, x, bottom, 12) }
                device.waitForIdle()
            }
        },
    )
}
