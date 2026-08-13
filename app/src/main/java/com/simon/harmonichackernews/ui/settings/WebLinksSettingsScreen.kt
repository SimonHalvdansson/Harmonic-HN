package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.settings.AndroidSettingsResources
import androidx.compose.runtime.remember

@Composable
fun WebLinksSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<WebLinksSettingsDialog?>(null) }
    val app = LocalHarmonicUiDependencies.current
    val repository = app.settings
    val presenter = remember(app) { WebLinksSettingsPresenter(repository) }
    val appSettings by repository.updates.collectAsState(initial = repository.snapshot())
    val reading = appSettings.reading
    val state = presenter.state(
        settings = appSettings,
        fontLabel = AndroidSettingsResources.fontLabel(context, reading.readerFont),
    )
    SharedWebLinksSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onBooleanChanged = presenter::setBoolean,
        onReaderFontSizeChanged = presenter::setReaderFontSize,
        onDialogRequested = { dialog = it },
        contentVersion = appSettings.hashCode(),
    )

    when (dialog) {
        WebLinksSettingsDialog.Preload -> PreloadWebViewDialog(onDismiss = { dialog = null })
        WebLinksSettingsDialog.ReaderFont -> FontSelectionDialog(
            readerMode = true,
            onDismiss = { dialog = null },
        )
        WebLinksSettingsDialog.ArchiveDomains -> ArchiveRedirectDomainsDialog(
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}
