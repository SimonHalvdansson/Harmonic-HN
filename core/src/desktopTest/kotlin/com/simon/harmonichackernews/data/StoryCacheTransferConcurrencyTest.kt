package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.app.HarmonicPersistentStorageFactory
import com.simon.harmonichackernews.app.HarmonicStorageRoots
import com.simon.harmonichackernews.cache.ArticleSnapshotService
import com.simon.harmonichackernews.cache.StoryCacheService
import com.simon.harmonichackernews.network.KtorHttpClient
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withTimeout
import kotlinx.io.files.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class StoryCacheTransferConcurrencyTest {
    @Test
    fun duplicateArticleRequestsKeepTheirOrder() = runBlocking {
        val directory = createTempDirectory("harmonic-duplicate-article-")
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/old") {
                started.complete(Unit)
                response.await()
            }
            respond("<html>${request.url.encodedPath}</html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
        })
        try {
            val storage = HarmonicPersistentStorageFactory.create(
                HarmonicStorageRoots(Path(directory.toString()), Path(directory.toString(), "pdf")),
                InMemoryKeyValueStore(), InMemoryKeyValueStore(), nowMillis = { 1L },
            )
            val service = StoryCacheService(storage.storyCacheRepository,
                ArticleSnapshotService(KtorHttpClient(client), storage.articleSnapshotStore), { 1L })
            withTimeout(10_000) {
                val old = async { service.cacheArticle(1, "https://example.com/old") }
                started.await()
                val fresh = async(start = CoroutineStart.UNDISPATCHED) { service.cacheArticle(1, "https://example.com/new") }
                response.complete(Unit)
                assertTrue(old.await())
                assertTrue(fresh.await())
                assertEquals("<html>/new</html>", service.loadArticle(1))
                assertEquals("https://example.com/new", service.articleUrl(1))
            }
        } finally {
            response.complete(Unit)
            client.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun cacheLimitEvictionCannotBeUndoneByALateArticleResponse() = runBlocking {
        val directory = createTempDirectory("harmonic-evicted-article-")
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            started.complete(Unit)
            response.await()
            respond("<html>Evicted</html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
        })
        try {
            var clock = 0L
            val storage = HarmonicPersistentStorageFactory.create(
                HarmonicStorageRoots(Path(directory.toString()), Path(directory.toString(), "pdf")),
                InMemoryKeyValueStore(), InMemoryKeyValueStore(), nowMillis = { clock },
            )
            val service = StoryCacheService(storage.storyCacheRepository,
                ArticleSnapshotService(KtorHttpClient(client), storage.articleSnapshotStore), { clock++ })
            withTimeout(20_000) {
                service.storeStory(1, """{"id":1,"title":"Old","children":[]}""")
                val download = async { service.cacheArticle(1, "https://example.com/1") }
                started.await()
                for (id in 2..201) service.storeStory(id, """{"id":$id,"title":"New","children":[]}""")
                assertFalse(service.hasStoryPayload(1))
                response.complete(Unit)
                assertFalse(download.await())
                assertNull(service.loadArticle(1))
                assertNull(service.articleUrl(1))
                assertTrue(storage.articleSnapshotStore.list().isEmpty())
            }
        } finally {
            response.complete(Unit)
            client.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun transfersOverlapWithoutBlockingStoryWritesAndRemovalCancelsOnlyItsTransfer() = runBlocking {
        val directory = createTempDirectory("harmonic-transfer-")
        val started = Channel<Int>(Channel.UNLIMITED)
        val responses = listOf(CompletableDeferred<Unit>(), CompletableDeferred<Unit>())
        val client = HttpClient(MockEngine { request ->
            val id = request.url.encodedPath.removePrefix("/").toInt()
            started.send(id)
            responses[id - 1].await()
            respond("<html>Article $id</html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
        })
        try {
            val storage = HarmonicPersistentStorageFactory.create(
                HarmonicStorageRoots(Path(directory.toString()), Path(directory.toString(), "pdf")),
                InMemoryKeyValueStore(), InMemoryKeyValueStore(), nowMillis = { 1L },
            )
            val service = StoryCacheService(storage.storyCacheRepository,
                ArticleSnapshotService(KtorHttpClient(client), storage.articleSnapshotStore), { 1L })
            withTimeout(10_000) {
                val first = async { service.cacheArticle(1, "https://example.com/1") }
                val second = async { service.cacheArticle(2, "https://example.com/2") }
                assertEquals(setOf(1, 2), setOf(started.receive(), started.receive()))
                assertTrue(service.storeStory(3, """{"id":3,"title":"Saved while downloading","children":[]}"""))
                service.remove(1)
                assertTrue(first.isCancelled)
                assertTrue(second.isActive)
                responses.forEach { it.complete(Unit) }
                assertTrue(second.await())
                assertNull(service.loadArticle(1))
                assertEquals("<html>Article 2</html>", service.loadArticle(2))
                assertTrue(service.hasStoryPayload(3))
                assertTrue(storage.articleSnapshotStore.list().none { it.temporary })
            }
        } finally {
            responses.forEach { it.complete(Unit) }
            client.close()
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun clearingDuringTransferCannotRecreateDeletedContent() = runBlocking {
        val directory = createTempDirectory("harmonic-clear-transfer-")
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine {
            started.complete(Unit)
            response.await()
            respond("<html>Late response</html>", headers = headersOf(HttpHeaders.ContentType, "text/html"))
        })
        try {
            val storage = HarmonicPersistentStorageFactory.create(
                HarmonicStorageRoots(Path(directory.toString()), Path(directory.toString(), "pdf")),
                InMemoryKeyValueStore(), InMemoryKeyValueStore(), nowMillis = { 1L },
            )
            val service = StoryCacheService(storage.storyCacheRepository,
                ArticleSnapshotService(KtorHttpClient(client), storage.articleSnapshotStore), { 1L })
            withTimeout(10_000) {
                service.storeStory(1, """{"id":1,"title":"Story","children":[]}""")
                val download = async { service.cacheArticle(1, "https://example.com/1") }
                started.await()
                assertEquals(1, service.clear())
                assertTrue(download.isCancelled)
                response.complete(Unit)
                assertNull(service.loadArticle(1))
                assertEquals(0, service.itemCount())
                assertTrue(storage.articleSnapshotStore.list().isEmpty())
            }
        } finally {
            response.complete(Unit)
            client.close()
            directory.toFile().deleteRecursively()
        }
    }
}
