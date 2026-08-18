package com.simon.harmonichackernews.data

import kotlin.math.roundToLong

class OpenRouterModelInfo {
    var provider: String? = null
    var name: String? = null
    var website: String? = null
    var providerIconUrl: String? = null
    var description: String? = null
    var promptPricePerToken: String? = null
    var completionPricePerToken: String? = null
    var contextLength: Long = 0
    var maxCompletionTokens: Long = 0
    var inputModalities: List<String> = emptyList()
    var outputModalities: List<String> = emptyList()
    var knowledgeCutoff: String? = null

    fun formatPromptPrice(): String? = formatPrice(promptPricePerToken, "input")

    fun formatCompletionPrice(): String? = formatPrice(completionPricePerToken, "output")

    fun formatContext(): String? = contextLength
        .takeIf { it > 0 }
        ?.let { "${compactTokens(it)} context" }

    fun formatMaxOutput(): String? = maxCompletionTokens
        .takeIf { it > 0 }
        ?.let { "${compactTokens(it)} max output" }

    fun formatModalities(): String? {
        if (inputModalities.isEmpty() || outputModalities.isEmpty()) return null
        val priority = listOf("text", "image", "file", "audio", "video")
        val inputs = inputModalities
            .distinct()
            .sortedWith(compareBy({ priority.indexOf(it).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }, { it }))
            .joinToString(" + ")
            .replaceFirstChar(Char::uppercase)
        val outputs = outputModalities.distinct().joinToString(" + ")
        return "$inputs → $outputs"
    }

    fun formatKnowledgeCutoff(): String? {
        val parts = knowledgeCutoff?.take(10)?.split('-') ?: return null
        if (parts.size < 2) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull()?.let { MONTHS.getOrNull(it - 1) } ?: return null
        return "Knowledge $month $year"
    }

    private fun formatPrice(perToken: String?, label: String): String? {
        val perMillion = perToken?.toDoubleOrNull()?.times(1_000_000)
            ?.takeIf { it.isFinite() && it >= 0 }
            ?: return null
        val cents = (perMillion * 100).roundToLong()
        val whole = cents / 100
        val remainder = cents % 100
        val amount = if (remainder == 0L) {
            whole.toString()
        } else {
            "$whole.${remainder.toString().padStart(2, '0')}"
        }
        return "\$$amount/M $label"
    }

    private fun compactTokens(value: Long): String {
        val (divisor, suffix) = when {
            value >= 1_000_000_000 -> 1_000_000_000L to "B"
            value >= 1_000_000 -> 1_000_000L to "M"
            value >= 1_000 -> 1_000L to "K"
            else -> return value.toString()
        }
        val hundredths = (value.toDouble() / divisor * 100).roundToLong()
        val whole = hundredths / 100
        val remainder = hundredths % 100
        val decimal = remainder.toString().padStart(2, '0').trimEnd('0')
        return if (decimal.isEmpty()) "$whole$suffix" else "$whole.$decimal$suffix"
    }

    private companion object {
        val MONTHS = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
    }
}
