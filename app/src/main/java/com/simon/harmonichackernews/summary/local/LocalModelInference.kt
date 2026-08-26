package com.simon.harmonichackernews.summary.local

import android.content.Context
import com.simon.harmonichackernews.summary.LocalSummaryPreparation
import com.simon.harmonichackernews.summary.LocalModelRuntime
import com.simon.harmonichackernews.summary.LocalModelService

/** Streaming text summarization through an installed local-AI runtime feature. */
class LocalModelInference(
  context: Context,
  private val models: LocalModelService,
) {
  private val appContext = context.applicationContext
  private val inferenceLock = Any()
  private val engines = mutableMapOf<LocalModelRuntime, LocalInferenceEngine>()

  fun interface ProgressCallback {
    fun onProgress(summary: String)
  }

  fun interface LoadCallback {
    fun onLoaded(loadMillis: Long)
  }

  fun summarize(
    text: String,
    systemInstruction: String = LocalSummaryPreparation.SYSTEM_INSTRUCTION,
    progressCallback: ProgressCallback,
    loadCallback: LoadCallback,
  ): String = synchronized(inferenceLock) {
    summarizeLocked(text, systemInstruction, progressCallback, loadCallback)
  }

  private fun summarizeLocked(
    text: String,
    systemInstruction: String,
    progressCallback: ProgressCallback,
    loadCallback: LoadCallback,
  ): String {
    val model = models.selectedModel
    if (!models.isRuntimeInstalled(model.runtime)) {
      throw IllegalStateException(
        "${models.runtimeLabel(model.runtime)} is not installed",
      )
    }

    val modelPath = models.installedPath()
    val prepared = LocalSummaryPreparation.prepare(
      text = text,
      modelContextTokens = model.contextTokens,
      totalMemoryBytes = androidTotalMemoryBytes(appContext),
    )

    return getEngine(model.runtime).summarize(
      context = appContext,
      model = model,
      modelPath = modelPath,
      contextTokens = prepared.contextTokens,
      systemInstruction = systemInstruction
        .takeIf(String::isNotBlank)
        ?: LocalSummaryPreparation.SYSTEM_INSTRUCTION,
      text = prepared.text,
      progressCallback = progressCallback,
      loadCallback = loadCallback,
    )
  }

  private fun getEngine(
    runtime: LocalModelRuntime,
  ): LocalInferenceEngine {
    return engines.getOrPut(runtime) {
      val className = models.engineClassName(runtime)
        ?: throw IllegalStateException("${models.runtimeLabel(runtime)} has no inference engine")
      try {
        Class.forName(className)
          .getDeclaredConstructor()
          .newInstance() as LocalInferenceEngine
      } catch (exception: ReflectiveOperationException) {
        throw IllegalStateException(
          "Could not load ${models.runtimeLabel(runtime)}",
          exception,
        )
      }
    }
  }
}
