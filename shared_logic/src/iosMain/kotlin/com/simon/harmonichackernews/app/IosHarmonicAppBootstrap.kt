package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.network.IosNetworkComponent
import com.simon.harmonichackernews.network.PreviewCachePolicy
import com.simon.harmonichackernews.platform.IosPlatformBindings
import com.simon.harmonichackernews.platform.createIosPlatformDependencies
import com.simon.harmonichackernews.settings.AppLaunchPreferenceKeys
import com.simon.harmonichackernews.settings.IosKeyValueStore
import platform.Foundation.NSUserDefaults
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.network.DownloadStore
import com.simon.harmonichackernews.presentation.UserMessageStore
import com.simon.harmonichackernews.summary.LocalModelService

/** Native runtime and storage decisions required before the iOS host creates its app graph. */
class IosHostRuntimeBindings(
    val metadata: AppMetadata,
    val currentMinutesFromMidnight: () -> Int,
    val systemDark: () -> Boolean,
    val storyCacheRepository: StoryCacheRepository,
    val articleSnapshotStore: DownloadStore? = null,
    val pdfDownloadStore: DownloadStore? = null,
    val localModels: LocalModelService? = null,
    val userMessages: UserMessageStore = UserMessageStore(),
)

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
    val network = IosNetworkComponent(userAgent)
    val savedItems = SavedItemsRepository(appData)
    val platform = createIosPlatformDependencies(
        appData,
        bindings,
    )
    val app = HarmonicAppComposition(
        network = network.graph,
        platform = platform,
        host = HarmonicHostConfiguration(
            metadata = runtime.metadata,
            settingsStore = preferences,
            appDataStore = appData,
            savedItemsRepository = savedItems,
            previewCacheStore = previewCache,
            settingsChanges = preferences.changes,
            currentMinutesFromMidnight = runtime.currentMinutesFromMidnight,
            systemDark = runtime.systemDark,
            storyCacheRepository = runtime.storyCacheRepository,
            articleSnapshotStore = runtime.articleSnapshotStore,
            pdfDownloadStore = runtime.pdfDownloadStore,
            localModels = runtime.localModels,
            userMessages = runtime.userMessages,
        ),
    )

    fun close() {
        network.close()
    }
}
