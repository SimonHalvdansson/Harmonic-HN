package com.simon.harmonichackernews.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.Serializable

@Serializable
internal data class SettingsNavigationRestoration(val routes: List<String>)

/** Scoped to a Settings visit by the Navigation3 entry decorator on every host. */
internal class SettingsNavigationViewModel(
    savedStateHandle: SavedStateHandle,
    initialSection: SettingsSection?,
    twoPane: Boolean,
) : ViewModel() {
    private val restoredRoutes = savedStateHandle.get<SavedState>("routes")?.let {
        decodeFromSavedState<SettingsNavigationRestoration>(it).routes
    }
    val navigation = SettingsNavigationStore(
        initialSection = initialSection.takeIf { restoredRoutes == null },
        twoPane = twoPane,
        restoredRoutes = restoredRoutes,
    )

    init {
        savedStateHandle.setSavedStateProvider("routes") {
            encodeToSavedState(SettingsNavigationRestoration(navigation.savedRoutes()))
        }
    }
}
