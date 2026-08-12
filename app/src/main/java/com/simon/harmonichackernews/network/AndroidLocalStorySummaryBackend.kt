package com.simon.harmonichackernews.network

import android.content.Context
import com.simon.harmonichackernews.summary.StorySummaryBackend
import com.simon.harmonichackernews.summary.StorySummaryEvent
import com.simon.harmonichackernews.summary.StorySummaryInput
import com.simon.harmonichackernews.network.SummaryUseCase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/** Bridges the distribution-specific Android local-AI implementation into the shared runtime. */
class AndroidLocalStorySummaryBackend(
    context: Context,
    private val summaryUseCase: SummaryUseCase,
) : StorySummaryBackend {
    private val appContext = context.applicationContext

    override fun summarize(input: StorySummaryInput): Flow<StorySummaryEvent> = callbackFlow {
        val callback = object : LocalSummaryCallback {
            override fun onDebugInfo(debugInfo: String?) {
                debugInfo?.let { trySend(StorySummaryEvent.DebugInfo(it)) }
            }

            override fun onProgress(summary: String?) {
                summary?.let { trySend(StorySummaryEvent.Progress(it)) }
            }

            override fun onSuccess(summary: String?) {
                trySend(StorySummaryEvent.Success(summary.orEmpty()))
                close()
            }

            override fun onFailure(error: String?) {
                trySend(StorySummaryEvent.Failure(error ?: "Unknown error"))
                close()
            }
        }
        if (input.hasArticleText) {
            LocalSummaryManager.summarizeText(appContext, input.articleText, callback)
        } else {
            launch {
                try {
                    val articleText = summaryUseCase.extractArticleText(input.articleUrl)
                    LocalSummaryManager.summarizeText(appContext, articleText, callback)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    callback.onFailure(
                        "Extraction failed: " +
                            (error.message?.takeIf(String::isNotBlank) ?: "Unknown error"),
                    )
                }
            }
        }
        awaitClose()
    }
}
