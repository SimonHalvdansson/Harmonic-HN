package com.simon.harmonichackernews.ui.content

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class CommentContentLruEntriesTest {
    @Test
    fun emptySingletonAndDuplicateOperationsPreserveTheInstalledValue() {
        val cache = CommentContentLruEntries<Key, Value>()
        assertEquals(null, cache[Key(1)])
        assertEquals(null, cache.remove(Key(1)))
        assertFalse(cache.isNotEmpty())

        val first = Value(1)
        cache.install(Key(1), first)
        repeat(3) { assertSame(first, cache[Key(1)]) }
        cache.install(Key(1), Value(2))
        assertSame(first, cache.peek(Key(1)))
        assertEquals(Key(1), cache.oldestKey())
        assertEquals(1, cache.size)
        assertSame(first, cache.remove(Key(1)))
        assertFalse(cache.isNotEmpty())

        cache.install(Key(2), Value(2))
        cache.clear()
        cache.clear()
        cache.install(Key(3), Value(3))
        assertEquals(Key(3), cache.oldestKey())
        assertEquals(1, cache.size)
    }

    @Test
    fun randomizedOperationsMatchAListBasedRecencyOracle() {
        repeat(10) { seed ->
            val random = Random(seed)
            val cache = CommentContentLruEntries<Key, Value>()
            val expected = mutableListOf<Pair<Key, Value>>()
            repeat(2_000) { step ->
                val key = Key(random.nextInt(24))
                val index = expected.indexOfFirst { it.first == key }
                when (random.nextInt(10)) {
                    0, 1, 2 -> {
                        val value = Value(step)
                        cache.install(key, value)
                        if (index < 0) expected.add(key to value)
                    }
                    3, 4 -> {
                        val entry = if (index < 0) null else expected.removeAt(index)
                        if (entry != null) expected.add(entry)
                        assertSame(entry?.second, cache[key])
                    }
                    5 -> assertSame(expected.getOrNull(index)?.second, cache.peek(key))
                    6 -> {
                        val removed = if (index < 0) null else expected.removeAt(index).second
                        assertSame(removed, cache.remove(key))
                    }
                    7 -> if (expected.isNotEmpty()) {
                        val removed = expected.removeAt(0)
                        assertEquals(removed.first, cache.oldestKey())
                        assertSame(removed.second, cache.remove(cache.oldestKey()))
                    }
                    8 -> if (expected.isNotEmpty()) {
                        val existing = expected[random.nextInt(expected.size)]
                        cache.install(Key(existing.first.id), Value(-1))
                    }
                    9 -> if (step % 97 == 0) {
                        cache.clear()
                        expected.clear()
                    }
                }
                assertEquals(expected.size, cache.size, "seed $seed, step $step")
                assertEquals(expected.map { it.first }.toSet(), cache.keys)
                if (expected.isNotEmpty()) assertEquals(expected.first().first, cache.oldestKey())
                for ((expectedKey, expectedValue) in expected) {
                    assertSame(expectedValue, cache.peek(Key(expectedKey.id)))
                }
            }
            // Draining checks the complete order, including both ends and every interior link.
            for ((key, value) in expected) {
                assertEquals(key, cache.oldestKey())
                assertSame(value, cache.remove(key))
            }
            assertFalse(cache.isNotEmpty())
            cache.install(Key(25), Value(25))
            assertEquals(Key(25), cache.oldestKey())
        }
    }

    // Collisions and separate equal key instances exercise key lookup independently of node identity.
    private data class Key(val id: Int) {
        override fun hashCode(): Int = id % 3
    }

    private data class Value(val revision: Int)
}
