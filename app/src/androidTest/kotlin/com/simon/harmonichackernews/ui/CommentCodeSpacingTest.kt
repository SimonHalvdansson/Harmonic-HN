package com.simon.harmonichackernews.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simon.harmonichackernews.network.StoryTextProcessor
import com.simon.harmonichackernews.presentation.CommentTextPolicy
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommentCodeSpacingTest {
    @Test
    fun hackerNewsCodeBlocksHaveABlankLineBeforeFollowingProse() {
        // Same HTML boundaries as comment 49747390, including the newline on each
        // side of </code></pre> and the unclosed <p> before the next code block.
        val html = "This should work:<p><pre><code>  command\n</code></pre>\n" +
            "Then open:<p><pre><code>  another command\n</code></pre>\nThat's running."
        assertEquals(
            "This should work:\n\n  command\n\nThen open:\n\n  another command\n\nThat's running.",
            render(html),
        )
    }

    @Test
    fun existingParagraphBoundaryDoesNotGainAnotherBlankLine() {
        assertEquals("  command\n\nNext", render("<pre><code>  command\n</code></pre><p>Next"))
        assertEquals("  command\n\nNext", render("<pre><code>  command</code></pre>Next"))
    }

    private fun render(html: String): String = AnnotatedString.fromHtml(
        CommentTextPolicy.preserveLegacyParagraphSpacing(StoryTextProcessor.preprocessHtml(html)!!),
    ).text.replace('\u00a0', ' ')
}
