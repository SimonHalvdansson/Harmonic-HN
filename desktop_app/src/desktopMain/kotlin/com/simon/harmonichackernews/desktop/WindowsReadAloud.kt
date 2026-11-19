package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.platform.ReadAloudEngine
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.COM.util.ObjectFactory
import com.sun.jna.platform.win32.COM.util.IDispatch
import com.sun.jna.platform.win32.COM.util.annotation.ComObject
import com.sun.jna.platform.win32.COM.util.annotation.ComMethod
import com.sun.jna.platform.win32.COM.util.annotation.ComProperty
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.math.ln
import kotlin.math.roundToInt

@ComObject(progId = "SAPI.SpVoice")
interface WindowsSpeechVoice : IDispatch {
    @ComMethod fun Speak(text: String, flags: Int): Int
    @ComMethod fun WaitUntilDone(milliseconds: Int): Boolean
    @ComProperty fun setRate(rate: Int)
}

/** One COM apartment for the voice's complete lifetime; never block the Compose/SWT threads. */
internal class WindowsSpeechEngine(private val callbackScope: CoroutineScope) : ReadAloudEngine {
    private val dispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Harmonic speech").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var factory: ObjectFactory? = null
    private var voice: WindowsSpeechVoice? = null
    private var apartment = false
    private var speech: Job? = null
    private var generation = 0
    private var closed = false
    override fun speak(text: String, rate: Float, completed: (String?) -> Unit) {
        if (closed) return
        val expected = ++generation
        speech?.cancel()
        speech = scope.launch {
            try {
                if (!apartment) {
                    val result = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
                    check(result.toInt() >= 0) { "COM initialization failed" }
                    apartment = true
                }
                val output = voice ?: ObjectFactory().also { factory = it }
                    .createObject(WindowsSpeechVoice::class.java).also { voice = it }
                output.setRate((ln(rate.toDouble()) / ln(2.0) * 5).roundToInt().coerceIn(-10, 10))
                // Async + purge + explicitly plain text: article markup must never become SSML.
                output.Speak(text, 1 or 2 or 16)
                while (!output.WaitUntilDone(0)) delay(100)
                callbackScope.launch { if (expected == generation && !closed) completed(null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                callbackScope.launch { if (expected == generation && !closed) completed("Windows speech is unavailable. Check installed speech voices in Windows Settings.") }
            }
        }
    }
    override fun stop() {
        generation++; speech?.cancel()
        scope.launch { runCatching { voice?.Speak("", 1 or 2 or 16) } }
    }
    override fun close() {
        if (closed) return
        closed = true; generation++; speech?.cancel()
        scope.launch {
            try { runCatching { voice?.Speak("", 1 or 2 or 16) }; factory?.disposeAll() }
            finally {
                voice = null
                if (apartment) Ole32.INSTANCE.CoUninitialize()
                scope.cancel(); dispatcher.close()
            }
        }
    }
}
