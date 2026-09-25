package com.simon.harmonichackernews.presentation

import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.data.SavedItemSnapshot
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
import com.simon.harmonichackernews.network.CachedStoryHeader
import com.simon.harmonichackernews.network.JSONParser
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.AlgoliaSubmissionType
import com.simon.harmonichackernews.network.AlgoliaSubmissionsCursor
import com.simon.harmonichackernews.network.AlgoliaSubmissionsPage
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
import com.simon.harmonichackernews.settings.ContentFilters
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.settings.StoredUserSettings
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.settings.UserPreferenceKeys
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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
class StoryRequestsTest {
    @Test
    fun logoutDuringSearchDiscardsRetainedPersonalFeedAndLoadsTopStoriesOnReturn() = runTest {
        val accounts = MemoryAccounts()
        accounts.saveAccount(HackerNewsAccount("reader", "password"))
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val feed = RecordingFeedLoader(StoryFeedResult.ItemIds(emptyList()))
        val runtime = cacheRuntime(
            backgroundScope, session, saved, storyRequests(session, saved, backgroundScope, feed),
            QueuedCacheDispatcher(), accounts = accounts,
        )
        runtime.initialize("Favorites", emptySet(), hasAccount = true, restoring = false)
        runtime.mainStore.replace(listOf(Story("Saved favorite", 42, true, false)))
        runtime.openSearch()
        runCurrent()
        accounts.clearAccount()
        runCurrent()
        assertTrue(runtime.searching)
        assertTrue(runtime.mainStories.isEmpty())
        assertEquals(StoryType.TOP_STORIES, session.mainStoryType)
        assertEquals(StoryType.TOP_STORIES, session.searchStoryType)
        assertFalse(runtime.closeSearch())
        runCurrent()
        assertEquals(listOf(Pair<StoryType, String?>(StoryType.TOP_STORIES, null)), feed.requests)
    }

    @Test
    fun logoutReplacesSelectedAccountFeedAndLoginRestoresItsMenuPosition() = runTest {
        for (type in listOf(StoryType.FAVORITES, StoryType.UPVOTED)) {
            val preferences = MemoryKeyValueStore().apply {
                putString(UserPreferenceKeys.DEFAULT_STORY_TYPE, type.label)
                putString(UserPreferenceKeys.FRONTPAGE_ORDER, "UPVOTED,HISTORY,FAVORITES,TOP_STORIES")
            }
            val accounts = MemoryAccounts()
            accounts.saveAccount(HackerNewsAccount("reader", "password"))
            val session = StoriesSessionState()
            val saved = SavedItemsRepository(MemoryKeyValueStore())
            val runtime = cacheRuntime(
                backgroundScope, session, saved, storyRequests(session, saved, backgroundScope),
                QueuedCacheDispatcher(), accounts = accounts,
                settings = StoredUserSettings(preferences, emptyFlow()),
            )
            runtime.initialize(restoring = false)
            runCurrent()
            val originalMenu = runtime.availableStoryTypes
            assertEquals(type, runtime.currentType)
            accounts.clearAccount()
            runCurrent()
            assertEquals(StoryType.TOP_STORIES, runtime.currentType)
            assertFalse(runtime.availableStoryTypes.any { it.isUserItemList })
            accounts.saveAccount(HackerNewsAccount("another-reader", "password"))
            runCurrent()
            assertEquals(originalMenu, runtime.availableStoryTypes)
            assertEquals(StoryType.TOP_STORIES, runtime.currentType)
            assertEquals(type.label, preferences.getString(UserPreferenceKeys.DEFAULT_STORY_TYPE))
        }
    }

    @Test
    fun disablingBookmarksReplacesSelectedFeedAndRestoringCannotSelectHiddenPages() = runTest {
        val preferences = MemoryKeyValueStore().apply {
            putString(UserPreferenceKeys.DEFAULT_STORY_TYPE, "Bookmarks")
            putString(UserPreferenceKeys.FRONTPAGE_ORDER, "BOOKMARKS,HISTORY")
        }
        val settings = StoredUserSettings(preferences, emptyFlow())
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val runtime = cacheRuntime(
            backgroundScope, session, saved, storyRequests(session, saved, backgroundScope),
            QueuedCacheDispatcher(), settings = settings,
        )
        runtime.initialize(restoring = false)
        assertEquals(StoryType.BOOKMARKS, runtime.currentType)
        preferences.putBoolean(UserPreferenceKeys.BOOKMARKS_ENABLED, false)
        runtime.reconcileSettings()
        assertEquals(StoryType.TOP_STORIES, runtime.currentType)
        assertFalse(StoryType.BOOKMARKS in runtime.availableStoryTypes)
        preferences.putBoolean(UserPreferenceKeys.BOOKMARKS_ENABLED, true)
        runtime.reconcileSettings()
        assertEquals(StoryType.BOOKMARKS, runtime.availableStoryTypes.first())

        for (hidden in listOf(StoryType.BOOKMARKS, StoryType.FAVORITES, StoryType.UPVOTED)) {
            preferences.putBoolean(UserPreferenceKeys.BOOKMARKS_ENABLED, false)
            session.initialized = true
            session.mainStoryType = hidden
            runtime.mainStore.replace(listOf(Story("Unavailable saved item", 42, true, false)))
            runtime.initialize(restoring = true)
            assertEquals(StoryType.TOP_STORIES, runtime.currentType)
            assertTrue(runtime.mainStories.isEmpty())
        }
    }

    @Test
    fun pullRefreshUpdatesRetainedMetadataWithoutDiscardingVisibleContent() = runTest {
        val reply = CompletableDeferred<HackerNewsItemDto>()
        val requested = mutableListOf<Int>()
        val api = object : HackerNewsApi by UnusedHackerNewsApi {
            override suspend fun getItem(id: Int): HackerNewsItemDto {
                requested += id
                return if (id == 1) reply.await() else HackerNewsItemDto(id = id, by = "author", title = "New row")
            }
        }
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val requests = storyRequests(session, saved, backgroundScope,
            RecordingFeedLoader(StoryFeedResult.ItemIds(listOf(2, 1))), api)
        val runtime = cacheRuntime(backgroundScope, session, saved, requests, worker)
        val retained = Story("Old title", 1, true, true).apply {
            score = 10
            descendants = 5
            previewImageUrl = "https://example.com/image.png"
        }
        runtime.mainStore.replace(listOf(retained))

        runtime.refresh(true)
        runCurrent()
        worker.runAll()
        runCurrent()
        assertEquals(listOf(2, 1), requested)
        assertEquals("Old title", runtime.mainStore.state.value.items.last().title)
        assertTrue(retained.loaded)
        reply.complete(HackerNewsItemDto(id = 1, by = "author", title = "Fresh title", score = 99, descendants = 77))
        runCurrent()

        val row = runtime.mainStore.state.value.items.last()
        assertEquals("Fresh title", row.title)
        assertEquals(99, row.score)
        assertEquals(77, row.descendants)
        assertTrue(row.isRead)
        assertEquals("https://example.com/image.png", row.previewImageUrl)
        assertTrue(runtime.mainStories.last() === retained)
        runtime.loadVisibleStories()
        runCurrent()
        assertEquals(listOf(2, 1), requested)
    }

    @Test
    fun searchOptionsSurviveRuntimeRecreationWithoutWaitingForCollectors() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val runtime = cacheRuntime(
            backgroundScope, session, saved, storyRequests(session, saved, backgroundScope),
            QueuedCacheDispatcher(),
        )
        runtime.mainStore.replace(listOf(Story("Retained", 42, true, false)))
        runtime.openSearch()
        runtime.selectSearchOption(StorySearchOption.SORT, 1)
        runtime.selectSearchOption(StorySearchOption.DATE, 2)
        runtime.selectSearchOption(StorySearchOption.POINTS, 3)
        runtime.selectSearchOption(StorySearchOption.COMMENTS, 2)
        runtime.toggleOnlyRead()

        val restored = cacheRuntime(
            backgroundScope, session, saved, storyRequests(session, saved, backgroundScope),
            QueuedCacheDispatcher(),
        )
        assertTrue(restored.searching)
        assertEquals(
            StorySearchOptions(1, 2, 3, 2, onlyRead = true),
            restored.searchOptions.state.value.options,
        )
        assertEquals(listOf(42), restored.mainStore.state.value.items.map { it.id })

        runtime.closeSearch()
        assertFalse(runtime.searching)
        assertEquals(StorySearchOptions(), session.searchOptions)
        assertEquals(StorySearchOptions(), runtime.searchOptions.state.value.options)
    }

    @Test
    fun feedAndSearchSelectionsUseTheRetainedSessionImmediately() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val runtime = cacheRuntime(
            backgroundScope, session, saved, storyRequests(session, saved, backgroundScope),
            QueuedCacheDispatcher(),
        )
        runtime.mainStore.replace(listOf(Story("Retained", 42, true, false)))
        runtime.selectType(StoryListTarget.MAIN, StoryType.NEW_STORIES)
        runtime.openSearch()
        assertEquals(StoryType.NEW_STORIES, runtime.currentType)
        runtime.selectType(StoryListTarget.SEARCH, StoryType.TOP_STORIES)
        assertEquals(StoryType.TOP_STORIES, runtime.currentType)
        assertEquals(StoryType.NEW_STORIES, session.mainStoryType)

        runtime.closeSearch()
        assertEquals(StoryType.NEW_STORIES, runtime.currentType)
        assertEquals(listOf(42), runtime.mainStore.state.value.items.map { it.id })
        runtime.evaluateUpdate(alwaysShow = true)
        assertTrue(session.showRefreshPrompt)
        runtime.openSearch()
        runtime.evaluateUpdate(alwaysShow = false)
        assertFalse(session.showRefreshPrompt)
    }

    @Test
    fun commentsUpdatesRefreshFeedAndSearchSnapshotsWhilePreservingRowState() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val requests = storyRequests(session, saved, backgroundScope)
        val runtime = cacheRuntime(backgroundScope, session, saved, requests, QueuedCacheDispatcher())
        val feedStory = Story("Feed title", 42, true, true).apply {
            score = 1
            descendants = 2
            previewImageUrl = "https://example.com/preview.png"
        }
        val searchStory = Story("Search title", 42, true, false)
        val otherStory = Story("Unrelated", 43, true, false)
        runtime.mainStore.replace(listOf(feedStory, otherStory))
        runtime.openSearch()
        runtime.searchStore.replace(listOf(searchStory))
        val previousFeed = runtime.mainStore.state.value
        val previousSearch = runtime.searchStore.state.value
        val update = Story("Updated title", 42, true, false).apply {
            score = 25
            descendants = 8
            createdAtEpochSeconds = 123
            url = "https://example.com/updated"
        }

        assertTrue(runtime.mergeExternalStoryUpdate(update))

        for (store in listOf(runtime.mainStore, runtime.searchStore)) {
            val row = store.state.value.items.first()
            assertEquals("Updated title", row.title)
            assertEquals(25, row.score)
            assertEquals(8, row.descendants)
            assertEquals(123, row.createdAtEpochSeconds)
            assertEquals(update.url, row.url)
        }
        assertEquals("Feed title", previousFeed.items.first().title)
        assertEquals("Search title", previousSearch.items.first().title)
        assertTrue(runtime.mainStore.state.value.items.first().isRead)
        assertEquals(feedStory.previewImageUrl, runtime.mainStore.state.value.items.first().previewImageUrl)
        assertTrue(runtime.mainStories.first() === feedStory)
        assertEquals(previousFeed.items.last(), runtime.mainStore.state.value.items.last())
        assertFalse(runtime.mergeExternalStoryUpdate(Story("Absent", 99, true, false)))
    }

    @Test
    fun feedCacheIsPreparedOnWorkerBeforeApplyingRows() = runTest {
        val worker = QueuedCacheDispatcher()
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val requests = storyRequests(session, saved, backgroundScope,
            RecordingFeedLoader(StoryFeedResult.ItemIds(listOf(2, 1))))
        val hydrated = mutableListOf<Int>()
        val runtime = cacheRuntime(backgroundScope, session, saved, requests, worker,
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
        val requests = storyRequests(session, saved, backgroundScope)
        var reads = 0
        val runtime = cacheRuntime(backgroundScope, session, saved, requests, worker,
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

    @Test
    fun refreshingCancelsThePreviousFeedRequestAndAppliesOnlyTheReplacement() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val cancelled = CompletableDeferred<Unit>()
        var loads = 0
        val feedLoader = object : StoryFeedLoader {
            override suspend fun load(storyType: StoryType, frontDay: String?): StoryFeedResult {
                loads++
                if (loads == 1) {
                    try {
                        awaitCancellation()
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
                return StoryFeedResult.LinkDirectory(listOf(Story("Replacement", 2, true, false)))
            }
            override suspend fun loadNextScrapedPage(
                storyType: StoryType,
                nextPageUrl: String,
            ): HackerNewsListPage = error("Not used")
        }
        val worker = QueuedCacheDispatcher()
        val runtime = cacheRuntime(
            backgroundScope, session, saved,
            storyRequests(session, saved, backgroundScope, feedLoader), worker,
        )
        runtime.refresh(false)
        runCurrent()
        runtime.refresh(false)
        runCurrent()
        worker.runAll()
        runCurrent()

        assertTrue(cancelled.isCompleted)
        assertEquals(listOf(2), runtime.mainStore.state.value.items.map { it.id })
        assertFalse(runtime.mainStore.state.value.loading)
        assertEquals(null, runtime.failure)
    }

    @Test
    fun visibleSummariesStartRequestsBeforeOffscreenReadsAndScrollChangesPriority() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val reads = mutableListOf<Int>()
        val requested = mutableListOf<Int>()
        val api = object : HackerNewsApi by UnusedHackerNewsApi {
            override suspend fun getItem(id: Int): HackerNewsItemDto {
                requested += id
                awaitCancellation()
            }
        }
        val runtime = cacheRuntime(backgroundScope, session, saved,
            storyRequests(session, saved, backgroundScope,
                RecordingFeedLoader(StoryFeedResult.ItemIds((1..200).toList())), api), worker,
            header = { id, rebuild ->
                assertTrue(worker.executing)
                assertFalse(rebuild)
                reads += id
                cachedHeader(id)
            },
        )
        runtime.refresh(false)
        runCurrent()
        assertTrue(reads.isEmpty())
        worker.runAll(); runCurrent()
        assertEquals((1..12).toList(), reads)
        assertEquals((1..12).toList(), requested)
        assertEquals((1..200).toList(), runtime.mainStories.map { it.id })
        assertTrue(runtime.mainStories.take(12).all { it.loaded })
        assertFalse(runtime.mainStories[12].loaded)

        // One old batch is already queued; subsequent batches must follow the new viewport.
        runtime.loadVisibleStories(lastVisibleIndex = 60, firstVisibleIndex = 50)
        repeat(5) { worker.runAll(); runCurrent() }
        assertTrue((51..78).all { it in reads })
        assertFalse(40 in reads)
        assertFalse(100 in reads)
        assertEquals(reads.size, reads.toSet().size)
        assertEquals(requested.size, requested.toSet().size)
        runtime.loadVisibleStories(lastVisibleIndex = 60, firstVisibleIndex = 50)
        repeat(2) { worker.runAll(); runCurrent() }
        assertEquals(reads.size, reads.toSet().size)
        assertEquals(requested.size, requested.toSet().size)
    }

    @Test
    fun filtersBackfillTheScreenBeforePublishingAndSkipReadRowsWithoutIo() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val reads = mutableListOf<Int>()
        val history = MemoryHistoryStore().apply { (1..5).forEach { record(it, 0) } }
        val requests = storyRequests(session, saved, backgroundScope,
            RecordingFeedLoader(StoryFeedResult.ItemIds((1..100).toList())))
        val runtime = cacheRuntime(backgroundScope, session, saved, requests, worker,
            header = { id, _ -> reads += id; cachedHeader(id, if (id <= 30) "Blocked" else "Cached $id") },
            history = history,
        )
        requests.configureVisibility(ContentFilters(words = listOf("Blocked")), false)
        runtime.configure(pagination = false, hideRead = true, alwaysOpenComments = false, useIntegratedWebView = false)
        runtime.refresh(false); runCurrent()
        repeat(4) { worker.runAll(); runCurrent() }
        assertTrue(runtime.mainStories.take(12).all { it.loaded })
        assertEquals((31..42).toList(), runtime.mainStories.take(12).map { it.id })
        assertFalse(reads.any { it <= 5 })
        assertEquals(reads.size, reads.toSet().size)
    }

    @Test
    fun failedDetailsRecoverLegacyHeadersOnceAndKeepImagesAndTint() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val reads = mutableListOf<Pair<Int, Boolean>>()
        var attempts = 0
        val api = object : HackerNewsApi by UnusedHackerNewsApi {
            override suspend fun getItem(id: Int): HackerNewsItemDto {
                attempts++
                error("Offline")
            }
        }
        val runtime = cacheRuntime(backgroundScope, session, saved,
            storyRequests(session, saved, backgroundScope, RecordingFeedLoader(StoryFeedResult.ItemIds(listOf(1))), api), worker,
            header = { id, rebuild ->
                reads += id to rebuild
                if (rebuild) cachedHeader(id, extra = """,
                    "preview_image_url":"https://example.com/image.jpg",
                    "preview_image_url_loaded":true,
                    "preview_image_tint_color_loaded":true,"preview_image_tint_color":123,
                    "preview_image_tint_source_url":"https://example.com/image.jpg",
                    "preview_image_tint_base_color":456,"preview_image_tint_mode":"light",
                    "favicon_tint_color_loaded":true,"favicon_tint_color":789,
                    "favicon_tint_source_url":"https://example.com/favicon.ico",
                    "favicon_tint_base_color":456,"favicon_tint_mode":"light",
                    "kids":[9,7,8]
                """) else null
            },
        )
        runtime.refresh(false); runCurrent()
        worker.runAll(); runCurrent()
        assertEquals(listOf(1 to false), reads)
        assertEquals(3, attempts)
        worker.runAll(); runCurrent()
        val story = runtime.mainStories.single()
        assertTrue(story.loaded)
        assertFalse(story.loadingFailed)
        assertEquals("https://example.com/image.jpg", story.previewImageUrl)
        assertEquals(123, story.previewImageTintColor)
        assertEquals(789, story.faviconTintColor)
        assertEquals(listOf(9, 7, 8), story.kids?.toList())
        runtime.loadVisibleStories()
        worker.runAll(); runCurrent()
        assertEquals(listOf(1 to false, 1 to true), reads)
        assertEquals(3, attempts)
    }

    @Test
    fun onlineSummaryMissesNeverReadFullDiscussions() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val reads = mutableListOf<Pair<Int, Boolean>>()
        val api = object : HackerNewsApi by UnusedHackerNewsApi {
            override suspend fun getItem(id: Int) = HackerNewsItemDto(id = id, title = "Fresh", by = "author")
        }
        val runtime = cacheRuntime(backgroundScope, session, saved,
            storyRequests(session, saved, backgroundScope, RecordingFeedLoader(StoryFeedResult.ItemIds(listOf(1))), api), worker,
            header = { id, rebuild -> reads += id to rebuild; null },
        )
        runtime.refresh(false); runCurrent()
        repeat(3) { worker.runAll(); runCurrent() }
        assertEquals(listOf(1 to false), reads)
        assertEquals("Fresh", runtime.mainStories.single().title)
    }

    @Test
    fun lateRecoveryCannotOverwriteFreshResponseOrSurviveFeedChange() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        var attempts = 0
        val fresh = CompletableDeferred<HackerNewsItemDto>()
        val api = object : HackerNewsApi by UnusedHackerNewsApi {
            override suspend fun getItem(id: Int): HackerNewsItemDto {
                if (++attempts <= 3) error("Offline")
                return fresh.await()
            }
        }
        val requests = storyRequests(session, saved, backgroundScope,
            RecordingFeedLoader(StoryFeedResult.ItemIds(listOf(1))), api)
        val runtime = cacheRuntime(backgroundScope, session, saved, requests, worker,
            header = { id, rebuild -> if (rebuild) cachedHeader(id, "Old cache") else null },
        )
        runtime.refresh(false); runCurrent()
        worker.runAll(); runCurrent() // Failed HTTP queues recovery.
        val story = runtime.mainStories.single()
        requests.loadStoryRow(story, false, requests.storyLoadGeneration)
        runCurrent()
        fresh.complete(HackerNewsItemDto(id = 1, title = "Fresh", by = "author"))
        runCurrent()
        worker.runAll(); runCurrent() // An already queued cache read must not overwrite HTTP.
        assertEquals("Fresh", story.title)

        runtime.refresh(false, true); runCurrent()
        worker.runAll()
        runtime.selectType(StoryListTarget.MAIN, StoryType.NEW_STORIES)
        runtime.clearActiveStories()
        runCurrent()
        assertTrue(runtime.mainStories.isEmpty())
    }

    @Test
    fun externalUpdateWinsOverAnApproachingCacheResultAndRetainedRowsKeepIdentity() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val pending = CompletableDeferred<HackerNewsItemDto>()
        val api = object : HackerNewsApi by UnusedHackerNewsApi {
            override suspend fun getItem(id: Int): HackerNewsItemDto = pending.await()
        }
        val runtime = cacheRuntime(backgroundScope, session, saved,
            storyRequests(session, saved, backgroundScope,
                RecordingFeedLoader(StoryFeedResult.ItemIds((1..40).toList())), api), worker,
            header = { id, _ -> cachedHeader(id) },
        )
        val retained = Story("Live", 1, true, true).apply { previewImageUrl = "live.jpg" }
        runtime.mainStore.replace(listOf(retained))
        runtime.refresh(true); runCurrent()
        worker.runAll(); runCurrent()
        assertTrue(runtime.mainStories.first() === retained)
        assertEquals("Live", retained.title)
        assertEquals("live.jpg", retained.previewImageUrl)
        worker.runAll() // Header for row 13 is detached and waiting for publication.
        runtime.mergeExternalStoryUpdate(Story("New external", 13, true, false))
        runCurrent()
        assertEquals("New external", runtime.mainStories[12].title)
        assertFalse(runtime.mainStories[12].loaded)
    }

    @Test
    fun refreshHydratesRetainedPlaceholdersBeforeStartingTheirRequests() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val reads = mutableListOf<Int>()
        val requested = mutableListOf<Int>()
        val api = object : HackerNewsApi by UnusedHackerNewsApi {
            override suspend fun getItem(id: Int): HackerNewsItemDto {
                requested += id
                awaitCancellation()
            }
        }
        val runtime = cacheRuntime(backgroundScope, session, saved,
            storyRequests(session, saved, backgroundScope,
                RecordingFeedLoader(StoryFeedResult.ItemIds(listOf(1))), api), worker,
            header = { id, rebuild ->
                assertFalse(rebuild)
                reads += id
                cachedHeader(id)
            },
        )
        val retained = Story("Loading...", 1, false, false)
        runtime.mainStore.replace(listOf(retained))
        runtime.refresh(true); runCurrent()
        worker.runAll(); runCurrent()
        assertTrue(requested.isEmpty())
        worker.runAll(); runCurrent()
        assertTrue(runtime.mainStories.single() === retained)
        assertEquals("Cached 1", retained.title)
        assertEquals(listOf(1), reads)
        assertEquals(listOf(1), requested)
    }

    @Test
    fun returningFromSearchStillHydratesPreviouslyOffscreenFeedRows() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val reads = mutableListOf<Int>()
        val runtime = cacheRuntime(backgroundScope, session, saved,
            storyRequests(session, saved, backgroundScope,
                RecordingFeedLoader(StoryFeedResult.ItemIds((1..100).toList()))), worker,
            header = { id, _ -> reads += id; cachedHeader(id) },
        )
        runtime.refresh(false); runCurrent()
        worker.runAll(); runCurrent()
        runtime.openSearch(); runCurrent()
        assertTrue(runtime.closeSearch())
        runtime.loadVisibleStories(lastVisibleIndex = 50, firstVisibleIndex = 40)
        repeat(5) { runCurrent(); worker.runAll() }
        runCurrent()
        assertTrue(runtime.mainStories[40].loaded)
        assertTrue(41 in reads)
        assertFalse(90 in reads)
    }

    @Test
    fun scrapedPagesPrepareTheirLeadingRowsAndKeepCommentClassification() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val reads = mutableListOf<Int>()
        val feed = object : StoryFeedLoader {
            override suspend fun load(storyType: StoryType, frontDay: String?) =
                StoryFeedResult.Scraped(HackerNewsListPage((1..30).toList(), listOf(13), "next"))
            override suspend fun loadNextScrapedPage(storyType: StoryType, nextPageUrl: String) =
                HackerNewsListPage((25..60).toList(), listOf(43), null)
        }
        val runtime = cacheRuntime(backgroundScope, session, saved,
            storyRequests(session, saved, backgroundScope, feed), worker,
            header = { id, _ -> reads += id; cachedHeader(id) },
        )
        runtime.selectType(StoryListTarget.MAIN, StoryType.CLASSIC)
        runtime.refresh(false); runCurrent()
        repeat(4) { worker.runAll(); runCurrent() }
        assertTrue(runtime.mainStories[12].isComment)
        runtime.loadVisibleStories(lastVisibleIndex = 29, firstVisibleIndex = 20)
        repeat(3) { worker.runAll(); runCurrent() }
        val beforeNextPage = reads.size
        runtime.loadMore(); runCurrent()
        worker.runAll(); runCurrent()
        assertEquals((31..42).toList(), reads.drop(beforeNextPage))
        assertEquals((1..60).toList(), runtime.mainStories.map { it.id })
        repeat(4) { worker.runAll(); runCurrent() }
        assertTrue(runtime.mainStories[42].isComment)
        assertEquals(reads.size, reads.toSet().size)
    }

    @Test
    fun shorterRefreshPreparesTheClampedViewportWithoutWaitingForAnotherScroll() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val reads = mutableListOf<Int>()
        var ids = (1..100).toList()
        val feed = object : StoryFeedLoader {
            override suspend fun load(storyType: StoryType, frontDay: String?) = StoryFeedResult.ItemIds(ids)
            override suspend fun loadNextScrapedPage(storyType: StoryType, nextPageUrl: String): HackerNewsListPage = error("Unused")
        }
        val runtime = cacheRuntime(backgroundScope, session, saved,
            storyRequests(session, saved, backgroundScope, feed), worker,
            header = { id, _ -> reads += id; cachedHeader(id) },
        )
        runtime.refresh(false); runCurrent()
        worker.runAll(); runCurrent()
        runtime.loadVisibleStories(lastVisibleIndex = 60, firstVisibleIndex = 50)
        repeat(4) { worker.runAll(); runCurrent() }
        ids = (101..105).toList()
        runtime.refresh(true); runCurrent()
        worker.runAll(); runCurrent()
        assertEquals(ids, runtime.mainStories.map { it.id })
        assertTrue(runtime.mainStories.all { it.loaded })
    }

    @Test
    fun uncachedFilteredResponsesLoadReplacementsUntilTheViewportIsFilled() = runTest {
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(MemoryKeyValueStore())
        val worker = QueuedCacheDispatcher()
        val api = object : HackerNewsApi by UnusedHackerNewsApi {
            override suspend fun getItem(id: Int) = HackerNewsItemDto(
                id = id, title = if (id <= 25) "Blocked" else "Fresh $id", by = "author",
            )
        }
        val requests = storyRequests(session, saved, backgroundScope,
            RecordingFeedLoader(StoryFeedResult.ItemIds((1..80).toList())), api)
        val runtime = cacheRuntime(backgroundScope, session, saved, requests, worker)
        requests.configureVisibility(ContentFilters(words = listOf("Blocked")), false)
        runtime.refresh(false); runCurrent()
        repeat(10) { worker.runAll(); runCurrent() }
        assertEquals((26..37).toList(), runtime.mainStories.take(12).map { it.id })
        assertTrue(runtime.mainStories.take(12).all { it.loaded })
    }

    private fun cachedHeader(id: Int, title: String = "Cached $id", extra: String = "") =
        JSONParser.prepareCachedStoryHeader(
            """{"id":$id,"title":"$title","author":"fixture"$extra}""", id,
        )

    private fun cacheRuntime(
        scope: CoroutineScope,
        session: StoriesSessionState,
        saved: SavedItemsRepository,
        requests: StoryRequests,
        worker: CoroutineDispatcher,
        hydrate: (Story) -> Boolean = { false },
        cached: () -> List<Story> = { emptyList() },
        header: (suspend (Int, Boolean) -> CachedStoryHeader?)? = null,
        history: ObservableHistoryStore = MemoryHistoryStore(),
        accounts: ObservableHackerNewsAccountRepository = MemoryAccounts(),
        settings: UserSettings = StoredUserSettings(MemoryKeyValueStore(), emptyFlow()),
    ) = StoriesFeatureRuntime(
        scope = scope,
        sessionState = session,
        requests = requests,
        savedItems = saved,
        savedItemActions = SavedItemActionUseCase(saved, { 0L },
            voteRequest = { _, _ -> error("Not used") },
            favoriteRequest = { _, _ -> error("Not used") }),
        historyStore = history,
        accounts = accounts,
        connectivity = AlwaysOnline,
        userSettings = settings,
        loadContentFilters = { ContentFilters() },
        rootStoryResolver = CommentMasterResolver(UnusedHackerNewsRepository),
        nowMillis = { 1_000L },
        loadCachedStoryHeader = header ?: { id, _ ->
            val story = Story("Loading...", id, false, false)
            if (hydrate(story)) JSONParser.prepareCachedStoryHeader(
                """{"id":$id,"title":"${story.title}"}""", id,
            ) else null
        },
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
        val requests = storyRequests(session, savedItems, backgroundScope)
        val runtime = StoriesFeatureRuntime(
            scope = backgroundScope,
            sessionState = session,
            requests = requests,
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
            loadContentFilters = { ContentFilters() },
            rootStoryResolver = CommentMasterResolver(UnusedHackerNewsRepository),
            nowMillis = { 1_000L },
            loadCachedStoryHeader = { _, _ -> null },
        )
        runtime.initialize(
            preferredTypeLabel = StoryType.TOP_STORIES.label,
            enabledAdditionalFrontpages = emptySet(),
            hasAccount = false,
            restoring = false,
        )
        runtime.mainStore.replace(listOf(Story("Retained", 42, true, false)))

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
    fun feedRequestsReturnTheirResultDirectly() = runTest {
        val feedLoader = RecordingFeedLoader(StoryFeedResult.ItemIds(listOf(1, 2, 3)))
        val requests = StoryRequests(
            scope = backgroundScope,
            sessionState = StoriesSessionState(),
            algoliaRepository = UnusedAlgoliaRepository,
            hackerNewsRepository = UnusedHackerNewsRepository,
            hackerNewsApi = UnusedHackerNewsApi,
            userItemsLoader = UnusedUserItemsLoader,
            savedItemsRepository = SavedItemsRepository(MemoryKeyValueStore()),
            storyFeedLoader = feedLoader,
            readStoryIds = { emptyList() },
            isStoryRead = { false },
            shouldHideReadStories = { false },
        )
        val result = requests.loadFeed(StoryType.TOP_STORIES, frontDay = null)
        assertEquals(listOf(1, 2, 3), assertIs<StoryFeedResult.ItemIds>(result).ids)
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
        val requests = StoryRequests(
            scope = backgroundScope,
            sessionState = StoriesSessionState(),
            algoliaRepository = UnusedAlgoliaRepository,
            hackerNewsRepository = UnusedHackerNewsRepository,
            hackerNewsApi = UnusedHackerNewsApi,
            userItemsLoader = userItemsLoader,
            savedItemsRepository = savedItems,
            storyFeedLoader = RecordingFeedLoader(StoryFeedResult.ItemIds(emptyList())),
            readStoryIds = { emptyList() },
            isStoryRead = { false },
            shouldHideReadStories = { false },
        )
        val effect = async { requests.effects.first() }
        runCurrent()

        requests.syncUserItems(
                source = SavedItemSource.UPVOTED,
                generation = 11,
                savedAtMillis = 123,
            )
        runCurrent()

        val synced = assertIs<StoryRequestEvent.UserItemsSynced>(effect.await())
        assertEquals(listOf(5, 3), synced.snapshot.itemIds)
        assertEquals(setOf(3), synced.snapshot.commentIds)
        assertEquals(synced.snapshot, savedItems.loadSnapshot(SavedItemSource.UPVOTED))
        assertEquals(listOf("upvoted" to true), userItemsLoader.requests)
    }

    @Test
    fun syncResponseFromAnEarlierAccountSessionCannotReplaceItsNewSnapshot() = runTest {
        for (switchBack in listOf(false, true)) {
            var account = "alice"
            val savedItems = SavedItemsRepository(MemoryKeyValueStore()).also { it.bindAccountScope { account } }
            val response = CompletableDeferred<HackerNewsUserItemsResult>()
            val requests = storyRequests(StoriesSessionState(), savedItems, backgroundScope,
                userItemsLoader = object : HackerNewsUserItemsLoader {
                    override suspend fun getUserItems(path: String, loginRequired: Boolean) = response.await()
                },
            )
            val effects = mutableListOf<StoryRequestEvent>()
            backgroundScope.launch { requests.effects.collect { effects += it } }
            requests.syncUserItems(SavedItemSource.FAVORITES, 1, 10)
            runCurrent()
            account = "bob"
            savedItems.refreshAccountScope()
            if (switchBack) account = "alice"
            savedItems.saveSnapshotAtomic(SavedItemSource.FAVORITES,
                SavedItemSnapshot(listOf(9), emptySet()), 20)
            response.complete(HackerNewsUserItemsResult.Success(HackerNewsUserItems(listOf(1), emptyList())))
            runCurrent()
            assertEquals(listOf(9), savedItems.loadSnapshot(SavedItemSource.FAVORITES).itemIds)
            assertEquals(emptyList(), effects)
        }
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

    private fun storyRequests(
        session: StoriesSessionState,
        savedItems: SavedItemsRepository,
        scope: CoroutineScope,
        feedLoader: StoryFeedLoader = RecordingFeedLoader(StoryFeedResult.ItemIds(emptyList())),
        api: HackerNewsApi = UnusedHackerNewsApi,
        userItemsLoader: HackerNewsUserItemsLoader = UnusedUserItemsLoader,
    ) = StoryRequests(
        scope = scope,
        sessionState = session,
        algoliaRepository = UnusedAlgoliaRepository,
        hackerNewsRepository = UnusedHackerNewsRepository,
        hackerNewsApi = api,
        userItemsLoader = userItemsLoader,
        savedItemsRepository = savedItems,
        storyFeedLoader = feedLoader,
        readStoryIds = { emptyList() },
        isStoryRead = { false },
        shouldHideReadStories = { false },
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
        override suspend fun getSubmissions(userName: String, pageSize: Int, type: AlgoliaSubmissionType, cursor: AlgoliaSubmissionsCursor): AlgoliaSubmissionsPage =
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
