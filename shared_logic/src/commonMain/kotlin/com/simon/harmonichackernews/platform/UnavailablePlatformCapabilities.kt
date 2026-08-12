package com.simon.harmonichackernews.platform

/** A platform capability is deliberately unavailable, rather than silently emulated. */
class PlatformCapabilityUnavailableException(
    val capability: String,
    val reason: String,
) : IllegalStateException("$capability is unavailable: $reason")

/** Explicit local-summary implementation for platforms that have no on-device model runtime. */
class UnavailableLocalSummaryEngine(
    val reason: String,
) : LocalSummaryEngine {
    override suspend fun isAvailable(): Boolean = false

    override suspend fun summarize(request: SummaryRequest): SummaryResult {
        throw PlatformCapabilityUnavailableException("Local summaries", reason)
    }
}
