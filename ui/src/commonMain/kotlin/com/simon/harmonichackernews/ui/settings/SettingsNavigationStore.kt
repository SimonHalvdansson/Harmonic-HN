package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.SettingsSearchEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Host Back and the in-page Up button must consume the same Settings hierarchy. */
fun handleSettingsBack(navigation: SettingsNavigationStore?, closeSettings: () -> Unit) {
    if (navigation?.navigateBack() != true) closeSettings()
}

enum class SettingsDetailNavigation { Replace, Push, Pop }

data class SettingsNavigationState(
    val detailStack: List<SettingsSection>,
    val twoPane: Boolean,
    val detailNavigation: SettingsDetailNavigation = SettingsDetailNavigation.Replace,
    val returnToSource: Boolean = false,
    val searchQuery: String = "",
    val searchResultId: String? = null,
    val recentSearchResultIds: List<String> = emptyList(),
    val searchOriginRoutes: List<String> = emptyList(),
) {
    val selectedSection: SettingsSection
        get() = detailStack.lastOrNull() ?: SettingsSection.Appearance

    val canNavigateBackWithinSettings: Boolean
        get() = detailStack.size > if ((twoPane || returnToSource) && selectedSection != SettingsSection.Search) 1 else 0

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
    val focusHideComments: Boolean = false,
    restoredSearchQuery: String = "",
    restoredSearchResultId: String? = null,
    restoredRecentSearchResultIds: List<String> = emptyList(),
    restoredSearchOriginRoutes: List<String> = emptyList(),
) {
    // Keep reading positions for the whole Settings visit, including recreation of the
    // outer navigation surface when its retained transition changes pane layout.
    val scrollHistory = SettingsScrollHistory()

    private val mutableState = MutableStateFlow(
        SettingsNavigationState(
            detailStack = restoredRoutes
                ?.mapNotNull(SettingsSection::fromRoute)
                .orEmpty()
                .ifEmpty {
                    listOfNotNull(initialSection ?: SettingsSection.Appearance.takeIf { twoPane })
                },
            twoPane = twoPane,
            returnToSource = focusHideComments,
            searchQuery = restoredSearchQuery,
            searchResultId = restoredSearchResultId,
            recentSearchResultIds = restoredRecentSearchResultIds,
            searchOriginRoutes = restoredSearchOriginRoutes,
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
        mutableState.value = current.copy(
            detailStack = stack,
            detailNavigation = if (preserveCurrentDetail) SettingsDetailNavigation.Push else SettingsDetailNavigation.Replace,
            searchQuery = if (section == SettingsSection.Search) "" else current.searchQuery,
            searchResultId = current.searchResultId.takeIf { preserveCurrentDetail && section != SettingsSection.Search },
            searchOriginRoutes = when {
                section == SettingsSection.Search && SettingsSection.Search !in current.detailStack ->
                    current.detailStack.map { it.route }
                section == SettingsSection.Search || preserveCurrentDetail -> current.searchOriginRoutes
                else -> emptyList()
            },
        ).normalized()
        return true
    }

    fun updateSearchQuery(query: String) {
        mutableState.value = mutableState.value.copy(searchQuery = query)
    }

    fun openSearchResult(entry: SettingsSearchEntry): Boolean {
        val section = SettingsSection.fromRoute(entry.screenRoute) ?: return false
        if (section == SettingsSection.Search) return false
        val current = mutableState.value
        val searchIndex = current.detailStack.indexOf(SettingsSection.Search)
        if (searchIndex < 0) return false
        mutableState.value = current.copy(
            detailStack = current.detailStack.take(searchIndex + 1) + section,
            detailNavigation = SettingsDetailNavigation.Push,
            searchResultId = entry.id,
            recentSearchResultIds = (listOf(entry.id) + current.recentSearchResultIds).distinct().take(5),
        )
        return true
    }

    fun closeSearch() {
        val current = mutableState.value
        val searchIndex = current.detailStack.indexOf(SettingsSection.Search)
        if (searchIndex < 0) return
        mutableState.value = current.copy(
            detailStack = current.searchOriginRoutes.mapNotNull(SettingsSection::fromRoute)
                .ifEmpty { current.detailStack.take(searchIndex) },
            detailNavigation = SettingsDetailNavigation.Pop,
            searchResultId = null,
            searchOriginRoutes = emptyList(),
        ).normalized()
    }

    fun navigateBack(): Boolean {
        val current = mutableState.value
        if (!current.canNavigateBackWithinSettings) return false
        if (current.selectedSection == SettingsSection.Search) {
            closeSearch()
            return true
        }
        mutableState.value = current.copy(
            detailStack = current.detailStack.dropLast(1),
            detailNavigation = SettingsDetailNavigation.Pop,
            searchResultId = current.searchResultId.takeUnless {
                current.detailStack.dropLast(1).lastOrNull() == SettingsSection.Search
            },
        ).normalized()
        return true
    }

    fun restore(routes: List<String>) {
        mutableState.value = mutableState.value.copy(
            detailStack = routes.mapNotNull(SettingsSection::fromRoute),
            detailNavigation = SettingsDetailNavigation.Replace,
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
