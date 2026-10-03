package com.simon.harmonichackernews.ui.comments

import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshots.Snapshot
import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.navigation.StoryRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

// A real composition verifies invalidation, which value-only controller tests cannot catch.
@OptIn(ExperimentalCoroutinesApi::class)
class CommentsSheetRecompositionTest {
    @Test
    fun draggingDoesNotRecomposeUnchangedCommentsInteractions() = runTest {
        withController { controller ->
            var contentCommits = 0
            var progressCommits = 0
            var observedProgress = 1f
            withComposition(content = {
                Observe(read = {
                    listOf(
                        controller.topInsetPx,
                        controller.sheetRequest,
                        controller.navigationRequest,
                        controller.showWebsiteRequest,
                        controller.scrollToCommentRequest,
                        controller.stopScrollRequest,
                        controller.highlightedCommentId,
                        controller.searchScrollTopTargetId,
                        controller.searchDialogVisible,
                        controller.predictiveBackActive,
                        controller.predictiveBackProgress,
                        controller.suppressedCommentIds,
                        controller.commentActionOverlay,
                        controller.linkPreviewOverlay,
                        controller.suppressedReferenceUrlForComment(123),
                    )
                }) { contentCommits++ }
                Observe(read = { observedProgress = controller.sheetSlideOffset }) { progressCommits++ }
            }) { frame ->
                val initialContentCommits = contentCommits
                val initialProgressCommits = progressCommits
                for (offset in listOf(0.95f, 0.8f, 0.5f, 0.2f, 0f, 0.5f, 1f)) {
                    controller.updateSheet(offset, 0)
                    frame()
                    assertEquals(offset, observedProgress)
                }
                assertEquals(initialProgressCommits + 7, progressCommits)
                assertEquals(initialContentCommits, contentCommits,
                    "Moving the sheet must not invalidate unchanged comments UI")

                // The same readers must still receive actual interaction and inset changes.
                controller.requestExpandSheet()
                frame()
                assertEquals(initialContentCommits + 1, contentCommits)
                controller.updateSheet(1f, 24)
                frame()
                assertEquals(initialContentCommits + 2, contentCommits)
                controller.revealSearchResult(123, 50)
                frame()
                assertEquals(initialContentCommits + 3, contentCommits)
            }
        }
    }

    @Test
    fun visibilityReadersOnlyRecomposeAtSheetThresholds() = runTest {
        withController { controller ->
            controller.updateContent(controller.screenState.copy(integratedWebView = true))
            var commits = 0
            var expanded = true
            var websiteVisible = false
            withComposition(content = {
                Observe(read = {
                    expanded = controller.isSheetExpanded()
                    websiteVisible = controller.isWebsiteVisible()
                }) { commits++ }
            }) { frame ->
                val initialCommits = commits
                for (offset in listOf(0.95f, 0.8f, 0.5f, 0.2f)) {
                    controller.updateSheet(offset, 0)
                    frame()
                }
                assertEquals(initialCommits + 1, commits)
                assertEquals(false, expanded)
                assertEquals(false, websiteVisible)

                controller.updateSheet(0f, 0)
                frame()
                assertEquals(initialCommits + 2, commits)
                assertEquals(true, websiteVisible)

                controller.updateSheet(1f, 0)
                frame()
                assertEquals(initialCommits + 3, commits)
                assertEquals(true, expanded)
                assertEquals(false, websiteVisible)
            }
        }
    }

    private suspend fun TestScope.withController(block: suspend (CommentsScreenController) -> Unit) {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("SheetTest")
        val scene = bootstrap.scene
        scene.navigation.openStory(StoryRoute(42))
        val binding = CommentsFeatureBinding.create(
            bootstrap.app, scene, checkNotNull(scene.navigation.state.value.storyRequest), backgroundScope,
        )
        runCurrent()
        try {
            block(binding.controller)
        } finally {
            binding.close()
            bootstrap.close()
        }
    }

    private suspend fun TestScope.withComposition(
        content: @Composable () -> Unit,
        block: suspend (frame: () -> Unit) -> Unit,
    ) {
        val clock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + clock)
        val composition = Composition(EmptyApplier(), recomposer)
        val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
        var frameTime = 0L
        fun frame() {
            Snapshot.sendApplyNotifications()
            runCurrent()
            frameTime += 16_666_667L
            clock.sendFrame(frameTime)
            runCurrent()
        }
        try {
            composition.setContent(content)
            frame()
            block(::frame)
        } finally {
            composition.dispose()
            recomposer.cancel()
            runner.join()
        }
    }

    @Composable
    private fun Observe(read: () -> Any?, onCommit: () -> Unit) {
        read()
        SideEffect(onCommit)
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
