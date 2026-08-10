package com.simon.harmonichackernews.summary.local

import android.content.Context
import com.simon.harmonichackernews.summary.LocalSummaryPreparation

/** Streaming text summarization through an installed local-AI runtime feature. */
object LocalModelInference {
  private val inferenceLock = Any()
  private val engines = mutableMapOf<LocalModelManager.Runtime, LocalInferenceEngine>()

  fun interface ProgressCallback {
    fun onProgress(summary: String)
  }

  fun interface LoadCallback {
    fun onLoaded(loadMillis: Long)
  }

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
    val prepared = LocalSummaryPreparation.prepare(
      text = text,
      modelContextTokens = model.contextTokens,
      totalMemoryBytes = LocalModelManager.getTotalMemoryBytes(appContext),
    )

    return getEngine(model.runtime).summarize(
      context = appContext,
      model = model,
      modelPath = modelPath,
      contextTokens = prepared.contextTokens,
      systemInstruction = LocalSummaryPreparation.SYSTEM_INSTRUCTION,
      text = prepared.text,
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
}
