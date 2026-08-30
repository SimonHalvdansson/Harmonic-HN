package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.settings.KeyValueStore

/** Lets shared appearance state update the containing UIKit traits and status-bar foreground. */
interface IosAppearanceController {
    fun setDarkAppearance(dark: Boolean)
}

/**
 * Native services installed by the iOS host.
 *
 * The core services are required because the primary shared screens use them. Reply notifications
 * and local summaries are the only genuinely optional native features.
 */
class IosPlatformBindings(
    val credentials: CredentialStore,
    accountStorage: HackerNewsAccountRepository,
    val externalLinks: ExternalLinkOpener,
    val sharing: ShareService,
    val clipboard: ClipboardService,
    val connectivity: ConnectivityService,
    val timeFormatting: PlatformTimeFormatter,
    val appearance: IosAppearanceController,
    val replyNotifications: com.simon.harmonichackernews.network.ReplyNotificationPlatform? = null,
    localSummary: LocalSummaryEngine? = null,
    nativeLocalSummary: IosNativeSummaryBridge? = null,
) {
    val localSummary: LocalSummaryEngine? =
        localSummary ?: nativeLocalSummary?.let(::IosNativeLocalSummaryEngine)

    /** Observation and mutation serialization around the host's atomic Keychain-backed storage. */
    val accounts: ObservableHackerNewsAccountRepository =
        ObservableAccountRepositoryAdapter(accountStorage)
}

/** Adds the Foundation persistence adapters to the capabilities supplied by the native host. */
fun createIosPlatformDependencies(
    appDataStore: KeyValueStore,
    bindings: IosPlatformBindings,
): AppPlatformDependencies = AppPlatformDependencies(
    credentials = bindings.credentials,
    accounts = bindings.accounts,
    history = IosHistoryStore(appDataStore),
    externalLinks = bindings.externalLinks,
    sharing = bindings.sharing,
    clipboard = bindings.clipboard,
    connectivity = bindings.connectivity,
    battery = IosBatteryStatusService(),
    timeFormatting = bindings.timeFormatting,
    replyNotifications = bindings.replyNotifications,
    localSummary = bindings.localSummary,
)
