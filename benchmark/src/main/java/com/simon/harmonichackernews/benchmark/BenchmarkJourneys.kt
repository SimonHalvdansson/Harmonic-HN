package com.simon.harmonichackernews.benchmark

import android.content.Intent
import android.graphics.Point
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

internal const val BenchmarkPackageName =
    "com.simon.harmonichackernews.compose.benchmark"
private const val SeedCommentsBenchmarkAction =
    "com.simon.harmonichackernews.action.BENCHMARK_SEED_COMMENTS"
private const val OpenCommentsBenchmarkAction =
    "com.simon.harmonichackernews.action.BENCHMARK_OPEN_COMMENTS"
private const val CommentsBenchmarkFixtureExtra = "benchmark_comments_fixture"

internal enum class CommentsBenchmarkFixture(
    val intentValue: String,
    val title: String,
) {
    SMALL(
        intentValue = "small",
        title = "Deterministic Comments benchmark fixture",
    ),
    MEDIUM(
        intentValue = "medium",
        title = "How I cut GTA Online loading times by 70%",
    ),
    LARGE(
        intentValue = "large",
        title = "CrowdStrike Update: Windows Bluescreen and Boot Loops",
    ),
}

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

internal fun finishBenchmarkApp() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    UiDevice.getInstance(instrumentation).apply {
        pressHome()
        waitForIdle()
    }
}

private fun UiDevice.awaitHomeScreen() {
    waitForIdle()
    wait(Until.findObject(By.text("Get started")), 2_000)?.let { label ->
        (label.parent ?: label).click()
    }
    wait(Until.findObject(By.desc("Refresh")), 1_000)?.let {
        pressBack()
    }
    check(wait(Until.hasObject(By.text("Top Stories")), 30_000)) {
        "The stories screen did not appear"
    }
    waitForIdle()
}

private fun UiDevice.awaitStoryContent() {
    awaitHomeScreen()
    // A cold process start can restore the Comments destination from the previous iteration.
    // Return to Stories in setup so only the forward open is part of the measured trace.
    wait(Until.findObject(By.desc("Refresh")), 500)?.let {
        pressBack()
        check(wait(Until.gone(By.desc("Refresh")), 10_000)) {
            "The restored comments screen did not close"
        }
    }
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

internal fun MacrobenchmarkScope.openFirstStoryComments() {
    openStoryCommentsAt(findFirstStoryCommentsButtonCenter())
}

/** Seeds the fixed JSON through the app's real cache, verifies production parsing, then returns. */
internal fun MacrobenchmarkScope.prepareDeterministicCommentsFixture(
    fixture: CommentsBenchmarkFixture,
) {
    device.executeShellCommand(commentsBenchmarkCommand(SeedCommentsBenchmarkAction, fixture))
    check(device.wait(Until.hasObject(By.text(fixture.title)), 30_000)) {
        "The ${fixture.intentValue} deterministic Comments fixture did not parse and render"
    }
    device.waitForIdle()
    device.pressBack()
    check(device.wait(Until.gone(By.text(fixture.title)), 10_000)) {
        "The ${fixture.intentValue} deterministic Comments fixture did not close"
    }
    check(device.wait(Until.hasObject(By.text("Top Stories")), 10_000)) {
        "The Stories screen did not return after fixture setup"
    }
    device.waitForIdle()
    SystemClock.sleep(300)
}

/** Opens the already-seeded fixed JSON through the normal Comments route without UI lookup. */
internal fun MacrobenchmarkScope.openDeterministicCommentsFixture(
    fixture: CommentsBenchmarkFixture,
) {
    device.executeShellCommand(commentsBenchmarkCommand(OpenCommentsBenchmarkAction, fixture))
    SystemClock.sleep(550)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.findFirstStoryCommentsButtonCenter(): Point {
    val firstRank = device.wait(
        Until.findObject(By.text(StoryRankPattern)),
        10_000,
    ) ?: error("No visible ranked story was found")
    val firstStoryY = firstRank.visibleBounds.centerY()
    val commentsButton = device.findObjects(By.clickable(true))
        .asSequence()
        .filter { it.visibleBounds.centerX() >= device.displayWidth * 0.8f }
        .filter { it.visibleBounds.centerY() >= firstStoryY }
        .minByOrNull { it.visibleBounds.centerY() }
        ?: error("No visible story comments button was found")
    return Point(
        commentsButton.visibleBounds.centerX(),
        commentsButton.visibleBounds.centerY(),
    )
}

internal fun MacrobenchmarkScope.openStoryCommentsAt(center: Point) {
    device.click(center.x, center.y)
    // FrameTimingMetric needs to include the complete 450 ms destination transition, while the
    // app's staged cache work is intentionally running behind it. The trace-section metric verifies
    // that this click created the Comments coordinator without querying accessibility mid-capture.
    SystemClock.sleep(550)
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.scrollComments(repetitions: Int = 3) {
    val centerX = device.displayWidth / 2
    val upperY = (device.displayHeight * 0.3f).toInt()
    val lowerY = (device.displayHeight * 0.82f).toInt()
    repeat(repetitions) {
        device.swipe(centerX, lowerY, centerX, upperY, 12)
    }
    device.waitForIdle()
}

private val StoryRankPattern = Pattern.compile("[0-9]+\\.")

private fun commentsBenchmarkCommand(
    action: String,
    fixture: CommentsBenchmarkFixture,
): String =
    "am start -W --activity-single-top -a $action " +
        "--es $CommentsBenchmarkFixtureExtra ${fixture.intentValue} " +
        "-n $BenchmarkPackageName/com.simon.harmonichackernews.MainActivity"
