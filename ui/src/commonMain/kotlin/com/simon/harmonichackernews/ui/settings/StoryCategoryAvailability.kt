package com.simon.harmonichackernews.ui.settings

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.simon.harmonichackernews.summary.LocalModelCatalog
import com.simon.harmonichackernews.summary.LocalModelTransferState
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies

data class StoryCategoryAvailability(val canEnable: Boolean = true, val reason: String? = null)

@Composable
fun storyCategoryAvailability(): StoryCategoryAvailability {
    val app = LocalHarmonicUiDependencies.current
    val preferences = app.articleCategorySettings.updates.collectAsStateWithLifecycle(initialValue = app.articleCategorySettings.snapshot()).value
    val native = app.articleCategories.state.collectAsStateWithLifecycle().value.native
    val models = app.localModels
    val manager = models?.cachedState?.collectAsStateWithLifecycle()?.value
    val model = LocalModelCatalog.embeddingModel
    val supported = app.articleCategories.supported && models?.isSupported(model) == true
    val state = manager?.statuses?.get(model.id)?.state
    val runtime = manager?.runtimeStatuses?.get(model.runtime)
    return when {
        !native.checked && preferences.preferSystemEmbeddings -> StoryCategoryAvailability(false, "Checking model…")
        native.model != null && preferences.preferSystemEmbeddings -> StoryCategoryAvailability()
        !supported -> StoryCategoryAvailability(false, "Unavailable on this device")
        state == LocalModelTransferState.DOWNLOADED -> StoryCategoryAvailability()
        runtime?.active == true && runtime.pendingModelId == model.id ->
            StoryCategoryAvailability(false, "Installing runtime…")
        state == LocalModelTransferState.DOWNLOADING || state == LocalModelTransferState.WAITING ->
            StoryCategoryAvailability(false, "Downloading model…")
        else -> StoryCategoryAvailability(false, "Download to enable")
    }
}
