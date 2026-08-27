package com.simon.harmonichackernews.summary.local

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.network.FileResumableDownloadDestination
import com.simon.harmonichackernews.network.KtorTransferClient
import com.simon.harmonichackernews.network.ResumableDownloadService
import com.simon.harmonichackernews.summary.LocalModelFilePolicy
import com.simon.harmonichackernews.ui.settings.SettingsIntents.createAiSummary
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.io.files.Path

/** Downloads a local model to app-owned storage, with resumable progress.  */
class LocalModelDownloadWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID)
        val modelName = inputData.getString(KEY_MODEL_NAME)
        val modelUrl = inputData.getString(KEY_MODEL_URL)
        val fileName = inputData.getString(KEY_FILE_NAME)
        val expectedBytes = inputData.getLong(KEY_EXPECTED_BYTES, 0L)
        if (
            modelId == null || modelName == null || modelUrl == null || fileName == null ||
            expectedBytes <= 0L
        ) {
            return failure("Invalid model download request")
        }

        ensureNotificationChannel()
        try {
            setForeground(createForegroundInfo(modelName, 0))
        } catch (error: CancellationException) {
            throw error
        } catch (e: Throwable) {
            return failure("Android couldn't restart the download in the background")
        }

        val modelsRoot = Path(androidLocalModelsRoot(applicationContext).absolutePath)
        val outputFile = LocalModelFilePolicy.completedPath(modelsRoot, modelId, fileName)
        val partialFile = LocalModelFilePolicy.partialPath(modelsRoot, modelId, fileName)
        try {
            var lastUpdateAt = SystemClock.elapsedRealtime()
            var lastPercent = 0
            ResumableDownloadService(
                KtorTransferClient(applicationContext.harmonicAppComposition.network.httpClient),
            ).download(
                url = modelUrl,
                expectedBytes = expectedBytes,
                destination = FileResumableDownloadDestination(outputFile, partialFile),
                onProgress = { progress ->
                    val now = SystemClock.elapsedRealtime()
                    val percent = progress.percentOf(expectedBytes)
                    if (
                        percent >= lastPercent + MIN_PROGRESS_PERCENT_DELTA ||
                        now - lastUpdateAt >= PROGRESS_INTERVAL_MILLIS
                    ) {
                        publishProgress(modelName, progress.bytesWritten, expectedBytes)
                        lastUpdateAt = now
                        lastPercent = percent
                    }
                },
            )
            if (lastPercent < 100) publishProgress(modelName, expectedBytes, expectedBytes)
            return Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (e: Throwable) {
            return failure(errorMessage(e))
        }
    }

    private suspend fun publishProgress(
        modelName: String,
        receivedBytes: Long,
        expectedBytes: Long,
    ) {
        setProgress(
            Data.Builder()
                .putLong(KEY_RECEIVED_BYTES, receivedBytes)
                .build(),
        )
        val percent = min(100L, receivedBytes * 100L / expectedBytes).toInt()
        setForeground(createForegroundInfo(modelName, percent))
    }

    private fun createForegroundInfo(modelName: String, percent: Int): ForegroundInfo {
        val context = applicationContext
        val settingsIntent = createAiSummary(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.stat_sys_download)
            .setContentTitle("Downloading $modelName")
            .setContentText("$percent% complete")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, false)
            .setContentIntent(pendingIntent)

        val notificationId = id.hashCode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(
                notificationId,
                notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }
        return ForegroundInfo(notificationId, notification.build())
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local model downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress for local AI model downloads"
            }
            manager?.createNotificationChannel(channel)
        }
    }

    private fun failure(error: String): Result =
        Result.failure(Data.Builder().putString(KEY_ERROR, error).build())

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_MODEL_URL = "model_url"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_EXPECTED_BYTES = "expected_bytes"
        const val KEY_RECEIVED_BYTES = "received_bytes"
        const val KEY_ERROR = "error"

        private const val CHANNEL_ID = "local_model_download"
        private const val PROGRESS_INTERVAL_MILLIS = 500L
        private const val MIN_PROGRESS_PERCENT_DELTA = 1

        private fun errorMessage(throwable: Throwable): String =
            throwable.message?.takeIf(String::isNotEmpty) ?: "Unknown download error"
    }

}

private fun com.simon.harmonichackernews.network.TransferProgress.percentOf(
    expectedBytes: Long,
): Int = min(100L, bytesWritten.coerceAtMost(expectedBytes) * 100L / expectedBytes).toInt()
