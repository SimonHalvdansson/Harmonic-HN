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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataSettingsRuntimeDispatchTest {
    @Test
    fun storageScansAndDeletionStayOffCallerAndDialogsReuseCounts() = runBlocking {
        val owner = Thread.currentThread()
        val accesses = CopyOnWriteArrayList<Thread>()
        val files = InMemoryStoryCacheFileStore()
        files.write(StoryCacheKeys.FULL_NAMESPACE, "42.json", "{}".encodeToByteArray())
        val observed = object : StoryCacheFileStore by files {
            override fun list(namespace: String): List<CacheFileInfo> {
                accesses += Thread.currentThread()
                return files.list(namespace)
            }
            override fun clear(namespace: String) {
                accesses += Thread.currentThread()
                files.clear(namespace)
            }
        }
        val store = InMemoryKeyValueStore()
        val service = DataSettingsService(
            AppSettingsRepository(store, emptyFlow()),
            SettingsResetUseCase(store, store, AiSummarySettingsRepository(store, TestCredentialStore(), emptyFlow())),
            SavedItemsRepository(store),
            object : ObservableHackerNewsAccountRepository {
                override val accountState = MutableStateFlow<HackerNewsAccountState>(HackerNewsAccountState.LoggedOut)
                override suspend fun saveAccount(account: HackerNewsAccount) = true
                override suspend fun clearAccount() = true
            },
            null,
            StoryCacheService(StoryCacheRepository(observed, InMemoryStoryCacheMetadataStore()),
                ArticleSnapshotService(KtorHttpClient(client = { error("Offline") }), null), { 0L }),
            StoryPreviewRepository(PreviewContentCoordinator(this), object : LinkSummaryRepository {
                override suspend fun load(pageUrl: String, fallbackTitle: String?) = LinkSummary()
            }, store),
            StoryResourceTintRepository(store), null,
        )
        val runtime = DataSettingsRuntime(this, service, { LocalCalendarDate(2026, 9, 26) })
        withTimeout(5_000) {
            runtime.state.first { it.snapshot.postCacheCount == 1 }
            assertEquals(4, accesses.size)
            repeat(10) {
                runtime.showDialog(DataSettingsDialogState.RESET)
                runtime.showDialog(null)
            }
            assertEquals(4, accesses.size)
            runtime.refresh()
            runtime.showDialog(DataSettingsDialogState.IMPORT)
            val revision = runtime.state.value.revision
            runtime.state.first { it.revision > revision }
            assertEquals(DataSettingsDialogState.IMPORT, runtime.state.value.dialog)
            assertEquals(8, accesses.size)
            runtime.clearPostCache()
            runtime.state.first { it.snapshot.postCacheCount == 0 }
            assertTrue(accesses.all { it !== owner })
            assertTrue(files.list(StoryCacheKeys.FULL_NAMESPACE).isEmpty())
        }
    }
}
