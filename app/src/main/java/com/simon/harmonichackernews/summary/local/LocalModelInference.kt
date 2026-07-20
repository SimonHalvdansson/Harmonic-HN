package com.simon.harmonichackernews.summary.local

import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/** Streaming text summarization using the selected downloaded local model. */
object LocalModelInference {
  private val inferenceLock = Any()
  private const val LOW_MEMORY_THRESHOLD_BYTES = 8L * 1024L * 1024L * 1024L
  private const val LOW_MEMORY_MAX_WORDS = 500
  private const val DEFAULT_MAX_WORDS = 1500
  private const val LOW_MEMORY_MAX_CONTEXT_TOKENS = 2048
  private const val CONTEXT_OUTPUT_HEADROOM_TOKENS = 512
  private const val ESTIMATED_TOKENS_PER_WORD = 2
  private const val INFERENCE_TIMEOUT_MINUTES = 10L

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

    if (model.runtime == LocalModelManager.Runtime.LLAMA_CPP) {
      return GgufInference.summarize(
        modelId = model.id,
        modelPath = modelPath,
        contextTokens = contextTokens,
        systemInstruction = SYSTEM_INSTRUCTION,
        text = preparedText,
        progressCallback = progressCallback,
        loadCallback = loadCallback,
      )
    }

    var engine: Engine? = null
    var conversation: com.google.ai.edge.litertlm.Conversation? = null
    try {
      val loadStartedAt = System.nanoTime()
      engine = createInitializedEngine(
        context = appContext,
        modelPath = modelPath,
        maxTokens = contextTokens,
        preferGpu = !isEmulator(),
      )
      loadCallback.onLoaded((System.nanoTime() - loadStartedAt) / 1_000_000L)
      conversation =
        engine.createConversation(
          ConversationConfig(
            systemInstruction = Contents.of(SYSTEM_INSTRUCTION),
            samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 0.3),
          )
        )
      val responseText = StringBuilder()
      val failure = AtomicReference<Throwable?>()
      val completed = CountDownLatch(1)
      conversation.sendMessageAsync(
        preparedText,
        object : MessageCallback {
          override fun onMessage(message: Message) {
            responseText.append(message.toString())
            progressCallback.onProgress(responseText.toString().trimStart())
          }

          override fun onDone() {
            completed.countDown()
          }

          override fun onError(throwable: Throwable) {
            failure.set(throwable)
            completed.countDown()
          }
        },
      )
      if (!completed.await(INFERENCE_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
        conversation.cancelProcess()
        throw IllegalStateException("Local model inference timed out")
      }
      failure.get()?.let { throw it }
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
  ): Engine {
    if (preferGpu) {
      var gpuEngine: Engine? = null
      try {
        gpuEngine =
          Engine(
            EngineConfig(
              modelPath = modelPath,
              backend = Backend.GPU(),
              maxNumTokens = maxTokens,
              cacheDir = context.cacheDir.absolutePath,
            )
          )
        gpuEngine.initialize()
        return gpuEngine
      } catch (_: Exception) {
        gpuEngine?.close()
      }
    }

    val cpuEngine =
      Engine(
        EngineConfig(
          modelPath = modelPath,
          backend = Backend.CPU(),
          maxNumTokens = maxTokens,
          cacheDir = context.cacheDir.absolutePath,
        )
      )
    cpuEngine.initialize()
    return cpuEngine
  }

  private fun truncateWords(text: String, maxWords: Int): String {
    val words = text.trim().split(Regex("\\s+"))
    if (words.size <= maxWords) {
      return text.trim()
    }
    return words.take(maxWords).joinToString(" ")
  }

  private fun isEmulator(): Boolean {
    return Build.FINGERPRINT.startsWith("generic") ||
      Build.FINGERPRINT.contains("emulator") ||
      Build.HARDWARE.contains("goldfish") ||
      Build.HARDWARE.contains("ranchu")
  }
}
