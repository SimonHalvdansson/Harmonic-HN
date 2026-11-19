package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

@Composable
fun UserTagRoute(
    userName: String,
    currentTag: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit,
) {
    val app = LocalHarmonicUiDependencies.current
    val presenter = remember(app) {
        FiltersTagsSettingsPresenter(app.settings, app.contentFilters, app.userTags)
    }
    UserTagDialog(
        currentTag = currentTag,
        currentColor = app.userTags.colorFor(userName),
        userName = userName,
        onSave = { tag, color ->
            presenter.setTag(userName, tag, color)
            onSaved(tag)
        },
        onDismiss = onDismiss,
    )
}
