package com.simon.harmonichackernews.ui.stories

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.ArgbColor
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.presentation.StoryPreviewOverlayState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StoryPreviewPagerRestorationTest {
    @Test
    fun shorterDeckRestoresByStoryIdInsteadOfSavedOutOfBoundsPage() =
        verifyRestoration(savedPage = 4, rebuiltIds = listOf(2, 5), visibleId = 5, expectedPage = 1)

    @Test
    fun reorderedDeckRestoresTheSameStoryEvenWhenOldIndexIsInBounds() =
        verifyRestoration(savedPage = 2, rebuiltIds = listOf(3, 1, 2), visibleId = 3, expectedPage = 0)

    @Test
    fun returningToRetainedPreviewUsesTheLastVisibleStory() =
        verifyRestoration(savedPage = 0, rebuiltIds = listOf(1, 2, 3), visibleId = 3, expectedPage = 2)

    private fun verifyRestoration(
        savedPage: Int,
        rebuiltIds: List<Int>,
        visibleId: Int,
        expectedPage: Int,
    ) = runTest {
        val recomposer = Recomposer(coroutineContext)
        fun compose(registry: SaveableStateRegistry, content: @Composable () -> Unit): Composition =
            Composition(EmptyApplier(), recomposer).apply {
                setContent {
                    CompositionLocalProvider(LocalSaveableStateRegistry provides registry, content = content)
                }
            }
        val registry = SaveableStateRegistry(null) { true }
        // Reproduce the independent saved index written by the old preview implementation.
        val original = compose(registry) { rememberPagerState(initialPage = savedPage) { 5 } }
        val saved = registry.performSave()
        original.dispose()
        val overlay = StoryPreviewOverlayState(
            stories = rebuiltIds.map { id ->
                StoryListItemSnapshot(StorySnapshot(id = id), StoryPresentationSnapshot(loaded = true))
            },
            cardBackgrounds = rebuiltIds.map { ArgbColor(0) },
            initialPage = 0,
            sessionId = 1,
        )
        lateinit var pager: PagerState
        val restored = compose(SaveableStateRegistry(saved) { true }) {
            pager = rememberStoryPreviewPagerState(overlay, visibleId)
        }
        try {
            assertEquals(rebuiltIds.size, pager.pageCount)
            assertEquals(expectedPage, pager.currentPage)
            assertEquals(visibleId, overlay.stories[pager.currentPage].id)
        } finally {
            restored.dispose()
            recomposer.cancel()
        }
    }

    private class EmptyApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }
}
