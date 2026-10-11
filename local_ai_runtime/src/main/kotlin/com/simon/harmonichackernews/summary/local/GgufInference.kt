package com.simon.harmonichackernews.summary.local

import com.simon.harmonichackernews.summary.LocalSummaryGenerationPolicy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Streaming llama.cpp inference for the downloadable GGUF model catalog. */
object GgufInference {
  init {
    System.loadLibrary("local-llama")
    nativeInitialize()
  }

  private val mutex = Mutex()

  suspend fun summarize(
    modelId: String,
    modelPath: String,
    contextTokens: Int,
    systemInstruction: String,
    text: String,
    progressCallback: LocalModelInference.ProgressCallback,
    loadCallback: LocalModelInference.LoadCallback,
  ): String = mutex.withLock {
    withContext(Dispatchers.IO) {
      coroutineScope {
        ensureActive()
        nativeResetCancellation()
        // Native prefill can spend seconds inside one call. A sibling signals its atomic
        // abort flag as soon as the caller cancels, including before the first visible token.
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
          try { awaitCancellation() } finally { nativeCancel() }
        }
        try {
          val loadStartedAt = System.nanoTime()
          val loaded = nativeLoad(modelPath, contextTokens)
          ensureActive()
          check(loaded) { nativeError("Could not load the local model") }
          loadCallback.onLoaded((System.nanoTime() - loadStartedAt) / 1_000_000L)
          generate(modelId, systemInstruction, text, progressCallback)
        } finally {
          withContext(NonCancellable) {
            cancellation.cancelAndJoin()
            nativeClose()
          }
        }
      }
    }
  }

  private suspend fun generate(
    modelId: String,
    systemInstruction: String,
    text: String,
    progressCallback: LocalModelInference.ProgressCallback,
  ): String {
    val context = currentCoroutineContext()
    val generation = LocalSummaryGenerationPolicy.configuration(modelId)
    context.ensureActive()
    val started = nativeStart(
      systemInstruction,
      text,
      generation.responsePrefix,
      generation.maxOutputTokens,
    )
    context.ensureActive()
    check(started) { nativeError("Could not process the summary input") }
    val response = StringBuilder(generation.responsePrefix)
    while (true) {
      context.ensureActive()
      val piece = nativeNextToken()
      context.ensureActive()
      if (piece == null) break
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
  }

  private fun nativeError(fallback: String): String {
    return nativeLastError().ifBlank { fallback }
  }

  private external fun nativeInitialize()
  private external fun nativeResetCancellation()
  private external fun nativeCancel()
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
