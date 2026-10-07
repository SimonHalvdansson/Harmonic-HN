package com.simon.harmonichackernews

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simon.harmonichackernews.settings.ThemeSelection
import com.simon.harmonichackernews.ui.theme.harmonicThemePalette
import com.simon.harmonichackernews.utils.AndroidActivityTheme
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidThemeConsistencyTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun darkSelectionMustNotReadLightHostAttributes() = checkHostMismatch(dark = true)

    @Test
    fun lightSelectionMustNotReadDarkHostAttributes() = checkHostMismatch(dark = false)

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 31)
    fun dynamicUsesEverySystemRoleInBothAppearances() {
        for (dark in listOf(false, true)) {
            val actual = harmonicThemePalette(context, ThemeSelection.forScheme("dynamic", dark)).colorScheme
            val expected = if (dark) androidx.compose.material3.dynamicDarkColorScheme(context)
                else androidx.compose.material3.dynamicLightColorScheme(context)
            assertEquals(expected.toString(), actual.toString())
        }
    }

    private fun checkHostMismatch(dark: Boolean) {
        val selection = ThemeSelection.forScheme("dynamic", dark)
        val staleSelection = ThemeSelection.forScheme("dynamic", !dark)
        val expectedContext = ContextThemeWrapper(
            context,
            AndroidActivityTheme.themeResource(selection),
        )
        val staleContext = ContextThemeWrapper(
            context,
            AndroidActivityTheme.themeResource(staleSelection),
        )
        val expected = harmonicThemePalette(expectedContext, selection)
        val actual = harmonicThemePalette(staleContext, selection)
        assertEquals(
            "Palette must follow its selection even when the host theme is stale",
            expected.colorScheme.toString(),
            actual.colorScheme.toString(),
        )
    }
}
