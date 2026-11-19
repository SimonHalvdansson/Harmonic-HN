package com.simon.harmonichackernews.settings

import com.simon.harmonichackernews.cache.ArticleSnapshotService
import com.simon.harmonichackernews.cache.StoryCacheService
import com.simon.harmonichackernews.data.*
import com.simon.harmonichackernews.network.*
import com.simon.harmonichackernews.platform.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ReadingHistoryRuntimeTest {
    @Test
    fun exportWaitsForInitializationAndUsesTheStoredSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TestKeyValueStore(mapOf(StoredHistoryKeys.HISTORIES to "42q123-7q456"))
        val history = StoredHistoryStore(store, storageDispatcher = dispatcher)
        try {
            val runtime = runtime(history, dispatcher)
            val effect = async(start = CoroutineStart.UNDISPATCHED) { runtime.effects.first() }
            // Neither the store nor the settings refresh has initialized yet.
            assertEquals(0, runtime.state.value.snapshot.historyCount)
            runtime.exportReadingHistory()
            runCurrent()
            val document = assertIs<DataSettingsRuntimeEffect.CreateExportDocument>(effect.await())
            assertEquals("text/csv", document.mimeType)
            assertEquals("HarmonicReadingHistory2026-01-02.csv", document.filename)
            assertEquals(ReadingHistoryExport.csv(history.load()), document.content)
            assertEquals(2, runtime.state.value.snapshot.historyCount)
            assertEquals("42q123-7q456", store.getString(StoredHistoryKeys.HISTORIES))
            history.clearHistory()
            assertEquals(4, document.content.split("\r\n").size)
        } finally { history.close() }
    }

    @Test
    fun emptyHistoryProducesAMessageRatherThanAnEmptyDocument() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val history = StoredHistoryStore(TestKeyValueStore(), storageDispatcher = dispatcher)
        try {
            val runtime = runtime(history, dispatcher)
            val effect = async(start = CoroutineStart.UNDISPATCHED) { runtime.effects.first() }
            runtime.exportReadingHistory()
            runCurrent()
            assertEquals(DataSettingsRuntimeEffect.Message("No reading history"), effect.await())
        } finally { history.close() }
    }

    @Test
    fun failedExportReportsAnError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val history = StoredHistoryStore(TestKeyValueStore(), storageDispatcher = dispatcher)
        try {
            var fail = false
            val failing = object : ObservableHistoryStore by history {
                override suspend fun awaitHistories(): List<History> {
                    if (fail) error("Read failed")
                    return history.awaitHistories()
                }
            }
            val runtime = runtime(failing, dispatcher)
            runCurrent()
            fail = true
            val effect = async(start = CoroutineStart.UNDISPATCHED) { runtime.effects.first() }
            runtime.exportReadingHistory()
            runCurrent()
            assertEquals(DataSettingsRuntimeEffect.Message("Could not export reading history"), effect.await())
        } finally { history.close() }
    }

    private fun CoroutineScope.runtime(history: ObservableHistoryStore, dispatcher: CoroutineDispatcher) =
        DataSettingsRuntime(this, service(history), { LocalCalendarDate(2026, 1, 2) }, dispatcher)

    private fun CoroutineScope.service(history: ObservableHistoryStore): DataSettingsService {
        val store = InMemoryKeyValueStore()
        return DataSettingsService(
            AppSettingsRepository(store, emptyFlow()),
            SettingsResetUseCase(store, store, AiSummarySettingsRepository(store, TestCredentialStore(), emptyFlow())),
            SavedItemsRepository(store),
            object : ObservableHackerNewsAccountRepository {
                override val accountState = MutableStateFlow<HackerNewsAccountState>(HackerNewsAccountState.LoggedOut)
                override suspend fun saveAccount(account: HackerNewsAccount) = true
                override suspend fun clearAccount() = true
            },
            history,
            StoryCacheService(StoryCacheRepository(InMemoryStoryCacheFileStore(), InMemoryStoryCacheMetadataStore()),
                ArticleSnapshotService(KtorHttpClient(client = { error("Offline") }), null), { 0L }),
            StoryPreviewRepository(PreviewContentCoordinator(this), object : LinkSummaryRepository {
                override suspend fun load(pageUrl: String, fallbackTitle: String?) = LinkSummary()
            }, store),
            StoryResourceTintRepository(store), null,
        )
    }
}
