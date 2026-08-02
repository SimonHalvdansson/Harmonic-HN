package com.simon.harmonichackernews.summary.local

import android.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.NonNull
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.simon.harmonichackernews.ui.settings.SettingsIntents
import com.simon.harmonichackernews.ui.settings.SettingsIntents.createAiSummary
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutionException
import kotlin.math.min

/** Downloads a local model to app-owned storage, with resumable progress.  */
class LocalModelDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    private var connection: HttpURLConnection? = null

    override fun doWork(): Result {
        val modelId = getInputData().getString(KEY_MODEL_ID)
        val modelName = getInputData().getString(KEY_MODEL_NAME)
        val modelUrl = getInputData().getString(KEY_MODEL_URL)
        val fileName = getInputData().getString(KEY_FILE_NAME)
        val expectedBytes = getInputData().getLong(KEY_EXPECTED_BYTES, 0L)
        if (modelId == null || modelName == null || modelUrl == null || fileName == null || expectedBytes <= 0L) {
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
            getApplicationContext(), modelId, fileName
        )
        val partialFile = LocalModelManager.getPartialModelFile(
            getApplicationContext(), modelId, fileName
        )
        val parent = partialFile.getParentFile()
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
            connection = URL(modelUrl).openConnection() as HttpURLConnection?
            connection!!.setConnectTimeout(30000)
            connection!!.setReadTimeout(60000)
            connection!!.setInstanceFollowRedirects(true)
            connection!!.setRequestProperty("Accept-Encoding", "identity")
            if (downloadedBytes > 0L) {
                connection!!.setRequestProperty("Range", "bytes=" + downloadedBytes + "-")
            }
            connection!!.connect()

            val responseCode = connection!!.getResponseCode()
            val resumed = responseCode == HttpURLConnection.HTTP_PARTIAL
            if (responseCode != HttpURLConnection.HTTP_OK && !resumed) {
                return failure("Model server returned HTTP " + responseCode)
            }
            if (!resumed) {
                downloadedBytes = 0L
            }

            BufferedInputStream(connection!!.getInputStream()).use { input ->
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
                    output.getFD().sync()
                }
            }
            if (partialFile.length() != expectedBytes) {
                return failure(
                    ("Downloaded model size was " + partialFile.length()
                            + " bytes; expected " + expectedBytes)
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
            return failure(getMessage(e))
        } finally {
            if (connection != null) {
                connection!!.disconnect()
                connection = null
            }
        }
    }

    override fun onStopped() {
        if (connection != null) {
            connection!!.disconnect()
        }
        super.onStopped()
    }

    private fun publishProgress(modelName: String?, receivedBytes: Long, expectedBytes: Long) {
        setProgressAsync(
            Data.Builder()
                .putLong(KEY_RECEIVED_BYTES, receivedBytes)
                .build()
        )
        val percent = min(100L, receivedBytes * 100L / expectedBytes).toInt()
        setForegroundAsync(createForegroundInfo(modelName, percent))
    }

    private fun createForegroundInfo(modelName: String?, percent: Int): ForegroundInfo {
        val context = getApplicationContext()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Local model downloads", NotificationManager.IMPORTANCE_LOW
            )
            channel.setDescription("Progress for local AI model downloads")
            manager.createNotificationChannel(channel)
        }

        val settingsIntent = createAiSummary(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: NotificationCompat.Builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.stat_sys_download)
                .setContentTitle("Downloading " + modelName)
                .setContentText(percent.toString() + "% complete")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, percent, false)
        notification.setContentIntent(pendingIntent)

        val notificationId = getId().hashCode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ForegroundInfo(
                notificationId, notification.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        }
        return ForegroundInfo(notificationId, notification.build())
    }

    private fun failure(error: String?): Result {
        return Result.failure(Data.Builder().putString(KEY_ERROR, error).build())
    }

    companion object {
        const val KEY_MODEL_ID: String = "model_id"
        const val KEY_MODEL_NAME: String = "model_name"
        const val KEY_MODEL_URL: String = "model_url"
        const val KEY_FILE_NAME: String = "file_name"
        const val KEY_EXPECTED_BYTES: String = "expected_bytes"
        const val KEY_RECEIVED_BYTES: String = "received_bytes"
        const val KEY_ERROR: String = "error"

        private const val CHANNEL_ID = "local_model_download"
        private val BUFFER_SIZE = 256 * 1024
        private fun getMessage(throwable: Throwable?): String? {
            if (throwable == null || throwable.message == null || throwable.message!!.isEmpty()) {
                return "Unknown download error"
            }
            return throwable.message
        }
    }
}
