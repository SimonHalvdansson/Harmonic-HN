package com.simon.harmonichackernews.data

import java.util.Locale

internal object LinkPreviewFormatUtils {
    fun formatCount(count: Int, singular: String, plural: String?): String {
        return if (count == 1) "1 $singular" else "${kFormat(count)} $plural"
    }

    fun kFormat(number: Int): String {
        if (number < 1000) {
            return number.toString()
        }

        val rounded = (Math.round(number.toDouble() / 100) * 100).toDouble()
        val result = String.format(Locale.US, "%.1fk", rounded / 1000)
        if (result.endsWith(".0k")) {
            return result.substring(0, result.length - 3) + "k"
        }
        return result
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
