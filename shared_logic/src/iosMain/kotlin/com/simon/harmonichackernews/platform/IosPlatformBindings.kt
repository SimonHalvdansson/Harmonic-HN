package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.settings.KeyValueStore

/**
 * Native services installed by the iOS host.
 *
 * An account vault is the only capability required to start the shared application graph. Every
 * other native facility can be brought up incrementally and remains an explicit unavailable value
 * until the Swift/UIKit host installs it. This lets an iOS shell render settings or signed-out
 * content without first implementing unrelated facilities such as notifications or local AI.
 */
class IosPlatformBindings(
    val credentials: CredentialStore,
    val accounts: ObservableHackerNewsAccountRepository =
        CredentialBackedHackerNewsAccountRepository(credentials),
    val capabilities: OptionalPlatformCapabilities = OptionalPlatformCapabilities(),
) {
    /** Compatibility constructor for hosts that already provide the complete legacy service set. */
    constructor(
        credentials: CredentialStore,
        cache: CacheStore,
        files: FileStore,
        externalLinks: ExternalLinkOpener,
        sharing: ShareService,
        clipboard: ClipboardService,
        connectivity: ConnectivityService,
        notifications: NotificationScheduler,
        articles: ArticleViewer,
        localSummary: LocalSummaryEngine,
    ) : this(
        credentials = credentials,
        capabilities = OptionalPlatformCapabilities(
            cache = PlatformCapability.Available(cache),
            files = PlatformCapability.Available(files),
            externalLinks = PlatformCapability.Available(externalLinks),
            sharing = PlatformCapability.Available(sharing),
            clipboard = PlatformCapability.Available(clipboard),
            connectivity = PlatformCapability.Available(connectivity),
            notifications = PlatformCapability.Available(notifications),
            articles = PlatformCapability.Available(articles),
            localSummary = PlatformCapability.Available(localSummary),
        ),
    )

    constructor(
        credentials: CredentialStore,
        cache: CacheStore,
        files: FileStore,
        externalLinks: ExternalLinkOpener,
        sharing: ShareService,
        clipboard: ClipboardService,
        connectivity: ConnectivityService,
        notifications: NotificationScheduler,
        articles: ArticleViewer,
    ) : this(
        credentials = credentials,
        cache = cache,
        files = files,
        externalLinks = externalLinks,
        sharing = sharing,
        clipboard = clipboard,
        connectivity = connectivity,
        notifications = notifications,
        articles = articles,
        localSummary = UnavailableLocalSummaryEngine(
            "The iOS host has not installed a local inference engine",
        ),
    )
}

/** Adds the Foundation persistence adapters to the capabilities supplied by the native host. */
fun createIosPlatformDependencies(
    appDataStore: KeyValueStore,
    bindings: IosPlatformBindings,
): AppPlatformDependencies = AppPlatformDependencies(
    credentials = bindings.credentials,
    accounts = bindings.accounts,
    capabilities = bindings.capabilities.copy(
        bookmarks = PlatformCapability.Available(
            LegacyObservableBookmarkStoreAdapter(IosBookmarkStore(appDataStore)),
        ),
        history = PlatformCapability.Available(
            LegacyObservableHistoryStoreAdapter(IosHistoryStore(appDataStore)),
        ),
    ),
)
