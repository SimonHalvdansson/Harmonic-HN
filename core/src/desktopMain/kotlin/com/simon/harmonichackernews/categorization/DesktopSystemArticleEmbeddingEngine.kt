package com.simon.harmonichackernews.categorization

import com.simon.harmonichackernews.platform.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class DesktopSystemArticleEmbeddingEngine(private val cacheRoot: Path) : NativeArticleEmbeddingEngine {
    private val mutex = Mutex()
    private var process: Process? = null
    private var reader: java.io.BufferedReader? = null
    private var writer: java.io.BufferedWriter? = null

    override suspend fun model(): NativeEmbeddingModel? {
        if (!System.getProperty("os.name").startsWith("Mac", true)) return null
        val result = request(buildJsonObject {})
        val signature = result["signature"]?.jsonPrimitive?.content ?: return null
        return NativeEmbeddingModel(signature, "Apple Natural Language", result.getValue("dimensions").jsonPrimitive.int)
    }

    override suspend fun embed(text: String): FloatArray = request(buildJsonObject { put("text", text) })
        .getValue("vector").jsonArray.map { it.jsonPrimitive.float }.toFloatArray()

    private suspend fun request(input: JsonObject): JsonObject = mutex.withLock {
        withContext(Dispatchers.IO) {
            withTimeout(30_000) {
                if (process?.isAlive != true) start()
                val child = checkNotNull(process)
                coroutineScope {
                    var completed = false
                    val guard = launch { try { awaitCancellation() } finally { if (!completed) child.destroyForcibly() } }
                    try {
                        writer!!.apply { write(input.toString()); newLine(); flush() }
                        val line = runInterruptible { reader!!.readLine() } ?: error("System embedding helper stopped")
                        Json.parseToJsonElement(line).jsonObject.also { check(it["error"] == null) { "System embedding failed" }; completed = true }
                    } finally { guard.cancel() }
                }
            }
        }
    }

    private fun start() {
        val name = "harmonic-embeddings"
        val bytes = javaClass.classLoader.getResourceAsStream("native/$name")?.use { it.readBytes() }
            ?: error("System embeddings are not included in this build")
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val directory = cacheRoot.resolve("sentence-embeddings").resolve(hash.take(16))
        Files.createDirectories(directory)
        val executable = directory.resolve(name)
        if (!Files.exists(executable)) {
            val temp = Files.createTempFile(directory, name, ".tmp")
            try {
                Files.write(temp, bytes)
                check(temp.toFile().setExecutable(true, true))
                Files.move(temp, executable, StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temp) }
        }
        process = ProcessBuilder(executable.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        reader = process!!.inputStream.bufferedReader()
        writer = process!!.outputStream.bufferedWriter()
    }

    override suspend fun close() = mutex.withLock {
        process?.destroyForcibly()
        process = null
        reader = null
        writer = null
    }
}
