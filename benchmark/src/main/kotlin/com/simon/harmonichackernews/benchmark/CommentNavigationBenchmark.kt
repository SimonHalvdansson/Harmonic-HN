package com.simon.harmonichackernews.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual long-press buttons against a fixed, large, variable-height thread. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class CommentNavigationBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    private var originalNavigation: Boolean? = null
    private var originalSmoothScroll: Boolean? = null

    @Before
    fun prepareApp() {
        prepareBenchmarkApp()
        withCommentSettings {
            originalNavigation = setSwitch("Show navigation buttons", true)
            originalSmoothScroll = setSwitch("Smooth scroll comments", true)
        }
    }

    @After
    fun finishApp() {
        try {
            if (originalNavigation != null || originalSmoothScroll != null) {
                withCommentSettings {
                    originalNavigation?.let { setSwitch("Show navigation buttons", it) }
                    originalSmoothScroll?.let { setSwitch("Smooth scroll comments", it) }
                }
            }
        } finally {
            finishBenchmarkApp()
        }
    }

    @Test
    fun longPressToLastAndFirst() = rule.measureRepeated(
        packageName = BenchmarkPackageName,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            prepareDeterministicCommentsFixture(CommentsBenchmarkFixture.LARGE)
            openDeterministicCommentsFixture(CommentsBenchmarkFixture.LARGE)
        },
        measureBlock = {
            val next = checkNotNull(device.wait(
                Until.findObject(By.desc("Next top-level comment")), 5_000,
            ))
            next.longClick()
            device.waitForIdle()
            check(device.wait(Until.gone(By.text(CommentsBenchmarkFixture.LARGE.title)), 5_000))
            val previous = checkNotNull(device.wait(
                Until.findObject(By.desc("Previous top-level comment")), 5_000,
            ))
            previous.longClick()
            device.waitForIdle()
            check(device.wait(Until.hasObject(By.text(CommentsBenchmarkFixture.LARGE.title)), 5_000))
        },
    )

    private fun withCommentSettings(block: UiDevice.() -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        instrumentation.context.startActivity(checkNotNull(
            instrumentation.context.packageManager.getLaunchIntentForPackage(BenchmarkPackageName),
        ))
        device.waitForIdle()
        if (device.hasObject(By.res("comment-row"))) {
            device.pressBack()
            device.waitForIdle()
        }
        checkNotNull(device.wait(Until.findObject(By.desc("More options")), 5_000)).click()
        checkNotNull(device.wait(Until.findObject(By.text("Settings")), 5_000)).click()
        checkNotNull(device.wait(Until.findObject(By.text("Comments")), 5_000)).click()
        device.waitForIdle()
        device.block()
        device.pressBack()
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    private fun UiDevice.setSwitch(label: String, checked: Boolean): Boolean {
        // Keep swipes below the pinned comment preview, which otherwise intercepts them.
        var attempts = 0
        while (!hasObject(By.text(label)) && attempts++ < 8) {
            swipe(displayWidth / 2, (displayHeight * 0.88f).toInt(),
                displayWidth / 2, (displayHeight * 0.4f).toInt(), 60)
            waitForIdle()
        }
        val row = checkNotNull(findObject(By.checkable(true).hasDescendant(By.text(label))))
        val original = row.isChecked
        if (original != checked) {
            row.click()
            waitForIdle()
            check(checkNotNull(findObject(By.checkable(true).hasDescendant(By.text(label)))).isChecked == checked)
        }
        return original
    }
}
