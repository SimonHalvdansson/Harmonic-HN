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

    private fun checkHostMismatch(dark: Boolean) {
        val selection = ThemeSelection(if (dark) "material_dark" else "material_light", dark)
        val staleSelection = ThemeSelection(if (dark) "material_light" else "material_dark", !dark)
        val expectedContext = ContextThemeWrapper(
            context,
            AndroidActivityTheme.themeResource(selection.theme, selection.dark),
        )
        val staleContext = ContextThemeWrapper(
            context,
            AndroidActivityTheme.themeResource(staleSelection.theme, staleSelection.dark),
        )
        val expected = harmonicThemePalette(expectedContext, selection)
        val actual = harmonicThemePalette(staleContext, selection)
        assertEquals(
            "Palette must follow its selection even when the host theme is stale",
            expected.colors,
            actual.colors,
        )
    }
}
