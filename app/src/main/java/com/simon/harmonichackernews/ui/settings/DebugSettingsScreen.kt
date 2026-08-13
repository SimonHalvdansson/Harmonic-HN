package com.simon.harmonichackernews.ui.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.navigation.StoryDestination
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.utils.AndroidStoryCache

private const val OpenWithoutCacheStoryId = 49089500

@Composable
fun DebugSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
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
            AndroidStoryCache.remove(context, OpenWithoutCacheStoryId)
            NetworkComponent.removeCachedStoryResponses(context, OpenWithoutCacheStoryId)
            app.navigation.openStory(StoryDestination(OpenWithoutCacheStoryId))
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
