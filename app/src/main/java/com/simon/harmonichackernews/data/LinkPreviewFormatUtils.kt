package com.simon.harmonichackernews.data

import androidx.annotation.Nullable
import java.util.Locale

internal object LinkPreviewFormatUtils {
    fun formatCount(count: Int, singular: String, plural: String?): String {
        if (count == 1) {
            return "1 " + singular
        }
        return kFormat(count) + " " + plural
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
        if (url == null) {
            return null
        }

        var shortenedUrl: String = url
        if (shortenedUrl.startsWith("https://")) {
            shortenedUrl = shortenedUrl.substring(8)
        } else if (shortenedUrl.startsWith("http://")) {
            shortenedUrl = shortenedUrl.substring(7)
        }

        if (shortenedUrl.startsWith("www.")) {
            shortenedUrl = shortenedUrl.substring(4)
        }

        return shortenedUrl
    }
}
