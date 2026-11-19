package com.simon.harmonichackernews.ui.settings

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class SettingsSliderTransitionTest {
    @Test
    fun slidersSurviveInterruptedNavigation() = verify(reflow = false)

    @Test
    fun slidersSurviveMovingBetweenPaneLayoutsDuringNavigation() = verify(reflow = true)

    @Test
    fun slidersSurviveHostSceneRecreationDuringNavigation() = verify(reflow = true, recreateScene = true)

    // Material sliders query alignment lines during measure. Text-only navigation probes do
    // not exercise the LookaheadDelegate alignment-line path seen in the 3.2.5 crash report.
    private fun verify(reflow: Boolean, recreateScene: Boolean = false) = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("SettingsSliderTransitionTest")
        val owner = SettingsTestOwner()
        val navigation = SettingsNavigationStore(SettingsSection.CommentsSpacing)
        var twoPane by mutableStateOf(false)
        val repository = bootstrap.app.settings
        val scene = ImageComposeScene(1200, 1000, Density(1f)) {
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
                        isFoldable = false,
                        tabletPaneHorizontalPadding = 0.dp,
                        onBackFromSettings = {},
                        onSectionChanged = {},
                        supportsTwoPane = true,
                        sceneReflowKey = if (recreateScene) twoPane else Unit,
                        renderList = { _, _, _, _ -> Text("Settings") },
                        renderDetail = { section, _, onBack, _ ->
                            when (section) {
                                SettingsSection.CommentsSpacing -> CommentsSpacingSettingsRoute(repository, onBack)
                                SettingsSection.StoriesSpacing -> StoriesSpacingSettingsRoute(repository, bootstrap.app.articleCategorySettings, onBack)
                                else -> Text(section.route)
                            }
                        },
                    )
                }
            }
        }
        var time = 0L
        fun frames(count: Int = 45) = repeat(count) {
            scene.render(time.also { time += 16_666_667L }).close()
        }
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        fun visibleSlider() = scene.semanticsOwners
            .flatMap { descendants(it.rootSemanticsNode) }
            .single { it.config.getOrNull(SemanticsActions.SetProgress) != null }
        try {
            frames()
            assertTrue(visibleSlider().config[SemanticsActions.SetProgress].action!!.invoke(25f))
            frames()
            assertEquals(25, repository.snapshot().comments.spacing)

            // Interrupt both enter and exit, including moving the retained slider out of a
            // two-pane AnimatedContent/NavDisplay tree and back into the one-pane stack.
            repeat(4) {
                navigation.navigateTo(SettingsSection.StoriesSpacing, preserveCurrentDetail = true)
                frames(5)
                if (reflow) twoPane = !twoPane
                frames(5)
                assertTrue(navigation.navigateBack())
                frames(5)
                if (reflow) twoPane = !twoPane
                frames(5)
            }
            frames()
            assertEquals(SettingsSection.CommentsSpacing, navigation.state.value.selectedSection)
            assertEquals(25f, visibleSlider().config[SemanticsProperties.ProgressBarRangeInfo].current)
            assertTrue(visibleSlider().config[SemanticsActions.SetProgress].action!!.invoke(50f))
            frames()
            assertEquals(50, repository.snapshot().comments.spacing)
            assertEquals(50f, visibleSlider().config[SemanticsProperties.ProgressBarRangeInfo].current)
        } finally {
            scene.close()
            owner.viewModelStore.clear()
            bootstrap.close()
        }
    }
}
