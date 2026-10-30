package com.simon.harmonichackernews.ui.comments

import androidx.compose.ui.text.LinkAnnotation
import kotlin.test.Test
import kotlin.test.assertEquals

class SummaryMarkdownLinksTest {
    @Test
    fun unfinishedTagsAndRepeatedAngleBracketsKeepTheirText() {
        for (input in listOf("<a " + " ".repeat(60 * 1024) + "x", "<".repeat(32 * 1024) + ">")) {
            assertEquals(input, summaryMarkdownAnnotatedString(input).text)
        }
    }

    @Test
    fun unfinishedCommentsAndLinkLabelsKeepTheirText() {
        for (input in listOf("<!--".repeat(8 * 1024), "[".repeat(32 * 1024), "[".repeat(16 * 1024) + "](unfinished")) {
            assertEquals(input, summaryMarkdownAnnotatedString(input).text)
        }
        assertEquals("Beforeafter", summaryMarkdownAnnotatedString("Before<!-- hidden -->after").text)
    }

    @Test
    fun oversizedMarkdownKeepsTheCompleteUnformattedSource() {
        val input = "**" + "x".repeat(64 * 1024) + "**"
        assertEquals(input, summaryMarkdownAnnotatedString(input).text)
        assertEquals(null, summaryMarkdownListItems(input))
    }

    @Test
    fun tagsWithAttributesAndBrokenPrefixesPreserveAutolinks() {
        val input = "<span class='note'>A</span><br/>B <unfinished <b>C</b> <person@example.com>"
        assertEquals("AB <unfinished C person@example.com", summaryMarkdownAnnotatedString(input).text)
    }

    @Test
    fun autoLinkRemainsVisible() {
        val rendered = summaryMarkdownAnnotatedString("Read <https://example.com/guide> for details.")
        assertEquals("Read https://example.com/guide for details.", rendered.text)
        assertEquals("https://example.com/guide", (rendered.getLinkAnnotations(0, rendered.length).single().item as LinkAnnotation.Url).url)
    }

    @Test
    fun emailAutolinksAndHtmlTagsRemainDistinct() {
        val rendered = summaryMarkdownAnnotatedString("<b>Email</b> <person@example.com> or <https://example.com/a_(b)>.")
        assertEquals("Email person@example.com or https://example.com/a_(b).", rendered.text)
        assertEquals(listOf("mailto:person@example.com", "https://example.com/a_(b)"),
            rendered.getLinkAnnotations(0, rendered.length).map { (it.item as LinkAnnotation.Url).url })
    }

    @Test
    fun escapedAndNestedParenthesesKeepTheDestinationIntact() {
        for (destination in listOf("https://example.com/a_(b_(c))", "https://example.com/a\\(b\\)")) {
            val rendered = summaryMarkdownAnnotatedString("[Details]($destination), next.")
            assertEquals("Details, next.", rendered.text)
            assertEquals(destination.replace("\\", ""),
                (rendered.getLinkAnnotations(0, rendered.length).single().item as LinkAnnotation.Url).url)
        }
        val unfinished = "[Details](https://example.com/a_(b)"
        assertEquals(unfinished, summaryMarkdownAnnotatedString(unfinished).text)
    }

    @Test
    fun linkWithBalancedParenthesesKeepsItsDestination() {
        val url = "https://en.wikipedia.org/wiki/Function_(mathematics)"
        val rendered = summaryMarkdownAnnotatedString("[Function]($url)")
        assertEquals(url, (rendered.getLinkAnnotations(0, rendered.length).single().item as LinkAnnotation.Url).url)
        assertEquals("Function", rendered.text)
    }
}
