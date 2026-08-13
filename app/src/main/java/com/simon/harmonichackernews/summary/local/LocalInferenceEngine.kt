package com.simon.harmonichackernews.summary.local

import android.content.Context
import com.simon.harmonichackernews.summary.LocalModelDefinition

/** Contract implemented by each downloadable local-AI runtime feature. */
interface LocalInferenceEngine {
  fun summarize(
    context: Context,
    model: LocalModelDefinition,
    modelPath: String,
    contextTokens: Int,
    systemInstruction: String,
    text: String,
    progressCallback: LocalModelInference.ProgressCallback,
    loadCallback: LocalModelInference.LoadCallback,
  ): String
}
