package com.simon.harmonichackernews.presentation

/** One browser document can supply at most one response; old asynchronous reads cannot win. */
class CaptchaResponseGate {
    private var generation = 0
    private var reading = false
    private var consumed = false
    private var closed = false

    fun beginRead(): Int? {
        if (closed || consumed || reading) return null
        reading = true
        return generation
    }

    fun finishRead(ticket: Int, response: String?): String? {
        if (closed || consumed || !reading || ticket != generation) return null
        reading = false
        return response?.trim()?.takeIf(String::isNotEmpty)?.also { consumed = true }
    }

    fun reset() {
        generation++
        reading = false
        consumed = false
        closed = false
    }

    fun close() {
        generation++
        reading = false
        closed = true
    }
}
