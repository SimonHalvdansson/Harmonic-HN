package com.simon.harmonichackernews.summary

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal class DesktopAppleIntelligence(private val cacheRoot: Path) {
    @Volatile var available: Boolean = false
        private set
    @Volatile var status: String = "Checking Apple Intelligence availability…"
        private set

    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (!isMac) return@withContext
        try {
            withTimeout(15_000) {
                runHelper(listOf("--availability"), null) { event ->
                    available = event["available"]?.jsonPrimitive?.booleanOrNull == true
                    status = event["message"]?.jsonPrimitive?.contentOrNull ?: "Apple Intelligence is unavailable"
                }
            }
        } catch (_: TimeoutCancellationException) {
            available = false
            status = "Apple Intelligence availability check timed out"
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            available = false
            status = error.message ?: "Apple Intelligence is unavailable"
        }
    }

    suspend fun summarize(instruction: String, text: String, onProgress: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            withTimeout(10 * 60_000L) {
                var result = ""
                var completed = false
                val input = buildJsonObject {
                    put("instruction", instruction)
                    put("text", LocalSummaryPreparation.prepare(text, 4096, Long.MAX_VALUE).text)
                }.toString()
                runHelper(emptyList(), input) { event ->
                    event["error"]?.jsonPrimitive?.contentOrNull?.let { error(it) }
                    event["text"]?.jsonPrimitive?.contentOrNull?.let {
                        result = it
                        onProgress(it)
                    }
                    completed = event["done"]?.jsonPrimitive?.booleanOrNull == true || completed
                }
                check(completed && result.isNotBlank()) { "Apple Intelligence returned no summary" }
                result.trim()
            }
        }

    private suspend fun runHelper(
        arguments: List<String>,
        input: String?,
        onEvent: (kotlinx.serialization.json.JsonObject) -> Unit,
    ) = coroutineScope {
        val process = ProcessBuilder(listOf(executable().toString()) + arguments)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        // Closing the process pipes releases a blocking read even on JVMs that ignore interrupts.
        val cancellation = launch(Dispatchers.IO) {
            try { awaitCancellation() } finally { process.destroyForcibly() }
        }
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                if (input != null) { writer.write(input); writer.newLine() }
            }
            process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = runInterruptible(Dispatchers.IO) { reader.readLine() } ?: break
                    onEvent(Json.parseToJsonElement(line).jsonObject)
                }
            }
            check(runInterruptible(Dispatchers.IO) { process.waitFor() } == 0) {
                "Apple Intelligence helper failed"
            }
        } finally {
            cancellation.cancel()
            process.destroyForcibly()
        }
    }

    private fun executable(): Path {
        check(isMac) { "Apple Intelligence is only available on macOS" }
        val name = "harmonic-foundation-models"
        val bytes = javaClass.classLoader.getResourceAsStream("native/$name")?.use { it.readBytes() }
            ?: error("Apple Intelligence is not included in this desktop build")
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val directory = cacheRoot.resolve("foundation-models").resolve(digest.take(16))
        Files.createDirectories(directory)
        val target = directory.resolve(name)
        if (!Files.isRegularFile(target) || Files.size(target) != bytes.size.toLong()) {
            val temporary = Files.createTempFile(directory, name, ".tmp")
            try {
                Files.write(temporary, bytes)
                check(temporary.toFile().setExecutable(true, true)) { "Cannot prepare Apple Intelligence helper" }
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        }
        return target
    }

    companion object {
        val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
        val model = LocalModelDefinition(
            "apple-intelligence", "Apple Intelligence", "System managed", "",
            LocalModelBrand.SYSTEM, "", "", 0L, false,
            LocalModelRuntime.APPLE_FOUNDATION_MODELS, 4096,
        )
    }
}
