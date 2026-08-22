package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.data.InMemoryStoryCacheFileStore
import com.simon.harmonichackernews.data.InMemoryStoryCacheMetadataStore
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.network.DownloadStore
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.summary.LocalModelService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.Clock

/**
 * Complete platform contract for one production application graph.
 *
 * Hosts must make time, appearance, metadata and cache-lifetime decisions explicitly. Optional
 * native features stay nullable, while the named [inMemory] factory keeps preview and test hosts
 * concise without making non-persistent defaults look production-ready.
 */
class HarmonicHostConfiguration(
    val metadata: AppMetadata,
    val settingsStore: KeyValueStore,
    val appDataStore: KeyValueStore,
    val previewCacheStore: KeyValueStore,
    val settingsChanges: Flow<Unit>,
    val appearanceChanges: Flow<Unit> = emptyFlow(),
    val currentMinutesFromMidnight: () -> Int,
    val systemDark: () -> Boolean,
    val storyCacheRepository: StoryCacheRepository,
    val showCommentsUpButtonByDefault: Boolean = false,
    val savedItemsRepository: SavedItemsRepository? = null,
    val widgetConfigurationStore: KeyValueStore = InMemoryKeyValueStore(),
    val widgetRuntimeStore: KeyValueStore = InMemoryKeyValueStore(),
    val localModels: LocalModelService? = null,
    val articleSnapshotStore: DownloadStore? = null,
    val pdfDownloadStore: DownloadStore? = null,
    val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    companion object {
        /** Explicitly non-persistent configuration for previews and host-contract checks. */
        fun inMemory(
            metadata: AppMetadata,
            currentMinutesFromMidnight: () -> Int,
            systemDark: () -> Boolean,
            settingsChanges: Flow<Unit>,
            showCommentsUpButtonByDefault: Boolean = false,
            settingsStore: KeyValueStore = InMemoryKeyValueStore(),
            appDataStore: KeyValueStore = InMemoryKeyValueStore(),
            previewCacheStore: KeyValueStore = InMemoryKeyValueStore(),
        ): HarmonicHostConfiguration = HarmonicHostConfiguration(
            metadata = metadata,
            settingsStore = settingsStore,
            appDataStore = appDataStore,
            previewCacheStore = previewCacheStore,
            settingsChanges = settingsChanges,
            appearanceChanges = emptyFlow(),
            currentMinutesFromMidnight = currentMinutesFromMidnight,
            systemDark = systemDark,
            showCommentsUpButtonByDefault = showCommentsUpButtonByDefault,
            storyCacheRepository = StoryCacheRepository(
                InMemoryStoryCacheFileStore(),
                InMemoryStoryCacheMetadataStore(),
            ),
        )
    }
}
