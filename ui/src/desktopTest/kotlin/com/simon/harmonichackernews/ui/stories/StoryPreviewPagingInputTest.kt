package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
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
import kotlin.test.assertTrue

/** Real touch input through the overlay and card's nested vertical scroll, without a device. */
class StoryPreviewPagingInputTest {
    @Test
    fun slowDeliberateDragPagesOnPhoneAndPortraitTablet() {
        for (height in listOf(800, 1400)) {
            for (insideCard in listOf(true, false)) {
                for (direction in listOf(-1f, 1f)) {
                    verifySwipe(height, insideCard, distance = 120f * direction, framesPerStep = 4,
                        expectedId = if (direction < 0) 3 else 1)
                }
            }
        }
    }

    @Test
    fun shortSlowDragReturnsToCurrentStory() {
        for (insideCard in listOf(true, false)) {
            verifySwipe(1400, insideCard, distance = -80f, framesPerStep = 4, expectedId = 2)
        }
    }

    @Test
    fun shortFastFlickAdvancesOneStory() {
        for (insideCard in listOf(true, false)) {
            for (direction in listOf(-1f, 1f)) {
                verifySwipe(1400, insideCard, distance = 48f * direction, framesPerStep = 1,
                    steps = 6, expectedId = if (direction < 0) 3 else 1)
            }
        }
    }

    @Test
    fun resizingToTallWindowKeepsTheSameDragDistance() = verifySwipe(
        1400, insideCard = true, distance = -120f, framesPerStep = 4, expectedId = 3,
        initialHeight = 600,
    )

    @Test
    fun flickAtEndOfDeckStaysOnLastStory() = verifySwipe(
        1400, insideCard = true, distance = -48f, framesPerStep = 1, steps = 6,
        initialId = 4, expectedId = 4,
    )

    @Test
    fun scrollableCardUsesDragBeforePaging() = verifySwipe(
        300, insideCard = true, distance = -120f, framesPerStep = 4, expectedId = 2,
        storyText = "A long preview contains text to read before moving to the next story. ".repeat(20),
    )

    private fun verifySwipe(
        height: Int,
        insideCard: Boolean,
        distance: Float,
        framesPerStep: Int,
        expectedId: Int,
        steps: Int = 10,
        initialHeight: Int = height,
        initialId: Int = 2,
        storyText: String? = null,
    ) = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("StoryPreviewPagingInputTest")
        val listener = Proxy.newProxyInstance(
            StoriesScreenController.Listener::class.java.classLoader,
            arrayOf(StoriesScreenController.Listener::class.java),
        ) { _, _, _ -> null } as StoriesScreenController.Listener
        val controller = StoriesScreenController.create(100, object : SavedItemStateReader {
            override fun isBookmarked(itemId: Int) = false
            override fun isFavorited(itemId: Int) = false
            override fun isUpvoted(itemId: Int, isComment: Boolean) = false
        }, listener)
        val settings = StoryDisplaySettings.from(bootstrap.app.userSettings.story)
        val stories = (1..4).map { id ->
            StoryListItemSnapshot(
                StorySnapshot(id, title = "Paging story $id", author = "tester", score = 12, text = storyText),
                StoryPresentationSnapshot(loaded = true, isLink = false),
            )
        }
        controller.updateContent(StoriesState(
            mainList = PortableStoryListState(items = stories), displaySettings = settings,
        ))
        controller.showStoryPreview(stories, List(stories.size) { 0xffeeeeee.toInt() }, initialId)
        var cardBounds = Rect.Zero
        val viewportHeight = mutableStateOf(initialHeight)
        val scene = ImageComposeScene(if (height > 1000) 1024 else 400, height, Density(1f)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                    Box(Modifier.height(viewportHeight.value.dp)) {
                        StoryPreviewOverlay(controller, tablet = height > 1000) { story, page, color, modifier ->
                            StoryPreviewCard(
                                controller, story, page, color, settings, StoryPreviewSummaryState(),
                                hasAccount = false, bookmarksEnabled = true, faviconUrl = null,
                                textStyle = TextStyle.Default, htmlToPlainText = { it },
                                modifier = modifier.onGloballyPositioned {
                                    if (story.id == initialId) cardBounds = it.boundsInWindow()
                                },
                            )
                        }
                    }
                }
            }
        }
        var time = 0L
        fun frames(count: Int) = repeat(count) {
            scene.render(time.also { time += 16_000_000 }).close()
        }
        fun event(type: PointerEventType, point: Offset) = scene.sendPointerEvent(
            type, point, timeMillis = time / 1_000_000, type = PointerType.Touch,
        )
        try {
            frames(60)
            if (initialHeight != height) {
                viewportHeight.value = height
                frames(10)
            }
            assertTrue(cardBounds.height > 0f)
            val start = Offset(if (insideCard) cardBounds.center.x else 8f, cardBounds.center.y)
            event(PointerEventType.Press, start)
            for (step in 1..steps) {
                frames(framesPerStep)
                event(PointerEventType.Move, start + Offset(0f, distance * step / steps))
            }
            event(PointerEventType.Release, start + Offset(0f, distance))
            frames(120)
            assertEquals(expectedId, controller.visibleStoryPreviewId,
                "height=$height insideCard=$insideCard distance=$distance framesPerStep=$framesPerStep")
            assertEquals(0, controller.storyPreviewDismissRequest, "A swipe must not dismiss the preview")
        } finally {
            scene.close()
            bootstrap.close()
        }
    }
}
