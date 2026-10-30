package com.simon.harmonichackernews

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/** One application-owned initialization; cancelling an activity's wait cannot cancel startup. */
internal class AndroidAppStartup<T : Any>(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val initialize: suspend () -> T,
) {
    @Volatile private var ready: T? = null
    private var initialization = newAttempt()

    private fun newAttempt(): Deferred<T> =
        scope.async(dispatcher, start = CoroutineStart.LAZY) {
            initialize().also { ready = it }
        }

    val value: T get() = checkNotNull(ready) { "Await application startup before accessing its graph" }
    val valueOrNull: T? get() = ready

    fun start() { synchronized(this) { initialization }.start() }
    suspend fun await(): T = synchronized(this) { initialization }.await()

    /** Explicit retries share one new attempt; active work and a ready graph are retained. */
    fun retry() {
        val attempt = synchronized(this) {
            if (ready == null && initialization.isCancelled) initialization = newAttempt()
            initialization
        }
        attempt.start()
    }
}
