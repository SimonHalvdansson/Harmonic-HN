package com.simon.harmonichackernews

import android.content.Context
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.network.PreviewCachePolicy
import com.simon.harmonichackernews.platform.AndroidPlatformDependencies
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.settings.AndroidUserSettings
import com.simon.harmonichackernews.utils.ThemeUtils

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
        return HarmonicAppComposition(
            network = NetworkComponent.graph,
            platform = AndroidPlatformDependencies.create(context),
            settingsStore = AndroidKeyValueStore.defaults(context),
            appDataStore = AndroidKeyValueStore.global(context),
            previewCacheStore = AndroidKeyValueStore.named(context, PreviewCachePolicy.STORE_NAME),
            settingsChanges = settings.changes,
            currentTheme = { ThemeUtils.getPreferredTheme(context) },
        )
    }
}
