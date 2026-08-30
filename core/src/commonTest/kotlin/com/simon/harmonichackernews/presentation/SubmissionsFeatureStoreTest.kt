package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.navigation.toDestination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionsFeatureStoreTest {
    @Test
    fun initializeLoadsContentAndLaterRestoresScrollPosition() = runTest {
        val story = story(1)
        val session = SubmissionsSessionState(
            SubmissionsStore("alice", FakeAlgoliaRepository(listOf(story)), pageSize = 10),
        )
        val store = featureStore(this, session)

        assertNull(store.start())
        runCurrent()
        assertEquals(listOf(story), store.state.value.items)
        assertTrue(store.state.value.loadedSuccessfully)

        store.accept(SubmissionsIntent.RecordScrollPosition(4, -32, appBarCollapsed = true))
        val restoration = featureStore(this, session).start()
        assertEquals(4, restoration?.firstVisibleStoryPosition)
        assertEquals(-32, restoration?.firstVisibleStoryTop)
        assertTrue(restoration?.appBarCollapsed == true)
    }

    @Test
    fun startIsIdempotentAndLoadsOnlyOnce() = runTest {
        val repository = FakeAlgoliaRepository(listOf(story(1)))
        val store = featureStore(
            scope = this,
            session = SubmissionsSessionState(
                SubmissionsStore("alice", repository, pageSize = 10),
            ),
        )

        assertNull(store.start())
        assertNull(store.start())
        runCurrent()

        assertEquals(1, repository.submissionsRequests)
        assertTrue(store.state.value.loadedSuccessfully)
    }

    @Test
    fun closeCancelsLoadingAndIgnoresLaterIntents() = runTest {
        val response = CompletableDeferred<List<Story>>()
        val repository = DeferredAlgoliaRepository(response)
        val session = SubmissionsSessionState(
            SubmissionsStore("alice", repository, pageSize = 10),
        )
        val store = featureStore(backgroundScope, session)

        store.start()
        runCurrent()
        assertTrue(store.state.value.loading)

        store.close()
        store.close()
        assertNull(store.start())
        store.accept(SubmissionsIntent.SelectFilter(SubmissionFilter.COMMENTS))
        store.accept(SubmissionsIntent.Refresh)
        response.complete(listOf(story(1)))
        runCurrent()

        assertFalse(store.state.value.loading)
        assertFalse(store.state.value.loadedSuccessfully)
        assertEquals(SubmissionFilter.BOTH, store.state.value.filter)
        assertEquals(1, repository.submissionsRequests)
    }

    @Test
    fun repeatedLoadMoreKeepsTheActiveRequest() = runTest {
        val response = CompletableDeferred<List<Story>>()
        val repository = PagingAlgoliaRepository(response)
        val session = SubmissionsSessionState(
            SubmissionsStore("alice", repository, pageSize = 1),
        )
        val store = featureStore(backgroundScope, session)

        store.start()
        runCurrent()
        assertTrue(store.state.value.canLoadMore)

        store.accept(SubmissionsIntent.LoadMore)
        runCurrent()
        store.accept(SubmissionsIntent.LoadMore)
        runCurrent()

        assertEquals(2, repository.submissionsRequests)
        assertTrue(store.state.value.loading)

        response.complete(listOf(story(1), story(2)))
        runCurrent()

        assertFalse(store.state.value.loading)
        assertEquals(listOf(1, 2), store.state.value.items.map(Story::id))
    }

    @Test
    fun storyLinkUsesIntegratedViewerOrExternalPlatformEffect() = runTest {
        val session = session()
        var integrated = true
        val store = featureStore(this, session, integratedWebView = { integrated })
        val story = story(1).also {
            it.isLink = true
            it.url = "https://example.com"
        }

        val integratedEffect = async { store.effects.first() }
        runCurrent()
        store.accept(SubmissionsIntent.OpenStoryLink(story))
        assertEquals(
            SubmissionsRuntimeEffect.OpenStory(story.toDestination(showWebsite = true)),
            integratedEffect.await(),
        )

        integrated = false
        val externalEffect = async { store.effects.first() }
        runCurrent()
        store.accept(SubmissionsIntent.OpenStoryLink(story))
        assertEquals(
            SubmissionsRuntimeEffect.OpenExternalLink("https://example.com"),
            externalEffect.await(),
        )
    }

    @Test
    fun commentMasterIsResolvedAndContentRevisionIsPublished() = runTest {
        val source = story(7).also {
            it.isComment = true
            it.commentMasterId = 42
        }
        val master = story(42).also { it.title = "Master" }
        val session = SubmissionsSessionState(
            SubmissionsStore("alice", FakeAlgoliaRepository(listOf(source)), pageSize = 10),
        )
        val store = featureStore(
            this,
            session,
            hackerNewsRepository = FakeHackerNewsRepository(master),
        )
        store.start()
        runCurrent()
        val revision = store.state.value.revision
        val effect = async { store.effects.first() }
        runCurrent()

        store.accept(SubmissionsIntent.OpenCommentMaster(source))
        runCurrent()

        val open = assertIs<SubmissionsRuntimeEffect.OpenStory>(effect.await())
        assertEquals(master.id, open.destination.storyId)
        assertFalse(open.destination.showWebsite)
        assertTrue(store.state.value.revision > revision)
    }

    private fun featureStore(
        scope: CoroutineScope,
        session: SubmissionsSessionState,
        integratedWebView: () -> Boolean = { true },
        hackerNewsRepository: HackerNewsRepository = FakeHackerNewsRepository(null),
    ) = SubmissionsFeatureStore(
        scope = scope,
        sessionState = session,
        commentMasterResolver = CommentMasterResolver(hackerNewsRepository),
        useIntegratedWebView = integratedWebView,
    )

    private fun session() = SubmissionsSessionState(
        SubmissionsStore("alice", FakeAlgoliaRepository(emptyList()), pageSize = 10),
    )

    private class FakeAlgoliaRepository(
        private val items: List<Story>,
    ) : AlgoliaRepository {
        var submissionsRequests = 0

        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> {
            submissionsRequests += 1
            return items.take(limit)
        }

        override suspend fun search(url: String): List<Story> = error("Not used")
        override suspend fun getItemJson(id: Int): String = error("Not used")
    }

    private class DeferredAlgoliaRepository(
        private val response: CompletableDeferred<List<Story>>,
    ) : AlgoliaRepository {
        var submissionsRequests = 0

        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> {
            submissionsRequests += 1
            return response.await().take(limit)
        }

        override suspend fun search(url: String): List<Story> = error("Not used")
        override suspend fun getItemJson(id: Int): String = error("Not used")
    }

    private class PagingAlgoliaRepository(
        private val nextPage: CompletableDeferred<List<Story>>,
    ) : AlgoliaRepository {
        var submissionsRequests = 0

        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> {
            submissionsRequests += 1
            return if (submissionsRequests == 1) {
                listOf(story(1))
            } else {
                nextPage.await().take(limit)
            }
        }

        override suspend fun search(url: String): List<Story> = error("Not used")
        override suspend fun getItemJson(id: Int): String = error("Not used")
    }

    private class FakeHackerNewsRepository(
        private val result: Story?,
    ) : HackerNewsRepository {
        override suspend fun getStory(id: Int): Story? = result
        override suspend fun getComment(id: Int): Comment? = error("Not used")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
    }

    private companion object {
        fun story(id: Int) = Story().also {
            it.id = id
            it.loaded = true
        }
    }
}
