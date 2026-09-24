package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.presentation.SubmissionFilter
import com.simon.harmonichackernews.presentation.SubmissionsListStore
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.navigation.toStory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlgoliaSubmissionsRepositoryTest {
    @Test
    fun commentNavigationSeedShowsCommentImmediatelyAndRetainsParentStory() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"hits":[{"objectID":"42","_tags":["comment","author_alice"],"author":"alice","comment_text":"A reply we already have","story_title":"Parent story","story_url":"https://example.com/article","story_id":10,"parent_id":11,"created_at_i":123}],"page":0,"nbPages":1}""")
        })
        try {
            val comment = KtorAlgoliaRepository(client).getSubmissions("alice", 100).items.single()
            val seededHeader = comment.toDestination().toStory()
            assertEquals(42, seededHeader.id)
            assertEquals("Comment by alice", seededHeader.title)
            assertEquals("A reply we already have", seededHeader.text)
            assertEquals("alice", seededHeader.by)
            assertEquals(123, seededHeader.createdAtEpochSeconds)
            assertTrue(seededHeader.loaded)
            assertTrue(seededHeader.isComment)
            assertFalse(seededHeader.isLink)
            val parent = seededHeader.toRootStory()!!
            assertEquals(10, parent.id)
            assertEquals("Parent story", parent.title)
            assertEquals("https://example.com/article", parent.url)
            assertTrue(parent.isLink)
        } finally {
            client.close()
        }
    }

    @Test
    fun combinesAuthorWithRequestedTypeAndIncludesPolls() = runTest {
        val expectedTags = listOf("author_alice,(story,poll,comment)", "author_alice,(story,poll)", "author_alice,comment")
        val requests = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            assertEquals("/api/v1/search_by_date", request.url.encodedPath)
            assertEquals("100", request.url.parameters["hitsPerPage"])
            requests += request.url.parameters["tags"]
            respond("""{"hits":[{"objectID":"42","_tags":["poll","author_alice"],"title":"A poll","author":"alice"}],"page":0,"nbPages":1}""")
        })
        try {
            val repository = KtorAlgoliaRepository(client)
            for (type in AlgoliaSubmissionType.entries) {
                val page = repository.getSubmissions("alice", 100, type)
                assertEquals(42, page.items.single().id)
                assertFalse(page.items.single().isComment)
                assertFalse(page.canLoadMore)
            }
            assertEquals(expectedTags, requests.filterNotNull())
        } finally {
            client.close()
        }
    }

    @Test
    fun paginationUsesServerMetadataInsteadOfDecodedHitCount() = runTest {
        var response = """{"hits":[{"objectID":"1","_tags":["story"]}],"page":0,"nbPages":1}"""
        val client = HttpClient(MockEngine { respond(response) })
        try {
            val repository = KtorAlgoliaRepository(client)
            assertFalse(repository.getSubmissions("alice", 1).canLoadMore)
            response = """{"hits":[{"objectID":"invalid"}],"page":0,"nbPages":2}"""
            assertTrue(repository.getSubmissions("alice", 1).canLoadMore)
            response = """{"hits":[],"page":0,"nbPages":0}"""
            val empty = repository.getSubmissions("alice", 100, AlgoliaSubmissionType.STORIES)
            assertTrue(empty.items.isEmpty())
            assertFalse(empty.canLoadMore)
        } finally {
            client.close()
        }
    }

    @Test
    fun countOnlyRequestPreservesFullTotalAndItsAccuracy() = runTest {
        var exact = true
        val client = HttpClient(MockEngine { request ->
            assertEquals("0", request.url.parameters["hitsPerPage"])
            assertEquals("0", request.url.parameters["page"])
            respond("""{"hits":[],"nbHits":9985,"nbPages":0,"exhaustiveNbHits":$exact}""")
        })
        try {
            val repository = KtorAlgoliaRepository(client)
            val count = repository.getSubmissions("pg", 0, AlgoliaSubmissionType.COMMENTS)
            assertEquals(AlgoliaSubmissionCount(9985, true), count.totalCount)
            assertTrue(count.items.isEmpty())
            assertFalse(count.canLoadMore)
            exact = false
            assertEquals(AlgoliaSubmissionCount(9985, false),
                repository.getSubmissions("pg", 0, AlgoliaSubmissionType.COMMENTS).totalCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun approximateCountAtTheCapDoesNotPrematurelyEndHistory() = runTest {
        val client = HttpClient(MockEngine {
            val hits = (1000 downTo 1).joinToString(",") { id ->
                """{"objectID":"$id","author":"alice","created_at_i":$id}"""
            }
            respond("""{"hits":[$hits],"page":0,"nbPages":1,"nbHits":1000,"exhaustiveNbHits":false}""")
        })
        try {
            val page = KtorAlgoliaRepository(client).getSubmissions("alice", 1000)
            assertEquals(AlgoliaSubmissionsCursor(throughEpochSeconds = 1), page.nextCursor)
            assertEquals(AlgoliaSubmissionCount(1000, false), page.totalCount)
        } finally {
            client.close()
        }
    }

    @Test
    fun readsBeyondThousandHitsWithoutGapsOrDuplicatesAcrossTimestampTies() = runTest {
        val original = (2105 downTo 1).toList()
        var available = original
        val requests = mutableListOf<Pair<Int, Int?>>()
        val client = HttpClient(MockEngine { request ->
            val pageSize = request.url.parameters["hitsPerPage"]!!.toInt()
            val page = request.url.parameters["page"]!!.toInt()
            val through = request.url.parameters["numericFilters"]?.removePrefix("created_at_i<=")?.toInt()
            val comments = request.url.parameters["tags"]!!.endsWith(",comment")
            val matching = if (comments) available.filter { through == null || it / 8 + 1000 <= through } else emptyList()
            val hits = matching.take(1000).drop(page * pageSize).take(pageSize)
            if (pageSize > 0) {
                requests += page to through
                assertEquals(100, pageSize)
                assertTrue(page < 10, "Must start an older window before requesting page 10")
            }
            val hitJson = hits.joinToString(",") {
                """{"objectID":"$it","author":"alice","_tags":["comment"],"created_at_i":${it / 8 + 1000}}"""
            }
            val pages = if (pageSize == 0) 0 else (minOf(1000, matching.size) + pageSize - 1) / pageSize
            respond("""{"hits":[$hitJson],"page":$page,"nbPages":$pages,"nbHits":${matching.size},"exhaustiveNbHits":true}""")
        })
        try {
            val store = SubmissionsListStore("alice", KtorAlgoliaRepository(client))
            store.selectFilter(SubmissionFilter.COMMENTS)
            store.ensureLoaded()
            assertEquals(AlgoliaSubmissionCount(original.size, true), store.state.value.commentCount)
            // A new submission between page loads must not shift the snapshot.
            available = listOf(3000) + original
            var loads = 0
            while (store.state.value.canLoadMore && loads++ < 30) {
                store.loadMore()
                assertFalse(store.state.value.loadingFailed)
            }
            assertFalse(store.state.value.canLoadMore)
            assertEquals(original, store.state.value.items.map { it.id })
            assertEquals(AlgoliaSubmissionCount(original.size, true), store.state.value.commentCount)
            assertEquals(3, requests.count { it.first == 0 })
            assertEquals(1, requests[1].first)
            assertEquals(original.first() / 8 + 1000, requests[1].second)
        } finally {
            client.close()
        }
    }
}
