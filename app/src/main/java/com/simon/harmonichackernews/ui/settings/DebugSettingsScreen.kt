package com.simon.harmonichackernews.ui.settings

import android.os.Build
import androidx.compose.runtime.Composable
import com.simon.harmonichackernews.debug.DebugCachedPostFixture
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

private const val OpenWithoutCacheStoryId = 49089500

@Composable
fun DebugSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val app = LocalHarmonicUiDependencies.current
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
            app.storyCache.remove(OpenWithoutCacheStoryId)
            app.network.removeCachedStoryResponses(OpenWithoutCacheStoryId)
            app.navigation.openStory(StoryDestination(OpenWithoutCacheStoryId))
        },
        onCachePost = {
            if (DebugCachedPostFixture.seed(app.storyCache.repository, app.nowMillis())) {
                app.navigation.openStory(DebugCachedPostFixture.story().toDestination())
            }
        },
        onOpenLink = { app.links.open(it) },
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
