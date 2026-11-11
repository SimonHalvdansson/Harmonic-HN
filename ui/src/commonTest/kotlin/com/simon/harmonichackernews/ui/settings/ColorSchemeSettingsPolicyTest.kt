package com.simon.harmonichackernews.ui.settings

import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.ColorSchemeStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorSchemeSettingsPolicyTest {
    @Test
    fun summaryUsesBlackOnlyForExplicitDarkAppearanceWithTheOverride() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        val presenter = AppearanceSettingsPresenter(repository)
        presenter.setColorScheme("slate")
        for (pureBlack in listOf(false, true)) for (system in listOf(false, true)) {
            for (dark in listOf(false, true)) {
                presenter.setPureBlackDarkMode(pureBlack)
                presenter.setFollowSystem(system)
                presenter.setManualDark(dark)
                val expected = when {
                    system -> "System · Slate"
                    !dark -> "Light · Slate"
                    pureBlack -> "Black"
                    else -> "Dark · Slate"
                }
                assertEquals(expected, themeSettingsSummary(repository.snapshot().appearance))
            }
        }
    }

    @Test
    fun pureBlackDisplayOverridesLeaveEditorSelectionsAndStoredPreferencesIntact() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        val appearance = AppearanceSettingsPresenter(repository)
        appearance.setColorScheme("blue")
        appearance.setColorStyle(ColorSchemeStyle.Vibrant)
        appearance.setPureBlackDarkMode(true)
        appearance.setNighttimePureBlackDarkMode(true)
        val stored = repository.snapshot()
        val active = stored.copy(pureBlackDarkMode = true)
        val stories = StoriesSettingsPresenter(repository).state(active,
            com.simon.harmonichackernews.ui.content.SettingsStoryPreviewModel,
            androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Gray))
        val comments = CommentsSettingsPresenter(repository).state(active)
        assertEquals("flat", stories.displayStyle)
        kotlin.test.assertFalse(stories.tint)
        kotlin.test.assertTrue(stories.pureBlackDarkMode)
        assertEquals(com.simon.harmonichackernews.settings.DisplayStyle.FLAT, comments.displayStyle)
        kotlin.test.assertTrue(comments.pureBlackDarkMode)
        assertEquals("blue", appearance.themeState("21:00 - 06:00", true, true, active).schemes.light)
        assertEquals(ColorSchemeStyle.Vibrant, appearance.themeState("21:00 - 06:00", true, true, active).schemes.lightStyle)
        assertEquals(stored, repository.snapshot())
        assertEquals(stored.comments.displayStyle, CommentsSettingsPresenter(repository).state(stored).displayStyle)
    }

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
