package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.simon.harmonichackernews.network.NetworkComponent;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;

public class FileDownloader {
    private String mCacheDir;
    private final Handler mMainHandler;

    public FileDownloader(Context ctx) {
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) || !Environment.isExternalStorageRemovable()) {
            if (ctx.getExternalCacheDir() != null) {
                mCacheDir = ctx.getExternalCacheDir().getPath();
            }
        } else {
            if (ctx.getCacheDir() != null) {
                mCacheDir = ctx.getCacheDir().getPath();
            }
        }

        mMainHandler = new Handler(Looper.getMainLooper());
    }

    @WorkerThread
    public void downloadFile(String url, String mimeType, FileDownloaderCallback callback) {
        if (TextUtils.isEmpty(mCacheDir)) {
            mMainHandler.post(() -> callback.onFailure(null, null));
            return;
        }
        File outputFile = new File(mCacheDir, new File(url).getName());
        if (outputFile.exists()) {
            mMainHandler.post(() -> callback.onSuccess(outputFile.getPath()));
            return;
        }

        final Request request = new Request.Builder().url(url)
                .addHeader("Content-Type", mimeType)
                .build();

        NetworkComponent.getOkHttpClientInstance().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mMainHandler.post(() -> callback.onFailure(call, e));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    BufferedSink sink = Okio.buffer(Okio.sink(outputFile));
                    sink.writeAll(response.body().source());
                    sink.close();
                    mMainHandler.post(() -> callback.onSuccess(outputFile.getPath()));
                } catch (IOException e) {
                    this.onFailure(call, e);
                }
            }
        });
    }

    public interface FileDownloaderCallback {
        void onFailure(Call call, IOException e);
        void onSuccess(String filePath);
    }
}
