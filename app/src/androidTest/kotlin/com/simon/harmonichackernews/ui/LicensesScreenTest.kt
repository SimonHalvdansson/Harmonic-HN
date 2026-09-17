package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
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

    @Test
    fun newLibrariesAndMissingLogoFallbackRenderAndOpenTheirProject() {
        val unknown = LicenseEntry("Future dependency", "Example", "MIT License", "https://example.com")
        val entries = CommonLicenseCatalog.complete(emptyList(), includeLocalAi = true) + unknown
        val opened = mutableListOf<String>()
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("dark", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                LicensesScreen(entries, onBack = {}, onOpenLicense = opened::add)
            }
        }
        for (name in listOf("Compose LaTeX", "KaTeX math fonts", unknown.name)) {
            val entry = entries.single { it.name == name }
            compose.onNodeWithContentDescription(
                "${entry.name}, ${entry.licenseType}, by ${entry.creator}. Open project page.",
            ).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(entry.url, opened.last()) }
        }
    }
}
