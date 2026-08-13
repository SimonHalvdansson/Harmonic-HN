package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.network.IosNetworkComponent
import com.simon.harmonichackernews.network.PreviewCachePolicy
import com.simon.harmonichackernews.platform.IosPlatformBindings
import com.simon.harmonichackernews.platform.StorageKeyPolicy
import com.simon.harmonichackernews.platform.createIosPlatformDependencies
import com.simon.harmonichackernews.settings.AppLaunchPreferenceKeys
import com.simon.harmonichackernews.settings.IosKeyValueStore
import platform.Foundation.NSUserDefaults
import com.simon.harmonichackernews.summary.LocalModelService
import kotlinx.io.files.Path

/** Native runtime and storage decisions required before the iOS host creates its app graph. */
class IosHostRuntimeBindings(
    val metadata: AppMetadata,
    val currentMinutesFromMidnight: () -> Int,
    val systemDark: () -> Boolean,
    val filesDirectory: String,
    val cacheDirectory: String,
    val localModels: LocalModelService? = null,
) {
    init {
        require(filesDirectory.isNotBlank()) { "The iOS files directory is required" }
        require(cacheDirectory.isNotBlank()) { "The iOS cache directory is required" }
    }
}

/**
 * Swift-facing owner for one Harmonic application graph.
 *
 * Run `:shared_ui:assembleHarmonicSharedXCFramework`, link the generated
 * `HarmonicShared.xcframework`, construct this object at application startup, retain it for the
 * process lifetime, and call [close] during host teardown. The Xcode host remains responsible for
 * navigation and for the native services it supplies through [IosPlatformBindings].
 */
class IosHarmonicAppBootstrap(
    userAgent: String,
    bindings: IosPlatformBindings,
    runtime: IosHostRuntimeBindings,
    settingsDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    appDataDefaults: NSUserDefaults = NSUserDefaults(
        suiteName = AppLaunchPreferenceKeys.STORE_NAME,
    ),
) {
    val preferences = IosKeyValueStore(settingsDefaults)
    val appData = IosKeyValueStore(appDataDefaults)
    val previewCache = IosKeyValueStore(NSUserDefaults(suiteName = PreviewCachePolicy.STORE_NAME))
    val fileAccess = IosKeyValueStore(
        NSUserDefaults(suiteName = StorageKeyPolicy.FILE_ACCESS_STORE),
    )
    val network = IosNetworkComponent(userAgent)
    val platform = createIosPlatformDependencies(
        appData,
        bindings,
    )
    val persistentStorage = HarmonicPersistentStorageFactory.create(
        roots = HarmonicStorageRoots(
            files = Path(runtime.filesDirectory),
            pdfCache = Path(runtime.cacheDirectory, StorageKeyPolicy.PDF_CACHE_DIRECTORY),
        ),
        appDataStore = appData,
        fileAccessStore = fileAccess,
    )
    val app = HarmonicAppComposition(
        network = network.graph,
        platform = platform,
        host = HarmonicHostConfiguration(
            metadata = runtime.metadata,
            settingsStore = preferences,
            appDataStore = appData,
            previewCacheStore = previewCache,
            settingsChanges = preferences.changes,
            currentMinutesFromMidnight = runtime.currentMinutesFromMidnight,
            systemDark = runtime.systemDark,
            storyCacheRepository = persistentStorage.storyCacheRepository,
            articleSnapshotStore = persistentStorage.articleSnapshotStore,
            pdfDownloadStore = persistentStorage.pdfDownloadStore,
            localModels = runtime.localModels,
        ),
    )

    /** Creates independent navigation and screen state for one UIWindowScene. */
    fun createScene(): HarmonicSceneComposition = app.createScene()

    fun close() {
        network.close()
    }
}
