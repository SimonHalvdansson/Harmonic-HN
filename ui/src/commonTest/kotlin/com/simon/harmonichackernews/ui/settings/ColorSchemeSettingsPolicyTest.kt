package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.ColorSchemeStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorSchemeSettingsPolicyTest {
    @Test
    fun sharedSelectionAndStyleSurviveEveryAppearanceMode() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        val presenter = AppearanceSettingsPresenter(repository)
        presenter.setColorScheme("hacker_news")
        presenter.setColorStyle(ColorSchemeStyle.Vibrant)
        for (system in listOf(false, true)) for (dark in listOf(false, true)) {
            presenter.setFollowSystem(system)
            presenter.setManualDark(dark)
            val appearance = repository.snapshot().appearance
            assertEquals("hacker_news", appearance.colorSchemes.light)
            assertEquals(appearance.colorSchemes.light, appearance.colorSchemes.dark)
            assertEquals(ColorSchemeStyle.Vibrant, appearance.colorSchemes.lightStyle)
            assertEquals(appearance.colorSchemes.lightStyle, appearance.colorSchemes.darkStyle)
            val label = if (system) "System" else if (dark) "Dark" else "Light"
            assertEquals("$label · HN", themeSettingsSummary(appearance))
        }
    }
}
