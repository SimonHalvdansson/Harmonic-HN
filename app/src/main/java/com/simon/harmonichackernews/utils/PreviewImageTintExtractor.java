package com.simon.harmonichackernews.utils;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adapter-lifecycle-owned worker for extracting image tint colors.
 *
 * <p>Drawable sampling stays on the main thread. Only the immutable sample bitmap is handed to
 * the worker, and all callbacks are dispatched back to the main thread.</p>
 */
public final class PreviewImageTintExtractor {
    private static final int MAX_PENDING_EXTRACTIONS = 32;

    public enum Source {
        PREVIEW_IMAGE,
        FAVICON
    }

    public interface Callback {
        void onTintReady(int tintColor);

        void onTintFailed();

        void onTintCancelled();
    }

    private final Handler resultHandler = new Handler(Looper.getMainLooper());
    private final Map<RequestKey, ExtractionTask> tasks = new HashMap<>();
    @Nullable
    private ThreadPoolExecutor executor;
    private int generation;
    private boolean attached;

    @MainThread
    public void attach() {
        attached = true;
    }

    @MainThread
    public void detach() {
        attached = false;
        cancelAll();
    }

    @MainThread
    public void request(
            Object owner,
            String sourceUrl,
            int baseColor,
            String paletteTintMode,
            Source source,
            Drawable drawable,
            Callback callback) {
        if (!attached) {
            callback.onTintCancelled();
            return;
        }

        RequestKey key = new RequestKey(
                owner,
                sourceUrl,
                baseColor,
                paletteTintMode,
                source);
        if (tasks.containsKey(key)) {
            return;
        }

        final Bitmap sample;
        try {
            sample = PreviewImageTintUtils.renderDrawableToSampleBitmap(drawable);
        } catch (RuntimeException e) {
            callback.onTintFailed();
            return;
        }
        if (sample == null) {
            callback.onTintFailed();
            return;
        }

        ExtractionTask task = new ExtractionTask(key, sample, generation, callback);
        tasks.put(key, task);

        ThreadPoolExecutor currentExecutor = getExecutor();
        try {
            currentExecutor.execute(task);
        } catch (RejectedExecutionException firstRejection) {
            if (!currentExecutor.isShutdown()) {
                Runnable oldest = currentExecutor.getQueue().poll();
                if (oldest instanceof ExtractionTask) {
                    cancelTask((ExtractionTask) oldest);
                }
                try {
                    currentExecutor.execute(task);
                    return;
                } catch (RejectedExecutionException ignored) {
                    // The owner may have detached while the queue was being trimmed.
                }
            }
            cancelTask(task);
        }
    }

    private ThreadPoolExecutor getExecutor() {
        if (executor != null && !executor.isShutdown()) {
            return executor;
        }

        executor = new ThreadPoolExecutor(
                1,
                1,
                15L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_EXTRACTIONS),
                runnable -> new Thread(() -> {
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    } catch (SecurityException ignored) {
                        // Continue on the worker's default priority if the runtime disallows this.
                    }
                    runnable.run();
                }, "story-tint-extractor"),
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @MainThread
    private void finish(
            ExtractionTask task,
            @Nullable Integer tintColor,
            @Nullable RuntimeException error) {
        if (tasks.get(task.key) != task) {
            return;
        }
        tasks.remove(task.key);

        if (task.cancelled || task.generation != generation) {
            task.callback.onTintCancelled();
        } else if (error != null || tintColor == null) {
            task.callback.onTintFailed();
        } else {
            task.callback.onTintReady(tintColor);
        }
    }

    @MainThread
    private void cancelTask(ExtractionTask task) {
        if (tasks.get(task.key) != task) {
            return;
        }

        tasks.remove(task.key);
        task.cancel();
        task.callback.onTintCancelled();
    }

    @MainThread
    private void cancelAll() {
        generation++;
        for (ExtractionTask task : new ArrayList<>(tasks.values())) {
            cancelTask(task);
        }

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static final class RequestKey {
        private final Object owner;
        private final String sourceUrl;
        private final int baseColor;
        private final String paletteTintMode;
        private final Source source;

        private RequestKey(
                Object owner,
                String sourceUrl,
                int baseColor,
                String paletteTintMode,
                Source source) {
            this.owner = owner;
            this.sourceUrl = sourceUrl;
            this.baseColor = baseColor;
            this.paletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
            this.source = source;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof RequestKey)) {
                return false;
            }

            RequestKey other = (RequestKey) object;
            return owner == other.owner
                    && baseColor == other.baseColor
                    && source == other.source
                    && Objects.equals(sourceUrl, other.sourceUrl)
                    && Objects.equals(paletteTintMode, other.paletteTintMode);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(owner);
            result = 31 * result + Objects.hashCode(sourceUrl);
            result = 31 * result + baseColor;
            result = 31 * result + Objects.hashCode(paletteTintMode);
            result = 31 * result + source.hashCode();
            return result;
        }
    }

    private final class ExtractionTask implements Runnable {
        private final RequestKey key;
        private final Bitmap sample;
        private final int generation;
        private final Callback callback;
        private final AtomicBoolean sampleClaimed = new AtomicBoolean();
        private volatile boolean cancelled;

        private ExtractionTask(
                RequestKey key,
                Bitmap sample,
                int generation,
                Callback callback) {
            this.key = key;
            this.sample = sample;
            this.generation = generation;
            this.callback = callback;
        }

        private void cancel() {
            cancelled = true;
            if (sampleClaimed.compareAndSet(false, true)) {
                recycleSample();
            }
        }

        @Override
        public void run() {
            if (!sampleClaimed.compareAndSet(false, true)) {
                return;
            }

            Integer tintColor = null;
            RuntimeException failure = null;
            try {
                if (!cancelled) {
                    tintColor = PreviewImageTintUtils.calculateCardTint(
                            key.baseColor,
                            sample,
                            key.paletteTintMode);
                }
            } catch (RuntimeException e) {
                failure = e;
            } finally {
                recycleSample();
            }

            if (!cancelled) {
                Integer result = tintColor;
                RuntimeException error = failure;
                resultHandler.post(() -> finish(this, result, error));
            }
        }

        private void recycleSample() {
            if (!sample.isRecycled()) {
                sample.recycle();
            }
        }
    }
}
