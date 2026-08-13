package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.InMemoryStoryCacheFileStore
import com.simon.harmonichackernews.data.InMemoryStoryCacheMetadataStore
import com.simon.harmonichackernews.data.StoryCacheRepository
import com.simon.harmonichackernews.data.StoryResourceTintRepository
import com.simon.harmonichackernews.cache.ArticleSnapshotService
import com.simon.harmonichackernews.cache.SharedStoryCacheService
import com.simon.harmonichackernews.cache.StoryCacheRuntime
import com.simon.harmonichackernews.cache.StoryCacheUseCase
import com.simon.harmonichackernews.network.DownloadStore
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.NetworkGraph
import com.simon.harmonichackernews.network.PdfDownloadService
import com.simon.harmonichackernews.network.WidgetConfigurationService
import com.simon.harmonichackernews.network.StoryPreviewRepository
import com.simon.harmonichackernews.network.ReplyNotificationRuntime
import com.simon.harmonichackernews.network.ReplyNotificationUseCase
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.navigation.AppLinkNavigator
import com.simon.harmonichackernews.navigation.AppLaunchRouter
import com.simon.harmonichackernews.platform.AppPlatformDependencies
import com.simon.harmonichackernews.platform.CommentsPlatformDependencies
import com.simon.harmonichackernews.platform.StoriesPlatformDependencies
import com.simon.harmonichackernews.platform.SubmissionsPlatformDependencies
import com.simon.harmonichackernews.platform.PlatformCapability
import com.simon.harmonichackernews.presentation.ScreenSessionRegistry
import com.simon.harmonichackernews.presentation.LoginWorkflow
import com.simon.harmonichackernews.presentation.UserMessageStore
import com.simon.harmonichackernews.presentation.UserProfileBlockPort
import com.simon.harmonichackernews.presentation.UserProfileLoader
import com.simon.harmonichackernews.presentation.UserProfileNotificationPort
import com.simon.harmonichackernews.presentation.UserProfileRuntime
import com.simon.harmonichackernews.presentation.WebContentService
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.AiModelDefaultsUseCase
import com.simon.harmonichackernews.settings.AiSummarySettingsRepository
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.DataSettingsService
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.StoredSettingsMutator
import com.simon.harmonichackernews.settings.StoredUserSettings
import com.simon.harmonichackernews.settings.SettingsResetUseCase
import com.simon.harmonichackernews.settings.AppLaunchStateStore
import com.simon.harmonichackernews.settings.AppearanceRuntime
import com.simon.harmonichackernews.settings.NighttimeScheduleStore
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.settings.UserTagsRepository
import com.simon.harmonichackernews.summary.CloudStorySummaryBackend
import com.simon.harmonichackernews.summary.ExtractingStorySummaryBackend
import com.simon.harmonichackernews.summary.PlatformLocalStorySummaryBackend
import com.simon.harmonichackernews.summary.StorySummaryRuntime
import com.simon.harmonichackernews.summary.UnavailableStorySummaryBackend
import com.simon.harmonichackernews.summary.LocalModelService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Clock

/**
 * Platform-neutral application composition shared by Android, iOS, and future hosts.
 *
 * A platform bootstrap owns this object's lifecycle and supplies transports and persistence. The
 * composition deliberately exposes long-lived repositories rather than global singletons so a
 * host can create, retain, and dispose one application graph explicitly.
 */
class HarmonicAppComposition(
    val network: NetworkGraph,
    val platform: AppPlatformDependencies,
    val metadata: AppMetadata = AppMetadata(),
    settingsStore: KeyValueStore,
    appDataStore: KeyValueStore,
    savedItemsRepository: SavedItemsRepository? = null,
    previewCacheStore: KeyValueStore,
    widgetConfigurationStore: KeyValueStore = InMemoryKeyValueStore(),
    widgetRuntimeStore: KeyValueStore = InMemoryKeyValueStore(),
    settingsChanges: Flow<Unit>,
    currentMinutesFromMidnight: () -> Int = { 0 },
    systemDark: () -> Boolean = { false },
    val localModels: LocalModelService? = null,
    storyCacheRepository: StoryCacheRepository = StoryCacheRepository(
        InMemoryStoryCacheFileStore(),
        InMemoryStoryCacheMetadataStore(),
    ),
    articleSnapshotStore: DownloadStore? = null,
    pdfDownloadStore: DownloadStore? = null,
    val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    val userMessages: UserMessageStore = UserMessageStore(),
) {
    val sessions = ScreenSessionRegistry()
    val navigation = MainNavigationStore()
    val webContent = WebContentService()
    val launches = AppLaunchRouter(navigation)
    val links = AppLinkNavigator(navigation, platform.capabilities.externalLinks)
    val launchState = AppLaunchStateStore(appDataStore)
    val appearance = AppearanceRuntime(
        settings = settingsStore,
        scheduleStore = NighttimeScheduleStore(appDataStore),
        launchState = launchState,
        currentMinutesFromMidnight = currentMinutesFromMidnight,
        systemDark = systemDark,
    )
    val userSettings: UserSettings = StoredUserSettings(
        store = settingsStore,
        changes = settingsChanges,
        theme = { appearance.selection().theme },
    )
    val settings = AppSettingsRepository(userSettings, StoredSettingsMutator(settingsStore))
    val contentFilters = ContentFilterRepository(settingsStore)
    val userTags = UserTagsRepository(settingsStore)
    val savedItems = savedItemsRepository ?: SavedItemsRepository(appDataStore)
    val storyResourceTints = StoryResourceTintRepository(appDataStore)
    val aiSummarySettings = AiSummarySettingsRepository(
        store = settingsStore,
        credentials = platform.credentials,
        changes = settingsChanges,
    )
    val aiModelDefaults = AiModelDefaultsUseCase(
        settings = aiSummarySettings,
        catalog = network.aiModelCatalogRepository,
    )
    val settingsReset = SettingsResetUseCase(
        defaultSettings = settingsStore,
        globalSettings = appDataStore,
        credentials = platform.credentials,
    )
    val previewResources = StoryPreviewRepository(
        coordinator = network.previewContentCoordinator,
        linkSummaries = network.linkSummaryRepository,
        store = previewCacheStore,
    )
    val storyCache = SharedStoryCacheService(
        repository = storyCacheRepository,
        articleSnapshots = ArticleSnapshotService(network.httpClient, articleSnapshotStore),
        nowMillis = nowMillis,
    )
    val pdfDownloads = PdfDownloadService(network.httpClient, pdfDownloadStore, nowMillis)
    val widgets = WidgetConfigurationService(
        configStore = widgetConfigurationStore,
        runtimeStore = widgetRuntimeStore,
        repository = network.hackerNewsRepository,
    )
    val hackerNewsUser = HackerNewsUserService(
        session = network.hackerNewsSession,
        accounts = platform.accounts,
    )
    val login = LoginWorkflow(platform.accounts, hackerNewsUser)
    val replyNotifications: ReplyNotificationRuntime? =
        platform.capabilities.replyNotifications.getOrNull()?.let { notificationPlatform ->
            ReplyNotificationRuntime(
                useCase = ReplyNotificationUseCase(network.replyScanner, appDataStore),
                platform = notificationPlatform,
            )
        }
    val dataSettings = DataSettingsService(
        settings = settings,
        settingsReset = settingsReset,
        savedItems = savedItems,
        accounts = platform.accounts,
        history = platform.capabilities.history.getOrNull(),
        storyCache = storyCache,
        previewResources = previewResources,
        storyResourceTints = storyResourceTints,
    )

    fun createUserProfileRuntime(
        username: String,
        monthNames: List<String>,
    ): UserProfileRuntime = UserProfileRuntime(
        username = username,
        monthNames = monthNames,
        loader = UserProfileLoader(network.hackerNewsApi::getUser),
        accounts = platform.accounts,
        blocks = object : UserProfileBlockPort {
            override fun isBlocked(username: String): Boolean =
                contentFilters.containsUser(username)

            override fun setBlocked(username: String, blocked: Boolean): Boolean =
                if (blocked) contentFilters.addUser(username) else contentFilters.removeUser(username)
        },
        notifications = object : UserProfileNotificationPort {
            override fun configuredUsername(): String? =
                replyNotifications?.configuredUsername

            override suspend fun enable(username: String): Boolean =
                replyNotifications?.enable(username) is com.simon.harmonichackernews.network.ReplySubscriptionResult.Enabled

            override fun disable() {
                replyNotifications?.disable()
            }
        },
    )

    fun createStoryCacheRuntime(scope: CoroutineScope): StoryCacheRuntime {
        val useCase = StoryCacheUseCase(
            hackerNewsRepository = network.hackerNewsRepository,
            algoliaRepository = network.algoliaRepository,
            sink = storyCache,
        )
        return StoryCacheRuntime(scope, useCase::execute)
    }

    val localSummaryCanAttempt: Boolean
        get() = platform.capabilities.localSummary.getOrNull()?.canAttempt() == true

    val localSummaryEngine
        get() = platform.capabilities.localSummary.getOrNull()

    fun createStorySummaryRuntime(scope: CoroutineScope): StorySummaryRuntime {
        val localBackend = when (val capability = platform.capabilities.localSummary) {
            is PlatformCapability.Available -> PlatformLocalStorySummaryBackend(capability.service)
            is PlatformCapability.Unavailable -> UnavailableStorySummaryBackend(capability.reason)
        }
        return StorySummaryRuntime(
            scope = scope,
            cloudBackend = CloudStorySummaryBackend(network.summaryUseCase) {
                aiSummarySettings.cloudConfig()
            },
            localBackend = ExtractingStorySummaryBackend(
                useCase = network.summaryUseCase,
                textBackend = localBackend,
            ),
        )
    }

    /** Compatibility view for callers that only need an optional capability. */
    val platformCapabilities get() = platform.capabilities

    /** Feature-sized platform views derived from the one application-scoped capability graph. */
    fun storiesPlatformDependencies(): StoriesPlatformDependencies = StoriesPlatformDependencies(
        accounts = platform.accounts,
        history = platform.capabilities.history.requireService(),
        connectivity = platform.capabilities.connectivity.requireService(),
        externalLinks = platform.capabilities.externalLinks.requireService(),
    )

    fun commentsPlatformDependencies(): CommentsPlatformDependencies =
        CommentsPlatformDependencies(
            accounts = platform.accounts,
            externalLinks = platform.capabilities.externalLinks.requireService(),
            sharing = platform.capabilities.sharing.requireService(),
            clipboard = platform.capabilities.clipboard.requireService(),
        )

    fun submissionsPlatformDependencies(): SubmissionsPlatformDependencies =
        SubmissionsPlatformDependencies(
            externalLinks = platform.capabilities.externalLinks.requireService(),
        )
}
