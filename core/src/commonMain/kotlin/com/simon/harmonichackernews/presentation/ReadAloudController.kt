package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.platform.ReadAloudEngine
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*

data class ReadAloudState(
    val title: String = "", val url: String = "", val playing: Boolean = false,
    val index: Int = 0, val count: Int = 0, val rate: Float = 1f, val error: String? = null,
    val positionSeconds: Int = 0, val durationSeconds: Int = 0,
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
    // Speech engines expose sentence completion, but no portable media timeline. Estimate time
    // from text length and snap seeks to a word boundary within the target utterance.
    private var starts = IntArray(0)
    private var offset = 0
    private var generation = 0
    private fun seconds(characters: Int) = (characters / (CHARACTERS_PER_SECOND * state.value.rate)).roundToInt()
    private fun duration() = seconds(starts.last()).coerceAtLeast(1)

    fun command(command: JsonObject) {
        when (command["action"]?.jsonPrimitive?.contentOrNull) {
            "start" -> {
                val text = command["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (text.isBlank() || text.length > MAX_TEXT) return
                interrupt()
                chunks = split(text)
                starts = IntArray(chunks.size + 1)
                for (i in chunks.indices) starts[i + 1] = starts[i] + chunks[i].length + 1
                offset = 0
                mutableState.value = ReadAloudState(
                    title = command["title"]?.jsonPrimitive?.contentOrNull.orEmpty().take(300),
                    url = command["url"]?.jsonPrimitive?.contentOrNull.orEmpty().take(8192),
                    count = chunks.size, rate = state.value.rate, durationSeconds = duration(),
                )
                play()
            }
            "pause" -> pause()
            "play" -> play()
            "stop" -> stop()
            "next" -> moveTo(state.value.index + 1)
            "previous" -> moveTo(state.value.index - 1)
            "seek" -> {
                val target = command["seconds"]?.jsonPrimitive?.intOrNull ?: return
                if (chunks.isEmpty()) return
                val character = (target.coerceIn(0, duration()) * CHARACTERS_PER_SECOND * state.value.rate).roundToInt()
                    .coerceIn(0, starts.last() - 1)
                val match = starts.asList().binarySearch(character)
                val index = if (match >= 0) match else -match - 2
                val chunk = chunks[index]
                var word = (character - starts[index]).coerceAtMost(chunk.lastIndex)
                while (word > 0 && !chunk[word - 1].isWhitespace()) word--
                moveTo(index, word)
            }
            "rate" -> {
                val rate = command["rate"]?.jsonPrimitive?.floatOrNull ?: return
                if (!rate.isFinite() || rate !in 0.5f..2f) return
                val playing = state.value.playing
                interrupt()
                mutableState.value = state.value.copy(rate = rate, playing = false)
                if (chunks.isNotEmpty()) mutableState.value = state.value.copy(
                    positionSeconds = seconds(starts[state.value.index] + offset), durationSeconds = duration(),
                )
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
        interrupt(); chunks = emptyList(); starts = IntArray(0); offset = 0
        mutableState.value = ReadAloudState(rate = state.value.rate)
    }
    fun fail(message: String) { pause(); mutableState.value = state.value.copy(error = message) }
    fun close() { stop(); engine.close() }
    private fun interrupt() { generation++; engine.stop() }
    private fun moveTo(index: Int, character: Int = 0) {
        if (chunks.isEmpty()) return
        val playing = state.value.playing
        interrupt()
        val target = index.coerceIn(chunks.indices)
        offset = character
        mutableState.value = state.value.copy(index = target, playing = false,
            positionSeconds = seconds(starts[target] + offset), error = null)
        if (playing) play()
    }
    private fun speak() {
        val expected = ++generation
        engine.speak(chunks[state.value.index].substring(offset), state.value.rate) { error ->
            if (expected == generation && state.value.playing) {
                when {
                    error != null -> fail(error)
                    state.value.index + 1 >= chunks.size -> stop()
                    else -> {
                        offset = 0
                        val next = state.value.index + 1
                        mutableState.value = state.value.copy(index = next, positionSeconds = seconds(starts[next]))
                        speak()
                    }
                }
            }
        }
    }
    companion object {
        private const val CHARACTERS_PER_SECOND = 15f
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
