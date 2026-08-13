package com.simon.harmonichackernews

import android.content.Context
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicHostConfiguration
import com.simon.harmonichackernews.app.AppMetadata
import com.simon.harmonichackernews.app.AppBootstrapPolicy
import com.simon.harmonichackernews.app.HarmonicPersistentStorageFactory
import com.simon.harmonichackernews.app.HarmonicStorageRoots
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.network.AndroidNetworkEnvironment
import com.simon.harmonichackernews.network.PreviewCachePolicy
import com.simon.harmonichackernews.platform.createAndroidPlatformDependencies
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.presentation.UserMessageStore
import com.simon.harmonichackernews.platform.StorageKeyPolicy
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.summary.local.createAndroidLocalModelService
import java.util.Calendar
import kotlinx.io.files.Path

/** Resolves the graph from its actual Android Application owner without a static locator. */
internal val Context.harmonicAppComposition: HarmonicAppComposition
    get() = (applicationContext as? HarmonicApplication)?.composition
        ?: error("HarmonicApplication does not own this process")

internal fun createAndroidAppComposition(context: Context): HarmonicAppComposition {
    val network = AndroidNetworkEnvironment(context)
    val settingsStore = AndroidKeyValueStore.defaults(context)
    val appDataStore = AndroidKeyValueStore.global(context)
    val userMessages = UserMessageStore()
    val localModels = createAndroidLocalModelService(context)
    val filesRoot = Path(context.filesDir.absolutePath)
    val persistentStorage = HarmonicPersistentStorageFactory.create(
        roots = HarmonicStorageRoots(
            files = filesRoot,
            pdfCache = Path(
                (context.externalCacheDir ?: context.cacheDir).absolutePath,
                StorageKeyPolicy.PDF_CACHE_DIRECTORY,
            ),
        ),
        appDataStore = appDataStore,
        fileAccessStore = AndroidKeyValueStore.named(
            context,
            StorageKeyPolicy.FILE_ACCESS_STORE,
        ),
    )
    return HarmonicAppComposition(
        network = network.graph,
        platform = createAndroidPlatformDependencies(
            context,
            localModels = localModels,
            userMessages = userMessages,
        ),
        host = HarmonicHostConfiguration(
            metadata = AppMetadata(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                buildNumber = BuildConfig.VERSION_CODE.toString(),
                buildType = BuildConfig.BUILD_TYPE,
                debug = BuildConfig.DEBUG,
                debugSettingsEnabled = BuildConfig.DEBUG_SETTINGS_ENABLED,
            ),
            settingsStore = settingsStore,
            appDataStore = appDataStore,
            previewCacheStore = AndroidKeyValueStore.named(context, PreviewCachePolicy.STORE_NAME),
            widgetConfigurationStore = AndroidKeyValueStore.named(
                context,
                AppBootstrapPolicy.WIDGET_CONFIGURATION_STORE,
            ),
            widgetRuntimeStore = AndroidKeyValueStore.named(
                context,
                AppBootstrapPolicy.WIDGET_RUNTIME_STORE,
            ),
            settingsChanges = settingsStore.changes,
            currentMinutesFromMidnight = {
                Calendar.getInstance().let { calendar ->
                    AppBootstrapPolicy.minutesFromMidnight(
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                    )
                }
            },
            systemDark = { ThemeUtils.uiModeNight(context) },
            localModels = localModels,
            storyCacheRepository = persistentStorage.storyCacheRepository,
            articleSnapshotStore = persistentStorage.articleSnapshotStore,
            pdfDownloadStore = persistentStorage.pdfDownloadStore,
            userMessages = userMessages,
        ),
    )
}
