package com.simon.harmonichackernews.summary.local

import android.content.Context

/**
 * Defensive no-op runtime delivery implementation for the FOSS distribution.
 * 
 * 
 * The FOSS UI does not expose local summarization, but keeping this boundary explicit makes
 * stale preferences and shared main-source code fail closed without linking Play Feature Delivery.
 */
object LocalAiRuntimeManager {
    private const val UNAVAILABLE_MESSAGE = "Local AI is not included in the FOSS distribution."

    fun isLocalAiIncluded(): Boolean = false

    fun addStatusListener(context: Context, listener: StatusListener) {
        listener.onRuntimeStatusChanged()
    }

    fun removeStatusListener(listener: StatusListener) {
    }

    fun getStatus(context: Context, runtime: LocalModelManager.Runtime): Status {
        return Status(runtime)
    }

    fun isRuntimeInstalled(
        context: Context,
        runtime: LocalModelManager.Runtime
    ): Boolean {
        return false
    }

    fun requestRuntimeAndModelDownload(context: Context, modelId: String): String? {
        return UNAVAILABLE_MESSAGE
    }

    fun cancelRuntimeInstall(
        context: Context,
        runtime: LocalModelManager.Runtime
    ) {
    }

    fun getRuntimeLabel(runtime: LocalModelManager.Runtime): String = when (runtime) {
        LocalModelManager.Runtime.GEMINI_NANO -> "Gemini Nano"
        else -> "local AI runtime"
    }

    fun getEngineClassName(runtime: LocalModelManager.Runtime): String {
        throw IllegalStateException("Local AI is not included in the FOSS distribution.")
    }

    fun interface StatusListener {
        fun onRuntimeStatusChanged()
    }

    enum class State {
        NOT_INSTALLED,
        PENDING,
        DOWNLOADING,
        INSTALLING,
        INSTALLED,
        FAILED,
        CANCELED
    }

    class Status internal constructor(val runtime: LocalModelManager.Runtime) {
        val state: State = State.NOT_INSTALLED
        val bytesDownloaded: Long = 0L
        val totalBytes: Long = 0L
        val error: String = UNAVAILABLE_MESSAGE
        val pendingModelId: String = ""
        val sessionId: Int = 0

        val isActive: Boolean
            get() = false

        val progressPercent: Int
            get() = 0
    }
}
