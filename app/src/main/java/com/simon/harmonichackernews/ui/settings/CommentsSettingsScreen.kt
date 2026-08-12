package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.AndroidAppComposition
import com.simon.harmonichackernews.settings.CommentSortingPreference
import com.simon.harmonichackernews.settings.CommentVolumeNavigationMode
import com.simon.harmonichackernews.settings.CommentsProvider

@Composable
fun CommentsSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<CommentsSettingsDialog?>(null) }
    val app = remember(context) { AndroidAppComposition.get(context) }
    val repository = app.settings
    val presenter = remember(app) { CommentsSettingsPresenter(repository) }
    val settings by repository.updates.collectAsState(initial = repository.snapshot())
    val state = presenter.state(settings)
    SharedCommentsSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onDisplayStyleChanged = presenter::setDisplayStyle,
        onTextSizeOffsetChanged = presenter::setTextSizeOffset,
        onBooleanChanged = presenter::setBoolean,
        onDialogRequested = { dialog = it },
        contentVersion = settings.hashCode(),
    )

    when (dialog) {
        CommentsSettingsDialog.Sorting -> ChoiceDialog(
            title = "Comment sorting",
            options = CommentSortingPreference.entries.map { it.storedValue to it.label },
            selected = state.sorting.storedValue,
            onDismiss = { dialog = null },
            onSelected = presenter::setSorting,
        )
        CommentsSettingsDialog.Provider -> ChoiceDialog(
            title = "Comments provider",
            options = CommentsProvider.entries.map { it.storedValue to it.label },
            selected = state.provider.storedValue,
            onDismiss = { dialog = null },
            onSelected = presenter::setProvider,
        )
        CommentsSettingsDialog.VolumeNavigation -> ChoiceDialog(
            title = "Volume buttons for navigation",
            options = CommentVolumeNavigationMode.entries.map { it.storedValue to it.label },
            selected = state.volumeNavigation.storedValue,
            onDismiss = { dialog = null },
            onSelected = presenter::setVolumeNavigation,
        )
        CommentsSettingsDialog.ThreadDepth -> ThreadDepthIndicatorsDialog(
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
internal fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) = SingleChoiceDialog(
    title = title,
    options = options,
    selected = selected,
    onDismiss = onDismiss,
    onSelected = {
        onSelected(it)
        onDismiss()
    },
)
