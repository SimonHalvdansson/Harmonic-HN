package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.navigation.toDestination
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionsFeatureRuntimeTest {
    @Test
    fun initializeLoadsContentAndLaterRestoresScrollPosition() = runTest {
        val story = story(1)
        val session = SubmissionsSessionState(
            SubmissionsStore("alice", FakeAlgoliaRepository(listOf(story)), pageSize = 10),
        )
        val runtime = runtime(this, session)

        assertNull(runtime.initialize())
        runCurrent()
        assertEquals(listOf(story), runtime.state.value.items)
        assertTrue(runtime.state.value.loadedSuccessfully)

        runtime.recordScrollPosition(4, -32, appBarCollapsed = true)
        val restoration = runtime.initialize()
        assertEquals(4, restoration?.firstVisibleStoryPosition)
        assertEquals(-32, restoration?.firstVisibleStoryTop)
        assertTrue(restoration?.appBarCollapsed == true)
    }

    @Test
    fun storyLinkUsesIntegratedViewerOrExternalPlatformEffect() = runTest {
        val session = session()
        var integrated = true
        val runtime = runtime(this, session, integratedWebView = { integrated })
        val story = story(1).also {
            it.isLink = true
            it.url = "https://example.com"
        }

        val integratedEffect = async { runtime.effects.first() }
        runCurrent()
        runtime.openStoryLink(story)
        assertEquals(
            SubmissionsRuntimeEffect.OpenStory(story.toDestination(showWebsite = true)),
            integratedEffect.await(),
        )

        integrated = false
        val externalEffect = async { runtime.effects.first() }
        runCurrent()
        runtime.openStoryLink(story)
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
        val runtime = runtime(
            this,
            session,
            hackerNewsRepository = FakeHackerNewsRepository(master),
        )
        runtime.initialize()
        runCurrent()
        val revision = runtime.state.value.revision
        val effect = async { runtime.effects.first() }
        runCurrent()

        runtime.openCommentMaster(source)
        runCurrent()

        val open = assertIs<SubmissionsRuntimeEffect.OpenStory>(effect.await())
        assertEquals(master.id, open.destination.storyId)
        assertFalse(open.destination.showWebsite)
        assertTrue(runtime.state.value.revision > revision)
    }

    private fun runtime(
        scope: TestScope,
        session: SubmissionsSessionState,
        integratedWebView: () -> Boolean = { true },
        hackerNewsRepository: HackerNewsRepository = FakeHackerNewsRepository(null),
    ) = SubmissionsFeatureRuntime(
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
        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> =
            items.take(limit)

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
