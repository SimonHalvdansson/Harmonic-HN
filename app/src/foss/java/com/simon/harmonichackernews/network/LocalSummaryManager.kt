package com.simon.harmonichackernews.network

import android.content.Context

/** Fail-closed local summarization boundary for the FOSS distribution.  */
internal object LocalSummaryManager {
    private const val UNAVAILABLE_MESSAGE = "Local AI is not included in the FOSS distribution."

    fun canAttemptLocalSummarization(): Boolean = false

    fun checkLocalSummaryAvailability(
        context: Context?,
        callback: LocalSummaryAvailabilityCallback?,
    ) {
        LocalSummaryCallbacks.availability(callback, false, false, UNAVAILABLE_MESSAGE)
    }

    fun summarizeArticle(
        context: Context?,
        articleUrl: String?,
        callback: LocalSummaryCallback?,
    ) {
        LocalSummaryCallbacks.failure(callback, UNAVAILABLE_MESSAGE)
    }

    fun summarizeText(
        context: Context?,
        text: String?,
        callback: LocalSummaryCallback?,
    ) {
        LocalSummaryCallbacks.failure(callback, UNAVAILABLE_MESSAGE)
    }

    fun isLocalSummaryReady(context: Context?): Boolean = false

    fun isLocalSummaryConfigurationKnown(context: Context?): Boolean = true
}
