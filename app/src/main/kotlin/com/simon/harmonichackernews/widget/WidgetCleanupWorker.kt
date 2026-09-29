package com.simon.harmonichackernews.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.simon.harmonichackernews.harmonicAppComposition
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val widgetStorageLocks = ConcurrentHashMap<Int, Mutex>()

/** Cleanup waits for a cancelled refresh to finish writing its images. */
internal suspend fun <T> withWidgetStorage(id: Int, block: suspend () -> T): T =
    widgetStorageLocks.getOrPut(id, ::Mutex).withLock { block() }

class WidgetCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getInt(WIDGET_ID, -1)
        if (id < 0) return@withContext Result.failure()
        val context = applicationContext
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id) != null) {
            return@withContext Result.success()
        }
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(WidgetRefreshWorker.workName(id)).result.get()
        manager.cancelUniqueWork(WidgetRefreshWorker.resizeWorkName(id)).result.get()
        withWidgetStorage(id) {
            // Do not clear a replacement widget if its ID is reused before cleanup executes.
            if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id) != null) {
                return@withWidgetStorage Result.success()
            }
            context.harmonicAppComposition.widgets.clear(id)
            if (WidgetRefreshWorker.imageDirectory(context, id).deleteRecursively()) Result.success()
            else Result.retry()
        }
    }

    companion object {
        private const val WIDGET_ID = "widgetId"
        fun enqueue(context: Context, id: Int) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "stories-widget-cleanup-$id", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<WidgetCleanupWorker>()
                    .setInputData(workDataOf(WIDGET_ID to id)).build(),
            )
        }
    }
}
