package com.simon.harmonichackernews.presentation

object CommentTextPolicy {
    private val paragraphStart = Regex("<p\\s*>", RegexOption.IGNORE_CASE)
    private val adjacentParagraphs = Regex("</p>\\s*<p", RegexOption.IGNORE_CASE)
    private val adjacentDivisions = Regex("</div>\\s*<div", RegexOption.IGNORE_CASE)

    fun preserveLegacyParagraphSpacing(html: String): String = html
        .replace(paragraphStart, "<br><br>")
        .replace(adjacentParagraphs, "</p><br><p")
        .replace(adjacentDivisions, "</div><br><div")
}
