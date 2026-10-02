package com.simon.harmonichackernews.summary

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Uses the official JVM artifact, including its platform-native runtime. */
internal class DesktopLiteRtInference(private val cacheDirectory: String) {
    private val mutex = Mutex()

    companion object {
        // The official JVM artifact ships these exact native targets (no Intel macOS binary).
        val supported: Boolean = run {
            val os = System.getProperty("os.name").lowercase()
            val arch = System.getProperty("os.arch").lowercase()
            val arm = arch in setOf("aarch64", "arm64")
            val x64 = arch in setOf("x86_64", "amd64")
            (os.startsWith("mac") && arm) || (os.startsWith("windows") && x64) ||
                (os.startsWith("linux") && (arm || x64))
        }
    }

    suspend fun summarize(
        model: LocalModelDefinition,
        modelPath: String,
        systemInstruction: String,
        text: String,
        onProgress: (String) -> Unit,
        onLoaded: (Long) -> Unit,
    ): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            val prepared = LocalSummaryPreparation.prepare(text, model.contextTokens, Long.MAX_VALUE)
            val engine = Engine(EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(),
                maxNumTokens = prepared.contextTokens,
                cacheDir = cacheDirectory,
            ))
            val started = System.nanoTime()
            // close() is valid only after successful native initialization.
            engine.initialize()
            engine.use {
                currentCoroutineContext().ensureActive()
                onLoaded((System.nanoTime() - started) / 1_000_000L)
                engine.createConversation(ConversationConfig(
                    systemInstruction = Contents.of(systemInstruction),
                    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = 0.3),
                )).use { conversation ->
                    val response = StringBuilder()
                    try {
                        withTimeout(10 * 60_000L) {
                            conversation.sendMessageAsync(prepared.text).collect { message ->
                                response.append(message.toString())
                                onProgress(response.toString().trimStart())
                            }
                        }
                    } finally { conversation.cancelProcess() }
                    response.toString().trim().also {
                        check(it.isNotEmpty()) { "The local model returned an empty summary" }
                    }
                }
            }
        }
    }
}
