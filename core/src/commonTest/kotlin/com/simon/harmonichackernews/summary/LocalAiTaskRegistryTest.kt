package com.simon.harmonichackernews.summary

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAiTaskRegistryTest {
    @Test
    fun memoryTrimClosesActiveWorkWithoutCancellingItsCaller() = runTest {
        val registry = LocalAiTaskRegistry()
        var closed = false
        val result = async {
            runCatching { registry.run { try { awaitCancellation() } finally { closed = true } } }
        }
        runCurrent()
        registry.trimMemory()
        val failure = result.await().exceptionOrNull()
        assertIs<IllegalStateException>(failure)
        assertTrue(failure.message.orEmpty().contains("free memory"))
        assertTrue(closed)
        assertTrue(currentCoroutineContext().isActive)
        assertEquals("new request", registry.run { "new request" })
    }

    @Test
    fun trimsEveryLocalRequestAndLeavesUnrelatedTasksRunning() = runTest {
        val registry = LocalAiTaskRegistry()
        val unrelated = launch { awaitCancellation() }
        val tasks = List(2) { async { runCatching { registry.run { awaitCancellation() } } } }
        runCurrent()
        registry.trimMemory()
        tasks.forEach { assertIs<IllegalStateException>(it.await().exceptionOrNull()) }
        assertTrue(unrelated.isActive)
        unrelated.cancelAndJoin()
    }

    @Test
    fun normalUserCancellationIsPreserved() = runTest {
        val registry = LocalAiTaskRegistry()
        var closed = false
        val task = launch { registry.run { try { awaitCancellation() } finally { closed = true } } }
        runCurrent()
        task.cancelAndJoin()
        assertTrue(closed)
        assertEquals(42, registry.run { 42 })
    }
}
