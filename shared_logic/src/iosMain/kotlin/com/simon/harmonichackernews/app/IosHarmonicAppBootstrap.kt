package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.network.IosNetworkComponent
import com.simon.harmonichackernews.network.PreviewCachePolicy
import com.simon.harmonichackernews.platform.IosPlatformBindings
import com.simon.harmonichackernews.platform.createIosPlatformDependencies
import com.simon.harmonichackernews.settings.AppLaunchPreferenceKeys
import com.simon.harmonichackernews.settings.IosKeyValueStore
import platform.Foundation.NSUserDefaults
import com.simon.harmonichackernews.data.SavedItemsRepository

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
    settingsDefaults: NSUserDefaults,
    appDataDefaults: NSUserDefaults,
) {
    constructor(userAgent: String, bindings: IosPlatformBindings) : this(
        userAgent,
        bindings,
        NSUserDefaults.standardUserDefaults,
        NSUserDefaults(suiteName = AppLaunchPreferenceKeys.STORE_NAME),
    )

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
        settingsStore = preferences,
        appDataStore = appData,
        savedItemsRepository = savedItems,
        previewCacheStore = previewCache,
        settingsChanges = preferences.changes,
    )

    fun close() {
        network.close()
    }
}
