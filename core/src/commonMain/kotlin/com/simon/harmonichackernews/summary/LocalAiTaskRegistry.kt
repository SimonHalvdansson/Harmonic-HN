package com.simon.harmonichackernews.summary

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope

/** Cancels native-AI requests without cancelling their UI/session owners or unrelated work. */
class LocalAiTaskRegistry {
    private class Entry(val task: Deferred<*>) { val pressure = MutableStateFlow(false) }
    private val entries = MutableStateFlow<Set<Entry>>(emptySet())

    suspend fun <T> run(block: suspend () -> T): T = supervisorScope {
        val task = async(start = CoroutineStart.LAZY) { block() }
        val entry = Entry(task)
        entries.update { it + entry }
        try {
            task.await()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (entry.pressure.value) throw IllegalStateException("Local AI stopped to free memory. Try again when more memory is available.")
            throw cancelled
        } finally {
            entries.update { it - entry }
        }
    }

    fun trimMemory() {
        entries.value.forEach { entry ->
            entry.pressure.value = true
            entry.task.cancel(CancellationException("Local AI memory pressure"))
        }
    }
}
