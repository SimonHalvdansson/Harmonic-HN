@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.ui.comments.CommentsPreviewPlatform
import com.simon.harmonichackernews.ui.comments.CommentsPreviewPlatformProvider
import com.simon.harmonichackernews.ui.comments.HeaderStoryTextBlock
import com.simon.harmonichackernews.ui.content.htmlAnnotatedString
import com.simon.harmonichackernews.ui.content.trimmedCommentText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeaderStoryTextGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun plainHeaderPreservesPreparedFormattingQuotesAndParagraphTrimming() {
        val html = "<p>Ordinary <b>bold</b> and <i>italic</i> text</p><p>&gt; A quote</p><p>4 &gt; 3</p>"
        val trim = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                CommentsPreviewPlatformProvider(platform {}) {
                    SelectionContainer {
                        HeaderStoryTextBlock(html, trim.value, FontFamily.SansSerif, 16f) { _, _, _ ->
                            error("Ordinary text has no link preview")
                        }
                    }
                }
            }
        }
        val expected = htmlAnnotatedString(html, Color.Blue, LinkInteractionListener {})
        for (trimEdges in listOf(false, true, false)) {
            compose.runOnIdle { trim.value = trimEdges }
            val actual = compose.onNodeWithText("Ordinary", substring = true)
                .fetchSemanticsNode().config[SemanticsProperties.Text].single()
            assertEquals(if (trimEdges) expected.trimmedCommentText() else expected, actual)
        }
    }

    @Test
    fun linksSupportTapAndLongPressAfterPlainTextAndPlacementChanges() {
        val url = "https://example.com/article"
        val html = mutableStateOf("<a href='$url'>Reference article</a>")
        val topPadding = mutableStateOf(16.dp)
        val clicks = mutableListOf<String?>()
        val previews = mutableListOf<Triple<String, String, Rect>>()
        compose.setContent {
            MaterialTheme {
                CommentsPreviewPlatformProvider(platform { clicks += it }) {
                    Box(Modifier.padding(start = 16.dp, top = topPadding.value)) {
                        SelectionContainer {
                            HeaderStoryTextBlock(html.value, false, FontFamily.SansSerif, 16f) { target, label, bounds ->
                                previews += Triple(target, label, bounds)
                            }
                        }
                    }
                }
            }
        }
        val link = compose.onNodeWithText("Reference article")
        link.performTouchInput { click(Offset(20f, center.y)) }
        link.performTouchInput { longClick(Offset(20f, center.y)) }
        compose.runOnIdle {
            assertEquals(listOf(url), clicks)
            assertEquals(url, previews.single().first)
            assertEquals("Reference article", previews.single().second)
            assertTrue(previews.single().third.left > 0f)
            html.value = "Plain header"
        }
        compose.onNodeWithText("Plain header").assertExists()
        compose.runOnIdle {
            html.value = "<a href='$url'>Reference article</a>"
            topPadding.value = 48.dp
        }
        link.performTouchInput { longClick(Offset(20f, center.y)) }
        compose.runOnIdle {
            assertEquals(listOf(url), clicks)
            assertEquals(2, previews.size)
            assertTrue(previews.last().third.top > previews.first().third.top)
        }
        link.performTouchInput { click(Offset(20f, center.y)) }
        compose.runOnIdle { assertEquals(listOf(url, url), clicks) }
    }

    private fun platform(openLink: (String?) -> Unit) = CommentsPreviewPlatform(
        textStyle = TextStyle.Default, openLink = openLink, downloadPdf = {},
        openCustomTab = {}, plainText = { it },
    )
}
