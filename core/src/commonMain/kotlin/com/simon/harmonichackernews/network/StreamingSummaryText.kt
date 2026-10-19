package com.simon.harmonichackernews.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Copies growing text only when publishing it, with an immediate first token and bounded cadence. */
internal class StreamingSummaryText(
    private val scope: CoroutineScope,
    private val maximumChars: Int,
    private val onProgress: suspend (String) -> Unit,
) {
    private val mutex = Mutex()
    private val text = StringBuilder()
    private var pending = false
    private var publisher: Job? = null

    suspend fun append(chunk: String) {
        if (chunk.isEmpty()) return
        mutex.withLock {
            if (chunk.length > maximumChars - text.length) {
                throw CloudSummaryException("Summary exceeded the response limit")
            }
            text.append(chunk)
            pending = true
            if (publisher == null) {
                publish()
                publisher = scope.launch(start = CoroutineStart.LAZY) {
                    while (true) {
                        delay(50)
                        val keepPublishing = mutex.withLock {
                            if (pending) {
                                publish()
                                true
                            } else {
                                publisher = null
                                false
                            }
                        }
                        if (!keepPublishing) break
                    }
                }.also(Job::start)
            }
        }
    }

    /** The terminal result includes pending text without delaying completion or emitting afterward. */
    suspend fun finish(): String = mutex.withLock {
        publisher?.cancel()
        publisher = null
        pending = false
        text.toString()
    }

    private suspend fun publish() {
        onProgress(text.toString())
        pending = false
    }
}
