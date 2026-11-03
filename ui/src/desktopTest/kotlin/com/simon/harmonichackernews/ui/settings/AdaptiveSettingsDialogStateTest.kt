package com.simon.harmonichackernews.ui.settings

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.settings.TitleFilterRule
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class AdaptiveSettingsDialogStateTest {
    @Test
    fun sceneReflowKeepsTheProductionNestedEditorDraftAndRuleListScroll() = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("AdaptiveSettingsDialogStateTest")
        val owner = SettingsTestOwner()
        val navigation = SettingsNavigationStore(SettingsSection.FiltersTags, twoPane = true)
        var twoPane by mutableStateOf(true)
        var detailIdentity: Any? = null
        val originalRules = List(30) { TitleFilterRule("Rule $it") }
        bootstrap.app.contentFilters.setTitleRules(originalRules)
        val scene = ImageComposeScene(1200, 800, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(
                    LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene),
                    LocalLifecycleOwner provides owner,
                    LocalViewModelStoreOwner provides owner,
                ) {
                    SettingsNavigationShell(
                        navigation = navigation,
                        directive = PaneScaffoldDirective(if (twoPane) 2 else 1, 24.dp, 1, 0.dp, 360.dp, emptyList()),
                        isFoldable = false, tabletPaneHorizontalPadding = 0.dp,
                        onBackFromSettings = {}, onSectionChanged = {}, supportsTwoPane = true,
                        sceneReflowKey = twoPane,
                        renderList = { _, _, _, _ -> Text("Settings list") },
                        renderDetail = { _, singlePane, onBack, _ ->
                            val identity = remember { Any() }
                            SideEffect { detailIdentity = identity }
                            FiltersTagsSettingsRoute(
                                settings = bootstrap.app.settings,
                                filters = bootstrap.app.contentFilters,
                                userTags = bootstrap.app.userTags,
                                showNavigation = singlePane, onBack = onBack,
                                onManageFrontpages = {}, profileDialog = { _, _, _ -> },
                            )
                        },
                    )
                }
            }
        }
        var time = 0L
        fun frames(count: Int = 45) = repeat(count) { scene.render(time.also { time += 16_666_667L }).close() }
        fun nodes(unmerged: Boolean = true): List<SemanticsNode> {
            fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
            return scene.semanticsOwners.flatMap { descendants(if (unmerged) it.unmergedRootSemanticsNode else it.rootSemanticsNode) }
        }
        fun tagged(tag: String) = nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
        fun clickText(label: String) {
            val target = nodes(unmerged = false).single {
                it.config.getOrNull(SemanticsActions.OnClick) != null &&
                    it.config.getOrNull(SemanticsProperties.Text)?.any { entry -> entry.text == label } == true
            }
            assertTrue(assertNotNull(target.config[SemanticsActions.OnClick].action).invoke())
            frames()
        }
        fun editorText() = tagged("title-filter-rule-input").config[SemanticsProperties.EditableText].text
        fun listScroll() = tagged("filter-rules-list").config[SemanticsProperties.VerticalScrollAxisRange].value()
        try {
            frames()
            val originalDetail = assertNotNull(detailIdentity)
            clickText("Filter by story title")
            assertTrue(assertNotNull(tagged("filter-rules-list").config[SemanticsActions.ScrollBy].action).invoke(0f, 1600f))
            frames()
            val scroll = listScroll()
            assertTrue(scroll > 0f, "The retained list must start away from its first rule")
            val edit = nodes(unmerged = false).first {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.singleOrNull()?.startsWith("Edit Rule ") == true &&
                    it.boundsInWindow.height > 0f
            }
            assertTrue(assertNotNull(edit.config[SemanticsActions.OnClick].action).invoke())
            frames()
            val field = tagged("title-filter-rule-input")
            assertTrue(assertNotNull(field.config[SemanticsActions.SetText].action).invoke(AnnotatedString("AI research draft")))
            frames()
            clickText("Contains text")
            repeat(4) {
                twoPane = !twoPane
                frames()
                assertSame(originalDetail, detailIdentity, "Scene recreation must not recreate the destination")
                tagged("title-filter-rule-editor")
                assertEquals("AI research draft", editorText())
                assertEquals(scroll, listScroll(), .5f, "The obscured rule list must keep its reading position")
                val mode = nodes(unmerged = false).single {
                    it.config.getOrNull(SemanticsProperties.Selected) == true &&
                        it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Contains text" } == true
                }
                assertNotNull(mode)
                assertEquals(SettingsSection.FiltersTags, navigation.state.value.selectedSection)
            }
            clickText("Cancel")
            assertTrue(nodes().none { it.config.getOrNull(SemanticsProperties.TestTag) == "title-filter-rule-editor" })
            tagged("filter-rules-dialog")
            assertEquals(scroll, listScroll(), .5f)
            assertEquals(originalRules, bootstrap.app.contentFilters.titleRules(), "Cancelling must leave all persisted rules unchanged")
        } finally {
            scene.close()
            owner.viewModelStore.clear()
            bootstrap.close()
        }
    }
}
