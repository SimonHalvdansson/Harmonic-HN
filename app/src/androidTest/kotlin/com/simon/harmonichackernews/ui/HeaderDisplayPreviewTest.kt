@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.settings.*
import com.simon.harmonichackernews.ui.settings.HeaderPreviewSample
import com.simon.harmonichackernews.ui.settings.HeaderPreviewSampleSelector
import com.simon.harmonichackernews.ui.settings.HeaderDisplayPreview
import com.simon.harmonichackernews.ui.settings.SettingsPage
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HeaderDisplayPreviewTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun settings(loggedIn: Boolean) = CommentDisplaySettings.from(
        AppSettingsRepository(InMemoryKeyValueStore(), kotlinx.coroutines.flow.emptyFlow()).snapshot().comments,
        false, false, loggedIn, false,
    ).copy(headerMetaInfo = com.simon.harmonichackernews.settings.CommentsHeaderMetaInfo.BUTTONS, headerLayout = CommentsHeaderLayout.BALANCED,
        headerEdge = CommentsHeaderEdge.ROUNDED)

    @Test fun signedInActionsShareMetadataAndAnimateLayoutChanges() {
        val state = mutableStateOf(settings(true).copy(headerLayout = CommentsHeaderLayout.STANDARD))
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsPage("Post header", true, {}, showTopAppBar = false, pinnedContent = {
                    HeaderDisplayPreview(state.value, true, {})
                }) { item { Text("Layout preview") } }
            }
        }
        compose.onAllNodesWithContentDescription("Upvote").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Reply to post").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Open profile: willow").assertCountEquals(1)
        compose.onNodeWithContentDescription("User").assertDoesNotExist()
        assertPrimaryRow()
        compose.onNodeWithContentDescription("Upvote").performClick()
        compose.onNodeWithContentDescription("Remove vote").assertExists()
        compose.onNodeWithContentDescription("Remove vote").performClick()
        compose.onNodeWithContentDescription("Reply to post").performClick()
        compose.onNodeWithContentDescription("Open profile: willow").performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onNodeWithText("Header display").assertDoesNotExist()
        compose.onNodeWithContentDescription("Favorite").performClick()
        compose.onNodeWithContentDescription("Remove favorite").assertExists()
        val largeTitleTop = titleTop()
        compose.runOnIdle { state.value = state.value.copy(showUpButton = true) }
        assertEquals("Large preview needs no extra title clearance for Back", largeTitleTop, titleTop(), 1f)
        save("header-large-with-back")
        compose.runOnIdle { state.value = state.value.copy(showUpButton = false) }
        save("header-signed-in-standard")

        val before = height()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.value = state.value.copy(headerLayout = CommentsHeaderLayout.BALANCED) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(160)
        val during = height()
        save("header-layout-midpoint")
        compose.mainClock.advanceTimeBy(1000)
        val after = height()
        save("header-signed-in-balanced")
        assertTrue("Layout should reduce the header height: $before -> $after", before > after)
        assertTrue("Header height should interpolate, not snap: $before -> $during -> $after", during > after && during < before)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("science.org \u00b7 2h", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("2h").assertDoesNotExist()
        save("header-signed-in-balanced")
        val titleTop = compose.onNodeWithText("Algorithm breaks speed limit for solving linear equations", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        val domainTop = compose.onNodeWithText("science.org", substring = true, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Domain should sit beneath the title", domainTop > titleTop)
        compose.runOnIdle { state.value = state.value.copy(showUpButton = true) }
        val titleWithBack = compose.onNodeWithText("Algorithm breaks speed limit for solving linear equations", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.top
        assertTrue("Back control reserves space only when shown", titleWithBack > titleTop)
        val backBottom = compose.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot.bottom
        val gap = (titleWithBack - backBottom) / compose.activity.resources.displayMetrics.density
        assertTrue("Compact title should sit close beneath Back: ${gap}dp", gap in 0f..16f)
        save("header-small-with-back")
        compose.runOnIdle { state.value = state.value.copy(showHeaderPreviewImage = false) }
        save("header-off-with-back")
        compose.runOnIdle { state.value = state.value.copy(showHeaderPreviewImage = true) }
        compose.runOnIdle { state.value = state.value.copy(showUpButton = false) }

        compose.runOnIdle { state.value = state.value.copy(headerEdge = CommentsHeaderEdge.SHADOW) }
        save("header-shadow")
        compose.onNodeWithContentDescription("Bookmark").performClick()
        compose.onNodeWithContentDescription("Remove bookmark").assertExists()
        compose.onNodeWithContentDescription("Refresh").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithContentDescription("Refresh").assertHasClickAction()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Bookmark").assertDoesNotExist()
        compose.onNodeWithText("Refresh").assertDoesNotExist()
        save("header-preview-menu")
    }

    @Test fun loggedOutKeepsSeparateActionsAndPreviewRefreshFinishes() {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                HeaderDisplayPreview(settings(false), true, {})
            }
        }
        compose.onNodeWithContentDescription("Open profile: willow").assertDoesNotExist()
        compose.onNodeWithContentDescription("Upvote").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reply to post").assertDoesNotExist()
        compose.onNodeWithContentDescription("User").assertExists()
        compose.onNodeWithContentDescription("Bookmark").performClick()
        compose.onNodeWithContentDescription("Remove bookmark").assertExists()
        compose.onNodeWithContentDescription("Refresh").performClick()
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithContentDescription("Refresh").assertHasClickAction()
        save("header-logged-out")
    }

    @Test fun primaryButtonsAndExpandedActionsFitNarrowScreens() {
        val state = mutableStateOf(settings(true).copy(canProvideSummary = true))
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("material_dark", true)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                        androidx.compose.ui.platform.LocalDensity.current.density, 1.4f),
                ) {
                    Box(Modifier.width(androidx.compose.ui.unit.Dp(320f))) {
                        HeaderDisplayPreview(state.value, true, {})
                    }
                }
            }
        }
        assertPrimaryRow()
        val buttons = listOf("Favorite", "Bookmark", "Summarize", "Share", "Refresh", "More options")
        val centers = buttons.map {
            compose.onNodeWithContentDescription(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.y
        }
        save("header-narrow-dark")
        assertTrue("Compact actions should share one row: $centers", centers.max() - centers.min() < 2f)
        compose.onNodeWithContentDescription("Summarize").assertExists().performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.runOnIdle { state.value = state.value.copy(headerMetaInfo = CommentsHeaderMetaInfo.ON_IMAGE) }
        compose.onNodeWithText("53", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("18", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Upvote").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Remove vote").assertExists()
        save("header-meta-narrow-on-image")
    }

    @Test fun imageDetailsFollowTheSampleAndFallBackWhenNoImageIsAvailable() {
        val state = mutableStateOf(settings(true).copy(headerMetaInfo = CommentsHeaderMetaInfo.ON_IMAGE))
        val sample = mutableStateOf(HeaderPreviewSample.LINK_WITH_IMAGE)
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                SettingsPage("Post header", true, {}, showTopAppBar = false,
                    pinnedContent = { HeaderDisplayPreview(state.value, true, {}, sample.value) },
                    pinnedContentBackground = androidx.compose.ui.graphics.Color.Transparent,
                    floatingContent = { HeaderPreviewSampleSelector(sample.value) { sample.value = it } },
                ) {
                    item { Text("Layout preview") }
                }
            }
        }
        compose.onNodeWithText("Preview only").assertDoesNotExist()
        val selectorBounds = compose.onNodeWithContentDescription("Next preview post").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Link with image").assertDoesNotExist()
        compose.onNodeWithText("1 of 4").assertDoesNotExist()
        val image = compose.onNodeWithContentDescription("Story preview image", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Small preview should be wider than tall", image.width > image.height)
        val voteBadge = compose.onNodeWithContentDescription("53 points", useUnmergedTree = true).assertHasNoClickAction().fetchSemanticsNode().boundsInRoot
        val replyBadge = compose.onNodeWithContentDescription("18 comments", useUnmergedTree = true).assertHasNoClickAction().fetchSemanticsNode().boundsInRoot
        assertTrue("Informational counts belong on the image", image.contains(voteBadge.center) && image.contains(replyBadge.center))
        compose.onNodeWithText("science.org \u00b7 2h \u00b7 willow", useUnmergedTree = true).assertIsDisplayed()
        val title = compose.onNodeWithText("Algorithm breaks speed limit for solving linear equations", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val domain = compose.onNodeWithText("science.org", substring = true, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("Image and title/domain group share a vertical center", (title.top + domain.bottom) / 2f, image.center.y, 3f)
        compose.onNodeWithContentDescription("User").assertIsDisplayed()
        assertTrue("Reply remains in the toolbar", compose.onNodeWithContentDescription("Reply to post").fetchSemanticsNode().boundsInRoot.top > image.bottom)
        compose.onNodeWithContentDescription("Upvote").performClick()
        compose.onNodeWithContentDescription("Remove vote").assertExists()
        save("header-meta-on-image")
        compose.onNodeWithContentDescription("Next preview post").performClick()
        assertEquals("Sample controls stay fixed when preview height changes", selectorBounds,
            compose.onNodeWithContentDescription("Next preview post").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Story preview image", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("User").assertIsDisplayed()
        compose.onNodeWithText("53", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("willow", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(headerLayout = CommentsHeaderLayout.STANDARD) }
        val noImageTitleTop = titleTop()
        val noImageHeight = height()
        compose.runOnIdle { state.value = state.value.copy(showHeaderPreviewImage = false) }
        assertEquals("Large must not reserve an image slot when the post has no image", noImageTitleTop, titleTop(), 1f)
        assertEquals("Absent and disabled images use the same header height", noImageHeight, height(), 1f)
        val previewTop = compose.onNodeWithContentDescription("Header preview").fetchSemanticsNode().boundsInRoot.top
        assertTrue("No empty image-sized gap above the title",
            (noImageTitleTop - previewTop) / compose.activity.resources.displayMetrics.density < 140f)
        compose.runOnIdle { state.value = state.value.copy(showHeaderPreviewImage = true) }
        save("header-meta-no-image")
        compose.onNodeWithContentDescription("Next preview post").performClick()
        compose.onNodeWithText("I made a small tool", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("science.org", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("User").assertIsDisplayed()
        save("header-meta-text-post")
        compose.onNodeWithContentDescription("Next preview post").performClick()
        compose.onNodeWithText("WILLOW / READING-LIST", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("A small, offline-first home", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Story preview image", useUnmergedTree = true).assertDoesNotExist()
        save("header-link-preview")
        compose.runOnIdle { state.value = state.value.copy(headerLayout = CommentsHeaderLayout.BALANCED) }
        compose.onNodeWithContentDescription("Next preview post").performClick()
        compose.onNodeWithText("science.org", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(showHeaderPreviewImage = false) }
        compose.onNodeWithContentDescription("User").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(showHeaderPreviewImage = true, hasAccountDetails = false) }
        compose.onNodeWithContentDescription("53 points", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("18 comments", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Upvote").assertDoesNotExist()
        assertEquals(CommentsHeaderMetaInfo.ON_IMAGE, state.value.headerMetaInfo)
    }

    private fun assertPrimaryRow() {
        val bounds = listOf("Open profile: willow", "Upvote", "Reply to post").map {
            compose.onNodeWithContentDescription(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertTrue("User, vote and comment should share a row", bounds.maxOf { it.center.y } - bounds.minOf { it.center.y } < 2f)
        assertTrue("User, vote and comment should appear in that order",
            bounds[0].right <= bounds[1].left && bounds[1].right <= bounds[2].left)
    }

    @Test fun smallImageLongPressInspectsEvenOverTheNonClickableBadges() {
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                HeaderDisplayPreview(settings(true).copy(headerMetaInfo = CommentsHeaderMetaInfo.ON_IMAGE), true, {})
            }
        }
        compose.onNodeWithContentDescription("53 points", useUnmergedTree = true)
            .assertHasNoClickAction().performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Inspect story image").assertIsDisplayed()
        save("header-small-image-inspection")
    }

    @Test fun tightToolbarKeepsOneRowAndExcessActionsInTheMenu() {
        val state = mutableStateOf(settings(true).copy(headerMetaInfo = CommentsHeaderMetaInfo.ON_IMAGE, canProvideSummary = true))
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("material_light", false)
            HarmonicTheme(palette.colorScheme, palette.dark) {
                Box(Modifier.width(androidx.compose.ui.unit.Dp(240f))) {
                    HeaderDisplayPreview(state.value, true, {})
                }
            }
        }
        val centers = listOf("User", "Reply to post", "Upvote", "Share", "More options").map {
            compose.onNodeWithContentDescription(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center.y
        }
        assertTrue("Actions never wrap to another row: $centers", centers.max() - centers.min() < 2f)
        compose.onNodeWithContentDescription("Summarize").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Favorite").assertIsDisplayed()
        compose.onNodeWithText("Summarize").assertIsDisplayed().performClick()
        compose.runOnIdle { state.value = state.value.copy(showFavicons = false, headerTint = CommentsHeaderTint.THEME) }
        compose.onNodeWithContentDescription("Site icon", useUnmergedTree = true).assertDoesNotExist()
        save("header-tight-toolbar-theme")
    }

    private fun titleTop() = compose.onNodeWithText(
        "Algorithm breaks speed limit for solving linear equations", useUnmergedTree = true,
    ).fetchSemanticsNode().boundsInRoot.top

    private fun height() = compose.onNodeWithContentDescription("Header preview")
        .fetchSemanticsNode().boundsInRoot.height

    private fun save(name: String) {
        val bitmap = compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        File(compose.activity.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
