package com.simon.harmonichackernews.settings

import androidx.datastore.core.CorruptionException
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DataStoreFilePersistenceTest {
    @Test
    fun everyValueTypeSurvivesCloseAndReopen() = runBlocking {
        val directory = createTempDirectory("harmonic-datastore-")
        val file = directory.resolve("settings.preferences_pb").toString()
        suspend fun useStore(block: suspend (DataStoreKeyValueStore) -> Unit) {
            val job = SupervisorJob()
            try {
                val store = DataStoreKeyValueStore.openFile(file, CoroutineScope(job + Dispatchers.IO))
                block(store)
                store.awaitPersisted()
            } finally { job.cancelAndJoin() }
        }
        try {
            useStore { store ->
                store.update {
                    putString("text", "one\ntwo")
                    putBoolean("boolean", true)
                    putInt("int", 42)
                    putLong("long", 9_000_000_000L)
                    putFloat("float", 1.25f)
                    putStringSet("set", setOf("a,b", "blåbær", ""))
                }
            }
            useStore { store ->
                assertEquals("one\ntwo", store.getString("text"))
                assertTrue(store.getBoolean("boolean", false))
                assertEquals(42, store.getInt("int", 0))
                assertEquals(9_000_000_000L, store.getLong("long", 0))
                assertEquals(1.25f, store.getFloat("float", 0f))
                assertEquals(setOf("a,b", "blåbær", ""), store.getStringSet("set"))
                store.clear()
            }
            useStore { assertTrue(it.keys().isEmpty()) }
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun corruptedDataIsNeverSilentlyReplacedWithDefaults() = runBlocking {
        val directory = createTempDirectory("harmonic-datastore-corruption-")
        val file = directory.resolve("settings.preferences_pb")
        val bytes = byteArrayOf(0xff.toByte(), 0xff.toByte())
        Files.write(file, bytes)
        val job = SupervisorJob()
        try {
            assertFailsWith<CorruptionException> {
                DataStoreKeyValueStore.openFile(file.toString(), CoroutineScope(job + Dispatchers.IO))
            }
            assertTrue(bytes.contentEquals(Files.readAllBytes(file)))
        } finally { job.cancelAndJoin(); directory.toFile().deleteRecursively() }
    }
}
