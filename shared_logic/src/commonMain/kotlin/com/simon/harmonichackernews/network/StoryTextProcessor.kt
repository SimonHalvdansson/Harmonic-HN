package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.Story

/** Pure HTML and title normalization shared by wire mappers and legacy Android callers. */
object StoryTextProcessor {
    private val anchorPattern = Regex("(?is)<a\\b[^>]*>.*?</a>")
    private val urlPattern = Regex(
        "(https?:(?:/{1}|(?:&#x2F;)|(?:&#47;))" +
            "(?:/{1}|(?:&#x2F;)|(?:&#47;))" +
            "(?=[^\\s<>\"]*\\.)[^\\s<>\"]+)",
    )
    private const val TRAILING_PUNCTUATION = ".,;:!?)"
    private val pdfSuffixes = arrayOf(" [pdf]", "[pdf]", " (pdf)", "(pdf)")
    private val videoSuffixes = arrayOf(" [video]", "[video]", " (video)", "(video)")

    fun preprocessHtml(input: String?): String? {
        if (input.isNullOrEmpty()) return input
        var processed = linkify(input)
        if (processed.contains("code>")) {
            processed = processed.replace("<pre><code>", "<pre><small>")
                .replace("</code></pre>", "</small></pre>")
                .replace("<code>", "<pre><small>")
                .replace("</code>", "</small></pre>")
        }
        if (processed.contains("<pre>")) processed = escapePreBlockWhitespace(processed)
        if (processed.contains("pre>")) {
            processed = processed.replace("<pre>", "<div><tt>")
                .replace("</pre>", "</tt></div>")
        }
        return processed
    }

    fun applyTitleBadges(story: Story?) {
        val title = story?.title?.takeUnless(String::isEmpty) ?: return
        val url = story.url?.takeUnless(String::isEmpty) ?: return
        story.pdfTitle = null
        story.videoTitle = null

        val mayHaveSuffix = title.last() == ']' || title.last() == ')'
        val pdfTitle = if (mayHaveSuffix) stripSuffix(title, pdfSuffixes) else null
        when {
            url.endsWith(".pdf", ignoreCase = true) -> story.pdfTitle = pdfTitle ?: title
            pdfTitle != null -> story.pdfTitle = pdfTitle
            mayHaveSuffix -> story.videoTitle = stripSuffix(title, videoSuffixes)
        }
    }

    private fun linkify(input: String): String {
        if (!input.contains("http:") && !input.contains("https:")) return input
        val output = StringBuilder(input.length)
        var endOfPreviousAnchor = 0
        anchorPattern.findAll(input).forEach { anchor ->
            output.append(linkifySegment(input.substring(endOfPreviousAnchor, anchor.range.first)))
            output.append(anchor.value)
            endOfPreviousAnchor = anchor.range.last + 1
        }
        output.append(linkifySegment(input.substring(endOfPreviousAnchor)))
        return output.toString()
    }

    private fun linkifySegment(segment: String): String = urlPattern.replace(segment) { match ->
        val url = match.value
        var end = url.length
        while (end > 0 && url[end - 1] in TRAILING_PUNCTUATION) end--
        if (end > 0 && url[end - 1] == ')') {
            val core = url.substring(0, end)
            if (core.count { it == ')' } > core.count { it == '(' }) end--
        }
        val core = url.substring(0, end)
            .replace("&#x2F;", "/")
            .replace("&#47;", "/")
        "<a href=\"$core\">$core</a>${url.substring(end)}"
    }

    private fun escapePreBlockWhitespace(input: String): String = buildString(input.length) {
        var inPre = false
        var index = 0
        while (index < input.length) {
            when {
                input.startsWith("<pre>", index) -> {
                    inPre = true
                    append("<pre>")
                    index += 5
                }
                input.startsWith("</pre>", index) -> {
                    inPre = false
                    append("</pre>")
                    index += 6
                }
                inPre && input[index] == ' ' -> {
                    append("&nbsp;")
                    index++
                }
                inPre && input[index] == '\n' -> {
                    append("<br>")
                    index++
                }
                else -> append(input[index++])
            }
        }
    }

    private fun stripSuffix(title: String, suffixes: Array<String>): String? =
        suffixes.firstOrNull { title.endsWith(it, ignoreCase = true) }
            ?.let { title.dropLast(it.length) }
}
