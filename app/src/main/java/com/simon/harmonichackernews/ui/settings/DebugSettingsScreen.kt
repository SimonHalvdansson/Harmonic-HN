package com.simon.harmonichackernews.ui.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.simon.harmonichackernews.debug.DebugCachedPostFixture
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.network.LinkPreviewUseCase
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import kotlinx.coroutines.launch

private const val OpenWithoutCacheStoryId = 49089500

@Composable
fun DebugSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onOpenLinkPreviews: () -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val scope = rememberCoroutineScope()
    SharedDebugSettingsRoute(
        repository = app.settings,
        environment = DebugEnvironmentUiState(
            appVersion = app.metadata.versionName,
            appBuild = app.metadata.buildNumber,
            buildVersion = app.metadata.buildType,
            platformVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        ),
        showNavigation = showNavigation,
        onBack = onBack,
        onOpenHnId = { app.navigation.openStory(StoryDestination(it)) },
        onOpenWithoutCache = {
            scope.launch {
                app.storyCache.remove(OpenWithoutCacheStoryId)
                app.network.removeCachedStoryResponses(OpenWithoutCacheStoryId)
                app.navigation.openStory(StoryDestination(OpenWithoutCacheStoryId))
            }
        },
        onCachePost = {
            scope.launch {
                if (DebugCachedPostFixture.seed(app.storyCache::storeStory)) {
                    app.navigation.openStory(DebugCachedPostFixture.story().toDestination())
                }
            }
        },
        onOpenLink = { app.links.open(it) },
        onOpenLinkPreviews = onOpenLinkPreviews,
        onEasterEggRequested = app.navigation::openCoulombGas,
        dialogContent = { dialog, dismiss ->
            when (dialog) {
                DebugSettingsDialog.CHANGELOG -> SettingsChangelogDialog(
                    onDismiss = dismiss,
                    onOpenGithub = {
                        app.links.open(app.metadata.projectUrl)
                        dismiss()
                    },
                )
                DebugSettingsDialog.WELCOME -> WelcomeSettingsDialog(
                    styleChooser = false,
                    onDismiss = dismiss,
                )
                DebugSettingsDialog.NOTIFICATIONS -> DebugNotificationsDialog(
                    onDismiss = dismiss,
                )
            }
        },
    )
}

@Composable
fun LinkPreviewsDebugScreen(onBack: () -> Unit) {
    val app = LocalHarmonicUiDependencies.current
    val useCase = remember(app.network.linkPreviewRepository) {
        LinkPreviewUseCase(app.network.linkPreviewRepository)
    }
    SharedLinkPreviewsDebugScreen(
        comments = app.userSettings.comments,
        loadPreview = useCase::load,
        onOpenLink = app.links::open,
        onBack = onBack,
    )
}
