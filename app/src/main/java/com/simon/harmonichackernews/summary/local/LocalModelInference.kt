package com.simon.harmonichackernews.summary.local

import android.content.Context
import kotlin.math.max
import kotlin.math.min

/** Streaming text summarization through an installed local-AI runtime feature. */
object LocalModelInference {
  private val inferenceLock = Any()
  private val engines = mutableMapOf<LocalModelManager.Runtime, LocalInferenceEngine>()
  private const val LOW_MEMORY_THRESHOLD_BYTES = 8L * 1024L * 1024L * 1024L
  private const val LOW_MEMORY_MAX_WORDS = 500
  private const val DEFAULT_MAX_WORDS = 1500
  private const val LOW_MEMORY_MAX_CONTEXT_TOKENS = 2048
  private const val CONTEXT_OUTPUT_HEADROOM_TOKENS = 512
  private const val ESTIMATED_TOKENS_PER_WORD = 2

  fun interface ProgressCallback {
    fun onProgress(summary: String)
  }

  fun interface LoadCallback {
    fun onLoaded(loadMillis: Long)
  }

  private const val SYSTEM_INSTRUCTION =
    "Summarize the article as a concise, information-dense bullet-point list. " +
      "Focus on the key takeaways and noteworthy facts. Keep the entire summary under 500 " +
      "characters where possible. Return only the summary in Markdown, with no preamble."

  @JvmStatic
  fun summarize(
    context: Context,
    text: String,
    progressCallback: ProgressCallback,
    loadCallback: LoadCallback,
  ): String = synchronized(inferenceLock) {
    summarizeLocked(context, text, progressCallback, loadCallback)
  }

  private fun summarizeLocked(
    context: Context,
    text: String,
    progressCallback: ProgressCallback,
    loadCallback: LoadCallback,
  ): String {
    val appContext = context.applicationContext
    val model = LocalModelManager.getSelectedModel(appContext)
    if (!LocalAiRuntimeManager.isRuntimeInstalled(appContext, model.runtime)) {
      throw IllegalStateException(
        "${LocalAiRuntimeManager.getRuntimeLabel(model.runtime)} is not installed",
      )
    }

    val modelPath = LocalModelManager.getSelectedModelPath(appContext)
    val lowMemory =
      LocalModelManager.getTotalMemoryBytes(appContext) in 1 until LOW_MEMORY_THRESHOLD_BYTES
    val contextTokens =
      if (lowMemory) min(model.contextTokens, LOW_MEMORY_MAX_CONTEXT_TOKENS)
      else model.contextTokens
    val contextWordBudget =
      max(250, (contextTokens - CONTEXT_OUTPUT_HEADROOM_TOKENS) / ESTIMATED_TOKENS_PER_WORD)
    val preferredMaxWords = if (lowMemory) LOW_MEMORY_MAX_WORDS else DEFAULT_MAX_WORDS
    val preparedText = truncateWords(text, min(preferredMaxWords, contextWordBudget))

    return getEngine(model.runtime).summarize(
      context = appContext,
      model = model,
      modelPath = modelPath,
      contextTokens = contextTokens,
      systemInstruction = SYSTEM_INSTRUCTION,
      text = preparedText,
      progressCallback = progressCallback,
      loadCallback = loadCallback,
    )
  }

  private fun getEngine(runtime: LocalModelManager.Runtime): LocalInferenceEngine {
    return engines.getOrPut(runtime) {
      val className = LocalAiRuntimeManager.getEngineClassName(runtime)
      try {
        Class.forName(className)
          .getDeclaredConstructor()
          .newInstance() as LocalInferenceEngine
      } catch (exception: ReflectiveOperationException) {
        throw IllegalStateException(
          "Could not load ${LocalAiRuntimeManager.getRuntimeLabel(runtime)}",
          exception,
        )
      }
    }
  }

  private fun truncateWords(text: String, maxWords: Int): String {
    val words = text.trim().split(Regex("\\s+"))
    if (words.size <= maxWords) {
      return text.trim()
    }
    return words.take(maxWords).joinToString(" ")
  }
}
