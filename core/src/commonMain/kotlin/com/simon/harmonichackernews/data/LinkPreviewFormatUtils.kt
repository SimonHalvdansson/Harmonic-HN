package com.simon.harmonichackernews.data

import kotlin.math.roundToLong

internal object LinkPreviewFormatUtils {
    fun formatCount(count: Int, singular: String, plural: String?): String {
        return if (count == 1) "1 $singular" else "${kFormat(count)} $plural"
    }

    fun kFormat(number: Int): String {
        if (number < 1000) {
            return number.toString()
        }

        val rounded = (number.toDouble() / 100).roundToLong() * 100
        val whole = rounded / 1000
        val tenths = (rounded % 1000) / 100
        return if (tenths == 0L) "${whole}k" else "$whole.${tenths}k"
    }

    fun shortenUrl(url: String?): String? {
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
