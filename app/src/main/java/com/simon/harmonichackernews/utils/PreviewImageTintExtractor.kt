package com.simon.harmonichackernews.utils

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.annotation.MainThread
import androidx.annotation.Nullable
import java.util.Objects
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.Volatile

/**
 * Adapter-lifecycle-owned worker for extracting image tint colors.
 * 
 * 
 * Drawable sampling stays on the main thread. Only the immutable sample bitmap is handed to
 * the worker, and all callbacks are dispatched back to the main thread.
 */
class PreviewImageTintExtractor {
    enum class Source {
        PREVIEW_IMAGE,
        FAVICON
    }

    interface Callback {
        fun onTintReady(tintColor: Int)

        fun onTintFailed()

        fun onTintCancelled()
    }

    private val resultHandler = Handler(Looper.getMainLooper())
    private val tasks: MutableMap<RequestKey?, ExtractionTask> =
        HashMap<RequestKey?, ExtractionTask>()
    private var executor: ThreadPoolExecutor? = null
    private var generation = 0
    private var attached = false

    @MainThread
    fun attach() {
        attached = true
    }

    @MainThread
    fun detach() {
        attached = false
        cancelAll()
    }

    @MainThread
    fun request(
        owner: Any?,
        sourceUrl: String?,
        baseColor: Int,
        paletteTintMode: String?,
        source: Source,
        drawable: Drawable?,
        callback: Callback
    ) {
        if (!attached) {
            callback.onTintCancelled()
            return
        }

        val key = RequestKey(
            owner,
            sourceUrl,
            baseColor,
            paletteTintMode,
            source
        )
        if (tasks.containsKey(key)) {
            return
        }

        val sample: Bitmap?
        try {
            sample = PreviewImageTintUtils.renderDrawableToSampleBitmap(drawable)
        } catch (e: RuntimeException) {
            callback.onTintFailed()
            return
        }
        if (sample == null) {
            callback.onTintFailed()
            return
        }

        val task = ExtractionTask(key, sample, generation, callback)
        tasks.put(key, task)

        val currentExecutor = getExecutor()
        try {
            currentExecutor.execute(task)
        } catch (firstRejection: RejectedExecutionException) {
            if (!currentExecutor.isShutdown()) {
                val oldest = currentExecutor.getQueue().poll()
                if (oldest is ExtractionTask) {
                    cancelTask(oldest)
                }
                try {
                    currentExecutor.execute(task)
                    return
                } catch (ignored: RejectedExecutionException) {
                    // The owner may have detached while the queue was being trimmed.
                }
            }
            cancelTask(task)
        }
    }

    private fun getExecutor(): ThreadPoolExecutor {
        if (executor != null && !executor!!.isShutdown()) {
            return executor!!
        }

        executor = ThreadPoolExecutor(
            1,
            1,
            15L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue<Runnable?>(MAX_PENDING_EXTRACTIONS),
            ThreadFactory { runnable: Runnable? ->
                Thread(Runnable {
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                    } catch (ignored: SecurityException) {
                        // Continue on the worker's default priority if the runtime disallows this.
                    }
                    runnable!!.run()
                }, "story-tint-extractor")
            },
            ThreadPoolExecutor.AbortPolicy()
        )
        executor!!.allowCoreThreadTimeOut(true)
        return executor!!
    }

    @MainThread
    private fun finish(
        task: ExtractionTask,
        tintColor: Int?,
        error: RuntimeException?
    ) {
        if (tasks.get(task.key) != task) {
            return
        }
        tasks.remove(task.key)

        if (task.cancelled || task.generation != generation) {
            task.callback.onTintCancelled()
        } else if (error != null || tintColor == null) {
            task.callback.onTintFailed()
        } else {
            task.callback.onTintReady(tintColor)
        }
    }

    @MainThread
    private fun cancelTask(task: ExtractionTask) {
        if (tasks.get(task.key) != task) {
            return
        }

        tasks.remove(task.key)
        task.cancel()
        task.callback.onTintCancelled()
    }

    @MainThread
    private fun cancelAll() {
        generation++
        for (task in ArrayList<ExtractionTask>(tasks.values)) {
            cancelTask(task)
        }

        if (executor != null) {
            executor!!.shutdownNow()
            executor = null
        }
    }

    private class RequestKey(
        private val owner: Any?,
        private val sourceUrl: String?,
        val baseColor: Int,
        paletteTintMode: String?,
        private val source: Source
    ) {
        val paletteTintMode: String

        init {
            this.paletteTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode)
        }

        override fun equals(`object`: Any?): Boolean {
            if (this === `object`) {
                return true
            }
            if (`object` !is RequestKey) {
                return false
            }

            val other: RequestKey = `object`
            return owner === other.owner && baseColor == other.baseColor && source == other.source && sourceUrl == other.sourceUrl
                    && paletteTintMode == other.paletteTintMode
        }

        override fun hashCode(): Int {
            var result = System.identityHashCode(owner)
            result = 31 * result + Objects.hashCode(sourceUrl)
            result = 31 * result + baseColor
            result = 31 * result + Objects.hashCode(paletteTintMode)
            result = 31 * result + source.hashCode()
            return result
        }
    }

    private inner class ExtractionTask(
        val key: RequestKey,
        private val sample: Bitmap,
        val generation: Int,
        val callback: Callback
    ) : Runnable {
        private val sampleClaimed = AtomicBoolean()

        @Volatile
        var cancelled = false

        fun cancel() {
            cancelled = true
            if (sampleClaimed.compareAndSet(false, true)) {
                recycleSample()
            }
        }

        override fun run() {
            if (!sampleClaimed.compareAndSet(false, true)) {
                return
            }

            var tintColor: Int? = null
            var failure: RuntimeException? = null
            try {
                if (!cancelled) {
                    tintColor = PreviewImageTintUtils.calculateCardTint(
                        key.baseColor,
                        sample,
                        key.paletteTintMode
                    )
                }
            } catch (e: RuntimeException) {
                failure = e
            } finally {
                recycleSample()
            }

            if (!cancelled) {
                val result = tintColor
                val error = failure
                resultHandler.post(Runnable { finish(this, result, error) })
            }
        }

        fun recycleSample() {
            if (!sample.isRecycled()) {
                sample.recycle()
            }
        }
    }

    companion object {
        private const val MAX_PENDING_EXTRACTIONS = 32
    }
}
