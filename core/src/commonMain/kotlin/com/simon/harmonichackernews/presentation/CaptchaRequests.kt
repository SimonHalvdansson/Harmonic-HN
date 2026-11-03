package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.navigation.MainCaptchaRequest
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.network.HackerNewsCaptchaChallenge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The awaiting action belongs to this scene. Native hosts only return a token or cancellation. */
class CaptchaRequests(private val navigation: MainNavigationStore) {
    private val queue = Mutex()
    private var pending: Pending? = null
    private var closed = false

    suspend fun awaitResponse(challenge: HackerNewsCaptchaChallenge): String? = queue.withLock {
        if (closed) return@withLock null
        val request = navigation.showCaptchaDialog(challenge)
        val waiting = Pending(request, CompletableDeferred())
        pending = waiting
        try {
            waiting.response.await()
        } finally {
            if (pending === waiting) {
                pending = null
                if (navigation.state.value.captchaRequest?.serial == request.serial) {
                    navigation.dismissCaptchaDialog()
                }
            }
        }
    }

    fun complete(serial: Int, response: String) {
        val current = pending?.takeIf { it.request.serial == serial } ?: return
        response.trim().takeIf(String::isNotEmpty)?.let(current.response::complete)
    }

    fun cancel(serial: Int) {
        pending?.takeIf { it.request.serial == serial }?.response?.complete(null)
    }

    fun owns(serial: Int): Boolean = pending?.request?.serial == serial

    fun close() {
        closed = true
        pending?.response?.complete(null)
    }

    private class Pending(val request: MainCaptchaRequest, val response: CompletableDeferred<String?>)
}
