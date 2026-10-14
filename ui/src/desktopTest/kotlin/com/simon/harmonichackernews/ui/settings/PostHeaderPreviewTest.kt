package com.simon.harmonichackernews.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.CommentsHeaderEdge
import com.simon.harmonichackernews.settings.CommentsHeaderTint
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import kotlinx.coroutines.flow.emptyFlow
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostHeaderPreviewTest {
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
