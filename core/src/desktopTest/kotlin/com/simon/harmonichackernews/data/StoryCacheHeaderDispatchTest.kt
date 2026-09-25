package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.cache.ArticleSnapshotService
import com.simon.harmonichackernews.cache.StoryCacheService
import com.simon.harmonichackernews.network.KtorHttpClient
import com.simon.harmonichackernews.platform.FileAccessTimeStore
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path

class StoryCacheHeaderDispatchTest {
    @Test
    fun headerReadsAndLegacySummaryWritesRunAwayFromTheCaller() = runBlocking {
        val directory = createTempDirectory("harmonic-header-")
        try {
            val files = FileStoryCacheStore(Path(directory.toString()),
                FileAccessTimeStore(InMemoryKeyValueStore()))
            val accessThreads = CopyOnWriteArrayList<Thread>()
            val observed = object : StoryCacheFileStore by files {
                override fun readText(namespace: String, key: String, charsetName: String): String? {
                    accessThreads += Thread.currentThread()
                    return files.readText(namespace, key, charsetName)
                }

                override fun write(namespace: String, key: String, value: ByteArray): Boolean {
                    accessThreads += Thread.currentThread()
                    return files.write(namespace, key, value)
                }
            }
            val repository = StoryCacheRepository(observed, InMemoryStoryCacheMetadataStore())
            val service = StoryCacheService(repository,
                ArticleSnapshotService(KtorHttpClient(client = { error("Offline") }), null), { 0L })
            repository.storeStory(42, """{"id":42,"title":"Offline header","children":[]}""", 0L)
            val caller = Thread.currentThread()
            for (legacy in listOf(false, true)) {
                if (legacy) files.remove(StoryCacheKeys.SUMMARY_NAMESPACE, "42.json")
                accessThreads.clear()
                val header = assertNotNull(service.loadStoryHeader(42))
                assertTrue(accessThreads.isNotEmpty())
                assertTrue(accessThreads.all { it !== caller })
                val accesses = accessThreads.size
                val story = Story().apply { id = 42 }
                assertTrue(header.applyTo(story))
                assertEquals("Offline header", story.title)
                assertEquals(accesses, accessThreads.size)
            }
            accessThreads.clear()
            assertNull(service.loadStoryHeader(43))
            assertTrue(accessThreads.isEmpty())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
