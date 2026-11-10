package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.*
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
import com.simon.harmonichackernews.ui.content.*
import com.simon.harmonichackernews.settings.StoryPreviewMode
import java.io.File
import org.jetbrains.skia.EncodedImageFormat
import java.lang.reflect.Proxy
import javax.swing.SwingUtilities
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryPreviewDismissInputTest {
    @Test
    fun roundedCornerStartsAnimatedDismissal() = verifyDismissal(tapUpdate = false)

    @Test
    fun dimmedTapToUpdateDismissesWithoutRefreshingAndWorksNormallyAfterward() = verifyDismissal(tapUpdate = true)

    @Test
    fun taggedAuthorHasSharedSnapshotsThroughoutOpeningAndClosing() = verifyDismissal(tapUpdate = false, withSource = true)

    private fun verifyDismissal(tapUpdate: Boolean, withSource: Boolean = false) = SwingUtilities.invokeAndWait {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("StoryPreviewDismissInputTest")
        bootstrap.app.userTags.setTag("tester", "Maintainer")
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
        if (!withSource) controller.showStoryPreview(stories, listOf(0xffeeeeee.toInt()), 42)
        var cardBounds = Rect.Zero
        var updateBounds = Rect.Zero
        var scrim = 0f
        var previewContentCommits = 0
        var sharedTransition: StoryPreviewSharedTransitionState? = null
        val scheduler = TestCoroutineScheduler()
        val scene = ImageComposeScene(400, 800, Density(1f), coroutineContext = StandardTestDispatcher(scheduler)) {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                CompositionLocalProvider(LocalHarmonicUiDependencies provides HarmonicUiDependencies(bootstrap.app, bootstrap.scene)) {
                    Box(Modifier.fillMaxSize()) {
                        if (withSource) {
                            StoryRow(SettingsStoryPreviewModel.copy(title = stories.single().story.title.orEmpty(),
                                authorName = "tester", authorTag = "Maintainer"),
                                settings.copy(previewImageMode = StoryPreviewMode.OFF, showPreviewText = false)
                                    .toStoryRowStyle(StoryRowStyleContext(12, 0, false)),
                                modifier = Modifier.padding(top = 80.dp).graphicsLayer {
                                    alpha = if (controller.isStoryPreviewShowing()) 0f else 1f
                                }, listItem = true, animateChanges = false, capturePreviewSourceGeometry = true,
                                onPreviewSourceGeometryChanged = { controller.updateStoryPreviewSourceGeometry(42, it) })
                        }
                        StoryPreviewOverlay(controller, tablet = false, onScrimAlphaChanged = { scrim = it }) { story, page, color, modifier ->
                            val transition = checkNotNull(LocalStoryPreviewSharedTransition.current)
                            SideEffect { previewContentCommits++; sharedTransition = transition }
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
        fun frame() {
            // Drive effects, delays, and frames on one clock instead of racing the unconfined
            // scene dispatcher and real-time coroutine delays against a tight render loop.
            scheduler.runCurrent()
            Snapshot.sendApplyNotifications()
            scene.render(time).close()
            time += 16_000_000
            scheduler.advanceTimeBy(16)
            scheduler.runCurrent()
        }
        fun awaitIdle() {
            repeat(120) {
                frame()
                Snapshot.sendApplyNotifications()
                scheduler.runCurrent()
                if (!Snapshot.current.hasPendingChanges() &&
                    !Snapshot.isApplyObserverNotificationPending && !scene.hasInvalidations()) return
            }
            error("Preview must settle before measuring gesture recompositions")
        }
        fun tap(point: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = time / 1_000_000, type = PointerType.Touch)
            scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = time / 1_000_000 + 1, type = PointerType.Touch)
        }
        try {
            if (withSource) {
                repeat(12) { frame() }
                controller.showStoryPreview(stories, listOf(0xffeeeeee.toInt()), 42)
            }
            var sharedAuthorFrames = 0
            repeat(45) { index ->
                frame()
                sharedTransition?.takeIf { it.active }?.let { transition ->
                    if (transition.sourceSnapshot(StoryPreviewSourceElement.Author) != null &&
                        transition.targetSnapshot(StoryPreviewSharedElement.Author) != null) sharedAuthorFrames++
                }
                if (withSource) System.getenv("HARMONIC_AUTHOR_PREVIEW_FRAMES")?.let { folder ->
                    File(folder).mkdirs()
                    scene.render(time).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                        File(folder, "frame-${index.toString().padStart(3, '0')}.png").writeBytes(data.bytes)
                    } }
                }
            }
            if (withSource) assertTrue(sharedAuthorFrames > 2, "Author must participate in shared opening frames")
            awaitIdle()
            assertTrue(cardBounds.width > 0f)
            assertTrue(updateBounds.width > 0f)
            assertEquals(0.32f, scrim, 0.001f)
            fun walk(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::walk)
            val author = scene.semanticsOwners.flatMap { walk(it.unmergedRootSemanticsNode) }.single {
                it.config.getOrNull(SemanticsProperties.TestTag) == "story-author-tag" && cardBounds.contains(it.boundsInRoot.center)
            }
            assertEquals("tester (Maintainer)", author.config[SemanticsProperties.Text].single().text)
            assertTrue(cardBounds.contains(author.boundsInRoot.center), "The author tag belongs inside the preview card")
            controller.startStoryPreviewPredictiveBack(0.1f, edge = 0, touchY = 200f)
            // Starting the gesture may finish pending geometry or snapshot work. Measure only
            // subsequent progress updates once those one-time changes have settled.
            awaitIdle()
            val commitsBeforeGesture = previewContentCommits
            for (progress in listOf(0.2f, 0.4f, 0.6f, 0.8f)) {
                controller.updateStoryPreviewPredictiveBack(progress, edge = 0, touchY = 200f)
                repeat(2) { frame() }
                assertEquals(commitsBeforeGesture, previewContentCommits,
                    "Gesture progress $progress must not invalidate retained preview content through its CompositionLocal")
            }
            assertTrue(scrim < 0.2f, "The scrim must still follow gesture progress")
            controller.cancelStoryPreviewPredictiveBack()
            repeat(25) { frame() }
            assertEquals(0.32f, scrim, 0.001f)
            tap(if (tapUpdate) updateBounds.center else cardBounds.topLeft + Offset(1f, 1f))
            assertTrue(controller.storyPreviewDismissRequest != 0, "The outside tap must request dismissal")
            assertTrue(controller.isStoryPreviewShowing(), "Dismissal must retain the preview for its animation")
            assertEquals(0, refreshes, "A dimmed update button must not refresh the list")
            var closingAuthorFrames = 0
            repeat(45) {
                frame()
                sharedTransition?.takeIf { it.active }?.let { transition ->
                    if (transition.sourceSnapshot(StoryPreviewSourceElement.Author) != null &&
                        transition.targetSnapshot(StoryPreviewSharedElement.Author) != null) closingAuthorFrames++
                }
            }
            if (withSource) assertTrue(closingAuthorFrames > 2, "Author must participate in shared closing frames")
            assertFalse(controller.isStoryPreviewShowing(), "The exit animation must finish dismissal")
            if (tapUpdate) {
                tap(updateBounds.center)
                assertEquals(1, refreshes, "After dismissal the update button must refresh normally")
            }
        } finally {
            scene.close()
            scheduler.runCurrent()
            bootstrap.close()
        }
    }
}
