package com.simon.harmonichackernews.network

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
    fun combinesAuthorWithRequestedTypeAndIncludesPolls() = runTest {
        val expectedTags = listOf("author_alice", "author_alice,(story,poll)", "author_alice,comment")
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
}
