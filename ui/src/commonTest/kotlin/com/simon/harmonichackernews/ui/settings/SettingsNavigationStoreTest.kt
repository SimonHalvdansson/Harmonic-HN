package com.simon.harmonichackernews.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsNavigationStoreTest {
    @Test
    fun manageFrontpagesRestoresAndReturnsToItsEntryPointInEitherLayout() {
        for (twoPane in listOf(false, true)) {
            for (parent in listOf(SettingsSection.Stories, SettingsSection.FiltersTags)) {
                val navigation = SettingsNavigationStore(initialSection = parent, twoPane = twoPane)
                navigation.navigateTo(SettingsSection.Frontpages, preserveCurrentDetail = true)
                val restored = SettingsNavigationStore(
                    twoPane = twoPane,
                    restoredRoutes = navigation.savedRoutes(),
                )
                assertEquals(SettingsSection.Frontpages, restored.state.value.selectedSection)
                assertTrue(restored.navigateBack())
                assertEquals(parent, restored.state.value.selectedSection)
            }
        }
    }

    @Test
    fun hostBackTraversesThemeAppearanceAndListBeforeClosing() {
        val navigation = SettingsNavigationStore(initialSection = SettingsSection.Appearance)
        navigation.navigateTo(SettingsSection.Theme, preserveCurrentDetail = true)
        var closes = 0

        handleSettingsBack(navigation) { closes++ }
        assertEquals(listOf(SettingsSection.Appearance), navigation.state.value.detailStack)
        assertEquals(0, closes)
        handleSettingsBack(navigation) { closes++ }
        assertTrue(navigation.state.value.detailStack.isEmpty())
        assertEquals(0, closes)
        handleSettingsBack(navigation) { closes++ }
        assertEquals(1, closes)
    }

    @Test
    fun wideSettingsBackKeepsItsDefaultDetailUntilTheHostCloses() {
        val navigation = SettingsNavigationStore(twoPane = true)
        navigation.navigateTo(SettingsSection.Theme, preserveCurrentDetail = true)
        var closes = 0
        handleSettingsBack(navigation) { closes++ }
        assertEquals(listOf(SettingsSection.Appearance), navigation.state.value.detailStack)
        assertEquals(0, closes)
        handleSettingsBack(navigation) { closes++ }
        assertEquals(1, closes)
    }

    @Test
    fun selectedDetailSurvivesRepeatedLayoutChanges() {
        val navigation = SettingsNavigationStore(twoPane = true)
        navigation.navigateTo(SettingsSection.Comments)

        repeat(25) {
            navigation.updateLayout(twoPane = false)
            assertEquals(SettingsSection.Comments, navigation.state.value.selectedSection)
            assertTrue(navigation.state.value.canNavigateBackWithinSettings)

            navigation.updateLayout(twoPane = true)
            assertEquals(SettingsSection.Comments, navigation.state.value.selectedSection)
            assertFalse(navigation.state.value.canNavigateBackWithinSettings)
        }
    }

    @Test
    fun expandingFromTheListCreatesTheDefaultDetail() {
        val navigation = SettingsNavigationStore(twoPane = false)
        assertTrue(navigation.state.value.detailStack.isEmpty())

        navigation.updateLayout(twoPane = true)

        assertEquals(listOf(SettingsSection.Appearance), navigation.state.value.detailStack)
    }

    @Test
    fun navigatingUpFromAnyPhoneDetailReturnsToTheSettingsList() {
        SettingsSection.entries.forEach { section ->
            val navigation = SettingsNavigationStore(twoPane = false)

            navigation.navigateTo(section)
            assertTrue(navigation.navigateBack(), "Up should consume the $section detail")
            assertTrue(navigation.state.value.detailStack.isEmpty())
            assertFalse(navigation.state.value.canNavigateBackWithinSettings)
        }
    }

    @Test
    fun savedRoutesStartAtTheListAndPreserveDetailOrder() {
        val navigation = SettingsNavigationStore(twoPane = false)
        assertEquals(listOf(SettingsNavigationStore.LIST_ROUTE), navigation.savedRoutes())

        navigation.navigateTo(SettingsSection.Appearance)
        navigation.navigateTo(SettingsSection.Comments, preserveCurrentDetail = true)

        assertEquals(
            listOf(
                SettingsNavigationStore.LIST_ROUTE,
                SettingsSection.Appearance.route,
                SettingsSection.Comments.route,
            ),
            navigation.savedRoutes(),
        )
    }
}
