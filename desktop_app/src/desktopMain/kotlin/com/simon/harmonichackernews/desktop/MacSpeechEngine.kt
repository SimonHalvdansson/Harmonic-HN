package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.platform.ReadAloudEngine
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/** macOS's system speech service, using stdin so article text never becomes shell arguments. */
internal class MacSpeechEngine(private val callbackScope: CoroutineScope) : ReadAloudEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var process: Process? = null
    private var generation = 0
    private var closed = false

    override fun speak(text: String, rate: Float, completed: (String?) -> Unit) {
        val expected = synchronized(lock) {
            if (closed) return
            stop()
            generation
        }
        scope.launch {
            var output: Process? = null
            val error = try {
                output = ProcessBuilder("/usr/bin/say", "-r", (175 * rate).roundToInt().toString(), "-f", "-")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                synchronized(lock) {
                    if (closed || expected != generation) {
                        output.destroyForcibly()
                    } else {
                        process = output
                    }
                }
                output.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                if (output.waitFor() == 0) null else UNAVAILABLE
            } catch (_: Exception) {
                UNAVAILABLE
            } finally {
                output?.destroyForcibly()
                synchronized(lock) { if (process === output) process = null }
            }
            callbackScope.launch {
                if (synchronized(lock) { !closed && expected == generation }) completed(error)
            }
        }
    }

    override fun stop() = synchronized(lock) {
        generation++
        process?.destroyForcibly()
        process = null
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            stop()
        }
        scope.cancel()
    }

    private companion object {
        const val UNAVAILABLE = "macOS speech is unavailable. Check installed speech voices in System Settings."
    }
}
