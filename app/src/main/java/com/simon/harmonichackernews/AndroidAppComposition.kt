package com.simon.harmonichackernews

import android.content.Context
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.AppMetadata
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.PreviewCachePolicy
import com.simon.harmonichackernews.platform.AndroidPlatformDependencies
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.platform.StoredBookmarkStore

/** Process-owned Android entry point into the platform-neutral application graph. */
object AndroidAppComposition {
    @Volatile
    private var active: HarmonicAppComposition? = null

    fun initialize(context: Context): HarmonicAppComposition = get(context)

    fun get(context: Context): HarmonicAppComposition = active ?: synchronized(this) {
        active ?: create(context.applicationContext).also { active = it }
    }

    private fun create(context: Context): HarmonicAppComposition {
        val settings = AndroidUserSettings.get(context)
        val appDataStore = AndroidKeyValueStore.global(context)
        val savedItems = SavedItemsRepository(appDataStore)
        return HarmonicAppComposition(
            network = NetworkComponent.graph,
            platform = AndroidPlatformDependencies.create(
                context,
                bookmarkStore = StoredBookmarkStore(savedItems),
            ),
            metadata = AppMetadata(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                buildNumber = BuildConfig.VERSION_CODE.toString(),
                buildType = BuildConfig.BUILD_TYPE,
                debug = BuildConfig.DEBUG,
                debugSettingsEnabled = BuildConfig.DEBUG_SETTINGS_ENABLED,
            ),
            settingsStore = AndroidKeyValueStore.defaults(context),
            appDataStore = appDataStore,
            savedItemsRepository = savedItems,
            previewCacheStore = AndroidKeyValueStore.named(context, PreviewCachePolicy.STORE_NAME),
            settingsChanges = settings.changes,
            currentTheme = { ThemeUtils.getPreferredTheme(context) },
        )
    }
}
