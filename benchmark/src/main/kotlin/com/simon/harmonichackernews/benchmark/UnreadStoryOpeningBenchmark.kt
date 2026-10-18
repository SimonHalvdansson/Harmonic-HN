package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.data.History
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.AlgoliaRepository
import com.simon.harmonichackernews.network.HackerNewsApi
import com.simon.harmonichackernews.network.HackerNewsRepository
import com.simon.harmonichackernews.network.HackerNewsUserItemsLoader
import com.simon.harmonichackernews.network.StoryFeedLoader
import com.simon.harmonichackernews.platform.ConnectivityService
import com.simon.harmonichackernews.platform.HackerNewsAccount
import com.simon.harmonichackernews.platform.HackerNewsAccountState
import com.simon.harmonichackernews.platform.HistoryStoreSnapshot
import com.simon.harmonichackernews.platform.ObservableHackerNewsAccountRepository
import com.simon.harmonichackernews.platform.ObservableHistoryStore
import com.simon.harmonichackernews.presentation.CommentMasterResolver
import com.simon.harmonichackernews.presentation.SavedItemActionUseCase
import com.simon.harmonichackernews.presentation.StoriesFeatureRuntime
import com.simon.harmonichackernews.presentation.StoriesSessionState
import com.simon.harmonichackernews.presentation.StoryRequests
import com.simon.harmonichackernews.settings.ContentFilters
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.StoredUserSettings
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Owner-thread CPU/allocation cost of an unread open and its subsequent history notification.
 * Executes production runtime methods on the benchmark thread with immediate coroutine dispatch.
 * History snapshots are prepared outside timing, as production does on its storage worker.
 * Excludes persistence, network, feature/UI collectors, and comment rendering; not frame latency.
 */
@RunWith(AndroidJUnit4::class)
class UnreadStoryOpeningBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null

    @Test fun openUnread30() = measure(feedSize = 30, historySize = 1_000, syncHistory = false)
    @Test fun openUnread500() = measure(feedSize = 500, historySize = 1_000, syncHistory = false)
    @Test fun openAndSyncHistory1000() = measure(feedSize = 500, historySize = 1_000, syncHistory = true)
    @Test fun openAndSyncHistory10000() = measure(feedSize = 500, historySize = 10_000, syncHistory = true)

    private fun measure(feedSize: Int, historySize: Int, syncHistory: Boolean) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val session = StoriesSessionState()
        val saved = SavedItemsRepository(InMemoryKeyValueStore())
        val history = PreparedHistory(historySize)
        val requests = StoryRequests(
            scope, session, unused<AlgoliaRepository>(), unused<HackerNewsRepository>(),
            unused<HackerNewsApi>(), unused<HackerNewsUserItemsLoader>(), saved,
            unused<StoryFeedLoader>(), { emptyList() }, history::contains, { false },
        )
        val runtime = StoriesFeatureRuntime(
            scope, session, requests, saved,
            SavedItemActionUseCase(saved, { 1_000L }, { _, _ -> error("Unused") }, { _, _ -> error("Unused") }),
            history, LoggedOut, Online, StoredUserSettings(InMemoryKeyValueStore(), emptyFlow()),
            { ContentFilters() }, CommentMasterResolver(unused<HackerNewsRepository>()),
            { 1_000L }, { _, _ -> null },
        )
        val stories = List(feedSize) { index ->
            Story("Story $index", TARGET_ID + index, true, false).apply {
                by = "reader"
                url = "https://example.com/$index"
                descendants = 20
                kids = IntArray(4) { id + feedSize + it }
                isLink = true
            }
        }
        runtime.mainStore.replace(stories)
        val target = stories.first()
        try {
            benchmarkRule.measureRepeated {
                runWithTimingDisabled {
                    runtime.mainStore.markRead(target.id, false)
                    history.historyState.value = history.before
                    runtime.initializeHistory()
                    history.historyState.value = history.after
                }
                runtime.openStory(target, showWebsite = false)
                if (syncHistory) runtime.syncHistoryIfChanged()
                sink = runtime.mainStore.state.value
            }
            check(target.isRead && runtime.mainStore.state.value.items.first().presentation.isRead)
        } finally {
            runtime.dispose()
            scope.cancel()
        }
    }

    private class PreparedHistory(count: Int) : ObservableHistoryStore {
        val before = HistoryStoreSnapshot(List(count) { History(it + 1, it.toLong()) }, 1L)
        val after = HistoryStoreSnapshot(before.histories.drop(1) + History(TARGET_ID, 1_000L), 2L)
        override val historyState = MutableStateFlow(before)
        override fun initialize() = Unit
        override fun load() = historyState.value.histories
        override fun contains(id: Int) = id in historyState.value.ids
        override val size get() = load().size
        override val changeVersion get() = historyState.value.changeVersion
        // Persistence and snapshot construction happen on a different worker in production.
        override suspend fun recordHistory(id: Int, createdAtMillis: Long) = true
        override fun record(id: Int, createdAtMillis: Long) = Unit
        override fun remove(id: Int): Unit = error("Unused")
        override fun clear(): Unit = error("Unused")
        override suspend fun removeHistory(id: Int): Boolean = error("Unused")
        override suspend fun clearHistory(): Unit = error("Unused")
    }

    private object LoggedOut : ObservableHackerNewsAccountRepository {
        override val accountState = MutableStateFlow<HackerNewsAccountState>(HackerNewsAccountState.LoggedOut)
        override suspend fun saveAccount(account: HackerNewsAccount): Boolean = error("Unused")
        override suspend fun clearAccount(): Boolean = error("Unused")
    }

    private object Online : ConnectivityService {
        override fun isOnline() = true
        override fun isUnmetered() = true
    }

    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java),
    ) { _, method, _ -> error("Unexpected call: ${method.name}") } as T

    private companion object {
        const val TARGET_ID = 100_001
    }
}
