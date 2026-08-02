package com.simon.harmonichackernews

import android.content.Context

/** FOSS builds do not initialize Play split delivery.  */
class LocalAiApplicationSupportImpl : LocalAiApplicationSupport {
    override fun install(context: Context?) {
    }
}
