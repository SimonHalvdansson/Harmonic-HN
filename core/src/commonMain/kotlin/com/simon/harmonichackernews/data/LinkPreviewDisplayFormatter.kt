package com.simon.harmonichackernews.data

import kotlin.math.roundToLong
import kotlin.math.round

object LinkPreviewDisplayFormatter {
    /** Shared by model and rich-card counts; raw provider values remain unchanged. */
    fun formatCompactCount(number: Long): String {
        val (divisor, suffix) = when {
            number >= 1_000_000_000_000L -> 1_000_000_000_000L to "T"
            number >= 1_000_000_000L -> 1_000_000_000L to "B"
            number >= 1_000_000L -> 1_000_000L to "M"
            number >= 1_000L -> 1_000L to "K"
            else -> return number.toString()
        }
        val scaled = number.toDouble() / divisor
        val factor = when {
            scaled >= 100 -> 1.0
            scaled >= 10 -> 10.0
            else -> 100.0
        }
        return "${(round(scaled * factor) / factor).toString().removeSuffix(".0")}$suffix"
    }

    fun formatCount(count: Int, singular: String, plural: String?): String {
        return if (count == 1) "1 $singular" else "${formatCompactCount(count)} $plural"
    }

    fun formatCompactCount(number: Int): String {
        if (number < 1000) {
            return number.toString()
        }

        val rounded = (number.toDouble() / 100).roundToLong() * 100
        val whole = rounded / 1000
        val tenths = (rounded % 1000) / 100
        return if (tenths == 0L) "${whole}k" else "$whole.${tenths}k"
    }

    fun formatDisplayUrl(url: String?): String? {
        url ?: return null

        var shortenedUrl = when {
            url.startsWith("https://") -> url.substring(8)
            url.startsWith("http://") -> url.substring(7)
            else -> url
        }

        shortenedUrl = shortenedUrl.removePrefix("www.")

        return shortenedUrl
    }
}
