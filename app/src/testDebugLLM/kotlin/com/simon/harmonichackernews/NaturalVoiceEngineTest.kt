package com.simon.harmonichackernews

import com.simon.harmonichackernews.platform.ReadAloudEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.*
import org.junit.Test

class NaturalVoiceEngineTest {
    private class Voice : NaturalSpeech {
        var preparations = 0
        val ready = CompletableDeferred<Boolean>()
        val finished = CompletableDeferred<Boolean>()
        val requests = mutableListOf<String>()
        override suspend fun prepare(): Boolean {
            preparations++
            return ready.await()
        }
        override suspend fun speak(text: String): Boolean {
            requests += text
            return finished.await()
        }
        override fun stop() = Unit
        override fun close() = Unit
    }

    private class SystemVoice : ReadAloudEngine {
        val requests = mutableListOf<Pair<String, Float>>()
        override fun speak(text: String, rate: Float, completed: (String?) -> Unit) {
            requests += text to rate
            completed(null)
        }
        override fun stop() = Unit
        override fun close() = Unit
    }

    private val natural = Voice()
    private val system = SystemVoice()
    private var preferNatural = true
    private val engine = NaturalVoiceEngine(system, { preferNatural }, natural, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
    private var completions = 0
    private fun speak(text: String, rate: Float = 1f) = engine.speak(text, rate) { assertNull(it); completions++ }

    @Test fun systemPreferenceAndCustomRatesBypassMlKit() {
        preferNatural = false
        speak("first")
        preferNatural = true
        speak("second", 1.5f)
        assertEquals(listOf("first" to 1f, "second" to 1.5f), system.requests)
        assertEquals(0, natural.preparations)
    }

    @Test fun unavailableVoiceFallsBackForTheSession() {
        natural.ready.complete(false)
        speak("first")
        speak("second")
        assertEquals(listOf("first" to 1f, "second" to 1f), system.requests)
        assertEquals(1, natural.preparations)
    }

    @Test fun naturalPlaybackAdvancesOnlyAfterCompletion() {
        natural.ready.complete(true)
        speak("sentence")
        assertEquals(0, completions)
        natural.finished.complete(true)
        assertEquals(1, completions)
        assertTrue(system.requests.isEmpty())
    }

    @Test fun failedSynthesisReplaysCurrentSentenceAndKeepsReading() {
        natural.ready.complete(true)
        speak("first")
        natural.finished.complete(false)
        speak("second")
        assertEquals(listOf("first"), natural.requests)
        assertEquals(listOf("first" to 1f, "second" to 1f), system.requests)
    }

    @Test fun stopDuringPreparationNeverSpeaksTheInterruptedSentence() {
        speak("old")
        engine.stop()
        natural.ready.complete(true)
        speak("new")
        assertEquals(listOf("new"), natural.requests)
        engine.close()
        natural.finished.complete(false)
        assertEquals(0, completions)
        assertTrue(system.requests.isEmpty())
    }
}
