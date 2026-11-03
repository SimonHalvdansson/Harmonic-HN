package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.DomainFilterRule
import com.simon.harmonichackernews.settings.TitleFilterMatchMode
import com.simon.harmonichackernews.settings.TitleFilterRule
import com.simon.harmonichackernews.ui.settings.DomainFilterRuleEditor
import com.simon.harmonichackernews.ui.settings.TitleFilterRuleEditor
import com.simon.harmonichackernews.ui.settings.TitleFilterRulesDialog
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilterRuleDialogsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun removingTypedRulesAnimatesTheListThroughIntermediateHeights() {
        val rules = mutableStateOf(listOf(TitleFilterRule("Alpha"), TitleFilterRule("Beta"), TitleFilterRule("Gamma")))
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                TitleFilterRulesDialog(rules.value, onRulesChanged = { rules.value = it }, onDismiss = {})
            }
        }
        compose.waitForIdle()
        fun height() = compose.onNodeWithTag("settings-dialog-animated-content").fetchSemanticsNode().boundsInRoot.height
        val before = height()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { rules.value = listOf(TitleFilterRule("Alpha")) }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(112)
            val shrinking = height()
            compose.mainClock.advanceTimeBy(400)
            val after = height()
            assertTrue("Typed rule removal must animate height: $before, $shrinking, $after", shrinking < before - 1f && shrinking > after + 1f)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun unreadableRulesShowAWarningAndCannotBeOverwrittenFromTheList() {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                TitleFilterRulesDialog(emptyList(), onRulesChanged = { error("Unreadable rules must stay intact") },
                    onDismiss = {}, editable = false)
            }
        }
        compose.onNodeWithText("Add rule").assertIsNotEnabled()
        compose.onNodeWithText("Saved rules unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("These saved rules use an unsupported or damaged format. They are inactive, and their data has been kept unchanged. A newer version of Harmonic may be needed to read them.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun titlePreviewDistinguishesWholeWordsFromContainsTextAndSavesTheChosenMode() {
        var saved: TitleFilterRule? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                TitleFilterRuleEditor(onSave = { saved = it }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("title-filter-rule-input").performTextInput("AI")
        compose.onNode(hasText("A better office chair") and hasText("Visible")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Preview").assertExists()
        compose.onNodeWithText("Contains text").performScrollTo().performClick()
        compose.onNode(hasText("A better office chair") and hasText("Hidden")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save rule").performClick()
        compose.waitUntil(5_000) { saved != null }
        assertEquals(TitleFilterRule("AI", TitleFilterMatchMode.CONTAINS_TEXT), saved)
    }

    @Test
    fun titleListEditsOneLegacyRuleAndLeavesOtherModesUntouched() {
        val original = listOf(
            TitleFilterRule("AI", TitleFilterMatchMode.CONTAINS_TEXT),
            TitleFilterRule("crypto", TitleFilterMatchMode.CONTAINS_TEXT),
        )
        var saved = original
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                TitleFilterRulesDialog(original, onRulesChanged = { saved = it }, onDismiss = {})
            }
        }
        assertEquals(original, saved)
        compose.onNodeWithContentDescription("Edit AI").performClick()
        compose.onNodeWithText("Words / phrase").performScrollTo().performClick()
        compose.onNodeWithText("Save rule").performClick()
        compose.waitUntil(5_000) { saved != original }
        assertEquals(TitleFilterMatchMode.WORDS_PHRASE, saved[0].mode)
        assertEquals(original[1], saved[1])
        compose.onNodeWithContentDescription("Edit crypto").assertExists()
    }

    @Test
    fun internationalUrlPreviewNormalizesHostnameAndHonorsTheSubdomainSwitch() {
        var saved: DomainFilterRule? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                DomainFilterRuleEditor(onSave = { saved = it }, onDismiss = {})
            }
        }
        compose.onNodeWithTag("domain-filter-rule-input").performTextInput("https://WWW.BÜCHER.de.:8080/read?x=1")
        compose.onNodeWithTag("normalized-filter-host").assertTextEquals("www.xn--bcher-kva.de")
        compose.onNode(hasText("news.www.xn--bcher-kva.de") and hasText("Hidden")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("include-filter-subdomains").performScrollTo().performClick()
        compose.onNode(hasText("news.www.xn--bcher-kva.de") and hasText("Visible")).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save rule").performClick()
        compose.waitUntil(5_000) { saved != null }
        assertEquals("www.xn--bcher-kva.de", saved?.domain)
        assertFalse(saved!!.includeSubdomains)
        assertFalse(saved!!.legacyContainsText)
    }

    @Test
    fun editingAnOldDomainExplainsTheMatchingChangeBeforeSaving() {
        var saved: DomainFilterRule? = null
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                DomainFilterRuleEditor(
                    DomainFilterRule("example.com", legacyContainsText = true),
                    onSave = { saved = it }, onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("This saved rule uses the previous contains-text matching. Saving replaces it with the hostname rule shown here.")
            .performScrollTo().assertIsDisplayed()
        assertEquals(null, saved)
        compose.onNodeWithText("Save rule").performClick()
        compose.waitUntil(5_000) { saved != null }
        assertTrue(saved!!.includeSubdomains)
        assertFalse(saved!!.legacyContainsText)
    }
}
