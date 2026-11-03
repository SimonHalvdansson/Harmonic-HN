package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.navigation.LocalPredictiveBackCompletion
import com.simon.harmonichackernews.ui.navigation.PredictiveBackCompletion
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class SettingsSearchTransitionTest {
    @Test fun cancelledBackRestoresSearchAndItsQuery() = verify(cancel = true)
    @Test fun completedBackReturnsToThePillWithoutAFlash() = verify(cancel = false)
    @Test fun cancelledBackRestoresSearchInTheListPane() = verify(cancel = true, twoPane = true)
    @Test fun completedBackReturnsToTheListPane() = verify(cancel = false, twoPane = true)
    @Test fun toolbarBackReturnsToTheUnscrolledPill() = verify(cancel = false, toolbarBack = true)
    @Test fun toolbarBackReturnsToAPartlyClippedPill() = verify(cancel = false, toolbarBack = true, scrolled = true)
    @Test fun gestureBackReturnsToAPartlyClippedPill() = verify(cancel = false, scrolled = true)

    private fun verify(cancel: Boolean, twoPane: Boolean = false, toolbarBack: Boolean = false, scrolled: Boolean = false) = SwingUtilities.invokeAndWait {
        val navigation = SettingsNavigationStore(twoPane = twoPane)
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dispatcher
        }
        val lifecycle = SettingsTestOwner()
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("SettingsSearchTransitionTest")
        val dependencies = HarmonicUiDependencies(bootstrap.app, bootstrap.scene)
        val completion = PredictiveBackCompletion()
        val scene = ImageComposeScene(if (twoPane) 1000 else 400, 800, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides lifecycle,
                    LocalViewModelStoreOwner provides lifecycle,
                    LocalHarmonicUiDependencies provides dependencies,
                    LocalNavigationEventDispatcherOwner provides owner,
                    LocalPredictiveBackCompletion provides completion,
                ) {
                    val back = rememberSettingsPredictiveBack(navigation, twoPane, true, {})
                    SettingsNavigationShell(
                        navigation = navigation,
                        directive = PaneScaffoldDirective(if (twoPane) 2 else 1, 16.dp, 1, 0.dp, 360.dp, emptyList()),
                        isFoldable = false,
                        tabletPaneHorizontalPadding = 0.dp,
                        onBackFromSettings = {},
                        onSectionChanged = {},
                        predictiveBackOverlay = back.overlay,
                        completedPredictiveBack = back.completed,
                        renderList = { _, _, _, onSelect ->
                            Box(Modifier.fillMaxSize().background(Color.Green).padding(start = 16.dp, top = 120.dp, end = 16.dp)) {
                                LazyColumn(state = rememberLazyListState(initialFirstVisibleItemScrollOffset = if (scrolled) 30 else 0)) {
                                    item { SettingsSearchButton { onSelect(SettingsSection.Search) } }
                                    items(20) { Box(Modifier.height(80.dp)) }
                                }
                            }
                        },
                        renderDetail = { _, _, _, _ -> },
                    )
                }
            }
        }
        var time = 0L
        fun frame(): Color = scene.render(time.also { time += 16_666_667L }).use {
            it.toComposeImageBitmap().toPixelMap()[200, 700]
        }
        fun frames(count: Int = 40) { repeat(count) { frame() } }
        fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        fun event(progress: Float) = NavigationEvent(
            touchX = progress * 200f, touchY = 400f, progress = progress,
            swipeEdge = NavigationEvent.EDGE_LEFT,
        )
        try {
            frames()
            val source = navigation.searchTransition.sourceBounds!!
            assertEquals(60f, source.height, .01f, "Keep the full pill geometry even when scrolling clips it")
            assertEquals(if (scrolled) 30f else 60f, navigation.searchTransition.sourceVisibleBounds!!.height, .01f)
            navigation.navigateTo(SettingsSection.Search)
            navigation.updateSearchQuery("font")
            // Early frames expose the list below the growing search surface.
            frames(3)
            assertEquals(Color.Green, frame())
            assertTrue(navigation.searchTransition.expansion() < 1f)
            frames()
            assertEquals(1f, navigation.searchTransition.expansion())
            if (toolbarBack) {
                val button = scene.semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }.single {
                    it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Back") &&
                        it.config.getOrNull(SemanticsActions.OnClick) != null
                }
                assertTrue(button.config[SemanticsActions.OnClick].action!!.invoke())
            } else {
                input.backStarted(event(0f))
                input.backProgressed(event(.6f))
                frames(3)
                assertTrue(navigation.searchTransition.expansion() in .3f.. .9f)
                assertEquals("font", navigation.state.value.searchQuery)
                if (cancel) input.backCancelled() else input.backCompleted()
            }
            frames()
            if (cancel) {
                assertEquals(listOf(SettingsSection.Search), navigation.state.value.detailStack)
                assertEquals("font", navigation.state.value.searchQuery)
                assertEquals(1f, navigation.searchTransition.expansion())
            } else {
                assertEquals(if (twoPane) listOf(SettingsSection.Appearance) else emptyList(), navigation.state.value.detailStack)
                assertEquals(source, navigation.searchTransition.sourceBounds, "Returning must preserve the list's scroll position")
                repeat(30) { assertEquals(Color.Green, frame(), "The search surface must stay dismissed") }
            }
        } finally {
            scene.close()
            dispatcher.dispose()
            lifecycle.viewModelStore.clear()
            bootstrap.close()
        }
    }
}
