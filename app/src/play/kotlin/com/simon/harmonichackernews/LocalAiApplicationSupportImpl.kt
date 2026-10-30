package com.simon.harmonichackernews

import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Enables access to Play-delivered local-AI feature code.  */
class LocalAiApplicationSupportImpl : LocalAiApplicationSupport {
    override suspend fun install(context: Context): Unit = withContext(Dispatchers.IO) {
        // This is awaited during process startup, before any optional feature class is loaded.
        SplitCompat.install(context.applicationContext)
    }
}
