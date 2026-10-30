package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.zIndex
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.PortableStoryListState
import com.simon.harmonichackernews.presentation.SavedItemStateReader
import com.simon.harmonichackernews.presentation.StoriesState
import com.simon.harmonichackernews.presentation.StoryDisplaySettings
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.ui.HarmonicUiDependencies
import com.simon.harmonichackernews.ui.LocalHarmonicUiDependencies
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import java.lang.reflect.Proxy
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoryPreviewDismissInputTest {
    @Test
    fun roundedCornerStartsAnimatedDismissal() = verifyDismissal(tapUpdate = false)

    @Test
    fun dimmedTapToUpdateDismissesWithoutRefreshingAndWorksNormallyAfterward() = verifyDismissal(tapUpdate = true)

    private fun verifyDismissal(tapUpdate: Boolean) = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("StoryPreviewDismissInputTest")
        var refreshes = 0
        val listener = Proxy.newProxyInstance(
            StoriesScreenController.Listener::class.java.classLoader,
            arrayOf(StoriesScreenController.Listener::class.java),
        ) { _, method, _ ->
            if (method.name == "onRefresh") refreshes++
            null
        } as StoriesScreenController.Listener
        val controller = StoriesScreenController.create(100, object : SavedItemStateReader {
            override fun isBookmarked(itemId: Int) = false
            override fun isFavorited(itemId: Int) = false
            override fun isUpvoted(itemId: Int, isComment: Boolean) = false
        }, listener)
        val settings = StoryDisplaySettings.from(bootstrap.app.userSettings.story)
        val stories = listOf(StoryListItemSnapshot(
            StorySnapshot(42, title = "A preview with rounded corners", author = "tester", score = 12),
            StoryPresentationSnapshot(loaded = true, isLink = false),
        ))
        controller.updateContent(StoriesState(
            mainList = PortableStoryListState(items = stories), displaySettings = settings,
            updateAvailable = true,
        ))
        controller.showStoryPreview(stories, listOf(0xffeeeeee.toInt()), 42)
        var cardBounds = Rect.Zero
        var updateBounds = Rect.Zero
        var scrim = 0f
        var previewContentCommits = 0
        val scene = ImageComposeScene(400, 800, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                    Box(Modifier.fillMaxSize()) {
                        StoryPreviewOverlay(controller, tablet = false, onScrimAlphaChanged = { scrim = it }) { story, page, color, modifier ->
                            checkNotNull(LocalStoryPreviewSharedTransition.current)
                            SideEffect { previewContentCommits++ }
                            StoryPreviewCard(
                                controller, story, page, color, settings, StoryPreviewSummaryState(),
                                hasAccount = false, bookmarksEnabled = true, faviconUrl = null,
                                textStyle = TextStyle.Default, htmlToPlainText = { it },
                                modifier = modifier.onGloballyPositioned { cardBounds = it.boundsInWindow() },
                            )
                        }
                        StoryTapToUpdateButton(
                            controller, rememberLazyListState(),
                            modifier = Modifier.zIndex(101f).onGloballyPositioned { updateBounds = it.boundsInWindow() },
                            modalScrimAlpha = if (controller.isStoryPreviewShowing()) scrim else 0f,
                            modalScrimActive = controller.isStoryPreviewShowing(),
                        )
                    }
                }
            }
        }
        var time = 0L
        fun frame() { scene.render(time.also { time += 16_000_000 }).close() }
        fun tap(point: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1, type = PointerType.Touch)
        }
        try {
            repeat(45) { frame() }
            assertTrue(cardBounds.width > 0f)
            assertTrue(updateBounds.width > 0f)
            assertEquals(0.32f, scrim, 0.001f)
            controller.startStoryPreviewPredictiveBack(0.1f, edge = 0, touchY = 200f)
            repeat(4) { frame() }
            val commitsBeforeGesture = previewContentCommits
            for (progress in listOf(0.2f, 0.4f, 0.6f, 0.8f)) {
                controller.updateStoryPreviewPredictiveBack(progress, edge = 0, touchY = 200f)
                repeat(2) { frame() }
            }
            assertEquals(commitsBeforeGesture, previewContentCommits,
                "Gesture frames must not invalidate retained preview content through its CompositionLocal")
            assertTrue(scrim < 0.2f, "The scrim must still follow gesture progress")
            controller.cancelStoryPreviewPredictiveBack()
            repeat(25) { frame() }
            assertEquals(0.32f, scrim, 0.001f)
            tap(if (tapUpdate) updateBounds.center else cardBounds.topLeft + Offset(1f, 1f))
            assertTrue(controller.storyPreviewDismissRequest != 0, "The outside tap must request dismissal")
            assertTrue(controller.isStoryPreviewShowing(), "Dismissal must retain the preview for its animation")
            assertEquals(0, refreshes, "A dimmed update button must not refresh the list")
            repeat(45) { frame() }
            assertFalse(controller.isStoryPreviewShowing(), "The exit animation must finish dismissal")
            if (tapUpdate) {
                tap(updateBounds.center)
                assertEquals(1, refreshes, "After dismissal the update button must refresh normally")
            }
        } finally {
            scene.close()
            bootstrap.close()
        }
    }
}
