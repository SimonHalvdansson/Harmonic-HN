package com.simon.harmonichackernews.summary.local;

import android.content.Context;

import androidx.annotation.Nullable;

/**
 * Defensive no-op runtime delivery implementation for the FOSS distribution.
 *
 * <p>The FOSS UI does not expose local summarization, but keeping this boundary explicit makes
 * stale preferences and shared main-source code fail closed without linking Play Feature Delivery.
 */
public final class LocalAiRuntimeManager {
    private LocalAiRuntimeManager() {
    }

    public interface StatusListener {
        void onRuntimeStatusChanged();
    }

    public enum State {
        NOT_INSTALLED,
        PENDING,
        DOWNLOADING,
        INSTALLING,
        INSTALLED,
        FAILED,
        CANCELED
    }

    public static final class Status {
        public final LocalModelManager.Runtime runtime;
        public final State state;
        public final long bytesDownloaded;
        public final long totalBytes;
        public final String error;
        public final String pendingModelId;
        public final int sessionId;

        private Status(LocalModelManager.Runtime runtime) {
            this.runtime = runtime;
            state = State.NOT_INSTALLED;
            bytesDownloaded = 0L;
            totalBytes = 0L;
            error = "Local AI is not included in the FOSS distribution.";
            pendingModelId = "";
            sessionId = 0;
        }

        public boolean isActive() {
            return false;
        }

        public int getProgressPercent() {
            return 0;
        }
    }

    public static void addStatusListener(Context context, StatusListener listener) {
        listener.onRuntimeStatusChanged();
    }

    public static void removeStatusListener(StatusListener listener) {
    }

    public static Status getStatus(Context context, LocalModelManager.Runtime runtime) {
        return new Status(runtime);
    }

    public static boolean isRuntimeInstalled(Context context,
                                             LocalModelManager.Runtime runtime) {
        return false;
    }

    @Nullable
    public static String requestRuntimeAndModelDownload(Context context, String modelId) {
        return "Local AI is not included in the FOSS distribution.";
    }

    public static void cancelRuntimeInstall(Context context,
                                            LocalModelManager.Runtime runtime) {
    }

    public static String getRuntimeLabel(LocalModelManager.Runtime runtime) {
        return runtime == LocalModelManager.Runtime.GEMINI_NANO
                ? "Gemini Nano"
                : "local AI runtime";
    }

    public static String getEngineClassName(LocalModelManager.Runtime runtime) {
        throw new IllegalStateException("Local AI is not included in the FOSS distribution.");
    }
}
