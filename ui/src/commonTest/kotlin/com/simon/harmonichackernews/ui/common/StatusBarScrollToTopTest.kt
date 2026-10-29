package com.simon.harmonichackernews.ui.common

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusBarScrollToTopTest {
    private val viewport = Rect(0f, 0f, 1000f, 800f)

    @Test
    fun ipadKeepsIndependentPanesWhileIphoneHasExactlyOneTarget() {
        val stories = target(Rect(0f, 40f, 400f, 800f))
        val comments = target(Rect(420f, 40f, 1000f, 800f))
        assertEquals(listOf(comments, stories), select(stories, comments))
        assertEquals(listOf(comments), statusBarScrollToTopTargets(listOf(stories, comments), viewport, false))
    }

    @Test
    fun modalOwnsItsPaneWithoutScrollingTheListBehindIt() {
        val stories = target(Rect(0f, 40f, 400f, 800f))
        val comments = target(Rect(420f, 40f, 1000f, 800f))
        val ask = target(comments.bounds, priority = 1)
        assertEquals(listOf(ask, stories), select(stories, ask, comments))
    }

    @Test
    fun coveredDisabledAndOffscreenDestinationsCannotCaptureTheGesture() {
        val covered = target(viewport, enabled = false, priority = 2)
        val offscreen = target(Rect(1000f, 0f, 1400f, 800f), priority = 3)
        val old = target(viewport)
        val current = target(viewport)
        assertEquals(listOf(current), select(covered, offscreen, old, current))
    }

    @Test
    fun disposedModalRestoresTheUnderlyingTarget() {
        val list = target(viewport)
        val modal = target(viewport, priority = 1)
        val registry = StatusBarScrollToTopRegistry()
        registry.targets += listOf(list, modal)
        assertEquals(listOf(modal), statusBarScrollToTopTargets(registry.targets, viewport, true))
        registry.targets -= modal
        assertEquals(listOf(list), statusBarScrollToTopTargets(registry.targets, viewport, true))
    }

    private fun select(vararg targets: StatusBarScrollToTopTarget) =
        statusBarScrollToTopTargets(targets.toList(), viewport, multiplePanes = true)

    private fun target(bounds: Rect, enabled: Boolean = true, priority: Int = 0) =
        StatusBarScrollToTopTarget {}.also {
            it.bounds = bounds
            it.enabled = enabled
            it.priority = priority
        }
}
