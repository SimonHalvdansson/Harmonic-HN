package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.network.IosNetworkComponent
import com.simon.harmonichackernews.platform.IosPlatformBindings
import com.simon.harmonichackernews.platform.createIosPlatformDependencies
import com.simon.harmonichackernews.settings.AppLaunchPreferenceKeys
import com.simon.harmonichackernews.settings.IosKeyValueStore
import com.simon.harmonichackernews.settings.ThemePreferences
import platform.Foundation.NSUserDefaults

/**
 * Swift-facing owner for one Harmonic application graph.
 *
 * Run `:shared_ui:assembleHarmonicSharedXCFramework`, link the generated
 * `HarmonicShared.xcframework`, construct this object at application startup, retain it for the
 * process lifetime, and call [close] during host teardown. The Xcode host remains responsible for
 * navigation and for whichever optional native services it advertises through
 * [IosPlatformBindings]. Unsupported facilities remain typed unavailable capabilities.
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
    val network = IosNetworkComponent(userAgent)
    val platform = createIosPlatformDependencies(appData, bindings)
    val app = HarmonicAppComposition(
        network = network.graph,
        platform = platform,
        settingsStore = preferences,
        appDataStore = appData,
        settingsChanges = preferences.changes,
        currentTheme = {
            preferences.getString(ThemePreferences.KEY, ThemePreferences.DEFAULT)
        },
    )

    fun close() {
        network.close()
    }
}
