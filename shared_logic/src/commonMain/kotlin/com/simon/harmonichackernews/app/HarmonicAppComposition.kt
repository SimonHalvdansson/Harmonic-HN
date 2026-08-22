package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.StoryResourceTintRepository
import com.simon.harmonichackernews.cache.ArticleSnapshotService
import com.simon.harmonichackernews.cache.SharedStoryCacheService
import com.simon.harmonichackernews.cache.StoryCacheRuntime
import com.simon.harmonichackernews.cache.StoryCacheUseCase
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.NetworkGraph
import com.simon.harmonichackernews.network.PdfDownloadService
import com.simon.harmonichackernews.network.WidgetConfigurationService
import com.simon.harmonichackernews.network.WidgetRefreshRuntime
import com.simon.harmonichackernews.network.ReferenceLinkPreviewRuntime
import com.simon.harmonichackernews.network.StoryPreviewRepository
import com.simon.harmonichackernews.network.ReplyNotificationRuntime
import com.simon.harmonichackernews.network.ReplyNotificationUseCase
import com.simon.harmonichackernews.platform.AppPlatformDependencies
import com.simon.harmonichackernews.platform.CommentsPlatformDependencies
import com.simon.harmonichackernews.platform.StoriesPlatformDependencies
import com.simon.harmonichackernews.platform.ConfiguredExternalLinkOpener
import com.simon.harmonichackernews.presentation.LoginWorkflow
import com.simon.harmonichackernews.presentation.UserMessageStore
import com.simon.harmonichackernews.presentation.UserProfileBlockPort
import com.simon.harmonichackernews.presentation.UserProfileLoader
import com.simon.harmonichackernews.presentation.UserProfileNotificationPort
import com.simon.harmonichackernews.presentation.UserProfileRuntime
import com.simon.harmonichackernews.presentation.UserProfileSession
import com.simon.harmonichackernews.presentation.WebContentService
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.AiModelDefaultsUseCase
import com.simon.harmonichackernews.settings.AiSummarySettingsRepository
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.DataSettingsService
import com.simon.harmonichackernews.settings.StoredSettingsMutator
import com.simon.harmonichackernews.settings.StoredUserSettings
import com.simon.harmonichackernews.settings.SettingsResetUseCase
import com.simon.harmonichackernews.settings.AppLaunchStateStore
import com.simon.harmonichackernews.settings.AppearanceRuntime
import com.simon.harmonichackernews.settings.NighttimeScheduleStore
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.settings.UserTagsRepository
import com.simon.harmonichackernews.settings.DataSettingsRuntime
import com.simon.harmonichackernews.summary.CloudStorySummaryBackend
import com.simon.harmonichackernews.summary.ExtractingStorySummaryBackend
import com.simon.harmonichackernews.summary.PlatformLocalStorySummaryBackend
import com.simon.harmonichackernews.summary.StorySummaryRuntime
import com.simon.harmonichackernews.summary.UnavailableStorySummaryBackend
import com.simon.harmonichackernews.summary.LocalSummarySettingsRuntime
import kotlinx.coroutines.CoroutineScope

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
    val host: HarmonicHostConfiguration,
) {
    val metadata: AppMetadata = host.metadata
    val localModels = host.localModels
    val nowMillis = host.nowMillis
    val storyUpdates = StoryUpdateBus()
    val webContent = WebContentService()
    val launchState = AppLaunchStateStore(host.appDataStore)
    val appearance = AppearanceRuntime(
        settings = host.settingsStore,
        scheduleStore = NighttimeScheduleStore(host.appDataStore),
        launchState = launchState,
        settingsChanges = host.settingsChanges,
        appearanceChanges = host.appearanceChanges,
        currentMinutesFromMidnight = host.currentMinutesFromMidnight,
        systemDark = host.systemDark,
    )
    val userSettings: UserSettings = StoredUserSettings(
        store = host.settingsStore,
        changes = host.settingsChanges,
        theme = { appearance.selection().theme },
        showCommentsUpButtonByDefault = host.showCommentsUpButtonByDefault,
    )
    val externalLinks = ConfiguredExternalLinkOpener(platform.externalLinks) {
        userSettings.reading.externalBrowser
    }
    val settings = AppSettingsRepository(userSettings, StoredSettingsMutator(host.settingsStore))
    val contentFilters = ContentFilterRepository(host.settingsStore)
    val userTags = UserTagsRepository(host.settingsStore)
    val savedItems = host.savedItemsRepository ?: SavedItemsRepository(host.appDataStore)
    val storyResourceTints = StoryResourceTintRepository(host.appDataStore)
    val aiSummarySettings = AiSummarySettingsRepository(
        store = host.settingsStore,
        credentials = platform.credentials,
        changes = host.settingsChanges,
    )
    val aiModelDefaults = AiModelDefaultsUseCase(
        settings = aiSummarySettings,
        catalog = network.aiModelCatalogRepository,
    )
    val settingsReset = SettingsResetUseCase(
        defaultSettings = host.settingsStore,
        globalSettings = host.appDataStore,
        credentials = platform.credentials,
    )
    val previewResources = StoryPreviewRepository(
        coordinator = network.previewContentCoordinator,
        linkSummaries = network.linkSummaryRepository,
        store = host.previewCacheStore,
    )
    val storyCache = SharedStoryCacheService(
        repository = host.storyCacheRepository,
        articleSnapshots = ArticleSnapshotService(network.httpClient, host.articleSnapshotStore),
        nowMillis = nowMillis,
    )
    val pdfDownloads = PdfDownloadService(network.httpClient, host.pdfDownloadStore, nowMillis)
    val widgets = WidgetConfigurationService(
        configStore = host.widgetConfigurationStore,
        runtimeStore = host.widgetRuntimeStore,
        repository = network.hackerNewsRepository,
    )
    val widgetRefresh = WidgetRefreshRuntime(widgets, nowMillis)
    val hackerNewsUser = HackerNewsUserService(
        session = network.hackerNewsSession,
        accounts = platform.accounts,
    )
    val login = LoginWorkflow(platform.accounts, hackerNewsUser)
    val replyNotifications: ReplyNotificationRuntime? =
        platform.replyNotifications?.let { notificationPlatform ->
            ReplyNotificationRuntime(
                useCase = ReplyNotificationUseCase(network.replyScanner, host.appDataStore),
                platform = notificationPlatform,
            )
        }
    val dataSettings = DataSettingsService(
        settings = settings,
        settingsReset = settingsReset,
        savedItems = savedItems,
        accounts = platform.accounts,
        history = platform.history,
        storyCache = storyCache,
        previewResources = previewResources,
        storyResourceTints = storyResourceTints,
    )

    /** Creates navigation, retained screen state, and transient messages for one host scene. */
    fun createScene(
        userMessages: UserMessageStore = UserMessageStore(),
    ): HarmonicSceneComposition = HarmonicSceneComposition(this, userMessages)

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

    fun createUserProfileSession(
        scope: CoroutineScope,
        username: String,
        monthNames: List<String>,
    ): UserProfileSession = UserProfileSession(
        scope,
        createUserProfileRuntime(username, monthNames),
        username,
    )

    fun createDataSettingsRuntime(
        scope: CoroutineScope,
        today: () -> com.simon.harmonichackernews.platform.LocalCalendarDate,
    ): DataSettingsRuntime = DataSettingsRuntime(scope, dataSettings, today)

    fun createStoryCacheRuntime(scope: CoroutineScope): StoryCacheRuntime {
        val useCase = StoryCacheUseCase(
            hackerNewsRepository = network.hackerNewsRepository,
            algoliaRepository = network.algoliaRepository,
            sink = storyCache,
        )
        return StoryCacheRuntime(scope, useCase::execute)
    }

    val localSummaryCanAttempt: Boolean
        get() = platform.localSummary?.canAttempt() == true

    val localSummaryEngine
        get() = platform.localSummary

    fun createStorySummaryRuntime(scope: CoroutineScope): StorySummaryRuntime {
        val localBackend = platform.localSummary
            ?.let(::PlatformLocalStorySummaryBackend)
            ?: UnavailableStorySummaryBackend("The platform has no local summary engine")
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

    fun createReferenceLinkPreviewRuntime(scope: CoroutineScope): ReferenceLinkPreviewRuntime =
        ReferenceLinkPreviewRuntime(
            scope = scope,
            previews = previewResources,
            summaries = network.linkSummaryRepository,
            connectivity = platform.connectivity,
        )

    fun createLocalSummarySettingsRuntime(scope: CoroutineScope): LocalSummarySettingsRuntime =
        LocalSummarySettingsRuntime(scope, localSummaryEngine, localModels)

    /** Feature-sized platform views derived from the one application-scoped platform graph. */
    fun storiesPlatformDependencies(): StoriesPlatformDependencies = StoriesPlatformDependencies(
        accounts = platform.accounts,
        history = platform.history,
        connectivity = platform.connectivity,
    )

    fun commentsPlatformDependencies(): CommentsPlatformDependencies =
        CommentsPlatformDependencies(
            accounts = platform.accounts,
            externalLinks = externalLinks,
            sharing = platform.sharing,
            clipboard = platform.clipboard,
        )

}
