package com.simon.harmonichackernews.presentation

data class EditorTextEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int = selectionStart,
)

fun formatEditorItalic(text: String, selectionStart: Int, selectionEnd: Int): EditorTextEdit {
    val (start, end) = normalizedSelection(text, selectionStart, selectionEnd)
    return if (start == end) {
        EditorTextEdit(
            text = text.substring(0, start) + "**" + text.substring(start),
            selectionStart = start + 1,
        )
    } else {
        EditorTextEdit(
            text = text.substring(0, start) + "*" +
                text.substring(start, end) + "*" + text.substring(end),
            selectionStart = start + 1,
            selectionEnd = end + 1,
        )
    }
}

fun formatEditorCodeBlock(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
): EditorTextEdit {
    val (start, end) = normalizedSelection(text, selectionStart, selectionEnd)
    if (start == end) {
        val prefix = codeBlockPrefix(text, start)
        val suffix = codeBlockSuffix(text, start)
        val snippet = prefix + "  code" + suffix
        return EditorTextEdit(
            text = text.substring(0, start) + snippet + text.substring(start),
            selectionStart = start + prefix.length + 2,
            selectionEnd = start + prefix.length + 6,
        )
    }

    val lineStart = text.lastIndexOf('\n', startIndex = start - 1).let {
        if (it < 0) 0 else it + 1
    }
    val nextNewline = text.indexOf('\n', startIndex = end)
    val lineEnd = if (nextNewline < 0) text.length else nextNewline
    val prefix = codeBlockPrefix(text, lineStart)
    val indented = text.substring(lineStart, lineEnd)
        .split('\n')
        .joinToString("\n") { "  $it" }
    val suffix = codeBlockSuffix(text, lineEnd)
    return EditorTextEdit(
        text = text.substring(0, lineStart) + prefix + indented + suffix +
            text.substring(lineEnd),
        selectionStart = lineStart + prefix.length,
        selectionEnd = lineStart + prefix.length + indented.length,
    )
}

private fun normalizedSelection(text: String, first: Int, second: Int): Pair<Int, Int> {
    val start = minOf(first, second).coerceIn(0, text.length)
    val end = maxOf(first, second).coerceIn(0, text.length)
    return start to end
}

private fun codeBlockPrefix(text: String, position: Int): String {
    if (position == 0) return ""
    val newlines = text.substring(0, position).takeLastWhile { it == '\n' }.length
    return when {
        newlines >= 2 -> ""
        newlines == 1 -> "\n"
        else -> "\n\n"
    }
}

private fun codeBlockSuffix(text: String, position: Int): String {
    if (position >= text.length) return ""
    val newlines = text.substring(position).takeWhile { it == '\n' }.length
    return when {
        newlines >= 2 -> ""
        newlines == 1 -> "\n"
        else -> "\n\n"
    }
}
