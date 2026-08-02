package com.simon.harmonichackernews

import android.content.Context
import com.google.android.play.core.splitcompat.SplitCompat

/** Enables access to Play-delivered local-AI feature code.  */
class LocalAiApplicationSupportImpl : LocalAiApplicationSupport {
    override fun install(context: Context?) {
        if (context != null) SplitCompat.install(context)
    }
}
