package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlgoliaSearchPaginationTest {
    @Test
    fun searchRetainsPaginationMetadataEvenWhenHitsCannotBeMapped() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"hits":[{"objectID":"invalid"}],"page":2,"nbPages":5,"nbHits":9999}""")
        })
        try {
            val result = KtorAlgoliaRepository(client).search("https://hn.algolia.com/api/v1/search?page=2")
            assertEquals(emptyList(), result.stories)
            assertEquals(2, result.page)
            assertEquals(5, result.pageCount)
            assertEquals(3, result.nextPage)
        } finally {
            client.close()
        }
    }

    @Test
    fun finalAccessiblePageStopsEvenWithMoreTotalMatchesAndAFullPage() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"hits":[{"objectID":"42","author":"fixture","title":"Story","_tags":["story"]}],"page":4,"nbPages":5,"nbHits":9999,"hitsPerPage":1}""")
        })
        try {
            val result = KtorAlgoliaRepository(client).search("https://hn.algolia.com/api/v1/search?page=4")
            assertEquals(42, result.stories.single().id)
            assertNull(result.nextPage)
        } finally {
            client.close()
        }
    }

    @Test
    fun emptyFinalPageStopsWithoutInventingAResultCount() = runTest {
        val client = HttpClient(MockEngine { respond("""{"hits":[],"page":0,"nbPages":0,"nbHits":0}""") })
        try {
            val result = KtorAlgoliaRepository(client).search("https://hn.algolia.com/api/v1/search")
            assertEquals(emptyList(), result.stories)
            assertNull(result.nextPage)
        } finally {
            client.close()
        }
    }
}
