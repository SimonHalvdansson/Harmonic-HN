package com.simon.harmonichackernews.summary.local

import android.content.Context
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.LocalRuntimeInstallState
import com.simon.harmonichackernews.summary.LocalRuntimeInstallStatus

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

    fun getStatus(context: Context, runtime: LocalModelRuntime): LocalRuntimeInstallStatus =
        LocalRuntimeInstallStatus(
            state = LocalRuntimeInstallState.NOT_INSTALLED,
            runtime = runtime,
            error = UNAVAILABLE_MESSAGE,
        )

    fun isRuntimeInstalled(
        context: Context,
        runtime: LocalModelRuntime
    ): Boolean {
        return false
    }

    fun requestRuntimeAndModelDownload(context: Context, modelId: String): String? {
        return UNAVAILABLE_MESSAGE
    }

    fun cancelRuntimeInstall(
        context: Context,
        runtime: LocalModelRuntime
    ) {
    }

    fun getRuntimeLabel(runtime: LocalModelRuntime): String = when (runtime) {
        LocalModelRuntime.GEMINI_NANO -> "Gemini Nano"
        else -> "local AI runtime"
    }

    fun getEngineClassName(runtime: LocalModelRuntime): String {
        throw IllegalStateException("Local AI is not included in the FOSS distribution.")
    }

    fun interface StatusListener {
        fun onRuntimeStatusChanged()
    }

}
