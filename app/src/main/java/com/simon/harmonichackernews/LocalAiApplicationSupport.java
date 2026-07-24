package com.simon.harmonichackernews;

import android.content.Context;

/** Distribution-specific initialization for optional local-AI delivery. */
public interface LocalAiApplicationSupport {
    void install(Context context);
}
