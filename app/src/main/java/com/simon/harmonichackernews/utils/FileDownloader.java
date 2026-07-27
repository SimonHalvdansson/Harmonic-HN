package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.simon.harmonichackernews.network.NetworkComponent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSink;
import okio.Okio;

public class FileDownloader {
    private static final String CACHE_DIR_NAME = "pdf_cache";
    private static final String CACHE_FILE_SUFFIX = ".pdf";
    private static final String TEMP_FILE_SUFFIX = ".download";
    private static final long MAX_CACHE_SIZE_BYTES = 250L * 1024L * 1024L;
    private static final long MAX_CACHE_FILE_AGE_MS = TimeUnit.DAYS.toMillis(30);
    private static final long MAX_TEMP_FILE_AGE_MS = TimeUnit.DAYS.toMillis(1);
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private static final ExecutorService CACHE_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "harmonic-pdf-cache");
                thread.setDaemon(true);
                return thread;
            });

    private final File mCacheDir;
    private final Handler mMainHandler;

    public FileDownloader(Context ctx) {
        File externalCacheDir = ctx.getExternalCacheDir();
        File baseCacheDir = externalCacheDir == null ? ctx.getCacheDir() : externalCacheDir;
        mCacheDir = baseCacheDir == null ? null : new File(baseCacheDir, CACHE_DIR_NAME);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Enqueues an asynchronous download for the provided URL. This method may be
     * called from the main thread.
     */
    public void downloadFile(String url, String mimeType, FileDownloaderCallback callback) {
        if (TextUtils.isEmpty(url) || mCacheDir == null) {
            deliverFailure(callback, null, new IOException("PDF cache is unavailable"));
            return;
        }

        CACHE_EXECUTOR.execute(() -> prepareDownload(url, mimeType, callback));
    }

    private void prepareDownload(
            String url,
            String mimeType,
            FileDownloaderCallback callback) {
        if ((!mCacheDir.exists() && !mCacheDir.mkdirs()) || !mCacheDir.isDirectory()) {
            deliverFailure(callback, null, new IOException("Could not create PDF cache"));
            return;
        }

        final File outputFile;
        final File tempFile;
        try {
            outputFile = new File(mCacheDir, sha256Hex(url) + CACHE_FILE_SUFFIX);
            cleanupCache(outputFile.isFile() && outputFile.length() > 0L ? outputFile : null);
            if (outputFile.isFile() && outputFile.length() > 0L) {
                outputFile.setLastModified(System.currentTimeMillis());
                deliverSuccess(callback, outputFile);
                return;
            }
            if (outputFile.exists()) {
                outputFile.delete();
            }
            tempFile = File.createTempFile(sha256Hex(url) + "-", TEMP_FILE_SUFFIX, mCacheDir);
        } catch (IOException e) {
            deliverFailure(callback, null, e);
            return;
        }

        final Request request;
        try {
            request = new Request.Builder().url(url)
                    .header("Accept", mimeType)
                    .build();
        } catch (IllegalArgumentException e) {
            tempFile.delete();
            deliverFailure(callback, null, new IOException("Invalid PDF URL", e));
            return;
        }

        NetworkComponent.getOkHttpClientInstance().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                tempFile.delete();
                deliverFailure(callback, call, e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful()) {
                        throw new IOException("PDF server returned HTTP "
                                + closeableResponse.code());
                    }
                    ResponseBody body = closeableResponse.body();
                    if (body == null) {
                        throw new IOException("PDF response had no body");
                    }
                    if (body.contentLength() > MAX_CACHE_SIZE_BYTES) {
                        throw new IOException("PDF is larger than the 250 MiB viewer limit");
                    }
                    try (BufferedSink sink = Okio.buffer(Okio.sink(tempFile))) {
                        Buffer buffer = new Buffer();
                        long downloadedBytes = 0L;
                        long bytesRead;
                        while ((bytesRead = body.source().read(buffer, 8 * 1024L)) != -1L) {
                            downloadedBytes += bytesRead;
                            if (downloadedBytes > MAX_CACHE_SIZE_BYTES) {
                                throw new IOException(
                                        "PDF is larger than the 250 MiB viewer limit");
                            }
                            sink.write(buffer, bytesRead);
                        }
                    }
                    if (tempFile.length() <= 0L) {
                        throw new IOException("Downloaded PDF was empty");
                    }
                    CACHE_EXECUTOR.execute(
                            () -> finishDownload(call, tempFile, outputFile, callback));
                } catch (IOException e) {
                    tempFile.delete();
                    deliverFailure(callback, call, e);
                }
            }
        });
    }

    private void finishDownload(
            Call call,
            File tempFile,
            File outputFile,
            FileDownloaderCallback callback) {
        try {
            moveReplacing(tempFile, outputFile);
            outputFile.setLastModified(System.currentTimeMillis());
            cleanupCache(outputFile);
            deliverSuccess(callback, outputFile);
        } catch (IOException e) {
            tempFile.delete();
            deliverFailure(callback, call, e);
        }
    }

    private void cleanupCache(File protectedFile) {
        File[] files = mCacheDir.listFiles();
        if (files == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long totalBytes = 0L;
        List<File> cachedPdfs = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            long age = Math.max(0L, now - file.lastModified());
            if (name.endsWith(TEMP_FILE_SUFFIX)) {
                if (age > MAX_TEMP_FILE_AGE_MS) {
                    file.delete();
                }
                continue;
            }
            if (!name.endsWith(CACHE_FILE_SUFFIX)) {
                continue;
            }
            if (!file.equals(protectedFile) && age > MAX_CACHE_FILE_AGE_MS) {
                if (file.delete()) {
                    continue;
                }
            }
            cachedPdfs.add(file);
            totalBytes += file.length();
        }

        cachedPdfs.sort(Comparator.comparingLong(File::lastModified));
        for (File file : cachedPdfs) {
            if (totalBytes <= MAX_CACHE_SIZE_BYTES) {
                break;
            }
            if (file.equals(protectedFile)) {
                continue;
            }
            long length = file.length();
            if (file.delete()) {
                totalBytes -= length;
            }
        }
    }

    private static void moveReplacing(File source, File target) throws IOException {
        try {
            Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(
                    source.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256Hex(String value) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[bytes.length * 2];
            for (int i = 0; i < bytes.length; i++) {
                int unsignedByte = bytes[i] & 0xff;
                hex[i * 2] = HEX_DIGITS[unsignedByte >>> 4];
                hex[i * 2 + 1] = HEX_DIGITS[unsignedByte & 0x0f];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
    }

    private void deliverSuccess(FileDownloaderCallback callback, File file) {
        mMainHandler.post(() -> callback.onSuccess(file.getPath()));
    }

    private void deliverFailure(
            FileDownloaderCallback callback,
            Call call,
            IOException exception) {
        mMainHandler.post(() -> callback.onFailure(call, exception));
    }

    public interface FileDownloaderCallback {
        void onFailure(Call call, IOException e);
        void onSuccess(String filePath);
    }
}
