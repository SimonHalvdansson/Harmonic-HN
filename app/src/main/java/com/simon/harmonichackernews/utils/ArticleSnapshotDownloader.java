package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.simon.harmonichackernews.network.NetworkComponent;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class ArticleSnapshotDownloader {
    private static final long MAX_ARTICLE_CACHE_BYTES = 200L * 1024L * 1024L;
    private static final long MAX_TEMP_FILE_AGE_MS = TimeUnit.DAYS.toMillis(1);
    private static final int BUFFER_SIZE_BYTES = 16 * 1024;
    private static final String HTML_FILE_SUFFIX = ".html";
    private static final String TEMP_FILE_SUFFIX = ".download";
    private static final Object CACHE_LOCK = new Object();

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ArticleSnapshotDownloader(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @Nullable
    public Call download(int storyId,
                         @NonNull String articleUrl,
                         @NonNull DownloadCallback callback) {
        final Request request;
        try {
            request = new Request.Builder()
                    .url(articleUrl)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build();
        } catch (IllegalArgumentException e) {
            return null;
        }

        Call call = NetworkComponent.getOkHttpClientInstance().newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call failedCall, @NonNull IOException e) {
                deliverResult(callback, failedCall, false);
            }

            @Override
            public void onResponse(@NonNull Call completedCall, @NonNull Response response) {
                boolean success = false;
                File tempFile = null;
                try (Response closeableResponse = response) {
                    if (!closeableResponse.isSuccessful()) {
                        throw new IOException("Article server returned HTTP "
                                + closeableResponse.code());
                    }

                    ResponseBody body = closeableResponse.body();
                    if (body == null) {
                        throw new IOException("Article response had no body");
                    }
                    MediaType contentType = body.contentType();
                    if (!isHtml(contentType)) {
                        throw new IOException("Article response was not HTML");
                    }
                    long contentLength = body.contentLength();
                    if (contentLength > Utils.MAX_CACHED_ARTICLE_BYTES) {
                        throw new IOException("Article HTML exceeds the 5 MiB cache limit");
                    }

                    File cacheDir = Utils.getArticleCacheDir(appContext);
                    if ((!cacheDir.exists() && !cacheDir.mkdirs()) || !cacheDir.isDirectory()) {
                        throw new IOException("Could not create article cache");
                    }
                    tempFile = File.createTempFile(
                            storyId + "-", TEMP_FILE_SUFFIX, cacheDir);
                    streamBodyToFile(body, tempFile);

                    File outputFile = Utils.getArticleCacheFile(appContext, storyId);
                    synchronized (CACHE_LOCK) {
                        moveReplacing(tempFile, outputFile);
                        outputFile.setLastModified(System.currentTimeMillis());
                        SettingsUtils.saveStringToSharedPreferences(
                                appContext,
                                Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_URL + storyId,
                                articleUrl);
                        SettingsUtils.saveStringToSharedPreferences(
                                appContext,
                                Utils.KEY_SHARED_PREFERENCES_CACHED_ARTICLE_CHARSET + storyId,
                                contentType.charset(StandardCharsets.UTF_8).name());
                        cleanupCache(cacheDir, outputFile);
                    }
                    tempFile = null;
                    success = true;
                } catch (IOException ignored) {
                    // A failed article snapshot is reported to the batch controller below.
                } finally {
                    if (tempFile != null) {
                        tempFile.delete();
                    }
                    deliverResult(callback, completedCall, success);
                }
            }
        });
        return call;
    }

    private static boolean isHtml(@Nullable MediaType contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.type().toLowerCase(Locale.US);
        String subtype = contentType.subtype().toLowerCase(Locale.US);
        return ("text".equals(type) && "html".equals(subtype))
                || ("application".equals(type) && "xhtml+xml".equals(subtype));
    }

    private static void streamBodyToFile(@NonNull ResponseBody body,
                                         @NonNull File outputFile) throws IOException {
        long downloadedBytes = 0L;
        try (BufferedInputStream inputStream = new BufferedInputStream(body.byteStream());
             FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            byte[] buffer = new byte[BUFFER_SIZE_BYTES];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                downloadedBytes += bytesRead;
                if (downloadedBytes > Utils.MAX_CACHED_ARTICLE_BYTES) {
                    throw new IOException("Article HTML exceeds the 5 MiB cache limit");
                }
                outputStream.write(buffer, 0, bytesRead);
            }
            if (downloadedBytes == 0L) {
                throw new IOException("Article response was empty");
            }
            outputStream.getFD().sync();
        }
    }

    private void cleanupCache(@NonNull File cacheDir,
                              @NonNull File protectedFile) {
        File[] files = cacheDir.listFiles();
        if (files == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long totalBytes = 0L;
        List<File> cachedArticles = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            String name = file.getName();
            if (name.endsWith(TEMP_FILE_SUFFIX)) {
                long age = Math.max(0L, now - file.lastModified());
                if (age > MAX_TEMP_FILE_AGE_MS) {
                    file.delete();
                }
                continue;
            }
            if (!name.endsWith(HTML_FILE_SUFFIX)) {
                continue;
            }
            if (file.length() <= 0L || file.length() > Utils.MAX_CACHED_ARTICLE_BYTES) {
                int storyId = getStoryId(file);
                if (storyId > 0) {
                    Utils.deleteCachedArticleSnapshot(appContext, storyId);
                } else {
                    file.delete();
                }
                continue;
            }
            cachedArticles.add(file);
            totalBytes += file.length();
        }

        cachedArticles.sort(Comparator.comparingLong(File::lastModified));
        for (File file : cachedArticles) {
            if (totalBytes <= MAX_ARTICLE_CACHE_BYTES) {
                break;
            }
            if (file.equals(protectedFile)) {
                continue;
            }
            long length = file.length();
            int storyId = getStoryId(file);
            if (storyId > 0) {
                Utils.deleteCachedArticleSnapshot(appContext, storyId);
                if (!file.exists()) {
                    totalBytes -= length;
                }
            }
        }
    }

    private static int getStoryId(@NonNull File file) {
        String name = file.getName();
        if (!name.endsWith(HTML_FILE_SUFFIX)) {
            return -1;
        }
        try {
            return Integer.parseInt(
                    name.substring(0, name.length() - HTML_FILE_SUFFIX.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void moveReplacing(@NonNull File source,
                                      @NonNull File target) throws IOException {
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

    private void deliverResult(@NonNull DownloadCallback callback,
                               @NonNull Call call,
                               boolean success) {
        mainHandler.post(() -> callback.onComplete(call, success));
    }

    public interface DownloadCallback {
        void onComplete(@NonNull Call call, boolean success);
    }
}
