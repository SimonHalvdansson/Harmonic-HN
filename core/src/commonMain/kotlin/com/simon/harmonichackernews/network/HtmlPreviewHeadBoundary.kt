package com.simon.harmonichackernews.network

/**
 * Finds one explicit head boundary across byte chunks without decoding or parsing every prefix.
 * This is only an optimization hint. Ambiguous/malformed markup takes the bounded full-read path;
 * Ksoup remains responsible for extracting the preview. Raw text and comments cannot end the head.
 */
internal class HtmlPreviewHeadBoundary {
    private var offset = 0
    private var finished = false
    private var inHead = false
    private var inComment = false
    private var commentDashes = 0
    private var rawEnd: String? = null
    private var rawMatch = 0
    private var tag: StringBuilder? = null
    private var quote: Char? = null

    /** Returns the exclusive byte offset once, or null when more data/full parsing is needed. */
    fun accept(bytes: ByteArray, count: Int): Int? {
        if (finished) return null
        for (index in 0 until count) {
            offset++
            val ch = (bytes[index].toInt() and 0xff).toChar()
            if (inComment) {
                if (ch == '>' && commentDashes >= 2) inComment = false
                commentDashes = if (ch == '-') commentDashes + 1 else 0
                continue
            }
            val end = rawEnd
            if (end != null) {
                if (rawMatch == 1 && ch == '!' && end == "</script") {
                    // Legacy script comments can enter HTML's double-escaped script state.
                    // Leave those unusual boundaries to the full HTML parser.
                    finished = true
                    return null
                }
                if (rawMatch == end.length) {
                    if (ch == '>' || ch.isWhitespace()) {
                        rawEnd = null
                        tag = StringBuilder(end)
                        // Process the closing delimiter below, including optional whitespace.
                    } else {
                        rawMatch = if (ch == '<') 1 else 0
                        continue
                    }
                } else {
                    rawMatch = if (ch.lowercaseChar() == end[rawMatch]) rawMatch + 1
                        else if (ch == '<') 1 else 0
                    continue
                }
            }
            val current = tag
            if (current == null) {
                if (ch == '<') tag = StringBuilder("<")
                else if (!ch.isWhitespace()) {
                    // Unexpected text can make the HTML parser implicitly close the head.
                    finished = true
                    return null
                }
                continue
            }
            current.append(ch)
            if (current.length > 4096) {
                finished = true
                return null
            }
            if (current.length == 4 && current.toString() == "<!--") {
                tag = null
                inComment = true
                commentDashes = 0
                continue
            }
            if (quote != null) {
                if (ch == quote) quote = null
                continue
            }
            if (ch == '\'' || ch == '"') {
                quote = ch
                continue
            }
            if (ch != '>') continue
            tag = null
            val value = current.toString().lowercase()
            val closing = value.startsWith("</")
            val name = value.drop(if (closing) 2 else 1)
                .takeWhile { it.isLetterOrDigit() || it == '!' }
            val delimiter = value.getOrNull((if (closing) 2 else 1) + name.length)
            if (delimiter != '>' && delimiter != '/' && delimiter?.isWhitespace() != true) {
                finished = true
                return null
            }
            when {
                name == "!doctype" && !inHead -> Unit
                name == "html" && !closing && !inHead -> Unit
                name == "head" && !closing && !inHead -> inHead = true
                inHead && name == "head" && closing -> {
                    finished = true
                    return offset
                }
                inHead && name in RAW_TAGS -> if (!closing) {
                    rawEnd = "</$name"
                    rawMatch = 0
                }
                inHead && !closing && name in VOID_TAGS -> Unit
                else -> {
                    finished = true
                    return null
                }
            }
        }
        return null
    }

    private companion object {
        val RAW_TAGS = setOf("script", "style", "title")
        val VOID_TAGS = setOf("meta", "link", "base")
    }
}
