package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.SettingsSearchEntry
import com.simon.harmonichackernews.settings.SettingsSearchIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSearchTest {
    private val labels = SettingsSection.entries.associateWith { it.name }
    private fun entries(availability: SettingsSearchAvailability = SettingsSearchAvailability()) =
        settingsSearchCatalog(labels, availability)
    private fun target(title: String): SettingsSearchEntry = entries().first { it.title == title }

    @Test fun everyEntryHasAUniqueIdAndNavigableDestination() {
        val entries = entries(SettingsSearchAvailability(loggedIn = true, debug = true, geminiNano = true))
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        entries.forEach {
            assertTrue(SettingsSection.fromRoute(it.screenRoute) != null, it.id)
            assertTrue(it.path.isNotEmpty(), it.id)
            assertFalse(it.screenRoute == SettingsSection.Search.route)
        }
    }

    @Test fun synonymsFindTheExpectedSettingsWithoutChangingVisibleCopy() {
        val index = SettingsSearchIndex(entries())
        for ((query, expected) in listOf(
            "dead comments" to "Hide comments", "flagged" to "Hide comments",
            "adblock" to "Block in-app browser ads", "grey" to "Gray out clicked posts",
            "typeface" to "Font", "avatar" to "UserAvatars", "backup" to "Export settings",
        )) assertTrue(index.search(query).any { it.title == expected }, query)
        assertEquals(SettingsSection.Theme.route, index.search("dark mode").first().screenRoute)
        assertEquals(SettingsSection.Font.route, index.search("comments font").first().screenRoute)
    }

    @Test fun unavailableControlsAreNotIndexed() {
        val availability = SettingsSearchAvailability(capabilities = SettingsSearchCapabilities(
            webLinks = WebLinksSettingsCapabilities(adBlocking = false, readerMode = false,
                closeWebViewOnBack = false, preloadWebsites = false),
            notifications = false, systemSettings = false, volumeNavigation = false,
            transparentStatusBar = false,
        ), cloudAi = false)
        val unavailable = setOf("Block in-app browser ads", "Reader mode", "Hide browser on back",
            "Preload websites", "Notifications", "Volume buttons for navigation", "Transparent status bar", "API Key")
        assertFalse(entries(availability).any { it.title in unavailable || it.screenRoute == "debug" })
        assertFalse(entries().any { it.screenRoute == "notifications" })
    }

    @Test fun resultAndNestedDetailReturnToTheSameSearchBeforeClosing() {
        for (twoPane in listOf(false, true)) {
            val navigation = SettingsNavigationStore(twoPane = twoPane)
            navigation.navigateTo(SettingsSection.Search)
            navigation.updateSearchQuery("dead comments")
            assertTrue(navigation.openSearchResult(target("Hide comments")))
            navigation.navigateTo(SettingsSection.ThreadDepth, preserveCurrentDetail = true)
            assertTrue(navigation.navigateBack())
            assertEquals(SettingsSection.Comments, navigation.state.value.selectedSection)
            assertTrue(navigation.navigateBack())
            assertEquals(SettingsSection.Search, navigation.state.value.selectedSection)
            assertEquals("dead comments", navigation.state.value.searchQuery)
            assertEquals(listOf(target("Hide comments").id), navigation.state.value.recentSearchResultIds)
            assertTrue(navigation.navigateBack())
            assertFalse(SettingsSection.Search in navigation.state.value.detailStack)
        }
    }

    @Test fun restorationPreservesSearchAndItsTargetAcrossLayoutChanges() {
        val original = SettingsNavigationStore()
        original.navigateTo(SettingsSection.Search)
        original.updateSearchQuery("dead")
        val target = target("Hide comments")
        original.openSearchResult(target)
        val state = original.state.value
        val restored = SettingsNavigationStore(twoPane = true, restoredRoutes = original.savedRoutes(),
            restoredSearchQuery = state.searchQuery, restoredSearchResultId = state.searchResultId,
            restoredRecentSearchResultIds = state.recentSearchResultIds)
        restored.updateLayout(false)
        assertEquals(target.id, restored.state.value.searchResultId)
        assertTrue(restored.navigateBack())
        assertEquals("dead", restored.state.value.searchQuery)
        assertEquals(SettingsSection.Search, restored.state.value.selectedSection)
        restored.updateLayout(true)
        assertTrue(restored.navigateBack())
    }

    @Test fun repeatedResultsDoNotAccumulateDestinationsOrRecentDuplicates() {
        val navigation = SettingsNavigationStore(twoPane = true, initialSection = SettingsSection.Comments)
        navigation.navigateTo(SettingsSection.Search)
        repeat(8) {
            navigation.openSearchResult(target("Hide comments"))
            assertEquals(listOf(SettingsSection.Search, SettingsSection.Comments), navigation.state.value.detailStack)
            navigation.navigateBack()
        }
        assertEquals(1, navigation.state.value.recentSearchResultIds.size)
        navigation.navigateTo(SettingsSection.Stories)
        assertFalse(navigation.openSearchResult(target("Hide comments")))
        assertEquals(null, navigation.state.value.searchResultId)
    }

    @Test fun closingWideSearchRemovesItsResultAndReopeningStartsAFreshQuery() {
        val navigation = SettingsNavigationStore(twoPane = true)
        navigation.navigateTo(SettingsSection.Search)
        navigation.updateSearchQuery("dead")
        navigation.openSearchResult(target("Hide comments"))
        navigation.closeSearch()
        assertEquals(listOf(SettingsSection.Appearance), navigation.state.value.detailStack)
        assertEquals(null, navigation.state.value.searchResultId)
        navigation.navigateTo(SettingsSection.Search)
        assertEquals("", navigation.state.value.searchQuery)
        assertEquals(1, navigation.state.value.recentSearchResultIds.size)
    }

    @Test fun wideSearchRestoresThePreviousDetailHierarchyAfterBackOrClose() {
        for (useToolbar in listOf(false, true)) {
            val navigation = SettingsNavigationStore(twoPane = true)
            navigation.navigateTo(SettingsSection.Theme, preserveCurrentDetail = true)
            navigation.navigateTo(SettingsSection.Search)
            navigation.openSearchResult(target("Hide comments"))
            val state = navigation.state.value
            val restored = SettingsNavigationStore(twoPane = true, restoredRoutes = navigation.savedRoutes(),
                restoredSearchOriginRoutes = state.searchOriginRoutes)
            if (useToolbar) restored.closeSearch() else {
                assertTrue(restored.navigateBack())
                assertEquals(SettingsSection.Search, restored.state.value.selectedSection)
                assertTrue(restored.navigateBack())
            }
            assertEquals(listOf(SettingsSection.Appearance, SettingsSection.Theme), restored.state.value.detailStack)
            assertTrue(restored.state.value.searchOriginRoutes.isEmpty())
        }
    }
}
