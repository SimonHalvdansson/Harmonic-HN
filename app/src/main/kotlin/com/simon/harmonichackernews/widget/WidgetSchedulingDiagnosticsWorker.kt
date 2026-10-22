package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val diagnosticsLocks = ConcurrentHashMap<Int, Mutex>()

/** Diagnostics must remain writable while a refresh holds its image/storage lock during network I/O. */
internal suspend fun <T> withWidgetDiagnostics(id: Int, block: suspend () -> T): T =
    diagnosticsLocks.getOrPut(id, ::Mutex).withLock { block() }

/** Persists captured events even when the refresh is blocked by its network constraint. */
class WidgetSchedulingDiagnosticsWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(WIDGET_ID, -1)
        val workId = inputData.getString(WORK_ID)?.let(UUID::fromString) ?: return Result.failure()
        if (widgetId < 0) return Result.failure()
        return withWidgetDiagnostics(widgetId) {
            // A queued event must not recreate diagnostics after the widget has been deleted.
            if (AppWidgetManager.getInstance(applicationContext).getAppWidgetInfo(widgetId) == null) {
                return@withWidgetDiagnostics Result.success()
            }
            try {
                WidgetSchedulingDiagnostics(applicationContext, widgetId).recordEnqueued(
                    workId = workId,
                    source = inputData.getString(SOURCE).orEmpty(),
                    now = inputData.getLong(ENQUEUED, 0),
                    androidUpdate = inputData.getLong(ANDROID_UPDATE, 0).takeIf { it > 0 },
                )
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Result.retry()
            }
        }
    }

    companion object {
        private const val WIDGET_ID = "widgetId"
        private const val WORK_ID = "workId"
        private const val SOURCE = "source"
        private const val ENQUEUED = "enqueued"
        private const val ANDROID_UPDATE = "androidUpdate"

        internal fun workName(widgetId: Int) = "stories-widget-diagnostics-$widgetId"

        internal fun request(
            widgetId: Int,
            workId: UUID,
            source: String,
            now: Long,
            androidUpdate: Long?,
        ): OneTimeWorkRequest = OneTimeWorkRequestBuilder<WidgetSchedulingDiagnosticsWorker>()
            .setInputData(workDataOf(
                WIDGET_ID to widgetId, WORK_ID to workId.toString(), SOURCE to source,
                ENQUEUED to now, ANDROID_UPDATE to (androidUpdate ?: 0L),
            )).build()

        internal fun enqueue(context: Context, widgetId: Int, request: OneTimeWorkRequest) {
            // Preserve event order across process death and retries without replacing earlier events.
            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(widgetId), ExistingWorkPolicy.APPEND_OR_REPLACE, request,
            )
        }
    }
}
