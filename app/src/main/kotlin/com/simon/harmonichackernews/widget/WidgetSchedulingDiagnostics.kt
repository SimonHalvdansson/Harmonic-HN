package com.simon.harmonichackernews.widget

import android.content.Context
import androidx.work.WorkInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Persist receiver/enqueue events even when the network-constrained worker never starts. */
internal class WidgetSchedulingDiagnostics(context: Context, widgetId: Int) {
    private val preferences = context.getSharedPreferences("widget-scheduling-$widgetId", Context.MODE_PRIVATE)

    fun recordAndroidUpdate(now: Long = System.currentTimeMillis()) {
        preferences.edit().putLong("androidUpdate", now).apply()
    }

    fun recordEnqueued(workId: UUID, source: String, now: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString("workId", workId.toString())
            .putString("source", source)
            .putLong("enqueued", now)
            .apply()
    }

    fun recordStarted(workId: UUID, attempt: Int, now: Long = System.currentTimeMillis()) {
        preferences.edit()
            .putString("startedWorkId", workId.toString())
            .putInt("attempt", attempt + 1)
            .putLong("started", now)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    fun snapshot(workInfos: List<WorkInfo>, lastSuccess: Long?, now: Long = System.currentTimeMillis()): String {
        val recorded = preferences.all
        val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        fun time(value: Long?) = value?.takeIf { it > 0 && it != Long.MAX_VALUE }
            ?.let { dateFormat.format(Date(it)) } ?: "not recorded"
        fun eventTime(key: String) = time(recorded[key] as? Long)
        val workId = recorded["workId"] as? String
        // A replaced/cancelled predecessor must not be presented as the current request.
        val work = workInfos.firstOrNull { it.id.toString() == workId }
        return buildString {
            append("Observed: ${time(now)}\n")
            append("Android update: ${eventTime("androidUpdate")}\n")
            append("Queued: ${eventTime("enqueued")}")
            (recorded["source"] as? String)?.let { append(" · $it") }
            workId?.let { append(" · ${it.take(8)}") }
            append("\nStarted: ${eventTime("started")}")
            (recorded["startedWorkId"] as? String)?.let { append(" · ${it.take(8)} · attempt ${recorded["attempt"]}") }
            append("\nJob: ")
            if (work == null) {
                append(if (workId == null) "not recorded" else "not found (pending enqueue or pruned)")
            } else {
                append("${work.state} · runs=${work.runAttemptCount} · requires ${work.constraints.requiredNetworkType}")
                if (work.state == WorkInfo.State.ENQUEUED && work.nextScheduleTimeMillis != Long.MAX_VALUE) {
                    append("\nEligible after: ${time(work.nextScheduleTimeMillis)}")
                }
                if (work.stopReason != WorkInfo.STOP_REASON_NOT_STOPPED) {
                    append("\nLast stop: ${widgetWorkStopReason(work.stopReason)}")
                }
            }
            append("\nLast success: ${time(lastSuccess)}")
        }
    }
}

private fun widgetWorkStopReason(reason: Int): String = when (reason) {
    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "cancelled by app"
    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "connectivity constraint"
    WorkInfo.STOP_REASON_DEVICE_STATE -> "device state (e.g. Doze)"
    WorkInfo.STOP_REASON_QUOTA -> "quota"
    WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "background restriction"
    WorkInfo.STOP_REASON_APP_STANDBY -> "app standby"
    WorkInfo.STOP_REASON_TIMEOUT -> "timeout"
    WorkInfo.STOP_REASON_USER -> "user stopped app"
    WorkInfo.STOP_REASON_PREEMPT -> "preempted"
    WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "system processing"
    else -> "code $reason"
}
