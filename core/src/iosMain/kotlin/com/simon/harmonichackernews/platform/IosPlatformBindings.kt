package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.network.ReplyNotificationPlatform
import com.simon.harmonichackernews.settings.KeyValueStore
import com.simon.harmonichackernews.summary.IosLiteRtSummaryBridge

/** Keeps UIKit traits, status-bar foreground, and backing views aligned with the shared theme. */
interface IosAppearanceController {
    fun setAppearance(dark: Boolean, backgroundArgb: Int)
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
    val textDocuments: TextDocumentService,
    val replyNotifications: ReplyNotificationPlatform? = null,
    localSummary: LocalSummaryEngine? = null,
    nativeLocalSummary: IosNativeSummaryBridge? = null,
    val nativeLiteRt: IosLiteRtSummaryBridge? = null,
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
    localSummary: LocalSummaryEngine? = bindings.localSummary,
): AppPlatformDependencies = AppPlatformDependencies(
    credentials = bindings.credentials,
    accounts = bindings.accounts,
    history = IosHistoryStore(appDataStore),
    externalLinks = bindings.externalLinks,
    sharing = bindings.sharing,
    textDocuments = bindings.textDocuments,
    clipboard = bindings.clipboard,
    connectivity = bindings.connectivity,
    battery = IosBatteryStatusService(),
    timeFormatting = bindings.timeFormatting,
    replyNotifications = bindings.replyNotifications,
    localSummary = localSummary,
)
