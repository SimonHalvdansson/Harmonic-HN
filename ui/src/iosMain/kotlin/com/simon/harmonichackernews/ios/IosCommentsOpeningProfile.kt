package com.simon.harmonichackernews.ios

import androidx.compose.runtime.withFrameNanos
import com.simon.harmonichackernews.presentation.CommentsPerformanceTrace
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSLock
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import kotlin.time.TimeSource

/** Opt-in simulator diagnostics. Disk writes happen after the measured opening, never per frame. */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal class IosCommentsOpeningProfile private constructor(
    private val mode: String,
    private val storyId: Int,
) {
    val waitForFirstDraw: Boolean get() = mode != "immediate"
    val waitForAnotherFrame: Boolean get() = mode == "next-frame"
    private val start = TimeSource.Monotonic.markNow()
    private val lock = NSLock()
    private val events = mutableListOf<Pair<String, Double>>()
    val trace = CommentsPerformanceTrace(
        begin = { name, _ -> event("$name.begin") },
        end = { name, _ -> event("$name.end") },
    )

    fun event(name: String) {
        val time = start.elapsedNow().inWholeNanoseconds / 1_000_000.0
        lock.lock()
        try { events += name to time } finally { lock.unlock() }
    }

    suspend fun recordFrames() {
        val frames = mutableListOf<Double>()
        var previous = withFrameNanos { it }
        while (start.elapsedNow().inWholeMilliseconds < 1_000) {
            val current = withFrameNanos { it }
            frames += (current - previous) / 1_000_000.0
            previous = current
        }
        lock.lock()
        val recorded = try { events.distinctBy { it.first } } finally { lock.unlock() }
        val json = """{"mode":"$mode","storyId":$storyId,"events":{${recorded.joinToString { "\"${it.first}\":${it.second}" }}},"frameIntervalsMs":[${frames.joinToString()}]}"""
        NSString.create(string = json).writeToFile(
            NSHomeDirectory() + "/Library/Caches/comments-profile-${NSUUID().UUIDString}.json",
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }

    companion object {
        fun create(debug: Boolean, storyId: Int): IosCommentsOpeningProfile? {
            if (!debug) return null
            val mode = NSProcessInfo.processInfo.environment["HARMONIC_PROFILE_COMMENTS"] as? String
            return mode?.takeIf { it == "immediate" || it == "after-draw" || it == "next-frame" }
                ?.let { IosCommentsOpeningProfile(it, storyId) }
        }
    }
}
