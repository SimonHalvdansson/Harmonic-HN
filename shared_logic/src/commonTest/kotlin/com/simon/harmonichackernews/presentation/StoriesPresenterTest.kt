package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsListPage
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.HackerNewsUserItemsLoader
import com.simon.harmonichackernews.network.HackerNewsUserItemsResult
import com.simon.harmonichackernews.network.HackerNewsUserItems
import com.simon.harmonichackernews.network.StoryFeedLoader
import com.simon.harmonichackernews.network.StoryFeedResult
import com.simon.harmonichackernews.network.dto.HackerNewsItemDto
import com.simon.harmonichackernews.network.dto.HackerNewsUserDto
import com.simon.harmonichackernews.settings.KeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class StoriesPresenterTest {
    @Test
    fun feedTransportAndCompletionAreOwnedByTheSharedPresenter() = runTest {
        val feedLoader = RecordingFeedLoader(StoryFeedResult.ItemIds(listOf(1, 2, 3)))
        val presenter = StoriesPresenter(
            scope = backgroundScope,
            sessionState = StoriesSessionState(),
            algoliaRepository = UnusedAlgoliaRepository,
            hackerNewsRepository = UnusedHackerNewsRepository,
            hackerNewsApi = UnusedHackerNewsApi,
            userItemsLoader = UnusedUserItemsLoader,
            savedItemsRepository = SavedItemsRepository(MemoryKeyValueStore()),
            storyFeedLoader = feedLoader,
            clickedStoryIds = { emptyList() },
            isStoryClicked = { false },
            shouldFilterStory = { false },
            shouldHideClickedStories = { false },
        )
        val effect = async { presenter.effects.first() }
        runCurrent()

        presenter.dispatch(
            StoriesAction.LoadFeed(
                storyType = StoryType.TOP_STORIES,
                frontDay = null,
                generation = 7,
            ),
        )
        runCurrent()

        val loaded = assertIs<StoriesEffect.FeedLoaded>(effect.await())
        assertEquals(StoryType.TOP_STORIES, loaded.storyType)
        assertEquals(7, loaded.generation)
        assertEquals(listOf(1, 2, 3), assertIs<StoryFeedResult.ItemIds>(loaded.result).ids)
        assertEquals(
            listOf(Pair<StoryType, String?>(StoryType.TOP_STORIES, null)),
            feedLoader.requests,
        )
    }

    @Test
    fun userItemSyncNormalizesAndPersistsItsSnapshotInCommonPresentation() = runTest {
        val keyValueStore = MemoryKeyValueStore()
        val savedItems = SavedItemsRepository(keyValueStore)
        val userItemsLoader = RecordingUserItemsLoader()
        val presenter = StoriesPresenter(
            scope = backgroundScope,
            sessionState = StoriesSessionState(),
            algoliaRepository = UnusedAlgoliaRepository,
            hackerNewsRepository = UnusedHackerNewsRepository,
            hackerNewsApi = UnusedHackerNewsApi,
            userItemsLoader = userItemsLoader,
            savedItemsRepository = savedItems,
            storyFeedLoader = RecordingFeedLoader(StoryFeedResult.ItemIds(emptyList())),
            clickedStoryIds = { emptyList() },
            isStoryClicked = { false },
            shouldFilterStory = { false },
            shouldHideClickedStories = { false },
        )
        val effect = async { presenter.effects.first() }
        runCurrent()

        presenter.dispatch(
            StoriesAction.SyncUserItems(
                source = SavedItemSource.UPVOTED,
                generation = 11,
                savedAtMillis = 123,
            ),
        )
        runCurrent()

        val synced = assertIs<StoriesEffect.UserItemsSynced>(effect.await())
        assertEquals(listOf(5, 3), synced.snapshot.itemIds)
        assertEquals(setOf(3), synced.snapshot.commentIds)
        assertEquals(synced.snapshot, savedItems.loadSnapshot(SavedItemSource.UPVOTED))
        assertEquals(listOf("upvoted" to true), userItemsLoader.requests)
    }

    private class RecordingFeedLoader(
        private val result: StoryFeedResult,
    ) : StoryFeedLoader {
        val requests = mutableListOf<Pair<StoryType, String?>>()

        override suspend fun load(storyType: StoryType, frontDay: String?): StoryFeedResult {
            requests += storyType to frontDay
            return result
        }

        override suspend fun loadNextScrapedPage(
            storyType: StoryType,
            nextPageUrl: String,
        ): HackerNewsListPage = error("Not used")
    }

    private object UnusedAlgoliaRepository : AlgoliaRepository {
        override suspend fun getSubmissions(userName: String, limit: Int): List<Story> =
            error("Not used")
        override suspend fun search(url: String): List<Story> = error("Not used")
        override suspend fun getItemJson(id: Int): String = error("Not used")
    }

    private object UnusedHackerNewsRepository : HackerNewsRepository {
        override suspend fun getStory(id: Int): Story? = error("Not used")
        override suspend fun getComment(id: Int): Comment? = error("Not used")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
    }

    private object UnusedHackerNewsApi : HackerNewsApi {
        override suspend fun getItem(id: Int): HackerNewsItemDto? = error("Not used")
        override suspend fun getUser(username: String): HackerNewsUserDto? = error("Not used")
        override suspend fun getMaxItemId(): Int = error("Not used")
        override suspend fun getStoryIds(type: StoryType): List<Int> = error("Not used")
    }

    private object UnusedUserItemsLoader : HackerNewsUserItemsLoader {
        override suspend fun getUserItems(
            path: String,
            loginRequired: Boolean,
        ): HackerNewsUserItemsResult = error("Not used")
    }

    private class RecordingUserItemsLoader : HackerNewsUserItemsLoader {
        val requests = mutableListOf<Pair<String, Boolean>>()
        override suspend fun getUserItems(
            path: String,
            loginRequired: Boolean,
        ): HackerNewsUserItemsResult {
            requests += path to loginRequired
            return HackerNewsUserItemsResult.Success(
                HackerNewsUserItems(
                    itemIds = listOf(3, 5, 3),
                    commentIds = listOf(3, 3),
                ),
            )
        }
    }

    private class MemoryKeyValueStore : KeyValueStore {
        private val values = mutableMapOf<String, Any?>()
        override fun contains(key: String) = key in values
        override fun remove(key: String) { values.remove(key) }
        override fun getString(key: String, default: String?) = values[key] as? String ?: default
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getFloat(key: String, default: Float) = values[key] as? Float ?: default
        override fun putFloat(key: String, value: Float) { values[key] = value }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String) = values[key] as? Set<String> ?: emptySet()
        override fun putStringSet(key: String, value: Set<String>?) { values[key] = value }
    }
}
