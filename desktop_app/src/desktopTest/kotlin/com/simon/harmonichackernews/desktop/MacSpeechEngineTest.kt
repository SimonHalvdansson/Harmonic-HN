package com.simon.harmonichackernews.desktop

import com.simon.harmonichackernews.presentation.ReadAloudController
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*

class MacSpeechEngineTest {
    @Test fun installedVoiceSupportsCompletionCancellationAndControllerCommands() = runBlocking {
        if (System.getenv("HARMONIC_TEST_SPEECH") != "1" ||
            !System.getProperty("os.name").startsWith("Mac")) return@runBlocking
        val engine = MacSpeechEngine(this)
        val controller = ReadAloudController(engine)
        fun command(action: String) = controller.command(buildJsonObject { put("action", action) })
        try {
            val first = CompletableDeferred<String?>()
            engine.speak("Harmonic read aloud test.", 1.5f) { first.complete(it) }
            assertNull(withTimeout(20_000) { first.await() })
            var stale = false
            engine.speak("This sentence should stop before it finishes. ".repeat(20), 1f) { stale = true }
            delay(300)
            engine.stop()
            val resumed = CompletableDeferred<String?>()
            engine.speak("Playback resumed.", 2f) { resumed.complete(it) }
            assertNull(withTimeout(20_000) { resumed.await() })
            assertFalse(stale)

            controller.command(buildJsonObject {
                put("action", "start"); put("title", "Mac speech test")
                put("text", "First sentence is long enough to pause while it is speaking. Second sentence. Third sentence.")
            })
            delay(300)
            command("pause")
            assertFalse(controller.state.value.playing)
            val paused = controller.state.value.index
            command("next")
            assertEquals(paused + 1, controller.state.value.index)
            command("previous")
            assertEquals(paused, controller.state.value.index)
            controller.command(buildJsonObject { put("action", "rate"); put("rate", 2f) })
            command("play")
            withTimeout(30_000) {
                while (controller.state.value.count > 0 && controller.state.value.error == null) delay(100)
            }
            assertNull(controller.state.value.error)
            assertEquals(0, controller.state.value.count)
        } finally { controller.close() }
    }
}
