package com.simon.harmonichackernews.settings

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class QueuedPreferenceWritesTest {
    @Test
    fun pendingRemovalsAndClearAreVisibleWhileDiskCommitIsBlocked() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val persisted = ConcurrentHashMap<String, Any>().apply {
            put("old", "disk value")
            put("removed", "disk value")
        }
        val commits = AtomicInteger()
        val preferences = fakePreferences(persisted) { updates, clear ->
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            if (clear) persisted.clear()
            updates.forEach { (key, value) -> if (value == null) persisted.remove(key) else persisted[key] = value }
            commits.incrementAndGet()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val writes = QueuedPreferenceWrites(preferences, scope)
            repeat(30) { writes.update(mapOf("new" to it)) }
            assertEquals(29, writes.read("new", -1) { -1 })
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            // These must return without waiting for the blocked disk commit.
            writes.update(mapOf("removed" to null))
            assertNull(writes.read<String?>("removed", null) { "disk value" })
            writes.update(mapOf("new" to 99), clear = true)
            assertNull(writes.read<String?>("old", null) { "disk value" })
            assertEquals(99, writes.read("new", -1) { -1 })
            assertEquals(setOf("new"), writes.keys())
            release.countDown()
            withTimeout(5_000) { while (commits.get() < 2) delay(10) }
            assertEquals(99, writes.read("new", -1) { persisted["new"] as Int })
            assertEquals(2, commits.get())
        } finally {
            release.countDown()
            scope.cancel()
        }
    }

    private fun fakePreferences(
        values: Map<String, Any>,
        commit: (Map<String, Any?>, Boolean) -> Unit,
    ): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "getAll" -> values.toMap()
            "edit" -> {
                val updates = linkedMapOf<String, Any?>()
                var cleared = false
                Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, edit, args ->
                    when (edit.name) {
                        "clear" -> { cleared = true; proxy }
                        "remove" -> { updates[args[0] as String] = null; proxy }
                        "commit" -> { commit(updates, cleared); true }
                        "apply" -> error("Content writes must not enter the lifecycle apply queue")
                        else -> { updates[args[0] as String] = args[1]; proxy }
                    }
                }
            }
            else -> error("Unexpected preference call: ${method.name}")
        }
    } as SharedPreferences
}
