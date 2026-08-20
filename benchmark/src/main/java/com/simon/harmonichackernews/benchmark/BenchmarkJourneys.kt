package com.simon.harmonichackernews.benchmark

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val BenchmarkPackageName =
    "com.simon.harmonichackernews.compose.benchmark"

internal fun MacrobenchmarkScope.awaitStoryContent() {
    device.awaitStoryContent()
}

internal fun prepareBenchmarkApp() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)
    val launchIntent = instrumentation.context.packageManager
        .getLaunchIntentForPackage(BenchmarkPackageName)
        ?: error("No launcher activity for $BenchmarkPackageName")
    instrumentation.context.startActivity(
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    device.awaitHomeScreen()
    device.pressHome()
    device.waitForIdle()
}

private fun UiDevice.awaitHomeScreen() {
    waitForIdle()
    wait(Until.findObject(By.text("Get started")), 2_000)?.click()
    check(wait(Until.hasObject(By.text("Top Stories")), 30_000)) {
        "The stories screen did not appear"
    }
    waitForIdle()
}

private fun UiDevice.awaitStoryContent() {
    awaitHomeScreen()
    check(wait(Until.hasObject(By.text(StoryRankPattern)), 30_000)) {
        "A ranked story row did not appear"
    }
    waitForIdle()
    SystemClock.sleep(500)
}

internal fun MacrobenchmarkScope.scrollStoryList(repetitions: Int = 4) {
    val centerX = device.displayWidth / 2
    val upperY = (device.displayHeight * 0.22f).toInt()
    val lowerY = (device.displayHeight * 0.78f).toInt()
    repeat(repetitions) {
        device.swipe(centerX, lowerY, centerX, upperY, 12)
    }
    repeat(repetitions) {
        device.swipe(centerX, upperY, centerX, lowerY, 12)
    }
    device.waitForIdle()
}

private val StoryRankPattern = Pattern.compile("[0-9]+\\.")
