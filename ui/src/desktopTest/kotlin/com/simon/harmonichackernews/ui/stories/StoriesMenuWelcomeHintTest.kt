package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.PortableStoryListState
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.StoriesState
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.AppLaunchStateStore
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.common.harmonicFilterButtonColors
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.pageBackground
import java.io.File
import java.lang.reflect.Proxy
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoriesMenuWelcomeHintTest {
    @Test
    fun hintWaitsForStoriesAndFitsPhoneWideAndLargeTextLayouts() = SwingUtilities.invokeAndWait {
        for ((width, height, paneWidth, fontScale) in listOf(
            Window(400, 900, 400), Window(840, 900, 420), Window(740, 420, 370),
            Window(320, 800, 320, 1.6f),
        )) for (dark in listOf(false, true)) {
            val bootstrap = DesktopHarmonicAppBootstrap.inMemory("StoriesMenuWelcomeHintTest")
            val app = bootstrap.app
            val navigation = bootstrap.scene.navigation
            val listener = Proxy.newProxyInstance(
                StoriesScreenController.Listener::class.java.classLoader,
                arrayOf(StoriesScreenController.Listener::class.java),
            ) { _, _, _ -> null } as StoriesScreenController.Listener
            val controller = StoriesScreenController.create(100, object : SavedItemStateReader {
                override fun isBookmarked(itemId: Int) = false
                override fun isFavorited(itemId: Int) = false
                override fun isUpvoted(itemId: Int, isComment: Boolean) = false
            }, listener)
            controller.updateContent(StoriesState(
                displaySettings = StoryDisplaySettings.from(app.userSettings.story),
                mainList = PortableStoryListState(items = listOf(
                    "A new perspective on solving familiar problems",
                    "Building thoughtful tools for everyday use",
                    "What we learned from a year of experiments",
                ).mapIndexed { index, title ->
                    StoryListItemSnapshot(StorySnapshot(index + 1, title = title, score = 42,
                        url = "https://example.com/$index"), StoryPresentationSnapshot(loaded = true))
                }),
            ))
            val lifecycleOwner = object : LifecycleOwner {
                override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
            }
            val scene = ImageComposeScene(width, height, Density(1f, fontScale)) {
                val palette = HarmonicThemeCatalog.scheme("dynamic", dark)
                HarmonicTheme(palette.colorScheme, palette.dark) {
                    CompositionLocalProvider(
                        LocalLifecycleOwner provides lifecycleOwner,
                        LocalHarmonicUiDependencies provides HarmonicUiDependencies(app, bootstrap.scene),
                    ) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.pageBackground)) {
                            Box(Modifier.width(paneWidth.dp)) {
                                StoriesRoute(controller, tintStore = app.storyResourceTints,
                                    filterColors = harmonicFilterButtonColors(), pullToRefreshEnabled = false)
                            }
                        }
                    }
                }
            }
            try {
                var time = 0L
                fun render() { scene.render(time.also { time += 16_666_667L }).close() }
                fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
                fun allNodes() = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                fun hints() = allNodes().filter { it.config.getOrNull(SemanticsProperties.TestTag) == "stories-welcome-hint" }
                fun settle() { repeat(30) { render() } }
                fun waitForHint() {
                    val deadline = System.nanoTime() + 3_000_000_000L
                    while (hints().isEmpty() && System.nanoTime() < deadline) {
                        Thread.sleep(10)
                        render()
                    }
                    assertTrue(hints().isNotEmpty(), "The hint should appear on Stories after accepting Welcome")
                    settle()
                }
                settle()
                assertTrue(hints().isEmpty(), "Existing users must not see a hint on startup")
                navigation.openWelcome()
                app.launchState.completeWelcome()
                settle()
                assertTrue(hints().isEmpty(), "Retained Stories must not show a popup over Welcome")
                bootstrap.scene.closeWelcome()
                render()
                assertTrue(hints().isEmpty(), "Wait for the return animation before opening the hint")
                waitForHint()
                val hint = hints().single().boundsInWindow
                val menu = allNodes().first {
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("More options") == true
                }.boundsInWindow
                assertTrue(hint.width <= 320f + 1f && hint.left >= 16 && hint.right <= width - 16,
                    "The hint must fit the window ($width, $fontScale): $hint")
                assertTrue(hint.top >= menu.bottom - 1f && hint.bottom <= height,
                    "The hint must sit below the menu and remain visible: hint=$hint, menu=$menu")
                scene.render(time).use { image ->
                    val tip = image.toComposeImageBitmap().toPixelMap()[menu.center.x.toInt(), (hint.top - 2).toInt()]
                    val container = HarmonicThemeCatalog.scheme("dynamic", dark).colorScheme.surfaceContainer
                    assertTrue(abs(tip.red - container.red) + abs(tip.green - container.green) +
                        abs(tip.blue - container.blue) < .1f, "The caret must point at More options")
                }
                System.getenv("HARMONIC_HINT_CAPTURE_DIR")?.let { directory ->
                    scene.render(time).use { image -> image.encodeToData()?.use { data ->
                        File(directory).mkdirs()
                        File(directory, "stories-hint-${width}x$height-$fontScale-$dark.png").writeBytes(data.bytes)
                    } }
                }
                val dismissPoint = if (dark) Offset(width - 12f, height - 12f) else allNodes().single {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == "Got it" } == true
                }.boundsInWindow.center
                scene.sendPointerEvent(PointerEventType.Press, dismissPoint)
                scene.sendPointerEvent(PointerEventType.Release, dismissPoint)
                settle()
                assertFalse(app.launchState.storiesMenuHintPending.value)
                assertTrue(hints().isEmpty())
                assertFalse(AppLaunchStateStore(app.host.appDataStore).storiesMenuHintPending.value)
                scene.sendPointerEvent(PointerEventType.Press, menu.center)
                scene.sendPointerEvent(PointerEventType.Release, menu.center)
                settle()
                for (label in listOf("Log in", "Settings")) {
                    assertTrue(allNodes().any { node ->
                        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true
                    }, "The menu must still offer $label after the hint is dismissed")
                }
                navigation.openWelcome(styleChooser = true)
                bootstrap.scene.closeWelcome()
                settle()
                assertTrue(hints().isEmpty(), "Style picker visits must not repeat the hint")
            } finally {
                scene.close()
                bootstrap.close()
            }
        }
    }

    private data class Window(val width: Int, val height: Int, val paneWidth: Int, val fontScale: Float = 1f)
}
