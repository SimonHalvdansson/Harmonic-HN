package com.simon.harmonichackernews.ui.comments

import com.simon.harmonichackernews.app.DesktopHarmonicAppBootstrap
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.navigation.toDestination
import com.simon.harmonichackernews.data.Comment
import com.simon.harmonichackernews.navigation.StoryRoute
import com.simon.harmonichackernews.presentation.CommentsIntent
import com.simon.harmonichackernews.presentation.CommentsRuntimeEffect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsFeatureBindingTest {
    @Test
    fun reopeningRestoresScrollAndCollapsedCommentsEvenWithoutADiskCacheHit() = runTest {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("HarmonicTest")
        val scene = bootstrap.scene
        fun open(target: Int = -1): CommentsFeatureBinding {
            scene.navigation.openStory(StoryRoute(42, scrollToCommentId = target))
            val request = checkNotNull(scene.navigation.state.value.storyRequest)
            val binding = CommentsFeatureBinding.create(bootstrap.app, scene, request, backgroundScope)
            binding.updatePresentationCapabilities(isTablet = false)
            scene.sessions.commentsStateFor(request.serial, 42).commentThread.replaceParsedComments(
                binding.story,
                listOf(comment(201, 42, 0), comment(202, 201, 1), comment(203, 42, 0)),
                "Default",
                false,
            )
            return binding
        }
        try {
            val first = open()
            runCurrent()
            first.store.accept(CommentsIntent.ToggleComment(201))
            first.store.accept(CommentsIntent.RecordScrollPosition(203, 44))
            first.close()
            scene.navigation.detailRemovedFromBackStack()

            val reopened = open()
            assertTrue(reopened.controller.initialScrollRestorationPending)
            reopened.handleEffect(CommentsRuntimeEffect.ThreadReady(false, false), scene) {}
            assertTrue(reopened.controller.initialScrollRestorationPending)
            reopened.controller.completeInitialScrollRestoration()
            assertFalse(reopened.controller.initialScrollRestorationPending)
            assertEquals(203, reopened.controller.scrollToCommentRequest?.commentId)
            assertEquals(-44, reopened.controller.scrollToCommentRequest?.topOffsetPx)
            assertFalse(checkNotNull(reopened.store.comment(201)).expanded)
            reopened.close()
            scene.navigation.detailRemovedFromBackStack()

            val targeted = open(202)
            targeted.handleEffect(CommentsRuntimeEffect.ThreadReady(false, false), scene) {}
            assertEquals(202, targeted.controller.scrollToCommentRequest?.commentId)
            assertTrue(checkNotNull(targeted.store.comment(201)).expanded)
            targeted.close()
        } finally { bootstrap.close() }
    }

    @Test
    fun leavingBeforeThreadLoadPreservesPreviouslyCollapsedComments() = runTest {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("HarmonicTest")
        try {
            val scene = bootstrap.scene
            scene.navigation.openStory(StoryRoute(42))
            val request = checkNotNull(scene.navigation.state.value.storyRequest)
            val progress = scene.sessions.commentsStateFor(request.serial, 42).scrollProgress
            progress.initialized = true
            progress.topCommentId = 203
            progress.topCommentOffset = -44
            progress.collapsedIDs += 201
            val binding = CommentsFeatureBinding.create(bootstrap.app, scene, request, backgroundScope)
            assertTrue(binding.controller.initialScrollRestorationPending)
            binding.close()
            assertEquals(setOf(201), progress.collapsedIDs)
            assertEquals(203, progress.topCommentId)
            assertEquals(-44, progress.topCommentOffset)
        } finally { bootstrap.close() }
    }

    @Test
    fun browserFirstOpeningPreservesReadingPositionUntilCommentsAreExpanded() = runTest {
        val bootstrap = DesktopHarmonicAppBootstrap.inMemory("HarmonicTest")
        try {
            val scene = bootstrap.scene
            val story = Story().apply { id = 42; url = "https://example.com"; isLink = true }
            scene.navigation.openStory(story.toDestination(showWebsite = true))
            val request = checkNotNull(scene.navigation.state.value.storyRequest)
            val session = scene.sessions.commentsStateFor(request.serial, 42)
            session.scrollProgress.apply {
                initialized = true
                topCommentId = 203
                topCommentOffset = -44
                collapsedIDs += 201
            }
            val binding = CommentsFeatureBinding.create(bootstrap.app, scene, request, backgroundScope)
            binding.updatePresentationCapabilities(isTablet = false)
            session.commentThread.replaceParsedComments(binding.story,
                listOf(comment(201, 42, 0), comment(202, 201, 1), comment(203, 42, 0)), "Default", false)
            runCurrent()
            binding.handleEffect(CommentsRuntimeEffect.ThreadReady(true, false), scene) {}
            assertTrue(binding.controller.integratedWebView, "Link fixture enables the integrated browser")
            assertEquals(0f, binding.controller.sheetSlideOffset)
            kotlin.test.assertNull(binding.controller.scrollToCommentRequest)
            assertFalse(binding.controller.initialScrollRestorationPending)
            binding.controller.listener.onScrollPositionChanged(0, 0)
            assertEquals(203, session.scrollProgress.topCommentId)
            binding.controller.listener.onSheetSettled(true)
            assertEquals(203, binding.controller.scrollToCommentRequest?.commentId)
            assertTrue(binding.controller.initialScrollRestorationPending)
            binding.controller.completeInitialScrollRestoration()
            binding.close()
        } finally { bootstrap.close() }
    }

    private fun comment(id: Int, parent: Int, depth: Int) = Comment().apply {
        this.id = id
        this.parent = parent
        this.depth = depth
        text = "Comment $id"
        by = "author"
        expanded = true
    }
}
