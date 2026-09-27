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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoriesScrollingBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun imageArrivals() = measure(cached = false)
    @Test fun cachedPalettes() = measure(cached = true)

    private fun measure(cached: Boolean) {
        var run = (System.currentTimeMillis() / 1_000 % 90_000).toInt()
        rule.measureRepeated(
            packageName = BenchmarkPackageName,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Full(),
            iterations = InstrumentationRegistry.getArguments().getString("stories.iterations")?.toInt() ?: 8,
            setupBlock = {
                pressHome()
                killProcess()
                startActivityAndWait(Intent().setClassName(BenchmarkPackageName,
                    "com.simon.harmonichackernews.StoriesScrollBenchmarkActivity")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra("stories_run", run++)
                    .putExtra("stories_cached", cached))
                device.wait(Until.findObject(By.text("Get started")), 500)?.let { (it.parent ?: it).click() }
                check(device.wait(Until.hasObject(By.text("1. A small database built for predictable performance")), 10_000))
                device.waitForIdle()
                SystemClock.sleep(750)
            },
            measureBlock = {
                val x = device.displayWidth / 2
                val top = (device.displayHeight * 0.25f).toInt()
                val bottom = (device.displayHeight * 0.80f).toInt()
                repeat(8) { device.swipe(x, bottom, x, top, 20) }
                device.waitForIdle()
                repeat(8) { device.swipe(x, top, x, bottom, 20) }
                device.waitForIdle()
            },
        )
    }
}
