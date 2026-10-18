package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentsHeaderEdge
import com.simon.harmonichackernews.settings.CommentsHeaderTint
import com.simon.harmonichackernews.settings.CommentsHeaderMetaInfo
import com.simon.harmonichackernews.settings.CommentsHeaderLayout
import com.simon.harmonichackernews.settings.PreviewTintPolicy
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import com.simon.harmonichackernews.ui.theme.cardBackground
import com.simon.harmonichackernews.ui.theme.pageBackground
import com.simon.harmonichackernews.ui.comments.CommentsHeader
import com.simon.harmonichackernews.ui.comments.CommentsPreviewPlatform
import com.simon.harmonichackernews.ui.comments.CommentsScreenController
import com.simon.harmonichackernews.ui.comments.CommentsUpButton
import kotlinx.coroutines.flow.emptyFlow
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class PostHeaderPreviewTest {
    @Test fun largePreviewSampleChangesAnimateAcrossThePinningThreshold() = SwingUtilities.invokeAndWait {
        val sample = mutableStateOf(HeaderPreviewSample.LINK_PREVIEW)
        val settings = CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, true, false,
        ).copy(headerLayout = CommentsHeaderLayout.STANDARD, showHeaderPreviewImage = true,
            headerMetaInfo = CommentsHeaderMetaInfo.BUTTONS)
        var previewBounds = Rect.Zero
        val scene = ImageComposeScene(420, 900, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsPage("Post header", true, {}, showTopAppBar = false,
                    contentVersion = sample.value.ordinal,
                    pinnedContent = {
                        Box(Modifier.onGloballyPositioned { previewBounds = it.boundsInRoot() }) {
                            HeaderDisplayPreview(settings, true, {}, sample.value)
                        }
                    },
                ) { items(20) { Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Magenta)) } }
            }
        }
        try {
            var time = 0L
            fun frame(): Float {
                scene.render(time.also { time += 16_666_667L }).use { image ->
                    val pixels = image.toComposeImageBitmap().toPixelMap()
                    for (x in 16 until 404 step 8) {
                        assertEquals(Color.Magenta.toArgb(), pixels[x, previewBounds.bottom.toInt() + 4].toArgb(),
                            "The outgoing sample must not paint over the settings as the preview shrinks")
                    }
                }
                return previewBounds.height
            }
            repeat(40) { frame() }
            val tall = frame()
            assertTrue(tall > 900f * 0.7f, "Preview 4 must exceed the pinning threshold")
            sample.value = HeaderPreviewSample.TEXT_POST
            val shrinking = List(40) { frame() }
            val short = shrinking.last()
            assertTrue(short < 900f * 0.7f, "Preview 3 must fit beneath the pinning threshold")
            sample.value = HeaderPreviewSample.LINK_PREVIEW
            val growing = List(40) { frame() }
            assertEquals(tall, growing.last())
            for (heights in listOf(listOf(tall) + shrinking, listOf(short) + growing)) {
                assertTrue(heights.count { it > short + 1f && it < tall - 1f } >= 5,
                    "Both directions must interpolate height instead of recreating the preview: $heights")
                assertTrue(heights.zipWithNext().all { (a, b) -> kotlin.math.abs(a - b) < (tall - short) / 3f },
                    "Crossing the pinning threshold must not jump to the final height: $heights")
            }
        } finally { scene.close() }
    }

    @Test fun statusBarProtectionStaysAtTheTopAndTracksHeaderCoverage() = SwingUtilities.invokeAndWait {
        val listState = LazyListState()
        var protectionColor: Color? = null
        var protectionBounds = Rect.Zero
        val palette = HarmonicThemeCatalog.resolve("material_light", false)
        val background = palette.colorScheme.pageBackground
        val headerColor = Color(0xFF84BCA5)
        val scene = ImageComposeScene(360, 900, Density(1f)) {
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsPage("Post header", true, {}, showTopAppBar = false, listState = listState,
                    pinnedContent = { Box(Modifier.fillMaxWidth().height(700.dp).background(headerColor)) },
                    topOverlay = {
                        HeaderSettingsStatusBarProtection(listState, headerColor, 24.dp) { color, height ->
                            SideEffect { protectionColor = color }
                            DisposableEffect(Unit) { onDispose { protectionColor = null } }
                            Box(Modifier.fillMaxWidth().height(height).background(color)
                                .onGloballyPositioned { protectionBounds = it.boundsInRoot() })
                        }
                    },
                ) {
                    items(20) { Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Magenta)) }
                }
            }
        }
        try {
            var time = 0L
            fun settle() { repeat(20) { scene.render(time.also { time += 16_666_667L }).close() } }
            settle()
            assertNull(protectionColor, "The unscrolled header already fills the status bar")
            listState.dispatchRawDelta(20f)
            settle()
            assertEquals(headerColor, protectionColor)
            assertEquals(Rect(0f, 0f, 360f, 24f), protectionBounds)
            listState.dispatchRawDelta(668f)
            settle()
            assertEquals(lerp(background, headerColor, 0.5f), protectionColor)
            listState.dispatchRawDelta(40f)
            settle()
            assertEquals(background, protectionColor)
            assertEquals(Rect(0f, 0f, 360f, 24f), protectionBounds,
                "Protection must stay above the list after the preview scrolls away")
            scene.render(time).use { image ->
                assertEquals(background.toArgb(), image.toComposeImageBitmap().toPixelMap()[180, 4].toArgb())
            }
            listState.dispatchRawDelta(-728f)
            settle()
            assertNull(protectionColor)
        } finally { scene.close() }
    }

    @Test fun headerLayoutAnimationsNeverPaintOverTheSettingsBelow() = SwingUtilities.invokeAndWait {
        val initial = CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, true, false,
        ).copy(headerMetaInfo = CommentsHeaderMetaInfo.ON_IMAGE, showHeaderPreviewImage = true)
        val settings = mutableStateOf(initial)
        var previewBounds = Rect.Zero
        val scene = ImageComposeScene(360, 1000, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsPage("Post header", true, {}, showTopAppBar = false,
                    pinnedContentBackground = Color.Transparent,
                    pinnedContent = {
                        Box(Modifier.onGloballyPositioned { previewBounds = it.boundsInRoot() }) {
                            HeaderDisplayPreview(settings.value, true, {})
                        }
                    },
                ) { items(20) { Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Magenta)) } }
            }
        }
        try {
            var time = 0L
            fun frame() = scene.render(time.also { time += 16_666_667L }).use { image ->
                val pixels = image.toComposeImageBitmap().toPixelMap()
                val belowHeader = previewBounds.bottom.toInt() + 4
                for (x in 16 until 344 step 8) {
                    assertEquals(Color.Magenta.toArgb(), pixels[x, belowHeader].toArgb(),
                        "Retained header content must not draw over settings during a transition")
                }
            }
            fun settle() { repeat(30) { frame() } }
            val layouts = listOf(
                initial,
                initial.copy(headerLayout = CommentsHeaderLayout.BALANCED),
                initial.copy(showHeaderPreviewImage = false),
            )
            for (from in layouts) for (to in layouts) {
                if (from == to) continue
                settings.value = from
                settle()
                settings.value = to
                settle()
            }
            for (layout in layouts) for (from in CommentsHeaderMetaInfo.entries) for (to in CommentsHeaderMetaInfo.entries) {
                if (from == to) continue
                settings.value = layout.copy(headerMetaInfo = from)
                settle()
                settings.value = layout.copy(headerMetaInfo = to)
                settle()
            }
            // Reverse a transition before it completes, as with quick repeated taps.
            repeat(12) {
                settings.value = layouts[it % layouts.size].copy(headerMetaInfo = CommentsHeaderMetaInfo.entries[it % 3])
                repeat(3) { frame() }
            }
            settle()
        } finally { scene.close() }
    }

    @Test fun smallImageTitleClearsBackButtonWithOrWithoutBrowserSheetHandle() = SwingUtilities.invokeAndWait {
        val settings = CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, false, false,
        ).copy(headerLayout = CommentsHeaderLayout.BALANCED, showUpButton = true, showFavicons = false)
        val title = "A long post title that wraps onto several lines in a small-image header and must " +
            "always stay below the back button when the post is scrolled all the way to the top"
        var expectedTitleTop: Float? = null
        for ((integratedWebView, showSheetControls) in listOf(false to false, true to true, true to false)) {
            val actions = HeaderPreviewActions()
            val controller = CommentsScreenController.create(
                shouldSmoothScroll = { false },
                story = StoryListItemSnapshot(
                    StorySnapshot(id = -1, title = title, author = "willow", url = "https://science.org"),
                    StoryPresentationSnapshot(loaded = true, isLink = true),
                ),
                initialThreadCached = true, showWebsite = false, accountUser = null,
                savedItemState = actions, listener = actions,
            ).also {
                it.updateContent(it.screenState.copy(
                    integratedWebView = integratedWebView, showSheetControls = showSheetControls,
                ))
            }
            var buttonBounds = Rect.Zero
            val scene = ImageComposeScene(360, 1000, Density(1f)) {
                val theme = HarmonicThemeCatalog.resolve("material_light", false)
                HarmonicTheme(theme.colorScheme, theme.dark) {
                    Box {
                        CommentsHeader(
                            controller = controller, settings = settings, contentVersion = 0, storyPosterTag = "",
                            tintBaseColor = theme.colorScheme.cardBackground.toArgb(), initialTint = null,
                            headerTopPadding = 24.dp, bookmarksEnabled = true, lastRefreshedText = null,
                            textStyle = TextStyle.Default,
                            previewPlatform = CommentsPreviewPlatform(TextStyle.Default, {}, {}, {}, { it }),
                            headerPreviewImageDisplayed = true,
                            headerPreviewImage = { modifier, _, _ -> Box(modifier) },
                        )
                        Box(Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 4.dp)) {
                            CommentsUpButton({}, Modifier.onGloballyPositioned { buttonBounds = it.boundsInRoot() })
                        }
                    }
                }
            }
            try {
                scene.render(0L).close()
                fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
                val titleTop = scene.semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }.filter {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text == title } == true
                }.maxOf { it.boundsInRoot.top }
                assertTrue(buttonBounds.height >= 48f)
                assertTrue(titleTop >= buttonBounds.bottom + 5f,
                    "Small-image title ($titleTop) must clear the back button ($buttonBounds), " +
                        "browser=$integratedWebView, controls=$showSheetControls")
                expectedTitleTop?.let { assertEquals(it, titleTop, "The sheet handle must not change title clearance") }
                expectedTitleTop = titleTop
            } finally { scene.close() }
        }
    }

    @Test fun imageTintedDetailButtonsHaveTheirFinalColorOnTheFirstFrame() = SwingUtilities.invokeAndWait {
        val settings = CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, true, false,
        ).copy(headerMetaInfo = CommentsHeaderMetaInfo.BUTTONS, headerTint = CommentsHeaderTint.IMAGE,
            tintHeader = true)
        for (dark in listOf(false, true)) {
            val theme = HarmonicThemeCatalog.resolve(if (dark) "material_dark" else "material_light", dark)
            for (sample in listOf(HeaderPreviewSample.LINK_WITH_IMAGE, HeaderPreviewSample.LINK_PREVIEW,
                HeaderPreviewSample.TEXT_POST)) {
                val rawTint = PreviewTintPolicy.calculateCardTint(
                    theme.colorScheme.cardBackground.toArgb(), sample.tintPalette, settings.paletteTintMode,
                )
                val headerColor = Color(PreviewTintPolicy.ensureCardTintContrast(
                    rawTint, theme.colorScheme.pageBackground.toArgb(), settings.paletteTintMode,
                ))
                val buttonColor = lerp(headerColor, theme.colorScheme.onSurface, 0.07f).toArgb()
                val scene = ImageComposeScene(600, 900, Density(1f)) {
                    HarmonicTheme(theme.colorScheme, theme.dark) {
                        HeaderDisplayPreview(settings, true, {}, sample)
                    }
                }
                try {
                    fun tintedPixels(time: Long) = scene.render(time).use { image ->
                        val pixels = image.toComposeImageBitmap().toPixelMap()
                        (0 until pixels.height).sumOf { y ->
                            (0 until pixels.width).count { x -> pixels[x, y].toArgb() == buttonColor }
                        }
                    }
                    val first = tintedPixels(0L)
                    repeat(60) { scene.render((it + 1) * 16_666_667L).close() }
                    val settled = tintedPixels(61 * 16_666_667L)
                    assertTrue(settled > 5_000, "Large detail buttons must use the image tint: $sample, dark=$dark")
                    assertEquals(settled, first, "Button tint must be ready on the first frame: $sample, dark=$dark")
                } finally { scene.close() }
            }
        }
    }

    @Test fun askHnPreviewNeverShowsSiteInfo() = SwingUtilities.invokeAndWait {
        val settings = mutableStateOf(CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, false, false,
        ).copy(showFavicons = true, headerTint = CommentsHeaderTint.OFF, tintHeader = false))
        val scene = ImageComposeScene(600, 900, Density(1f)) {
            val theme = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(theme.colorScheme, theme.dark) {
                HeaderDisplayPreview(settings.value, true, {}, HeaderPreviewSample.TEXT_POST)
            }
        }
        try {
            var time = 0L
            fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
            for (showFavicons in listOf(true, false)) {
                settings.value = settings.value.copy(showFavicons = showFavicons)
                repeat(60) { scene.render(time.also { time += 16_666_667L }).close() }
                val allNodes = scene.semanticsOwners.flatMap { nodes(it.rootSemanticsNode) }
                assertTrue(allNodes.none {
                    it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Site icon") == true
                }, "Ask HN must not display a favicon")
                assertTrue(allNodes.none {
                    it.config.getOrNull(SemanticsProperties.Text)?.any { text ->
                        "ycombinator" in text.text
                    } == true
                }, "Ask HN must not display a site domain")
            }
        } finally { scene.close() }
    }

    @Test fun roundedShadowTransitionNeverFlashesBeyondTheSettledShadow() = SwingUtilities.invokeAndWait {
        val settings = mutableStateOf(CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, false, false,
        ).copy(headerEdge = CommentsHeaderEdge.ROUNDED, headerTint = CommentsHeaderTint.THEME))
        var previewBounds = Rect.Zero
        val scene = ImageComposeScene(600, 900, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                Box(Modifier.onGloballyPositioned { previewBounds = it.boundsInRoot() }) {
                    HeaderDisplayPreview(settings.value, true, {}, HeaderPreviewSample.LINK_WITHOUT_IMAGE)
                }
            }
        }
        try {
            var time = 0L
            fun frame() = scene.render(time.also { time += 16_666_667L }).use { image ->
                val pixels = image.toComposeImageBitmap().toPixelMap()
                // Both modes reserve the same 16dp edge below the header surface.
                val surfaceBottom = previewBounds.bottom.toInt() - 16
                (surfaceBottom until surfaceBottom + 16).sumOf { pixels[300, it].alpha.toDouble() }
            }
            repeat(60) { frame() }
            val rounded = frame()
            settings.value = settings.value.copy(headerEdge = CommentsHeaderEdge.SHADOW)
            val entering = List(30) { frame() }
            val shadow = frame()
            assertTrue(shadow > rounded, "Shadow mode must actually draw a shadow")
            settings.value = settings.value.copy(headerEdge = CommentsHeaderEdge.ROUNDED)
            val leaving = List(30) { frame() }
            assertTrue((entering + leaving).all { it <= shadow + 0.02 },
                "Changing the outline must never flash a darker shadow than the final mode")
            assertEquals(rounded, frame(), 0.02, "Returning to Rounded must remove the shadow completely")
        } finally { scene.close() }
    }

    @Test fun fadeRevealsTheContentBehindThePinnedPreview() = SwingUtilities.invokeAndWait {
        val settings = CommentDisplaySettings.from(
            AppSettingsRepository(InMemoryKeyValueStore(), emptyFlow()).snapshot().comments,
            false, false, false, false,
        ).copy(headerEdge = CommentsHeaderEdge.FADE, headerTint = CommentsHeaderTint.THEME)
        val listState = LazyListState()
        var previewBounds = Rect.Zero
        val scene = ImageComposeScene(600, 900, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsPage("Post header", true, {}, showTopAppBar = false, listState = listState,
                    pinnedContentBackground = Color.Transparent,
                    pinnedContent = {
                        Box(Modifier.onGloballyPositioned { previewBounds = it.boundsInRoot() }) {
                            HeaderDisplayPreview(settings, true, {}, HeaderPreviewSample.LINK_WITHOUT_IMAGE)
                        }
                    },
                ) {
                    items(20) { Box(Modifier.fillMaxWidth().height(100.dp).background(Color.Magenta)) }
                }
            }
        }
        try {
            var time = 0L
            fun settle() = repeat(60) { scene.render(time.also { time += 16_666_667L }).close() }
            settle()
            listState.dispatchRawDelta(100f)
            settle()
            scene.render(time).use { image ->
                val pixels = image.toComposeImageBitmap().toPixelMap()
                val bottom = previewBounds.bottom.toInt()
                val top = pixels[300, bottom - 32]
                val middle = pixels[300, bottom - 16]
                val end = pixels[300, bottom - 1]
                assertTrue(top.green > middle.green && middle.green > end.green,
                    "The underlying magenta row must become progressively visible through the fade")
                assertTrue(end.green < 0.05f && end.red > 0.95f && end.blue > 0.95f,
                    "The fade must end in transparency, without an opaque page-colored strip")
            }
        } finally { scene.close() }
    }

    @Test fun floatingArrowsStayFixedAndWrapInBothDirections() = SwingUtilities.invokeAndWait {
        val sample = mutableStateOf(HeaderPreviewSample.LINK_WITH_IMAGE)
        val listState = LazyListState()
        var controls = Rect.Zero
        val scene = ImageComposeScene(600, 900, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsPage("Post header", true, {}, showTopAppBar = false, listState = listState,
                    floatingContent = {
                        Box(Modifier.onGloballyPositioned { controls = it.boundsInRoot() }) {
                            HeaderPreviewSampleSelector(sample.value) { sample.value = it }
                        }
                    },
                ) {
                    items(20) { Box(Modifier.fillMaxWidth().height(100.dp)) }
                }
            }
        }
        try {
            var time = 0L
            fun settle() = repeat(10) { scene.render(time.also { time += 16_666_667L }).close() }
            fun click(position: Offset) {
                scene.sendPointerEvent(PointerEventType.Press, position)
                scene.sendPointerEvent(PointerEventType.Release, position)
                settle()
            }
            settle()
            val initialBounds = controls
            assertEquals(112f, controls.width)
            assertEquals(56f, controls.height)
            listState.dispatchRawDelta(500f)
            settle()
            assertEquals(initialBounds, controls, "Scrolling must not move the floating controls")
            click(Offset(controls.left + 28f, controls.center.y))
            assertEquals(HeaderPreviewSample.LINK_PREVIEW, sample.value)
            click(Offset(controls.right - 28f, controls.center.y))
            assertEquals(HeaderPreviewSample.LINK_WITH_IMAGE, sample.value)
            click(Offset(controls.right - 28f, controls.center.y))
            assertEquals(HeaderPreviewSample.LINK_WITHOUT_IMAGE, sample.value)
        } finally { scene.close() }
    }
}
