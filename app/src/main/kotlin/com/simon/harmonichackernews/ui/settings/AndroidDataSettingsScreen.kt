package com.simon.harmonichackernews.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.simon.harmonichackernews.settings.SettingsTransfer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.platform.AndroidTextDocuments
import com.simon.harmonichackernews.settings.DataSettingsRuntimeEffect
import com.simon.harmonichackernews.platform.PresentationCopy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.IOException

private const val FILE_PICKER_UNAVAILABLE = "No system file picker is available."

internal class PendingTextExport {
    private var content: String? = null

    fun replace(value: String) {
        content = value
    }

    fun take(): String? = content.also { content = null }

    fun clear() {
        content = null
    }
}

@Composable
fun AndroidDataSettingsScreen(
    showNavigation: Boolean,
    onBack: () -> Unit,
    onSettingsReset: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appComposition = LocalHarmonicUiDependencies.current
    val runtime = remember(appComposition, scope) {
        appComposition.createDataSettingsRuntime(scope) {
            appComposition.platform.timeFormatting
                .localDate(kotlin.time.Clock.System.now().toEpochMilliseconds())
        }
    }
    val runtimeState by runtime.state.collectAsStateWithLifecycle()
    val pendingExport = remember { PendingTextExport() }
    DisposableEffect(pendingExport) {
        onDispose(pendingExport::clear)
    }

    val writeExport: (Uri?) -> Unit = { uri ->
        val content = pendingExport.take()
        if (uri != null && content != null) {
            scope.launch {
                try {
                    AndroidTextDocuments.write(context, uri, content)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    appComposition.userMessages.show(PresentationCopy.WRITE_ERROR)
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"), writeExport,
    )
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    runtime.importBookmarks(AndroidTextDocuments.read(context, uri))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    appComposition.userMessages.show(PresentationCopy.READ_ERROR)
                }
            }
        }
    }

    var pendingSettingsExport by rememberSaveable { mutableStateOf<String?>(null) }
    val settingsExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = pendingSettingsExport
        pendingSettingsExport = null
        if (uri != null && content != null) scope.launch {
            try {
                AndroidTextDocuments.write(context, uri, content)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                appComposition.userMessages.show(PresentationCopy.WRITE_ERROR)
            }
        }
    }
    val settingsImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            try {
                runtime.importSettings(AndroidTextDocuments.read(context, uri, SettingsTransfer.MAX_CHARS))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                appComposition.userMessages.show("Could not read settings. Choose a JSON file under 256 KB.")
            }
        }
    }

    LaunchedEffect(runtime) {
        runtime.effects.collect { effect ->
            when (effect) {
                is DataSettingsRuntimeEffect.CreateExportDocument -> {
                    try {
                        if (effect.mimeType == "application/json") {
                            pendingSettingsExport = effect.content
                            settingsExportLauncher.launch(effect.filename)
                        } else if (effect.mimeType == "text/csv") {
                            HistoryExportActivity.start(context, effect.filename, effect.content)
                        } else {
                            pendingExport.replace(effect.content)
                            exportLauncher.launch(effect.filename)
                        }
                    } catch (_: ActivityNotFoundException) {
                        pendingExport.clear()
                        pendingSettingsExport = null
                        appComposition.userMessages.show(FILE_PICKER_UNAVAILABLE)
                    } catch (_: IOException) {
                        appComposition.userMessages.show(PresentationCopy.WRITE_ERROR)
                    }
                }
                DataSettingsRuntimeEffect.OpenSettingsImportDocument -> {
                    try {
                        settingsImportLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    } catch (_: ActivityNotFoundException) {
                        appComposition.userMessages.show(FILE_PICKER_UNAVAILABLE)
                    }
                }
                DataSettingsRuntimeEffect.OpenImportDocument -> {
                    try {
                        importLauncher.launch(arrayOf("text/plain"))
                    } catch (_: ActivityNotFoundException) {
                        appComposition.userMessages.show(FILE_PICKER_UNAVAILABLE)
                    }
                }
                DataSettingsRuntimeEffect.OpenAppLinkSettings -> {
                    Toast.makeText(
                        context,
                        "The option should be under \"Open by default\"",
                        Toast.LENGTH_LONG,
                    ).show()
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        },
                    )
                }
                DataSettingsRuntimeEffect.SettingsReset -> onSettingsReset()
                is DataSettingsRuntimeEffect.Message ->
                    appComposition.userMessages.show(effect.text)
            }
        }
    }

    DataSettingsRuntimeContent(
        repository = appComposition.settings,
        runtime = runtime,
        state = runtimeState,
        showNavigation = showNavigation,
        onBack = onBack,
    ) { onDismiss ->
        MessageActionDialog(
            message = "Since Harmonic does not own the domain news.ycombinator.com, " +
                "intercepting links needs to be enabled manually.\n\n" +
                "Go to \"Open by default\" → \"Add link\" in the linked app settings page.",
            neutralLabel = "Go to settings",
            onNeutral = runtime::openAppLinkSettings,
            onDismiss = onDismiss,
        )
    }
}
