package com.simon.harmonichackernews.localai.llama

import android.content.Context
import com.simon.harmonichackernews.summary.local.GgufInference
import com.simon.harmonichackernews.summary.local.LocalInferenceEngine
import com.simon.harmonichackernews.summary.local.LocalModelInference
import com.simon.harmonichackernews.summary.LocalModelDefinition

/** llama.cpp implementation loaded after the local-AI runtime feature is installed. */
class LlamaInferenceEngine : LocalInferenceEngine {
  override fun summarize(
    context: Context,
    model: LocalModelDefinition,
    modelPath: String,
    contextTokens: Int,
    systemInstruction: String,
    text: String,
    progressCallback: LocalModelInference.ProgressCallback,
    loadCallback: LocalModelInference.LoadCallback,
  ): String {
    return GgufInference.summarize(
      modelId = model.id,
      modelPath = modelPath,
      contextTokens = contextTokens,
      systemInstruction = systemInstruction,
      text = text,
      progressCallback = progressCallback,
      loadCallback = loadCallback,
    )
  }
}
