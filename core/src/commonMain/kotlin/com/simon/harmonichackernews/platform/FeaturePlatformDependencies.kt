package com.simon.harmonichackernews.platform

import com.simon.harmonichackernews.network.ReplyNotificationPlatform
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Native services required by the shared application graph.
 *
 * Every production host must supply these facilities before it can construct the graph. Features
 * therefore never discover a missing core dependency after a screen has already opened. The two
 * facilities that are genuinely product-optional remain nullable values.
 */
data class AppPlatformDependencies(
    val credentials: CredentialStore,
    val accounts: ObservableHackerNewsAccountRepository,
    val history: ObservableHistoryStore,
    val externalLinks: ExternalLinkOpener,
    val sharing: ShareService,
    val clipboard: ClipboardService,
    val connectivity: ConnectivityService,
    val timeFormatting: PlatformTimeFormatter,
    val credentialDispatcher: CoroutineDispatcher = Dispatchers.Default,
    val replyNotifications: ReplyNotificationPlatform? = null,
    val localSummary: LocalSummaryEngine? = null,
)

/** Platform facilities used by the stories shell. */
data class StoriesPlatformDependencies(
    val accounts: ObservableHackerNewsAccountRepository,
    val history: ObservableHistoryStore,
    val connectivity: ConnectivityService,
)

/** Platform facilities used by the comments shell. */
data class CommentsPlatformDependencies(
    val accounts: ObservableHackerNewsAccountRepository,
    val externalLinks: ExternalLinkOpener,
    val sharing: ShareService,
    val clipboard: ClipboardService,
)
