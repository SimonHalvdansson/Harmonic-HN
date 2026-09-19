package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.StoryType
import com.simon.harmonichackernews.StoryTypeMenuPolicy
import com.simon.harmonichackernews.settings.AppSettingsRepository
import com.simon.harmonichackernews.settings.InMemoryKeyValueStore
import com.simon.harmonichackernews.ui.settings.ManageFrontpagesSettingsRoute
import com.simon.harmonichackernews.ui.settings.ManageFrontpagesSettingsScreen
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManageFrontpagesInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val initial = listOf(StoryType.TOP_STORIES, StoryType.NEW_STORIES, StoryType.BEST_STORIES)
    private val frontpages = mutableStateOf(initial)
    private val default = mutableStateOf(StoryType.TOP_STORIES)

    @Test
    fun tappingTheDragHandleDoesNotSelectTheDefault() {
        showScreen()
        compose.onNodeWithContentDescription("Drag to reorder New Stories")
            .performTouchInput { click() }
        compose.runOnIdle { assertEquals(StoryType.TOP_STORIES, default.value) }

        compose.onNodeWithText("New Stories").performClick()
        compose.runOnIdle { assertEquals(StoryType.NEW_STORIES, default.value) }
        compose.onNodeWithText("DEFAULT").assertIsDisplayed()
    }

    @Test
    fun visibleAddAndRemoveControlsKeepTheDefaultAndAppendToTheMainList() {
        showScreen()
        compose.onNodeWithContentDescription("Add Classic").performClick()
        compose.runOnIdle {
            assertEquals(initial + StoryType.CLASSIC, frontpages.value)
            assertEquals(StoryType.TOP_STORIES, default.value)
        }
        compose.onNodeWithContentDescription("Add Classic").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove Classic").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(initial, frontpages.value)
            assertEquals(StoryType.TOP_STORIES, default.value)
        }
        compose.onNodeWithContentDescription("Add Classic").assertIsDisplayed()
    }

    @Test
    fun releasedCardAnimatesIntoItsSlotAfterTheOrderIsSaved() {
        showScreen()
        val targetTop = compose.onNodeWithText("New Stories").fetchSemanticsNode().boundsInRoot.top
        val handle = compose.onNodeWithContentDescription("Drag to reorder Best Stories")
        val rowHeight = handle.fetchSemanticsNode().boundsInRoot.center.y -
            compose.onNodeWithContentDescription("Drag to reorder New Stories")
                .fetchSemanticsNode().boundsInRoot.center.y
        compose.mainClock.autoAdvance = false
        handle.performTouchInput { down(center) }
        handle.performTouchInput { moveBy(Offset(0f, -rowHeight * 0.75f)) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        handle.performTouchInput { up() }

        val tops = buildList {
            repeat(24) {
                compose.mainClock.advanceTimeByFrame()
                add(compose.onNodeWithText("Best Stories").fetchSemanticsNode().boundsInRoot.top)
            }
        }
        assertTrue(
            "The released card must pass through intermediate positions rather than snap",
            tops.filter { it > targetTop + 1f }.map { it.toInt() }.distinct().size >= 3,
        )
        assertEquals(targetTop, tops.last(), 1f)
        compose.runOnIdle {
            assertEquals(listOf(StoryType.TOP_STORIES, StoryType.BEST_STORIES, StoryType.NEW_STORIES), frontpages.value)
            assertEquals(StoryType.TOP_STORIES, default.value)
        }
    }

    @Test
    fun cancellingADragKeepsTheLatestSavedOrder() {
        showScreen()
        val savedOrder = listOf(StoryType.TOP_STORIES, StoryType.BEST_STORIES, StoryType.NEW_STORIES)
        compose.runOnIdle { frontpages.value = savedOrder }
        compose.waitForIdle()
        val handle = compose.onNodeWithContentDescription("Drag to reorder New Stories")
        val initialTop = handle.fetchSemanticsNode().boundsInRoot.top
        val rowHeight = initialTop - compose.onNodeWithContentDescription("Drag to reorder Best Stories")
            .fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.autoAdvance = false
        handle.performTouchInput { down(center) }
        handle.performTouchInput { moveBy(Offset(0f, -rowHeight * 0.75f)) }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
        handle.performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(initialTop, handle.fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.runOnIdle { assertEquals(savedOrder, frontpages.value) }
    }

    @Test
    fun resetRestoresTheOrderWithoutChangingTheDefaultOrAddedFrontpages() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        repository.setAdditionalFrontpages(setOf(StoryType.CLASSIC.label))
        repository.setPreferredStoryType(StoryType.NEW_STORIES.label)
        repository.setFrontpageOrder(listOf(StoryType.CLASSIC.name, StoryType.NEW_STORIES.name))
        showRoute(repository)

        compose.onNodeWithContentDescription("Reset frontpage order").performClick()
        compose.runOnIdle {
            val story = repository.snapshot().story
            assertTrue(story.frontpageOrder.isEmpty())
            assertEquals(StoryType.NEW_STORIES.label, story.preferredStoryType)
            assertEquals(setOf(StoryType.CLASSIC.label), story.additionalFrontpages)
            assertEquals(
                StoryTypeMenuPolicy.baseFrontpages + StoryType.CLASSIC,
                StoryTypeMenuPolicy.frontpages(story.additionalFrontpages, story.frontpageOrder),
            )
        }
        assertTrue(
            compose.onNodeWithText("Top Stories").fetchSemanticsNode().boundsInRoot.top <
                compose.onNodeWithText("New Stories").fetchSemanticsNode().boundsInRoot.top,
        )
    }

    @Test
    fun lastAvailableFrontpageCanScrollAboveTheResetButtonInAShortWindow() {
        val store = InMemoryKeyValueStore()
        showRoute(AppSettingsRepository(store, store.changes))
        val lastIndex = StoryTypeMenuPolicy.baseFrontpages.size + StoryType.additionalFrontpages.size + 1
        compose.onNode(hasScrollAction()).performScrollToIndex(lastIndex)
        val last = compose.onNodeWithContentDescription("Add ${StoryType.additionalFrontpages.last().label}")
        last.assertIsDisplayed()
        val reset = compose.onNodeWithContentDescription("Reset frontpage order")
        reset.assertIsDisplayed()
        assertTrue(last.fetchSemanticsNode().boundsInRoot.bottom < reset.fetchSemanticsNode().boundsInRoot.top)
    }

    private fun showRoute(repository: AppSettingsRepository) {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                Box(Modifier.height(480.dp)) {
                    ManageFrontpagesSettingsRoute(repository, onBack = {})
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showScreen() {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                ManageFrontpagesSettingsScreen(
                    frontpages = frontpages.value,
                    defaultLabel = default.value.label,
                    available = listOf(StoryType.CLASSIC).filterNot { it in frontpages.value },
                    onBack = {},
                    onDefaultSelected = { default.value = it },
                    onOrderChanged = { frontpages.value = it },
                    onResetOrder = { frontpages.value = initial },
                    onRemove = { frontpages.value -= it },
                    onAdd = { frontpages.value += it },
                )
            }
        }
        compose.waitForIdle()
    }
}
