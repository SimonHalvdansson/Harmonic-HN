package com.simon.harmonichackernews.ui.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkInteractionListener
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentHtmlTest {
    @Test
    fun formattedParagraphsUseOneLegacyBlankLine() {
        val rendered = htmlAnnotatedString(
            html = "First\n<p>Second</p>\n<p>Third</p>",
            linkColor = Color.Blue,
            linkListener = LinkInteractionListener { },
        )

        assertEquals("First\n\nSecond\n\nThird", rendered.text)
    }
}
