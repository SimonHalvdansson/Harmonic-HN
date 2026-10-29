package com.simon.harmonichackernews.ui.settings

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class SettingsLayoutStateTest {
    @Test fun listAndNestedSectionsKeepTheirStateWhenMovingBetweenLayouts() = verify(false)
    @Test fun stateAlsoSurvivesLayoutChangesDuringAnUnfinishedTransition() = verify(true)

    private fun verify(interrupt: Boolean) = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("SettingsLayoutStateTest")
        val owner = SettingsTestOwner()
        val navigation = SettingsNavigationStore(SettingsSection.Appearance)
        var twoPane by mutableStateOf(false)
        var renderVersion by mutableStateOf(0)
        val values = mutableMapOf<String, MutableState<String>>()
        val identities = mutableMapOf<String, Any>()
        val models = mutableMapOf<String, ProbeModel>()
        val renderedInputs = mutableMapOf<String, String>()
        val scene = ImageComposeScene(1200, 800, Density(1f)) {
            // Capture a new value in each renderer, so reusing an old renderer cannot pass by
            // reading mutable state directly from inside its retained composition.
            val version = renderVersion
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(
                    LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene),
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner,
                ) {
                    SettingsNavigationShell(
                        navigation, PaneScaffoldDirective(if (twoPane) 2 else 1, 24.dp, 1, 0.dp, 360.dp, emptyList()),
                        false, 0.dp, {}, {}, supportsTwoPane = true,
                        renderList = { _, showSelection, _, _ ->
                            Probe("list", values, identities, models, "$version:$showSelection", renderedInputs)
                        },
                        renderDetail = { section, singlePane, _, _ ->
                            Probe(section.route, values, identities, models,
                                "$version:$singlePane:${LocalSettingsParentSection.current?.route}", renderedInputs)
                        },
                    )
                }
            }
        }
        var time = 0L
        fun frames(count: Int = 45) { repeat(count) { scene.render(time.also { time += 16_666_667 }).close() } }
        try {
            frames()
            val listIdentity = identities.getValue("list")
            val appearanceModel = models.getValue(SettingsSection.Appearance.route)
            values.getValue("list").value = "list selection"
            values.getValue(SettingsSection.Appearance.route).value = "appearance preview"
            navigation.navigateTo(SettingsSection.Theme, preserveCurrentDetail = true)
            frames(if (interrupt) 5 else 45)
            values.getValue(SettingsSection.Theme.route).value = "theme selection"
            val theme = identities.getValue(SettingsSection.Theme.route)
            val themeModel = models.getValue(SettingsSection.Theme.route)
            repeat(4) {
                renderVersion++
                twoPane = !twoPane
                frames(if (interrupt) 5 else 45)
                assertEquals("theme selection", values.getValue(SettingsSection.Theme.route).value)
                assertSame(theme, identities[SettingsSection.Theme.route])
                assertSame(themeModel, models[SettingsSection.Theme.route])
                assertFalse(themeModel.cleared)
                assertEquals("$renderVersion:${!twoPane}:${SettingsSection.Appearance.route}",
                    renderedInputs[SettingsSection.Theme.route], "Moved content must receive the current rendering inputs")
                assertEquals(listOf(SettingsSection.Appearance, SettingsSection.Theme), navigation.state.value.detailStack)
            }
            twoPane = true
            frames()
            assertSame(listIdentity, identities["list"])
            assertEquals("$renderVersion:true", renderedInputs["list"])
            assertEquals("list selection", values.getValue("list").value)
            assertTrue(navigation.navigateBack())
            frames()
            assertEquals("appearance preview", values.getValue(SettingsSection.Appearance.route).value)
            assertSame(appearanceModel, models[SettingsSection.Appearance.route])
            assertTrue(themeModel.cleared, "A popped section must release its ViewModel")
            twoPane = false
            frames()
            // The parent can leave composition while hidden in two panes; its saved state and
            // ViewModel still belong to the retained entry even if its remember identity changes.
            assertEquals("appearance preview", values.getValue(SettingsSection.Appearance.route).value)
            val restoredAppearance = identities.getValue(SettingsSection.Appearance.route)
            twoPane = true
            frames()
            assertSame(restoredAppearance, identities[SettingsSection.Appearance.route])
            assertFalse(appearanceModel.cleared)
            twoPane = false
            frames()
            assertTrue(navigation.navigateBack())
            frames()
            assertTrue(appearanceModel.cleared)
            navigation.navigateTo(SettingsSection.Appearance)
            frames()
            assertEquals("initial", values.getValue(SettingsSection.Appearance.route).value,
                "A fresh visit must not inherit a popped section's state")
        } finally {
            scene.close()
            owner.viewModelStore.clear()
            bootstrap.close()
        }
    }

    private class ProbeModel : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Composable
    private fun Probe(key: String, values: MutableMap<String, MutableState<String>>, identities: MutableMap<String, Any>, models: MutableMap<String, ProbeModel>, input: String, inputs: MutableMap<String, String>) {
        val value = rememberSaveable { mutableStateOf("initial") }
        val identity = remember { Any() }
        val model = viewModel { ProbeModel() }
        SideEffect { values[key] = value; identities[key] = identity; models[key] = model; inputs[key] = input }
        Text(value.value)
    }
}
