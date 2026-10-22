package com.simon.harmonichackernews.widget

import android.content.Context
import androidx.work.WorkInfo
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Persist receiver/enqueue events even when the network-constrained worker never starts. */
internal class WidgetSchedulingDiagnostics(context: Context, private val widgetId: Int) {
    private val appContext = context.applicationContext

    // Even locating the DataStore file stays off Main. Opening migrates the existing diagnostics.
    private suspend fun store(): AndroidKeyValueStore = withContext(Dispatchers.IO) {
        AndroidKeyValueStore.named(appContext, "widget-scheduling-$widgetId").also { it.awaitLoaded() }
    }

    suspend fun recordAndroidUpdate(now: Long = System.currentTimeMillis()) {
        val preferences = store()
        preferences.putLong("androidUpdate", now)
        preferences.awaitPersisted()
    }

    suspend fun recordEnqueued(
        workId: UUID,
        source: String,
        now: Long = System.currentTimeMillis(),
        androidUpdate: Long? = null,
    ) {
        val preferences = store()
        preferences.update {
            putString("workId", workId.toString())
            putString("source", source)
            putLong("enqueued", now)
            androidUpdate?.let { putLong("androidUpdate", it) }
        }
        preferences.awaitPersisted()
    }

    suspend fun recordStarted(workId: UUID, attempt: Int, now: Long = System.currentTimeMillis()) {
        val preferences = store()
        preferences.update {
            putString("startedWorkId", workId.toString())
            putInt("attempt", attempt + 1)
            putLong("started", now)
        }
        preferences.awaitPersisted()
    }

    suspend fun clear() {
        val preferences = store()
        preferences.clear()
        preferences.awaitPersisted()
    }

    fun snapshots(workInfos: List<WorkInfo>, lastSuccess: Long?): Flow<String> = flow {
        emitAll(store().changes.map { snapshot(workInfos, lastSuccess) })
    }.flowOn(Dispatchers.IO)

    suspend fun snapshot(workInfos: List<WorkInfo>, lastSuccess: Long?, now: Long = System.currentTimeMillis()): String {
        val preferences = store()
        val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        fun time(value: Long?) = value?.takeIf { it > 0 && it != Long.MAX_VALUE }
            ?.let { dateFormat.format(Date(it)) } ?: "not recorded"
        fun eventTime(key: String) = time(preferences.getLong(key, 0))
        val workId = preferences.getString("workId")
        // A replaced/cancelled predecessor must not be presented as the current request.
        val work = workInfos.firstOrNull { it.id.toString() == workId }
        return buildString {
            append("Observed: ${time(now)}\n")
            append("Android update: ${eventTime("androidUpdate")}\n")
            append("Queued: ${eventTime("enqueued")}")
            preferences.getString("source")?.let { append(" · $it") }
            workId?.let { append(" · ${it.take(8)}") }
            append("\nStarted: ${eventTime("started")}")
            preferences.getString("startedWorkId")?.let { append(" · ${it.take(8)} · attempt ${preferences.getInt("attempt", 0)}") }
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
