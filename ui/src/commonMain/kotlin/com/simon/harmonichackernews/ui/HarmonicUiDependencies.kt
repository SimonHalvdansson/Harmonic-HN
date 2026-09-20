package com.simon.harmonichackernews.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.presentation.UserProfileRuntime
import com.simon.harmonichackernews.presentation.UserProfileSession
import com.simon.harmonichackernews.network.ReferenceLinkPreviewRuntime
import com.simon.harmonichackernews.settings.DataSettingsRuntime
import com.simon.harmonichackernews.platform.LocalCalendarDate
import com.simon.harmonichackernews.summary.LocalSummarySettingsRuntime
import com.simon.harmonichackernews.resources.BundledHarmonicResources
import com.simon.harmonichackernews.ui.common.LocalHazeGlassEnabled
import com.simon.harmonichackernews.ui.common.LocalHazePreferences
import com.simon.harmonichackernews.settings.SurfaceEffectMode
import com.simon.harmonichackernews.settings.SurfaceEffectPreferences
import com.simon.harmonichackernews.settings.GlassPreferences
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Explicit application- and scene-scoped dependencies for shared and platform Compose surfaces.
 *
 * UI code reads this environment instead of recovering repositories from an Android Context.
 * Native contexts remain appropriate only for actual platform effects such as intents and images.
 */
class HarmonicUiDependencies(
    private val app: HarmonicAppComposition,
    val scene: HarmonicSceneComposition,
) {
    val network = app.network
    val metadata = app.metadata
    val navigation = scene.navigation
    val launches = scene.launches
    val links = scene.links
    val webContent = app.webContent
    val nowMillis = app.nowMillis
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
    val userMessages = scene.userMessages

    fun createUserProfileRuntime(username: String, monthNames: List<String>): UserProfileRuntime =
        app.createUserProfileRuntime(username, monthNames)

    fun createUserProfileSession(
        scope: CoroutineScope,
        username: String,
        monthNames: List<String>,
    ): UserProfileSession = app.createUserProfileSession(scope, username, monthNames)

    fun createReferenceLinkPreviewRuntime(scope: CoroutineScope): ReferenceLinkPreviewRuntime =
        app.createReferenceLinkPreviewRuntime(scope)

    fun createDataSettingsRuntime(
        scope: CoroutineScope,
        today: () -> LocalCalendarDate,
    ): DataSettingsRuntime = app.createDataSettingsRuntime(scope, today)

    fun createLocalSummarySettingsRuntime(scope: CoroutineScope): LocalSummarySettingsRuntime =
        app.createLocalSummarySettingsRuntime(scope)
}

val LocalHarmonicUiDependencies = staticCompositionLocalOf<HarmonicUiDependencies> {
    error("Harmonic UI dependencies were not provided by the host")
}

@Composable
fun ProvideHarmonicUiDependencies(
    dependencies: HarmonicUiDependencies,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(dependencies.webContent) {
        dependencies.installBundledResources()
    }
    val surfaceEffects by remember(dependencies.settings) {
        dependencies.settings.updates.map {
            SurfaceEffectPreferences(it.appearance.surfaceEffectMode, it.debug.glass)
        }.distinctUntilChanged()
    }.collectAsState(initial = SurfaceEffectPreferences(
        dependencies.userSettings.appearance.surfaceEffectMode,
        dependencies.userSettings.debug.glass,
    ))
    val activeEffects = if (dependencies.metadata.debugSettingsEnabled) surfaceEffects
        else surfaceEffects.copy(glass = GlassPreferences())
    CompositionLocalProvider(
        LocalHarmonicUiDependencies provides dependencies,
        LocalHazeGlassEnabled provides (activeEffects.mode == SurfaceEffectMode.Glass),
        LocalHazePreferences provides activeEffects,
        content = content,
    )
}

/** Installs large shared assets for Compose and native UI hosts that own this environment. */
suspend fun HarmonicUiDependencies.installBundledResources() {
    try {
        withContext(Dispatchers.Default) {
            webContent.adBlocklist.install(BundledHarmonicResources.adBlocklist())
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // The feature remains operational with an empty list if a host mispackages the asset.
    }
}
