package com.simon.harmonichackernews.summary.local

/** Streaming llama.cpp inference for the downloadable GGUF model catalog. */
object GgufInference {
  private const val MAX_OUTPUT_TOKENS = 256

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
      val responsePrefix =
        if (LocalModelManager.MODEL_QWEN_08B == modelId) "- " else ""
      if (!nativeStart(systemInstruction, text, responsePrefix, MAX_OUTPUT_TOKENS)) {
        throw IllegalStateException(nativeError("Could not process the summary input"))
      }
      val response = StringBuilder(responsePrefix)
      while (true) {
        val piece = nativeNextToken() ?: break
        if (piece.isNotEmpty()) {
          response.append(piece)
          val streamedSummary = visibleSummary(response.toString()) ?: continue
          if (streamedSummary.isNotEmpty()) {
            progressCallback.onProgress(streamedSummary)
          }
        }
      }
      nativeLastError().takeIf { it.isNotBlank() }?.let {
        throw IllegalStateException(it)
      }
      val summary = visibleSummary(response.toString())?.trim().orEmpty()
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

  private fun visibleSummary(rawResponse: String): String? {
    val trimmed = rawResponse.trimStart()
    if ("<think>".startsWith(trimmed)) {
      return null
    }
    if (trimmed.startsWith("<think>")) {
      val end = trimmed.indexOf("</think>")
      if (end < 0) {
        return null
      }
      return trimmed.substring(end + "</think>".length).trimStart()
    }
    return trimmed
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
