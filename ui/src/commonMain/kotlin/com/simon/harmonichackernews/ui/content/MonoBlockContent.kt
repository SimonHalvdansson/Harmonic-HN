package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

internal data class CommentTextPart(val text: AnnotatedString, val monoBlock: Boolean)

internal fun AnnotatedString.monoBlockParts(): List<CommentTextPart> {
    val ranges = getStringAnnotations(COMMENT_MONO_BLOCK_TAG, 0, length)
    if (ranges.isEmpty()) return emptyList()
    return buildList {
        var cursor = 0
        fun appendProse(end: Int) {
            // Block boundaries already have paragraph spacing in the column. Only strip the
            // separator newlines outside the author's preformatted range.
            var start = cursor
            var stop = end
            if (start > 0) while (start < stop && text[start] == '\n') start++
            if (end < length) while (stop > start && text[stop - 1] == '\n') stop--
            if (start < stop) add(CommentTextPart(subSequence(start, stop), false))
        }
        for (range in ranges) {
            appendProse(range.start)
            add(CommentTextPart(subSequence(range.start, range.end), true))
            cursor = range.end
        }
        appendProse(length)
    }
}

@Composable
internal fun MonoBlockContent(
    text: AnnotatedString,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (AnnotatedString, Modifier) -> Unit,
) {
    val parts = if (enabled) remember(text) { text.monoBlockParts() } else emptyList()
    if (parts.isEmpty()) {
        content(text, modifier)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            if (part.monoBlock) {
                val scroll = rememberScrollState()
                Box(Modifier.fillMaxWidth().horizontalScroll(scroll, enabled = scroll.maxValue > 0)) {
                    content(part.text, Modifier)
                }
                val indicator = scroll.scrollIndicatorState
                if (indicator != null && scroll.maxValue in 1 until Int.MAX_VALUE) {
                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                    Spacer(
                        Modifier.fillMaxWidth().height(2.dp).nonInteractiveScrollbar(
                            state = indicator,
                            orientation = Orientation.Horizontal,
                            thumbColor = color.copy(alpha = 0.42f),
                            trackColor = color.copy(alpha = 0.09f),
                            thickness = 2.dp,
                            thumbMaxLengthFraction = 1f,
                            isFadeEnabled = false,
                            mainAxisTrackInset = 0.dp,
                            crossAxisTrackInset = 0.dp,
                        ),
                    )
                }
            } else content(part.text, Modifier)
        }
    }
}
