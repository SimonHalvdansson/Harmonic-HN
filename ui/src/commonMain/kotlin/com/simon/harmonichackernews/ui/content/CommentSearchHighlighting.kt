package com.simon.harmonichackernews.ui.content


import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt

@Composable
internal fun animatedSearchMatches(
    body: AnnotatedString,
    searchTerm: String,
    baseColor: Color,
    markedColor: Color,
): AnnotatedString {
    val normalizedSearchTerm = searchTerm.trim()
    var highlightTransition by remember(body) {
        mutableStateOf(SearchHighlightTransition())
    }
    val highlightProgress = remember(body) { Animatable(0f) }
    LaunchedEffect(body, normalizedSearchTerm) {
        val next = highlightTransition.retarget(body, normalizedSearchTerm, highlightProgress.value)
        highlightTransition = next
        highlightProgress.snapTo(0f)
        if (next.runs.all { it.from == it.to }) {
            highlightProgress.snapTo(1f)
        } else {
            highlightProgress.animateTo(1f, contentTween())
        }
    }
    val displayedBody = remember(
        body,
        baseColor,
        markedColor,
        highlightTransition,
        highlightProgress.value,
    ) {
        highlightSearchMatches(
            body = body,
            transition = highlightTransition,
            progress = highlightProgress.value,
            baseColor = baseColor,
            markedColor = markedColor,
        )
    }
    return displayedBody
}

internal fun highlightSearchMatches(
    body: AnnotatedString,
    searchTerm: String,
    markedColor: Color,
): AnnotatedString {
    val needle = searchTerm.trim()
    if (needle.isEmpty()) return body
    val firstMatch = body.text.indexOf(needle, ignoreCase = true)
    if (firstMatch < 0) return body
    val style = SpanStyle(color = markedColor, fontWeight = FontWeight.Bold)

    return buildAnnotatedString {
        append(body)
        var start = firstMatch
        while (start >= 0) {
            addStyle(
                style,
                start,
                start + needle.length,
            )
            start = body.text.indexOf(needle, start + needle.length, ignoreCase = true)
        }
    }
}

internal data class SearchHighlightRun(
    val start: Int,
    val end: Int,
    val from: Float,
    val to: Float,
    val baseWeight: FontWeight,
) {
    fun amount(progress: Float): Float = from + (to - from) * progress
}

/** Retargeting resolves overlap and original weights once; frames visit only highlighted runs. */
internal class SearchHighlightTransition(val runs: List<SearchHighlightRun> = emptyList()) {
    fun retarget(body: AnnotatedString, searchTerm: String, progress: Float): SearchHighlightTransition {
        val matches = searchMatchRanges(body.text, searchTerm)
        val boundaries = buildSet {
            runs.forEach { add(it.start); add(it.end) }
            matches.forEach { add(it.start); add(it.end) }
            body.spanStyles.forEach { span ->
                if (span.item.fontWeight != null) { add(span.start); add(span.end) }
            }
        }.sorted()
        var previousIndex = 0
        var matchIndex = 0
        return SearchHighlightTransition(buildList {
            for (index in 0 until boundaries.lastIndex) {
                val start = boundaries[index]
                val end = boundaries[index + 1]
                while (previousIndex < runs.size && runs[previousIndex].end <= start) previousIndex++
                while (matchIndex < matches.size && matches[matchIndex].end <= start) matchIndex++
                val from = runs.getOrNull(previousIndex)?.takeIf { start >= it.start }?.amount(progress) ?: 0f
                val to = if (matches.getOrNull(matchIndex)?.let { start >= it.start } == true) 1f else 0f
                if (from > SearchHighlightThreshold || to > 0f) {
                    add(SearchHighlightRun(start, end, from, to, body.baseFontWeightAt(start)))
                }
            }
        })
    }
}

internal fun searchMatchRanges(text: String, searchTerm: String): List<TextRange> {
    val needle = searchTerm.trim()
    if (needle.isEmpty()) return emptyList()
    // Match in the original text: Unicode lowercasing can change its length and offsets.
    return buildList {
        var start = text.indexOf(needle, ignoreCase = true)
        while (start >= 0) {
            add(TextRange(start, start + needle.length))
            start = text.indexOf(needle, start + needle.length, ignoreCase = true)
        }
    }
}

internal fun highlightSearchMatches(
    body: AnnotatedString,
    transition: SearchHighlightTransition,
    progress: Float,
    baseColor: Color,
    markedColor: Color,
): AnnotatedString {
    if (transition.runs.none { it.amount(progress) > SearchHighlightThreshold }) return body
    return buildAnnotatedString {
        append(body)
        transition.runs.forEach { run ->
            val amount = run.amount(progress)
            if (amount > SearchHighlightThreshold) {
                val weight = (run.baseWeight.weight +
                    (FontWeight.Bold.weight - run.baseWeight.weight) * amount
                ).roundToInt().coerceIn(1, 1000)
                addStyle(
                    SpanStyle(color = lerp(baseColor, markedColor, amount), fontWeight = FontWeight(weight)),
                    run.start,
                    run.end,
                )
            }
        }
    }
}

private fun AnnotatedString.baseFontWeightAt(index: Int): FontWeight = spanStyles
    .lastOrNull { range -> index >= range.start && index < range.end && range.item.fontWeight != null }
    ?.item
    ?.fontWeight
    ?: FontWeight.Normal

private const val SearchHighlightThreshold = 0.001f
