package com.simon.harmonichackernews.network

import android.content.Context
import com.simon.harmonichackernews.summary.LocalSummaryAvailability
import com.simon.harmonichackernews.summary.StorySummaryBackend
import com.simon.harmonichackernews.summary.StorySummaryEvent
import com.simon.harmonichackernews.summary.StorySummaryInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Fail-closed local summarization boundary for the FOSS distribution. */
internal object LocalSummaryManager {
    private const val UNAVAILABLE_MESSAGE = "Local AI is not included in the FOSS distribution."

    fun canAttemptLocalSummarization(): Boolean = false

    suspend fun checkLocalSummaryAvailability(context: Context?): LocalSummaryAvailability =
        LocalSummaryAvailability(false, false, UNAVAILABLE_MESSAGE)

    fun isLocalSummaryReady(context: Context?): Boolean = false
}

internal class AndroidLocalSummaryBackend(context: Context) : StorySummaryBackend {
    override fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent> = flowOf(
        StorySummaryEvent.Failure("Local AI is not included in the FOSS distribution."),
    )
}
