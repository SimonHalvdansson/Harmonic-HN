package com.simon.harmonichackernews.data

import com.simon.harmonichackernews.cache.ArticleSnapshotService
import com.simon.harmonichackernews.cache.StoryCacheService
import com.simon.harmonichackernews.network.AlgoliaCommentsParser
import com.simon.harmonichackernews.network.KtorHttpClient
import com.simon.harmonichackernews.presentation.CommentThreadStore
import com.simon.harmonichackernews.utils.CommentSorter
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class PreparedCommentThreadTest {
    private val parser = AlgoliaCommentsParser()
    private val raw = """{"id":42,"title":"A title [pdf]","text":"<p>Header &amp; text</p>","children":[
        {"id":7,"parent_id":42,"author":" Alice ","text":"<p>Hello &amp; <a href='https://example.com/long'>https://example.com/...</a></p>","created_at_i":100,"children":[
            {"id":9,"parent_id":7,"author":"child","text":"child","created_at_i":300}]},
        {"id":8,"parent_id":42,"author":"Bob","text":"<p>世界 &lt;tag&gt;</p>","created_at_i":200},
        {"id":10,"parent_id":42,"text":" null ","children":[{"id":11,"text":"Hidden below deleted parent"}]}
    ]}"""

    @Test fun bothEncodingsPreserveContentAndCurrentPreferences() = runTest {
        val prepared = parser.prepare(raw, listOf(8, 7))
        for (encoding in PreparedCommentCodec.Encoding.entries) {
            val cached = assertNotNull(PreparedCommentCodec.decode(PreparedCommentCodec.encode(prepared, encoding)))
            assertEquals(prepared, cached)
            for (ids in listOf(emptyList(), listOf(8, 7), listOf(7, 7, 8))) {
                for (blocked in listOf(emptySet(), setOf(" ALICE "), setOf("child"))) {
                    val expected = parser.parse(raw, ids, blocked)
                    val actual = cached.restore(ids, blocked)
                    assertEquals(expected.comments.map { it.toSnapshot() }, actual.comments.map { it.toSnapshot() })
                    assertEquals(expected.cacheSummary!!.encode(42), actual.cacheSummary!!.encode(42))
                    for (sorting in listOf(CommentSorter.DEFAULT, CommentSorter.NEWEST_FIRST,
                        CommentSorter.OLDEST_FIRST, CommentSorter.REPLY_COUNT)) {
                        for (collapsed in listOf(false, true)) {
                            fun store(response: com.simon.harmonichackernews.network.AlgoliaCommentsResponse) =
                                CommentThreadStore().apply {
                                    val story = Story()
                                    response.updateStoryInformation(story, 0)
                                    reset(story)
                                    replaceParsedComments(story, response.comments, sorting, collapsed)
                                }
                            val oldStore = store(parser.parse(raw, ids, blocked))
                            val newStore = store(cached.restore(ids, blocked))
                            assertEquals(oldStore.state.value, newStore.state.value)
                            newStore.setSorting(CommentSorter.DEFAULT)
                            oldStore.setSorting(CommentSorter.DEFAULT)
                            assertEquals(oldStore.state.value, newStore.state.value)
                        }
                    }
                }
            }
            val first = cached.restore()
            first.comments.first().text = "Changed by presentation"
            assertNotEquals(first.comments.first().expandedAnchorText, cached.restore().comments.first().expandedAnchorText)
            assertEquals(prepared, cached)
        }
    }

    @Test fun rejectsCorruptionVersionsAndInvalidSubtreeBoundaries() = runTest {
        val prepared = parser.prepare(raw)
        for (encoding in PreparedCommentCodec.Encoding.entries) {
            val bytes = PreparedCommentCodec.encode(prepared, encoding)
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            assertNull(PreparedCommentCodec.decode(bytes))
            for (invalid in listOf(
                prepared.copy(schemaVersion = -1),
                prepared.copy(textPreparationVersion = -1),
                prepared.copy(comments = prepared.comments.map { it.copy(subtreeEndExclusive = 0) }),
            )) assertNull(PreparedCommentCodec.decode(PreparedCommentCodec.encode(invalid, encoding)))
        }
    }

    @Test fun emptyThreadsAndPreparedTextUpdatesRemainValid() = runTest {
        val empty = parser.prepare("""{"id":42,"title":"No comments","children":[]}""")
        for (encoding in PreparedCommentCodec.Encoding.entries) {
            assertTrue(assertNotNull(PreparedCommentCodec.decode(PreparedCommentCodec.encode(empty, encoding)))
                .restore().comments.isEmpty())
        }
        val parsed = parser.prepare(raw).restore().comments.first()
        val existing = Comment().apply { text = "old" }
        com.simon.harmonichackernews.CommentListDiff.updateExistingComment(existing, parsed)
        assertSame(parsed.expandedAnchorText, existing.expandedAnchorText)
        existing.text = "new text"
        assertEquals("new text", existing.expandedAnchorText)
    }

    @Test fun offlineRebuildAndWarmRestorationDoNotReadRawJsonAgain() = runTest {
        val files = RecordingFiles()
        val repository = StoryCacheRepository(files, InMemoryStoryCacheMetadataStore())
        val service = StoryCacheService(repository,
            ArticleSnapshotService(KtorHttpClient(client = { error("Offline") }), null), { 0L })
        val original = parser.parse(raw, listOf(8, 7))
        assertTrue(repository.storeStory(42, raw, 1_000, original.cacheSummary))
        val prepared = assertNotNull(service.loadPreparedThread(42))
        assertEquals(listOf(8, 7), prepared.rankedIds)
        val rawReads = files.rawReads
        assertEquals(prepared, service.loadPreparedThread(42))
        assertEquals(rawReads, files.rawReads)
        for (invalid in listOf(byteArrayOf(1, 2),
            PreparedCommentCodec.encode(prepared.copy(schemaVersion = 0)),
            PreparedCommentCodec.encode(prepared.copy(textPreparationVersion = 0)))) {
            files.write(StoryCacheKeys.PREPARED_NAMESPACE, "42.bin", invalid)
            assertEquals(prepared, service.loadPreparedThread(42))
            assertEquals(raw, repository.loadStoryPayload(42))
        }
    }

    @Test fun rawReplacementInvalidatesPreparedAndLifecycleRemovesBoth() = runTest {
        val files = RecordingFiles()
        val metadata = InMemoryStoryCacheMetadataStore()
        val repository = StoryCacheRepository(files, metadata, maximumStories = 1)
        val parsed = parser.parsePrepared(raw)
        assertTrue(repository.storeStory(42, raw, 1_000, parsed.cacheSummary))
        assertNotNull(StoryCacheRepository(files, metadata).loadPreparedThread(42))
        files.failRemoval = true
        assertFalse(repository.storeStory(42, raw.replace("A title", "New title"), 2_000))
        assertEquals(raw, repository.loadStoryPayload(42))
        assertNotNull(repository.loadPreparedThread(42))
        files.failRemoval = false
        files.failRawWrite = true
        assertFalse(repository.storeStory(42, "replacement", 2_000))
        assertNull(repository.loadPreparedThread(42))
        assertEquals(raw, repository.loadStoryPayload(42))
        files.failRawWrite = false
        assertTrue(repository.storeStory(42, raw, 3_000, parsed.cacheSummary))
        assertTrue(repository.storeStory(43, """{"id":43,"title":"Other"}""", 4_000))
        assertNull(files.read(StoryCacheKeys.PREPARED_NAMESPACE, "42.bin"))
        assertNull(repository.loadStoryPayload(42))
        repository.storeStory(42, raw, 5_000, parsed.cacheSummary)
        repository.remove(42)
        assertNull(files.read(StoryCacheKeys.PREPARED_NAMESPACE, "42.bin"))
        repository.storeStory(42, raw, 6_000, parsed.cacheSummary)
        assertEquals(1, repository.clear())
        assertTrue(files.list(StoryCacheKeys.PREPARED_NAMESPACE).isEmpty())
        assertNull(repository.loadStoryPayload(42))
    }

    @Test fun backgroundDownloadsPrepareBeforeTheirFirstOpen() = runTest {
        val files = RecordingFiles()
        val repository = StoryCacheRepository(files, InMemoryStoryCacheMetadataStore())
        val service = StoryCacheService(repository,
            ArticleSnapshotService(KtorHttpClient(client = { error("Offline") }), null), { 1_000L })
        service.cacheStory(42, raw)
        assertNotNull(repository.loadPreparedThread(42))
        assertEquals(0, files.rawReads)
        assertEquals(parser.prepare(raw), service.loadPreparedThread(42))
        assertEquals(0, files.rawReads)
    }

    private class RecordingFiles(
        private val delegate: StoryCacheFileStore = InMemoryStoryCacheFileStore(),
    ) : StoryCacheFileStore by delegate {
        var rawReads = 0
        var failRemoval = false
        var failRawWrite = false
        override fun read(namespace: String, key: String) = delegate.read(namespace, key)
        override fun readText(namespace: String, key: String, charsetName: String): String? {
            if (namespace == StoryCacheKeys.FULL_NAMESPACE) rawReads++
            return delegate.readText(namespace, key, charsetName)
        }
        override fun write(namespace: String, key: String, value: ByteArray): Boolean =
            if (failRawWrite && namespace == StoryCacheKeys.FULL_NAMESPACE) false else delegate.write(namespace, key, value)
        override fun remove(namespace: String, key: String): Boolean =
            if (failRemoval && namespace == StoryCacheKeys.PREPARED_NAMESPACE) false else delegate.remove(namespace, key)
        override fun list(namespace: String) = delegate.list(namespace)
        override fun clear(namespace: String) = delegate.clear(namespace)
    }
}
