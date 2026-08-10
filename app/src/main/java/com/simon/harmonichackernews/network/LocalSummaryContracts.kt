package com.simon.harmonichackernews.network

import android.os.Handler
import android.os.Looper

interface LocalSummaryCallback {
    fun onProgress(summary: String?) = Unit
    fun onDebugInfo(debugInfo: String?) = Unit
    fun onSuccess(summary: String?)
    fun onFailure(error: String?)
}

fun interface LocalSummaryAvailabilityCallback {
    fun onResult(
        available: Boolean,
        downloadableFallbackRequired: Boolean,
        statusMessage: String?,
    )
}

/** Main-thread delivery for callbacks emitted by Android's local model implementations. */
internal object LocalSummaryCallbacks {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun success(callback: LocalSummaryCallback?, summary: String?) = post(callback) {
        it.onSuccess(summary)
    }

    fun progress(callback: LocalSummaryCallback?, summary: String?) = post(callback) {
        it.onProgress(summary)
    }

    fun debugInfo(callback: LocalSummaryCallback?, value: String?) = post(callback) {
        it.onDebugInfo(value)
    }

    fun failure(callback: LocalSummaryCallback?, error: String?) = post(callback) {
        it.onFailure(error)
    }

    fun availability(
        callback: LocalSummaryAvailabilityCallback?,
        available: Boolean,
        downloadableFallbackRequired: Boolean,
        statusMessage: String?,
    ) {
        callback ?: return
        mainHandler.post {
            callback.onResult(available, downloadableFallbackRequired, statusMessage)
        }
    }

    fun errorMessage(error: Throwable?): String =
        error?.message?.takeUnless(String::isEmpty) ?: "Unknown error"

    private fun post(callback: LocalSummaryCallback?, action: (LocalSummaryCallback) -> Unit) {
        callback ?: return
        mainHandler.post { action(callback) }
    }
}
