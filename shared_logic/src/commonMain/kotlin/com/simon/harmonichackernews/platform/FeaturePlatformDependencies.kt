package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.network.ReplyNotificationPlatform

/** Explicit availability state for an optional platform facility. */
sealed interface PlatformCapability<out T> {
    data class Available<T>(val service: T) : PlatformCapability<T>
    data class Unavailable(val name: String, val reason: String) : PlatformCapability<Nothing>

    fun getOrNull(): T? = when (this) {
        is Available -> service
        is Unavailable -> null
    }

    fun requireService(): T = when (this) {
        is Available -> service
        is Unavailable -> throw PlatformCapabilityUnavailableException(name, reason)
    }
}

/**
 * Capabilities used by independent app features. A platform only supplies facilities it supports;
 * unsupported facilities remain explicit values rather than fake no-op implementations.
 */
data class OptionalPlatformCapabilities(
    val bookmarks: PlatformCapability<ObservableBookmarkStore> = unavailable("Bookmarks"),
    val history: PlatformCapability<ObservableHistoryStore> = unavailable("History"),
    val cache: PlatformCapability<CacheStore> = unavailable("Cache"),
    val files: PlatformCapability<FileStore> = unavailable("Files"),
    val externalLinks: PlatformCapability<ExternalLinkOpener> = unavailable("External links"),
    val sharing: PlatformCapability<ShareService> = unavailable("Sharing"),
    val clipboard: PlatformCapability<ClipboardService> = unavailable("Clipboard"),
    val connectivity: PlatformCapability<ConnectivityService> = unavailable("Connectivity"),
    val notifications: PlatformCapability<NotificationScheduler> = unavailable("Notifications"),
    val replyNotifications: PlatformCapability<ReplyNotificationPlatform> =
        unavailable("Reply notifications"),
    val articles: PlatformCapability<ArticleViewer> = unavailable("Article viewer"),
    val localSummary: PlatformCapability<LocalSummaryEngine> = unavailable("Local summaries"),
) {
    companion object {
        private fun unavailable(name: String): PlatformCapability.Unavailable =
            PlatformCapability.Unavailable(name, "The platform has not installed this capability")
    }
}

/** Minimum dependencies for the long-lived shared application graph. */
data class AppPlatformDependencies(
    val credentials: CredentialStore,
    val accounts: ObservableHackerNewsAccountRepository,
    val capabilities: OptionalPlatformCapabilities = OptionalPlatformCapabilities(),
)

/** Platform facilities used by the stories shell. */
data class StoriesPlatformDependencies(
    val accounts: ObservableHackerNewsAccountRepository,
    val history: ObservableHistoryStore,
    val connectivity: ConnectivityService,
    val externalLinks: ExternalLinkOpener,
)

/** Platform facilities used by the comments shell. */
data class CommentsPlatformDependencies(
    val accounts: ObservableHackerNewsAccountRepository,
    val externalLinks: ExternalLinkOpener,
    val sharing: ShareService,
    val clipboard: ClipboardService,
)

/** Platform facilities used by the submissions shell. */
data class SubmissionsPlatformDependencies(
    val externalLinks: ExternalLinkOpener,
)
