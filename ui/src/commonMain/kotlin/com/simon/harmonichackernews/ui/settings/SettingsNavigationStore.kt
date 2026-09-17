package com.simon.harmonichackernews.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsNavigationState(
    val detailStack: List<SettingsSection>,
    val twoPane: Boolean,
) {
    val selectedSection: SettingsSection
        get() = detailStack.lastOrNull() ?: SettingsSection.Appearance

    val canNavigateBackWithinSettings: Boolean
        get() = detailStack.size > if (twoPane) 1 else 0

    val routes: List<String>
        get() {
            val routes = ArrayList<String>(detailStack.size + 1)
            routes += SettingsNavigationStore.LIST_ROUTE
            detailStack.forEach { section -> routes += section.route }
            return routes
        }
}

/** Portable list/detail navigation, restoration, and two-pane default policy. */
class SettingsNavigationStore(
    initialSection: SettingsSection? = null,
    twoPane: Boolean = false,
    restoredRoutes: List<String>? = null,
) {
    private val mutableState = MutableStateFlow(
        SettingsNavigationState(
            detailStack = restoredRoutes
                ?.mapNotNull(SettingsSection::fromRoute)
                .orEmpty()
                .ifEmpty {
                    listOfNotNull(initialSection ?: SettingsSection.Appearance.takeIf { twoPane })
                },
            twoPane = twoPane,
        ).normalized(),
    )
    val state: StateFlow<SettingsNavigationState> = mutableState.asStateFlow()

    fun updateLayout(twoPane: Boolean) {
        mutableState.value = mutableState.value.copy(twoPane = twoPane).normalized()
    }

    fun navigateTo(section: SettingsSection, preserveCurrentDetail: Boolean = false): Boolean {
        val current = mutableState.value
        if (current.detailStack.lastOrNull() == section) return false
        val stack = if (preserveCurrentDetail) {
            current.detailStack + section
        } else {
            listOf(section)
        }
        mutableState.value = current.copy(detailStack = stack).normalized()
        return true
    }

    fun navigateBack(): Boolean {
        val current = mutableState.value
        if (!current.canNavigateBackWithinSettings) return false
        mutableState.value = current.copy(detailStack = current.detailStack.dropLast(1)).normalized()
        return true
    }

    fun restore(routes: List<String>) {
        mutableState.value = mutableState.value.copy(
            detailStack = routes.mapNotNull(SettingsSection::fromRoute),
        ).normalized()
    }

    fun savedRoutes(): List<String> = state.value.routes

    private fun SettingsNavigationState.normalized(): SettingsNavigationState =
        if (twoPane && detailStack.isEmpty()) copy(detailStack = listOf(SettingsSection.Appearance))
        else this

    companion object {
        const val LIST_ROUTE = "__settings_list__"
    }
}
