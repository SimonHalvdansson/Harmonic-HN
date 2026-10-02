package com.simon.harmonichackernews.summary

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Native handles stay in Swift; callbacks can arrive from the runtime's inference worker. */
interface IosLiteRtSummaryCallback {
    fun loaded(milliseconds: Long)
    fun progress(summary: String)
    fun complete(summary: String?, errorMessage: String?)
}

interface IosLiteRtSummaryTask {
    fun cancel()
}

interface IosLiteRtSummaryBridge {
    fun start(
        modelPath: String,
        cacheDirectory: String,
        contextTokens: Int,
        text: String,
        instruction: String,
        callback: IosLiteRtSummaryCallback,
    ): IosLiteRtSummaryTask
}

internal class IosLiteRtInference(
    private val bridge: IosLiteRtSummaryBridge,
    private val cacheDirectory: String,
    private val totalMemoryBytes: () -> Long,
) {
    private val mutex = Mutex()

    suspend fun summarize(
        model: LocalModelDefinition,
        modelPath: String,
        instruction: String,
        text: String,
        onProgress: (String) -> Unit,
        onLoaded: (Long) -> Unit,
    ): String = mutex.withLock {
        val prepared = LocalSummaryPreparation.prepare(text, model.contextTokens, totalMemoryBytes())
        withTimeout(10 * 60_000L) {
            suspendCancellableCoroutine { continuation ->
                val task = bridge.start(
                    modelPath, cacheDirectory, prepared.contextTokens, prepared.text, instruction,
                    object : IosLiteRtSummaryCallback {
                        override fun loaded(milliseconds: Long) {
                            if (continuation.isActive) onLoaded(milliseconds)
                        }

                        override fun progress(summary: String) {
                            if (continuation.isActive) onProgress(summary)
                        }

                        override fun complete(summary: String?, errorMessage: String?) {
                            if (!continuation.isActive) return
                            val result = summary?.trim().orEmpty()
                            continuation.resumeWith(
                                if (errorMessage.isNullOrBlank() && result.isNotBlank()) {
                                    Result.success(result)
                                } else {
                                    Result.failure(IllegalStateException(
                                        errorMessage?.takeIf(String::isNotBlank)
                                            ?: "The local model returned an empty summary",
                                    ))
                                },
                            )
                        }
                    },
                )
                continuation.invokeOnCancellation { task.cancel() }
            }
        }
    }
}
