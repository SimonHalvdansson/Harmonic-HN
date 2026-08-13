package com.simon.harmonichackernews

import android.content.Context
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.AppMetadata
import com.simon.harmonichackernews.app.AppBootstrapPolicy
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.network.AndroidNetworkEnvironment
import com.simon.harmonichackernews.network.PreviewCachePolicy
import com.simon.harmonichackernews.platform.createAndroidPlatformDependencies
import com.simon.harmonichackernews.settings.AndroidKeyValueStore
import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.presentation.UserMessageStore
import com.simon.harmonichackernews.settings.AppLaunchPreferenceKeys
import com.simon.harmonichackernews.utils.AndroidDownloadStore
import com.simon.harmonichackernews.network.StableHash
import com.simon.harmonichackernews.platform.StorageKeyPolicy
import com.simon.harmonichackernews.utils.AndroidStoryCacheFileStore
import com.simon.harmonichackernews.utils.AndroidStoryCacheMetadataStore
import com.simon.harmonichackernews.data.StoryCacheKeys
import java.io.File
import java.util.Calendar
import com.simon.harmonichackernews.utils.ThemeUtils
import com.simon.harmonichackernews.summary.local.createAndroidLocalModelService
import com.simon.harmonichackernews.utils.loadAndroidAdBlocklist

/** Resolves the graph from its actual Android Application owner without a static locator. */
internal val Context.harmonicAppComposition: HarmonicAppComposition
    get() = (applicationContext as? HarmonicApplication)?.composition
        ?: error("HarmonicApplication does not own this process")

internal fun createAndroidAppComposition(context: Context): HarmonicAppComposition {
    val network = AndroidNetworkEnvironment(context)
    val settingsStore = AndroidKeyValueStore.defaults(context)
    val appDataStore = AndroidKeyValueStore.global(context)
    val savedItems = SavedItemsRepository(appDataStore)
    val userMessages = UserMessageStore()
    val localModels = createAndroidLocalModelService(context)
    val storyCache = StoryCacheRepository(
        files = AndroidStoryCacheFileStore(context),
        metadata = AndroidStoryCacheMetadataStore(
            context.getSharedPreferences(
                AppLaunchPreferenceKeys.STORE_NAME,
                Context.MODE_PRIVATE,
            ),
        ),
    )
    return HarmonicAppComposition(
        network = network.graph,
        platform = createAndroidPlatformDependencies(
            context,
            localModels = localModels,
            userMessages = userMessages,
        ),
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
        savedItemsRepository = savedItems,
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
        storyCacheRepository = storyCache,
        articleSnapshotStore = AndroidDownloadStore(
            root = File(context.filesDir, StoryCacheKeys.ARTICLE_NAMESPACE),
            fileNameForKey = { storyId -> StoryCacheKeys.articleFile(storyId.toInt()) },
            targetSuffix = ".html",
            onCommit = { storyId, metadata ->
                storyCache.recordArticleMetadata(
                    storyId = storyId.toInt(),
                    sourceUrl = metadata.sourceUrl,
                    contentType = metadata.contentType,
                )
            },
            onRemove = { file ->
                file.name.removeSuffix(".html").toIntOrNull()?.let(
                    storyCache::removeArticleMetadata,
                )
            },
        ),
        pdfDownloadStore = AndroidDownloadStore(
            root = File(
                context.externalCacheDir ?: context.cacheDir,
                StorageKeyPolicy.PDF_CACHE_DIRECTORY,
            ),
            fileNameForKey = { url -> StableHash.sha256Hex(url) + ".pdf" },
            targetSuffix = ".pdf",
        ),
        userMessages = userMessages,
    ).also { composition ->
        loadAndroidAdBlocklist(context.resources, composition.webContent.adBlocklist)
    }
}
