package com.simon.harmonichackernews.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsNavigationStoreTest {
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
