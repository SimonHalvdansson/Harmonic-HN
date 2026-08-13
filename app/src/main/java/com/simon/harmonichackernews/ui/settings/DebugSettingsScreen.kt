package com.simon.harmonichackernews.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.settings.DebugBooleanPreference
import com.simon.harmonichackernews.ui.debug.CoulombGasContract
import com.simon.harmonichackernews.utils.AndroidLinkNavigation
import com.simon.harmonichackernews.utils.AndroidStoryCache

private const val OpenWithoutCacheStoryId = 49089500

@Composable
fun DebugSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = LocalHarmonicUiDependencies.current.settings
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    var dialog by rememberSaveable { mutableStateOf<DebugSettingsDialog?>(null) }

    SharedDebugSettingsScreen(
        showNavigation = showNavigation,
        contentVersion = settings.hashCode(),
        alwaysShowTapToRefresh = settings.debug.alwaysShowTapToRefresh,
        showAiSummaryDebugInfo = settings.debug.showAiSummaryDebugInfo,
        environment = DebugEnvironmentUiState(
            appVersion = BuildConfig.VERSION_NAME,
            appBuild = BuildConfig.VERSION_CODE.toString(),
            buildVersion = BuildConfig.BUILD_TYPE,
            platformVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        ),
        onBack = onBack,
        onAlwaysShowTapToRefreshChanged = {
            repository.setDebugBoolean(DebugBooleanPreference.ALWAYS_SHOW_TAP_TO_REFRESH, it)
        },
        onShowAiSummaryDebugInfoChanged = {
            repository.setDebugBoolean(DebugBooleanPreference.SHOW_AI_SUMMARY_INFO, it)
        },
        onOpenHnId = { AndroidLinkNavigation.openStory(it, -1, context) },
        onOpenWithoutCache = {
            AndroidStoryCache.remove(context, OpenWithoutCacheStoryId)
            NetworkComponent.removeCachedStoryResponses(context, OpenWithoutCacheStoryId)
            AndroidLinkNavigation.openStory(OpenWithoutCacheStoryId, -1, context)
        },
        onOpenLink = { AndroidLinkNavigation.openMaybeHackerNews(context, it) },
        onDialogRequested = { dialog = it },
        onEasterEggRequested = {
            context.startActivity(CoulombGasContract.createIntent(context))
        },
    )

    when (dialog) {
        DebugSettingsDialog.CHANGELOG -> ChangelogDialog(onDismiss = { dialog = null })
        DebugSettingsDialog.WELCOME -> WelcomeSettingsDialog(
            styleChooser = false,
            onDismiss = { dialog = null },
        )
        DebugSettingsDialog.NOTIFICATIONS -> DebugNotificationsDialog(
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun ChangelogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    SettingsChangelogDialog(
        onDismiss = onDismiss,
        onOpenGithub = {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/SimonHalvdansson/Harmonic-HN"),
                ),
            )
            onDismiss()
        },
    )
}
