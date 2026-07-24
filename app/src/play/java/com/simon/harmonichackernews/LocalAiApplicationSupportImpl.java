package com.simon.harmonichackernews;

import android.content.Context;

import com.google.android.play.core.splitcompat.SplitCompat;

/** Enables access to Play-delivered local-AI feature code. */
public final class LocalAiApplicationSupportImpl implements LocalAiApplicationSupport {
    @Override
    public void install(Context context) {
        SplitCompat.install(context);
    }
}
