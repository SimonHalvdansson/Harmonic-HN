package com.simon.harmonichackernews.summary.local

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.simon.harmonichackernews.harmonicAppComposition
import com.simon.harmonichackernews.network.DownloadSink
import com.simon.harmonichackernews.network.KtorTransferClient
import com.simon.harmonichackernews.network.ResumableDownloadDestination
import com.simon.harmonichackernews.network.ResumableDownloadService
import com.simon.harmonichackernews.ui.settings.SettingsIntents.createAiSummary
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        try {
            setForeground(createForegroundInfo(modelName, 0))
        } catch (error: CancellationException) {
            throw error
        } catch (e: Throwable) {
            return failure("Android couldn't restart the download in the background")
        }

        val outputFile = modelFile(
            applicationContext,
            modelId,
            fileName,
        )
        val partialFile = partialModelFile(
            applicationContext,
            modelId,
            fileName,
        )
        try {
            var lastUpdateAt = 0L
            ResumableDownloadService(
                KtorTransferClient(applicationContext.harmonicAppComposition.network.httpClient),
            ).download(
                url = modelUrl,
                expectedBytes = expectedBytes,
                destination = AndroidModelDownloadDestination(outputFile, partialFile),
                onProgress = { progress ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdateAt >= PROGRESS_INTERVAL_MILLIS) {
                        publishProgress(modelName, progress.bytesWritten, expectedBytes)
                        lastUpdateAt = now
                    }
                },
            )
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Local model downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progress for local AI model downloads"
            }
            manager?.createNotificationChannel(channel)
        }

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

        private fun errorMessage(throwable: Throwable): String =
            throwable.message?.takeIf(String::isNotEmpty) ?: "Unknown download error"
    }

    private class AndroidModelDownloadDestination(
        private val completed: File,
        private val partial: File,
    ) : ResumableDownloadDestination {
        override suspend fun prepare(): Boolean = withContext(Dispatchers.IO) {
            partial.parentFile?.let { it.isDirectory || it.mkdirs() } == true
        }

        override suspend fun completedBytes(): Long = withContext(Dispatchers.IO) {
            completed.takeIf(File::isFile)?.length() ?: 0L
        }

        override suspend fun partialBytes(): Long = withContext(Dispatchers.IO) {
            partial.takeIf(File::isFile)?.length() ?: 0L
        }

        override suspend fun removeCompleted(): Boolean = withContext(Dispatchers.IO) {
            !completed.exists() || completed.delete()
        }

        override suspend fun removePartial(): Boolean = withContext(Dispatchers.IO) {
            !partial.exists() || partial.delete()
        }

        override suspend fun openPartial(append: Boolean): DownloadSink =
            withContext(Dispatchers.IO) { ModelDownloadSink(partial, append) }

        override suspend fun promotePartial(): Boolean = withContext(Dispatchers.IO) {
            (!completed.exists() || completed.delete()) && partial.renameTo(completed)
        }
    }

    /** Closing on abort deliberately preserves the resumable partial file. */
    private class ModelDownloadSink(file: File, append: Boolean) : DownloadSink {
        private val output = FileOutputStream(file, append)
        override val reference: String = file.absolutePath

        override suspend fun write(buffer: ByteArray, offset: Int, length: Int) =
            withContext(Dispatchers.IO) {
                output.write(buffer, offset, length)
            }

        override suspend fun close() = withContext(Dispatchers.IO) {
            output.fd.sync()
            output.close()
        }

        override suspend fun abort() = withContext(Dispatchers.IO) {
            runCatching { output.fd.sync() }
            runCatching { output.close() }
            Unit
        }
    }
}
