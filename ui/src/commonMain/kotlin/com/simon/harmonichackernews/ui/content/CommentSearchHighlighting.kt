package com.simon.harmonichackernews.ui.content


import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
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
        mutableStateOf(SearchHighlightTransition.empty(body.length))
    }
    val highlightProgress = remember(body) { Animatable(0f) }
    LaunchedEffect(body, normalizedSearchTerm) {
        val currentEmphasis = highlightTransition.interpolate(highlightProgress.value)
        val targetEmphasis = searchMatchEmphasis(body.text, normalizedSearchTerm)
        highlightTransition = SearchHighlightTransition(
            from = currentEmphasis,
            to = targetEmphasis,
        )
        highlightProgress.snapTo(0f)
        if (currentEmphasis.contentEquals(targetEmphasis)) {
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

    return buildAnnotatedString {
        append(body)
        var start = body.text.indexOf(needle, ignoreCase = true)
        while (start >= 0) {
            addStyle(
                SpanStyle(color = markedColor, fontWeight = FontWeight.Bold),
                start,
                start + needle.length,
            )
            start = body.text.indexOf(needle, start + needle.length, ignoreCase = true)
        }
    }
}

private data class SearchHighlightTransition(
    val from: FloatArray,
    val to: FloatArray,
) {
    fun interpolate(progress: Float): FloatArray = FloatArray(from.size) { index ->
        from[index] + (to[index] - from[index]) * progress
    }

    companion object {
        fun empty(length: Int) = SearchHighlightTransition(
            from = FloatArray(length),
            to = FloatArray(length),
        )
    }
}

internal fun searchMatchEmphasis(
    text: String,
    searchTerm: String,
): FloatArray {
    val emphasis = FloatArray(text.length)
    val needle = searchTerm.trim()
    if (needle.isEmpty()) return emphasis

    // Match in the original text: Unicode lowercasing can change its length and offsets.
    var start = text.indexOf(needle, ignoreCase = true)
    while (start >= 0) {
        for (index in start until start + needle.length) {
            emphasis[index] = 1f
        }
        start = text.indexOf(needle, start + needle.length, ignoreCase = true)
    }
    return emphasis
}

private fun highlightSearchMatches(
    body: AnnotatedString,
    transition: SearchHighlightTransition,
    progress: Float,
    baseColor: Color,
    markedColor: Color,
): AnnotatedString {
    val emphasis = transition.interpolate(progress)
    if (emphasis.all { it <= SearchHighlightThreshold }) return body

    return buildAnnotatedString {
        append(body)
        var index = 0
        while (index < emphasis.size) {
            val amount = emphasis[index]
            if (amount <= SearchHighlightThreshold) {
                index++
                continue
            }
            val baseWeight = body.baseFontWeightAt(index)
            val start = index
            index++
            while (
                index < emphasis.size &&
                emphasis[index] > SearchHighlightThreshold &&
                abs(emphasis[index] - amount) <= SearchHighlightThreshold &&
                body.baseFontWeightAt(index) == baseWeight
            ) {
                index++
            }
            val weight = (
                baseWeight.weight +
                    (FontWeight.Bold.weight - baseWeight.weight) * amount
                ).roundToInt().coerceIn(1, 1000)
            addStyle(
                SpanStyle(
                    color = lerp(baseColor, markedColor, amount),
                    fontWeight = FontWeight(weight),
                ),
                start,
                index,
            )
        }
    }
}

private fun AnnotatedString.baseFontWeightAt(index: Int): FontWeight = spanStyles
    .lastOrNull { range -> index >= range.start && index < range.end && range.item.fontWeight != null }
    ?.item
    ?.fontWeight
    ?: FontWeight.Normal

private const val SearchHighlightThreshold = 0.001f
