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
    private const val TRAILING_PUNCTUATION = ".,;:!?"
    private val pdfSuffixes = arrayOf(" [pdf]", "[pdf]", " (pdf)", "(pdf)")
    private val trailingYear = Regex("""\s+\(\d{4}\)\s*$""")
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
        if (processed.contains("pre>")) {
            if (processed.contains("<pre>")) processed = escapePreBlockWhitespace(processed)
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
        val year = trailingYear.find(title)
        val pdfTitle = if (year != null) {
            stripSuffix(title.substring(0, year.range.first), pdfSuffixes)
                ?.plus(year.value.trimEnd())
        } else if (mayHaveSuffix) stripSuffix(title, pdfSuffixes) else null
        when {
            url.endsWith(".pdf", ignoreCase = true) -> story.pdfTitle = pdfTitle ?: title
            pdfTitle != null -> story.pdfTitle = pdfTitle
            mayHaveSuffix -> story.videoTitle = stripSuffix(title, videoSuffixes)
        }
    }

    private fun linkify(input: String): String {
        // Both supported URL schemes share this prefix; ordinary text needs only one scan.
        if (!input.contains("http")) return input
        val anchors = anchorPattern.findAll(input).iterator()
        if (!anchors.hasNext()) return linkifySegment(input)
        // Most HTTP links in API comments are already anchors. Preserve the original string
        // until a bare URL actually changes a segment, including the text between anchors.
        var output: StringBuilder? = null
        var segmentStart = 0
        var copiedThrough = 0
        var http = input.indexOf("http")
        while (true) {
            val anchor = if (anchors.hasNext()) anchors.next() else null
            val segmentEnd = anchor?.range?.first ?: input.length
            if (http >= 0 && http < segmentStart) http = input.indexOf("http", segmentStart)
            if (http >= 0 && http < segmentEnd) {
                val segment = input.substring(segmentStart, segmentEnd)
                val linked = linkifySegment(segment)
                if (linked != segment) {
                    val destination = output ?: StringBuilder(input.length).also { output = it }
                    destination.append(input, copiedThrough, segmentStart).append(linked)
                    copiedThrough = segmentEnd
                }
            }
            if (anchor == null) break
            segmentStart = anchor.range.last + 1
        }
        return output?.append(input, copiedThrough, input.length)?.toString() ?: input
    }

    private fun linkifySegment(segment: String): String {
        if (segment.isEmpty()) return segment
        return urlPattern.replace(segment) { match ->
            val url = match.value
            var end = url.length
            var unmatchedClosing = url.count { it == ')' } - url.count { it == '(' }
            while (end > 0) {
                when {
                    url[end - 1] in TRAILING_PUNCTUATION -> end--
                    url[end - 1] == ')' && unmatchedClosing > 0 -> {
                        end--
                        unmatchedClosing--
                    }
                    else -> break
                }
            }
            val core = url.substring(0, end)
                .replace("&#x2F;", "/")
                .replace("&#47;", "/")
            "<a href=\"$core\">$core</a>${url.substring(end)}"
        }
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
