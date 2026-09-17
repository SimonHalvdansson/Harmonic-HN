package com.simon.harmonichackernews.ui

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.adapters.CommentDisplaySettings
import com.simon.harmonichackernews.data.RepoInfo
import com.simon.harmonichackernews.data.StoryPresentationSnapshot
import com.simon.harmonichackernews.data.StorySnapshot
import com.simon.harmonichackernews.presentation.StoryListItemSnapshot
import com.simon.harmonichackernews.settings.DisplayStyle
import com.simon.harmonichackernews.ui.comments.CommentsPreviewPlatform
import com.simon.harmonichackernews.ui.comments.CommentsPreviewPlatformProvider
import com.simon.harmonichackernews.ui.comments.LinkPreviewContent
import com.simon.harmonichackernews.ui.theme.HarmonicTheme
import com.simon.harmonichackernews.ui.theme.HarmonicThemeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Exercises the real repository website row without making network requests or opening a browser. */
@RunWith(AndroidJUnit4::class)
class LinkPreviewRippleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun websitePressKeepsRoundedCornersClearAndStillOpensLink() {
        val website = "https://example.test"
        var openedLink: String? = null
        val repo = RepoInfo(
            name = "preview-fixture", owner = "harmonic", about = "Repository website press feedback",
            website = website, license = "MIT", language = "Kotlin", stars = 123, watching = 4, forks = 8,
        )
        val story = StoryListItemSnapshot(
            StorySnapshot(42, url = "https://github.com/harmonic/preview-fixture"),
            StoryPresentationSnapshot(loaded = true, isLink = true, repoInfo = repo),
        )
        compose.setContent {
            val palette = HarmonicThemeCatalog.resolve("light", false)
            HarmonicTheme(palette.colors, palette.colorScheme, palette.dark) {
                CommentsPreviewPlatformProvider(CommentsPreviewPlatform(
                    textStyle = TextStyle.Default,
                    openLink = { openedLink = it }, downloadPdf = {}, openCustomTab = {},
                    plainText = { it }, annotatedHtml = { text, _, _ -> AnnotatedString(text) },
                )) {
                    Column(Modifier.fillMaxWidth().background(Color.White).testTag("preview-fixture").padding(vertical = 24.dp)) {
                        Text("Link press feedback · UI fixture", Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                        LinkPreviewContent(story = story, contentVersion = 0, settings = settings)
                    }
                }
            }
        }
        compose.waitForIdle()
        val link = compose.onNode(hasClickAction() and hasText(repo.shortenedUrl!!))
        val resting = link.captureToImage().toPixelMap()
        compose.mainClock.autoAdvance = false
        link.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(400)
        // Android's RenderThread ripple has its own clock; the Compose test clock only advances
        // composition animations. Let the native pressed-state animation reach its full bounds.
        SystemClock.sleep(400)
        compose.waitForIdle()
        try {
            val pressed = link.captureToImage().toPixelMap()
            fun delta(x: Int, y: Int): Float {
                val before = resting[x, y]
                val after = pressed[x, y]
                return abs(before.red - after.red) + abs(before.green - after.green) + abs(before.blue - after.blue)
            }
            // Stay one physical pixel inside the capture: fractional layout bounds can leave
            // its outermost pixel outside even the straight edge of the rendered row.
            val straightEdgeDelta = delta(pressed.width / 2, 1)
            val cornerDelta = delta(1, 1)
            val screenshot = compose.onNodeWithTag("preview-fixture").captureToImage().asAndroidBitmap()
            File(compose.activity.filesDir, "link-preview-website-pressed-fixture.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            File(compose.activity.filesDir, "link-preview-website-pressed-detail.png").outputStream().use {
                link.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            assertTrue("A held website row must show press feedback (delta=$straightEdgeDelta)", straightEdgeDelta > 0.02f)
            assertTrue("The 2dp rounded corner must remain outside the press feedback (corner=$cornerDelta, edge=$straightEdgeDelta)", cornerDelta < straightEdgeDelta * 0.4f)
        } finally {
            link.performTouchInput { up() }
            compose.mainClock.autoAdvance = true
        }
        compose.waitForIdle()
        assertEquals(website, openedLink)
    }

    private val settings = CommentDisplaySettings(
        collapseParent = false, showThumbnail = false, showHeaderPreviewImage = false,
        tintHeader = false, showUpButton = false, paletteTintMode = "default",
        preferredTextSize = 14f, commentDepthIndicatorMode = "threads", showNavigationBar = false,
        font = "default", showInvert = false, showTopLevelDepthIndicator = false, theme = null,
        isTablet = false, faviconProvider = "default", swapLongPressTap = false,
        displayStyle = DisplayStyle.FLAT, outline = false, showDividers = false,
        highlightCommentMeta = false, collectReferenceLinks = false, hasAccountDetails = false,
        canProvideSummary = false, showAdditionalSummaryInfo = false, enableSummaryBoldFormatting = true,
    )
}
