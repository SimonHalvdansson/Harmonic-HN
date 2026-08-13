package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.platform.ExternalLinkOpener
import com.simon.harmonichackernews.platform.ExternalLinkRequest
import com.simon.harmonichackernews.platform.PlatformCapability
import com.simon.harmonichackernews.utils.HackerNewsLinks

/** A destination resolved without knowing which native navigation framework will execute it. */
sealed interface LinkDestination {
    data class Story(val destination: StoryDestination) : LinkDestination
    data class External(val request: ExternalLinkRequest) : LinkDestination
}

/** Shared internal-HN versus external-link routing policy. */
object LinkNavigationPolicy {
    fun resolve(
        url: String?,
        preferInApp: Boolean = true,
        shareable: Boolean = true,
    ): LinkDestination? {
        val normalized = url?.trim().orEmpty()
        if (normalized.isEmpty()) return null
        val item = HackerNewsLinks.parseItemLink(normalized)
        return if (item != null) {
            LinkDestination.Story(
                StoryDestination(
                    storyId = item.itemId,
                    scrollToCommentId = item.scrollToCommentId,
                ),
            )
        } else {
            LinkDestination.External(
                ExternalLinkRequest(
                    url = normalized,
                    preferInApp = preferInApp,
                    shareable = shareable,
                ),
            )
        }
    }
}

sealed interface LinkNavigationResult {
    data object Opened : LinkNavigationResult
    data object Invalid : LinkNavigationResult
    data class Unavailable(val reason: String) : LinkNavigationResult
}

/**
 * Application-scoped navigator used by Compose, SwiftUI, and desktop hosts. Native code supplies
 * only the external URL implementation; internal Hacker News links always enter the shared graph.
 */
class AppLinkNavigator(
    private val navigation: MainNavigationStore,
    private val externalLinks: PlatformCapability<ExternalLinkOpener>,
) {
    fun open(
        url: String?,
        preferInApp: Boolean = true,
        shareable: Boolean = true,
    ): LinkNavigationResult = when (
        val destination = LinkNavigationPolicy.resolve(url, preferInApp, shareable)
    ) {
        is LinkDestination.Story -> {
            navigation.openStory(destination.destination)
            LinkNavigationResult.Opened
        }
        is LinkDestination.External -> openExternal(destination.request)
        null -> LinkNavigationResult.Invalid
    }

    fun openExternal(request: ExternalLinkRequest): LinkNavigationResult =
        when (val capability = externalLinks) {
            is PlatformCapability.Available -> {
                capability.service.open(request)
                LinkNavigationResult.Opened
            }
            is PlatformCapability.Unavailable -> LinkNavigationResult.Unavailable(capability.reason)
        }
}
