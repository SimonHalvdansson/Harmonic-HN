package com.simon.harmonichackernews.cache

import com.simon.harmonichackernews.network.AlgoliaSearchPage
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaSubmissionType
import com.simon.harmonichackernews.network.AlgoliaSubmissionsCursor
import com.simon.harmonichackernews.network.AlgoliaSubmissionsPage
import com.simon.harmonichackernews.network.HackerNewsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StoryCacheUseCaseTest {
    @Test
    fun explicitSelectionKeepsRankAndNeverRequestsAnotherFeed() = runTest {
        val saved = mutableListOf<Int>()
        val positions = mutableMapOf<Int, StoryDownloadPosition?>()
        val useCase = useCase(
            top = { error("A selected feed must not load Top Stories") },
            save = { saved += it },
            position = { id, rank -> positions[id] = rank },
        )
        assertEquals(StoryCacheOutcome.FINISHED, useCase.execute(
            StoryCacheRequest(2, false, listOf(19, 19, -1, 6, 23)),
        ) {})
        assertEquals(listOf(19, 6), saved)
        assertEquals(StoryDownloadPosition(1_000, 0), positions[19])
        assertEquals(StoryDownloadPosition(1_000, 1), positions[6])
        assertEquals(StoryCacheOutcome.EMPTY, useCase.execute(StoryCacheRequest(4, false, emptyList())) {})
    }

    @Test
    fun allFailedAndMixedDownloadsHaveDistinctOutcomes() = runTest {
        for ((failures, expected) in listOf(
            setOf(1, 2) to StoryCacheOutcome.FAILED,
            setOf(1) to StoryCacheOutcome.PARTIAL,
            emptySet<Int>() to StoryCacheOutcome.FINISHED,
        )) {
            val progress = mutableListOf<StoryCacheProgress>()
            val useCase = useCase(load = { id ->
                check(id !in failures) { "Offline" }
                payload(id)
            })
            assertEquals(expected, useCase.execute(StoryCacheRequest(2, false), progress::add))
            assertEquals(StoryCacheProgress(2, 2), progress.last())
        }
    }

    @Test
    fun failedWritesAndArticleDownloadsAreNotSuccessfulCompletion() = runTest {
        assertEquals(StoryCacheOutcome.FAILED, useCase(save = { error("Write failed") })
            .execute(StoryCacheRequest(2, false)) {})
        assertEquals(StoryCacheOutcome.PARTIAL, useCase(article = { false })
            .execute(StoryCacheRequest(2, true)) {})
        assertEquals(StoryCacheOutcome.PARTIAL, useCase(article = { error("Article unavailable") })
            .execute(StoryCacheRequest(2, true)) {})
    }

    @Test
    fun cancellationPropagatesInsteadOfBecomingPartialCompletion() = runTest {
        assertFailsWith<CancellationException> {
            useCase(load = { throw CancellationException() }).execute(StoryCacheRequest(2, false)) {}
        }
    }

    private fun useCase(
        load: suspend (Int) -> String = { payload(it) },
        save: suspend (Int) -> Unit = {},
        article: suspend (Int) -> Boolean = { true },
        top: () -> List<Int> = { listOf(1, 2) },
        position: (Int, StoryDownloadPosition?) -> Unit = { _, _ -> },
    ) = StoryCacheUseCase(
        object : HackerNewsRepository {
            override suspend fun getStoryIds(type: StoryType) = top()
            override suspend fun getStory(id: Int): Story? = null
            override suspend fun getComment(id: Int): Comment? = null
        },
        object : AlgoliaRepository {
            override suspend fun getItemJson(id: Int) = load(id)
            override suspend fun search(url: String): AlgoliaSearchPage = error("Unused")
            override suspend fun getSubmissions(userName: String, pageSize: Int, type: AlgoliaSubmissionType, cursor: AlgoliaSubmissionsCursor): AlgoliaSubmissionsPage = error("Unused")
        },
        object : StoryCacheSink {
            override suspend fun cacheStory(id: Int, payload: String, download: StoryDownloadPosition?): String {
                save(id)
                position(id, download)
                return "https://example.com/$id"
            }
            override suspend fun cacheArticle(id: Int, url: String) = article(id)
        },
        nowMillis = { 1_000L },
    )

    private fun payload(id: Int) = """{"id":$id,"url":"https://example.com/$id","type":"story"}"""
}
