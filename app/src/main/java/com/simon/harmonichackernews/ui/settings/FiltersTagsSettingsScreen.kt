package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun FiltersTagsSettingsScreen(showNavigation: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = LocalHarmonicUiDependencies.current
    val settingsRepository = app.settings
    val presenter = remember(app) {
        FiltersTagsSettingsPresenter(app.settings, app.contentFilters, app.userTags)
    }
    val settings by settingsRepository.updates.collectAsState(
        initial = settingsRepository.snapshot(),
    )
    var tagRefresh by remember { mutableIntStateOf(0) }
    var filterDialog by rememberSaveable { mutableStateOf<ContentFilterDialog?>(null) }
    var tagDialogUser by rememberSaveable { mutableStateOf<String?>(null) }
    var profileUser by rememberSaveable { mutableStateOf<String?>(null) }
    val state = presenter.state(settings)

    SharedFiltersTagsSettingsScreen(
        state = state,
        showNavigation = showNavigation,
        onBack = onBack,
        onHideJobsChanged = presenter::setHideJobs,
        onFilterRequested = { filterDialog = it },
        onProfileRequested = { profileUser = it },
        onTagEditRequested = { tagDialogUser = it },
        onTagDeleteRequested = {
            presenter.setTag(it, "")
            tagRefresh++
        },
        contentVersion = settings.hashCode() + tagRefresh,
    )

    filterDialog?.let { type ->
        val content = type.content
        FilterListDialog(
            type = content.type,
            title = content.title,
            subtitle = content.subtitle,
            inputLabel = content.inputLabel,
            emptyMessage = content.emptyMessage,
            onDismiss = { filterDialog = null },
        )
    }
    tagDialogUser?.let { userName ->
        UserTagDialog(
            userName = userName,
            currentTag = presenter.tagFor(userName),
            onDismiss = { tagDialogUser = null },
            onSaved = {
                tagRefresh++
                tagDialogUser = null
            },
        )
    }
    profileUser?.let { userName ->
        UserSettingsDialog(
            userName = userName,
            onDismiss = { profileUser = null },
            onTagChanged = { tagRefresh++ },
        )
    }
}
