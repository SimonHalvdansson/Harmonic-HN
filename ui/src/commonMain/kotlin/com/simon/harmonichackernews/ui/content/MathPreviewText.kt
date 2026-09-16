package com.simon.harmonichackernews.ui.content

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import com.hrm.latex.renderer.measure.rememberLatexMeasurer
import com.hrm.latex.renderer.model.LatexConfig
import com.hrm.latex.renderer.model.LatexTheme
import com.hrm.latex.renderer.model.LineBreakingConfig

internal data class PreviewMathSegment(
    val source: String,
    val latex: String? = null,
    val display: Boolean = false,
)

/** Preserve prose and malformed input; only complete, unescaped math delimiters become formulas. */
internal fun previewMathSegments(text: String): List<PreviewMathSegment> {
    val segments = mutableListOf<PreviewMathSegment>()
    var plainStart = 0
    var index = 0
    fun escaped(at: Int): Boolean {
        var slashes = 0
        var previous = at - 1
        while (previous >= 0 && text[previous--] == '\\') slashes++
        return slashes % 2 != 0
    }
    while (index < text.length) {
        val opening = when {
            escaped(index) -> null
            text.startsWith("$$", index) -> "$$"
            text[index] == '$' -> "$"
            text.startsWith("\\(", index) -> "\\("
            text.startsWith("\\[", index) -> "\\["
            else -> null
        }
        if (opening == null) {
            index++
            continue
        }
        val closing = when (opening) {
            "\\(" -> "\\)"
            "\\[" -> "\\]"
            else -> opening
        }
        val start = index + opening.length
        var end = text.indexOf(closing, start)
        while (end >= 0 && escaped(end)) end = text.indexOf(closing, end + closing.length)
        if (end < 0 || text.substring(start, end).isBlank()) {
            index += opening.length
            continue
        }
        if (plainStart < index) segments += PreviewMathSegment(text.substring(plainStart, index))
        segments += PreviewMathSegment(
            source = text.substring(index, end + closing.length),
            latex = text.substring(start, end),
            display = opening == "$$" || opening == "\\[",
        )
        index = end + closing.length
        plainStart = index
    }
    if (plainStart < text.length) segments += PreviewMathSegment(text.substring(plainStart))
    return segments
}

/** Native inline formulas keep the surrounding abstract's font, wrapping, and theme. */
@Composable
internal fun MathPreviewText(
    text: String,
    color: Color,
    fontFamily: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val segments = remember(text) { previewMathSegments(text) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val config = remember(fontSize, color, widthPx) {
            LatexConfig(
                fontSize = fontSize,
                theme = LatexTheme.light(color = color),
                lineBreaking = LineBreakingConfig(enabled = true, maxWidth = widthPx),
            )
        }
        val measurer = rememberLatexMeasurer(config)
        val formulas = remember(segments, config, measurer) {
            segments.mapIndexedNotNull { index, segment ->
                segment.latex?.let { latex ->
                    val mathStyle = if (segment.display) "\\displaystyle " else "\\textstyle "
                    measurer.inlineContent(mathStyle + latex, config)?.let { index.toString() to it }
                }
            }.toMap()
        }
        val annotated = remember(segments) {
            buildAnnotatedString {
                segments.forEachIndexed { index, segment ->
                    if (segment.latex == null) {
                        append(segment.source.replace("\\$", "$"))
                    } else {
                        if (segment.display && length > 0) append('\n')
                        appendInlineContent(index.toString(), segment.source)
                        if (segment.display) append('\n')
                    }
                }
            }
        }
        Text(
            text = annotated,
            inlineContent = formulas,
            color = color,
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = lineHeight,
            style = style,
        )
    }
}
