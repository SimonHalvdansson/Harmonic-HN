package com.simon.harmonichackernews

import android.content.Context

/** Distribution-specific initialization for optional local-AI delivery.  */
interface LocalAiApplicationSupport {
    fun install(context: Context?)
}
