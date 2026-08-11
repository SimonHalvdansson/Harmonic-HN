package com.simon.harmonichackernews.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.preference.PreferenceManager
import com.simon.harmonichackernews.BuildConfig
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.ui.debug.CoulombGasContract
import com.simon.harmonichackernews.utils.Utils

private const val OpenWithoutCacheStoryId = 49089500

@Composable
fun DebugSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val refresh = rememberPreferenceRefresh()
    var dialog by rememberSaveable { mutableStateOf<DebugSettingsDialog?>(null) }

    SharedDebugSettingsScreen(
        showNavigation = showNavigation,
        contentVersion = refresh,
        alwaysShowTapToRefresh = prefs.getBoolean(
            "pref_always_show_tap_to_refresh",
            false,
        ),
        showAiSummaryDebugInfo = prefs.getBoolean(
            "pref_debug_show_llm_summary_info",
            false,
        ),
        environment = DebugEnvironmentUiState(
            appVersion = BuildConfig.VERSION_NAME,
            appBuild = BuildConfig.VERSION_CODE.toString(),
            buildVersion = BuildConfig.BUILD_TYPE,
            platformVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        ),
        onBack = onBack,
        onAlwaysShowTapToRefreshChanged = {
            prefs.edit().putBoolean("pref_always_show_tap_to_refresh", it).apply()
        },
        onShowAiSummaryDebugInfoChanged = {
            prefs.edit().putBoolean("pref_debug_show_llm_summary_info", it).apply()
        },
        onOpenHnId = { Utils.openCommentsActivity(it, -1, context) },
        onOpenWithoutCache = {
            Utils.removeStoryFromCaches(context, OpenWithoutCacheStoryId)
            NetworkComponent.removeCachedStoryResponses(context, OpenWithoutCacheStoryId)
            Utils.openCommentsActivity(OpenWithoutCacheStoryId, -1, context)
        },
        onOpenLink = { Utils.openLinkMaybeHN(context, it) },
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
