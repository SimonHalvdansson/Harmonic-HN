package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.summary.LocalSummaryAvailability
import com.simon.harmonichackernews.summary.LocalSummaryPreparation
import kotlinx.coroutines.suspendCancellableCoroutine

/** Completion object used instead of exporting a Kotlin suspend function to Swift. */
interface IosNativeSummaryCallback {
    fun complete(summary: String?, errorMessage: String?)
}

/** Small Swift-facing bridge for a system-managed on-device language model. */
interface IosNativeSummaryBridge {
    fun canAttempt(): Boolean
    fun isAvailable(): Boolean
    fun availabilityMessage(): String?
    fun summarize(
        text: String,
        instruction: String,
        callback: IosNativeSummaryCallback,
    )
}

/** Adapts Apple's asynchronous native model API to Harmonic's shared summary contract. */
class IosNativeLocalSummaryEngine(
    private val bridge: IosNativeSummaryBridge,
) : LocalSummaryEngine {
    override fun canAttempt(): Boolean = bridge.canAttempt()

    override suspend fun availability(): LocalSummaryAvailability = LocalSummaryAvailability(
        available = bridge.isAvailable(),
        downloadableFallbackRequired = false,
        statusMessage = bridge.availabilityMessage(),
    )

    override suspend fun isAvailable(): Boolean = bridge.isAvailable()

    override fun isReady(): Boolean = bridge.isAvailable()

    override suspend fun summarize(request: SummaryRequest): SummaryResult {
        val content = LocalSummaryPreparation.prepareManagedText(request.text)
        require(LocalSummaryPreparation.isLongEnough(content)) {
            "Article is too short for local summarization"
        }
        check(bridge.isAvailable()) {
            bridge.availabilityMessage() ?: "Apple Intelligence is unavailable"
        }
        return suspendCancellableCoroutine { continuation ->
            bridge.summarize(
                text = content,
                instruction = LocalSummaryPreparation.SYSTEM_INSTRUCTION,
                callback = object : IosNativeSummaryCallback {
                    override fun complete(summary: String?, errorMessage: String?) {
                        if (!continuation.isActive) return
                        val completed = summary?.trim().orEmpty()
                        if (errorMessage.isNullOrBlank() && completed.isNotBlank()) {
                            continuation.resumeWith(
                                Result.success(
                                    SummaryResult(
                                        text = completed,
                                        debugInfo = "Apple Intelligence · system model",
                                    ),
                                ),
                            )
                        } else {
                            continuation.resumeWith(
                                Result.failure(
                                    IllegalStateException(
                                        errorMessage?.takeIf(String::isNotBlank)
                                            ?: "Apple Intelligence returned an empty summary",
                                    ),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }
}
