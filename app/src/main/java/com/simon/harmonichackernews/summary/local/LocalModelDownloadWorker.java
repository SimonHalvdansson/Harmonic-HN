package com.simon.harmonichackernews.summary.local;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.simon.harmonichackernews.SettingsActivity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;

/** Downloads a local model to app-owned storage, with resumable progress. */
public class LocalModelDownloadWorker extends Worker {
    static final String KEY_MODEL_ID = "model_id";
    static final String KEY_MODEL_NAME = "model_name";
    static final String KEY_MODEL_URL = "model_url";
    static final String KEY_FILE_NAME = "file_name";
    static final String KEY_EXPECTED_BYTES = "expected_bytes";
    static final String KEY_RECEIVED_BYTES = "received_bytes";
    static final String KEY_ERROR = "error";

    private static final String CHANNEL_ID = "local_model_download";
    private static final int BUFFER_SIZE = 256 * 1024;
    private HttpURLConnection connection;

    public LocalModelDownloadWorker(@NonNull Context context,
                                    @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String modelId = getInputData().getString(KEY_MODEL_ID);
        String modelName = getInputData().getString(KEY_MODEL_NAME);
        String modelUrl = getInputData().getString(KEY_MODEL_URL);
        String fileName = getInputData().getString(KEY_FILE_NAME);
        long expectedBytes = getInputData().getLong(KEY_EXPECTED_BYTES, 0L);
        if (modelId == null || modelName == null || modelUrl == null
                || fileName == null || expectedBytes <= 0L) {
            return failure("Invalid model download request");
        }

        try {
            setForegroundAsync(createForegroundInfo(modelName, 0)).get();
        } catch (ExecutionException e) {
            return failure("Android couldn't restart the download in the background");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failure("Model download was interrupted");
        }

        File outputFile = LocalModelManager.getModelFile(
                getApplicationContext(), modelId, fileName);
        File partialFile = LocalModelManager.getPartialModelFile(
                getApplicationContext(), modelId, fileName);
        File parent = partialFile.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            return failure("Could not create model storage");
        }
        if (outputFile.exists() && outputFile.length() != expectedBytes
                && !outputFile.delete()) {
            return failure("Could not replace the incomplete model");
        }
        if (outputFile.length() == expectedBytes) {
            return Result.success();
        }
        if (partialFile.length() > expectedBytes && !partialFile.delete()) {
            return failure("Could not replace the invalid partial download");
        }

        long downloadedBytes = partialFile.length();
        try {
            connection = (HttpURLConnection) new URL(modelUrl).openConnection();
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(60_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (downloadedBytes > 0L) {
                connection.setRequestProperty("Range", "bytes=" + downloadedBytes + "-");
            }
            connection.connect();

            int responseCode = connection.getResponseCode();
            boolean resumed = responseCode == HttpURLConnection.HTTP_PARTIAL;
            if (responseCode != HttpURLConnection.HTTP_OK && !resumed) {
                return failure("Model server returned HTTP " + responseCode);
            }
            if (!resumed) {
                downloadedBytes = 0L;
            }

            try (BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream output = new FileOutputStream(partialFile, resumed)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long lastUpdateAt = 0L;
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    if (isStopped()) {
                        return failure("Model download was cancelled");
                    }
                    output.write(buffer, 0, bytesRead);
                    downloadedBytes += bytesRead;

                    long now = System.currentTimeMillis();
                    if (now - lastUpdateAt >= 500L) {
                        publishProgress(modelName, downloadedBytes, expectedBytes);
                        lastUpdateAt = now;
                    }
                }
                output.getFD().sync();
            }

            if (partialFile.length() != expectedBytes) {
                return failure("Downloaded model size was " + partialFile.length()
                        + " bytes; expected " + expectedBytes);
            }
            if (outputFile.exists() && !outputFile.delete()) {
                return failure("Could not replace the existing model");
            }
            if (!partialFile.renameTo(outputFile)) {
                return failure("Could not finish installing the model");
            }
            publishProgress(modelName, expectedBytes, expectedBytes);
            return Result.success();
        } catch (IOException e) {
            return failure(getMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
                connection = null;
            }
        }
    }

    @Override
    public void onStopped() {
        if (connection != null) {
            connection.disconnect();
        }
        super.onStopped();
    }

    private void publishProgress(String modelName, long receivedBytes, long expectedBytes) {
        setProgressAsync(new Data.Builder()
                .putLong(KEY_RECEIVED_BYTES, receivedBytes)
                .build());
        int percent = (int) Math.min(100L, receivedBytes * 100L / expectedBytes);
        setForegroundAsync(createForegroundInfo(modelName, percent));
    }

    private ForegroundInfo createForegroundInfo(String modelName, int percent) {
        Context context = getApplicationContext();
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Local model downloads", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Progress for local AI model downloads");
            manager.createNotificationChannel(channel);
        }

        Intent settingsIntent = SettingsActivity.createAiSummaryIntent(context);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                settingsIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading " + modelName)
                .setContentText(percent + "% complete")
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, percent, false);
        notification.setContentIntent(pendingIntent);

        int notificationId = getId().hashCode();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(notificationId, notification.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        }
        return new ForegroundInfo(notificationId, notification.build());
    }

    private Result failure(String error) {
        return Result.failure(new Data.Builder().putString(KEY_ERROR, error).build());
    }

    private static String getMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null
                || throwable.getMessage().isEmpty()) {
            return "Unknown download error";
        }
        return throwable.getMessage();
    }
}
