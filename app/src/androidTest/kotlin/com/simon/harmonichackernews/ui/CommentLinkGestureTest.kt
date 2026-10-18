@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.simon.harmonichackernews.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.font.FontFamily
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.settings.QuoteHighlightColor
import com.simon.harmonichackernews.settings.QuoteHighlightStyle
import com.simon.harmonichackernews.settings.QuoteSpacing
import com.simon.harmonichackernews.ui.content.CommentBodyText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentLinkGestureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun linkAfterPlainQuoteSupportsTapAndLongPressWithoutAnExtraClick() {
        val url = "https://example.com/article"
        val clicks = mutableListOf<String>()
        val previews = mutableListOf<Triple<String, String, Rect>>()
        compose.setContent {
            MaterialTheme {
                CommentBodyText(
                    highlightQuotes = true,
                    quoteSpacing = QuoteSpacing.COMPACT,
                    quoteHighlightColor = QuoteHighlightColor.MATERIAL,
                    quoteHighlightStyle = QuoteHighlightStyle.BOTH,
                    html = "&gt; A quote without links<p><a href='$url'>Reference article</a>",
                    searchTerm = "",
                    markedColor = Color.Yellow,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 16f,
                    animateSearchMatches = false,
                    onLinkClick = { clicks += it },
                    onLinkLongClick = { target, label, bounds -> previews += Triple(target, label, bounds) },
                )
            }
        }
        val link = compose.onNodeWithText("Reference article")
        link.performTouchInput { click(Offset(20f, center.y)) }
        link.performTouchInput { longClick(Offset(20f, center.y)) }
        compose.runOnIdle {
            assertEquals(listOf(url), clicks)
            val (target, label, bounds) = previews.single()
            assertEquals(url, target)
            assertEquals("Reference article", label)
            assertTrue(bounds.left > 0f && bounds.top > 0f)
        }
        link.performTouchInput { click(Offset(20f, center.y)) }
        compose.runOnIdle { assertEquals(listOf(url, url), clicks) }
    }
}
