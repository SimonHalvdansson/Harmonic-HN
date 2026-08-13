package com.simon.harmonichackernews.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.presentation.UserProfileRuntime

/**
 * Explicit application-scoped dependencies for shared and platform Compose surfaces.
 *
 * UI code reads this environment instead of recovering repositories from an Android Context.
 * Native contexts remain appropriate only for actual platform effects such as intents and images.
 */
class HarmonicUiDependencies(private val app: HarmonicAppComposition) {
    val network = app.network
    val metadata = app.metadata
    val navigation = app.navigation
    val launches = app.launches
    val links = app.links
    val webContent = app.webContent
    val pdfDownloads = app.pdfDownloads
    val widgets = app.widgets
    val platform = app.platform
    val userSettings = app.userSettings
    val settings = app.settings
    val contentFilters = app.contentFilters
    val userTags = app.userTags
    val savedItems = app.savedItems
    val storyResourceTints = app.storyResourceTints
    val previewResources = app.previewResources
    val storyCache = app.storyCache
    val replyNotifications = app.replyNotifications
    val aiSummarySettings = app.aiSummarySettings
    val aiModelDefaults = app.aiModelDefaults
    val localModels = app.localModels
    val localSummaryEngine = app.localSummaryEngine
    val hackerNewsUser = app.hackerNewsUser
    val login = app.login
    val settingsReset = app.settingsReset
    val dataSettings = app.dataSettings
    val launchState = app.launchState
    val appearance = app.appearance
    val userMessages = app.userMessages

    fun createUserProfileRuntime(username: String, monthNames: List<String>): UserProfileRuntime =
        app.createUserProfileRuntime(username, monthNames)
}

val LocalHarmonicUiDependencies = staticCompositionLocalOf<HarmonicUiDependencies> {
    error("Harmonic UI dependencies were not provided by the host")
}

@Composable
fun ProvideHarmonicUiDependencies(
    dependencies: HarmonicUiDependencies,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies, content = content)
}
