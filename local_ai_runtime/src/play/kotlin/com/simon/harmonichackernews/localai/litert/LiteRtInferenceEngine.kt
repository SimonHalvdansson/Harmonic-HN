package com.simon.harmonichackernews.localai.litert

import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.simon.harmonichackernews.summary.local.LocalInferenceEngine
import com.simon.harmonichackernews.summary.local.LocalModelInference
import com.simon.harmonichackernews.summary.LocalModelDefinition
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/** LiteRT-LM implementation loaded after the local-AI runtime feature is installed. */
/** LiteRT-LM implementation included only in Play-capable distributions. */
class LiteRtInferenceEngine : LocalInferenceEngine {
  override suspend fun summarize(
    context: Context,
    model: LocalModelDefinition,
    modelPath: String,
    contextTokens: Int,
    systemInstruction: String,
    text: String,
    progressCallback: LocalModelInference.ProgressCallback,
    loadCallback: LocalModelInference.LoadCallback,
  ): String {
    var engine: Engine? = null
    var conversation: com.google.ai.edge.litertlm.Conversation? = null
    try {
      val loadStartedAt = System.nanoTime()
      engine = createInitializedEngine(
        context = context,
        modelPath = modelPath,
        maxTokens = contextTokens,
        preferGpu = !isEmulator(),
      )
      currentCoroutineContext().ensureActive()
      loadCallback.onLoaded((System.nanoTime() - loadStartedAt) / 1_000_000L)
      conversation =
        engine.createConversation(
          ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 0.3),
          )
        )
      val responseText = StringBuilder()
      try {
        withTimeout(INFERENCE_TIMEOUT_MINUTES * 60_000) {
          conversation.sendMessageAsync(text).collect { message ->
            responseText.append(message.toString())
            progressCallback.onProgress(responseText.toString().trimStart())
          }
        }
      } finally {
        conversation.cancelProcess()
      }
      val summary = responseText.toString().trim()
      if (summary.isEmpty()) {
        throw IllegalStateException("Local model returned an empty summary")
      }
      return summary
    } finally {
      try {
        conversation?.close()
      } finally {
        engine?.close()
      }
    }
  }

  private fun createInitializedEngine(
    context: Context,
    modelPath: String,
    maxTokens: Int,
    preferGpu: Boolean,
  ): Engine = initializeEngineWithCpuFallback(
    preferGpu = preferGpu,
    create = { gpu ->
      Engine(
        EngineConfig(
          modelPath = modelPath,
          backend = if (gpu) Backend.GPU() else Backend.CPU(),
          maxNumTokens = maxTokens,
          cacheDir = context.cacheDir.absolutePath,
        )
      )
    },
    initialize = Engine::initialize,
    isInitialized = { it.isInitialized() },
  )

  private fun isEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic") ||
      Build.FINGERPRINT.contains("emulator") ||
      Build.HARDWARE.contains("goldfish") ||
      Build.HARDWARE.contains("ranchu")
  }

  private companion object {
    const val INFERENCE_TIMEOUT_MINUTES = 10L
  }
}

internal fun <T : AutoCloseable> initializeEngineWithCpuFallback(
  preferGpu: Boolean,
  create: (gpu: Boolean) -> T,
  initialize: (T) -> Unit,
  isInitialized: (T) -> Boolean,
): T {
  if (preferGpu) {
    var gpuEngine: T? = null
    try {
      gpuEngine = create(true)
      initialize(gpuEngine)
      return gpuEngine
    } catch (_: Exception) {
      // LiteRT rejects close() until initialization has succeeded. Closing a failed GPU engine
      // would throw here and prevent the CPU fallback from ever being created.
      if (gpuEngine != null && isInitialized(gpuEngine)) gpuEngine.close()
    }
  }
  return create(false).also(initialize)
}
