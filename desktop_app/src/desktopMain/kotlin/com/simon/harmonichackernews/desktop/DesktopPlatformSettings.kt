package com.simon.harmonichackernews.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPainter
import com.simon.harmonichackernews.app.HarmonicAppComposition
import com.simon.harmonichackernews.app.HarmonicSceneComposition
import com.simon.harmonichackernews.network.CloudSummaryDefaults
import com.simon.harmonichackernews.presentation.UserMessageDuration
import com.simon.harmonichackernews.resources.Res
import com.simon.harmonichackernews.resources.ic_edit
import com.simon.harmonichackernews.resources.ic_hard_drive
import com.simon.harmonichackernews.settings.AiSummaryTextSetting
import com.simon.harmonichackernews.settings.DataSettingsCounts
import com.simon.harmonichackernews.settings.DataSettingsDialogState
import com.simon.harmonichackernews.settings.DataSettingsRuntimeEffect
import com.simon.harmonichackernews.summary.LocalModelRuntime
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
import com.simon.harmonichackernews.ui.settings.LocalModelsRoute
import com.simon.harmonichackernews.ui.settings.MessageActionDialog
import com.simon.harmonichackernews.ui.settings.PortableSettingsDetail
import com.simon.harmonichackernews.ui.settings.SettingRow
import com.simon.harmonichackernews.ui.settings.SettingsDivider
import com.simon.harmonichackernews.ui.settings.SettingsSection
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberDesktopAppIconPainter(): Painter = remember {
    checkNotNull(desktopAppIconImage) { "The bundled Harmonic desktop app icon is missing" }
        .toPainter()
}

@Composable
internal fun DesktopSettingsDetail(
    section: SettingsSection,
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    singlePane: Boolean,
    onBack: () -> Unit,
    onNavigate: (SettingsSection, Boolean) -> Unit,
) {
    val appIcon = rememberDesktopAppIconPainter()
    PortableSettingsDetail(
        section = section,
        app = app,
        scene = scene,
        singlePane = singlePane,
        onBack = onBack,
        onNavigate = onNavigate,
        appIcon = appIcon,
        aboutBody = "Harmonic is an open-source Hacker News client. " +
            "This desktop host uses the same Kotlin Multiplatform application logic and " +
            "Compose screens as the Android app, with desktop-native storage, links, and " +
            "keyboard/window behavior.",
        debugPlatformVersion =
            "${System.getProperty("os.name")} ${System.getProperty("os.version")}",
        debugNotificationsMessage =
            "Reply notifications and Android notification fixtures do not apply to desktop.",
        aiSettings = { DesktopAiSettings(app, scene, singlePane, onBack) },
        dataSettings = { DesktopDataSettings(app, scene, singlePane, onBack) },
    )
}

@Composable
private fun DesktopAiSettings(
    app: HarmonicAppComposition,
    scene: HarmonicSceneComposition,
    showNavigation: Boolean,
    onBack: () -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    val localModels = checkNotNull(app.localModels) {
        "Desktop local-model service was not installed"
    }
    val localModelState by localModels.state.collectAsState()
    val scope = rememberCoroutineScope()
    val settingsRuntime = remember(app, scope) {
        app.createLocalSummarySettingsRuntime(scope)
    }
    val availabilityState by settingsRuntime.state.collectAsState()

    LaunchedEffect(settingsRuntime, localModelState, refresh) {
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
        selectedLocalModelId = localModelState.selectedModelId,
        showNavigation = showNavigation,
        contentVersion = refresh + localModelState.hashCode() + availabilityState.revision,
        onBack = onBack,
        onLocalModeUnavailable = {
            scene.userMessages.show(
                availabilityState.failure
                    ?: "Local summarization is unavailable in this desktop build",
                UserMessageDuration.LONG,
            )
        },
        localModelsContent = {
            val showMessage: (String) -> Unit = { message ->
                scene.userMessages.show(message, UserMessageDuration.LONG)
            }
            Column {
                LocalModelsRoute(
                    localModels = localModels,
                    managerState = localModelState,
                    nanoAvailabilityResolved = true,
                    nanoAvailable = false,
                    nanoBaseModelName = null,
                    models = localModels.catalog.filter {
                        it.runtime == LocalModelRuntime.LLAMA_CPP
                    },
                    onChanged = { refresh++ },
                    onMessage = showMessage,
                )
                DesktopModelStorageRows(
                    directoryPath = checkNotNull(localModels.storageDirectoryPath),
                    onOpen = { path -> openModelFolder(path)?.let(showMessage) },
                    onChoose = { currentPath ->
                        chooseModelFolder(currentPath)?.let { selected ->
                            val previousPath = localModels.storageDirectoryPath
                            localModels.changeStorageDirectory(selected.absolutePath)
                                ?.let(showMessage)
                                ?: run {
                                    refresh++
                                    if (previousPath != selected.absolutePath) {
                                        showMessage(
                                            "Model folder changed. Existing model files were not moved.",
                                        )
                                    }
                                }
                        }
                    },
                )
            }
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
private fun DesktopModelStorageRows(
    directoryPath: String,
    onOpen: (String) -> Unit,
    onChoose: (String) -> Unit,
) {
    SettingsDivider()
    SettingRow(
        title = "Open model folder",
        summary = directoryPath,
        summaryFontSizeSp = 12f,
        summaryLineHeightSp = 16f,
        summaryMaxLines = 2,
        icon = Res.drawable.ic_hard_drive,
        onClick = { onOpen(directoryPath) },
    )
    SettingsDivider()
    SettingRow(
        title = "Change model folder",
        summary = "Choose where models are stored. Existing files are not moved.",
        icon = Res.drawable.ic_edit,
        onClick = { onChoose(directoryPath) },
    )
}

private fun chooseModelFolder(currentPath: String): File? {
    val chooser = JFileChooser(File(currentPath)).apply {
        dialogTitle = "Choose Harmonic model folder"
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        selectedFile = File(currentPath)
    }
    val result = chooser.showOpenDialog(null)
    return chooser.selectedFile.takeIf { result == JFileChooser.APPROVE_OPTION }
}

private fun openModelFolder(path: String): String? = runCatching {
    val directory = File(path).absoluteFile
    check(directory.isDirectory || directory.mkdirs()) { "Could not create the model folder." }
    check(Desktop.isDesktopSupported()) { "Opening folders is not supported on this desktop." }
    val desktop = Desktop.getDesktop()
    check(desktop.isSupported(Desktop.Action.OPEN)) {
        "Opening folders is not supported on this desktop."
    }
    desktop.open(directory)
}.exceptionOrNull()?.let { error ->
    error.message?.takeIf(String::isNotBlank) ?: "Could not open the model folder."
}

@Composable
private fun DesktopDataSettings(
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
                    val file = chooseTextFile(save = true, suggestedName = effect.filename)
                    if (file == null) return@collect
                    runCatching { withContext(Dispatchers.IO) { file.writeText(effect.content) } }
                        .onSuccess { scene.userMessages.show("Bookmarks exported") }
                        .onFailure { scene.userMessages.show("Write error") }
                }
                DataSettingsRuntimeEffect.OpenImportDocument -> {
                    val file = chooseTextFile(save = false)
                    if (file == null) return@collect
                    runCatching { withContext(Dispatchers.IO) { file.readText() } }
                        .onSuccess(runtime::importBookmarks)
                        .onFailure { scene.userMessages.show("Read error") }
                }
                DataSettingsRuntimeEffect.OpenAppLinkSettings ->
                    scene.userMessages.show(
                        "Desktop link handling is controlled by your browser and OS",
                    )
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
            message = "Desktop link handling is configured in your operating system and browser.",
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

private fun chooseTextFile(save: Boolean, suggestedName: String? = null): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = if (save) "Export Harmonic bookmarks" else "Import Harmonic bookmarks"
        fileFilter = FileNameExtensionFilter("Text files", "txt")
        suggestedName?.let { selectedFile = File(it) }
    }
    val result = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
    return chooser.selectedFile.takeIf { result == JFileChooser.APPROVE_OPTION }
}
