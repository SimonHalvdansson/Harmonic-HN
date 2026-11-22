package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.ui.settings.SettingsNavigationShell
import com.simon.harmonichackernews.ui.settings.SettingsNavigationStore
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.app.CommonLicenseCatalog
import com.simon.harmonichackernews.app.LicenseEntry
import com.simon.harmonichackernews.ui.licenses.LicensesScreen
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LicensesScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun rowsOpenLocalNoticesBeforeTheirProjectWebsites() {
        val unknown = LicenseEntry.apache2("Future dependency", "Example", "https://example.com")
        val entries = CommonLicenseCatalog.complete(emptyList(), includeLocalAi = true) + unknown
        val opened = mutableListOf<String>()
        val app = (compose.activity.application as HarmonicApplication).composition
        val navigation = SettingsNavigationStore(SettingsSection.Licenses)
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        compose.setContent {
            DisposableEffect(scene) { onDispose { scene.close() } }
            val palette = HarmonicThemeCatalog.resolve("dark", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                    SettingsNavigationShell(
                        navigation, PaneScaffoldDirective(1, 0.dp, 1, 0.dp, 360.dp, emptyList()),
                        false, 0.dp, {}, {},
                        renderList = { _, _, _, _ -> },
                        renderDetail = { section, _, back, navigate ->
                            LicensesScreen(
                                entries, showNotice = section == SettingsSection.LicenseNotice,
                                onShowNotice = { navigate(SettingsSection.LicenseNotice, true) },
                                onBack = back, onOpenLicense = opened::add,
                            )
                        },
                    )
                }
            }
        }
        for ((name, notice) in mapOf(
            "Compose LaTeX" to "Copyright (c) 2026 huarangmeng",
            "KaTeX math fonts" to "Copyright (c) 2018 Khan Academy",
            unknown.name to "END OF TERMS AND CONDITIONS",
        )) {
            val previousOpenCount = opened.size
            val entry = entries.single { it.name == name }
            compose.onNodeWithContentDescription(
                "${entry.name}, ${entry.licenseType}, by ${entry.creator}. View license details.",
            ).performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText(entry.name).assertIsDisplayed()
            compose.waitUntil(5_000) {
                compose.onAllNodes(hasText(notice, substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { assertEquals(previousOpenCount, opened.size) }
            compose.onNodeWithText("Project website").performClick()
            compose.runOnIdle { assertEquals(entry.url, opened.last()) }
            compose.onNodeWithContentDescription("Back to licenses").performClick()
        }
    }
}
