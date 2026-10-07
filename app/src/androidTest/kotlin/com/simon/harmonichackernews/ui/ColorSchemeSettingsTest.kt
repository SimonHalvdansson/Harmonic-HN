package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.settings.*
import com.simon.harmonichackernews.ui.settings.*
import com.simon.harmonichackernews.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class ColorSchemeSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = InMemoryKeyValueStore()
    private val repository = AppSettingsRepository(store, store.changes)
    private val systemDark = mutableStateOf(false)
    private val couplingLabel = "Use the same color scheme in light and dark"

    private fun showSettings(block: () -> Unit) {
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        try {
            compose.setContent {
                val settings by repository.updates.collectAsState(initial = repository.snapshot())
                val dark = if (settings.appearance.followSystem) systemDark.value else settings.appearance.manualDark
                val palette = harmonicThemePalette(compose.activity,
                    ThemeSelection.forScheme(settings.appearance.colorSchemes.forMode(dark), dark, settings.appearance.colorSchemes.styleForMode(dark)))
                CompositionLocalProvider(
                    LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, scene),
                    // Hardware ripple timing is independent of Compose's test clock. Sample resting roles.
                    LocalRippleConfiguration provides null,
                ) {
                    HarmonicTheme(palette.colorScheme, dark) {
                        ThemeSettingsRoute(repository,
                            labels = ThemeRouteLabels("21:00 - 06:00", dark, true),
                            showNavigation = false, onBack = {}, onThemeChanged = {},
                            resolvePreviewScheme = { scheme, previewDark, style ->
                                harmonicThemePalette(compose.activity, ThemeSelection.forScheme(scheme, previewDark, style))
                            }, dialogContent = { _, _, _ -> })
                    }
                }
            }
            block()
        } finally { scene.close() }
    }

    private fun rootList() = compose.onNode(hasScrollAction() and !hasAnyAncestor(hasScrollAction()))
    private fun select(tag: String, scheme: String) {
        rootList().performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performScrollToIndex(ColorSchemeCatalog.options.indexOfFirst { it.value == scheme })
        compose.onNode(hasContentDescription("${ColorSchemeCatalog.label(scheme)} color scheme") and
            hasAnyAncestor(hasTestTag(tag))).performClick()
    }
    private fun click(text: String) {
        rootList().performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }
    private fun appearance() = AppSettingsRepository(store, store.changes).snapshot().appearance

    private fun style(tag: String, label: String) {
        rootList().performScrollToNode(hasTestTag("$tag style"))
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("$tag style"))).performClick()
    }

    private fun assertSwatch(tag: String, base: String, dark: Boolean, style: ColorSchemeStyle = ColorSchemeStyle.Balanced) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val expected = harmonicThemePalette(compose.activity, ThemeSelection.forScheme(base, dark, style)).colorScheme
        val pixels = compose.onNodeWithTag("Swatch-$tag-$base", useUnmergedTree = true).captureToImage().toPixelMap()
        fun assertRole(expected: Color, x: Float, y: Float) {
            val actual = pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]
            // Small rendering/compositing differences are acceptable; palette/mode differences are not.
            assertEquals(expected.red, actual.red, .01f)
            assertEquals(expected.green, actual.green, .01f)
            assertEquals(expected.blue, actual.blue, .01f)
        }
        assertRole(expected.primary, .7f, .3f)
        assertRole(expected.secondary, .7f, .7f)
        assertRole(expected.tertiary, .3f, .7f)
    }

    @Test
    fun synchronizationIncludesStylesAndPersistsThroughExplicitAppearances() = showSettings {
        assertEquals(ColorSchemeSelection(), appearance().colorSchemes)
        compose.onNodeWithTag("Color scheme style").assertDoesNotExist()
        select("Color scheme", "blue")
        style("Color scheme", "Vibrant")
        assertEquals(ColorSchemeStyle.Vibrant, appearance().colorSchemes.lightStyle)
        assertEquals(ColorSchemeStyle.Vibrant, appearance().colorSchemes.darkStyle)
        assertSwatch("Color scheme", "blue", false, ColorSchemeStyle.Vibrant)
        click(couplingLabel)
        assertFalse(appearance().colorSchemes.coupled)
        assertEquals(appearance().colorSchemes.lightStyle, appearance().colorSchemes.darkStyle)
        style("Dark color scheme", "Neutral surfaces")
        click("Light")
        compose.onNodeWithText(couplingLabel).assertDoesNotExist()
        compose.onNodeWithTag("Dark color scheme").assertDoesNotExist()
        select("Light color scheme", "amber")
        style("Light color scheme", "Balanced")
        assertEquals("amber", appearance().colorSchemes.light)
        assertEquals("blue", appearance().colorSchemes.dark)
        assertEquals(ColorSchemeStyle.NeutralSurfaces, appearance().colorSchemes.darkStyle)
        click("Dark")
        compose.onNodeWithText(couplingLabel).assertDoesNotExist()
        compose.onNodeWithTag("Light color scheme").assertDoesNotExist()
        rootList().performScrollToNode(hasTestTag("Dark color scheme"))
        compose.onNode(hasContentDescription("Blue color scheme") and
            hasAnyAncestor(hasTestTag("Dark color scheme"))).assertIsSelected()
        assertSwatch("Dark color scheme", "blue", true, ColorSchemeStyle.NeutralSurfaces)
        compose.runOnIdle { systemDark.value = true }
        click("System")
        click(couplingLabel)
        assertTrue(appearance().followSystem)
        assertTrue(appearance().manualDark)
        assertEquals("blue", appearance().colorSchemes.light)
        assertEquals(ColorSchemeStyle.NeutralSurfaces, appearance().colorSchemes.lightStyle)
        assertTrue(appearance().colorSchemes.coupled)
        click("Light")
        compose.onNodeWithText(couplingLabel).assertDoesNotExist()
        select("Light color scheme", "rose")
        style("Light color scheme", "Vibrant")
        assertEquals("rose", appearance().colorSchemes.dark)
        assertEquals(ColorSchemeStyle.Vibrant, appearance().colorSchemes.darkStyle)
        click("System")
        rootList().performScrollToNode(hasTestTag("Color scheme"))
        compose.onNodeWithTag("Color scheme").assertExists()
        assertTrue(appearance().colorSchemes.coupled)
    }

    @Test
    fun dynamicSwatchesUseActualSystemRolesInTheDisplayedMode() {
        repository.setColorSchemesCoupled(false, false)
        repository.setColorScheme("classic", false)
        repository.setColorScheme("pure", true)
        systemDark.value = true
        showSettings {
            click(couplingLabel)
            assertEquals(ColorSchemeSelection("pure"), appearance().colorSchemes)
            select("Color scheme", "dynamic")
            compose.onNodeWithTag("Color scheme style").assertDoesNotExist()
            assertSwatch("Color scheme", "dynamic", true)
            compose.runOnIdle { systemDark.value = false }
            assertSwatch("Color scheme", "dynamic", false)
            click(couplingLabel)
            select("Light color scheme", "dynamic")
            assertSwatch("Light color scheme", "dynamic", false)
            select("Dark color scheme", "dynamic")
            assertSwatch("Dark color scheme", "dynamic", true)
        }
    }

    @Test
    fun curatedAndDynamicChoicesHideStyleControlsWithoutDiscardingTheStyle() = showSettings {
        select("Color scheme", "green")
        style("Color scheme", "Vibrant")
        for (base in listOf("classic", "pure", "hacker_news", "hacker", "gray", "dynamic")) {
            select("Color scheme", base)
            compose.onNodeWithTag("Color scheme style").assertDoesNotExist()
            assertEquals(ColorSchemeStyle.Vibrant, appearance().colorSchemes.lightStyle)
        }
        select("Color scheme", "teal")
        rootList().performScrollToNode(hasTestTag("Color scheme style"))
        compose.onNode(hasText("Vibrant") and hasAnyAncestor(hasTestTag("Color scheme style"))).assertIsSelected()
    }
}
