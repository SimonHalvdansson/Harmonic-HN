package com.simon.harmonichackernews.network

import android.content.Context
import com.simon.harmonichackernews.network.SummaryManager.LocalSummaryAvailabilityCallback
import com.simon.harmonichackernews.network.SummaryManager.SummaryCallback
import com.simon.harmonichackernews.network.SummaryManager.postFailure
import com.simon.harmonichackernews.network.SummaryManager.postLocalAvailability

/** Fail-closed local summarization boundary for the FOSS distribution.  */
internal object LocalSummaryManager {
    private const val UNAVAILABLE_MESSAGE = "Local AI is not included in the FOSS distribution."

    fun canAttemptLocalSummarization(): Boolean = false

    fun checkLocalSummaryAvailability(
        context: Context?,
        callback: LocalSummaryAvailabilityCallback?,
    ) {
        postLocalAvailability(callback, false, false, UNAVAILABLE_MESSAGE)
    }

    fun summarizeArticle(
        context: Context?,
        articleUrl: String?,
        callback: SummaryCallback?,
    ) {
        postFailure(callback, UNAVAILABLE_MESSAGE)
    }

    fun summarizeText(
        context: Context?,
        text: String?,
        callback: SummaryCallback?,
    ) {
        postFailure(callback, UNAVAILABLE_MESSAGE)
    }

    fun isLocalSummaryReady(context: Context?): Boolean = false

    fun isLocalSummaryConfigurationKnown(context: Context?): Boolean = true
}
