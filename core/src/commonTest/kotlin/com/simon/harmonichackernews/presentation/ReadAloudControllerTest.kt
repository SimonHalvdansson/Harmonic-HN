package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.platform.ReadAloudEngine
import kotlinx.serialization.json.*
import kotlin.test.*

class ReadAloudControllerTest {
    private class Speech : ReadAloudEngine {
        val spoken = mutableListOf<String>()
        val completions = mutableListOf<(String?) -> Unit>()
        override fun speak(text: String, rate: Float, completed: (String?) -> Unit) { spoken += text; completions += completed }
        override fun stop() = Unit
        override fun close() = Unit
    }
    private fun action(name: String) = buildJsonObject { put("action", name) }
    private fun start(text: String = "First sentence. Second sentence.") = buildJsonObject {
        put("action", "start"); put("text", text); put("title", "Example"); put("url", "https://example.org")
    }
    @Test fun pauseResumeRejectsStaleCompletion() {
        val engine = Speech(); val controller = ReadAloudController(engine)
        controller.command(start()); controller.pause()
        engine.completions.first()(null)
        assertEquals(0, controller.state.value.index)
        controller.play(); engine.completions.last()(null)
        assertEquals(1, controller.state.value.index)
        assertEquals(listOf("First sentence.", "First sentence.", "Second sentence."), engine.spoken)
        engine.completions.last()(null)
        assertEquals(0, controller.state.value.count)
    }
    @Test fun replacingArticleIgnoresPreviousEngineCallback() {
        val engine = Speech(); val controller = ReadAloudController(engine)
        controller.command(start()); controller.command(start("Replacement."))
        engine.completions.first()("Old failure")
        assertNull(controller.state.value.error)
        assertTrue(controller.state.value.playing)
        engine.completions.last()("Missing voice")
        assertFalse(controller.state.value.playing)
        assertEquals("Missing voice", controller.state.value.error)
    }
    @Test fun pausedSkippingAndRateDoNotStartPlayback() {
        val engine = Speech(); val controller = ReadAloudController(engine)
        controller.command(start()); controller.pause(); controller.command(action("next"))
        controller.command(buildJsonObject { put("action", "rate"); put("rate", 1.5) })
        assertFalse(controller.state.value.playing)
        assertEquals(1, controller.state.value.index)
        assertEquals(1, engine.spoken.size)
        controller.command(action("stop")); controller.play()
        assertEquals(1, engine.spoken.size)
    }
    @Test fun seekResumesAtAWordAndRejectsCallbacksFromTheOldPosition() {
        val engine = Speech()
        val controller = ReadAloudController(engine)
        controller.command(start("First. " + "second ".repeat(60) + "end."))
        val duration = controller.state.value.durationSeconds
        controller.command(buildJsonObject { put("action", "seek"); put("seconds", 15) })
        assertEquals(1, controller.state.value.index)
        assertEquals(15, controller.state.value.positionSeconds)
        assertTrue(engine.spoken.last().startsWith("second "))
        assertTrue(engine.spoken.last().length < 300)
        engine.completions.first()(null)
        assertTrue(controller.state.value.playing)
        controller.pause()
        controller.command(buildJsonObject { put("action", "rate"); put("rate", 2) })
        assertTrue(controller.state.value.durationSeconds in duration / 2..duration / 2 + 1)
        controller.command(buildJsonObject { put("action", "seek"); put("seconds", 0) })
        assertFalse(controller.state.value.playing)
        assertEquals(0, controller.state.value.index)
        controller.play()
        assertEquals("First.", engine.spoken.last())
    }
    @Test fun splitLimitsChunksWithoutBreakingSurrogates() {
        val text = "x".repeat(1799) + "😀" + "y".repeat(4000)
        val parts = ReadAloudController.split(text)
        assertTrue(parts.all { it.length <= 1800 && !it.last().isHighSurrogate() })
        assertEquals(text, parts.joinToString(""))
        assertEquals(emptyList(), ReadAloudController.split("  "))
    }
    @Test fun bridgeParsesAndroidAndDesktopResults() {
        val source = start().toString()
        assertEquals(start(), ReadAloudController.parseCommand(source))
        assertEquals(start(), ReadAloudController.parseCommand(JsonPrimitive(source).toString()))
        assertNull(ReadAloudController.parseCommand("undefined"))
        assertNull(ReadAloudController.parseCommand("null"))
        assertNull(ReadAloudController.parseCommand("{\"action\":{}}"))
    }
}
