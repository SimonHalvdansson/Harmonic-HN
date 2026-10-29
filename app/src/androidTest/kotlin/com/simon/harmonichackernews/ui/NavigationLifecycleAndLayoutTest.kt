@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.HarmonicApplication
import com.simon.harmonichackernews.ui.navigation.ActivityBackPreview
import com.simon.harmonichackernews.ui.navigation.ActivityNavigationStack
import com.simon.harmonichackernews.ui.settings.SettingsNavigationShell
import com.simon.harmonichackernews.ui.settings.SettingsNavigationStore
import com.simon.harmonichackernews.ui.settings.SettingsSection
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationLifecycleAndLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun retainedDestinationStopsDuringCoverAndPredictiveBackDoesNotRecreateIt() {
        var entries by mutableStateOf(emptyList<String>())
        var preview by mutableStateOf<ActivityBackPreview<String>?>(null)
        val owners = mutableMapOf<String, LifecycleOwner>()
        val identities = mutableMapOf<String, Any>()
        compose.setContent {
            Theme {
                ActivityNavigationStack(entries, { it }, preview = preview,
                    root = { LifecycleProbe("root", owners, identities) },
                    content = { LifecycleProbe(it, owners, identities) })
            }
        }
        compose.waitForIdle()
        val root = identities.getValue("root")
        compose.runOnIdle { entries = listOf("first", "second") }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(Lifecycle.State.CREATED, owners.getValue("root").lifecycle.currentState)
            assertEquals(Lifecycle.State.CREATED, owners.getValue("first").lifecycle.currentState)
            assertEquals(Lifecycle.State.RESUMED, owners.getValue("second").lifecycle.currentState)
            preview = ActivityBackPreview("second", "first", Modifier, Modifier)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(Lifecycle.State.STARTED, owners.getValue("first").lifecycle.currentState)
            assertEquals(Lifecycle.State.STARTED, owners.getValue("second").lifecycle.currentState)
            preview = null
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED, owners.getValue("second").lifecycle.currentState)
            entries = listOf("first")
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(Lifecycle.State.DESTROYED, owners.getValue("second").lifecycle.currentState)
            assertEquals(Lifecycle.State.RESUMED, owners.getValue("first").lifecycle.currentState)
            entries = emptyList()
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(Lifecycle.State.RESUMED, owners.getValue("root").lifecycle.currentState)
            assertSame(root, identities["root"])
        }
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test fun settingsStateSurvivesRepeatedPaneChangesNestedBackAndSavedStateRestoration() {
        val app = (compose.activity.application as HarmonicApplication).composition
        val scene = app.createScene()
        val dependencies = HarmonicUiDependencies(app, scene)
        val navigation = SettingsNavigationStore(SettingsSection.Appearance)
        var twoPane by mutableStateOf(false)
        val values = mutableMapOf<SettingsSection, MutableState<String>>()
        val identities = mutableMapOf<SettingsSection, Any>()
        val nativeCreations = mutableMapOf<SettingsSection, Int>()
        val restoration = StateRestorationTester(compose)
        try {
            restoration.setContent {
                Theme {
                    CompositionLocalProvider(LocalHarmonicUiDependencies provides dependencies) {
                        SettingsNavigationShell(
                            navigation, PaneScaffoldDirective(if (twoPane) 2 else 1, 16.dp, 1, 0.dp, 120.dp, emptyList()),
                            false, 0.dp, {}, {}, supportsTwoPane = true,
                            renderList = { _, _, _, _ -> Text("Settings list") },
                            renderDetail = { section, _, _, _ ->
                                val value = rememberSaveable { mutableStateOf("initial") }
                                val identity = remember { Any() }
                                SideEffect { values[section] = value; identities[section] = identity }
                                Column {
                                    Text(value.value, Modifier.testTag(section.route))
                                    AndroidView(factory = { context ->
                                        nativeCreations[section] = (nativeCreations[section] ?: 0) + 1
                                        android.widget.TextView(context).apply { text = "Retained native preview" }
                                    })
                                }
                            },
                        )
                    }
                }
            }
            compose.waitForIdle()
            compose.runOnIdle {
                values.getValue(SettingsSection.Appearance).value = "parent preview"
                navigation.navigateTo(SettingsSection.Theme, preserveCurrentDetail = true)
            }
            compose.waitForIdle()
            compose.runOnIdle { values.getValue(SettingsSection.Theme).value = "child selection" }
            val child = identities.getValue(SettingsSection.Theme)
            repeat(6) {
                compose.runOnIdle { twoPane = !twoPane }
                compose.waitForIdle()
                compose.onNodeWithTag(SettingsSection.Theme.route).assertTextEquals("child selection")
                compose.runOnIdle {
                    assertSame(child, identities[SettingsSection.Theme])
                    assertEquals("Moving a section must preserve its native view", 1, nativeCreations[SettingsSection.Theme])
                }
            }
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag(SettingsSection.Theme.route).assertTextEquals("child selection")
            compose.runOnIdle { navigation.navigateBack() }
            compose.waitForIdle()
            compose.onNodeWithTag(SettingsSection.Appearance.route).assertTextEquals("parent preview")
        } finally { scene.close() }
    }

    @Composable private fun LifecycleProbe(key: String, owners: MutableMap<String, LifecycleOwner>, identities: MutableMap<String, Any>) {
        val owner = LocalLifecycleOwner.current
        val identity = remember { Any() }
        SideEffect { owners[key] = owner; identities[key] = identity }
        Text(key)
    }

    @Composable private fun Theme(content: @Composable () -> Unit) {
        val palette = HarmonicThemeCatalog.resolve("material_light", false)
        HarmonicTheme(palette.colorScheme, palette.dark, content = content)
    }
}
