package com.simon.harmonichackernews.presentation

object CommentTextPolicy {
    private val paragraphStart = Regex("<p\\s*>", RegexOption.IGNORE_CASE)
    private val adjacentParagraphs = Regex("</p>\\s*<p", RegexOption.IGNORE_CASE)
    private val adjacentDivisions = Regex("</div>\\s*<div", RegexOption.IGNORE_CASE)
    private val codeBlockEnd = Regex("(</tt>\\s*</div>)\\s*(<p\\s*>\\s*)?(?=\\S)", RegexOption.IGNORE_CASE)
    private val blockStart = Regex("<(?:p|br|div|pre)\\b", RegexOption.IGNORE_CASE)

    fun preserveLegacyParagraphSpacing(html: String): String {
        if ('<' !in html) return html
        // Android's compact HTML renderer collapses the code's final <br> and </div>
        // into one newline. Add the missing margin when prose follows directly.
        val spacedCode = codeBlockEnd.replace(html) { end ->
            val next = end.range.last + 1
            if (end.groups[2] == null && blockStart.matchesAt(html, next)) end.value
            else end.groupValues[1] + "<br>"
        }
        return spacedCode
            .replace(paragraphStart, "<br><br>")
            .replace(adjacentParagraphs, "</p><br><p")
            .replace(adjacentDivisions, "</div><br><div")
    }
}
