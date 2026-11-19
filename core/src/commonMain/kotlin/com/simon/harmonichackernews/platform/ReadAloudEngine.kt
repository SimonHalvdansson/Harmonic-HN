package com.simon.harmonichackernews.platform

/** Host speech output. Calls and completion callbacks are serialized on the owner's dispatcher. */
interface ReadAloudEngine {
    fun speak(text: String, rate: Float, completed: (String?) -> Unit)
    fun stop()
    fun close()
}
