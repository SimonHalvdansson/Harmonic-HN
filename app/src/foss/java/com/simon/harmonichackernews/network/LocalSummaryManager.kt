package com.simon.harmonichackernews.network

import android.content.Context
import com.simon.harmonichackernews.summary.StorySummaryBackend
import com.simon.harmonichackernews.summary.StorySummaryEvent
import com.simon.harmonichackernews.summary.StorySummaryInput
import com.simon.harmonichackernews.summary.LocalModelService
import com.simon.harmonichackernews.platform.LocalSummaryEngine
import com.simon.harmonichackernews.platform.PlatformCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal class AndroidLocalSummaryBackend(context: Context) : StorySummaryBackend {
    override fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent> = flowOf(
        StorySummaryEvent.Failure("Local AI is not included in the FOSS distribution."),
    )
}

internal fun createAndroidLocalSummaryCapability(
    context: Context,
    models: LocalModelService,
): PlatformCapability<LocalSummaryEngine> = PlatformCapability.Unavailable(
    name = "Local summaries",
    reason = "Local AI is not included in the FOSS distribution.",
)
