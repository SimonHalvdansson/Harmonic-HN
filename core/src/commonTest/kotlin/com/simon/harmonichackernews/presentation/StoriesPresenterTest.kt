package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.SavedItemSource
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.platform.HistoryStoreSnapshot
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.HackerNewsAccountState
import com.simon.harmonichackernews.platform.ConnectivityService
import com.simon.harmonichackernews.platform.ObservableHistoryStore
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
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
import com.simon.harmonichackernews.settings.StoredUserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoriesPresenterTest {
    @Test
    fun feedCacheIsPreparedOnWorkerBeforeApplyingRows() = runTest {
        val worker = QueuedCacheDispatcher()
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val presenter = presenter(session, saved, backgroundScope,
            RecordingFeedLoader(StoryFeedResult.ItemIds(listOf(2, 1))))
        val hydrated = mutableListOf<Int>()
        val runtime = cacheRuntime(backgroundScope, session, saved, presenter, worker,
            hydrate = { story ->
                assertTrue(worker.executing)
                hydrated += story.id
                story.title = "Cached ${story.id}"
                story.loaded = true
                true
            },
        )
        runtime.refresh(false)
        runCurrent()
        assertTrue(hydrated.isEmpty())
        assertTrue(runtime.mainStories.isEmpty())
        worker.runAll()
        runCurrent()
        assertEquals(listOf(2, 1), hydrated)
        assertEquals(listOf("Cached 2", "Cached 1"), runtime.mainStories.map { it.title })
    }

    @Test
    fun completedCacheReadCannotReplaceANewerFeedGeneration() = runTest {
        val worker = QueuedCacheDispatcher()
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val presenter = presenter(session, saved, backgroundScope)
        var reads = 0
        val runtime = cacheRuntime(backgroundScope, session, saved, presenter, worker,
            cached = {
                assertTrue(worker.executing)
                reads++
                listOf(Story("Old cached feed", 1, true, false))
            },
        )
        runtime.showCachedStories()
        runCurrent()
        worker.runAll() // The old read is complete; its continuation has not reached the UI yet.
        runtime.clearActiveStories()
        runCurrent()
        assertEquals(1, reads)
        assertTrue(runtime.mainStories.isEmpty())
        assertFalse(runtime.mainStore.state.value.loading)
    }

    private fun cacheRuntime(
        scope: CoroutineScope,
        session: StoriesSessionState,
        saved: SavedItemsRepository,
        presenter: StoriesPresenter,
        worker: CoroutineDispatcher,
        hydrate: (Story) -> Boolean = { false },
        cached: () -> List<Story> = { emptyList() },
    ) = StoriesFeatureRuntime(
        scope = scope,
        sessionState = session,
        presenter = presenter,
        savedItems = saved,
        savedItemActions = SavedItemActionUseCase(saved, { 0L },
            voteRequest = { _, _ -> error("Not used") },
            favoriteRequest = { _, _ -> error("Not used") }),
        historyStore = MemoryHistoryStore(),
        accounts = MemoryAccounts(),
        connectivity = AlwaysOnline,
        userSettings = StoredUserSettings(MemoryKeyValueStore(), emptyFlow()),
        loadContentFilters = { com.simon.harmonichackernews.settings.ContentFilters() },
        commentMasterResolver = CommentMasterResolver(UnusedHackerNewsRepository),
        nowMillis = { 1_000L },
        hydrateCachedStory = hydrate,
        loadCachedStories = cached,
        cacheDispatcher = worker,
    )

    private class QueuedCacheDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()
        var executing = false
            private set
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun runAll() {
            executing = true
            try { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
            finally { executing = false }
        }
    }

    @Test
    fun featureRuntimeRetainsMainFeedAcrossSearchAndKeepsSearchResultsIsolated() = runTest {
        val session = StoriesSessionState()
        val savedItems = SavedItemsRepository(MemoryKeyValueStore())
        val presenter = presenter(session, savedItems, backgroundScope)
        val runtime = StoriesFeatureRuntime(
            scope = backgroundScope,
            sessionState = session,
            presenter = presenter,
            savedItems = savedItems,
            savedItemActions = SavedItemActionUseCase(
                repository = savedItems,
                nowMillis = { 0L },
                voteRequest = { _, _ -> error("Not used") },
                favoriteRequest = { _, _ -> error("Not used") },
            ),
            historyStore = MemoryHistoryStore(),
            accounts = MemoryAccounts(),
            connectivity = AlwaysOnline,
            userSettings = StoredUserSettings(MemoryKeyValueStore(), emptyFlow()),
            loadContentFilters = { com.simon.harmonichackernews.settings.ContentFilters() },
            commentMasterResolver = CommentMasterResolver(UnusedHackerNewsRepository),
            nowMillis = { 1_000L },
            hydrateCachedStory = { false },
        )
        runtime.initialize(
            preferredTypeLabel = StoryType.TOP_STORIES.label,
            enabledAdditionalFrontpages = emptySet(),
            hasAccount = false,
            restoring = false,
        )
        presenter.mainStoryList.replace(listOf(Story("Retained", 42, true, false)))

        runtime.openSearch()
        runCurrent()
        assertTrue(runtime.searching)
        assertEquals(listOf(42), runtime.mainStories.map(Story::id))

        val retained = runtime.closeSearch()
        runCurrent()

        assertTrue(retained)
        assertFalse(runtime.searching)
        assertEquals(listOf(42), runtime.mainStories.map(Story::id))
        assertTrue(runtime.searchStories.isEmpty())
    }

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

    private fun presenter(
        session: StoriesSessionState,
        savedItems: SavedItemsRepository,
        scope: CoroutineScope,
        feedLoader: StoryFeedLoader = RecordingFeedLoader(StoryFeedResult.ItemIds(emptyList())),
    ) = StoriesPresenter(
        scope = scope,
        sessionState = session,
        algoliaRepository = UnusedAlgoliaRepository,
        hackerNewsRepository = UnusedHackerNewsRepository,
        hackerNewsApi = UnusedHackerNewsApi,
        userItemsLoader = UnusedUserItemsLoader,
        savedItemsRepository = savedItems,
        storyFeedLoader = feedLoader,
        clickedStoryIds = { emptyList() },
        isStoryClicked = { false },
        shouldHideClickedStories = { false },
    )

    private class MemoryHistoryStore : ObservableHistoryStore {
        private val items = mutableListOf<History>()
        private val mutableHistoryState = MutableStateFlow(HistoryStoreSnapshot())
        override val historyState: StateFlow<HistoryStoreSnapshot> = mutableHistoryState
        override fun initialize() = Unit
        override fun load(): List<History> = items.toList()
        override fun record(id: Int, createdAtMillis: Long) {
            items.removeAll { it.id == id }
            items += History(id, createdAtMillis)
            publish()
        }
        override fun remove(id: Int) {
            items.removeAll { it.id == id }
            publish()
        }
        override fun clear() {
            items.clear()
            publish()
        }
        override fun contains(id: Int) = items.any { it.id == id }
        override val size: Int get() = items.size
        override val changeVersion: Long get() = items.hashCode().toLong()
        override suspend fun recordHistory(id: Int, createdAtMillis: Long): Boolean {
            val previousVersion = changeVersion
            record(id, createdAtMillis)
            return changeVersion != previousVersion
        }
        override suspend fun removeHistory(id: Int): Boolean {
            val previousVersion = changeVersion
            remove(id)
            return changeVersion != previousVersion
        }
        override suspend fun clearHistory() = clear()

        private fun publish() {
            mutableHistoryState.value = HistoryStoreSnapshot(load(), changeVersion)
        }
    }

    private class MemoryAccounts : ObservableHackerNewsAccountRepository {
        private val mutableAccount = MutableStateFlow<HackerNewsAccountState>(
            HackerNewsAccountState.LoggedOut,
        )
        override val accountState: StateFlow<HackerNewsAccountState> = mutableAccount
        override suspend fun saveAccount(account: HackerNewsAccount): Boolean {
            mutableAccount.value = HackerNewsAccountState.LoggedIn(account)
            return true
        }
        override suspend fun clearAccount(): Boolean {
            mutableAccount.value = HackerNewsAccountState.LoggedOut
            return true
        }
    }

    private object AlwaysOnline : ConnectivityService {
        override fun isOnline(): Boolean = true
        override fun isUnmetered(): Boolean = true
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
