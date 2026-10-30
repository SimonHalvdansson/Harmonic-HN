package com.simon.harmonichackernews

import android.content.Context

/** FOSS builds do not initialize Play split delivery.  */
class LocalAiApplicationSupportImpl : LocalAiApplicationSupport {
    override suspend fun install(context: Context) {
    }
}
