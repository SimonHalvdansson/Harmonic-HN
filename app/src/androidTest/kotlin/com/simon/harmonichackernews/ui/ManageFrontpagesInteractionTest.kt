package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performScrollToNode
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
    fun resetRestoresTheOrderAndTopStoriesDefaultWhileKeepingAddedFrontpages() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        repository.setAdditionalFrontpages(setOf(StoryType.CLASSIC.label))
        repository.setPreferredStoryType(StoryType.NEW_STORIES.label)
        repository.setFrontpageOrder(listOf(StoryType.CLASSIC.name, StoryType.NEW_STORIES.name))
        showRoute(repository)

        compose.onNodeWithContentDescription("Reset frontpage order and default").performClick()
        compose.runOnIdle {
            val story = repository.snapshot().story
            assertTrue(story.frontpageOrder.isEmpty())
            assertEquals(StoryType.TOP_STORIES.label, story.preferredStoryType)
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
        val lastIndex = StoryTypeMenuPolicy.availableTypes(emptySet(), hasAccount = false).size +
            StoryType.additionalFrontpages.size + 1
        compose.onNode(hasScrollAction()).performScrollToIndex(lastIndex)
        val last = compose.onNodeWithContentDescription("Add ${StoryType.additionalFrontpages.last().label}")
        last.assertIsDisplayed()
        val reset = compose.onNodeWithContentDescription("Reset frontpage order and default")
        reset.assertIsDisplayed()
        assertEquals(compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot.center.x,
            reset.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        assertTrue(last.fetchSemanticsNode().boundsInRoot.bottom < reset.fetchSemanticsNode().boundsInRoot.top)
    }

    @Test
    fun addHeadingAnimatesWhenFrontpagesAreAddedAndRemoved() {
        showScreen()
        compose.mainClock.autoAdvance = false
        for (control in listOf("Add Classic", "Remove Classic")) {
            val before = compose.onNodeWithText("Add frontpage").fetchSemanticsNode().boundsInRoot.top
            compose.onNodeWithContentDescription(control).performClick()
            val tops = buildList {
                repeat(45) {
                    compose.mainClock.advanceTimeByFrame()
                    add(compose.onNodeWithText("Add frontpage").fetchSemanticsNode().boundsInRoot.top)
                }
            }
            val after = tops.last()
            assertTrue(kotlin.math.abs(after - before) > 10f)
            assertTrue("The heading must move through intermediate positions for $control",
                tops.filter { it > minOf(before, after) + 1f && it < maxOf(before, after) - 1f }
                    .map { it.toInt() }.distinct().size >= 3)
        }
    }

    @Test
    fun unslopShortcutScrollsToTheAvailableRowWithoutChangingPreferences() {
        verifyUnslopShortcut(enabled = false)
    }

    @Test
    fun unslopShortcutFindsAnAlreadyEnabledAndReorderedFrontpage() {
        verifyUnslopShortcut(enabled = true)
    }

    private fun verifyUnslopShortcut(enabled: Boolean) {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        if (enabled) {
            repository.setAdditionalFrontpages(setOf(StoryType.UNSLOP.label))
            repository.setFrontpageOrder((StoryTypeMenuPolicy.baseFrontpages.reversed() + StoryType.UNSLOP).map { it.name })
        }
        val before = repository.snapshot().story
        showRoute(repository, StoryType.UNSLOP)
        val target = compose.onNodeWithContentDescription(
            if (enabled) "Drag to reorder unslop.news" else "Add unslop.news",
        )
        compose.waitUntil(5_000) { target.isDisplayed() }
        target.assertIsDisplayed()
        assertTrue(target.fetchSemanticsNode().boundsInRoot.bottom <
            compose.onNodeWithContentDescription("Reset frontpage order and default").fetchSemanticsNode().boundsInRoot.top)
        compose.runOnIdle { assertEquals(before, repository.snapshot().story) }
    }

    private fun showRoute(repository: AppSettingsRepository, focusFrontpage: StoryType? = null) {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                Box(Modifier.height(480.dp)) {
                    ManageFrontpagesSettingsRoute(repository, onBack = {}, focusFrontpage = focusFrontpage)
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun everyAvailableFrontpageHasAnExplanationWithoutAddingIt() {
        val store = InMemoryKeyValueStore()
        val repository = AppSettingsRepository(store, store.changes)
        val before = repository.snapshot().story
        showRoute(repository)
        val explanations = listOf(
            StoryType.CLASSIC to "An alternative Hacker News frontpage based on votes from its oldest accounts.",
            StoryType.BEST_COMMENTS to "The most-upvoted Hacker News comments from the last 48 hours.",
            StoryType.HIGHLIGHTS to "A curated collection of standout Hacker News comments and discussions from over the years.",
            StoryType.ACTIVE to "Stories with the most active discussions on Hacker News right now.",
            StoryType.FRONT to "Stories that appeared on the Hacker News frontpage on a particular day. Use the date controls to browse past days.",
            StoryType.UNSLOP to "Hacker News stories with AI-related posts filtered out by unslop.news.",
        )
        for ((type, explanation) in explanations) {
            compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("About ${type.label}"))
            compose.onNodeWithContentDescription("About ${type.label}").performTouchInput { click() }
            compose.onNodeWithText(explanation).assertIsDisplayed()
            compose.onNodeWithText("OK").performClick()
            compose.runOnIdle { assertEquals(before, repository.snapshot().story) }
        }
    }

    @Test
    fun enabledFrontpageInfoDoesNotSelectRemoveOrReorderIt() {
        frontpages.value = initial + StoryType.CLASSIC
        showScreen()
        compose.onNodeWithContentDescription("About Classic").performTouchInput { click() }
        compose.onNodeWithText("An alternative Hacker News frontpage based on votes from its oldest accounts.")
            .assertIsDisplayed()
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle {
            assertEquals(initial + StoryType.CLASSIC, frontpages.value)
            assertEquals(StoryType.TOP_STORIES, default.value)
        }
    }

    private fun showScreen() {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                ManageFrontpagesSettingsScreen(
                    frontpages = frontpages.value,
                    defaultLabel = default.value.label,
                    available = listOf(StoryType.CLASSIC, StoryType.ACTIVE).filterNot { it in frontpages.value },
                    onBack = {},
                    onDefaultSelected = { default.value = it },
                    onOrderChanged = { frontpages.value = it },
                    onReset = { frontpages.value = initial; default.value = StoryType.TOP_STORIES },
                    onRemove = { frontpages.value -= it },
                    onAdd = { frontpages.value += it },
                )
            }
        }
        compose.waitForIdle()
    }
}
