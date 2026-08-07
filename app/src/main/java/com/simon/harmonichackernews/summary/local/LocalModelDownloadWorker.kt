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
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.simon.harmonichackernews.ui.settings.SettingsIntents.createAiSummary
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutionException
import kotlin.math.min

/** Downloads a local model to app-owned storage, with resumable progress.  */
class LocalModelDownloadWorker(
    context: Context,
    workerParams: WorkerParameters,
) : Worker(context, workerParams) {
    private var connection: HttpURLConnection? = null

    override fun doWork(): Result {
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
            setForegroundAsync(createForegroundInfo(modelName, 0)).get()
        } catch (e: ExecutionException) {
            return failure("Android couldn't restart the download in the background")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            return failure("Model download was interrupted")
        }

        val outputFile = LocalModelManager.getModelFile(
            applicationContext,
            modelId,
            fileName,
        )
        val partialFile = LocalModelManager.getPartialModelFile(
            applicationContext,
            modelId,
            fileName,
        )
        val parent = partialFile.parentFile
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            return failure("Could not create model storage")
        }
        if (outputFile.exists() && outputFile.length() != expectedBytes && !outputFile.delete()) {
            return failure("Could not replace the incomplete model")
        }
        if (outputFile.length() == expectedBytes) {
            return Result.success()
        }
        if (partialFile.length() > expectedBytes && !partialFile.delete()) {
            return failure("Could not replace the invalid partial download")
        }
        if (partialFile.length() == expectedBytes) {
            if (outputFile.exists() && !outputFile.delete()) {
                return failure("Could not replace the existing model")
            }
            if (!partialFile.renameTo(outputFile)) {
                return failure("Could not finish installing the model")
            }
            publishProgress(modelName, expectedBytes, expectedBytes)
            return Result.success()
        }

        var downloadedBytes = partialFile.length()
        try {
            val activeConnection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("Accept-Encoding", "identity")
                if (downloadedBytes > 0L) {
                    setRequestProperty("Range", "bytes=$downloadedBytes-")
                }
            }
            connection = activeConnection
            activeConnection.connect()

            val responseCode = activeConnection.responseCode
            val resumed = responseCode == HttpURLConnection.HTTP_PARTIAL
            if (responseCode != HttpURLConnection.HTTP_OK && !resumed) {
                return failure("Model server returned HTTP $responseCode")
            }
            if (!resumed) {
                downloadedBytes = 0L
            }

            BufferedInputStream(activeConnection.inputStream).use { input ->
                FileOutputStream(partialFile, resumed).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastUpdateAt = 0L
                    var bytesRead: Int
                    while ((input.read(buffer).also { bytesRead = it }) != -1) {
                        if (isStopped()) {
                            return failure("Model download was cancelled")
                        }
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead.toLong()

                        val now = System.currentTimeMillis()
                        if (now - lastUpdateAt >= 500L) {
                            publishProgress(modelName, downloadedBytes, expectedBytes)
                            lastUpdateAt = now
                        }
                    }
                    output.fd.sync()
                }
            }
            if (partialFile.length() != expectedBytes) {
                return failure(
                    "Downloaded model size was ${partialFile.length()} bytes; " +
                        "expected $expectedBytes",
                )
            }
            if (outputFile.exists() && !outputFile.delete()) {
                return failure("Could not replace the existing model")
            }
            if (!partialFile.renameTo(outputFile)) {
                return failure("Could not finish installing the model")
            }
            publishProgress(modelName, expectedBytes, expectedBytes)
            return Result.success()
        } catch (e: IOException) {
            return failure(errorMessage(e))
        } finally {
            connection?.disconnect()
            connection = null
        }
    }

    override fun onStopped() {
        connection?.disconnect()
        super.onStopped()
    }

    private fun publishProgress(modelName: String, receivedBytes: Long, expectedBytes: Long) {
        setProgressAsync(
            Data.Builder()
                .putLong(KEY_RECEIVED_BYTES, receivedBytes)
                .build(),
        )
        val percent = min(100L, receivedBytes * 100L / expectedBytes).toInt()
        setForegroundAsync(createForegroundInfo(modelName, percent))
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
        private const val BUFFER_SIZE = 256 * 1024

        private fun errorMessage(throwable: Throwable): String =
            throwable.message?.takeIf(String::isNotEmpty) ?: "Unknown download error"
    }
}
