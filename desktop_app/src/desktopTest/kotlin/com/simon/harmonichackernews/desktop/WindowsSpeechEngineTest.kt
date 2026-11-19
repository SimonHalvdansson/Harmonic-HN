package com.simon.harmonichackernews.desktop

import kotlinx.coroutines.*
import kotlin.test.*

class WindowsSpeechEngineTest {
    @Test fun installedVoiceCompletesAndCanBeStopped() = runBlocking {
        if (System.getenv("HARMONIC_TEST_SPEECH") != "1" ||
            !System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) return@runBlocking
        val engine = WindowsSpeechEngine(this)
        try {
            val result = CompletableDeferred<String?>()
            engine.speak("Harmonic read aloud test.", 1.5f) { result.complete(it) }
            assertNull(withTimeout(20000) { result.await() })
            var stale = false
            engine.speak("This sentence should stop before it finishes. ".repeat(10), 1f) { stale = true }
            delay(300); engine.stop()
            delay(300)
            assertFalse(stale)
            val resumed = CompletableDeferred<String?>()
            engine.speak("Playback resumed.", 1f) { resumed.complete(it) }
            assertNull(withTimeout(20000) { resumed.await() })
        } finally { engine.close() }
    }
}
