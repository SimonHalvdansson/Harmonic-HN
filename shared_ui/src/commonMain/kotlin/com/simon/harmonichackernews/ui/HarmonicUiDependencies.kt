package com.simon.harmonichackernews.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.simon.harmonichackernews.app.HarmonicAppComposition

/**
 * Explicit application-scoped dependencies for shared and platform Compose surfaces.
 *
 * UI code reads this environment instead of recovering repositories from an Android Context.
 * Native contexts remain appropriate only for actual platform effects such as intents and images.
 */
class HarmonicUiDependencies(app: HarmonicAppComposition) {
    val network = app.network
    val metadata = app.metadata
    val navigation = app.navigation
    val links = app.links
    val platform = app.platform
    val userSettings = app.userSettings
    val settings = app.settings
    val contentFilters = app.contentFilters
    val userTags = app.userTags
    val savedItems = app.savedItems
    val storyResourceTints = app.storyResourceTints
    val previewResources = app.previewResources
    val aiSummarySettings = app.aiSummarySettings
    val aiModelDefaults = app.aiModelDefaults
    val hackerNewsUser = app.hackerNewsUser
    val login = app.login
    val settingsReset = app.settingsReset
    val launchState = app.launchState
    val userMessages = app.userMessages
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
