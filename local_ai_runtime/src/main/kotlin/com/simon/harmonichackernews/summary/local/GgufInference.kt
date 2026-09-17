package com.simon.harmonichackernews.summary.local

import com.simon.harmonichackernews.summary.LocalSummaryGenerationPolicy

/** Streaming llama.cpp inference for the downloadable GGUF model catalog. */
object GgufInference {
  init {
    System.loadLibrary("local-llama")
    nativeInitialize()
  }

  @Synchronized
  fun summarize(
    modelId: String,
    modelPath: String,
    contextTokens: Int,
    systemInstruction: String,
    text: String,
    progressCallback: LocalModelInference.ProgressCallback,
    loadCallback: LocalModelInference.LoadCallback,
  ): String {
    val loadStartedAt = System.nanoTime()
    if (!nativeLoad(modelPath, contextTokens)) {
      throw IllegalStateException(nativeError("Could not load the local model"))
    }
    loadCallback.onLoaded((System.nanoTime() - loadStartedAt) / 1_000_000L)

    try {
      val generation = LocalSummaryGenerationPolicy.configuration(modelId)
      if (!nativeStart(
          systemInstruction,
          text,
          generation.responsePrefix,
          generation.maxOutputTokens,
        )) {
        throw IllegalStateException(nativeError("Could not process the summary input"))
      }
      val response = StringBuilder(generation.responsePrefix)
      while (true) {
        val piece = nativeNextToken() ?: break
        if (piece.isNotEmpty()) {
          response.append(piece)
          val streamedSummary =
            LocalSummaryGenerationPolicy.visibleOutput(response.toString()) ?: continue
          if (streamedSummary.isNotEmpty()) {
            progressCallback.onProgress(streamedSummary)
          }
        }
      }
      nativeLastError().takeIf { it.isNotBlank() }?.let {
        throw IllegalStateException(it)
      }
      val summary = LocalSummaryGenerationPolicy.visibleOutput(response.toString())
        ?.trim()
        .orEmpty()
      if (summary.isEmpty()) {
        throw IllegalStateException(nativeError("The local model returned an empty summary"))
      }
      return summary
    } finally {
      nativeClose()
    }
  }

  private fun nativeError(fallback: String): String {
    return nativeLastError().ifBlank { fallback }
  }

  private external fun nativeInitialize()
  private external fun nativeLoad(modelPath: String, contextTokens: Int): Boolean
  private external fun nativeStart(
    systemInstruction: String,
    text: String,
    responsePrefix: String,
    outputTokens: Int,
  ): Boolean
  private external fun nativeNextToken(): String?
  private external fun nativeLastError(): String
  private external fun nativeClose()
}
