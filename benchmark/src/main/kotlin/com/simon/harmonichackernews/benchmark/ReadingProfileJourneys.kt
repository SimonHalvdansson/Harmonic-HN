package com.simon.harmonichackernews.benchmark

import android.app.KeyguardManager
import android.content.Intent
import android.net.ConnectivityManager
import android.os.SystemClock
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread
import org.junit.rules.ExternalResource

internal const val ReadingProfileActivity =
    "com.simon.harmonichackernews.StoriesScrollBenchmarkActivity"
private const val FirstFixtureStory = "1. A small database built for predictable performance"

/** Shell input resets admin-enforced idle timers; UiAutomator gestures alone may not. */
internal fun MacrobenchmarkScope.keepReadingProfileDeviceAwake() {
    device.executeShellCommand("input keyevent 59") // A paired Shift down/up; no text or setting change.
}

/** Fixed input with the production Stories screen, store, image rendering and navigation. */
internal fun MacrobenchmarkScope.startReadingProfileStories() {
    startActivityAndWait(
        Intent().setClassName(BenchmarkPackageName, ReadingProfileActivity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("profile_reading", true),
    )
    device.wait(Until.findObject(By.text("Get started")), 500)?.let { (it.parent ?: it).click() }
    check(device.wait(Until.hasObject(By.text(FirstFixtureStory)), 10_000)) {
        "The fixed Stories feed did not render"
    }
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.prepareReadingProfileSettings() {
    checkNotNull(device.wait(Until.findObject(By.desc("More options")), 5_000)).click()
    check(device.wait(Until.hasObject(By.text("Settings")), 5_000)) {
        "The Stories menu did not show Settings"
    }
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.openReadingProfileSettings() {
    checkNotNull(device.findObject(By.text("Settings"))).click()
    check(device.wait(Until.hasObject(By.text("Appearance")), 5_000)) {
        "The Settings list did not render"
    }
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.prepareReadingProfileComments() {
    prepareDeterministicCommentsFixture(
        CommentsBenchmarkFixture.LARGE,
        activityName = ReadingProfileActivity,
    )
}

internal fun MacrobenchmarkScope.openReadingProfileComments() {
    openDeterministicCommentsFixture(CommentsBenchmarkFixture.LARGE, ReadingProfileActivity)
}

internal fun MacrobenchmarkScope.collapseAndExpandProfileComments() {
    revealFixtureComment("sammy2255")
    val reply = By.text("VargaLand")
    check(device.wait(Until.hasObject(reply), 5_000)) { "Expected an expanded first thread" }
    repeat(3) {
        checkNotNull(device.findObject(By.text("sammy2255"))).click()
        check(device.wait(Until.gone(reply), 5_000)) { "Collapse did not hide the reply" }
        device.waitForIdle()
        checkNotNull(device.findObject(By.text("sammy2255"))).click()
        check(device.wait(Until.hasObject(reply), 5_000)) { "Expansion did not restore the reply" }
        device.waitForIdle()
    }
}

internal fun MacrobenchmarkScope.openProfileArticle() {
    checkNotNull(device.wait(Until.findObject(By.text(FirstFixtureStory)), 10_000)).click()
    check(device.wait(Until.hasObject(By.text("A repeatable reading journey")), 15_000)) {
        "The local article did not render in the integrated browser"
    }
    check(device.wait(Until.hasObject(By.desc("Reader mode")), 15_000)) {
        "Reader mode did not become available"
    }
    device.waitForIdle()
}

internal fun MacrobenchmarkScope.readProfileArticle() {
    checkNotNull(device.findObject(By.desc("Reader mode"))).click()
    check(device.wait(Until.hasObject(By.desc("Reader mode on")), 10_000)) {
        "Reader mode did not enable"
    }
    check(device.wait(Until.gone(By.text("Fixture site navigation")), 10_000)) {
        "Reader extraction did not remove the site navigation"
    }
    val x = device.displayWidth / 2
    val top = (device.displayHeight * 0.25f).toInt()
    val bottom = (device.displayHeight * 0.65f).toInt()
    repeat(4) { device.swipe(x, bottom, x, top, 20) }
    device.waitForIdle()
    repeat(4) { device.swipe(x, top, x, bottom, 20) }
    device.waitForIdle()
    checkNotNull(device.findObject(By.desc("Reader mode on"))).click()
    check(device.wait(Until.hasObject(By.desc("Reader mode")), 10_000))
    check(device.wait(Until.hasObject(By.text("Fixture site navigation")), 10_000)) {
        "Disabling reader mode did not restore the original document"
    }
}

internal fun MacrobenchmarkScope.returnToReadingProfileStories() {
    repeat(3) {
        if (device.hasObject(By.text("Top Stories"))) return
        device.pressBack()
        device.waitForIdle()
    }
    check(device.wait(Until.hasObject(By.text("Top Stories")), 5_000)) {
        "Back navigation did not return to the Stories feed"
    }
}

/**
 * Prevent live HN refreshes from replacing cached fixtures. Only the dedicated benchmark app is
 * launched. Radio state is restored even when a journey fails; no app data or accounts are cleared.
 * The article server remains in the instrumentation process when Macrobenchmark kills the app.
 */
class ReadingProfileEnvironment : ExternalResource() {
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var wifi: Boolean? = null
    private var mobileData: Boolean? = null
    private var server: ServerSocket? = null
    private var worker: Thread? = null

    override fun before() {
        try {
            if (!device.isScreenOn) device.wakeUp()
            check(!InstrumentationRegistry.getInstrumentation().context
                .getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                "Unlock the benchmark device or select an emulator; do not change its lock policy"
            }
            wifi = device.executeShellCommand("settings get global wifi_on").trim().let { it == "1" || it == "2" }
            mobileData = device.executeShellCommand("settings get global mobile_data").trim() == "1"
            device.executeShellCommand("svc wifi disable")
            device.executeShellCommand("svc data disable")
            val connectivity = InstrumentationRegistry.getInstrumentation().context
                .getSystemService(ConnectivityManager::class.java)
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (connectivity.activeNetwork != null && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100)
            }
            check(connectivity.activeNetwork == null) {
                "Profile journeys require offline fixtures; another network is still active"
            }
            val listener = ServerSocket(18765, 10, InetAddress.getByName("127.0.0.1"))
            server = listener
            val paragraphs = (1..24).joinToString("\n") { index ->
                "<p>Section $index describes a small library built for readers. " +
                    "Its shelves hold essays about engineering, science, and everyday discoveries. " +
                    "A clear layout gives each idea room to breathe, while a consistent typeface " +
                    "helps readers follow the argument across several paragraphs. The librarian " +
                    "keeps the collection in a stable order so every visit starts with the same " +
                    "familiar titles. This article is served locally without advertisements, " +
                    "remote images, analytics, or changing content.</p>"
            }
            val body = ("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
                "<title>A repeatable reading journey</title>" +
                "<style>body{font:18px sans-serif;margin:24px;line-height:1.6}</style></head>" +
                "<body><nav>Fixture site navigation</nav><main><article>" +
                "<h1>A repeatable reading journey</h1>$paragraphs</article></main></body></html>")
                .toByteArray(Charsets.UTF_8)
            worker = thread(name = "reading-profile-http", isDaemon = true) {
                while (!listener.isClosed) {
                    try {
                        listener.accept().use { socket ->
                            socket.soTimeout = 5_000
                            val reader = socket.getInputStream().bufferedReader()
                            while (!reader.readLine().isNullOrEmpty()) { /* Consume request headers. */ }
                            socket.getOutputStream().apply {
                                write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                                    "Content-Length: ${body.size}\r\nCache-Control: no-store\r\n" +
                                    "Connection: close\r\n\r\n").toByteArray(Charsets.US_ASCII))
                                write(body)
                                flush()
                            }
                        }
                    } catch (error: java.io.IOException) {
                        if (!listener.isClosed && error !is SocketException) {
                            android.util.Log.w("ReadingProfile", "Article request failed", error)
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            after()
            throw error
        }
    }

    override fun after() {
        try {
            server?.close()
            worker?.join(6_000)
        } finally {
            try {
                wifi?.let { device.executeShellCommand("svc wifi ${if (it) "enable" else "disable"}") }
            } finally {
                mobileData?.let { device.executeShellCommand("svc data ${if (it) "enable" else "disable"}") }
            }
        }
    }
}
