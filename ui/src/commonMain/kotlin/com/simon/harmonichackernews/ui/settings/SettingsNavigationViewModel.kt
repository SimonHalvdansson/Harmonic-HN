package com.simon.harmonichackernews.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.serialization.Serializable

@Serializable
internal data class SettingsNavigationRestoration(
    val routes: List<String>,
    val searchQuery: String = "",
    val searchResultId: String? = null,
    val recentSearchResultIds: List<String> = emptyList(),
    val searchOriginRoutes: List<String> = emptyList(),
)

/** Scoped to a Settings visit by the Navigation3 entry decorator on every host. */
internal class SettingsNavigationViewModel(
    savedStateHandle: SavedStateHandle,
    initialSection: SettingsSection?,
    twoPane: Boolean,
    focusHideComments: Boolean = false,
) : ViewModel() {
    private val restored = savedStateHandle.get<SavedState>("routes")?.let {
        decodeFromSavedState<SettingsNavigationRestoration>(it)
    }
    val navigation = SettingsNavigationStore(
        initialSection = initialSection.takeIf { restored == null },
        twoPane = twoPane,
        restoredRoutes = restored?.routes,
        focusHideComments = savedStateHandle.get<Boolean>("focusHideComments") ?: focusHideComments,
        restoredSearchQuery = restored?.searchQuery.orEmpty(),
        restoredSearchResultId = restored?.searchResultId,
        restoredRecentSearchResultIds = restored?.recentSearchResultIds.orEmpty(),
        restoredSearchOriginRoutes = restored?.searchOriginRoutes.orEmpty(),
    )

    init {
        savedStateHandle["focusHideComments"] = navigation.focusHideComments
        savedStateHandle.setSavedStateProvider("routes") {
            val state = navigation.state.value
            encodeToSavedState(SettingsNavigationRestoration(
                navigation.savedRoutes(), state.searchQuery, state.searchResultId, state.recentSearchResultIds,
                state.searchOriginRoutes,
            ))
        }
    }
}
