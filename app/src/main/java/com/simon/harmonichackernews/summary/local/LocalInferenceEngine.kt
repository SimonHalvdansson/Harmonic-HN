package com.simon.harmonichackernews.summary.local

import android.content.Context

/** Contract implemented by each downloadable local-AI runtime feature. */
interface LocalInferenceEngine {
  fun summarize(
    context: Context,
    model: LocalModelManager.ModelInfo,
    modelPath: String,
    contextTokens: Int,
    systemInstruction: String,
    text: String,
    progressCallback: LocalModelInference.ProgressCallback,
    loadCallback: LocalModelInference.LoadCallback,
  ): String
}
