package com.simon.harmonichackernews.summary

object AiSummaryAvailabilityPolicy {
    const val MODE_LOCAL = "local"

    fun canProvideSummary(
        explicitlyEnabled: Boolean?,
        mode: String,
        localAvailable: Boolean,
        cloudApiKeyAvailable: Boolean,
    ): Boolean {
        if (explicitlyEnabled == false) return false
        return if (mode == MODE_LOCAL) localAvailable else cloudApiKeyAvailable
    }

    fun isEnabled(
        explicitlyEnabled: Boolean?,
        localAvailable: Boolean,
        cloudApiKeyAvailable: Boolean,
    ): Boolean = explicitlyEnabled ?: (localAvailable || cloudApiKeyAvailable)
}
