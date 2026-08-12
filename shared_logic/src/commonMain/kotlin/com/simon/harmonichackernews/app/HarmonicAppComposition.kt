package com.simon.harmonichackernews.app

import com.simon.harmonichackernews.data.SavedItemsRepository
import com.simon.harmonichackernews.data.StoryResourceTintRepository
import com.simon.harmonichackernews.network.HackerNewsUserService
import com.simon.harmonichackernews.network.NetworkGraph
import com.simon.harmonichackernews.navigation.MainNavigationStore
import com.simon.harmonichackernews.platform.AppPlatformDependencies
import com.simon.harmonichackernews.platform.CommentsPlatformDependencies
import com.simon.harmonichackernews.platform.PlatformServices
import com.simon.harmonichackernews.platform.StoriesPlatformDependencies
import com.simon.harmonichackernews.platform.SubmissionsPlatformDependencies
import com.simon.harmonichackernews.platform.toAppPlatformDependencies
import com.simon.harmonichackernews.presentation.ScreenSessionRegistry
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.ContentFilterRepository
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.settings.StoredSettingsMutator
import com.simon.harmonichackernews.settings.StoredUserSettings
import com.simon.harmonichackernews.settings.UserSettings
import com.simon.harmonichackernews.settings.UserTagsRepository
import kotlinx.coroutines.flow.Flow

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
    settingsStore: KeyValueStore,
    appDataStore: KeyValueStore,
    settingsChanges: Flow<Unit>,
    currentTheme: () -> String? = { null },
) {
    constructor(
        network: NetworkGraph,
        platformServices: PlatformServices,
        settingsStore: KeyValueStore,
        appDataStore: KeyValueStore,
        settingsChanges: Flow<Unit>,
        currentTheme: () -> String? = { null },
    ) : this(
        network = network,
        platform = platformServices.toAppPlatformDependencies(),
        settingsStore = settingsStore,
        appDataStore = appDataStore,
        settingsChanges = settingsChanges,
        currentTheme = currentTheme,
    )

    val sessions = ScreenSessionRegistry()
    val navigation = MainNavigationStore()
    val userSettings: UserSettings = StoredUserSettings(
        store = settingsStore,
        changes = settingsChanges,
        theme = currentTheme,
    )
    val settings = AppSettingsRepository(userSettings, StoredSettingsMutator(settingsStore))
    val contentFilters = ContentFilterRepository(settingsStore)
    val userTags = UserTagsRepository(settingsStore)
    val savedItems = SavedItemsRepository(appDataStore)
    val storyResourceTints = StoryResourceTintRepository(appDataStore)
    val hackerNewsUser = HackerNewsUserService(
        session = network.hackerNewsSession,
        accounts = platform.accounts,
    )

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
