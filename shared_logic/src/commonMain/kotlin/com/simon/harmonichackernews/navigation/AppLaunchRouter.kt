package com.simon.harmonichackernews.navigation

import com.simon.harmonichackernews.utils.HackerNewsLinks

/** Normalized app entry supplied after a platform has decoded its native launch object. */
sealed interface AppLaunchRequest {
    data class Settings(val sectionRoute: String? = null) : AppLaunchRequest
    data class Editor(val destination: EditorDestination) : AppLaunchRequest
    data class Submissions(val userName: String?) : AppLaunchRequest
    data object CoulombGas : AppLaunchRequest
    data class Story(val destination: StoryDestination?) : AppLaunchRequest
    data class ViewUrl(val url: String?) : AppLaunchRequest
    data class SharedText(val text: String?) : AppLaunchRequest
    data object Unknown : AppLaunchRequest
}

sealed interface AppLaunchResult {
    data object Routed : AppLaunchResult
    data object Ignored : AppLaunchResult
    data class Invalid(val message: String) : AppLaunchResult
}

/** Shared validation and navigation policy for Android intents, iOS URLs, and desktop launches. */
class AppLaunchRouter(
    private val navigation: MainNavigationStore,
) {
    fun route(request: AppLaunchRequest): AppLaunchResult = when (request) {
        is AppLaunchRequest.Settings -> routed { navigation.openSettings(request.sectionRoute) }
        is AppLaunchRequest.Editor -> if (request.destination.isValid) {
            routed { navigation.openEditor(request.destination) }
        } else {
            AppLaunchResult.Invalid("Invalid comment id")
        }
        is AppLaunchRequest.Submissions -> request.userName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { user -> routed { navigation.openSubmissions(user) } }
            ?: AppLaunchResult.Invalid("Invalid username")
        AppLaunchRequest.CoulombGas -> routed(navigation::openCoulombGas)
        is AppLaunchRequest.Story -> request.destination?.let { destination ->
            routed { navigation.openStory(destination) }
        } ?: AppLaunchResult.Ignored
        is AppLaunchRequest.ViewUrl -> routeItemLink(request.url)
        is AppLaunchRequest.SharedText -> routeItemLink(
            HackerNewsLinks.findItemLink(request.text)?.let { link ->
                "https://news.ycombinator.com/item?id=${link.itemId}" +
                    if (link.scrollToCommentId > 0) "#${link.scrollToCommentId}" else ""
            },
        )
        AppLaunchRequest.Unknown -> AppLaunchResult.Ignored
    }

    private fun routeItemLink(value: String?): AppLaunchResult {
        val link = HackerNewsLinks.parseItemLink(value)
            ?: return AppLaunchResult.Invalid("Unable to parse story")
        return routed {
            navigation.openStory(
                StoryDestination(
                    storyId = link.itemId,
                    scrollToCommentId = link.scrollToCommentId,
                ),
            )
        }
    }

    private inline fun routed(action: () -> Unit): AppLaunchResult {
        action()
        return AppLaunchResult.Routed
    }
}
