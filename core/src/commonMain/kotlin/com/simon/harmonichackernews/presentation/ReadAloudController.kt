package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.platform.ReadAloudEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

data class ReadAloudState(
    val title: String = "", val url: String = "", val playing: Boolean = false,
    val index: Int = 0, val count: Int = 0, val rate: Float = 1f, val error: String? = null,
) {
    fun json(): String = buildJsonObject {
        put("title", title); put("url", url); put("playing", playing)
        put("index", index); put("count", count); put("rate", rate); put("error", error)
    }.toString()
}

/** Owns an article independently of any browser document, including stale speech callbacks. */
class ReadAloudController(private val engine: ReadAloudEngine) {
    private val mutableState = MutableStateFlow(ReadAloudState())
    val state = mutableState.asStateFlow()
    private var chunks = emptyList<String>()
    private var generation = 0

    fun command(command: JsonObject) {
        when (command["action"]?.jsonPrimitive?.contentOrNull) {
            "start" -> {
                val text = command["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (text.isBlank() || text.length > MAX_TEXT) return
                interrupt()
                chunks = split(text)
                mutableState.value = ReadAloudState(
                    title = command["title"]?.jsonPrimitive?.contentOrNull.orEmpty().take(300),
                    url = command["url"]?.jsonPrimitive?.contentOrNull.orEmpty().take(8192),
                    count = chunks.size, rate = state.value.rate,
                )
                play()
            }
            "pause" -> pause()
            "play" -> play()
            "stop" -> stop()
            "next" -> seek(1)
            "previous" -> seek(-1)
            "rate" -> {
                val rate = command["rate"]?.jsonPrimitive?.floatOrNull ?: return
                if (!rate.isFinite() || rate !in 0.5f..2f) return
                val playing = state.value.playing
                interrupt()
                mutableState.value = state.value.copy(rate = rate, playing = false)
                if (playing) play()
            }
        }
    }

    fun play() {
        if (chunks.isEmpty() || state.value.playing) return
        mutableState.value = state.value.copy(playing = true, error = null)
        speak()
    }
    fun pause() { interrupt(); mutableState.value = state.value.copy(playing = false) }
    fun stop() {
        interrupt(); chunks = emptyList()
        mutableState.value = ReadAloudState(rate = state.value.rate)
    }
    fun fail(message: String) { pause(); mutableState.value = state.value.copy(error = message) }
    fun close() { stop(); engine.close() }
    private fun interrupt() { generation++; engine.stop() }
    private fun seek(delta: Int) {
        if (chunks.isEmpty()) return
        val playing = state.value.playing
        interrupt()
        mutableState.value = state.value.copy(index = (state.value.index + delta).coerceIn(chunks.indices), playing = false)
        if (playing) play()
    }
    private fun speak() {
        val expected = ++generation
        engine.speak(chunks[state.value.index], state.value.rate) { error ->
            if (expected == generation && state.value.playing) {
                when {
                    error != null -> fail(error)
                    state.value.index + 1 >= chunks.size -> stop()
                    else -> { mutableState.value = state.value.copy(index = state.value.index + 1); speak() }
                }
            }
        }
    }
    companion object {
        const val MAX_TEXT = 250_000
        // Sentence-sized chunks give predictable resume/skip even on engines without pause.
        fun split(text: String): List<String> = buildList {
            for (paragraph in text.trim().split(Regex("(?<=[.!?])\\s+|\\n+"))) {
                var remaining = paragraph.trim()
                while (remaining.length > 1800) {
                    var end = remaining.lastIndexOf(' ', 1800).takeIf { it > 0 } ?: 1800
                    if (remaining[end - 1].isHighSurrogate()) end--
                    add(remaining.take(end)); remaining = remaining.drop(end).trimStart()
                }
                if (remaining.isNotBlank()) add(remaining)
            }
        }
        fun parseCommand(raw: String?): JsonObject? = runCatching {
            if (raw == null || raw.length > MAX_TEXT * 6 + 20_000) return null
            val value = Json.parseToJsonElement(raw)
            val obj = (if (value is JsonPrimitive && value.isString) Json.parseToJsonElement(value.content) else value) as? JsonObject ?: return null
            if (obj.values.any { it !is JsonPrimitive }) return null
            obj
        }.getOrNull()
    }
}
