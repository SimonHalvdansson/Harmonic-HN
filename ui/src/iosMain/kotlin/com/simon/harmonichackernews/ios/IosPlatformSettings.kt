package com.simon.harmonichackernews.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.network.CloudSummaryDefaults
import com.simon.harmonichackernews.presentation.UserMessageDuration
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.quanta
import com.simon.harmonichackernews.settings.AiSummaryTextSetting
import com.simon.harmonichackernews.settings.DataSettingsCounts
import com.simon.harmonichackernews.settings.DataSettingsDialogState
import com.simon.harmonichackernews.settings.DataSettingsRuntimeEffect
import com.simon.harmonichackernews.ui.settings.AddBookmarksToFavoritesDialog
import com.simon.harmonichackernews.ui.settings.AiModelSelectorRoute
import com.simon.harmonichackernews.ui.settings.AiSummaryBaseUrlDialog
import com.simon.harmonichackernews.ui.settings.AiSummarySettingsDialog
import com.simon.harmonichackernews.ui.settings.AiSummarySettingsRoute
import com.simon.harmonichackernews.ui.settings.AiSummaryTextDialog
import com.simon.harmonichackernews.ui.settings.ClearAiModelsConfirmationDialog
import com.simon.harmonichackernews.ui.settings.DataSettingsAction
import com.simon.harmonichackernews.ui.settings.DataSettingsRoute
import com.simon.harmonichackernews.ui.settings.ImportBookmarksDialog
import com.simon.harmonichackernews.ui.settings.ManagedLocalModelPanel
import com.simon.harmonichackernews.ui.settings.MessageActionDialog
import com.simon.harmonichackernews.ui.settings.PortableSettingsDetail
import com.simon.harmonichackernews.ui.settings.SettingsSection
import org.jetbrains.compose.resources.painterResource
import platform.UIKit.UIDevice

@Composable
internal fun IosSettingsDetail(
    section: SettingsSection,
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    singlePane: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection, Boolean) -> Unit,
) {
    val appIcon = painterResource(Res.drawable.quanta)
    PortableSettingsDetail(
        section = section,
        app = app,
        scene = scene,
        singlePane = singlePane,
        onBack = onBack,
        onNavigate = onNavigate,
        appIcon = appIcon,
        aboutBody = "Harmonic is an open-source Hacker News client. " +
            "This iOS host uses the same Kotlin Multiplatform application logic and " +
            "Compose screens as the Android app, with iOS-native storage, links, and " +
            "keyboard/window behavior.",
        debugPlatformVersion = UIDevice.currentDevice.systemName + " " +
            UIDevice.currentDevice.systemVersion,
        debugNotificationsMessage =
            "Reply notifications and Android notification fixtures do not apply to iOS.",
        aiSettings = { IosAiSettings(app, scene, singlePane, onBack) },
        dataSettings = { IosDataSettings(app, scene, singlePane, onBack) },
    )
}

@Composable
private fun IosAiSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val settingsRuntime = remember(app, scope) {
        app.createLocalSummarySettingsRuntime(scope)
    }
    val availabilityState by settingsRuntime.state.collectAsState()

    LaunchedEffect(settingsRuntime, refresh) {
        settingsRuntime.resolve()
    }
    DisposableEffect(settingsRuntime) {
        onDispose(settingsRuntime::dispose)
    }

    AiSummarySettingsRoute(
        repository = app.aiSummarySettings,
        modelDefaults = app.aiModelDefaults,
        localSummarizationSupported = availabilityState.supported,
        localAvailabilityResolved = availabilityState.availabilityResolved,
        localConfigurationReady = availabilityState.configurationReady,
        localModeAvailable = availabilityState.available,
        showNavigation = showNavigation,
        contentVersion = refresh,
        onBack = onBack,
        onLocalModeUnavailable = {
            scene.userMessages.show(
                availabilityState.failure
                    ?: "Apple Intelligence summarization is unavailable on this device",
                UserMessageDuration.LONG,
            )
        },
        localModelsContent = {
            ManagedLocalModelPanel(
                title = "Apple Intelligence",
                status = when {
                    !availabilityState.availabilityResolved -> "Checking availability…"
                    availabilityState.available -> "Available · system managed"
                    else -> availabilityState.failure ?: "Not available"
                },
                available = availabilityState.available,
            )
        },
        dialogContent = { dialog, dismiss ->
            when (dialog) {
                AiSummarySettingsDialog.BaseUrl -> AiSummaryBaseUrlDialog(dismiss)
                AiSummarySettingsDialog.ApiKey -> AiSummaryTextDialog(
                    setting = AiSummaryTextSetting.API_KEY,
                    title = "API Key",
                    hint = "API Key",
                    defaultValue = "",
                    minLines = 1,
                    maxLines = 1,
                    textSizeSp = 16,
                    trimValue = true,
                    allowEmpty = true,
                    showReset = false,
                    onDismiss = dismiss,
                    onSaved = { refresh++ },
                )
                AiSummarySettingsDialog.Model -> AiModelSelectorRoute(onDismiss = dismiss)
                AiSummarySettingsDialog.SystemPrompt -> AiSummaryTextDialog(
                    setting = AiSummaryTextSetting.SYSTEM_PROMPT,
                    title = "System prompt",
                    hint = "System prompt",
                    defaultValue = CloudSummaryDefaults.SYSTEM_PROMPT,
                    minLines = 5,
                    maxLines = 10,
                    textSizeSp = 15,
                    trimValue = false,
                    allowEmpty = true,
                    showReset = true,
                    onDismiss = dismiss,
                    onSaved = { refresh++ },
                )
            }
        },
    )
}

@Composable
private fun IosDataSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val runtime = remember(app, scope) {
        app.createDataSettingsRuntime(scope) {
            app.platform.timeFormatting.localDate(app.nowMillis())
        }
    }
    val state by runtime.state.collectAsState()

    LaunchedEffect(runtime) {
        runtime.effects.collect { effect ->
            when (effect) {
                is DataSettingsRuntimeEffect.CreateExportDocument -> {
                    app.platform.sharing.share(effect.content, effect.filename)
                    scene.userMessages.show("Choose Save to Files to export bookmarks")
                }
                DataSettingsRuntimeEffect.OpenImportDocument -> {
                    scene.userMessages.show(
                        "Bookmark import needs a Files picker and is not available in this build",
                        UserMessageDuration.LONG,
                    )
                }
                DataSettingsRuntimeEffect.OpenAppLinkSettings ->
                    scene.userMessages.show("iOS link handling is controlled by your browser and OS")
                DataSettingsRuntimeEffect.SettingsReset -> app.appearance.refreshSelection()
                is DataSettingsRuntimeEffect.Message -> scene.userMessages.show(effect.text)
            }
        }
    }

    DataSettingsRoute(
        repository = app.settings,
        counts = DataSettingsCounts(
            state.snapshot.bookmarkCount,
            state.snapshot.historyCount,
            state.snapshot.postCacheCount,
            state.snapshot.tintCacheCount,
            state.snapshot.aiModelBytes,
        ),
        loggedIn = state.snapshot.loggedIn,
        showNavigation = showNavigation,
        showAppLinkSettings = false,
        onBack = onBack,
        contentVersion = state.revision,
        onAction = { action ->
            when (action) {
                DataSettingsAction.AddBookmarksToFavorites -> runtime.addBookmarksToFavorites()
                DataSettingsAction.ExportBookmarks -> runtime.exportBookmarks()
                DataSettingsAction.ImportBookmarks ->
                    runtime.showDialog(DataSettingsDialogState.IMPORT)
                DataSettingsAction.ClearHistory -> runtime.clearHistory()
                DataSettingsAction.ClearPostCache -> runtime.clearPostCache()
                DataSettingsAction.ClearTintCache -> runtime.clearTintCache()
                DataSettingsAction.ClearAiModels ->
                    runtime.showDialog(DataSettingsDialogState.AI_MODELS)
                DataSettingsAction.OpenLinksSettings ->
                    runtime.showDialog(DataSettingsDialogState.LINKS)
                DataSettingsAction.ResetSettings ->
                    runtime.showDialog(DataSettingsDialogState.RESET)
            }
        },
    )

    when (state.dialog) {
        DataSettingsDialogState.IMPORT -> ImportBookmarksDialog(
            onDismiss = { runtime.showDialog(null) },
            onImport = runtime::requestImport,
        )
        DataSettingsDialogState.RESET -> MessageActionDialog(
            title = "Reset all settings?",
            message = "Bookmarks, history, account details, user tags and cached posts are preserved.",
            positiveLabel = "Reset",
            negativeLabel = "Cancel",
            onPositive = runtime::resetSettings,
            onNegative = { runtime.showDialog(null) },
            onDismiss = { runtime.showDialog(null) },
        )
        DataSettingsDialogState.LINKS -> MessageActionDialog(
            message = "iOS link handling is configured in your operating system and browser.",
            onDismiss = { runtime.showDialog(null) },
        )
        DataSettingsDialogState.AI_MODELS -> ClearAiModelsConfirmationDialog(
            modelNames = state.snapshot.aiModelNames,
            onClear = runtime::clearAiModels,
            onDismiss = { runtime.showDialog(null) },
        )
        null -> Unit
    }
    state.favoriteIds?.let { ids ->
        AddBookmarksToFavoritesDialog(ids.toIntArray(), runtime::dismissFavorites)
    }
}
