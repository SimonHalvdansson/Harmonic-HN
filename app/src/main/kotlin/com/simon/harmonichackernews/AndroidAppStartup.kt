package com.simon.harmonichackernews

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/** One application-owned initialization; cancelling an activity's wait cannot cancel startup. */
internal class AndroidAppStartup<T : Any>(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    initialize: suspend () -> T,
) {
    @Volatile private var ready: T? = null
    private val initialization = scope.async(dispatcher, start = CoroutineStart.LAZY) {
        initialize().also { ready = it }
    }

    val value: T get() = checkNotNull(ready) { "Await application startup before accessing its graph" }
    val valueOrNull: T? get() = ready

    fun start() { initialization.start() }
    suspend fun await(): T = initialization.await()
}
