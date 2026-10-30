package com.simon.harmonichackernews

import android.content.Context

/** Completes distribution-specific setup before the application graph exposes optional AI code. */
interface LocalAiApplicationSupport {
    suspend fun install(context: Context)
}
