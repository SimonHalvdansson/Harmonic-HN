package com.simon.harmonichackernews;

import android.content.Context;

/** FOSS builds do not initialize Play split delivery. */
public final class LocalAiApplicationSupportImpl implements LocalAiApplicationSupport {
    @Override
    public void install(Context context) {
    }
}
